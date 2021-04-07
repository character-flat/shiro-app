package com.lagradost.shiro.ui.settings

import android.content.*
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.*

import androidx.preference.PreferenceFragmentCompat
import com.bumptech.glide.Glide
import com.lagradost.shiro.*
import com.lagradost.shiro.DataStore.getKeys
import com.lagradost.shiro.DataStore.removeKeys
import com.lagradost.shiro.MainActivity.Companion.checkWrite
import com.lagradost.shiro.MainActivity.Companion.getColorFromAttr
import com.lagradost.shiro.MainActivity.Companion.isDonor
import com.lagradost.shiro.MainActivity.Companion.md5
import com.lagradost.shiro.MainActivity.Companion.requestRW
import com.lagradost.shiro.R
import com.lagradost.shiro.VIEW_LST_KEY
import java.io.File
import kotlin.concurrent.thread

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        super.onCreateView(inflater, container, savedInstanceState)?.apply {
            setBackgroundColor(MainActivity.activity!!.getColorFromAttr(R.attr.background))
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)

        var easterEggClicks = 0
        //val saveHistory = findPreference("save_history") as SwitchPreference?
        val clearHistory = findPreference("clear_history") as Preference?
        //setKey(VIEW_POS_KEY, "GGG", 2L)
        val historyItems = getKeys(VIEW_POS_KEY).size + getKeys(VIEWSTATE_KEY).size

        findPreference<ListPreference>("theme")?.setOnPreferenceChangeListener { preference, newValue ->
            activity?.recreate()
            return@setOnPreferenceChangeListener true
        }

        clearHistory?.summary = "$historyItems item${if (historyItems == 1) "" else "s"}"
        clearHistory?.setOnPreferenceClickListener {
            val alertDialog: AlertDialog? = activity?.let {
                val builder = AlertDialog.Builder(it)
                builder.apply {
                    setPositiveButton(
                        "OK"
                    ) { dialog, id ->
                        val amount = removeKeys(VIEW_POS_KEY) + removeKeys(VIEWSTATE_KEY)
                        removeKeys(VIEW_LST_KEY)
                        removeKeys(VIEW_DUR_KEY)
                        if (amount != 0) {
                            Toast.makeText(
                                context,
                                "Cleared $amount item${if (amount == 1) "" else "s"}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        thread {
                            ShiroApi.requestHome(true)
                        }
                        clearHistory.summary = "0 items"
                    }
                    setNegativeButton(
                        "Cancel"
                    ) { dialog, id ->
                        // User cancelled the dialog
                    }
                }
                // Set other dialog properties
                builder.setTitle("Clear watch history")
                // Create the AlertDialog
                builder.create()
            }
            if (getKeys(VIEW_POS_KEY).isNotEmpty() || getKeys(VIEWSTATE_KEY).isNotEmpty()) {
                alertDialog?.show()
            }
            return@setOnPreferenceClickListener true
        }
        val clearCache = findPreference("clear_cache") as Preference?
        clearCache?.setOnPreferenceClickListener {
            val glide = Glide.get(requireContext())
            glide.clearMemory()
            thread {
                glide.clearDiskCache()
            }
            val updateFile = File(activity!!.filesDir.toString() + "/Download/apk/update.apk")
            if (updateFile.exists()) {
                updateFile.delete()
            }
            Toast.makeText(context, "Cleared image cache", Toast.LENGTH_LONG).show()
            return@setOnPreferenceClickListener true
        }

        val donatorId = findPreference("donator_id") as Preference?
        val id: String = Settings.Secure.getString(context?.contentResolver, Settings.Secure.ANDROID_ID)

        val encodedString = id.md5()
        donatorId?.summary = if (isDonor) "Thanks for the donation :D" else encodedString
        donatorId?.setOnPreferenceClickListener {
            val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip: ClipData = ClipData.newPlainText("ID", encodedString)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(
                context!!,
                "Copied donor ID, give this to the devs to enable donor mode (if you have donated)",
                Toast.LENGTH_LONG
            ).show()
            return@setOnPreferenceClickListener true
        }

        fun isLoggedIntoMal(): Boolean {
            return DataStore.getKey<String>(MAL_TOKEN_KEY, MAL_ACCOUNT_ID, null) != null
        }

        fun isLoggedIntoAniList(): Boolean {
            return DataStore.getKey<String>(ANILIST_TOKEN_KEY, ANILIST_ACCOUNT_ID, null) != null
        }


        val anilistButton = findPreference("anilist_setting_btt") as Preference?
        val isLoggedInAniList = isLoggedIntoAniList()
        anilistButton?.summary = if (isLoggedInAniList) "Logged in" else "Not logged in"
        anilistButton?.setOnPreferenceClickListener {
            if (!isLoggedIntoAniList()) {
                AniListApi.authenticate()
            } else {
                val alertDialog: AlertDialog? = activity?.let {
                    val builder = AlertDialog.Builder(it)
                    builder.apply {
                        setPositiveButton(
                            "Logout"
                        ) { dialog, id ->
                            DataStore.removeKey(ANILIST_UNIXTIME_KEY, ANILIST_ACCOUNT_ID)
                            DataStore.removeKey(ANILIST_TOKEN_KEY, ANILIST_ACCOUNT_ID)
                            DataStore.removeKey(ANILIST_USER_KEY, ANILIST_ACCOUNT_ID)
                            anilistButton.summary = if (isLoggedIntoMal()) "Logged in" else "Not logged in"
                        }
                        setNegativeButton(
                            "Cancel"
                        ) { dialog, id ->
                            // User cancelled the dialog
                        }
                    }
                    // Set other dialog properties
                    builder.setTitle("Logout from AniList")

                    // Create the AlertDialog
                    builder.create()
                }
                alertDialog?.show()

            }
            anilistButton.summary = if (isLoggedIntoAniList()) "Logged in" else "Not logged in"

            return@setOnPreferenceClickListener true
        }

        val malButton = findPreference("mal_setting_btt") as Preference?
        val isLoggedInMAL = isLoggedIntoMal()
        malButton?.summary = if (isLoggedInMAL) "Logged in" else "Not logged in"
        malButton?.setOnPreferenceClickListener {
            if (!isLoggedIntoMal()) {
                MALApi.authenticate()
            } else {
                val alertDialog: AlertDialog? = activity?.let {
                    val builder = AlertDialog.Builder(it)
                    builder.apply {
                        setPositiveButton(
                            "Logout"
                        ) { dialog, id ->
                            DataStore.removeKey(MAL_TOKEN_KEY, MAL_ACCOUNT_ID)
                            DataStore.removeKey(MAL_REFRESH_TOKEN_KEY, MAL_ACCOUNT_ID)
                            DataStore.removeKey(MAL_USER_KEY, MAL_ACCOUNT_ID)
                            DataStore.removeKey(MAL_UNIXTIME_KEY, MAL_ACCOUNT_ID)
                            malButton.summary = if (isLoggedIntoMal()) "Logged in" else "Not logged in"
                        }
                        setNegativeButton(
                            "Cancel"
                        ) { dialog, id ->
                            // User cancelled the dialog
                        }
                    }
                    // Set other dialog properties
                    builder.setTitle("Logout from MAL")

                    // Create the AlertDialog
                    builder.create()
                }
                alertDialog?.show()
            }

            return@setOnPreferenceClickListener true
        }

        // Changelog
        val changeLog = findPreference("changelog") as Preference?
        changeLog?.setOnPreferenceClickListener {
            val alertDialog: AlertDialog? = activity?.let {
                val builder = AlertDialog.Builder(it)
                builder.apply {
                    setPositiveButton("OK") { _, _ -> }
                }
                // Set other dialog properties
                builder.setTitle(getString(R.string.version_code))
                builder.setMessage(getString(R.string.changelog))
                // Create the AlertDialog
                builder.create()
            }
            alertDialog?.show()
            return@setOnPreferenceClickListener true
        }
        val checkUpdates = findPreference("check_updates") as Preference?
        checkUpdates?.setOnPreferenceClickListener {
            thread {
                val update = ShiroApi.getAppUpdate()
                activity?.runOnUiThread {
                    if (update.shouldUpdate && update.updateVersion != null && update.updateURL != null) {
                        //startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.updateURL)))
                        DownloadManager.downloadUpdate(update.updateURL)
                        Toast.makeText(context, "New version (${update.updateVersion}) found", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "No updates found :(", Toast.LENGTH_LONG).show()
                    }
                }
            }
            return@setOnPreferenceClickListener true
        }
        val statusBarHidden = findPreference("statusbar_hidden") as SwitchPreference?
        statusBarHidden?.setOnPreferenceChangeListener { _, newValue ->
            MainActivity.changeStatusBarState(newValue == true)
            return@setOnPreferenceChangeListener true
        }
        val useExternalStorage = findPreference("use_external_storage") as SwitchPreference?
        useExternalStorage?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue == true) {
                if (!checkWrite()) {
                    requestRW()
                }
            }
            return@setOnPreferenceChangeListener true
        }
        // EASTER EGG THEME
        val versionButton = findPreference("version") as Preference?
        val coolMode = findPreference("cool_mode") as SwitchPreference?
        if (coolMode?.isChecked == true) {
            coolMode.isVisible = true
        }
        versionButton?.setOnPreferenceClickListener {
            if (easterEggClicks == 7 && coolMode?.isChecked == false) {
                Toast.makeText(context, "Unlocked cool mode", Toast.LENGTH_LONG).show()
                coolMode.isVisible = true
            }
            easterEggClicks++
            return@setOnPreferenceClickListener true
        }
        coolMode?.setOnPreferenceChangeListener { preference, newValue ->
            activity?.recreate()
            return@setOnPreferenceChangeListener true
        }

        val forceLandscape = findPreference("force_landscape") as SwitchPreference?
        forceLandscape?.setOnPreferenceChangeListener { preference, newValue ->
            if (newValue == true) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
            } else {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            return@setOnPreferenceChangeListener true
        }

        /*val autoDarkMode = findPreference("auto_dark_mode") as SwitchPreferenceCompat?
        val darkMode = findPreference("dark_mode") as SwitchPreferenceCompat?
        //darkMode?.isEnabled = autoDarkMode?.isChecked != true
        darkMode?.isChecked =
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        autoDarkMode?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { preference: Preference, any: Any ->
                //darkMode?.isEnabled = any != true
                if (any == true) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                    val isDarkMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    darkMode?.isChecked = isDarkMode == Configuration.UI_MODE_NIGHT_YES
                } else {
                    if (darkMode?.isChecked == true) {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    } else {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    }
                }
                return@OnPreferenceChangeListener true
            }
        darkMode?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { preference: Preference, any: Any ->
                if (any == true) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatD        video_next_holder.isClickable = isClickelegate.MODE_NIGHT_YES)
                } else {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                }
                return@OnPreferenceChangeListener true
            }
         */
    }
}