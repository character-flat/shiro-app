package com.lagradost.shiro.ui

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.drawable.ColorDrawable
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.jaredrummler.cyanea.Cyanea
import com.jaredrummler.cyanea.app.CyaneaAppCompatActivity
import com.jaredrummler.cyanea.prefs.CyaneaTheme
import com.lagradost.shiro.R
import com.lagradost.shiro.ui.home.ExpandedHomeFragment.Companion.isInExpandedView
import com.lagradost.shiro.ui.player.PlayerEventType
import com.lagradost.shiro.ui.player.PlayerFragment.Companion.isInPlayer
import com.lagradost.shiro.ui.result.ResultFragment.Companion.isInResults
import com.lagradost.shiro.utils.*
import com.lagradost.shiro.utils.AniListApi.Companion.authenticateLogin
import com.lagradost.shiro.utils.AniListApi.Companion.initGetUser
import com.lagradost.shiro.utils.AppUtils.changeStatusBarState
import com.lagradost.shiro.utils.AppUtils.checkWrite
import com.lagradost.shiro.utils.AppUtils.getTextColor
import com.lagradost.shiro.utils.AppUtils.hasPIPPermission
import com.lagradost.shiro.utils.AppUtils.hideSystemUI
import com.lagradost.shiro.utils.AppUtils.init
import com.lagradost.shiro.utils.AppUtils.popCurrentPage
import com.lagradost.shiro.utils.AppUtils.requestRW
import com.lagradost.shiro.utils.AppUtils.shouldShowPIPMode
import com.lagradost.shiro.utils.AppUtils.transparentStatusAndNavigation
import com.lagradost.shiro.utils.InAppUpdater.runAutoUpdate
import kotlin.concurrent.thread

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

    // Old, is actually used for slugs
    @JsonProperty("aniListId") val aniListId: String,

    @JsonProperty("episodeIndex") val episodeIndex: Int,
    @JsonProperty("seasonIndex") val seasonIndex: Int,
    @JsonProperty("isMovie") val isMovie: Boolean,
    @JsonProperty("episode") val episode: ShiroApi.ShiroEpisodes?,
    @JsonProperty("coverImage") val coverImage: String,
    @JsonProperty("title") val title: String,
    @JsonProperty("bannerImage") val bannerImage: String,

    @JsonProperty("anilistID") val anilistID: Int?,
    @JsonProperty("malID") val malID: Int?,

    @JsonProperty("fillerEpisodes") val fillerEpisodes: HashMap<Int, Boolean>?
)

data class NextEpisode(
    @JsonProperty("isFound") val isFound: Boolean,
    @JsonProperty("episodeIndex") val episodeIndex: Int,
    @JsonProperty("seasonIndex") val seasonIndex: Int,
)

/*Class for storing bookmarks*/
data class BookmarkedTitle(
    @JsonProperty("name") override val name: String,
    @JsonProperty("image") override val image: String,
    @JsonProperty("slug") override val slug: String,
    @JsonProperty("english") override val english: String?
) : ShiroApi.CommonAnimePage

class MainActivity : CyaneaAppCompatActivity() {
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

        var lightMode = false
        var focusRequest: AudioFocusRequest? = null

        var masterViewModel: MasterViewModel? = null
    }

    // AUTH FOR LOGIN
    override fun onNewIntent(intent: Intent?) {
        if (intent != null) {
            val dataString = intent.dataString
            if (dataString != null && dataString != "") {
                if (dataString.contains("shiroapp")) {
                    if (dataString.contains("/anilistlogin")) {
                        authenticateLogin(dataString)
                    } else if (dataString.contains("/mallogin")) {
                        MALApi.authenticateLogin(dataString)
                    }
                }
            }
        }
        super.onNewIntent(intent)
    }

    override fun onBackPressed() {
        try {
            println("BACK PRESSED!!!! $isInResults $isInPlayer $isInExpandedView")
            if (isInResults || isInPlayer || isInExpandedView) {
                popCurrentPage(isInPlayer, isInExpandedView, isInResults)
            } else {
                super.onBackPressed()
            }
            // java.lang.IllegalStateException: FragmentManager is already executing transactions
        } catch (e: Exception) {
        }
    }


    private fun enterPIPMode() {
        if (!shouldShowPIPMode(isInPlayer) || !canShowPipMode) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                enterPictureInPictureMode(PictureInPictureParams.Builder().build())
            } catch (e: Exception) {
                try {
                    enterPictureInPictureMode()
                } catch (e: Exception) {
                }
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    enterPictureInPictureMode()
                } catch (e: Exception) {
                }
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
        init()
        if (isInPlayer) {
            hideSystemUI()
        }
    }


    private val callbacks = object : MediaSessionCompat.Callback() {
        override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
            if (mediaButtonEvent != null) {
                val event = mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT) as? KeyEvent
                println("EVENT: " + event?.keyCode)
                when (event?.keyCode) {
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

        // used for PlayerData to prevent crashes
        masterViewModel = masterViewModel ?: ViewModelProvider(this).get(MasterViewModel::class.java)
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(activity)
        init()
        DataStore.init(this)
        DownloadManager.init(this)
        // Hack to make tinting work
        if (Cyanea.instance.isLight) delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES

        // ----- Themes ----
        //AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        /*lightMode = false
        val currentTheme = when (settingsManager.getString("theme", "Black")) {
            "Black" -> R.style.AppTheme
            "Dark" -> {
                R.style.DarkMode
            }
            "Light" -> {
                lightMode = true
                R.style.LightMode
            }
            else -> R.style.AppTheme
        }

        theme.applyStyle(currentTheme, true)
        AppUtils.getTheme()?.let {
            theme.applyStyle(it, true)
        }*/

        supportActionBar?.hide()
        if (cyanea.isDark) {
            theme.applyStyle(R.style.lightText, true)
        } else {
            theme.applyStyle(R.style.darkText, true)
        }
        if (!Cyanea.instance.isThemeModified) {
            val list: List<CyaneaTheme> = CyaneaTheme.Companion.from(assets, "themes/cyanea_themes.json");
            list[0].apply(Cyanea.instance).recreate(this)
        }

        // -----------------
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        /*window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )*/
        transparentStatusAndNavigation()

        //@SuppressLint("HardwareIds")
        //val androidId: String = Settings.Secure.getString(activity?.contentResolver, Settings.Secure.ANDROID_ID).md5()
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
        // Hardcoded for now since it fails for some people :|
        isDonor = true //getDonorStatus() == androidId
        thread {
            runAutoUpdate(this)
        }
        //https://stackoverflow.com/questions/29146757/set-windowtranslucentstatus-true-when-android-lollipop-or-higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
            )
        }

        val statusBarHidden = settingsManager.getBoolean("statusbar_hidden", true)
        statusHeight = changeStatusBarState(statusBarHidden)


        //https://stackoverflow.com/questions/52594181/how-to-know-if-user-has-disabled-picture-in-picture-feature-permission
        //https://developer.android.com/guide/topics/ui/picture-in-picture
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
        /*
        navView.setOnNavigationItemReselectedListener {
            return@setOnNavigationItemReselectedListener
        }
        val options = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setEnterAnim(R.anim.enter)
            .setExitAnim(R.anim.exit)
            .setPopEnterAnim(R.anim.pop_enter)
            .setPopExitAnim(R.anim.pop_exit)
            .setPopUpTo(navController!!.graph.startDestination, false)
            .build()
        navView.setOnNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.navigation_home -> {
                    navController?.navigate(R.id.navigation_home, null, options)
                }
                R.id.navigation_search -> {
                    navController?.navigate(R.id.navigation_search, null, options)
                }
                R.id.navigation_downloads -> {
                    navController?.navigate(R.id.navigation_downloads, null, options)
                }
                R.id.navigation_settings -> {
                    navController?.navigate(R.id.navigation_settings, null, options)
                }
            }
            true
        }*/

        //val attrPrimary = Cyanea.instance.menuIconColor //if (lightMode) Cyanea.instance.primaryDark else Cyanea.instance.primary
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked),
            intArrayOf(android.R.attr.state_enabled),
            intArrayOf(-android.R.attr.state_enabled),
        )

        val usedColor = this.getTextColor()

        val colors = intArrayOf(
            usedColor,
            usedColor,
            usedColor,
            usedColor,
        )

        navView.itemRippleColor = ColorStateList.valueOf(usedColor)
        navView.itemIconTintList = ColorStateList(states, colors)
        navView.itemTextColor = ColorStateList(states, colors)

        // Uncommenting this fucks ripple
        //navView.itemBackground = ColorDrawable(Cyanea.instance.backgroundColorDark)

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

        window.setBackgroundDrawable(ColorDrawable(Cyanea.instance.backgroundColor))
        //val castContext = CastContext.getSharedInstance(activity!!.applicationContext)
        val data: Uri? = intent?.data

        if (data != null) {
            val dataString = data.toString()
            if (dataString != "") {
                println("GOT shiroapp auth awake: $dataString")
                if (dataString.contains("shiroapp")) {
                    if (dataString.contains("/anilistlogin")) {
                        authenticateLogin(dataString)
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
            initGetUser()
        }
    }
}
