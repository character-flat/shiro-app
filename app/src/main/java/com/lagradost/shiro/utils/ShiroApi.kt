package com.lagradost.shiro.utils

import android.annotation.SuppressLint
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
import com.lagradost.shiro.utils.extractors.Shiro
import com.lagradost.shiro.utils.extractors.Vidstream
import khttp.structures.cookie.CookieJar
import java.lang.Exception
import java.net.URLEncoder
import kotlin.concurrent.thread

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
    ) : CommonAnimePage

    interface CommonAnimePage {
        val name: String
        val image: String
        val slug: String
    }

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; rv:68.0) Gecko/20100101 Firefox/68.0"
        private val mapper = JsonMapper.builder().addModule(KotlinModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()

        // NULL IF ERROR
        fun getToken(): Token? {
            try {
                val headers = mapOf("User-Agent" to USER_AGENT)
                val shiro = khttp.get("https://shiro.is", headers = headers, timeout = 120.0)
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
                println(e)
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
            return if(links.isNullOrEmpty()) null else links.sortedBy { -it.quality }.distinctBy { it.url }
        }

        fun loadLinks(id: String, isCasting: Boolean, callback: (ExtractorLink) -> Unit): Boolean {
            return allApi.getUrl(id, isCasting) {
                callback.invoke(it)
            }
        }

        fun getRandomAnimePage(usedToken: Token? = currentToken): AnimePage? {
            println("Called random")
            return try {
                val url = "https://tapi.shiro.is/anime/random/TV?token=${usedToken?.token}"
                val response = khttp.get(url, timeout = 120.0)
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
            return try {
                val response = khttp.get(url, timeout = 120.0)
                val mapped = response.let { mapper.readValue<AnimePage>(it.text) }
                mapped.data.episodes = mapped.data.episodes?.distinctBy { it.episode_number }
                if (mapped.status == "Found")
                    mapped
                else null
            } catch (e: Exception) {
                println(e.message)
                null
            }
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
                val response = headers?.let { khttp.get(url, timeout = 120.0) }
                val mapped = response?.let { mapper.readValue<ShiroSearchResponse>(it.text) }

                return if (mapped?.status == "Found")
                    mapped.data
                else null
            } catch (e: Exception) {
                return null
            }
            //return response?.text?.let { mapper.readValue(it) }
        }

        fun search(query: String, usedToken: Token? = currentToken): List<ShiroSearchResponseShow>? {
            try {
                val url = "https://tapi.shiro.is/advanced?search=${
                    URLEncoder.encode(
                        query,
                        "UTF-8"
                    )
                }&token=${usedToken?.token}".replace("+", "%20")
                val headers = usedToken?.headers
                val response = headers?.let { khttp.get(url, timeout = 120.0) }
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
        private fun convertOldFavorites() {
            try {
                val keys = DataStore.getKeys(BOOKMARK_KEY)
                thread {
                    keys.pmap {
                        DataStore.getKey<AnimePageData>(it)
                    }
                    keys.forEach {
                        val data = DataStore.getKey<AnimePageData>(it)
                        if (data != null) {
                            // NEEDS REMOVAL TO PREVENT DUPLICATES
                            DataStore.removeKey(it)
                            DataStore.setKey(it, BookmarkedTitle(data.name, data.image, data.slug))
                        } else {
                            DataStore.removeKey(it)
                        }
                    }
                    DataStore.setKey(LEGACY_BOOKMARKS, false)
                }
            } catch (e: Exception) {
                return
            }

        }


        private fun getFav(): List<BookmarkedTitle?> {
            val legacyBookmarks = DataStore.getKey(LEGACY_BOOKMARKS, true)
            if (legacyBookmarks == true) {
                convertOldFavorites()
            }
            val keys = DataStore.getKeys(BOOKMARK_KEY)

            thread {
                keys.pmap {
                    DataStore.getKey<BookmarkedTitle>(it)
                }
            }

            return keys.map {
                DataStore.getKey(it)
            }
        }

        private fun getLastWatch(): List<LastEpisodeInfo?> {
            val keys = DataStore.getKeys(VIEW_LST_KEY)
            println("KEYS: $keys")
            thread {
                keys.pmap {
                    DataStore.getKey<LastEpisodeInfo>(it)?.id
                }
            }
            return (DataStore.getKeys(VIEW_LST_KEY).map {
                DataStore.getKey<LastEpisodeInfo>(it)
            }).sortedBy { if (it == null) 0 else -(it.seenAt) }
        }

        fun requestHome(canBeCached: Boolean = true): ShiroHomePage? {
            println("LOAD HOME $currentToken")
            if (currentToken == null) return null
            return getHome(canBeCached)
        }

        fun getHomeOnly(usedToken: Token? = currentToken): ShiroHomePage? {
            return try {
                val url = "https://tapi.shiro.is/latest?token=${usedToken!!.token}"
                val response = khttp.get(url, timeout = 120.0)
                response.text.let { mapper.readValue(it) }
            } catch (e: Exception) {
                println(e.message)
                null
            }
        }

        fun getHome(canBeCached: Boolean, usedToken: Token? = currentToken): ShiroHomePage? {
            var res: ShiroHomePage? = null
            println("HOME GETTING FETCHED ")
            if (canBeCached && cachedHome != null) {
                res = cachedHome
            } else {
                val url = "https://tapi.shiro.is/latest?token=${usedToken!!.token}"
                try {
                    val response = khttp.get(url, timeout = 120.0)
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
            res.recentlySeen = getLastWatch()
            cachedHome = res
            onHomeFetched.invoke(res)
            return res
        }

        private var currentToken: Token? = null
        var currentHeaders: MutableMap<String, String>? = null
        var onHomeFetched = Event<ShiroHomePage?>()
        var onHomeError = Event<Boolean>() // TRUE IF FULL RELOAD OF TOKEN, FALSE IF JUST HOME
        var hasThrownError = -1

        fun init() {
            if (currentToken != null) return

            currentToken = getToken()
            if (currentToken != null) {
                currentHeaders = currentToken?.headers?.toMutableMap()
                currentHeaders?.set("Cookie", "")
                currentToken?.cookies?.forEach {
                    currentHeaders?.set("Cookie", it.key + "=" + it.value.substring(0, it.value.indexOf(';')) + ";")
                }
                requestHome()
            } else {
                println("TOKEN ERROR")
                hasThrownError = 1
                onHomeError.invoke(true)
            }
        }
    }
}
