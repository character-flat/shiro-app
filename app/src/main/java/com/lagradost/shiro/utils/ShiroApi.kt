package com.lagradost.shiro.utils

import BOOKMARK_KEY
import DataStore.getKey
import DataStore.getKeys
import DataStore.removeKey
import DataStore.setKey
import LEGACY_BOOKMARKS
import SUBSCRIPTIONS_BOOKMARK_KEY
import VIEW_LST_KEY
import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.shiro.ui.BookmarkedTitle
import com.lagradost.shiro.ui.LastEpisodeInfo
import com.lagradost.shiro.ui.MainActivity.Companion.activity
import com.lagradost.shiro.utils.AppUtils.allApi
import com.lagradost.shiro.utils.AppUtils.md5
import com.lagradost.shiro.utils.AppUtils.settingsManager
import khttp.structures.cookie.CookieJar
import java.net.URLEncoder
import kotlin.concurrent.thread

const val SHIRO_TIMEOUT_TIME = 60.0

class ShiroApi {

    data class Token(
        @JsonProperty("headers") val headers: Map<String, String>,
        @JsonProperty("cookies") val cookies: CookieJar,
        @JsonProperty("token") val token: String,
    )

    data class Episode(@JsonProperty("file") val file: String)

    data class Donor(@JsonProperty("id") val id: String)

    data class ShiroSearchResponseShow(
        @JsonProperty("image") override val image: String,
        @JsonProperty("_id") val _id: String,
        @JsonProperty("slug") override val slug: String,
        @JsonProperty("name") override val name: String,
        @JsonProperty("english") override val english: String?,
    ) : CommonAnimePage

    data class ShiroHomePageData(
        @JsonProperty("trending_animes") val trending_animes: List<AnimePageData>,
        @JsonProperty("ongoing_animes") val ongoing_animes: List<AnimePageData>,
        @JsonProperty("latest_animes") val latest_animes: List<AnimePageData>,
        @JsonProperty("latest_episodes") val latest_episodes: List<ShiroEpisodes>,
    )

    data class ShiroHomePage(
        @JsonProperty("status") val status: String,
        @JsonProperty("data") val data: ShiroHomePageData,
        @JsonProperty("random") var random: AnimePage?,
        @JsonProperty("favorites") var favorites: List<BookmarkedTitle?>?,
        @JsonProperty("subscribed") var subscribed: List<BookmarkedTitle?>?,
        @JsonProperty("recentlySeen") var recentlySeen: List<LastEpisodeInfo?>?,
        // A hack for android TV MasterCardAdapter
        // @JsonProperty("searchResults") var searchResults: List<ShiroSearchResponseShow?>?
    )


    data class ShiroSearchResponse(
        @JsonProperty("data") val data: List<ShiroSearchResponseShow>,
        @JsonProperty("status") val status: String
    )

    data class ShiroFullSearchResponseCurrentPage(
        @JsonProperty("items") val items: List<ShiroSearchResponseShow>
    )

    data class ShiroFullSearchResponseNavItems(
        @JsonProperty("currentPage") val currentPage: ShiroFullSearchResponseCurrentPage
    )

    data class ShiroFullSearchResponseNav(
        @JsonProperty("nav") val nav: ShiroFullSearchResponseNavItems
    )

    data class ShiroFullSearchResponse(
        @JsonProperty("data") val data: ShiroFullSearchResponseNav,
        @JsonProperty("status") val status: String
    )

    data class ShiroVideo(
        @JsonProperty("video_id") val video_id: String,
        @JsonProperty("host") val host: String,
    )

    data class ShiroEpisodes(
        @JsonProperty("anime") val anime: AnimePageData?,
        @JsonProperty("anime_slug") val anime_slug: String,
        @JsonProperty("create") val create: String,
        @JsonProperty("dayOfTheWeek") val dayOfTheWeek: String,
        @JsonProperty("episode_number") val episode_number: Int,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("update") val update: String,
        @JsonProperty("_id") val _id: String,
        @JsonProperty("videos") val videos: List<ShiroVideo>
    )

    data class AnimePageData(
        @JsonProperty("banner") val banner: String?,
        @JsonProperty("canonicalTitle") val canonicalTitle: String?,
        @JsonProperty("episodeCount") val episodeCount: String,
        @JsonProperty("genres") val genres: List<String>?,
        @JsonProperty("image") override val image: String,
        @JsonProperty("japanese") val japanese: String?,
        @JsonProperty("language") val language: String,
        @JsonProperty("name") override var name: String,
        @JsonProperty("slug") override var slug: String,
        @JsonProperty("synopsis") val synopsis: String,
        @JsonProperty("type") val type: String?,
        @JsonProperty("views") val views: Int?,
        @JsonProperty("year") val year: String?,
        @JsonProperty("_id") val _id: String,
        @JsonProperty("episodes") var episodes: List<ShiroEpisodes>?,
        @JsonProperty("status") val status: String?,
        @JsonProperty("schedule") val schedule: String?,
        @JsonProperty("english") override val english: String?,
    ) : CommonAnimePage

    data class AnimePage(
        @JsonProperty("data") val data: AnimePageData,
        @JsonProperty("status") val status: String
    )

    // Hack, needed to deserialize
    data class CommonAnimePageData(
        @JsonProperty("name") override val name: String,
        @JsonProperty("image") override val image: String,
        @JsonProperty("slug") override val slug: String,
        @JsonProperty("english") override val english: String? = null,
    ) : CommonAnimePage

    interface CommonAnimePage {
        val name: String
        val image: String
        val slug: String
        val english: String?
    }

    data class AllSearchMethods(
        @JsonProperty("data") val data: AllSearchMethodsData,
        @JsonProperty("status") val status: String
    )

    data class AllSearchMethodsData(
        @JsonProperty("genres") val genres: List<Genre>,
        @JsonProperty("language") val language: List<Genre>,
        @JsonProperty("sort") val sort: List<Genre>,
        @JsonProperty("status") val status: List<Genre>,
        @JsonProperty("type") val type: List<Genre>,
        @JsonProperty("year") val year: List<Genre>,
    )

    data class Genre(
        @JsonProperty("slug") val slug: String,
        @JsonProperty("name") val name: String
    )

    companion object {
        infix fun Int.fmod(other: Int) = ((this % other) + other) % other
        const val maxStale = 60 * 10 // 10m

        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; rv:68.0) Gecko/20100101 Firefox/68.0"
        private val mapper = JsonMapper.builder().addModule(KotlinModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()

        // NULL IF ERROR
        fun getToken(): Token? {
            try {
                val headers = mapOf("User-Agent" to USER_AGENT)
                val shiro = khttp.get("https://shiro.is", headers = headers, timeout = SHIRO_TIMEOUT_TIME)
                val jsMatch = Regex("""src="(/static/js/main.*?)"""").find(shiro.text)
                val (destructed) = jsMatch!!.destructured
                val jsLocation = "https://shiro.is$destructed"
                val js = khttp.get(jsLocation, headers = headers)
                val tokenMatch = Regex("""token:"(.*?)"""").find(js.text)
                val (token) = tokenMatch!!.destructured
                val tokenHeaders = mapOf(
                    "User-Agent" to USER_AGENT
                )
                return Token(
                    tokenHeaders,
                    shiro.cookies,
                    token
                )
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }

        @SuppressLint("HardwareIds")
        fun getDonorStatus(): String {
            val url = "https://raw.githubusercontent.com/Blatzar/donors/master/donors.json"
            try {
                val androidId: String =
                    Settings.Secure.getString(activity?.contentResolver, Settings.Secure.ANDROID_ID)
                // Change cache with this
                // , headers = mapOf("Cache-Control" to "max-age=60")
                val response = khttp.get(url).text
                val users = mapper.readValue<List<Donor>>(response)
                users.forEach {
                    try {
                        if (androidId.md5() == it.id || it.id == "all") {
                            return androidId.md5()
                        }
                    } catch (e: Exception) {
                        return@forEach
                    }
                }
                return ""
            } catch (e: Exception) {
                return ""
            }
        }


        fun getVideoLink(id: String, isCasting: Boolean = false): List<ExtractorLink>? {
            val links = mutableListOf<ExtractorLink>()
            allApi.getUrl(id, isCasting) {
                links.add(it)
            }
            return if (links.isNullOrEmpty()) null else links.sortedBy { -it.quality }.distinctBy { it.url }
        }

        fun loadLinks(id: String, isCasting: Boolean, callback: (ExtractorLink) -> Unit): Boolean {
            return allApi.getUrl(id, isCasting) {
                callback.invoke(it)
            }
        }

        fun getRandomAnimePage(usedToken: Token? = currentToken): AnimePage? {
            return try {
                val url = "https://tapi.shiro.is/anime/random/TV?token=${usedToken?.token}"
                val response = khttp.get(url, timeout = SHIRO_TIMEOUT_TIME)
                val mapped = response.let { mapper.readValue<AnimePage>(it.text) }
                if (mapped.status == "Found")
                    mapped
                else null
            } catch (e: Exception) {
                null
            }
        }

        fun getAnimePage(slug: String, usedToken: Token? = currentToken): AnimePage? {
            println("Get anime $slug")
            val url = "https://tapi.shiro.is/anime/slug/${slug}?token=${usedToken?.token}"
            val headers = mapOf("Cache-Control" to "max-stale=$maxStale")
            return try {
                val response = khttp.get(url, timeout = SHIRO_TIMEOUT_TIME, headers = headers)
                val mapped = response.let { mapper.readValue<AnimePage>(it.text) }
                mapped.data.episodes =
                    mapped.data.episodes?.distinctBy { it.episode_number }?.sortedBy { it.episode_number }
                if (mapped.status == "Found")
                    mapped
                else null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        fun getSearchMethods(usedToken: Token? = currentToken): AllSearchMethodsData? {
            try {
                // Tags and years can be added
                val url = "https://tapi.shiro.is/types/all?token=${usedToken?.token}".replace("+", "%20")
                // Security headers
                val headers = usedToken?.headers
                val response = headers?.let { khttp.get(url, timeout = SHIRO_TIMEOUT_TIME) }
                val mapped = response?.let { mapper.readValue<AllSearchMethods>(it.text) }

                if (mapped?.status == "Found") {
                    return mapped.data
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return null
        }

        data class AllAnimeJson(
            @JsonProperty("id") val id: String,
            @JsonProperty("slug") val slug: String,
            @JsonProperty("mal_id") val mal_id: String?,
        )

        fun getMalIDFromSlug(slug: String): String? {
            try {
                val oneDayStale = 60 * 60 * 24
                val headers = mapOf("Cache-Control" to "max-stale=$oneDayStale")
                val res =
                    khttp.get("https://raw.githubusercontent.com/Blatzar/shiro-db/master/anime.json", headers = headers)
                val json = mapper.readValue<List<AllAnimeJson>>(res.text)
                return json.find { it.slug == slug }?.mal_id
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return null
        }

        fun getFirstSearchResultSlug(title: String?): String? {
            // Fallback on search
            val searchResults = title?.let { search(it) }
            val first = searchResults?.get(0)?.slug
            // Prioritizes sub if sub isn't hidden
            return if (settingsManager!!.getString("hide_behavior", "None") != "Hide subbed") {
                searchResults?.find { it.slug == first?.removeSuffix("-dubbed") }?.slug ?: first
            } else {
                searchResults?.find { it.slug == "$first-dubbed" }?.slug ?: first
            }
        }

        fun getSlugFromMalId(malId: String, title: String?): String? {
            try {
                val oneDayStale = 60 * 60 * 24
                val headers = mapOf("Cache-Control" to "max-stale=$oneDayStale")
                val res =
                    khttp.get("https://raw.githubusercontent.com/Blatzar/shiro-db/master/anime.json", headers = headers)
                val json = mapper.readValue<List<AllAnimeJson>>(res.text)
                return json.find { it.mal_id == malId }?.slug
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return null
        }

        fun quickSearch(query: String, usedToken: Token? = currentToken): List<ShiroSearchResponseShow>? {
            try {
                // Tags and years can be added
                val url = "https://tapi.shiro.is/anime/auto-complete/${
                    URLEncoder.encode(
                        query,
                        "UTF-8"
                    )
                }?token=${usedToken?.token}".replace("+", "%20")
                // Security headers
                val headers = usedToken?.headers
                val response = headers?.let { khttp.get(url, timeout = SHIRO_TIMEOUT_TIME) }
                val mapped = response?.let { mapper.readValue<ShiroSearchResponse>(it.text) }

                return if (mapped?.status == "Found")
                    mapped.data
                else null
            } catch (e: Exception) {
                return null
            }
            //return response?.text?.let { mapper.readValue(it) }
        }

        fun search(
            query: String,
            usedToken: Token? = currentToken,
            genresInput: List<Genre>? = null
        ): List<ShiroSearchResponseShow>? {
            try {
                val genres = genresInput?.joinToString(separator = "%2C") { it.slug } ?: ""
                val genresString = if (genres != "") "&genres=$genres" else ""

                val url = "https://tapi.shiro.is/advanced?search=${
                    URLEncoder.encode(
                        query,
                        "UTF-8"
                    )
                }$genresString&token=${usedToken?.token}".replace("+", "%20")
                println(url)
                val headers = usedToken?.headers
                val response = headers?.let { khttp.get(url, timeout = SHIRO_TIMEOUT_TIME) }
                val mapped = response?.let { mapper.readValue<ShiroFullSearchResponse>(it.text) }
                return if (mapped?.status == "Found")
                    mapped.data.nav.currentPage.items
                else null
            } catch (e: Exception) {
                return null
            }
        }

        fun getFullUrlCdn(url: String): String {
            return if (!url.startsWith("http")) {
                "https://cdn.shiro.is/$url"
            } else url
        }

        /*val lastCards = hashMapOf<String, Card>()
        fun getCardById(id: String, canBeCached: Boolean = true): EpisodeResponse? {
            if (canBeCached && lastCards.containsKey(id)) {
                return EpisodeResponse(lastCards[id]!!, 0)
            }
            val url =
                "https://fastani.net/api/data/anime/$id" //?season=$season&episode=$episode" // SPECIFYING EPISODE AND SEASON WILL ONLY GIVE 1 EPISODE
            val response = currentToken?.headers?.let { khttp.get(url, headers = it, cookies = currentToken?.cookies) }
            val resp: EpisodeResponse? = response?.text?.let { mapper.readValue(it) }
            if (resp != null) {
                lastCards[id] = resp.anime
            }
            return resp
        }*/

        var cachedHome: ShiroHomePage? = null


        // OTHERWISE CRASH AT BOOT FROM HAVING OLD FAVORITES SYSTEM
        private fun Context.convertOldFavorites() {
            try {
                val keys = getKeys(BOOKMARK_KEY)
                thread {
                    keys.pmap {
                        getKey<AnimePageData>(it)
                    }
                    keys.forEach {
                        val data = getKey<AnimePageData>(it)
                        if (data != null) {
                            // NEEDS REMOVAL TO PREVENT DUPLICATES
                            removeKey(it)
                            setKey(it, BookmarkedTitle(data.name, data.image, data.slug, data.english))
                        } else {
                            removeKey(it)
                        }
                    }
                    setKey(LEGACY_BOOKMARKS, false)
                }
            } catch (e: Exception) {
                return
            }

        }


        fun Context.getFav(): List<BookmarkedTitle?> {
            val legacyBookmarks = getKey(LEGACY_BOOKMARKS, true)
            if (legacyBookmarks == true) {
                convertOldFavorites()
            }
            val keys = getKeys(BOOKMARK_KEY)

            thread {
                keys.pmap {
                    getKey<BookmarkedTitle>(it)
                }
            }

            return keys.map {
                getKey(it)
            }
        }

        fun Context.getSubbed(): List<BookmarkedTitle?> {
            val keys = getKeys(SUBSCRIPTIONS_BOOKMARK_KEY)

            thread {
                keys.pmap {
                    getKey<BookmarkedTitle>(it)
                }
            }

            return keys.map {
                getKey(it)
            }
        }

        private fun Context.getLastWatch(): List<LastEpisodeInfo?> {
            val keys = getKeys(VIEW_LST_KEY)
            thread {
                keys.pmap {
                    getKey<LastEpisodeInfo>(it)?.id
                }
            }
            return (getKeys(VIEW_LST_KEY).map {
                getKey<LastEpisodeInfo>(it)
            }).sortedBy { if (it == null) 0 else -(it.seenAt) }
        }

        fun Context.requestHome(canBeCached: Boolean = true): ShiroHomePage? {
            if (currentToken == null) return null
            return getHome(canBeCached)
        }

        fun getHomeOnly(usedToken: Token? = currentToken): ShiroHomePage? {
            return try {
                val url = "https://tapi.shiro.is/latest?token=${usedToken!!.token}"
                val response = khttp.get(url, timeout = SHIRO_TIMEOUT_TIME)
                response.text.let { mapper.readValue(it) }
            } catch (e: Exception) {
                println(e.message)
                null
            }
        }

        private fun Context.getHome(canBeCached: Boolean, usedToken: Token? = currentToken): ShiroHomePage? {
            var res: ShiroHomePage? = null
            if (canBeCached && cachedHome != null) {
                res = cachedHome
            } else {
                val url = "https://tapi.shiro.is/latest?token=${usedToken!!.token}"
                try {
                    val headers = mapOf("Cache-Control" to "max-stale=$maxStale")
                    val response = khttp.get(url, timeout = SHIRO_TIMEOUT_TIME, headers = headers)
                    res = response.text.let { mapper.readValue(it) }
                } catch (e: Exception) {
                    println(e.message)
                }
                if (res != null) {
                    res.random = getRandomAnimePage()
                }
                //res?.schedule = getSchedule()
            }
            // Anything below here shouldn't do network requests (network on main thread)
            // (card.removeButton.setOnClickListener {requestHome(true)})

            if (res == null) {
                hasThrownError = 0
                onHomeError.invoke(false)
                return null
            }
            res.favorites = getFav()
            res.subscribed = getSubbed()
            res.recentlySeen = getLastWatch()
            cachedHome = res
            onHomeFetched.invoke(res)
            return res
        }

        var currentToken: Token? = null
        var currentHeaders: MutableMap<String, String>? = null
        var onHomeFetched = Event<ShiroHomePage?>()
        var onTokenFetched = Event<Boolean>()
        var onHomeError = Event<Boolean>() // TRUE IF FULL RELOAD OF TOKEN, FALSE IF JUST HOME
        var hasThrownError = -1

        fun Context.initShiroApi() {
            if (currentToken != null) return

            currentToken = getToken()
            if (currentToken != null) {
                currentHeaders = currentToken?.headers?.toMutableMap()
                currentHeaders?.set("Cookie", "")
                currentToken?.cookies?.forEach {
                    currentHeaders?.set("Cookie", it.key + "=" + it.value.substring(0, it.value.indexOf(';')) + ";")
                }
                onTokenFetched.invoke(true)

                requestHome()
            } else {
                println("TOKEN ERROR")
                hasThrownError = 1
                onHomeError.invoke(true)
            }
        }
    }
}
