package com.lagradost.shiro.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule

const val PREFERENCES_NAME: String = "rebuild_preference"
const val VIEW_POS_KEY: String = "view_pos" // VIDEO POSITION
const val VIEW_DUR_KEY: String = "view_dur" // VIDEO DURATION
const val VIEW_LST_KEY: String = "view_lst_watched" // LAST WATCHED, ONE PER TITLE ID
const val BOOKMARK_KEY: String = "bookmark" // BOOKMARK/FAVORITE BookmarkedTitle
const val VIEWSTATE_KEY: String = "viewstate" // BOOKMARK/FAVORITE BookmarkedTitle
const val DOWNLOAD_CHILD_KEY: String = "dload_child" // DownloadFileMetadata
const val DOWNLOAD_PARENT_KEY: String = "dload_parent" // DownloadParentFileMetadata
const val PLAYBACK_SPEED_KEY: String = "playback_speed" // Last used playback speed
const val RESIZE_MODE_KEY: String = "resize_mode" // Last used resize mode

const val LEGACY_BOOKMARKS: String = "legacy_bookmarks" // for converting old bookmark system

const val ANILIST_UNIXTIME_KEY: String = "anilist_unixtime" // When token expires
const val MAL_UNIXTIME_KEY: String = "mal_unixtime" // When token expires

const val ANILIST_TOKEN_KEY: String = "anilist_token" // anilist token for api
const val MAL_TOKEN_KEY: String = "mal_token" // anilist token for api
const val MAL_REFRESH_TOKEN_KEY: String = "mal_refresh_token" // refresh token

const val ANILIST_USER_KEY: String = "anilist_user" // user data like profile
const val MAL_USER_KEY: String = "mal_user" // user data like profile


@SuppressLint("StaticFieldLeak")
object DataStore {

    val mapper = JsonMapper.builder().addModule(KotlinModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()!!

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    private var localContext: Context? = null

    fun getSharedPrefs(): SharedPreferences {
        return getPreferences(localContext!!)
    }

    fun init(_context: Context) {
        localContext = _context
    }

    fun getFolderName(folder: String, path: String): String {
        return "${folder}/${path}"
    }

    fun getKeys(folder: String): List<String> {
        return getSharedPrefs().all.keys.filter { it.startsWith(folder) }
    }

    fun removeKey(folder: String, path: String) {
        removeKey(
            getFolderName(
                folder,
                path
            )
        )
    }

    fun containsKey(folder: String, path: String): Boolean {
        return containsKey(
            getFolderName(
                folder,
                path
            )
        )
    }

    fun containsKey(path: String): Boolean {
        val prefs = getSharedPrefs()
        return prefs.contains(path)
    }

    fun removeKey(path: String) {
        val prefs = getSharedPrefs()
        if (prefs.contains(path)) {
            val editor: SharedPreferences.Editor = prefs.edit()
            editor.remove(path)
            editor.apply()
        }
    }

    fun removeKeys(folder: String): Int {
        val keys = DataStore.getKeys(folder)
        keys.forEach { value ->
            removeKey(value)
        }
        return keys.size
    }

    fun <T> setKey(path: String, value: T) {
        val editor: SharedPreferences.Editor = getSharedPrefs().edit()
        editor.putString(path, mapper.writeValueAsString(value))
        editor.apply()
    }

    fun <T> setKey(folder: String, path: String, value: T) {
        setKey(
            getFolderName(
                folder,
                path
            ), value
        )
    }

    inline fun <reified T : Any> String.toKotlinObject(): T {
        return mapper.readValue(this, T::class.java)
    }

    // GET KEY GIVEN PATH AND DEFAULT VALUE, NULL IF ERROR
    inline fun <reified T : Any> getKey(path: String, defVal: T?): T? {
        try {
            val json: String = getSharedPrefs()
                .getString(path, null) ?: return defVal
            return json.toKotlinObject()
        } catch (e: Exception) {
            return null
        }
    }

    inline fun <reified T : Any> getKey(path: String): T? {
        return getKey(path, null)
    }

    inline fun <reified T : Any> getKey(folder: String, path: String): T? {
        return getKey(
            getFolderName(
                folder,
                path
            ), null
        )
    }

    inline fun <reified T : Any> getKey(folder: String, path: String, defVal: T?): T? {
        return getKey(
            getFolderName(
                folder,
                path
            ), defVal
        )
    }
}