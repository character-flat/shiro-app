package com.lagradost.shiro.ui.downloads

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.ContentLoadingProgressBar
import com.google.android.material.button.MaterialButton
import com.lagradost.shiro.R
import com.lagradost.shiro.utils.AppUtils.popupMenuNoIcons
import com.lagradost.shiro.utils.Coroutines
import com.lagradost.shiro.utils.IDisposable
import com.lagradost.shiro.utils.ShiroApi
import com.lagradost.shiro.utils.VideoDownloadManager


interface DownloadButtonViewHolder {
    var downloadButton: EasyDownloadButton?
    fun reattachDownloadButton()
}

data class DownloadClickEvent(val action: Int, val data: EasyDownloadButton.IMinimumData)

data class AllDataWithId(
    override val id: Int,
    val slug: String,
    val title: String,
    val episode: Int, // Starting from 0
    val episodeOffset: Int,
//    val anilistID: Int?,
//    val malID: Int?,
    val fillerEpisodes: HashMap<Int, Boolean>?,
    val data: ShiroApi.Companion.AnimePageNewData?

) : EasyDownloadButton.IMinimumData

class EasyDownloadButton : IDisposable {
    interface IMinimumData {
        val id: Int
    }

    override fun dispose() {
        try {
            downloadProgressEventListener?.let { VideoDownloadManager.downloadProgressEvent -= it }
            downloadStatusEventListener?.let { VideoDownloadManager.downloadStatusEvent -= it }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    var downloadProgressEventListener: ((Triple<Int, Long, Long>) -> Unit)? = null
    var downloadStatusEventListener: ((Pair<Int, VideoDownloadManager.DownloadType>) -> Unit)? = null

    fun setUpMaterialButton(
        setupCurrentBytes: Long?,
        setupTotalBytes: Long?,
        progressBar: ContentLoadingProgressBar,
        downloadButton: MaterialButton,
        textView: TextView?,
        data: IMinimumData,
        clickCallback: (DownloadClickEvent) -> Unit,
    ) {
        setUpDownloadButton(setupCurrentBytes, setupTotalBytes, progressBar, textView, data, downloadButton, {
            downloadButton.setIconResource(it.first)
            downloadButton.text = it.second
        }, clickCallback)
    }

    fun setUpButton(
        setupCurrentBytes: Long?,
        setupTotalBytes: Long?,
        progressBar: ContentLoadingProgressBar,
        downloadImage: ImageView,
        textView: TextView?,
        data: IMinimumData,
        clickCallback: (DownloadClickEvent) -> Unit,
    ) {
        setUpDownloadButton(setupCurrentBytes, setupTotalBytes, progressBar, textView, data, downloadImage, {
            downloadImage.setImageResource(it.first)
        }, clickCallback)
    }

    private fun setUpDownloadButton(
        setupCurrentBytes: Long?,
        setupTotalBytes: Long?,
        progressBar: ContentLoadingProgressBar,
        textView: TextView?,
        data: IMinimumData,
        downloadView: View,
        downloadImageChangeCallback: (Pair<Int, String>) -> Unit,
        clickCallback: (DownloadClickEvent) -> Unit,
    ) {
        var lastState: VideoDownloadManager.DownloadType? = null
        var currentBytes = setupCurrentBytes ?: 0
        var totalBytes = setupTotalBytes ?: 0
        var needImageUpdate = false

        fun changeDownloadImage(state: VideoDownloadManager.DownloadType) {
            lastState = state
            if (currentBytes <= 0) needImageUpdate = true

            val img = if (currentBytes > 0) {
                when (state) {
                    VideoDownloadManager.DownloadType.IsPaused -> Pair(
                        R.drawable.ic_baseline_play_arrow_24,
                        "Download Paused"
                    )
                    VideoDownloadManager.DownloadType.IsDownloading -> Pair(R.drawable.netflix_pause, "Downloading")
                    else -> Pair(R.drawable.ic_baseline_delete_outline_24, "Downloaded")
                }
            } else {
                Pair(R.drawable.netflix_download, "Download")
            }
            downloadImageChangeCallback.invoke(img)
        }

        @SuppressLint("SetTextI18n")
        fun fixDownloadedBytes(setCurrentBytes: Long, setTotalBytes: Long, animate: Boolean) {
            currentBytes = setCurrentBytes
            totalBytes = setTotalBytes

            if (currentBytes == 0L) {
                changeDownloadImage(VideoDownloadManager.DownloadType.IsStopped)
                textView?.visibility = View.GONE
                progressBar.visibility = View.GONE
            } else {
                if (lastState == VideoDownloadManager.DownloadType.IsStopped) {
                    changeDownloadImage(VideoDownloadManager.getDownloadState(data.id))
                }
                textView?.visibility = View.VISIBLE
                progressBar.visibility = View.VISIBLE
                val currentMbString = "%.1f".format(setCurrentBytes / 1000000f)
                val totalMbString = "%.1f".format(setTotalBytes / 1000000f)

                textView?.text =
                    "${currentMbString}MB / ${totalMbString}MB"

                progressBar.let { bar ->
                    bar.max = (setTotalBytes / 1000).toInt()

                    if (animate) {
                        val animation: ObjectAnimator = ObjectAnimator.ofInt(
                            bar,
                            "progress",
                            bar.progress,
                            (setCurrentBytes / 1000).toInt()
                        )
                        animation.duration = 500
                        animation.setAutoCancel(true)
                        animation.interpolator = DecelerateInterpolator()
                        animation.start()
                    } else {
                        bar.progress = (setCurrentBytes / 1000).toInt()
                    }
                }
            }
        }

        fixDownloadedBytes(currentBytes, totalBytes, false)
        changeDownloadImage(VideoDownloadManager.getDownloadState(data.id))

        downloadProgressEventListener = { downloadData: Triple<Int, Long, Long> ->
            if (data.id == downloadData.first) {
                if (downloadData.second != currentBytes || downloadData.third != totalBytes) { // TO PREVENT WASTING UI TIME
                    Coroutines.runOnMainThread {
                        fixDownloadedBytes(downloadData.second, downloadData.third, true)
                    }
                }
            }
        }

        downloadStatusEventListener = { downloadData: Pair<Int, VideoDownloadManager.DownloadType> ->
            if (data.id == downloadData.first) {
                if (lastState != downloadData.second || needImageUpdate) { // TO PREVENT WASTING UI TIME
                    Coroutines.runOnMainThread {
                        changeDownloadImage(downloadData.second)
                    }
                }
            }
        }

        downloadProgressEventListener?.let { VideoDownloadManager.downloadProgressEvent += it }
        downloadStatusEventListener?.let { VideoDownloadManager.downloadStatusEvent += it }

        downloadView.setOnClickListener {
            if (currentBytes <= 0) {
                clickCallback.invoke(DownloadClickEvent(DOWNLOAD_ACTION_DOWNLOAD, data))
            } else {
                val list = arrayListOf(
                    Pair(DOWNLOAD_ACTION_PLAY_FILE, R.string.popup_play_file),
                    Pair(DOWNLOAD_ACTION_DELETE_FILE, R.string.popup_delete_file),
                )

                // DON'T RESUME A DOWNLOADED FILE lastState != VideoDownloadManager.DownloadType.IsDone &&
                if ((currentBytes * 100 / totalBytes) < 98) {
                    list.add(
                        if (lastState == VideoDownloadManager.DownloadType.IsDownloading)
                            Pair(DOWNLOAD_ACTION_PAUSE_DOWNLOAD, R.string.popup_pause_download)
                        else
                            Pair(DOWNLOAD_ACTION_RESUME_DOWNLOAD, R.string.popup_resume_download)
                    )
                }

                it.popupMenuNoIcons(
                    list
                ) {
                    clickCallback.invoke(DownloadClickEvent(itemId, data))
                }
            }
        }
    }
}
