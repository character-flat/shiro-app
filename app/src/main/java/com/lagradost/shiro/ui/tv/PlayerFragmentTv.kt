package com.lagradost.shiro.ui.tv

/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import androidx.core.content.ContextCompat
import androidx.leanback.app.PlaybackSupportFragment
import androidx.leanback.app.VideoSupportFragment
import androidx.leanback.app.VideoSupportFragmentGlueHost
import androidx.leanback.media.PlaybackGlue
import androidx.leanback.media.PlaybackTransportControlGlue
import androidx.leanback.widget.Action
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.PlaybackControlsRow
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.ext.leanback.LeanbackPlayerAdapter
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import com.google.android.exoplayer2.util.MimeTypes
import com.lagradost.shiro.R
import com.lagradost.shiro.ui.player.PlayerData
import com.lagradost.shiro.ui.player.PlayerFragment.Companion.onPlayerNavigated
import com.lagradost.shiro.ui.player.SSLTrustManager
import com.lagradost.shiro.ui.tv.MainFragment.Companion.hasBeenInPlayer
import com.lagradost.shiro.utils.AppUtils.getCurrentActivity
import com.lagradost.shiro.utils.AppUtils.getViewPosDur
import com.lagradost.shiro.utils.AppUtils.setViewPosDur
import com.lagradost.shiro.utils.DataStore.mapper
import com.lagradost.shiro.utils.ExtractorLink
import com.lagradost.shiro.utils.ShiroApi
import com.lagradost.shiro.utils.ShiroApi.Companion.USER_AGENT
import com.lagradost.shiro.utils.ShiroApi.Companion.loadLinks
import java.io.File
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.min


/** A fragment representing the current metadata item being played */
class PlayerFragmentTv : VideoSupportFragment() {

    /** AndroidX navigation arguments */

    //private lateinit var player: SimpleExoPlayer
    //private lateinit var database: TvMediaDatabase

    /** Allows interaction with transport controls, volume keys, media buttons  */
    private lateinit var mediaSession: MediaSessionCompat
    private var playbackPosition: Long = 0

    /** Glue layer between the player and our UI */
    private lateinit var playerGlue: MediaPlayerGlue
    private var currentUrl: ExtractorLink? = null
    private lateinit var exoPlayer: SimpleExoPlayer

    var data: PlayerData? = null
    private var isCurrentlyPlaying: Boolean = false

    private var selectedSource: ExtractorLink? = null
    private var sources: Pair<Int?, List<ExtractorLink>?> = Pair(null, null)

    // Prevent clicking next episode button multiple times
    private var isLoadingNextEpisode = false
    private val extractorLinks = mutableListOf<ExtractorLink>()

    /**
     * Connects a [MediaSessionCompat] to a [Player] so transport controls are handled automatically
     */
    private lateinit var mediaSessionConnector: MediaSessionConnector

    /** Custom implementation of [PlaybackTransportControlGlue] */
    private inner class MediaPlayerGlue(context: Context, adapter: LeanbackPlayerAdapter) :
        PlaybackTransportControlGlue<LeanbackPlayerAdapter>(context, adapter) {

        private val actionRewind = PlaybackControlsRow.RewindAction(context)
        private val actionFastForward = PlaybackControlsRow.FastForwardAction(context)
        private val actionSkipOp = PlaybackControlsRow.FastForwardAction(context)
        private val actionNextEpisode = PlaybackControlsRow.SkipNextAction(context)
        private val actionSources = PlaybackControlsRow.MoreActions(context)

        //private val actionClosedCaptions = PlaybackControlsRow.ClosedCaptioningAction(context)

        fun skipForward(millis: Long = SKIP_PLAYBACK_MILLIS) =
            // Ensures we don't advance past the content duration (if set)
            exoPlayer.seekTo(
                if (exoPlayer.contentDuration > 0) {
                    min(exoPlayer.contentDuration, exoPlayer.currentPosition + millis)
                } else {
                    exoPlayer.currentPosition + millis
                }
            )

        fun skipBackward(millis: Long = SKIP_PLAYBACK_MILLIS) =
            // Ensures we don't go below zero position
            exoPlayer.seekTo(max(0, exoPlayer.currentPosition - millis))

        override fun onCreatePrimaryActions(adapter: ArrayObjectAdapter) {
            super.onCreatePrimaryActions(adapter)
            actionRewind.icon = ContextCompat.getDrawable(context, R.drawable.netflix_skip_back)
            actionFastForward.icon = ContextCompat.getDrawable(context, R.drawable.netflix_skip_forward)
            actionSkipOp.icon = ContextCompat.getDrawable(context, R.drawable.ic_baseline_fast_forward_24)
            actionNextEpisode.icon = ContextCompat.getDrawable(context, R.drawable.exo_controls_next)
            actionSources.icon = ContextCompat.getDrawable(context, R.drawable.ic_baseline_playlist_play_24)

            // Append rewind and fast forward actions to our player, keeping the play/pause actions
            // created by default by the glue
            // adapter.add(actionRewind)
            // adapter.add(actionFastForward)
            adapter.add(actionSkipOp)

            if (sources.second?.size ?: 0 > 1) {
                adapter.add(actionSources)
            }
            if (data?.episodeIndex!! + 1 < data?.card?.episodes?.size!!) {
                adapter.add(actionNextEpisode)
            }
            //adapter.add(actionClosedCaptions)
        }

        override fun onActionClicked(action: Action) = when (action) {
            actionRewind -> skipBackward()
            actionFastForward -> skipForward()
            actionSkipOp -> skipForward(SKIP_OP_MILLIS)
            actionNextEpisode -> {
                if (data?.episodeIndex != null && !isLoadingNextEpisode) {
                    savePos()
                    playerGlue.host.hideControlsOverlay(false)
                    isLoadingNextEpisode = true
                    data?.episodeIndex = minOf(data?.episodeIndex!! + 1, data?.card?.episodes?.size!! - 1)
                    selectedSource = null
                    extractorLinks.clear()
                    releasePlayer()
                    loadAndPlay()
                } else {
                }
            }
            actionSources -> {
                sources.second?.let {
                    val index = maxOf(sources.second?.indexOf(selectedSource) ?: -1, 0)
                    selectedSource = it[(index + 1) % it.size]
                    //val speed = speedsText[which]
                    savePos()
                    releasePlayer()
                    loadAndPlay()
                }
                val sourcesTxt =
                    sources.second!!.mapIndexed { index, extractorLink -> if (extractorLink == selectedSource) "✦ ${extractorLink.name} selected" else extractorLink.name }
                Toast.makeText(
                    requireContext(),
                    "${sourcesTxt.joinToString(separator = "\n")}",
                    Toast.LENGTH_SHORT
                ).show()

            }
            else -> super.onActionClicked(action)
        }

        /** Custom function used to update the metadata displayed for currently playing media */
        fun setMetadata() {
            // Displays basic metadata in the player
            /*title = metadata.title
            subtitle = metadata.author
            lifecycleScope.launch(Dispatchers.IO) {
                metadata.artUri?.let { art = Coil.get(it) }
            }*/

            // Prepares metadata playback

        }
    }

    /** Updates last know playback position */
    private val updateMetadataTask: Runnable = object : Runnable {
        override fun run() {

            // Make sure that the view has not been destroyed
            view ?: return
            /*
            // The player duration is more reliable, since metadata.playbackDurationMillis has the
            //  "official" duration as per Google / IMDb which may not match the actual media
            val contentDuration = exoPlayer.duration
            val contentPosition = exoPlayer.currentPosition

            // Updates metadata state
            val metadata = args.metadata.apply {
                playbackPositionMillis = contentPosition
            }*/

            // Marks as complete if 95% or more of video is complete
            /*if (exoPlayer.playbackState == SimpleExoPlayer.STATE_ENDED ||
                (contentDuration > 0 && contentPosition > contentDuration * 0.95)
            ) {
                /*val programUri = TvLauncherUtils.removeFromWatchNext(requireContext(), metadata)
                if (programUri != null) lifecycleScope.launch(Dispatchers.IO) {
                    database.metadata().update(metadata.apply { watchNext = false })
                }*/

                // If playback is not done, update the state in watch next row with latest time
            } else {
                /*val programUri = TvLauncherUtils.upsertWatchNext(requireContext(), metadata)
                lifecycleScope.launch(Dispatchers.IO) {
                    database.metadata().update(
                            metadata.apply { if (programUri != null) watchNext = true })
                }*/
            }*/

            // Schedules the next metadata update in METADATA_UPDATE_INTERVAL_MILLIS milliseconds
            Log.d(TAG, "Media metadata updated successfully")
            view?.postDelayed(this, METADATA_UPDATE_INTERVAL_MILLIS)
        }
    }
    private fun releasePlayer() {
        isCurrentlyPlaying = false
        if (this::exoPlayer.isInitialized) {
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    private fun savePos() {
        println("Savepos")
        if (this::exoPlayer.isInitialized) {
            if (((data != null
                        && data?.episodeIndex != null) || data?.card?.episodes != null)
                && exoPlayer.duration > 0 && exoPlayer.currentPosition > 0
            ) {
                //println("SAVED POS $playbackPosition ${exoPlayer.currentPosition}")
                setViewPosDur(data!!, exoPlayer.currentPosition, exoPlayer.duration)
            }
        }
    }

    private fun initPlayer(currentUrl: ExtractorLink? = null) {
        isCurrentlyPlaying = true
        thread {
            val currentUrl = currentUrl ?: getCurrentUrl()
            if (currentUrl == null) {
                activity?.let {
                    it.runOnUiThread {
                        Toast.makeText(it, "No links found", LENGTH_LONG).show()
                        it.onBackPressed()
                    }
                }
                return@thread
            }
            val isOnline =
                currentUrl.url.startsWith("https://") || currentUrl.url.startsWith("http://")
            //database = TvMediaDatabase.getInstance(requireContext())
            //val metadata = args.metadata

            // Adds this program to the continue watching row, in case the user leaves before finishing
            /*val programUri = TvLauncherUtils.upsertWatchNext(requireContext(), metadata)
            if (programUri != null) lifecycleScope.launch(Dispatchers.IO) {
                database.metadata().update(metadata.apply { watchNext = true })
            }*/

            val mimeType = if (currentUrl.isM3u8) MimeTypes.APPLICATION_M3U8 else MimeTypes.APPLICATION_MP4
            val _mediaItem = MediaItem.Builder()
                //Replace needed for android 6.0.0  https://github.com/google/ExoPlayer/issues/5983
                .setMimeType(mimeType)

            class CustomFactory : DataSource.Factory {
                override fun createDataSource(): DataSource {
                    return if (isOnline) {
                        val dataSource = DefaultHttpDataSourceFactory(USER_AGENT).createDataSource()
                        /*FastAniApi.currentHeaders?.forEach {
                            dataSource.setRequestProperty(it.key, it.value)
                        }*/
                        currentUrl?.referer?.let { dataSource.setRequestProperty("Referer", it) }
                        dataSource
                    } else {
                        DefaultDataSourceFactory(requireContext(), USER_AGENT).createDataSource()
                    }
                }
            }

            if (isOnline) {
                _mediaItem.setUri(currentUrl?.url)
            } else {
                currentUrl?.let {
                    _mediaItem.setUri(Uri.fromFile(File(it.url)))
                }
            }


            val mediaItem = _mediaItem.build()
            val _exoPlayer = if (context != null) {
                println("CONTEXT IS NULL!")
                val trackSelector = DefaultTrackSelector(requireContext())
                // Disable subtitles
                trackSelector.parameters = DefaultTrackSelector.ParametersBuilder(requireContext())
                    .setRendererDisabled(C.TRACK_TYPE_VIDEO, true)
                    .setRendererDisabled(C.TRACK_TYPE_TEXT, true)
                    .setDisabledTextTrackSelectionFlags(C.TRACK_TYPE_TEXT)
                    .clearSelectionOverrides()
                    .build()
                SimpleExoPlayer.Builder(requireContext())
                    .setTrackSelector(trackSelector)
            } else {
                SimpleExoPlayer.Builder(getCurrentActivity()!!)
            }

            _exoPlayer.setMediaSourceFactory(DefaultMediaSourceFactory(CustomFactory()))

            activity?.runOnUiThread {
                if (data?.card?.episodes != null || (data?.slug != null && data?.episodeIndex != null)) {
                    val pro = getViewPosDur(
                        data?.slug!!,
                        data?.episodeIndex!!
                    )
                    playbackPosition =
                        if (pro.pos > 0 && pro.dur > 0 && (pro.pos * 100 / pro.dur) < 95) { // UNDER 95% RESUME
                            pro.pos
                        } else {
                            0L
                        }
                }
                exoPlayer = _exoPlayer.build().apply {
                    //playWhenReady = isPlayerPlaying
                    //seekTo(currentWindow, playbackPosition)
                    seekTo(0, playbackPosition)
                    setMediaItem(mediaItem, false)
                    prepare()
                }
                exoPlayer.addListener(object : Player.Listener {
                    override fun onPlayerError(error: ExoPlaybackException) {
                        // Lets pray this doesn't spam Toasts :)
                        when (error.type) {
                            ExoPlaybackException.TYPE_SOURCE -> {
                                if (currentUrl?.url != "") {
                                    Toast.makeText(
                                        activity,
                                        "Source error\n" + error.sourceException.message,
                                        LENGTH_LONG
                                    )
                                        .show()
                                }
                            }
                            ExoPlaybackException.TYPE_REMOTE -> {
                                Toast.makeText(activity, "Remote error", LENGTH_LONG)
                                    .show()
                            }
                            ExoPlaybackException.TYPE_RENDERER -> {
                                Toast.makeText(
                                    activity,
                                    "Renderer error\n" + error.rendererException.message,
                                    LENGTH_LONG
                                )
                                    .show()
                            }
                            ExoPlaybackException.TYPE_UNEXPECTED -> {
                                Toast.makeText(
                                    activity,
                                    "Unexpected player error\n" + error.unexpectedException.message,
                                    LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                })

                // Initializes the video player
                //player = ExoPlayerFactory.newSimpleInstance(requireContext())


                // Listen to media session events. This is necessary for things like closed captions which
                // can be triggered by things outside of our app, for example via Google Assistant

                // Links our video player with this Leanback video playback fragment
                val playerAdapter = LeanbackPlayerAdapter(
                    requireContext(), exoPlayer, PLAYER_UPDATE_INTERVAL_MILLIS
                )

                // Enables pass-through of transport controls to our player instance
                playerGlue = MediaPlayerGlue(requireContext(), playerAdapter).apply {
                    host = VideoSupportFragmentGlueHost(this@PlayerFragmentTv)
                    title = "${data?.card?.name} - Episode ${data?.episodeIndex!! + 1}"


                    // Adds playback state listeners
                    addPlayerCallback(object : PlaybackGlue.PlayerCallback() {

                        override fun onPreparedStateChanged(glue: PlaybackGlue?) {
                            super.onPreparedStateChanged(glue)
                            if (glue?.isPrepared == true) {
                                // When playback is ready, skip to last known position
                                val startingPosition = 0L//metadata.playbackPositionMillis ?: 0
                                Log.d(TAG, "Setting starting playback position to $startingPosition")
                                seekTo(startingPosition)
                            }
                        }

                        override fun onPlayCompleted(glue: PlaybackGlue?) {
                            super.onPlayCompleted(glue)

                            // Don't forget to remove irrelevant content from the continue watching row
                            //TvLauncherUtils.removeFromWatchNext(requireContext(), args.metadata)

                        }
                    })
                    // Begins playback automatically
                    playWhenPrepared()
                    savePos()

                    // Displays the current item's metadata
                    //setMetadata(metadata)
                }
                // Setup the fragment adapter with our player glue presenter
                adapter = ArrayObjectAdapter(playerGlue.playbackRowPresenter).apply {
                    add(playerGlue.controlsRow)
                }

                // Adds key listeners
                playerGlue.host.setOnKeyInterceptListener { view, keyCode, event ->

                    // Early exit: if the controls overlay is visible, don't intercept any keys
                    if (playerGlue.host.isControlsOverlayVisible) return@setOnKeyInterceptListener false

                    //  This workaround is necessary for navigation library to work with
                    //  Leanback's [PlaybackSupportFragment]
                    if (!playerGlue.host.isControlsOverlayVisible &&
                        keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN
                    ) {
                        /*val navController = Navigation.findNavController(
                                requireActivity(), R.id.fragment_container)
                        navController.currentDestination?.id?.let { navController.popBackStack(it, true) }*/
                        savePos()
                        activity?.onBackPressed()
                        return@setOnKeyInterceptListener true
                    }

                    // Skips ahead when user presses DPAD_RIGHT
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                        playerGlue.skipForward()
                        preventControlsOverlay(playerGlue)
                        return@setOnKeyInterceptListener true
                    }

                    // Rewinds when user presses DPAD_LEFT
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                        playerGlue.skipBackward()
                        preventControlsOverlay(playerGlue)
                        return@setOnKeyInterceptListener true
                    }

                    false
                }
            }
        }
        isLoadingNextEpisode = false
    }

    private fun getCurrentEpisode(): ShiroApi.ShiroEpisodes? {
        return data?.card?.episodes?.getOrNull(data?.episodeIndex!!)//data?.card!!.cdnData.seasons.getOrNull(data?.seasonIndex!!)?.episodes?.get(data?.episodeIndex!!)
    }

    private fun loadAndPlay() {
        // Cached, first is index, second is links
        thread {
            if (!(sources.first == data?.episodeIndex && data?.episodeIndex != null)) {
                getCurrentEpisode()?.videos?.getOrNull(0)?.video_id?.let {
                    loadLinks(
                        it,
                        false,
                        callback = ::linkLoaded
                    )
                }
            }
            activity?.runOnUiThread {
                initPlayerIfPossible()
            }
        }
    }

    private fun linkLoaded(link: ExtractorLink) {
        println(" LINK ADDED ${link.name}")
        extractorLinks.add(link)
        sources = Pair(data?.episodeIndex, extractorLinks.sortedBy { -it.quality }.distinctBy { it.url })
        // Quickstart
        /*if (link.name == "Shiro") {
            activity?.runOnUiThread {
                initPlayerIfPossible(link)
            }
        }*/
    }

    private fun initPlayerIfPossible(link: ExtractorLink? = null) {
        if (!isCurrentlyPlaying) {
            initPlayer(link)
        }
    }

    private fun getCurrentUrl(): ExtractorLink? {
        val index = maxOf(sources.second?.indexOf(selectedSource) ?: -1, 0)
        println("SOURcES ${sources.second}")
        return sources.second?.getOrNull(index)
    }

    private fun getCurrentTitle(): String {
        if (data?.title != null) return data?.title!!

        val isMovie: Boolean = data?.card!!.episodes!!.size == 1 && data?.card?.status == "finished"
        // data?.card!!.cdndata?.seasons.size == 1 && data?.card!!.cdndata?.seasons[0].episodes.size == 1
        var preTitle = ""
        if (!isMovie) {
            preTitle = "Episode ${data?.episodeIndex!! + 1} · "
        }
        // Replaces with "" if it's null
        return preTitle + data?.card?.name
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //dataString = activity?.intent?.getSerializableExtra(DetailsActivityTV.MOVIE) as String
        //playbackPosition = activity?.intent?.getSerializableExtra(DetailsActivityTV.PLAYERPOS) as? Long ?: 0L
        //data = mapper.readValue<ShiroApi.AnimePageData>(dataString!!)
        mediaSession = MediaSessionCompat(requireContext(), getString(R.string.app_name))
        mediaSessionConnector = MediaSessionConnector(mediaSession)
    }

    /** Workaround used to prevent controls overlay from showing and taking focus */
    private fun preventControlsOverlay(playerGlue: MediaPlayerGlue) = view?.postDelayed({
        playerGlue.host.showControlsOverlay(false)
        playerGlue.host.hideControlsOverlay(false)
    }, 10)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(Color.BLACK)
        backgroundType = PlaybackSupportFragment.BG_NONE
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }


    override fun onStart() {
        super.onStart()
        if (data != null && data?.episodeIndex != null) {
            val pro = getViewPosDur(data!!.slug, data?.episodeIndex!!)
            if (pro.pos > 0 && pro.dur > 0 && (pro.pos * 100 / pro.dur) < 95) { // UNDER 95% RESUME
                playbackPosition = pro.pos
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // Disables ssl check
        val sslContext: SSLContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(SSLTrustManager()), SecureRandom())
        sslContext.createSSLEngine()
        HttpsURLConnection.setDefaultHostnameVerifier { _: String, _: SSLSession ->
            true
        }
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)

        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
        if (this::exoPlayer.isInitialized) {
            mediaSessionConnector.setPlayer(exoPlayer)
        }
        mediaSession.isActive = true
        hasBeenInPlayer = true
        isInPlayer = true
        onPlayerNavigated.invoke(true)
        loadAndPlay()
        // Kick off metadata update task which runs periodically in the main thread
        //view?.postDelayed(updateMetadataTask, METADATA_UPDATE_INTERVAL_MILLIS)
    }

    /**
     * Deactivates and removes callbacks from [MediaSessionCompat] since the [Player] instance is
     * destroyed in onStop and required metadata could be missing.
     */
    override fun onPause() {
        super.onPause()

        if (this::playerGlue.isInitialized) {
            playerGlue.pause()
        }
        mediaSession.isActive = false
        mediaSessionConnector.setPlayer(null)

        /*view?.post {
            // Launch metadata update task one more time as the fragment becomes paused to ensure
            //  that we have the most up-to-date information
            updateMetadataTask.run()

            // Cancel all future metadata update tasks
            view?.removeCallbacks(updateMetadataTask)
        }*/
    }

    /** Do all final cleanup in onDestroy */
    override fun onDestroy() {
        super.onDestroy()
        savePos()
        releasePlayer()
        mediaSession.release()
        onPlayerNavigated.invoke(false)
        isInPlayer = false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        savePos()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        arguments?.getString(DATA)?.let {
            data = mapper.readValue(it, PlayerData::class.java)
        }
    }

    companion object {
        const val DATA = "data"

        private val TAG = PlayerFragmentTv::class.java.simpleName

        /** How often the player refreshes its views in milliseconds */
        private const val PLAYER_UPDATE_INTERVAL_MILLIS: Int = 100

        /** Time between metadata updates in milliseconds */
        private val METADATA_UPDATE_INTERVAL_MILLIS: Long = TimeUnit.SECONDS.toMillis(10)

        /** Default time used when skipping playback in milliseconds */
        private val SKIP_PLAYBACK_MILLIS: Long = TimeUnit.SECONDS.toMillis(10)

        private val SKIP_OP_MILLIS: Long = TimeUnit.SECONDS.toMillis(85)
        var isInPlayer: Boolean = false

        fun newInstance(data: PlayerData) =
            PlayerFragmentTv().apply {
                arguments = Bundle().apply {
                    //println(data)
                    putString(DATA, mapper.writeValueAsString(data))
                }
            }
    }
}
