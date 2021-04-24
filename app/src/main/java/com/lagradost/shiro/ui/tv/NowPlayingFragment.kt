package com.lagradost.shiro.ui.tv

import com.lagradost.shiro.R

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
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.leanback.app.PlaybackSupportFragment
import androidx.leanback.app.VideoSupportFragment
import androidx.leanback.app.VideoSupportFragmentGlueHost
import androidx.leanback.media.PlaybackGlue
import androidx.leanback.media.PlaybackTransportControlGlue
import androidx.leanback.widget.Action
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.PlaybackControlsRow
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.ext.leanback.LeanbackPlayerAdapter
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import com.google.android.exoplayer2.util.MimeTypes
import com.lagradost.shiro.ui.PlayerData
import com.lagradost.shiro.ui.PlayerFragment.Companion.onLeftPlayer
import com.lagradost.shiro.ui.result.ResultFragment
import com.lagradost.shiro.utils.AppApi.setViewPosDur
import com.lagradost.shiro.utils.DataStore.mapper
import com.lagradost.shiro.utils.ShiroApi
import com.lagradost.shiro.utils.ShiroApi.Companion.USER_AGENT
import com.lagradost.shiro.utils.ShiroApi.Companion.getVideoLink
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.min


/** A fragment representing the current metadata item being played */
class NowPlayingFragment : VideoSupportFragment() {

    /** AndroidX navigation arguments */

    //private lateinit var player: SimpleExoPlayer
    //private lateinit var database: TvMediaDatabase

    /** Allows interaction with transport controls, volume keys, media buttons  */
    private lateinit var mediaSession: MediaSessionCompat

    /** Glue layer between the player and our UI */
    private lateinit var playerGlue: MediaPlayerGlue
    private var currentUrl: String? = null
    private lateinit var exoPlayer: SimpleExoPlayer

    var dataString: String? = null
    var data: ShiroApi.AnimePageData? = null
    var episodeIndex: Int? = null

    // Prevent clicking next episode button multiple times
    private var isLoadingNextEpisode = false

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

            // Append rewind and fast forward actions to our player, keeping the play/pause actions
            // created by default by the glue
            adapter.add(actionRewind)
            adapter.add(actionFastForward)
            adapter.add(actionSkipOp)
            if (episodeIndex!! + 1 < data?.episodes?.size!!) {
                adapter.add(actionNextEpisode)
            }
            //adapter.add(actionClosedCaptions)
        }

        override fun onActionClicked(action: Action) = when (action) {
            actionRewind -> skipBackward()
            actionFastForward -> skipForward()
            actionSkipOp -> skipForward(SKIP_OP_MILLIS)
            actionNextEpisode -> {
                if (episodeIndex != null && !isLoadingNextEpisode) {
                    playerGlue.host.hideControlsOverlay(false)
                    isLoadingNextEpisode = true
                    episodeIndex = minOf(episodeIndex!! + 1, data?.episodes?.size!! - 1)

                    releasePlayer()
                    initPlayer()
                } else {
                }
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
        if (this::exoPlayer.isInitialized) {
            exoPlayer.release()
        }
    }

    private fun savePos() {
        println("Savepos")
        if (this::exoPlayer.isInitialized) {
            if (((data?.slug != null

                        && episodeIndex != null) || data != null)
                && exoPlayer.duration > 0 && exoPlayer.currentPosition > 0
            ) {
                val playerData = PlayerData(
                    data!!.name, currentUrl, episodeIndex, 0, data, 0L, data!!.slug
                )
                setViewPosDur(playerData, exoPlayer.currentPosition, exoPlayer.duration)
            }
        }
    }

    private fun initPlayer() {
        backgroundType = PlaybackSupportFragment.BG_NONE
        thread {
            currentUrl = getCurrentUrl()
            if (currentUrl == null) {
                activity?.let {
                    it.runOnUiThread {
                        Toast.makeText(it, "Error getting link", Toast.LENGTH_LONG).show()
                        it.onBackPressed()
                    }
                }
                return@thread
            }
            val isOnline =
                currentUrl?.startsWith("https://") == true || currentUrl?.startsWith("http://") == true
            //database = TvMediaDatabase.getInstance(requireContext())
            //val metadata = args.metadata

            // Adds this program to the continue watching row, in case the user leaves before finishing
            /*val programUri = TvLauncherUtils.upsertWatchNext(requireContext(), metadata)
            if (programUri != null) lifecycleScope.launch(Dispatchers.IO) {
                database.metadata().update(metadata.apply { watchNext = true })
            }*/

            val _mediaItem = MediaItem.Builder()
                //Replace needed for android 6.0.0  https://github.com/google/ExoPlayer/issues/5983
                .setMimeType(MimeTypes.APPLICATION_MP4)

            class CustomFactory : DataSource.Factory {
                override fun createDataSource(): DataSource {
                    return if (isOnline) {
                        val dataSource = DefaultHttpDataSourceFactory(USER_AGENT).createDataSource()
                        /*FastAniApi.currentHeaders?.forEach {
                            dataSource.setRequestProperty(it.key, it.value)
                        }*/
                        dataSource.setRequestProperty("Referer", "https://cherry.subsplea.se/")
                        dataSource
                    } else {
                        DefaultDataSourceFactory(requireContext(), USER_AGENT).createDataSource()
                    }
                }
            }

            if (isOnline) {
                _mediaItem.setUri(currentUrl)
            } else {
                currentUrl?.let {
                    _mediaItem.setUri(Uri.fromFile(File(it)))
                }
            }

            val mediaItem = _mediaItem.build()
            val trackSelector = DefaultTrackSelector(requireContext())
            // Disable subtitles
            trackSelector.parameters = DefaultTrackSelector.ParametersBuilder(requireContext())
                .setRendererDisabled(C.TRACK_TYPE_VIDEO, true)
                .setRendererDisabled(C.TRACK_TYPE_TEXT, true)
                .setDisabledTextTrackSelectionFlags(C.TRACK_TYPE_TEXT)
                .clearSelectionOverrides()
                .build()

            val _exoPlayer =
                SimpleExoPlayer.Builder(this.requireContext())
                    .setTrackSelector(trackSelector)

            _exoPlayer.setMediaSourceFactory(DefaultMediaSourceFactory(CustomFactory()))

            activity?.runOnUiThread {

                exoPlayer = _exoPlayer.build().apply {
                    //playWhenReady = isPlayerPlaying
                    //seekTo(currentWindow, playbackPosition)
                    setMediaItem(mediaItem, false)
                    prepare()
                }

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
                    host = VideoSupportFragmentGlueHost(this@NowPlayingFragment)
                    title = "${data?.name} - Episode ${episodeIndex!! + 1}"


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

                    // TODO(owahltinez): This workaround is necessary for navigation library to work with
                    //  Leanback's [PlaybackSupportFragment]
                    if (!playerGlue.host.isControlsOverlayVisible &&
                        keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN
                    ) {
                        /*val navController = Navigation.findNavController(
                                requireActivity(), R.id.fragment_container)
                        navController.currentDestination?.id?.let { navController.popBackStack(it, true) }*/
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
        return data?.episodes?.get(episodeIndex!!)//data?.card!!.cdnData.seasons.getOrNull(data?.seasonIndex!!)?.episodes?.get(data?.episodeIndex!!)
    }

    private fun getCurrentUrl(): String? {
        //println("MAN::: " + data?.url)
        //if (data?.url != null) return data?.url!!
        return getCurrentEpisode()?.videos?.getOrNull(0)?.video_id?.let { getVideoLink(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dataString =
            activity?.intent?.getSerializableExtra(DetailsActivityTV.MOVIE) as String
        data = mapper.readValue<ShiroApi.AnimePageData>(dataString!!)
        episodeIndex = activity?.intent?.getSerializableExtra("position") as Int
        mediaSession = MediaSessionCompat(requireContext(), getString(R.string.app_name))
        mediaSessionConnector = MediaSessionConnector(mediaSession)
        initPlayer()
    }

    /** Workaround used to prevent controls overlay from showing and taking focus */
    private fun preventControlsOverlay(playerGlue: MediaPlayerGlue) = view?.postDelayed({
        playerGlue.host.showControlsOverlay(false)
        playerGlue.host.hideControlsOverlay(false)
    }, 10)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(Color.BLACK)
    }

    override fun onResume() {
        super.onResume()

        if (this::exoPlayer.isInitialized) {
            mediaSessionConnector.setPlayer(exoPlayer)
        }
        mediaSession.isActive = true

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
        mediaSession.release()
        onLeftPlayer.invoke(true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        savePos()
    }

    companion object {
        private val TAG = NowPlayingFragment::class.java.simpleName

        /** How often the player refreshes its views in milliseconds */
        private const val PLAYER_UPDATE_INTERVAL_MILLIS: Int = 100

        /** Time between metadata updates in milliseconds */
        private val METADATA_UPDATE_INTERVAL_MILLIS: Long = TimeUnit.SECONDS.toMillis(10)

        /** Default time used when skipping playback in milliseconds */
        private val SKIP_PLAYBACK_MILLIS: Long = TimeUnit.SECONDS.toMillis(10)

        private val SKIP_OP_MILLIS: Long = TimeUnit.SECONDS.toMillis(85)
    }
}
