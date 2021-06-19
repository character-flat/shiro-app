package com.lagradost.shiro.ui.player

import android.content.pm.PackageManager
import android.database.Cursor
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.lagradost.shiro.R
import com.lagradost.shiro.ui.MainActivity.Companion.canShowPipMode
import com.lagradost.shiro.ui.MainActivity.Companion.focusRequest
import com.lagradost.shiro.ui.MainActivity.Companion.onAudioFocusEvent
import com.lagradost.shiro.ui.MainActivity.Companion.statusHeight
import com.lagradost.shiro.utils.AppUtils.changeStatusBarState
import com.lagradost.shiro.utils.AppUtils.checkWrite
import com.lagradost.shiro.utils.AppUtils.getUri
import com.lagradost.shiro.utils.AppUtils.hasPIPPermission
import com.lagradost.shiro.utils.AppUtils.init
import com.lagradost.shiro.utils.AppUtils.loadPlayer
import com.lagradost.shiro.utils.AppUtils.requestRW
import com.lagradost.shiro.utils.DataStore
import com.lagradost.shiro.utils.DownloadManager
import java.io.*

class PlayerActivity : AppCompatActivity() {
    companion object {
        var playerActivity: AppCompatActivity? = null
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent.data
        if (data == null) {
            finish()
            return
        }
        playerActivity = this

        DataStore.init(this)
        DownloadManager.init(this)
        init()


        //https://stackoverflow.com/questions/29146757/set-windowtranslucentstatus-true-when-android-lollipop-or-higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
            )
        }

        // ----- Themes ----
        //AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        if (!checkWrite()) {
            Toast.makeText(this, "Accept storage permissions to play", Toast.LENGTH_LONG).show()
            requestRW()
        }
        val currentTheme = when (settingsManager.getString("theme", "Black")) {
            "Black" -> R.style.AppTheme
            "Dark" -> R.style.DarkMode
            "Light" -> R.style.LightMode
            else -> R.style.AppTheme
        }

        theme.applyStyle(currentTheme, true)
        if (settingsManager.getBoolean("cool_mode", false)) {
            theme.applyStyle(R.style.OverlayPrimaryColorBlue, true)
        } else if (settingsManager.getBoolean("beta_theme", false)) {
            theme.applyStyle(R.style.OverlayPrimaryColorGreenApple, true)
        } else if (settingsManager.getBoolean("purple_theme", false) && settingsManager.getBoolean(
                "auto_update",
                true
            ) && settingsManager.getBoolean("beta_mode", true)
        ) {
            theme.applyStyle(R.style.OverlayPrimaryColorPurple, true)
        }
        // -----------------

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

        val path = getUri(intent.data)!!.path
        // Because it doesn't get the path when it's downloaded, I have no idea
        val realPath =
            if (intent?.data?.path?.contains("/${Environment.DIRECTORY_DOWNLOADS}/Shiro/") == true && path?.length == 0) intent?.data?.path!!.removePrefix(
                "/file"
            ) else path
        setContentView(R.layout.activity_player)

        val playerData = PlayerData(
            realPath,
            realPath,
            null,
            null,
            null,
            null,
            ""
        )
        loadPlayer(playerData)
    }



}