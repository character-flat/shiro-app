package com.lagradost.shiro

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.ColorDrawable
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.TypedValue
import android.view.*
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlin.concurrent.thread

import com.fasterxml.jackson.annotation.JsonProperty
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.lagradost.shiro.ShiroApi.Companion.getAppUpdate
import com.lagradost.shiro.ShiroApi.Companion.getDonorStatus
import com.lagradost.shiro.ui.PlayerData
import com.lagradost.shiro.ui.PlayerEventType
import com.lagradost.shiro.ui.PlayerFragment
import com.lagradost.shiro.ui.PlayerFragment.Companion.isInPlayer
import com.lagradost.shiro.ui.home.ExpandedHomeFragment.Companion.isInExpandedView
import com.lagradost.shiro.ui.result.ResultFragment
import com.lagradost.shiro.ui.result.ResultFragment.Companion.isInResults
import kotlinx.android.synthetic.main.update_dialog.*
import java.lang.Exception
import java.net.URL
import java.net.URLDecoder
import java.security.MessageDigest

val Int.toPx: Int get() = (this * Resources.getSystem().displayMetrics.density).toInt()
val Int.toDp: Int get() = (this / Resources.getSystem().displayMetrics.density).toInt()

data class EpisodePosDurInfo(
    @JsonProperty("pos") val pos: Long,
    @JsonProperty("dur") val dur: Long,
    @JsonProperty("viewstate") val viewstate: Boolean,
)

data class LastEpisodeInfo(
    @JsonProperty("pos") val pos: Long,
    @JsonProperty("dur") val dur: Long,
    @JsonProperty("seenAt") val seenAt: Long,
    @JsonProperty("id") val id: ShiroApi.AnimePageData?,
    @JsonProperty("aniListId") val aniListId: String,
    @JsonProperty("episodeIndex") val episodeIndex: Int,
    @JsonProperty("seasonIndex") val seasonIndex: Int,
    @JsonProperty("isMovie") val isMovie: Boolean,
    @JsonProperty("episode") val episode: ShiroApi.ShiroEpisodes?,
    @JsonProperty("coverImage") val coverImage: String,
    @JsonProperty("title") val title: String,
    @JsonProperty("bannerImage") val bannerImage: String,
)

data class NextEpisode(
    @JsonProperty("isFound") val isFound: Boolean,
    @JsonProperty("episodeIndex") val episodeIndex: Int,
    @JsonProperty("seasonIndex") val seasonIndex: Int,
)

/*Class for storing bookmarks*/
data class BookmarkedTitle(
    @JsonProperty("name") val name: String,
    @JsonProperty("image") val image: String,
    @JsonProperty("slug") val slug: String
)

class MainActivity : AppCompatActivity() {
    companion object {
        var isInPIPMode = false

        @SuppressLint("StaticFieldLeak")
        var navController: NavController? = null
        var statusHeight: Int = 0
        var activity: MainActivity? = null
        var canShowPipMode: Boolean = false
        var isDonor: Boolean = false

        var onPlayerEvent = Event<PlayerEventType>()
        var onAudioFocusEvent = Event<Boolean>()

        var focusRequest: AudioFocusRequest? = null

        fun unixTime(): Long {
            return System.currentTimeMillis() / 1000L
        }

        fun isCastApiAvailable(): Boolean {
            val isCastApiAvailable =
                GoogleApiAvailability.getInstance()
                    .isGooglePlayServicesAvailable(activity?.applicationContext) == ConnectionResult.SUCCESS
            try {
                activity?.applicationContext?.let { CastContext.getSharedInstance(it) }
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

        fun requestAudioFocus() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
                val audioManager = activity!!.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.requestAudioFocus(focusRequest!!)
            } else {
                val audioManager: AudioManager =
                    activity?.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        }

        fun changeStatusBarState(hide: Boolean) {
            statusHeight = if (hide) {
                activity!!.window.setFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
                )
                0
            } else {
                activity!!.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                activity!!.getStatusBarHeight()
            }
        }

        fun openBrowser(url: String) {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(url)
            activity!!.startActivity(intent)
        }

        fun getViewPosDur(aniListId: String, episodeIndex: Int): EpisodePosDurInfo {
            val key = getViewKey(aniListId, episodeIndex)

            return EpisodePosDurInfo(
                DataStore.getKey(VIEW_POS_KEY, key, -1L)!!,
                DataStore.getKey(VIEW_DUR_KEY, key, -1L)!!,
                DataStore.containsKey(VIEWSTATE_KEY, key)
            )
        }

        private fun canPlayNextEpisode(card: ShiroApi.AnimePageData?, episodeIndex: Int): NextEpisode {
            val canNext = card!!.episodes!!.size > episodeIndex + 1

            return if (canNext) {
                NextEpisode(true, episodeIndex + 1, 0)
            } else {
                NextEpisode(false, episodeIndex + 1, 0)
            }

        }

        fun getLatestSeenEpisode(data: ShiroApi.AnimePageData): NextEpisode {
            for (i in (data.episodes?.size ?: 0) downTo 0) {
                val firstPos = getViewPosDur(data.slug, i)
                if (firstPos.viewstate) {
                    return NextEpisode(true, i, 0)
                }
            }
            return NextEpisode(false, 0, 0)
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
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(activity)

            if (settingsManager.getBoolean("save_history", true)) {
                DataStore.setKey(VIEW_POS_KEY, key, pos)
                DataStore.setKey(VIEW_DUR_KEY, key, dur)
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

            if (settingsManager.getBoolean("save_history", true)) {
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
                    )
                )

                thread {
                    ShiroApi.requestHome(true)
                }
            }
        }

        fun checkWrite(): Boolean {
            return (ContextCompat.checkSelfPermission(
                activity!!,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED)
        }

        fun requestRW() {
            ActivityCompat.requestPermissions(
                activity!!,
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

        fun popCurrentPage() {
            println("POPP")
            val currentFragment = activity?.supportFragmentManager!!.fragments.last {
                it.isVisible
            }

            val settingsManager = PreferenceManager.getDefaultSharedPreferences(activity)
            if (settingsManager.getBoolean("force_landscape", false)) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
            } else {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }

            // No fucked animations leaving the player :)
            when {
                isInPlayer -> {
                    activity?.supportFragmentManager!!.beginTransaction()
                        //.setCustomAnimations(R.anim.enter, R.anim.exit, R.anim.pop_enter, R.anim.pop_exit)
                        .remove(currentFragment)
                        .commitAllowingStateLoss()
                }
                isInExpandedView && !isInResults -> {
                    activity?.supportFragmentManager!!.beginTransaction()
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
                    activity?.supportFragmentManager!!.beginTransaction()
                        .setCustomAnimations(R.anim.enter, R.anim.exit, R.anim.pop_enter, R.anim.pop_exit)
                        .remove(currentFragment)
                        .commitAllowingStateLoss()
                }
            }
        }

        fun hideSystemUI() {
            // Enables regular immersive mode.
            // For "lean back" mode, remove SYSTEM_UI_FLAG_IMMERSIVE.
            // Or for "sticky immersive," replace it with SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            activity!!.window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
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
                .fold("", { str, it -> str + "%02x".format(it) })
        }

        // Shows the system bars by removing all the flags
// except for the ones that make the content appear under the system bars.
        fun showSystemUI() {
            activity!!.window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        }

        fun loadPlayer(episodeIndex: Int, startAt: Long, card: ShiroApi.AnimePageData) {
            loadPlayer(
                PlayerData(
                    null, null,
                    episodeIndex,
                    0,
                    card,
                    startAt,
                    card.slug
                )
            )
        }

        /*fun loadPlayer(pageData: FastAniApi.AnimePageData, episodeIndex: Int, startAt: Long?) {
            loadPlayer(PlayerData("${pageData.name} - Episode ${episodeIndex + 1}", null, episodeIndex, null, null, startAt, null, true))
        }
        fun loadPlayer(title: String?, url: String, startAt: Long?) {
            loadPlayer(PlayerData(title, url, null, null, null, startAt, null))
        }*/

        fun loadPlayer(data: PlayerData) {
            activity?.supportFragmentManager?.beginTransaction()
                ?.setCustomAnimations(R.anim.enter, R.anim.exit, R.anim.pop_enter, R.anim.pop_exit)
                ?.add(
                    R.id.videoRoot, PlayerFragment.newInstance(
                        data
                    )
                )
                ?.commit()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
        }

        fun loadPage(card: ShiroApi.AnimePageData) {

            activity?.supportFragmentManager?.beginTransaction()
                ?.setCustomAnimations(R.anim.enter, R.anim.exit, R.anim.pop_enter, R.anim.pop_exit)
                ?.add(R.id.homeRoot, ResultFragment.newInstance(card))
                ?.commit()
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

    }

    // AUTH FOR LOGIN
    override fun onNewIntent(intent: Intent?) {
        if (intent != null) {
            val dataString = intent.dataString
            if (dataString != null && dataString != "") {
                println("GOT fastaniapp auth$dataString")
                if (dataString.contains("fastaniapp")) {
                    if (dataString.contains("/anilistlogin")) {
                        AniListApi.authenticateLogin(dataString)
                    } else if (dataString.contains("/mallogin")) {
                        MALApi.authenticateLogin(dataString)
                    }
                }
            }
        }

        super.onNewIntent(intent)
    }

    override fun onBackPressed() {
        println("BACK PRESSED!!!! $isInResults $isInPlayer $isInExpandedView")

        if (isInResults || isInPlayer || isInExpandedView) {
            popCurrentPage()
        } else {
            super.onBackPressed()
        }
    }

    private fun getStatusBarHeight(): Int {
        var result = 0
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

    private fun enterPIPMode() {
        if (!shouldShowPIPMode() || !canShowPipMode) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                enterPictureInPictureMode(PictureInPictureParams.Builder().build())
            } catch (e: Exception) {
                enterPictureInPictureMode()
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                enterPictureInPictureMode()
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPIPMode()
    }

    override fun onRestart() {
        super.onRestart()
        if (isInPlayer) {
            hideSystemUI()
        }
    }

    override fun onResume() {
        super.onResume()
        println("RESUMED!!!")
        // This is needed to avoid NPE crash due to missing context
        DataStore.init(this)
        DownloadManager.init(this)
        if (isInPlayer) {
            hideSystemUI()
        }
    }

    private fun shouldShowPIPMode(): Boolean {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(activity)
        return settingsManager.getBoolean("pip_enabled", true) && isInPlayer
    }

    private fun hasPIPPermission(): Boolean {
        val appOps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        } else {
            return false
        }
        return appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
            android.os.Process.myUid(),
            packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    private val callbacks = object : MediaSessionCompat.Callback() {
        override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
            if (mediaButtonEvent != null) {

                val event = mediaButtonEvent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) as KeyEvent
                println("EVENT: " + event.keyCode)
                when (event.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> onPlayerEvent.invoke(PlayerEventType.Pause)
                    KeyEvent.KEYCODE_MEDIA_PLAY -> onPlayerEvent.invoke(PlayerEventType.Play)
                    KeyEvent.KEYCODE_MEDIA_STOP -> onPlayerEvent.invoke(PlayerEventType.Pause)
                    KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD -> onPlayerEvent.invoke(PlayerEventType.SeekForward)
                    KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> onPlayerEvent.invoke(PlayerEventType.SeekBack)
                    KeyEvent.KEYCODE_HEADSETHOOK -> onPlayerEvent.invoke(PlayerEventType.Pause)
                }
            }
            return super.onMediaButtonEvent(mediaButtonEvent)
        }

        override fun onPlay() {
            onPlayerEvent.invoke(PlayerEventType.Play)
        }

        override fun onStop() {
            onPlayerEvent.invoke(PlayerEventType.Pause)
        }
    }

    private val myAudioFocusListener =
        AudioManager.OnAudioFocusChangeListener {
            onAudioFocusEvent.invoke(
                when (it) {
                    AudioManager.AUDIOFOCUS_GAIN -> true
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE -> true
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> true
                    else -> false
                }
            )
        }

    override fun onDestroy() {
        mediaSession?.isActive = false
        super.onDestroy()
    }

    private var mediaSession: MediaSessionCompat? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        activity = this
        DataStore.init(this)
        DownloadManager.init(this)

        @SuppressLint("HardwareIds")
        val androidId: String = Settings.Secure.getString(activity?.contentResolver, Settings.Secure.ANDROID_ID).md5()
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(activity)
        if (settingsManager.getBoolean("force_landscape", false)) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        if (settingsManager.getBoolean("use_external_storage", false)) {
            if (!checkWrite()) {
                Toast.makeText(activity, "Accept storage permissions to download", Toast.LENGTH_LONG).show()
                requestRW()
            }
        }
        thread {
            ShiroApi.init()
        }
        thread {
            isDonor = getDonorStatus() == androidId
        }
        if (settingsManager.getBoolean("auto_update", true)) {
            thread {
                val update = getAppUpdate()
                if (update.shouldUpdate && update.updateURL != null) {

                    activity!!.runOnUiThread {
                        val dialog = Dialog(activity!!)
                        //dialog.window?.setBackgroundDrawable(ColorDrawable(ContextCompat.getColor(activity!!, R.color.colorPrimary)))

                        dialog.setContentView(R.layout.update_dialog)
                        dialog.update_dialog_header.text = "New update found!\n${update.updateVersion}\n"

                        dialog.update_later.setOnClickListener {
                            dialog.dismiss()
                        }
                        dialog.update_changelog.setOnClickListener {
                            val alertDialog: AlertDialog? = activity?.let {

                                val builder = AlertDialog.Builder(it)
                                builder.apply {
                                    setPositiveButton("OK") { _, _ -> }
                                }
                                // Set other dialog properties
                                builder.setTitle(update.updateVersion)
                                builder.setMessage(update.changelog)
                                // Create the AlertDialog
                                builder.create()
                            }
                            alertDialog?.window?.setBackgroundDrawable(
                                ColorDrawable(
                                    ContextCompat.getColor(
                                        activity!!,
                                        R.color.grayBackground
                                    )
                                )
                            )
                            alertDialog?.show()
                        }
                        dialog.update_never.setOnClickListener {
                            settingsManager.edit().putBoolean("auto_update", false).apply()
                            dialog.dismiss()
                        }
                        dialog.update_now.setOnClickListener {
                            Toast.makeText(activity!!, "Download started", Toast.LENGTH_LONG).show()
                            DownloadManager.downloadUpdate(update.updateURL)
                            dialog.dismiss()
                        }
                        dialog.show()
                    }
                }
            }
        }
        //https://stackoverflow.com/questions/29146757/set-windowtranslucentstatus-true-when-android-lollipop-or-higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
            )
        }
        //AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        if (settingsManager.getBoolean("cool_mode", false)) {
            theme.applyStyle(R.style.OverlayPrimaryColorBlue, true)
        }
        if (settingsManager.getBoolean("light_mode", false)) {
            theme.applyStyle(R.style.LightMode, true)
        }
        changeStatusBarState(settingsManager.getBoolean("statusbar_hidden", true))
        //window.statusBarColor = R.color.transparent

        //https://stackoverflow.com/questions/52594181/how-to-know-if-user-has-disabled-picture-in-picture-feature-permission
        //https://developer.android.com/guide/topics/ui/picture-in-picture

        //val action: String? = intent?.action


        canShowPipMode =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && // OS SUPPORT
                    packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) && // HAS FEATURE, MIGHT BE BLOCKED DUE TO POWER DRAIN
                    hasPIPPermission() // CHECK IF FEATURE IS ENABLED IN SETTINGS


        // CRASHES ON 7.0.0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).run {
                setAudioAttributes(AudioAttributes.Builder().run {
                    setUsage(AudioAttributes.USAGE_MEDIA)
                    setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    build()
                })
                setAcceptsDelayedFocusGain(true)
                setOnAudioFocusChangeListener(myAudioFocusListener)
                build()
            }
        }

        // Setting the theme
        /*
    val autoDarkMode = settingsManager.getBoolean("auto_dark_mode", true)
    val darkMode = settingsManager.getBoolean("dark_mode", false)

    if (autoDarkMode) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    } else {
        if (darkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }*/
        mediaSession = MediaSessionCompat(activity!!, "fastani").apply {

            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            // Do not let MediaButtons restart the player when the app is not visible
            setMediaButtonReceiver(null)

            // Set an initial PlaybackState with ACTION_PLAY, so media buttons can start the player
            val stateBuilder = PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PLAY_PAUSE)
            setPlaybackState(stateBuilder.build())

            // MySessionCallback has methods that handle callbacks from a media controller
            setCallback(callbacks)
        }

        mediaSession!!.isActive = true

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        /*val layout = listOf(
            R.id.navigation_home, R.id.navigation_search, /*R.id.navigation_downloads,*/ R.id.navigation_settings
        )
        val appBarConfiguration = AppBarConfiguration(
            layout.toSet()
        )*/

        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        //setupActionBarWithNavController(navController, appBarConfiguration)
        val navView: BottomNavigationView = findViewById(R.id.nav_view)
        navController = findNavController(R.id.nav_host_fragment)
        navView.setupWithNavController(navController!!)
        navView.itemBackground = ColorDrawable(activity!!.getColorFromAttr(R.attr.darkBar))

        /*navView.setOnKeyListener { v, keyCode, event ->
            println("$keyCode $event")
            if (event.action == ACTION_DOWN) {

                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {

                        val newItem =
                            navView.menu.findItem(layout[(layout.indexOf(navView.selectedItemId) + 1) % 4])
                        newItem.isChecked = true

                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {

                        val newItem =
                            navView.menu.findItem(
                                layout[Math.floorMod(
                                    layout.indexOf(navView.selectedItemId) - 1,
                                    4
                                )]
                            )
                        newItem.isChecked = true

                    }
                    // TODO FIX
                    KeyEvent.KEYCODE_ENTER -> {
                        navController!!.navigate(navView.selectedItemId)
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        navView.isFocusable = false
                        navView.clearFocus()
                        navView.requestFocus(FOCUS_UP)
                    }
                }
            }
            return@setOnKeyListener true
        }*/

        window.setBackgroundDrawableResource(R.color.background)
        //val castContext = CastContext.getSharedInstance(activity!!.applicationContext)
        val data: Uri? = intent?.data

        if (data != null) {
            val dataString = data.toString()
            if (dataString != "") {
                println("GOT fastaniapp auth awake: $dataString")
                if (dataString.contains("fastaniapp")) {
                    if (dataString.contains("/anilistlogin")) {
                        AniListApi.authenticateLogin(dataString)
                    } else if (dataString.contains("/mallogin")) {
                        MALApi.authenticateLogin(dataString)
                    }
                }
            }

            thread {
                val urlRegex = Regex("""fastani\.net/watch/(.*?)/(/d+)/(/+)""")
                val found = urlRegex.find(data.toString())
                if (found != null) {
                    val (id, season, episode) = found.destructured
                    println("$id $season $episode")
                    /*val card = getCardById(id)
                    if (card?.anime?.cdnData?.seasons?.getOrNull(season.toInt() - 1) != null) {
                        if (card.anime.cdnData.seasons[season.toInt() - 1].episodes.getOrNull(episode.toInt() - 1) != null) {
                            //loadPlayer(episode.toInt() - 1, season.toInt() - 1, card)
                        }
                    }*/
                }
            }
        } else {
            AniListApi.initGetUser()
        }
    }
}
