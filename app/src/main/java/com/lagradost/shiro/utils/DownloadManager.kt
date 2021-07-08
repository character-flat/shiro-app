package com.lagradost.shiro.utils

import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.shiro.utils.AppUtils.checkWrite
import com.lagradost.shiro.utils.AppUtils.requestRW
import com.lagradost.shiro.utils.ShiroApi.Companion.getFullUrlCdn
import kotlin.math.pow
import kotlin.math.round

object DownloadManager {
    data class DownloadParentFileMetadata(
        @JsonProperty("title") val title: String,
        @JsonProperty("coverImagePath") val coverImagePath: String,
        @JsonProperty("isMovie") val isMovie: Boolean,
        @JsonProperty("slug") val slug: String,

        @JsonProperty("anilistID") val anilistID: Int?,
        @JsonProperty("malID") val malID: Int?,
        @JsonProperty("fillerEpisodes") val fillerEpisodes: HashMap<Int, Boolean>?
    )

    // Glue for invoke()
    /*data class DownloadEventAndChild(
        @JsonProperty("downloadEvent") val downloadEvent: DownloadEvent,
        @JsonProperty("child") val child: DownloadFileMetadata,
    )*/

    data class DownloadFileMetadataSemiLegacy(
        @JsonProperty("internalId") val internalId: Int, // UNIQUE ID BASED ON aniListId season and index
        @JsonProperty("slug") val slug: String,
        @JsonProperty("animeData") val animeData: ShiroApi.AnimePageData,

        @JsonProperty("thumbPath") val thumbPath: String?,
        @JsonProperty("videoPath") val videoPath: String,

        @JsonProperty("videoTitle") val videoTitle: String,
        @JsonProperty("episodeIndex") val episodeIndex: Int,

        @JsonProperty("downloadAt") val downloadAt: Long,
        @JsonProperty("maxFileSize") val maxFileSize: Long, // IF MUST RESUME
        @JsonProperty("downloadFileLink") val downloadFileLink: ExtractorLink, // IF RESUME, DO IT FROM THIS URL
    )

    data class DownloadFileMetadata(
        @JsonProperty("internalId") val internalId: Int, // UNIQUE ID BASED ON aniListId season and index
        @JsonProperty("slug") val slug: String,
        @JsonProperty("animeData") val animeData: ShiroApi.AnimePageData,

        @JsonProperty("thumbPath") val thumbPath: String?,
        @JsonProperty("videoTitle") val videoTitle: String,
        @JsonProperty("episodeIndex") val episodeIndex: Int,
        @JsonProperty("downloadAt") val downloadAt: Long,
    )

    data class DownloadFileMetadataLegacy(
        @JsonProperty("internalId") val internalId: Int, // UNIQUE ID BASED ON aniListId season and index
        @JsonProperty("slug") val slug: String,
        @JsonProperty("animeData") val animeData: ShiroApi.AnimePageData,

        @JsonProperty("thumbPath") val thumbPath: String?,
        @JsonProperty("videoPath") val videoPath: String,

        @JsonProperty("videoTitle") val videoTitle: String,
        @JsonProperty("episodeIndex") val episodeIndex: Int,

        @JsonProperty("downloadAt") val downloadAt: Long,
        @JsonProperty("maxFileSize") val maxFileSize: Long, // IF MUST RESUME
        @JsonProperty("downloadFileUrl") val downloadFileUrl: String, // IF RESUME, DO IT FROM THIS URL
    )

    data class DownloadInfo(
        //val card: FastAniApi.Card?,
        @JsonProperty("episodeIndex") val episodeIndex: Int,
        @JsonProperty("animeData") val animeData: ShiroApi.AnimePageData,

        @JsonProperty("anilistID") val anilistID: Int? = null,
        @JsonProperty("malID") val malID: Int? = null,
        @JsonProperty("fillerEpisodes") val fillerEpisodes: HashMap<Int, Boolean>? = null
    )

    private fun Double.round(decimals: Int): Double {
        var multiplier = 1.0
        repeat(decimals) { multiplier *= 10 }
        return round(this * multiplier) / multiplier
    }

    fun convertBytesToAny(bytes: Long, digits: Int = 2, steps: Double = 3.0): Double {
        return (bytes / 1024.0.pow(steps)).round(digits)
    }

    fun downloadEpisode(context: FragmentActivity, info: DownloadInfo, link: List<ExtractorLink>) {
        if (!context.checkWrite()) {
            Toast.makeText(context, "Accept storage permissions to download", Toast.LENGTH_LONG).show()
            context.requestRW()
            return
        }

        val id = (info.animeData.slug + "E${info.episodeIndex}").hashCode()
        val isMovie: Boolean =
            info.animeData.episodes?.size ?: 0 == 1 && info.animeData.status == "finished"

        val episodeOffset = if (info.animeData.episodes?.filter { it.episode_number == 0 }.isNullOrEmpty()) 0 else -1

        DataStore.setKey(
            DOWNLOAD_PARENT_KEY, info.animeData.slug,
            DownloadParentFileMetadata(
                info.animeData.name,
                getFullUrlCdn(info.animeData.image), //mainPosterPath
                isMovie,
                info.animeData.slug,
                info.anilistID,
                info.malID,
                info.fillerEpisodes
            )
        )

        var title = info.animeData.name
        if (title.replace(" ", "") == "") {
            title = "Episode " + info.episodeIndex + 1
        }

        DataStore.setKey(
            DOWNLOAD_CHILD_KEY, id.toString(),
            DownloadFileMetadata(
                id,
                info.animeData.slug,
                info.animeData,
                getFullUrlCdn(info.animeData.image), //TODO Download poster
                title,
                info.episodeIndex,
                System.currentTimeMillis(),
            )
        )

        val mainTitle = info.animeData.name

        val folder = if (isMovie) {
            "Movies"
        } else {
            "Anime/${VideoDownloadManager.sanitizeFilename(mainTitle)}"
        }
        val name = if (isMovie) mainTitle else null

        VideoDownloadManager.downloadEpisode(
            context,
            "https://shiro.is/anime/${info.animeData.slug}",
            folder,
            VideoDownloadManager.DownloadEpisodeMetadata(
                id,
                mainTitle,
                null, // "Shiro"
                getFullUrlCdn(info.animeData.image),
                name,
                null,
                if (isMovie) null else info.episodeIndex + 1 + episodeOffset
            ),
            link
        )
    }
}