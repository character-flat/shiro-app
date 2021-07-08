package com.lagradost.shiro.ui.result

import android.content.res.ColorStateList
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.marginBottom
import androidx.core.view.marginLeft
import androidx.core.view.marginTop
import androidx.core.view.updateMarginsRelative
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import com.google.android.exoplayer2.ext.cast.CastPlayer
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaStatus.REPEAT_MODE_REPEAT_SINGLE
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.common.images.WebImage
import com.jaredrummler.cyanea.Cyanea
import com.lagradost.shiro.R
import com.lagradost.shiro.ui.MainActivity.Companion.isDonor
import com.lagradost.shiro.ui.downloads.DownloadFragment.Companion.downloadsUpdated
import com.lagradost.shiro.ui.result.ResultFragment.Companion.resultViewModel
import com.lagradost.shiro.ui.toPx
import com.lagradost.shiro.utils.*
import com.lagradost.shiro.utils.AppUtils.dubbify
import com.lagradost.shiro.utils.AppUtils.getColorFromAttr
import com.lagradost.shiro.utils.AppUtils.getLatestSeenEpisode
import com.lagradost.shiro.utils.AppUtils.getTextColor
import com.lagradost.shiro.utils.AppUtils.getViewKey
import com.lagradost.shiro.utils.AppUtils.getViewPosDur
import com.lagradost.shiro.utils.AppUtils.isCastApiAvailable
import com.lagradost.shiro.utils.AppUtils.loadPlayer
import com.lagradost.shiro.utils.AppUtils.settingsManager
import com.lagradost.shiro.utils.ShiroApi.Companion.getFullUrlCdn
import com.lagradost.shiro.utils.ShiroApi.Companion.getVideoLink
import com.lagradost.shiro.utils.VideoDownloadManager.downloadQueue
import kotlinx.android.synthetic.main.episode_result_compact.view.*
import kotlinx.android.synthetic.main.fragment_results.view.*
import org.json.JSONObject
import kotlin.concurrent.thread

class EpisodeAdapter(
    val activity: FragmentActivity,
    val data: ShiroApi.AnimePageData,
    private val resView: View,
    private val parentPosition: Int,
    rangeStart: Int? = null,
    rangeStop: Int? = null,
    private val isFiller: HashMap<Int, Boolean>? = null,
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    val stop = rangeStop ?: data.episodes!!.size
    val start = rangeStart ?: 0
    var episodes = data.episodes!!.subList(start, stop)
    private var prevFocus: Int? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        //resView.spanCount = if (isDonor) 2 else 3
        return CardViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.episode_result_compact, parent, false),
            activity,
            resView,
            data,
            start,
            parentPosition,
            isFiller,
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        //lastSelectedEpisode = 0
        holder.itemView.setOnFocusChangeListener { _, _ ->
            //lastSelectedEpisode = position
            if (prevFocus != null) {
                if (kotlin.math.abs(position - prevFocus!!) > 3 * 2) {
                    this.resView.episodes_res_view.layoutManager?.scrollToPosition(0)
                }
            }
            prevFocus = position
            //updateFocusPositions(holder, hasFocus, position)
        }

        when (holder) {
            is CardViewHolder -> {
                holder.bind(position)
            }
        }
    }

    override fun getItemCount(): Int {
        return episodes.size
    }

    class CardViewHolder
    constructor(
        itemView: View, val activity: FragmentActivity, private val resView: View,
        val data: ShiroApi.AnimePageData, val start: Int, private val parentPosition: Int,
        private val isFiller: HashMap<Int, Boolean>? = null
    ) :
        RecyclerView.ViewHolder(itemView) {
        val card: LinearLayout = itemView.episode_result_root

        companion object {
            val anilistID: Int?
                get() = resultViewModel?.currentAniListId?.value
            val malID: Int?
                get() = resultViewModel?.currentMalId?.value
        }

        // Downloads is only updated when re-bound!
        fun bind(position: Int) {
            if (position == 0) {
                card.requestFocus()
            }
            if (position == 0 || position == 1) {
                card.setOnFocusChangeListener { _: View, focused: Boolean ->
                    resView.isFocusable = focused
                }
            }
            val episodeOffset = if (data.episodes?.filter { it.episode_number == 0 }.isNullOrEmpty()) 0 else -1


            //println("START $start pos $position")
            val episodePos = start + position
            val key = getViewKey(data.slug, episodePos)

            // Because the view is recycled
            card.progressBar.visibility = GONE
            card.cardPauseIcon.visibility = GONE
            card.cardRemoveIcon.visibility = GONE

            // Sets loading icon on queued items
            if (downloadQueue.any { it.item.ep.episode == episodePos + 1 + episodeOffset }) {
                card.cdi_loading.visibility = VISIBLE
                card.cdi.visibility = GONE
            } else {
                card.cdi_loading.visibility = GONE
                card.cdi.visibility = VISIBLE
            }

            if (isDonor) {
                card.cdi.setOnClickListener {
                    card.cdi_loading.visibility = VISIBLE
                    card.cdi.visibility = GONE
                    thread {
                        val sources = data.episodes?.get(episodePos)?.videos?.getOrNull(0)?.video_id.let { video_id ->
                            getVideoLink(
                                video_id!!
                            )
                        }?.filter { !it.isM3u8 }
                        activity.runOnUiThread {
                            if (!sources.isNullOrEmpty()) {
                                // card.cdi.visibility = VISIBLE
                                // card.cdi_loading.visibility = GONE
                                if (settingsManager?.getBoolean("pick_downloads", false) == true) {
                                    lateinit var dialog: AlertDialog
                                    val sourcesTexts = sources.map { it.name }
                                    val builder = AlertDialog.Builder(activity, R.style.AlertDialogCustom)
                                    builder.setTitle("Pick source")
                                    builder.setSingleChoiceItems(sourcesTexts.toTypedArray(), 0) { _, which ->
                                        DownloadManager.downloadEpisode(
                                            activity,
                                            DownloadManager.DownloadInfo(
                                                episodePos,
                                                data,
                                                anilistID,
                                                malID,
                                                isFiller
                                            ),
                                            listOf(sources[which])
                                        )

                                        dialog.dismiss()
                                    }
                                    dialog = builder.create()
                                    dialog.show()
                                } else {
                                    DownloadManager.downloadEpisode(
                                        activity,
                                        DownloadManager.DownloadInfo(
                                            episodePos,
                                            data,
                                            anilistID,
                                            malID,
                                            isFiller
                                        ),
                                        sources
                                    )
                                }
                            } else {
                                Toast.makeText(
                                    activity,
                                    "Download failed for episode ${episodePos + 1 + episodeOffset}",
                                    Toast.LENGTH_SHORT
                                ).show()
                                card.cdi.visibility = VISIBLE
                                card.cdi_loading.visibility = GONE
                            }
                        }
                    }
                }
            } else {
                card.cdi.visibility = GONE
                val param = card.cardTitle.layoutParams as ViewGroup.MarginLayoutParams
                param.updateMarginsRelative(
                    card.cardTitle.marginLeft,
                    card.cardTitle.marginTop,
                    10.toPx,
                    card.cardTitle.marginBottom
                )
                card.cardTitle.layoutParams = param
            }

            itemView.episode_result_root.setOnClickListener {
                if (activity.isCastApiAvailable()) {
                    val castContext = CastContext.getSharedInstance(activity.applicationContext)
                    println("SSTATE: " + castContext.castState + "<<")
                    if (castContext.castState == CastState.CONNECTED) {
                        castEpisode(data, episodePos)
                    } else {
                        thread {
                            activity.loadPlayer(episodePos, 0L, data, anilistID, malID, isFiller)
                        }
                    }
                } else {
                    thread {
                        activity.loadPlayer(episodePos, 0L, data, anilistID, malID, isFiller)
                    }
                }
            }

            card.episode_result_root.setOnLongClickListener {
                val keyNormal = getViewKey(data.slug.dubbify(false), episodePos)
                val keyDubbed = getViewKey(data.slug.dubbify(true), episodePos)

                if (DataStore.containsKey(VIEWSTATE_KEY, keyNormal) || DataStore.containsKey(
                        VIEWSTATE_KEY,
                        keyDubbed
                    )
                ) {
                    DataStore.removeKey(VIEWSTATE_KEY, keyNormal)
                    DataStore.removeKey(VIEWSTATE_KEY, keyDubbed)
                } else {
                    DataStore.setKey(VIEWSTATE_KEY, key, System.currentTimeMillis())
                }
                // Hack, but works
                (activity.findViewById<RecyclerView>(R.id.episodes_res_view).adapter as MasterEpisodeAdapter).notifyItemChanged(
                    parentPosition
                )
                //resView.adapter?.notifyDataSetChanged()
                //setCardViewState(key, episodePos)
                return@setOnLongClickListener true
            }

            val isCurrentFiller = if (isFiller != null) {
                if (isFiller.containsKey(episodePos + 1)) {
                    isFiller[episodePos + 1] ?: false
                } else false
            } else false

            val title = "Episode ${episodePos + 1 + episodeOffset}" + (if (isCurrentFiller) " (Filler)" else "")
            card.cardTitle.text = title

            setCardViewState(episodePos)

            val pro = getViewPosDur(data.slug, episodePos)
            //println("DURPOS:" + epNum + "||" + pro.pos + "|" + pro.dur)
            if (pro.dur > 0 && pro.pos > 0) {
                var progress: Int = (pro.pos * 100L / pro.dur).toInt()
                if (progress < 5) {
                    progress = 5
                } else if (progress > 95) {
                    progress = 100
                }
                card.video_progress.alpha = 1f
                card.video_progress.progress = progress
            } else {
                card.video_progress.alpha = 0f
            }
            card.progressBar.progressTintList = ColorStateList.valueOf(Cyanea.instance.primary)
            fun updateIcon(megabytes: Int, fileInfo: VideoDownloadManager.DownloadedFileInfoResult?) {
                if (fileInfo == null) {
                    card.cdi.visibility = VISIBLE
                    card.progressBar.visibility = GONE
                    card.cardPauseIcon.visibility = GONE
                    card.cardRemoveIcon.visibility = GONE
                } else {
                    card.cdi.visibility = GONE
                    val megaBytesTotal = DownloadManager.convertBytesToAny(fileInfo.totalBytes, 0, 2.0).toInt()
                    if (megabytes + 0.1 >= megaBytesTotal) {
                        card.progressBar.visibility = GONE
                        card.cardPauseIcon.visibility = GONE
                        card.cardRemoveIcon.visibility = VISIBLE
                    } else {
                        card.progressBar.visibility = VISIBLE
                        card.cardRemoveIcon.visibility = GONE
                        card.cardPauseIcon.visibility = VISIBLE
                    }
                }
            }

            if (isDonor) {
                val internalId = (data.slug + "E${episodePos}").hashCode()
                val child = DataStore.getKey<DownloadManager.DownloadFileMetadata>(
                    DOWNLOAD_CHILD_KEY,
                    internalId.toString()
                )
                // ================ DOWNLOAD STUFF ================
                if (child != null) {
                    val fileInfo = VideoDownloadManager.getDownloadFileInfoAndUpdateSettings(activity, child.internalId)
                    if (fileInfo != null) {
                        val megaBytesTotal = DownloadManager.convertBytesToAny(fileInfo.totalBytes, 0, 2.0).toInt()
                        val localBytesTotal =
                            maxOf(DownloadManager.convertBytesToAny(fileInfo.fileLength, 0, 2.0).toInt(), 1)

                        println("FILE EXISTS:$episodePos")
                        fun deleteFile() {
                            if (VideoDownloadManager.deleteFileAndUpdateSettings(activity, child.internalId)) {
                                DataStore.removeKey(DOWNLOAD_CHILD_KEY, key)
                                activity.runOnUiThread {
                                    Toast.makeText(
                                        activity,
                                        "${child.videoTitle} E${child.episodeIndex + 1} deleted",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    updateIcon(0, null)
                                }
                                downloadsUpdated.invoke(true)
                            }
                        }

                        card.cardRemoveIcon.setOnClickListener {
                            val alertDialog: AlertDialog = activity.let {
                                val builder = AlertDialog.Builder(it, R.style.AlertDialogCustom)
                                builder.apply {
                                    setPositiveButton(
                                        "Delete"
                                    ) { _, _ ->
                                        deleteFile()
                                    }
                                    setNegativeButton(
                                        "Cancel"
                                    ) { _, _ ->
                                        // User cancelled the dialog
                                    }
                                }
                                // Set other dialog properties
                                builder.setTitle("Delete ${child.videoTitle} - E${child.episodeIndex + 1}")

                                // Create the AlertDialog
                                builder.create()
                            }
                            alertDialog.show()
                        }

                        card.cardTitle.text = title

                        //card.cardTitleExtra.text = "$localBytesTotal / $megaBytesTotal MB"

                        fun getDownload(): DownloadManager.DownloadInfo {
                            return DownloadManager.DownloadInfo(
                                child.episodeIndex,
                                data,
                                anilistID,
                                malID,
                                isFiller
                            )
                        }

                        fun getStatus(): Boolean { // IF CAN RESUME
                            return if (VideoDownloadManager.downloadStatus.containsKey(child.internalId)) {
                                VideoDownloadManager.downloadStatus[child.internalId] == VideoDownloadManager.DownloadType.IsPaused
                            } else {
                                true
                            }
                        }

                        fun setStatus(isPaused: Boolean) {
                            activity.runOnUiThread {
                                if (isPaused) {
                                    card.cardPauseIcon.setImageResource(R.drawable.netflix_play)
                                } else {
                                    card.cardPauseIcon.setImageResource(R.drawable.exo_icon_stop)
                                }
                            }
                        }

                        setStatus(true)
                        updateIcon(localBytesTotal, fileInfo)

                        card.cardPauseIcon.imageTintList = ColorStateList.valueOf(Cyanea.instance.primary)
                        card.cardPauseIcon.setOnClickListener { v ->
                            val ctw = ContextThemeWrapper(activity, R.style.PopupMenu)
                            val popup = PopupMenu(ctw, v)
                            if (getStatus()) {
                                popup.setOnMenuItemClickListener {
                                    when (it.itemId) {
                                        R.id.res_resumedload -> {
                                            val id = child.internalId//(data.slug + "E${episodePos}").hashCode()
                                            val pkg = VideoDownloadManager.getDownloadResumePackage(activity, id)

                                            if (pkg != null) {
                                                VideoDownloadManager.downloadFromResume(activity, pkg)
                                            }
                                        }
                                        R.id.res_stopdload -> {
                                            VideoDownloadManager.downloadEvent.invoke(
                                                Pair(
                                                    child.internalId,
                                                    VideoDownloadManager.DownloadActionType.Stop
                                                )
                                            )
                                            deleteFile()
                                        }
                                    }
                                    return@setOnMenuItemClickListener true
                                }
                                popup.inflate(R.menu.resume_menu)
                            } else {
                                popup.setOnMenuItemClickListener {
                                    when (it.itemId) {
                                        R.id.stop_pauseload -> {
                                            VideoDownloadManager.downloadEvent.invoke(
                                                Pair(
                                                    child.internalId,
                                                    VideoDownloadManager.DownloadActionType.Pause
                                                )
                                            )
                                        }
                                        R.id.stop_stopdload -> {
                                            VideoDownloadManager.downloadEvent.invoke(
                                                Pair(
                                                    child.internalId,
                                                    VideoDownloadManager.DownloadActionType.Stop
                                                )
                                            )
                                        }
                                    }
                                    return@setOnMenuItemClickListener true
                                }
                                popup.inflate(R.menu.stop_menu)
                            }
                            popup.show()
                        }

                        card.progressBar.progress = maxOf(minOf(localBytesTotal * 100 / megaBytesTotal, 100), 0)

                        VideoDownloadManager.downloadStatusEvent += {
                            if (it.first == child.internalId) {
                                val isPaused = it.second == VideoDownloadManager.DownloadType.IsPaused
                                setStatus(isPaused)
                            }
                        }

                        // This makes progress work but is incredibly laggy
                        /*DownloadManager.downloadEvent += {
                            activity.runOnUiThread {
                                if (it.downloadEvent.id == child.internalId) {
                                    (resView.episodes_res_view.adapter as EpisodeAdapter).notifyItemChanged(position)
                                }
                            }
                        }*/
                    }
                }
            }
        }

        private fun setCardViewState(episodePos: Int) {
            val keyNormal = getViewKey(data.slug.dubbify(false), episodePos)
            val keyDubbed = getViewKey(data.slug.dubbify(true), episodePos)
            if (DataStore.containsKey(VIEWSTATE_KEY, keyNormal) || DataStore.containsKey(VIEWSTATE_KEY, keyDubbed)) {
                val lastNormal = getLatestSeenEpisode(data.dubbify(false))
                val lastDubbed = getLatestSeenEpisode(data.dubbify(true))
                val last = if (lastDubbed.episodeIndex > lastNormal.episodeIndex) lastDubbed else lastNormal

                if (last.isFound && last.episodeIndex == episodePos) {
                    activity.let {
                        card.cardBg.setCardBackgroundColor(
                            Cyanea.instance.primaryLight
                        )
                    }
                } else {
                    activity.let {
                        card.cardBg.setCardBackgroundColor(
                            Cyanea.instance.primaryDark
                        )
                    }
                }
                activity.let {
                    card.cardTitle.setTextColor(
                        it.getTextColor()
                    )
                    card.cdi.setColorFilter(
                        ContextCompat.getColor(it, R.color.white)
                    )
                    card.cardRemoveIcon.setColorFilter(
                        ContextCompat.getColor(it, R.color.white)
                    )
                }
            } else {
                // Otherwise color is recycled
                activity.let {
                    card.cardTitle.setTextColor(
                        it.getTextColor()
                    )
                    card.cardBg.setCardBackgroundColor(
                        Cyanea.instance.backgroundColorDark
                    )
                    card.cdi.setColorFilter(
                        it.getColorFromAttr(R.attr.white)
                    )
                    card.cardRemoveIcon.setColorFilter(
                        it.getColorFromAttr(R.attr.white)
                    )
                }
            }
        }

        private fun castEpisode(data: ShiroApi.AnimePageData, episodeIndex: Int) {
            Toast.makeText(activity, "Getting links", Toast.LENGTH_SHORT).show()
            val castContext = CastContext.getSharedInstance(activity.applicationContext)
            castContext.castOptions
            val key = getViewKey(data.slug, episodeIndex)
            thread {
                val videoLinks = data.episodes?.get(episodeIndex)?.videos?.getOrNull(0)?.video_id.let { video_id ->
                    getVideoLink(
                        video_id!!, isCasting = true
                    )
                }

                /*val videoLinks = listOf(
                    ExtractorLink(
                        "Bus",
                        "https://samplelib.com/lib/download/mp4/sample-10s.mp4",
                        "",
                        0
                    ), ExtractorLink(
                        "Traffic",
                        "https://samplelib.com/lib/download/mp4/sample-20s.mp4",
                        "",
                        0
                    ), ExtractorLink(
                        "Crossing",
                        "https://samplelib.com/lib/download/mp4/sample-30s.mp4",
                        "",
                        0
                    )
                )*/

                println("LINK $videoLinks")
                if (videoLinks != null) {
                    activity.runOnUiThread {
                        val mediaItems = videoLinks.map {
                            val movieMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE)
                            movieMetadata.putString(
                                MediaMetadata.KEY_TITLE,
                                "Episode ${episodeIndex + 1} - ${it.name}"
                            )
                            movieMetadata.putString(MediaMetadata.KEY_ALBUM_ARTIST, data.name)
                            movieMetadata.addImage(WebImage(Uri.parse(getFullUrlCdn(data.image))))
                            MediaQueueItem.Builder(
                                MediaInfo.Builder(it.url)
                                    .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                                    .setContentType(MimeTypes.VIDEO_UNKNOWN)
                                    .setCustomData(JSONObject().put("data", it.name))
                                    .setMetadata(movieMetadata)
                                    .build()
                            )
                                .build()
                        }.toTypedArray()

                        val castPlayer = CastPlayer(castContext)
                        castPlayer.loadItems(
                            mediaItems,
                            0,
                            DataStore.getKey(VIEW_POS_KEY, key, 0L)!!,
                            REPEAT_MODE_REPEAT_SINGLE
                        )
                    }


                    /*castPlayer.setSessionAvailabilityListener(object : SessionAvailabilityListener {
                        override fun onCastSessionAvailable() {

                        }

                        override fun onCastSessionUnavailable() {}
                    })*/
                }
            }
        }
    }

}

