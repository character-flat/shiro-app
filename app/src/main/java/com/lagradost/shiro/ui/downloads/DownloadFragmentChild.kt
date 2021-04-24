package com.lagradost.shiro.ui.downloads

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.preference.PreferenceManager
import com.lagradost.shiro.*
import com.lagradost.shiro.ui.PlayerData
import kotlinx.android.synthetic.main.episode_result_downloaded.view.*
import kotlinx.android.synthetic.main.fragment_download_child.*
import java.io.File

import com.lagradost.shiro.ui.MainActivity
import com.lagradost.shiro.ui.PlayerFragment
import com.lagradost.shiro.ui.PlayerFragment.Companion.isInPlayer
import com.lagradost.shiro.ui.home.ExpandedHomeFragment.Companion.isInExpandedView
import com.lagradost.shiro.ui.result.ResultFragment.Companion.fixEpTitle
import com.lagradost.shiro.ui.result.ResultFragment.Companion.isInResults
import com.lagradost.shiro.ui.result.ResultFragment.Companion.isViewState
import com.lagradost.shiro.utils.AppApi.getColorFromAttr
import com.lagradost.shiro.utils.AppApi.getViewKey
import com.lagradost.shiro.utils.AppApi.getViewPosDur
import com.lagradost.shiro.utils.AppApi.loadPlayer
import com.lagradost.shiro.utils.AppApi.popCurrentPage
import com.lagradost.shiro.utils.AppApi.settingsManager
import com.lagradost.shiro.utils.DOWNLOAD_PARENT_KEY
import com.lagradost.shiro.utils.DataStore
import com.lagradost.shiro.utils.DownloadManager
import com.lagradost.shiro.utils.VIEWSTATE_KEY


class DownloadFragmentChild : Fragment() {
    var slug: String? = null
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isInResults = true
        arguments?.getString("slug")?.let {
            slug = it
        }
        val topParams: LinearLayout.LayoutParams = LinearLayout.LayoutParams(
            LinearLayoutCompat.LayoutParams.MATCH_PARENT, // view width
            MainActivity.statusHeight // view height
        )
        top_padding_download_child.layoutParams = topParams
        PlayerFragment.onLeftPlayer += ::onPlayerLeft
        download_go_back.setOnClickListener {
            activity?.popCurrentPage(isInPlayer, isInExpandedView, isInResults)
        }
        loadData()
    }

    override fun onDestroy() {
        super.onDestroy()
        PlayerFragment.onLeftPlayer -= ::onPlayerLeft
        isInResults = false
    }

    private fun onPlayerLeft(it: Boolean) {
        loadData()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun loadData() {
        downloadRootChild.removeAllViews()
        val save = settingsManager!!.getBoolean("save_history", true)

        // When fastani is down it doesn't report any seasons and this is needed.
        val episodeKeys = DownloadFragment.childMetadataKeys[slug]
        val parent = DataStore.getKey<DownloadManager.DownloadParentFileMetadata>(DOWNLOAD_PARENT_KEY, slug!!)
        download_header_text.text = parent?.title
        // Sorts by Seasons and Episode Index
        val sortedEpisodeKeys =
            episodeKeys!!.associateBy({ DataStore.getKey<DownloadManager.DownloadFileMetadata>(it) }, { it }).toList()
                .sortedBy { (key, _) -> key?.episodeIndex }.toMap()

        sortedEpisodeKeys.forEach { it ->
            val child = it.key

            if (child != null) {
                val file = File(child.videoPath)
                if (!file.exists()) {
                    return@forEach
                }

                val card: View = layoutInflater.inflate(R.layout.episode_result_downloaded, null)
                /*if (child.thumbPath != null) {
                    card.imageView.setImageURI(Uri.parse(child.thumbPath))
                }


                */
                val key = getViewKey(slug!!, child.episodeIndex)
                card.cardBg.setOnClickListener {
                    if (save) {
                        DataStore.setKey(VIEWSTATE_KEY, key, System.currentTimeMillis())
                    }
                    activity?.loadPlayer(
                        PlayerData(
                            child.videoTitle,
                            child.videoPath,
                            child.episodeIndex,
                            0,
                            null,
                            null,
                            slug!!
                        )
                    )
                }
                //MainActivity.loadPlayer(epIndex, index, data)
                val title = fixEpTitle(
                    child.videoTitle, child.episodeIndex + 1,
                    parent?.isMovie == true, true
                )

                // ================ DOWNLOAD STUFF ================
                fun deleteFile() {
                    if (file.exists()) {
                        file.delete()
                        val dir = File(file.absoluteFile.parent)
                        if (dir.listFiles().isEmpty()) {
                            dir.delete()
                        }
                    }
                    activity?.runOnUiThread {
                        card.visibility = GONE
                        DataStore.removeKey(it.value)
                        Toast.makeText(
                            context,
                            "${child.videoTitle} E${child.episodeIndex + 1} deleted",
                            Toast.LENGTH_LONG
                        ).show()
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

                card.setOnLongClickListener {
                    if (isViewState) {
                        if (DataStore.containsKey(VIEWSTATE_KEY, key)) {
                            DataStore.removeKey(VIEWSTATE_KEY, key)
                        } else {
                            DataStore.setKey(VIEWSTATE_KEY, key, System.currentTimeMillis())
                        }
                        loadData()
                    }
                    return@setOnLongClickListener true
                }

                card.cardTitle.text = title
                val megaBytesTotal = DownloadManager.convertBytesToAny(child.maxFileSize, 0, 2.0).toInt()
                val localBytesTotal = maxOf(DownloadManager.convertBytesToAny(file.length(), 0, 2.0).toInt(), 1)
                card.cardTitleExtra.text = "$localBytesTotal / $megaBytesTotal MB"

                fun updateIcon(megabytes: Int) {
                    if (megabytes + 3 >= megaBytesTotal) {
                        card.progressBar.visibility = GONE
                        card.cardPauseIcon.visibility = GONE
                        card.cardRemoveIcon.visibility = View.VISIBLE
                    } else {
                        card.progressBar.visibility = View.VISIBLE
                        card.cardRemoveIcon.visibility = GONE
                        card.cardPauseIcon.visibility = View.VISIBLE
                    }
                }

                fun getDownload(): DownloadManager.DownloadInfo {
                    return DownloadManager.DownloadInfo(
                        child.episodeIndex,
                        child.animeData
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
                updateIcon(localBytesTotal)

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

                DownloadManager.downloadEvent += {
                    activity?.runOnUiThread {
                        if (it.downloadEvent.id == child.internalId) {
                            val megaBytes = DownloadManager.convertBytesToAny(it.downloadEvent.bytes, 0, 2.0).toInt()
                            card.cardTitleExtra.text = "$megaBytes / $megaBytesTotal MB"
                            card.progressBar.progress = maxOf(minOf(megaBytes * 100 / megaBytesTotal, 100), 0)
                            updateIcon(megaBytes)
                        }
                    }
                }

                // ================ REGULAR ================
                if (DataStore.containsKey(VIEWSTATE_KEY, key)) {
                    card.cardBg.setCardBackgroundColor(
                        requireContext().getColorFromAttr(
                            R.attr.colorPrimaryMegaDark
                        )
                    )
                }

                val pro = getViewPosDur(slug!!, child.episodeIndex)
                if (pro.dur > 0 && pro.pos > 0) {
                    var progress: Int = (pro.pos * 100L / pro.dur).toInt()
                    if (progress < 5) {
                        progress = 5
                    } else if (progress > 95) {
                        progress = 100
                    }
                    card.video_progress.progress = progress
                } else {
                    card.video_progress.alpha = 0f
                }
                downloadRootChild.addView(card)
                downloadRootChild.invalidate()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_download_child, container, false)
    }


    companion object {
        fun newInstance(slug: String) =
            DownloadFragmentChild().apply {
                arguments = Bundle().apply {
                    putString("slug", slug)
                }
            }
    }
}