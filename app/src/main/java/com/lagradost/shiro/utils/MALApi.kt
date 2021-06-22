package com.lagradost.shiro.utils

import android.app.Activity
import android.util.Base64
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.shiro.utils.AppUtils.openBrowser
import com.lagradost.shiro.utils.AppUtils.splitQuery
import com.lagradost.shiro.utils.AppUtils.unixTime
import java.net.URL
import java.security.SecureRandom
import kotlin.concurrent.thread

const val MAL_CLIENT_ID: String = "8c25dbc2c2ea8adb0e1901c7e923aa78"
const val MAL_ACCOUNT_ID = "0" // MIGHT WANT TO BE USED IF YOU WANT MULTIPLE ACCOUNT LOGINS

class MALApi {
    companion object {
        private val mapper = JsonMapper.builder().addModule(KotlinModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()!!

        private var requestId = 0
        private var codeVerifier = ""

        fun Activity.authenticateMAL() {
            // It is recommended to use a URL-safe string as code_verifier.
            // See section 4 of RFC 7636 for more details.

            val secureRandom = SecureRandom()
            val codeVerifierBytes = ByteArray(96) // base64 has 6bit per char; (8/6)*96 = 128
            secureRandom.nextBytes(codeVerifierBytes)
            codeVerifier =
                Base64.encodeToString(codeVerifierBytes, Base64.DEFAULT).trimEnd('=').replace("+", "-")
                    .replace("/", "_").replace("\n", "")
            val codeChallenge = codeVerifier
            println("codeVerifier$codeVerifier")
            val request =
                "https://myanimelist.net/v1/oauth2/authorize?response_type=code&client_id=$MAL_CLIENT_ID&code_challenge=$codeChallenge&state=RequestID$requestId"
            openBrowser(request)
        }

        fun authenticateLogin(data: String) {
            try {
                val sanitizer =
                    splitQuery(URL(data.replace("shiroapp", "https").replace("/#", "?"))) // FIX ERROR
                val state = sanitizer["state"]!!
                if (state == "RequestID$requestId") {
                    val currentCode = sanitizer["code"]!!
                    thread {
                        var res = ""
                        try {
                            //println("cc::::: " + codeVerifier)
                            res = khttp.post(
                                "https://myanimelist.net/v1/oauth2/token",
                                data = mapOf(
                                    "client_id" to MAL_CLIENT_ID,
                                    "code" to currentCode,
                                    "code_verifier" to codeVerifier,
                                    "grant_type" to "authorization_code"
                                )
                            ).text
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        if (res != "") {
                            storeToken(res)
                            //println("GOT MAL MASTER TOKEN:::: " + res)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun storeToken(response: String) {
            try {
                if (response != "") {
                    val token = mapper.readValue<ResponseToken>(response)
                    DataStore.setKey(MAL_UNIXTIME_KEY, MAL_ACCOUNT_ID, (token.expires_in + unixTime()))
                    DataStore.setKey(MAL_REFRESH_TOKEN_KEY, MAL_ACCOUNT_ID, token.refresh_token)
                    DataStore.setKey(MAL_TOKEN_KEY, MAL_ACCOUNT_ID, token.access_token)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun refreshToken() {
            try {
                val res = khttp.post(
                    "https://myanimelist.net/v1/oauth2/token",
                    data = mapOf(
                        "client_id" to MAL_CLIENT_ID,
                        "grant_type" to "refresh_token",
                        "refresh_token" to DataStore.getKey(
                            MAL_REFRESH_TOKEN_KEY, MAL_ACCOUNT_ID)!!
                    )
                ).text
                storeToken(res)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private val allTitles = hashMapOf<Int, MalTitleHolder>()

        fun getDataAboutId(id: Int): MalAnime? {
            return try {
                // https://myanimelist.net/apiconfig/references/api/v2#operation/anime_anime_id_get
                val url = "https://api.myanimelist.net/v2/anime/$id?fields=id,title,num_episodes,my_list_status"
                val res = khttp.get(
                    url, headers = mapOf(
                        "Authorization" to "Bearer " + DataStore.getKey<String>(
                            MAL_TOKEN_KEY,
                            MAL_ACCOUNT_ID
                        )!!
                    )
                ).text
                mapper.readValue<MalAnime>(res)
            } catch (e : Exception) {
                null
            }
        }

        fun setAllMalData() {
            val user: String = "@me"
            var isDone = false
            var index = 0
            allTitles.clear()
            checkToken()
            while (!isDone) {
                val res = khttp.get(
                    "https://api.myanimelist.net/v2/users/$user/animelist?fields=list_status&limit=1000&offset=${index * 1000}",
                    headers = mapOf(
                        "Authorization" to "Bearer " + DataStore.getKey<String>(
                            MAL_TOKEN_KEY,
                            MAL_ACCOUNT_ID
                        )!!
                    )
                ).text
                val values = mapper.readValue<MalRoot>(res)
                val titles = values.data.map { MalTitleHolder(it.list_status, it.node.id, it.node.title) }
                for (t in titles) {
                    allTitles[t.id] = t
                }
                isDone = titles.size < 1000
                index++
            }
        }

        private fun checkToken() {
            if (unixTime() > DataStore.getKey<Long>(
                    MAL_UNIXTIME_KEY, MAL_ACCOUNT_ID)!!) {
                refreshToken()
            }
        }

        fun getUser(setSettings: Boolean = true): MalUser? {
            checkToken()
            return try {
                val res = khttp.get(
                    "https://api.myanimelist.net/v2/users/@me",
                    headers = mapOf(
                        "Authorization" to "Bearer " + DataStore.getKey<String>(
                            MAL_TOKEN_KEY,
                            MAL_ACCOUNT_ID
                        )!!
                    )
                ).text

                val user = mapper.readValue<MalUser>(res)
                if (setSettings) {
                    DataStore.setKey(MAL_USER_KEY, MAL_ACCOUNT_ID, user)
                }
                user
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        val malStatusAsString = arrayOf("watching", "completed", "on_hold", "dropped", "plan_to_watch")

        enum class MalStatusType(var value: Int) {
            Watching(0),
            Completed(1),
            OnHold(2),
            Dropped(3),
            PlanToWatch(4),
            None(-1)
        }

        fun fromIntToAnimeStatus(inp: Int): MalStatusType {//= AniListStatusType.values().first { it.value == inp }
            return when (inp) {
                -1 -> MalStatusType.None
                0 -> MalStatusType.Watching
                1 -> MalStatusType.Completed
                2 -> MalStatusType.OnHold
                3 -> MalStatusType.Dropped
                4 -> MalStatusType.PlanToWatch
                5 -> MalStatusType.Watching
                else -> MalStatusType.None
            }
        }

        fun setScoreRequest(
            id: Int,
            status: MalStatusType? = null,
            score: Int? = null,
            num_watched_episodes: Int? = null,
        ): Boolean {
            val res = setScoreRequest(
                id,
                if (status == null) null else malStatusAsString[maxOf(0, status.value)],
                score,
                num_watched_episodes
            )
            if (res != "") {
                return try {
                    val status = mapper.readValue<MalStatus>(res)
                    if (allTitles.containsKey(id)) {
                        val currentTitle = allTitles[id]!!
                        allTitles[id] = MalTitleHolder(status, id, currentTitle.name)
                    } else {
                        allTitles[id] = MalTitleHolder(status, id, "")
                    }
                    true
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            } else {
                return false
            }
        }

        private fun setScoreRequest(
            id: Int,
            status: String? = null,
            score: Int? = null,
            num_watched_episodes: Int? = null,
        ): String {
            return try {
                khttp.put(
                    "https://api.myanimelist.net/v2/anime/$id/my_list_status",
                    headers = mapOf(
                        "Authorization" to "Bearer " + DataStore.getKey<String>(
                            MAL_TOKEN_KEY,
                            MAL_ACCOUNT_ID
                        )!!
                    ),
                    data = mapOf("status" to status, "score" to score, "num_watched_episodes" to num_watched_episodes)
                ).text
            } catch (e: Exception) {
                e.printStackTrace()
                return ""
            }
        }
    }

    data class ResponseToken(
        @JsonProperty("token_type") val token_type: String,
        @JsonProperty("expires_in") val expires_in: Int,
        @JsonProperty("access_token") val access_token: String,
        @JsonProperty("refresh_token") val refresh_token: String,
    )

    data class MalRoot(
        @JsonProperty("data") val data: List<MalDatum>,
    )

    data class MalDatum(
        @JsonProperty("node") val node: MalNode,
        @JsonProperty("list_status") val list_status: MalStatus,
    )

    data class MalNode(
        @JsonProperty("id") val id: Int,
        @JsonProperty("title") val title: String,
        /*
        also, but not used
        main_picture ->
            public string medium;
			public string large;
         */
    )

    data class MalStatus(
        @JsonProperty("status") val status: String,
        @JsonProperty("score") val score: Int,
        @JsonProperty("num_episodes_watched") val num_episodes_watched: Int,
        @JsonProperty("is_rewatching") val is_rewatching: Boolean,
        @JsonProperty("updated_at") val updated_at: String,
    )

    data class MalUser(
        @JsonProperty("id") val id: Int,
        @JsonProperty("name") val name: String,
        @JsonProperty("location") val location: String,
        @JsonProperty("joined_at") val joined_at: String,
        @JsonProperty("picture") val picture: String,
    )

    // Used for getDataAboutId()
    data class MalAnime(
        @JsonProperty("id") val id: Int,
        @JsonProperty("title") val title: String,
        @JsonProperty("num_episodes") val num_episodes: Int,
        @JsonProperty("my_list_status") val my_list_status: MalStatus?
    )

    data class MalTitleHolder(
        val status: MalStatus,
        val id: Int,
        val name: String,
    )
}