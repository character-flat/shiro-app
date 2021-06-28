package com.lagradost.shiro.utils

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.app.UiModeManager
import android.content.Context
import android.content.Context.UI_MODE_SERVICE
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.database.Cursor
import android.graphics.Color
import android.graphics.Rect
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import android.util.TypedValue
import android.view.TouchDelegate
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import com.lagradost.shiro.R
import com.lagradost.shiro.ui.BookmarkedTitle
import com.lagradost.shiro.ui.EpisodePosDurInfo
import com.lagradost.shiro.ui.LastEpisodeInfo
import com.lagradost.shiro.ui.MainActivity.Companion.activity
import com.lagradost.shiro.ui.MainActivity.Companion.masterViewModel
import com.lagradost.shiro.ui.NextEpisode
import com.lagradost.shiro.ui.home.CardAdapter
import com.lagradost.shiro.ui.home.CardContinueAdapter
import com.lagradost.shiro.ui.home.EXPANDED_HOME_FRAGMENT_TAG
import com.lagradost.shiro.ui.home.ExpandedHomeFragment
import com.lagradost.shiro.ui.home.HomeFragment.Companion.homeViewModel
import com.lagradost.shiro.ui.player.PLAYER_FRAGMENT_TAG
import com.lagradost.shiro.ui.player.PlayerActivity.Companion.playerActivity
import com.lagradost.shiro.ui.player.PlayerData
import com.lagradost.shiro.ui.player.PlayerFragment
import com.lagradost.shiro.ui.result.RESULT_FRAGMENT_TAG
import com.lagradost.shiro.ui.result.ResultFragment
import com.lagradost.shiro.ui.tv.PlayerFragmentTv
import com.lagradost.shiro.ui.tv.TvActivity.Companion.tvActivity
import com.lagradost.shiro.utils.DataStore.mapper
import com.lagradost.shiro.utils.ShiroApi.Companion.getFav
import com.lagradost.shiro.utils.ShiroApi.Companion.getSubbed
import com.lagradost.shiro.utils.extractors.Vidstream
import java.io.*
import java.net.URL
import java.net.URLDecoder
import java.security.MessageDigest
import kotlin.concurrent.thread
import kotlin.math.roundToInt


object AppUtils {
    var settingsManager: SharedPreferences? = null
    var allApi: Vidstream = Vidstream()
    fun FragmentActivity.init() {
        settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        allApi.providersActive = settingsManager?.getStringSet("selected_providers", hashSetOf()) as HashSet<String>
    }

    fun unixTime(): Long {
        return System.currentTimeMillis() / 1000L
    }

    fun FragmentActivity.isTv(): Boolean {
        val uiModeManager: UiModeManager = getSystemService(UI_MODE_SERVICE) as UiModeManager
        return uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    fun Activity.getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            resources.getDimensionPixelSize(resourceId)
        } else
            0
    }

    // Guarantee slug is dubbed or not
    fun ShiroApi.AnimePageData.dubbify(turnDubbed: Boolean): ShiroApi.AnimePageData {
        return this.copy(
            slug = if (turnDubbed) {
                slug.removeSuffix("-dubbed") + "-dubbed"
            } else {
                slug.removeSuffix("-dubbed")
            }
        )
    }

    fun getTheme(): Int? {
        return when (settingsManager?.getString("accent_color", "red")) {
            "Default Red" -> null
            "Blue" -> R.style.OverlayPrimaryColorBlue
            "Purple" -> R.style.OverlayPrimaryColorPurple
            "Green" -> R.style.OverlayPrimaryColorGreenApple
            "Pink" -> R.style.OverlayPrimaryColorPink
            "Orange" -> R.style.OverlayPrimaryColorOrange
            "Lime" -> R.style.OverlayPrimaryColorLime
            "Yellow" -> R.style.OverlayPrimaryColorYellow
            "Light Blue" -> R.style.OverlayPrimaryColorLightBlue
            else -> null
        }
    }

    // Guarantee slug is dubbed or not
    fun String.dubbify(turnDubbed: Boolean): String {
        return if (turnDubbed) {
            this.removeSuffix("-dubbed") + "-dubbed"
        } else {
            this.removeSuffix("-dubbed")
        }
    }

    fun expandTouchArea(bigView: View?, smallView: View?, extraPadding: Int) {
        bigView?.post {
            val rect = Rect()
            smallView?.getHitRect(rect)
            rect.top -= extraPadding
            rect.left -= extraPadding
            rect.right += extraPadding
            rect.bottom += extraPadding
            bigView.touchDelegate = TouchDelegate(rect, smallView)
        }
    }

    // Copied from https://github.com/videolan/vlc-android/blob/master/application/vlc-android/src/org/videolan/vlc/util/FileUtils.kt
    fun Context.getUri(data: Uri?): Uri? {
        var uri = data
        val ctx = this
        if (data != null && data.scheme == "content") {
            // Mail-based apps - download the stream to a temporary file and play it
            if ("com.fsck.k9.attachmentprovider" == data.host || "gmail-ls" == data.host) {
                var inputStream: InputStream? = null
                var os: OutputStream? = null
                var cursor: Cursor? = null
                try {
                    cursor = ctx.contentResolver.query(
                        data,
                        arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null
                    )
                    if (cursor != null && cursor.moveToFirst()) {
                        val filename = cursor.getString(cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME))
                            .replace("/", "")
                        inputStream = ctx.contentResolver.openInputStream(data)
                        if (inputStream == null) return data
                        os = FileOutputStream(Environment.getExternalStorageDirectory().path + "/Download/" + filename)
                        val buffer = ByteArray(1024)
                        var bytesRead = inputStream.read(buffer)
                        while (bytesRead >= 0) {
                            os.write(buffer, 0, bytesRead)
                            bytesRead = inputStream.read(buffer)
                        }
                        uri =
                            Uri.fromFile(File(Environment.getExternalStorageDirectory().path + "/Download/" + filename))
                    }
                } catch (e: Exception) {
                    return null
                } finally {
                    inputStream?.close()
                    os?.close()
                    cursor?.close()
                }
            } else if (data.authority == "media") {
                uri = this.contentResolver.query(
                    data,
                    arrayOf(MediaStore.Video.Media.DATA), null, null, null
                )?.use {
                    val columnIndex = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                    if (it.moveToFirst()) Uri.fromFile(File(it.getString(columnIndex))) ?: data else data
                }
                //uri = MediaUtils.getContentMediaUri(data)
                /*} else if (data.authority == ctx.getString(R.string.tv_provider_authority)) {
                    println("TV AUTHORITY")
                    //val medialibrary = Medialibrary.getInstance()
                    //val media = medialibrary.getMedia(data.lastPathSegment!!.toLong())
                    uri = null//media.uri*/
            } else {
                val inputPFD: ParcelFileDescriptor?
                try {
                    inputPFD = ctx.contentResolver.openFileDescriptor(data, "r")
                    if (inputPFD == null) return data
                    uri = Uri.parse("fd://" + inputPFD.fd)
                    //                    Cursor returnCursor =
                    //                            getContentResolver().query(data, null, null, null, null);
                    //                    if (returnCursor != null) {
                    //                        if (returnCursor.getCount() > 0) {
                    //                            int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    //                            if (nameIndex > -1) {
                    //                                returnCursor.moveToFirst();
                    //                                title = returnCursor.getString(nameIndex);
                    //                            }
                    //                        }
                    //                        returnCursor.close();
                    //                    }
                } catch (e: FileNotFoundException) {
                    Log.e("TAG", "${e.message} for $data", e)
                    return null
                } catch (e: IllegalArgumentException) {
                    Log.e("TAG", "${e.message} for $data", e)
                    return null
                } catch (e: IllegalStateException) {
                    Log.e("TAG", "${e.message} for $data", e)
                    return null
                } catch (e: NullPointerException) {
                    Log.e("TAG", "${e.message} for $data", e)
                    return null
                } catch (e: SecurityException) {
                    Log.e("TAG", "${e.message} for $data", e)
                    return null
                }
            }// Media or MMS URI
        }
        return uri
    }

    private fun toggleHeart(name: String, image: String, slug: String): Boolean {
        /*Saving the new bookmark in the database*/
        val isBookmarked = DataStore.getKey<BookmarkedTitle>(BOOKMARK_KEY, slug, null) != null
        if (!isBookmarked) {
            DataStore.setKey(
                BOOKMARK_KEY,
                slug,
                BookmarkedTitle(
                    name,
                    image,
                    slug
                )
            )
        } else {
            DataStore.removeKey(BOOKMARK_KEY, slug)
        }
        thread {
            homeViewModel!!.favorites.postValue(getFav())
        }
        return !isBookmarked
    }

    fun Context.onLongCardClick(card: ShiroApi.CommonAnimePage): Boolean {
        var isBookmarked: Boolean? = null
        val selected =
            if (tvActivity != null && settingsManager?.getBoolean("hold_to_favorite", false) == true) "Favorite"
            else settingsManager?.getString("hold_behavior", "Show Toast")

        if (selected == "Favorite" || selected == "Favorite and Subscribe") {
            isBookmarked = toggleHeart(card.name, card.image, card.slug)
            val prefix = if (isBookmarked) "Added" else "Removed"
            Toast.makeText(this, "$prefix ${card.name}", Toast.LENGTH_SHORT).show()
        }
        if (selected == "Subscribe" || selected == "Favorite and Subscribe") {
            subscribeToShow(card/*, isBookmarked*/)
        }
        if (selected == "Show Toast") {
            Toast.makeText(this, card.name, Toast.LENGTH_SHORT).show()
        }
        return isBookmarked ?: false
    }

    // TODO USE THIS IN RESULT_FRAGMENT
    private fun subscribeToShow(data: ShiroApi.CommonAnimePage/*, isBookmarked: Boolean? = null*/) {
        // isBookmarked
        val subbedBookmark = DataStore.getKey<BookmarkedTitle>(SUBSCRIPTIONS_BOOKMARK_KEY, data.slug, null)
        val isSubbedOld = DataStore.getKey(SUBSCRIPTIONS_KEY, data.slug, false)!!
        val isSubbed = isSubbedOld || subbedBookmark != null

        if (isSubbed /*&& !(isBookmarked ?: !isSubbed)*/) {
            Firebase.messaging.unsubscribeFromTopic(data.slug)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        DataStore.removeKey(SUBSCRIPTIONS_BOOKMARK_KEY, data.slug)
                        DataStore.removeKey(SUBSCRIPTIONS_KEY, data.slug)
                    }
                    var msg = "Unsubscribed to ${data.name}"//getString(R.string.msg_subscribed)
                    if (!task.isSuccessful) {
                        msg = "Unsubscribing failed :("//getString(R.string.msg_subscribe_failed)
                    }
                    thread {
                        homeViewModel!!.subscribed.postValue(getSubbed())
                    }
                    //Log.d(TAG, msg)
                    Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
                }
        } else /*if (!isSubbed && (isBookmarked ?: !isSubbed))*/ {
            Firebase.messaging.subscribeToTopic(data.slug)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        DataStore.setKey(
                            SUBSCRIPTIONS_BOOKMARK_KEY, data.slug, BookmarkedTitle(
                                data.name,
                                data.image,
                                data.slug
                            )
                        )
                    }
                    var msg = "Subscribed to ${data.name}"//getString(R.string.msg_subscribed)
                    if (!task.isSuccessful) {
                        msg = "Subscription failed :("//getString(R.string.msg_subscribe_failed)
                    }
                    thread {
                        homeViewModel!!.subscribed.postValue(getSubbed())
                    }
                    //Log.d(TAG, msg)
                    Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
                }
        }
    }


    fun adjustAlpha(@ColorInt color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).roundToInt()
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return Color.argb(alpha, red, green, blue)
    }

    fun FragmentActivity.addFragmentOnlyOnce(location: Int, fragment: Fragment, tag: String) {
        // Make sure the current transaction finishes first

        supportFragmentManager.executePendingTransactions()

        // If there is no fragment yet with this tag...
        if (supportFragmentManager.findFragmentByTag(tag) == null) {
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.enter, R.anim.exit, R.anim.pop_enter, R.anim.pop_exit)
                .add(location, fragment, tag)
                .commitAllowingStateLoss()
        }
    }

    fun Context.isCastApiAvailable(): Boolean {
        val isCastApiAvailable =
            GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(applicationContext) == ConnectionResult.SUCCESS
        try {
            applicationContext?.let { CastContext.getSharedInstance(it) }
        } catch (e: Exception) {
            // track non-fatal
            return false
        }
        return isCastApiAvailable
    }


    fun getViewKey(data: PlayerData): String {
        return getViewKey(
            data.slug,
            data.episodeIndex!!
        )
    }

    fun getViewKey(id: String, episodeIndex: Int): String {
        return id + "E" + episodeIndex
    }

    fun Context.hideKeyboard(view: View) {
        val inputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }

    fun Fragment.hideKeyboard() {
        view.let {
            if (it != null) {
                activity?.hideKeyboard(it)
            }
        }
    }

    fun getCurrentActivity(): AppCompatActivity? {
        return when {
            activity != null -> activity
            tvActivity != null -> tvActivity
            playerActivity != null -> playerActivity
            else -> null
        }
    }

    fun Activity.requestAudioFocus(focusRequest: AudioFocusRequest?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.requestAudioFocus(focusRequest)
        } else {
            val audioManager: AudioManager =
                getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }

    fun Activity.changeStatusBarState(hide: Boolean): Int {
        return if (hide) {
            window?.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
            0
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            getStatusBarHeight()
        }
    }

    fun Activity.openBrowser(url: String) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(url)
        startActivity(intent)
    }

    fun getViewPosDur(aniListId: String, episodeIndex: Int): EpisodePosDurInfo {
        val key = getViewKey(aniListId, episodeIndex)

        return EpisodePosDurInfo(
            DataStore.getKey(VIEW_POS_KEY, key, -1L)!!,
            DataStore.getKey(VIEW_DUR_KEY, key, -1L)!!,
            DataStore.containsKey(VIEWSTATE_KEY, key)
        )
    }

    fun canPlayNextEpisode(card: ShiroApi.AnimePageData?, episodeIndex: Int): NextEpisode {
        val canNext = card!!.episodes!!.size > episodeIndex + 1

        return if (canNext) {
            NextEpisode(true, episodeIndex + 1, 0)
        } else {
            NextEpisode(false, episodeIndex + 1, 0)
        }

    }

    fun getLatestSeenEpisode(data: ShiroApi.AnimePageData): NextEpisode {
        for (i in (data.episodes?.size?.minus(1) ?: 0) downTo 0) {
            val firstPos = getViewPosDur(data.slug, i)
            if (firstPos.viewstate) {
                return NextEpisode(true, i, 0)
            }
        }
        return NextEpisode(false, 0, 0)
    }


    fun FragmentActivity.displayCardData(
        data: List<ShiroApi.CommonAnimePage?>?,
        resView: RecyclerView,
        textView: TextView,
        isOnTop: Boolean = false,
        adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>? = null,
        overrideHideDubbed: Boolean = false
    ) {
        val newAdapter: RecyclerView.Adapter<RecyclerView.ViewHolder> = adapter ?: CardAdapter(
            this,
            ArrayList(),
            isOnTop
        )

        //val snapHelper = PagerSnapHelper()
        //snapHelper.attachToRecyclerView(scrollView)

        val hideDubbed = settingsManager!!.getBoolean("hide_dubbed", false) && !overrideHideDubbed
        val filteredData = if (hideDubbed) data?.filter { it?.name?.endsWith("Dubbed") == false } else data
        resView.adapter = newAdapter

        (ArrayList(filteredData) as? ArrayList<ShiroApi.CommonAnimePage?>)?.let {
            (resView.adapter as CardAdapter).cardList = it
        }
        (resView.adapter as CardAdapter).notifyDataSetChanged()

        val layoutId = if (tvActivity != null) R.id.home_root_tv else R.id.homeRoot
        textView.setOnClickListener {
            if (this.supportFragmentManager.findFragmentByTag(EXPANDED_HOME_FRAGMENT_TAG) != null) return@setOnClickListener
            this.supportFragmentManager.beginTransaction()
                .setCustomAnimations(
                    R.anim.enter_from_right,
                    R.anim.exit_to_right,
                    R.anim.enter_from_right,
                    R.anim.exit_to_right
                )
                .add(
                    layoutId,
                    ExpandedHomeFragment.newInstance(
                        mapper.writeValueAsString(data),
                        textView.text.toString()
                    ),
                    EXPANDED_HOME_FRAGMENT_TAG
                )
                .commitAllowingStateLoss()
        }
    }

    fun FragmentActivity.displayCardData(
        data: List<LastEpisodeInfo?>?,
        resView: RecyclerView,
        isOnTop: Boolean = false
    ) {
        val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder> = CardContinueAdapter(
            this,
            listOf(),
            isOnTop
        )

        //val snapHelper = LinearSnapHelper()
        //snapHelper.attachToRecyclerView(scrollView)

        resView.adapter = adapter
        if (data != null) {
            (resView.adapter as CardContinueAdapter).cardList = data
            (resView.adapter as CardContinueAdapter).notifyDataSetChanged()
        }
    }


    fun getNextEpisode(data: ShiroApi.AnimePageData): NextEpisode {
        // HANDLES THE LOGIC FOR NEXT EPISODE
        var episodeIndex = 0
        var seasonIndex = 0
        val maxValue = 90
        val firstPos = getViewPosDur(data.slug, 0)
        // Hacky but works :)
        if (((firstPos.pos * 100) / firstPos.dur <= maxValue || firstPos.pos == -1L) && !firstPos.viewstate) {
            val found = data.episodes?.getOrNull(episodeIndex) != null
            return NextEpisode(found, episodeIndex, seasonIndex)
        }

        while (true) { // IF PROGRESS IS OVER 95% CONTINUE SEARCH FOR NEXT EPISODE
            val next = canPlayNextEpisode(data, episodeIndex)
            if (next.isFound) {
                val nextPro = getViewPosDur(data.slug, next.episodeIndex)
                seasonIndex = next.seasonIndex
                episodeIndex = next.episodeIndex
                if (((nextPro.pos * 100) / nextPro.dur <= maxValue || nextPro.pos == -1L) && !nextPro.viewstate) {
                    return NextEpisode(true, episodeIndex, seasonIndex)
                }
            } else {
                val found = data.episodes?.getOrNull(episodeIndex) != null
                return NextEpisode(found, episodeIndex, seasonIndex)
            }
        }
    }


    fun setViewPosDur(data: PlayerData, pos: Long, dur: Long) {
        val key = getViewKey(data)

        if (settingsManager?.getBoolean("save_history", true) == true) {
            DataStore.setKey(VIEW_POS_KEY, key, pos)
            DataStore.setKey(VIEW_DUR_KEY, key, dur)
            DataStore.setKey(VIEWSTATE_KEY, key, System.currentTimeMillis())
        }

        if (data.card == null) return

        // HANDLES THE LOGIC FOR NEXT EPISODE
        var episodeIndex = data.episodeIndex!!
        var seasonIndex = data.seasonIndex!!
        val maxValue = 90
        var canContinue: Boolean = (pos * 100 / dur) > maxValue
        var isFound = true
        var _pos = pos
        var _dur = dur

        val card = data.card
        while (canContinue) { // IF PROGRESS IS OVER 95% CONTINUE SEARCH FOR NEXT EPISODE
            val next = canPlayNextEpisode(card, episodeIndex)
            if (next.isFound) {
                val nextPro = getViewPosDur(card.slug, next.episodeIndex)
                seasonIndex = next.seasonIndex
                episodeIndex = next.episodeIndex
                if ((nextPro.pos * 100) / dur <= maxValue) {
                    _pos = nextPro.pos
                    _dur = nextPro.dur
                    canContinue = false
                    isFound = true
                }
            } else {
                canContinue = false
                isFound = false
            }
        }

        if (!isFound) return

        if (settingsManager?.getBoolean("save_history", true) == true) {
            DataStore.setKey(
                VIEW_LST_KEY,
                data.card.slug,
                LastEpisodeInfo(
                    _pos,
                    _dur,
                    System.currentTimeMillis(),
                    card,
                    card.slug,
                    episodeIndex,
                    seasonIndex,
                    data.card.episodes!!.size == 1 && data.card.status == "finished",
                    card.episodes?.get(episodeIndex),
                    card.image,
                    card.name,
                    card.banner.toString(),
                    data.anilistID,
                    data.malID,
                    data.fillerEpisodes
                )
            )

            thread {
                ShiroApi.requestHome(true)
            }
        }
    }

    fun Activity.checkWrite(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun Activity.requestRW() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ),
            1337
        )
    }

    fun fixCardTitle(title: String): String {
        val suffix = " Dubbed"
        return if (title.endsWith(suffix)) "✦ ${
            title.substring(
                0,
                title.length - suffix.length
            ).replace(" (Anime)", "")
        }" else title.replace(" (Anime)", "")
    }

    fun splitQuery(url: URL): Map<String, String> {
        val queryPairs: MutableMap<String, String> = LinkedHashMap()
        val query: String = url.query
        val pairs = query.split("&").toTypedArray()
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            queryPairs[URLDecoder.decode(pair.substring(0, idx), "UTF-8")] =
                URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
        }
        return queryPairs
    }

    fun FragmentActivity.popCurrentPage(isInPlayer: Boolean, isInExpandedView: Boolean, isInResults: Boolean) {
        println("POPPED CURRENT FRAGMENT")
        //supportFragmentManager.executePendingTransactions()
        thread {
            val currentFragment = supportFragmentManager.fragments.lastOrNull {
                it.isVisible
            }


            if (tvActivity == null) {
                requestedOrientation = if (settingsManager?.getBoolean("force_landscape", false) == true) {
                    ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            }

            if (currentFragment == null) {
                //this.onBackPressed()
                return@thread
            }

            // No fucked animations leaving the player :)
            when {
                isInPlayer -> {
                    supportFragmentManager.beginTransaction()
                        //.setCustomAnimations(R.anim.enter, R.anim.exit, R.anim.pop_enter, R.anim.pop_exit)
                        .remove(currentFragment)
                        .commitAllowingStateLoss()
                }
                isInExpandedView && !isInResults -> {
                    supportFragmentManager.beginTransaction()
                        .setCustomAnimations(
                            R.anim.enter_from_right,
                            R.anim.exit_to_right,
                            R.anim.pop_enter,
                            R.anim.pop_exit
                        )
                        .remove(currentFragment)
                        .commitAllowingStateLoss()
                }
                else -> {
                    supportFragmentManager.beginTransaction()
                        .setCustomAnimations(R.anim.enter, R.anim.exit, R.anim.pop_enter, R.anim.pop_exit)
                        .remove(currentFragment)
                        .commitAllowingStateLoss()
                }
            }
        }
    }

    fun Activity.hideSystemUI() {
        // Enables regular immersive mode.
        // For "lean back" mode, remove SYSTEM_UI_FLAG_IMMERSIVE.
        // Or for "sticky immersive," replace it with SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                // Set the content to appear under the system bars so that the
                // content doesn't resize when the system bars hide and show.
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                // Hide the nav bar and status bar
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
    }

    fun String.md5(): String {
        return hashString(this, "MD5")
    }

    fun String.sha256(): String {
        return hashString(this, "SHA-256")
    }

    private fun hashString(input: String, algorithm: String): String {
        return MessageDigest
            .getInstance(algorithm)
            .digest(input.toByteArray())
            .fold("") { str, it -> str + "%02x".format(it) }
    }

    // Shows the system bars by removing all the flags
// except for the ones that make the content appear under the system bars.
    fun Activity.showSystemUI() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)

    }

    fun <T> LifecycleOwner.observe(liveData: LiveData<T>, action: (t: T) -> Unit) {
        liveData.observe(this) { it?.let { t -> action(t) } }
    }

    fun FragmentActivity.loadPlayer(
        episodeIndex: Int,
        startAt: Long,
        card: ShiroApi.AnimePageData,
        anilistID: Int? = null,
        malID: Int? = null,
        fillerEpisodes: HashMap<Int, Boolean>? = null
    ) {
        loadPlayer(
            PlayerData(
                null, null,
                episodeIndex,
                0,
                card,
                startAt,
                card.slug,
                anilistID,
                malID,
                fillerEpisodes
            )
        )
    }

/*fun loadPlayer(pageData: FastAniApi.AnimePageData, episodeIndex: Int, startAt: Long?) {
    loadPlayer(PlayerData("${pageData.name} - Episode ${episodeIndex + 1}", null, episodeIndex, null, null, startAt, null, true))
}
fun loadPlayer(title: String?, url: String, startAt: Long?) {
    loadPlayer(PlayerData(title, url, null, null, null, startAt, null))
}*/

    fun FragmentActivity.loadPlayer(data: PlayerData) {
        runOnUiThread {
            masterViewModel?.playerData?.value = data
            val instance =
                if (tvActivity != null) PlayerFragmentTv.newInstance() else PlayerFragment.newInstance()//.newInstance(data)

            this.addFragmentOnlyOnce(android.R.id.content, instance, PLAYER_FRAGMENT_TAG)
        }

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
    }

    fun FragmentActivity.loadPage(slug: String, name: String) {
        this.addFragmentOnlyOnce(
            R.id.homeRoot,
            ResultFragment.newInstance(slug, name),
            RESULT_FRAGMENT_TAG
        )
        /*
        activity?.runOnUiThread {
            val _navController = Navigation.findNavController(activity!!, R.id.nav_host_fragment)
            _navController?.navigateUp()
            _navController?.navigate(R.layout.fragment_results,null,null)
        }
    */
        // NavigationUI.navigateUp(navController!!,R.layout.fragment_results)
    }

    @ColorInt
    fun Context.getColorFromAttr(
        @AttrRes attrColor: Int,
        typedValue: TypedValue = TypedValue(),
        resolveRefs: Boolean = true,
    ): Int {
        theme.resolveAttribute(attrColor, typedValue, resolveRefs)
        return typedValue.data
    }

    fun shouldShowPIPMode(isInPlayer: Boolean): Boolean {
        return settingsManager?.getBoolean("pip_enabled", true) ?: true && isInPlayer
    }

    fun Context.hasPIPPermission(): Boolean {
        val appOps =
            getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
            android.os.Process.myUid(),
            packageName
        ) == AppOpsManager.MODE_ALLOWED
    }


}