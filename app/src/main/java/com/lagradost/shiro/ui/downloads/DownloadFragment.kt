package com.lagradost.shiro.ui.downloads

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.fragment.app.Fragment
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.shiro.R
import com.lagradost.shiro.ui.MainActivity
import com.lagradost.shiro.ui.MainActivity.Companion.isDonor
import com.lagradost.shiro.utils.*
import com.lagradost.shiro.utils.AppUtils.addFragmentOnlyOnce
import com.lagradost.shiro.utils.AppUtils.loadPage
import kotlinx.android.synthetic.main.download_card.view.*
import kotlinx.android.synthetic.main.fragment_download.*
import java.io.File

class DownloadFragment : Fragment() {
    private val downloadFragmentTag = "DownloadFragment"

    data class EpisodesDownloaded(
        @JsonProperty("count") val count: Int,
        @JsonProperty("countDownloading") val countDownloading: Int,
        @JsonProperty("countBytes") val countBytes: Long,
    )

    private fun updateItems(bool: Boolean = true) {
        activity?.runOnUiThread {

            downloadRoot.removeAllViews()
            childMetadataKeys.clear()
            val inflater = activity?.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

            val epData = hashMapOf<String, EpisodesDownloaded>()
            try {
                val childKeys = getChildren()
                downloadCenterText.text =
                    if (isDonor) getString(R.string.resultpage1) else getString(R.string.resultpage2)
                downloadCenterRoot.visibility = if (childKeys.isEmpty()) VISIBLE else GONE

                for (k in childKeys) {
                    val child = DataStore.getKey<DownloadManager.DownloadFileMetadata>(k)
                    if (child != null) {
                        if (!File(child.videoPath).exists()) { // FILE DOESN'T EXIT
                            val thumbFile = File(child.thumbPath)
                            if (thumbFile.exists()) {
                                thumbFile.delete()
                            }
                            DataStore.removeKey(k)
                        } else {
                            if (childMetadataKeys.containsKey(child.slug)) {
                                childMetadataKeys[child.slug]?.add(k)
                            } else {
                                childMetadataKeys[child.slug] = mutableListOf(k)
                            }

                            val id = child.slug
                            println("EpisodeIndex: " + child.episodeIndex)
                            val isDownloading =
                                DownloadManager.downloadStatus.containsKey(child.internalId) &&
                                        DownloadManager.downloadStatus[child.internalId] == DownloadManager.DownloadStatusType.IsDownloading

                            if (!epData.containsKey(id)) {
                                epData[id] = EpisodesDownloaded(1, if (isDownloading) 1 else 0, child.maxFileSize)
                            } else {
                                val current = epData[id]!!
                                epData[id] = EpisodesDownloaded(
                                    current.count + 1,
                                    current.countDownloading + (if (isDownloading) 1 else 0),
                                    current.countBytes + child.maxFileSize
                                )
                            }
                        }
                    }
                }

                val keys = DataStore.getKeys(DOWNLOAD_PARENT_KEY)
                for (k in keys) {
                    val parent = DataStore.getKey<DownloadManager.DownloadParentFileMetadata>(k)
                    if (parent != null) {
                        if (epData.containsKey(parent.slug)) {
                            val cardView = inflater.inflate(R.layout.download_card, view?.parent as? ViewGroup, false)

                            cardView.imageView.setOnClickListener {
                                activity?.loadPage(parent.slug, parent.title)
                            }

                            cardView.cardTitle.text = parent.title
                            cardView.imageView.setImageURI(Uri.parse(parent.coverImagePath))

                            val childData = epData[parent.slug]!!
                            val megaBytes = DownloadManager.convertBytesToAny(childData.countBytes, 0, 2.0).toInt()
                            cardView.cardInfo.text =
                                if (parent.isMovie) "$megaBytes MB" else
                                    "${childData.count} Episode${(if (childData.count == 1) "" else "s")} | $megaBytes MB"

                            cardView.cardBg.setOnClickListener {
                                activity?.addFragmentOnlyOnce(
                                    R.id.homeRoot,
                                    DownloadFragmentChild.newInstance(
                                        parent.slug
                                    ),
                                    downloadFragmentTag
                                )

                                /*MainActivity.activity?.supportFragmentManager?.beginTransaction()
                                    ?.setCustomAnimations(R.anim.enter, R.anim.exit, R.anim.pop_enter, R.anim.pop_exit)
                                    ?.replace(
                                        R.id.homeRoot, DownloadFragmentChild(
                                            parent.anilistId
                                        )
                                    )
                                    ?.commitAllowingStateLoss()*/
                            }

                            downloadRoot.addView(cardView)
                        } else {
                            val coverFile = File(parent.coverImagePath)
                            if (coverFile.exists()) {
                                coverFile.delete()
                            }
                            DataStore.removeKey(k)
                        }
                    }
                }
            } catch (e: Exception) {
                println("ERROR LOADING DOWNLOADS:::")
                e.printStackTrace()
            }
        }
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val topParams: LinearLayout.LayoutParams = LinearLayout.LayoutParams(
            LinearLayoutCompat.LayoutParams.MATCH_PARENT, // view width
            MainActivity.statusHeight // view height
        )
        top_padding_download.layoutParams = topParams
        updateItems()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        /*val path = activity?.filesDir.toString() + "/Download/"
        File(path).walk().forEach {
            println("PATH: $it")
        }*/
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_download, container, false)
    }

    companion object {
        val downloadsUpdated = Event<Boolean>()
        val childMetadataKeys = hashMapOf<String, MutableList<String>>()
        const val LEGACY_DOWNLOADS = "legacy_downloads"
    }

    private fun getChildren(): List<String> {
        val legacyDownloads = DataStore.getKey(LEGACY_DOWNLOADS, true)
        if (legacyDownloads == true) {
            convertOldDownloads()
        }

        return DataStore.getKeys(DOWNLOAD_CHILD_KEY)
    }

    override fun onResume() {
        downloadsUpdated += ::updateItems
        super.onResume()
    }

    override fun onDestroy() {
        downloadsUpdated -= ::updateItems
        super.onDestroy()
    }

    private fun convertOldDownloads() {
        try {
            val keys = DataStore.getKeys(DOWNLOAD_CHILD_KEY)
            println("KEYS $keys")
            keys.pmap {
                DataStore.getKey<DownloadManager.DownloadFileMetadataLegacy>(it)
            }
            keys.forEach {
                val data = DataStore.getKey<DownloadManager.DownloadFileMetadataLegacy>(it)
                println("DATA $data $it")
                if (data != null) {
                    // NEEDS REMOVAL TO PREVENT DUPLICATES
                    DataStore.removeKey(it)
                    DataStore.setKey(
                        it, DownloadManager.DownloadFileMetadata(
                            data.internalId,
                            data.slug,
                            data.animeData,
                            data.thumbPath,
                            data.videoPath,
                            data.videoTitle,
                            data.episodeIndex,
                            data.downloadAt,
                            data.maxFileSize,
                            ExtractorLink(
                                "Shiro",
                                data.downloadFileUrl,
                                "https://shiro.is/",
                                Qualities.UHD.value
                            )
                        )
                    )
                }
            }
            DataStore.setKey(LEGACY_DOWNLOADS, false)
        } catch (e: Exception) {
            println("Error IN convertOldDownloads")
        }

    }
}