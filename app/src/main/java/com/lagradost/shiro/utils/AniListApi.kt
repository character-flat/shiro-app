package com.lagradost.shiro.utils

import ANILIST_CACHED_LIST
import ANILIST_SHOULD_UPDATE_LIST
import ANILIST_TOKEN_KEY
import ANILIST_UNIXTIME_KEY
import ANILIST_USER_KEY
import DataStore.getKey
import DataStore.getKeys
import DataStore.setKey
import DataStore.toKotlinObject
import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.shiro.R
import com.lagradost.shiro.ui.MainActivity.Companion.activity
import com.lagradost.shiro.ui.library.LibraryFragment.Companion.libraryViewModel
import com.lagradost.shiro.ui.settings.SettingsFragmentNew.Companion.settingsViewModel
import com.lagradost.shiro.utils.AppUtils.getCurrentActivity
import com.lagradost.shiro.utils.AppUtils.openBrowser
import com.lagradost.shiro.utils.AppUtils.splitQuery
import com.lagradost.shiro.utils.AppUtils.unixTime
import com.lagradost.shiro.utils.ShiroApi.Companion.getMalIDFromTitle
import com.lagradost.shiro.utils.ShiroApi.Companion.maxStale
import com.lagradost.shiro.utils.mvvm.logError
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

const val ANILIST_CLIENT_ID = "4636"
const val ANILIST_ACCOUNT_ID = "0" // MIGHT WANT TO BE USED IF YOU WANT MULTIPLE ACCOUNT LOGINS

class AniListApi {
    companion object {
        private val aniListStatusString = arrayOf("CURRENT", "COMPLETED", "PAUSED", "DROPPED", "PLANNING", "REPEATING")

        private val mapper = JsonMapper.builder().addModule(KotlinModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()!!


        // Changing names of these will show up in UI
        enum class AniListStatusType(var value: Int) {
            Watching(0),
            Completed(1),
            Paused(2),
            Dropped(3),
            Planning(4),
            Rewatching(5),
            None(-1)
        }

        fun fromIntToAnimeStatus(inp: Int): AniListStatusType {//= AniListStatusType.values().first { it.value == inp }
            return when (inp) {
                -1 -> AniListStatusType.None
                0 -> AniListStatusType.Watching
                1 -> AniListStatusType.Completed
                2 -> AniListStatusType.Paused
                3 -> AniListStatusType.Dropped
                4 -> AniListStatusType.Planning
                5 -> AniListStatusType.Rewatching
                else -> AniListStatusType.None
            }
        }

        fun convertAnilistStringToStatus(string: String): AniListStatusType {
            return fromIntToAnimeStatus(aniListStatusString.indexOf(string))
        }

        fun Context.authenticateAniList() {
            val request = "https://anilist.co/api/v2/oauth/authorize?client_id=$ANILIST_CLIENT_ID&response_type=token"
            openBrowser(request)
        }

        fun Context.initGetUser() {
            if (getKey<String>(ANILIST_TOKEN_KEY, ANILIST_ACCOUNT_ID, null) == null) return
            thread {
                getUser()
            }
        }

        fun Activity.authenticateLogin(data: String) {
            try {
                val sanitizer =
                    splitQuery(URL(data.replace("shiroapp", "https").replace("/#", "?"))) // FIX ERROR
                val token = sanitizer["access_token"]!!
                val expiresIn = sanitizer["expires_in"]!!

                val endTime = unixTime() + expiresIn.toLong()

                setKey(ANILIST_UNIXTIME_KEY, ANILIST_ACCOUNT_ID, endTime)
                setKey(ANILIST_TOKEN_KEY, ANILIST_ACCOUNT_ID, token)
                setKey(ANILIST_SHOULD_UPDATE_LIST, true)
                libraryViewModel?.requestAnilistList(this)
                thread {
                    getUser()
                    settingsViewModel?.hasLoggedIntoAnilist?.postValue(true)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun Context.checkToken(): Boolean {
            if (unixTime() > getKey(
                    ANILIST_UNIXTIME_KEY, ANILIST_ACCOUNT_ID, 0L
                )!!
            ) {
                getCurrentActivity()?.runOnUiThread {
                    val alertDialog: AlertDialog? = activity?.let {
                        val builder = AlertDialog.Builder(it, R.style.AlertDialogCustom)
                        builder.apply {
                            setPositiveButton(
                                "Login"
                            ) { dialog, id ->
                                authenticateAniList()
                            }
                            setNegativeButton(
                                "Cancel"
                            ) { dialog, id ->
                                // User cancelled the dialog
                            }
                        }
                        // Set other dialog properties
                        builder.setTitle("AniList token has expired")

                        // Create the AlertDialog
                        builder.create()
                    }
                    alertDialog?.show()
                }
                return true
            } else {
                return false
            }
        }

        private fun fixName(name: String): String {
            return name.lowercase().replace(" ", "").replace("[^a-zA-Z0-9]".toRegex(), "")
        }

        private fun searchShows(name: String): GetSearchRoot? {
            try {
                val query = """
                query (${"$"}id: Int, ${"$"}page: Int, ${"$"}search: String, ${"$"}type: MediaType) {
                    Page (page: ${"$"}page, perPage: 10) {
                        media (id: ${"$"}id, search: ${"$"}search, type: ${"$"}type) {
                            id
                            idMal
                            seasonYear
                            startDate { year month day }
                            title {
                                romaji
                            }
                            nextAiringEpisode {
                                timeUntilAiring
                                episode
                            }
                            recommendations {
                                nodes {
                                    id
                                    mediaRecommendation {
                                        id
                                        title {
                                            english
                                            romaji
                                        }
                                        idMal
                                        coverImage { medium large }
                                        averageScore
                                    }
                                }
                            }
                            relations {
                                edges {
                                    id
                                    relationType(version: 2)
                                    node {
                                        format
                                        id
                                        idMal
                                        coverImage { medium large }
                                        averageScore
                                        title {
                                            english
                                            romaji
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                """
                val data =
                    mapOf("query" to query, "variables" to mapOf("search" to name, "page" to 1, "type" to "ANIME"))

                val res = khttp.post(
                    "https://graphql.anilist.co/",
                    //headers = mapOf(),
                    json = data,//(if (vars == null) mapOf("query" to q) else mapOf("query" to q, "variables" to vars))
                    timeout = 5.0 // REASONABLE TIMEOUT
                ).text.replace("\\", "")
                return res.toKotlinObject()
            } catch (e: Exception) {
                logError(e)
            }
            return null
        }

        fun getShowId(malId: String?, name: String, year: Int?): GetSearchMedia? {
            // Strips these from the name
            val blackList = listOf(
                "TV Dubbed",
                "(Dub)",
                "Subbed",
                "(TV)",
                "(Uncensored)",
                "(Censored)",
                "(\\d+)" // year
            )
            val blackListRegex =
                Regex(""" (${blackList.joinToString(separator = "|").replace("(", "\\(").replace(")", "\\)")})""")
            //println("NAME $name NEW NAME ${name.replace(blackListRegex, "")}")
            val shows = searchShows(name.replace(blackListRegex, ""))
            val malIdFixed = malId ?: getMalIDFromTitle(name.replace(blackListRegex, ""))

            shows?.data?.Page?.media?.find {
                malIdFixed ?: "NONE" == it.idMal.toString()
            }?.let { return it }

            val filtered =
                shows?.data?.Page?.media?.filter {
                    (
                            it.startDate.year ?: year.toString() == year.toString()
                                    || year == null
                            )
                }
            filtered?.forEach {
                if (fixName(it.title.romaji) == fixName(name)) return it
            }

            return filtered?.firstOrNull()
        }

        private fun Context.postApi(url: String, q: String, cache: Boolean = false): String {
            return try {
                if (!checkToken()) {
                    // println("VARS_ " + vars)
                    khttp.post(
                        "https://graphql.anilist.co/",
                        headers = mapOf(
                            "Authorization" to "Bearer " + getKey(
                                ANILIST_TOKEN_KEY,
                                ANILIST_ACCOUNT_ID,
                                ""
                            )!!,
                            if (cache) "Cache-Control" to "max-stale=$maxStale" else "Cache-Control" to "no-cache"
                        ),
                        data = mapOf("query" to q),//(if (vars == null) mapOf("query" to q) else mapOf("query" to q, "variables" to vars))
                        timeout = 5.0 // REASONABLE TIMEOUT
                    ).text.replace("\\/", "/")
                } else {
                    ""
                }
            } catch (e: Exception) {
                logError(e)
                ""
            }
        }

        data class MediaRecommendation(
            @JsonProperty("id") val id: Int,
            @JsonProperty("title") val title: Title,
            @JsonProperty("idMal") val idMal: Int?,
            @JsonProperty("coverImage") val coverImage: CoverImage,
            @JsonProperty("averageScore") val averageScore: Int?
        )

        fun Context.getDataAboutId(id: Int): AniListTitleHolder? {
            val q =
                """query (${'$'}id: Int = $id) { # Define which variables will be used in the query (id)
                Media (id: ${'$'}id, type: ANIME) { # Insert our variables into the query arguments (id) (type: ANIME is hard-coded in the query)
                    id
                    episodes
                    isFavourite
                    mediaListEntry {
                        progress
                        status
                        score (format: POINT_10)
                    }
                    title {
                        english
                        romaji
                    }
                }
            }"""
            try {
                val data = postApi("https://graphql.anilist.co", q, true)
                println(data)
                var d: GetDataRoot? = null
                try {
                    d = mapper.readValue<GetDataRoot>(data)
                } catch (e: Exception) {
                    logError(e)
                    println("Anilist json failed")
                }
                if (d == null) {
                    return null
                }

                val main = d.data.Media
                if (main.mediaListEntry != null) {
                    return AniListTitleHolder(
                        title = main.title,
                        id = id,
                        isFavourite = main.isFavourite,
                        progress = main.mediaListEntry.progress,
                        episodes = main.episodes,
                        score = main.mediaListEntry.score,
                        type = fromIntToAnimeStatus(aniListStatusString.indexOf(main.mediaListEntry.status)),
                    )
                } else {
                    return AniListTitleHolder(
                        title = main.title,
                        id = id,
                        isFavourite = main.isFavourite,
                        progress = 0,
                        episodes = main.episodes,
                        score = 0,
                        type = AniListStatusType.None,
                    )
                }
            } catch (e: Exception) {
                logError(e)
                return null
            }

        }

        data class FullAnilistList(
            @JsonProperty("data") val data: Data
        )

        data class CompletedAt(
            @JsonProperty("year") val year: Int,
            @JsonProperty("month") val month: Int,
            @JsonProperty("day") val day: Int
        )

        data class StartedAt(
            @JsonProperty("year") val year: String?,
            @JsonProperty("month") val month: String?,
            @JsonProperty("day") val day: String?
        )

        data class Title(
            @JsonProperty("english") val english: String?,
            @JsonProperty("romaji") val romaji: String?
        )

        data class CoverImage(
            @JsonProperty("medium") val medium: String,
            @JsonProperty("large") val large: String?
        )

        data class Media(
            @JsonProperty("id") val id: Int,
            @JsonProperty("idMal") val idMal: Int?,
            @JsonProperty("season") val season: String?,
            @JsonProperty("seasonYear") val seasonYear: Int,
            @JsonProperty("format") val format: String?,
            //@JsonProperty("source") val source: String,
            @JsonProperty("episodes") val episodes: Int,
            @JsonProperty("title") val title: Title,
            //@JsonProperty("description") val description: String,
            @JsonProperty("coverImage") val coverImage: CoverImage,
            @JsonProperty("synonyms") val synonyms: List<String>,
            @JsonProperty("nextAiringEpisode") val nextAiringEpisode: SeasonNextAiringEpisode?,
        )

        data class Entries(
            @JsonProperty("status") val status: String?,
            @JsonProperty("completedAt") val completedAt: CompletedAt,
            @JsonProperty("startedAt") val startedAt: StartedAt,
            @JsonProperty("updatedAt") val updatedAt: Int,
            @JsonProperty("progress") val progress: Int,
            @JsonProperty("score") val score: Int,
            @JsonProperty("private") val private: Boolean,
            @JsonProperty("media") val media: Media
        )

        data class Lists(
            @JsonProperty("status") val status: String?,
            @JsonProperty("entries") val entries: List<Entries>
        )

        data class MediaListCollection(
            @JsonProperty("lists") val lists: List<Lists>
        )

        data class Data(
            @JsonProperty("MediaListCollection") val MediaListCollection: MediaListCollection
        )


        fun Context.getAnilistAnimeListSmart(): Array<Lists>? {
            if (getKey<String>(
                    ANILIST_TOKEN_KEY,
                    ANILIST_ACCOUNT_ID,
                    null
                ) == null
            ) return null
            if (checkToken()) return null
            return if (getKey(ANILIST_SHOULD_UPDATE_LIST, true) == true) {
                val list = getFullAnilistList()?.data?.MediaListCollection?.lists?.toTypedArray()
                if (list != null) {
                    setKey(ANILIST_CACHED_LIST, list)
                    setKey(ANILIST_SHOULD_UPDATE_LIST, false)
                }
                list
            } else {
                getKey(ANILIST_CACHED_LIST) as? Array<Lists>
            }
        }

        private fun Context.getFullAnilistList(): FullAnilistList? {
            try {
                var userID: Int? = null
                /** WARNING ASSUMES ONE USER! **/
                getKeys(ANILIST_USER_KEY).forEach { key ->
                    getKey<AniListUser>(key, null)?.let {
                        userID = it.id
                    }
                }

                val fixedUserID = userID ?: return null
                val mediaType = "ANIME"

                val query = """
                query (${'$'}userID: Int = $fixedUserID, ${'$'}MEDIA: MediaType = $mediaType) {
                    MediaListCollection (userId: ${'$'}userID, type: ${'$'}MEDIA) { 
                        lists {
                            status
                            entries
                            {
                                status
                                completedAt { year month day }
                                startedAt { year month day }
                                updatedAt
                                progress
                                score
                                private
                                media
                                {
                                    id
                                    idMal
                                    season
                                    seasonYear
                                    format
                                    episodes
                                    chapters
                                    title
                                    {
                                        english
                                        romaji
                                    }
                                    coverImage { medium }
                                    synonyms
                                    nextAiringEpisode {
                                        timeUntilAiring
                                        episode
                                    }
                                }
                            }
                        }
                    }
                    }
            """
                val text = postApi("https://graphql.anilist.co", query)
                return text.toKotlinObject()

            } catch (e: Exception) {
                logError(e)
                return null
            }
        }

        fun Context.toggleLike(id: Int): Boolean {
            val q = """mutation (${'$'}animeId: Int = $id) {
				ToggleFavourite (animeId: ${'$'}animeId) {
					anime {
						nodes {
							id
							title {
								romaji
							}
						}
					}
				}
			}"""
            val data = postApi("https://graphql.anilist.co", q)
            return data != ""
        }

        fun Context.postDataAboutId(id: Int, type: AniListStatusType, score: Int, progress: Int): Boolean {
            try {
                val q =
                    """mutation (${'$'}id: Int = $id, ${'$'}status: MediaListStatus = ${
                        aniListStatusString[maxOf(
                            0,
                            type.value
                        )]
                    }, ${'$'}scoreRaw: Int = ${score * 10}, ${'$'}progress: Int = $progress) {
                SaveMediaListEntry (mediaId: ${'$'}id, status: ${'$'}status, scoreRaw: ${'$'}scoreRaw, progress: ${'$'}progress) {
                    id
                    status
                    progress
                    score
                }
                }"""
                val data = postApi("https://graphql.anilist.co", q)
                return data != ""
            } catch (e: Exception) {
                logError(e)
                return false
            }
        }

        private fun Context.getUser(setSettings: Boolean = true): AniListUser? {
            val q = """
				{
  					Viewer {
    					id
    					name
						avatar {
							large
						}
                        favourites {
                            anime {
                                nodes {
                                    id
                                }
                            }
                        }
  					}
				}"""
            try {
                val data = postApi("https://graphql.anilist.co", q)
                if (data == "") return null
                val userData = mapper.readValue<AniListRoot>(data)
                val u = userData.data.Viewer
                val user = AniListUser(
                    u.id,
                    u.name,
                    u.avatar.large,
                )
                if (setSettings) {
                    setKey(ANILIST_USER_KEY, ANILIST_ACCOUNT_ID, user)
                }
                /* // TODO FIX FAVS
                for(i in u.favourites.anime.nodes) {
                    println("FFAV:" + i.id)
                }*/
                return user
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
                return null
            }
        }

        private fun getSeason(id: Int): SeasonResponse? {
            val q: String = """
               query (${'$'}id: Int = $id) {
                   Media (id: ${'$'}id, type: ANIME) {
                       id
                       idMal
                       relations {
                            edges {
                                 id
                                 relationType(version: 2)
                                 node {
                                      id
                                      format
                                      nextAiringEpisode {
                                           timeUntilAiring
                                           episode
                                      }
                                 }
                            }
                       }
                       nextAiringEpisode {
                            timeUntilAiring
                            episode
                       }
                       format
                   }
               }
        """

            val data = khttp.post(
                "https://graphql.anilist.co",
                data = mapOf("query" to q)
            ).text
            if (data == "") return null
            return try {
                mapper.readValue(data)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        fun getAllSeasons(id: Int): List<SeasonResponse?> {
            val seasons = mutableListOf<SeasonResponse?>()
            fun getSeasonRecursive(id: Int) {
                val season = getSeason(id)
                if (season != null) {
                    seasons.add(season)
                    if (season.data.Media.format?.startsWith("TV") == true) {
                        season.data.Media.relations.edges.forEach {
                            if (it.node.format != null) {
                                if (it.relationType == "SEQUEL" && it.node.format.startsWith("TV")) {
                                    getSeasonRecursive(it.node.id)
                                    return@forEach
                                }
                            }
                        }
                    }
                }
            }
            getSeasonRecursive(id)
            return seasons.toList()
        }

        fun secondsToReadable(seconds: Int, completedValue: String): String {
            var secondsLong = seconds.toLong()
            val days = TimeUnit.SECONDS
                .toDays(secondsLong)
            secondsLong -= TimeUnit.DAYS.toSeconds(days)

            val hours = TimeUnit.SECONDS
                .toHours(secondsLong)
            secondsLong -= TimeUnit.HOURS.toSeconds(hours)

            val minutes = TimeUnit.SECONDS
                .toMinutes(secondsLong)
            secondsLong -= TimeUnit.MINUTES.toSeconds(minutes)
            if (minutes < 0) {
                return completedValue
            }
            //println("$days $hours $minutes")
            return "${if (days != 0L) "$days" + "d " else ""}${if (hours != 0L) "$hours" + "h " else ""}${minutes}m"
        }
    }

    data class SeasonResponse(
        @JsonProperty("data") val data: SeasonData,
    )

    data class SeasonData(
        @JsonProperty("Media") val Media: SeasonMedia,
    )

    data class SeasonMedia(
        @JsonProperty("id") val id: Int,
        @JsonProperty("idMal") val idMal: Int?,
        @JsonProperty("format") val format: String?,
        @JsonProperty("nextAiringEpisode") val nextAiringEpisode: SeasonNextAiringEpisode?,
        @JsonProperty("relations") val relations: SeasonEdges,
    )

    data class SeasonNextAiringEpisode(
        @JsonProperty("episode") val episode: Int,
        @JsonProperty("timeUntilAiring") val timeUntilAiring: Int,
    )

    data class SeasonEdges(
        @JsonProperty("edges") val edges: List<SeasonEdge>,
    )

    data class SeasonEdge(
        @JsonProperty("id") val id: Int,
        @JsonProperty("relationType") val relationType: String,
        @JsonProperty("node") val node: SeasonNode,
    )

    data class AniListFavoritesMediaConnection(
        @JsonProperty("nodes") val nodes: List<LikeNode>,
    )

    data class AniListFavourites(
        @JsonProperty("anime") val anime: AniListFavoritesMediaConnection,
    )

    data class SeasonNode(
        @JsonProperty("id") val id: Int,
        @JsonProperty("format") val format: String?,
        @JsonProperty("title") val title: Title,
        @JsonProperty("idMal") val idMal: Int?,
        @JsonProperty("coverImage") val coverImage: CoverImage,
        @JsonProperty("averageScore") val averageScore: Int?
//        @JsonProperty("nextAiringEpisode") val nextAiringEpisode: SeasonNextAiringEpisode?,
    )

    data class AniListAvatar(
        @JsonProperty("large") val large: String,
    )

    data class AniListViewer(
        @JsonProperty("id") val id: Int,
        @JsonProperty("name") val name: String,
        @JsonProperty("avatar") val avatar: AniListAvatar,
        @JsonProperty("favourites") val favourites: AniListFavourites,
    )

    data class AniListData(
        @JsonProperty("Viewer") val Viewer: AniListViewer,
    )

    data class AniListRoot(
        @JsonProperty("data") val data: AniListData,
    )

    data class AniListUser(
        @JsonProperty("id") val id: Int,
        @JsonProperty("name") val name: String,
        @JsonProperty("picture") val picture: String,
    )


    data class LikeNode(
        @JsonProperty("id") val id: Int,
        //@JsonProperty("idMal") public int idMal;
    )

    data class LikePageInfo(
        @JsonProperty("total") val total: Int,
        @JsonProperty("currentPage") val currentPage: Int,
        @JsonProperty("lastPage") val lastPage: Int,
        @JsonProperty("perPage") val perPage: Int,
        @JsonProperty("hasNextPage") val hasNextPage: Boolean,
    )

    data class LikeAnime(
        @JsonProperty("nodes") val nodes: List<LikeNode>,
        @JsonProperty("pageInfo") val pageInfo: LikePageInfo,
    )

    data class LikeFavourites(
        @JsonProperty("anime") val anime: LikeAnime,
    )

    data class LikeViewer(
        @JsonProperty("favourites") val favourites: LikeFavourites,
    )

    data class LikeData(
        @JsonProperty("Viewer") val Viewer: LikeViewer,
    )

    data class LikeRoot(
        @JsonProperty("data") val data: LikeData,
    )

    data class Recommendation(
        @JsonProperty("title") val title: String,
        @JsonProperty("idMal") val idMal: Int,
        @JsonProperty("poster") val poster: String,
        @JsonProperty("averageScore") val averageScore: Int?
    )

    data class AniListTitleHolder(
        @JsonProperty("title") val title: Title,
        @JsonProperty("isFavourite") val isFavourite: Boolean,
        @JsonProperty("id") val id: Int,
        @JsonProperty("progress") val progress: Int,
        @JsonProperty("episodes") val episodes: Int,
        @JsonProperty("score") val score: Int,
        @JsonProperty("type") val type: AniListStatusType,
    )

    data class GetDataMediaListEntry(
        @JsonProperty("progress") val progress: Int,
        @JsonProperty("status") val status: String,
        @JsonProperty("score") val score: Int,
    )

    data class Nodes(
        @JsonProperty("id") val id: Int,
        @JsonProperty("mediaRecommendation") val mediaRecommendation: MediaRecommendation
    )

    data class GetDataMedia(
        @JsonProperty("isFavourite") val isFavourite: Boolean,
        @JsonProperty("episodes") val episodes: Int,
        @JsonProperty("title") val title: Title,
        @JsonProperty("mediaListEntry") val mediaListEntry: GetDataMediaListEntry?
    )

    data class Recommendations(
        @JsonProperty("nodes") val nodes: List<Nodes>
    )

    data class GetDataData(
        @JsonProperty("Media") val Media: GetDataMedia,
    )

    data class GetDataRoot(
        @JsonProperty("data") val data: GetDataData,
    )

    data class GetSearchTitle(
        @JsonProperty("romaji") val romaji: String,
    )

    data class GetSearchMedia(
        @JsonProperty("id") val id: Int,
        @JsonProperty("idMal") val idMal: Int?,
        @JsonProperty("seasonYear") val seasonYear: Int,
        @JsonProperty("title") val title: GetSearchTitle,
        @JsonProperty("startDate") val startDate: StartedAt,
        @JsonProperty("nextAiringEpisode") val nextAiringEpisode: SeasonNextAiringEpisode?,
        @JsonProperty("recommendations") val recommendations: Recommendations?,
        @JsonProperty("relations") val relations: SeasonEdges
    )

    data class GetSearchPage(
        @JsonProperty("Page") val Page: GetSearchData,
    )

    data class GetSearchData(
        @JsonProperty("media") val media: List<GetSearchMedia>,
    )

    data class GetSearchRoot(
        @JsonProperty("data") val data: GetSearchPage,
    )
}