package com.lagradost.shiro.ui.tv

import android.net.Uri
import android.os.Bundle
import androidx.leanback.app.VideoSupportFragment
import androidx.leanback.app.VideoSupportFragmentGlueHost
import androidx.leanback.media.MediaPlayerAdapter
import androidx.leanback.media.PlaybackTransportControlGlue
import androidx.leanback.widget.PlaybackControlsRow
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.shiro.utils.DataStore.mapper
import com.lagradost.shiro.utils.ShiroApi
import com.lagradost.shiro.utils.ShiroApi.Companion.getVideoLink
import kotlin.concurrent.thread

/** Handles video playback with media controls. */
class PlaybackVideoFragment : VideoSupportFragment() {

    private lateinit var mTransportControlGlue: PlaybackTransportControlGlue<MediaPlayerAdapter>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dataString =
            activity?.intent?.getSerializableExtra(DetailsActivityTV.MOVIE) as String
        val data = mapper.readValue<ShiroApi.AnimePageData>(dataString)
        val episode = activity?.intent?.getSerializableExtra("position") as Int

        val glueHost = VideoSupportFragmentGlueHost(this@PlaybackVideoFragment)
        val playerAdapter = MediaPlayerAdapter(activity)
        playerAdapter.setRepeatAction(PlaybackControlsRow.RepeatAction.INDEX_NONE)

        mTransportControlGlue = PlaybackTransportControlGlue(activity, playerAdapter)
        mTransportControlGlue.host = glueHost
        mTransportControlGlue.title = data.name
        mTransportControlGlue.subtitle = data.synopsis
        mTransportControlGlue.playWhenPrepared()

        //mTransportControlGlue.seekProvider =

        thread {
            val url = data.episodes?.get(episode)?.videos?.getOrNull(0)?.let { getVideoLink(it.video_id) }?.get(0)?.url
            println("$url $episode")
            if (url != null) {
                playerAdapter.setDataSource(Uri.parse(url))
            }
        }
    }

    override fun onPause() {
        super.onPause()
        mTransportControlGlue.pause()
    }




}