package com.lagradost.shiro.ui.result

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.*
import androidx.recyclerview.widget.RecyclerView
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.cast.CastPlayer
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.common.images.WebImage
import com.lagradost.shiro.*
import com.lagradost.shiro.utils.DataStore.mapper
import com.lagradost.shiro.utils.ShiroApi.Companion.getFullUrlCdn
import com.lagradost.shiro.utils.ShiroApi.Companion.getVideoLink
import com.lagradost.shiro.ui.MainActivity.Companion.activity
import com.lagradost.shiro.ui.MainActivity.Companion.isDonor
import com.lagradost.shiro.ui.AutofitRecyclerView
import com.lagradost.shiro.ui.toPx
import com.lagradost.shiro.ui.tv.DetailsActivityTV
import com.lagradost.shiro.ui.tv.PlaybackActivity
import com.lagradost.shiro.ui.tv.TvActivity.Companion.tvActivity
import com.lagradost.shiro.utils.*
import com.lagradost.shiro.utils.AppApi.getColorFromAttr
import com.lagradost.shiro.utils.AppApi.getLatestSeenEpisode
import com.lagradost.shiro.utils.AppApi.getViewKey
import com.lagradost.shiro.utils.AppApi.getViewPosDur
import com.lagradost.shiro.utils.AppApi.isCastApiAvailable
import com.lagradost.shiro.utils.AppApi.loadPlayer
import kotlinx.android.synthetic.main.episode_result_compact.view.*
import kotlinx.android.synthetic.main.episode_result_compact.view.cardBg
import kotlinx.android.synthetic.main.episode_result_compact.view.cardTitle
import java.io.File
import kotlin.concurrent.thread

class EpisodeAdapter(
    val context: Context,
    val data: ShiroApi.AnimePageData,
    private val resView: AutofitRecyclerView,
    private val save: Boolean,
    private val rangeStart: Int? = null,
    private val rangeStop: Int? = null,
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
            context,
            resView,
            save,
            data,
            start
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        //lastSelectedEpisode = 0
        holder.itemView.setOnFocusChangeListener { focusedView, hasFocus ->
            //lastSelectedEpisode = position
            if (prevFocus != null) {
                if (kotlin.math.abs(position - prevFocus!!) > 3 * 2) {
                    this.resView.layoutManager?.scrollToPosition(0)
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
        itemView: View, _context: Context, resView: RecyclerView, private val save: Boolean,
        val data: ShiroApi.AnimePageData, val start: Int
    ) :
        RecyclerView.ViewHolder(itemView) {
        // To prevent having to redo this operation on every bind
        val last = getLatestSeenEpisode(data)
        val context = _context
        val card: LinearLayout = itemView.episode_result_root
        val resView = resView

        // Downloads is only updated when re-bound!
        fun bind(position: Int) {
            println("Bind ep $position")
            val episodePos = start + position
            val key = getViewKey(data.slug, episodePos)


            // Because the view is recycled
            card.cdi.visibility = View.VISIBLE
            card.progressBar.visibility = View.GONE
            card.cardPauseIcon.visibility = View.GONE
            card.cardRemoveIcon.visibility = View.GONE

            if (isDonor) {
                card.cdi.visibility = View.VISIBLE
                card.cdi.setOnClickListener {
                    DownloadManager.downloadEpisode(
                        DownloadManager.DownloadInfo(
                            episodePos,
                            data
                        )
                    )
                }
            } else {
                card.cdi.visibility = View.GONE
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
                if (save) {
                    DataStore.setKey(VIEWSTATE_KEY, key, System.currentTimeMillis())
                }

                if (activity?.isCastApiAvailable() == true) {
                    val castContext = CastContext.getSharedInstance(activity!!.applicationContext)
                    println("SSTATE: " + castContext.castState + "<<")
                    if (castContext.castState == CastState.CONNECTED) {
                        castEpisode(data, episodePos)
                    } else {
                        thread {
                            activity?.loadPlayer(episodePos, 0L, data)
                        }
                    }
                } else {
                    thread {
                        if (tvActivity != null) {
                            val intent = Intent(tvActivity, PlaybackActivity::class.java)
                            intent.putExtra(DetailsActivityTV.MOVIE, mapper.writeValueAsString(data))
                            intent.putExtra("position", episodePos)
                            tvActivity?.startActivity(intent)
                        } else {
                            activity?.loadPlayer(start + episodePos, 0L, data)
                        }
                    }
                }
            }

            // Long tap to toggle viewstate, uncommenting this will probably fuck up latest viewstate
            /*card.cardBg.setOnLongClickListener {
                if (isViewState) {
                    if (DataStore.containsKey(VIEWSTATE_KEY, key)) {
                        DataStore.removeKey(VIEWSTATE_KEY, key)
                    } else {
                        DataStore.setKey<Long>(VIEWSTATE_KEY, key, System.currentTimeMillis())
                    }
                }
                return@setOnLongClickListener true
            }*/

            val title = "Episode ${episodePos + 1}"
            card.cardTitle.text = title
            if (DataStore.containsKey(VIEWSTATE_KEY, key)) {
                if (last.isFound && last.episodeIndex == episodePos) {
                    activity?.let {
                        card.cardBg.setCardBackgroundColor(
                            it.getColorFromAttr(R.attr.colorPrimaryDark)
                        )
                    }
                } else {
                    activity?.let {
                        card.cardBg.setCardBackgroundColor(
                            it.getColorFromAttr(R.attr.colorPrimaryDarker)
                        )
                    }
                }
                activity?.let {
                    card.cardTitle.setTextColor(
                        ContextCompat.getColor(it, R.color.textColor)
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
                activity?.let {
                    card.cardTitle.setTextColor(
                        it.getColorFromAttr(R.attr.textColor)
                    )
                    card.cardBg.setCardBackgroundColor(
                        it.getColorFromAttr(R.attr.darkBar)
                    )
                    card.cdi.setColorFilter(
                        it.getColorFromAttr(R.attr.white)
                    )
                    card.cardRemoveIcon.setColorFilter(
                        it.getColorFromAttr(R.attr.white)
                    )
                }
            }

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

            fun updateIcon(megabytes: Int, child: DownloadManager.DownloadFileMetadata) {
                val file = File(child.videoPath)
                val megaBytesTotal = DownloadManager.convertBytesToAny(child.maxFileSize, 0, 2.0).toInt()
                if (!file.exists()) {
                    card.cdi.visibility = View.VISIBLE
                    card.progressBar.visibility = View.GONE
                    card.cardPauseIcon.visibility = View.GONE
                    card.cardRemoveIcon.visibility = View.GONE
                } else {
                    card.cdi.visibility = View.GONE
                    if (megabytes + 3 >= megaBytesTotal) {
                        card.progressBar.visibility = View.GONE
                        card.cardPauseIcon.visibility = View.GONE
                        card.cardRemoveIcon.visibility = View.VISIBLE
                    } else {
                        card.progressBar.visibility = View.VISIBLE
                        card.cardRemoveIcon.visibility = View.GONE
                        card.cardPauseIcon.visibility = View.VISIBLE
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
                    val file = File(child.videoPath)
                    if (file.exists()) {
                        val megaBytesTotal = DownloadManager.convertBytesToAny(child.maxFileSize, 0, 2.0).toInt()
                        val localBytesTotal =
                            maxOf(DownloadManager.convertBytesToAny(file.length(), 0, 2.0).toInt(), 1)

                        println("FILE EXISTS:$episodePos")
                        fun deleteFile() {
                            if (file.exists()) {
                                file.delete()
                                val dir = File(file.absoluteFile.parent)
                                if (dir.listFiles().isEmpty()) {
                                    dir.delete()
                                }
                            }
                            activity?.runOnUiThread {
                                DataStore.removeKey(DOWNLOAD_CHILD_KEY, key)
                                Toast.makeText(
                                    context,
                                    "${child.videoTitle} E${child.episodeIndex + 1} deleted",
                                    Toast.LENGTH_LONG
                                ).show()
                                updateIcon(0, child)
                            }
                        }

                        card.cardRemoveIcon.setOnClickListener {
                            val alertDialog: AlertDialog? = activity?.let {
                                val builder = AlertDialog.Builder(it, R.style.AlertDialogCustom)
                                builder.apply {
                                    setPositiveButton(
                                        "Delete"
                                    ) { dialog, id ->
                                        deleteFile()
                                    }
                                    setNegativeButton(
                                        "Cancel"
                                    ) { dialog, id ->
                                        // User cancelled the dialog
                                    }
                                }
                                // Set other dialog properties
                                builder.setTitle("Delete ${child.videoTitle} - E${child.episodeIndex + 1}")

                                // Create the AlertDialog
                                builder.create()
                            }
                            alertDialog?.show()
                        }

                        card.cardTitle.text = title

                        //card.cardTitleExtra.text = "$localBytesTotal / $megaBytesTotal MB"

                        fun getDownload(): DownloadManager.DownloadInfo {
                            return DownloadManager.DownloadInfo(
                                child.episodeIndex,
                                data
                            )
                        }

                        fun getStatus(): Boolean { // IF CAN RESUME
                            return if (DownloadManager.downloadStatus.containsKey(child.internalId)) {
                                DownloadManager.downloadStatus[child.internalId] == DownloadManager.DownloadStatusType.IsPaused
                            } else {
                                true
                            }
                        }

                        fun setStatus() {
                            activity?.runOnUiThread {
                                if (getStatus()) {
                                    card.cardPauseIcon.setImageResource(R.drawable.netflix_play)
                                } else {
                                    card.cardPauseIcon.setImageResource(R.drawable.exo_icon_stop)
                                }
                            }
                        }

                        setStatus()
                        updateIcon(localBytesTotal, child)

                        card.cardPauseIcon.setOnClickListener { v ->
                            val popup = PopupMenu(context, v)
                            if (getStatus()) {
                                popup.setOnMenuItemClickListener {
                                    when (it.itemId) {
                                        R.id.res_resumedload -> {
                                            DownloadManager.downloadEpisode(getDownload(), true)
                                        }
                                        R.id.res_stopdload -> {
                                            DownloadManager.invokeDownloadAction(
                                                child.internalId,
                                                DownloadManager.DownloadStatusType.IsStopped
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
                                            DownloadManager.invokeDownloadAction(
                                                child.internalId,
                                                DownloadManager.DownloadStatusType.IsPaused
                                            )
                                        }
                                        R.id.stop_stopdload -> {
                                            DownloadManager.invokeDownloadAction(
                                                child.internalId,
                                                DownloadManager.DownloadStatusType.IsStopped
                                            )
                                            deleteFile()
                                        }
                                    }
                                    return@setOnMenuItemClickListener true
                                }
                                popup.inflate(R.menu.stop_menu)
                            }
                            popup.show()
                        }

                        card.progressBar.progress = maxOf(minOf(localBytesTotal * 100 / megaBytesTotal, 100), 0)

                        DownloadManager.downloadPauseEvent += {
                            if (it == child.internalId) {
                                setStatus()
                            }
                        }

                        DownloadManager.downloadDeleteEvent += {
                            if (it == child.internalId) {
                                deleteFile()
                            }
                        }
                        // TODO Doesn't work when resuming
                        DownloadManager.downloadEvent += {
                            resView.adapter?.notifyItemChanged(position)
                        }
                    }
                }
            }

        }

        private fun castEpisode(data: ShiroApi.AnimePageData, episodeIndex: Int) {
            val castContext = CastContext.getSharedInstance(activity!!.applicationContext)
            castContext.castOptions
            val key = getViewKey(data.slug, episodeIndex)
            thread {
                val videoLink = data.episodes?.get(episodeIndex)?.videos?.getOrNull(0)?.video_id.let { it1 ->
                    getVideoLink(
                        it1!!
                    )
                }
                println("LINK $videoLink")
                if (videoLink != null) {
                    activity!!.runOnUiThread {

                        val movieMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE)
                        movieMetadata.putString(
                            MediaMetadata.KEY_TITLE,
                            "Episode ${episodeIndex + 1}"
                        )
                        movieMetadata.putString(MediaMetadata.KEY_ALBUM_ARTIST, data.name)
                        movieMetadata.addImage(WebImage(Uri.parse(getFullUrlCdn(data.image))))

                        val mediaInfo = MediaInfo.Builder(videoLink)
                            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                            .setContentType(MimeTypes.VIDEO_UNKNOWN)
                            .setMetadata(movieMetadata).build()

                        val mediaItems = arrayOf(MediaQueueItem.Builder(mediaInfo).build())
                        val castPlayer = CastPlayer(castContext)

                        castPlayer.loadItems(
                            mediaItems,
                            0,
                            DataStore.getKey(VIEW_POS_KEY, key, 0L)!!,
                            Player.REPEAT_MODE_OFF
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

