package com.lagradost.shiro.ui.tv

import android.net.Uri
import android.os.Bundle
import androidx.leanback.app.VideoSupportFragment
import androidx.leanback.app.VideoSupportFragmentGlueHost
import androidx.leanback.media.MediaPlayerAdapter
import androidx.leanback.media.PlaybackTransportControlGlue
import androidx.leanback.widget.PlaybackControlsRow
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.shiro.DataStore.mapper
import com.lagradost.shiro.ShiroApi
import com.lagradost.shiro.ShiroApi.Companion.getVideoLink

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

        playerAdapter.setDataSource(Uri.parse(data.episodes?.get(0)?.let { getVideoLink(it._id) }))
    }

    override fun onPause() {
        super.onPause()
        mTransportControlGlue.pause()
    }
}