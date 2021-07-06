package com.lagradost.shiro.ui.tv

import android.content.Intent
import android.os.Bundle
import android.view.FocusFinder
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.jaredrummler.cyanea.Cyanea
import com.jaredrummler.cyanea.app.CyaneaAppCompatActivity
import com.jaredrummler.cyanea.prefs.CyaneaTheme
import com.lagradost.shiro.R
import com.lagradost.shiro.ui.MainActivity.Companion.masterViewModel
import com.lagradost.shiro.ui.MasterViewModel
import com.lagradost.shiro.ui.WebViewFragment.Companion.isInWebView
import com.lagradost.shiro.ui.home.ExpandedHomeFragment.Companion.isInExpandedView
import com.lagradost.shiro.ui.result.ResultFragment.Companion.isInResults
import com.lagradost.shiro.ui.settings.SettingsFragment.Companion.isInSettings
import com.lagradost.shiro.ui.tv.PlayerFragmentTv.Companion.isInPlayer
import com.lagradost.shiro.utils.*
import com.lagradost.shiro.utils.AniListApi.Companion.authenticateLogin
import com.lagradost.shiro.utils.AppUtils.init
import com.lagradost.shiro.utils.AppUtils.popCurrentPage
import com.lagradost.shiro.utils.AppUtils.settingsManager
import com.lagradost.shiro.utils.InAppUpdater.runAutoUpdate
import kotlinx.android.synthetic.main.activity_tv.*
import kotlinx.android.synthetic.main.fragment_main_tv.*
import kotlin.concurrent.thread

/**
 * Loads [MainFragment].
 */
class TvActivity : CyaneaAppCompatActivity() {
    companion object {
        var tvActivity: TvActivity? = null
        var isInSearch = false

        fun FragmentActivity.applyThemes() {
            // ----- Themes ----
            //AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            //theme.applyStyle(R.style.AppTheme, true)

            /* val currentTheme = when (settingsManager!!.getString("theme", "Black")) {
                 "Black" -> R.style.AppTheme
                 "Dark" -> R.style.DarkMode
                 "Light" -> R.style.LightMode
                 else -> R.style.AppTheme
             }*/

            /*if (settingsManager.getBoolean("cool_mode", false)) {
                theme.applyStyle(R.style.OverlayPrimaryColorBlue, true)
            } else if (BuildConfig.BETA && settingsManager.getBoolean("beta_theme", false)) {
                theme.applyStyle(R.style.OverlayPrimaryColorGreen, true)
            }*/
            //theme.applyStyle(R.style.AppTheme, true)
            theme.applyStyle(R.style.Theme_LeanbackCustom, true)
            /*theme.applyStyle(currentTheme, true)
            AppUtils.getTheme()?.let {
                theme.applyStyle(it, true)
            }*/
            // -----------------
        }

    }

    override fun onBackPressed() {
        if ((isInSearch || isInSettings) && !isInResults && !isInWebView) {
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.enter, R.anim.exit, R.anim.pop_enter, R.anim.pop_exit)
                .replace(R.id.home_root_tv, MainFragment())
                .commit()
        } else if (isInResults || isInPlayer || isInWebView) {
            popCurrentPage(isInPlayer, isInExpandedView, isInResults)
        } else {
            super.onBackPressed()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Disables ssl check - Needed for development with Android TV VM

        /*
        val sslContext: SSLContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(SSLTrustManager()), SecureRandom())
        sslContext.createSSLEngine()
        HttpsURLConnection.setDefaultHostnameVerifier { _: String, _: SSLSession ->
            true
        }
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
        */

        masterViewModel = masterViewModel ?: ViewModelProvider(this).get(MasterViewModel::class.java)
        DataStore.init(this)
        settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        init()
        thread {
            ShiroApi.init()
        }
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
        applyThemes()
        super.onCreate(savedInstanceState)
        /*if (!isTv()) {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
            return
        }*/
        // ------ Init -----
        tvActivity = this
        thread {
            runAutoUpdate(this)
        }

        setContentView(R.layout.activity_tv)
    }


    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return if (keyCode == KeyEvent.KEYCODE_DPAD_UP && !isInPlayer && !isInResults) {
            try {
                val nextFocused =
                    FocusFinder.getInstance().findNextFocus(home_root_tv, currentFocus, View.FOCUS_UP)
                if (nextFocused == null) {
                    //println("Null focus")
                    search_icon.requestFocus()
                } else {
                    //println("Found focus")
                    nextFocused.requestFocus()
                    //super.onKeyDown(keyCode, event)
                }
            } catch (e: Exception) {
                return false
            }
        } else {
            //println("Not")
            super.onKeyDown(keyCode, event)
        }
    }

    override fun onResume() {
        super.onResume()
        // This is needed to avoid NPE crash due to missing context
        DataStore.init(this)
        init()
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

}