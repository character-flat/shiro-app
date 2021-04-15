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

/** Handles video playback with media controls. */
class PlaybackVideoFragment : VideoSupportFragment() {

    private lateinit var mTransportControlGlue: PlaybackTransportControlGlue<MediaPlayerAdapter>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val str =
            activity?.intent?.getSerializableExtra(DetailsActivityTV.MOVIE) as String
        val data = mapper.readValue<ShiroApi.AnimePageData>(str)
        val glueHost = VideoSupportFragmentGlueHost(this@PlaybackVideoFragment)
        val playerAdapter = MediaPlayerAdapter(activity)
        playerAdapter.setRepeatAction(PlaybackControlsRow.RepeatAction.INDEX_NONE)

        mTransportControlGlue = PlaybackTransportControlGlue(activity, playerAdapter)
        mTransportControlGlue.host = glueHost
        mTransportControlGlue.title = data.name
        mTransportControlGlue.subtitle = data.synopsis
        mTransportControlGlue.playWhenPrepared()

        val url = data.episodes?.get(0)?.videos?.get(0)?.let { getVideoLink(it.video_id) }
        println(url)

        playerAdapter.setDataSource(Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"))
    }

    override fun onPause() {
        super.onPause()
        mTransportControlGlue.pause()
    }
}