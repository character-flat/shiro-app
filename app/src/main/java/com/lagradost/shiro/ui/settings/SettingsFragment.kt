package com.lagradost.shiro.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.bumptech.glide.Glide
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import com.jaredrummler.cyanea.Cyanea
import com.lagradost.shiro.BuildConfig
import com.lagradost.shiro.R
import com.lagradost.shiro.ui.MainActivity.Companion.isDonor
import com.lagradost.shiro.ui.MainActivity.Companion.statusHeight
import com.lagradost.shiro.ui.WebViewFragment.Companion.onWebViewNavigated
import com.lagradost.shiro.ui.tv.TvActivity.Companion.tvActivity
import com.lagradost.shiro.utils.*
import com.lagradost.shiro.utils.AniListApi.Companion.authenticateAniList
import com.lagradost.shiro.utils.AppUtils.allApi
import com.lagradost.shiro.utils.AppUtils.changeStatusBarState
import com.lagradost.shiro.utils.AppUtils.checkWrite
import com.lagradost.shiro.utils.AppUtils.getCurrentActivity
import com.lagradost.shiro.utils.AppUtils.md5
import com.lagradost.shiro.utils.AppUtils.observe
import com.lagradost.shiro.utils.AppUtils.requestRW
import com.lagradost.shiro.utils.BackupUtils.backup
import com.lagradost.shiro.utils.BackupUtils.restore
import com.lagradost.shiro.utils.BackupUtils.restorePrompt
import com.lagradost.shiro.utils.DataStore.getKeys
import com.lagradost.shiro.utils.DataStore.mapper
import com.lagradost.shiro.utils.DataStore.removeKeys
import com.lagradost.shiro.utils.InAppUpdater.runAutoUpdate
import com.lagradost.shiro.utils.MALApi.Companion.authenticateMAL
import com.lagradost.shiro.utils.ShiroApi.Companion.requestHome
import java.io.File
import kotlin.concurrent.thread

class SettingsFragment : PreferenceFragmentCompat() {
    companion object {
        var isInSettings: Boolean = false
        var restoreFileSelector: ActivityResultLauncher<String>? = null
        var settingsViewModel: SettingsViewModel? = null
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return super.onCreateView(inflater, container, savedInstanceState)?.apply {
            setBackgroundColor(Cyanea.instance.backgroundColor)
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        settingsViewModel =
            settingsViewModel ?: ViewModelProvider(getCurrentActivity()!!).get(SettingsViewModel::class.java)
        restoreFileSelector = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            activity?.let { activity ->
                uri?.let {
                    try {
                        val input = activity.contentResolver.openInputStream(uri) ?: return@registerForActivityResult

                        /*val bis = BufferedInputStream(input)
                        val buf = ByteArrayOutputStream()
                        var result = bis.read()
                        while (result != -1) {
                            buf.write(result)
                            result = bis.read()
                        }
                        val fullText = buf.toString("UTF-8")

                         println(fullText)*/
                        val builder = AlertDialog.Builder(activity)
                        val items = arrayOf("Settings", "Data")
                        val preselectedItems = booleanArrayOf(true, true)
                        builder.setTitle("Select what to restore")
                            .setMultiChoiceItems(
                                items, preselectedItems
                            ) { _, which, isChecked ->
                                preselectedItems[which] = isChecked
                            }
                        builder.setPositiveButton("OK") { _, _ ->
                            val restoredValue = mapper.readValue<BackupUtils.BackupFile>(input)
                            restore(restoredValue, preselectedItems[0], preselectedItems[1])
                            requestHome(true)
                            val intent = Intent(activity, getCurrentActivity()!!::class.java)
                            startActivity(intent)
                            activity.finishAffinity()
                        }

                        builder.setNegativeButton("Cancel") { _, _ ->

                        }
                        builder.show()
                    } catch (e: Exception) {
                        println(e.printStackTrace())
                        Toast.makeText(activity, "Error restoring backup file :(", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }


        val settingsXml = if (tvActivity == null) R.xml.settings else R.xml.settings_tv
        setPreferencesFromResource(settingsXml, rootKey)

        var easterEggClicks = 0
        //val saveHistory = findPreference("save_history") as SwitchPreference?
        val clearHistory = findPreference("clear_history") as Preference?
        //setKey(VIEW_POS_KEY, "GGG", 2L)
        val historyItems = getKeys(VIEW_POS_KEY).size + getKeys(
            VIEWSTATE_KEY
        ).size


        /*
        val accentColors = findPreference<ListPreference>("accent_color")
        fun unlockPinkTheme() {
            accentColors?.entries = getCurrentActivity()!!.resources.getTextArray(R.array.AccentColorsFull)
            accentColors?.entryValues = getCurrentActivity()!!.resources.getTextArray(R.array.AccentColorsFull)
        }

        if (DataStore.getKey("pink_theme", false) == true) {
            unlockPinkTheme()
        }
        findPreference<ListPreference>("theme")?.setOnPreferenceChangeListener { _, _ ->
            getCurrentActivity()!!.changeTheme()

            //activity?.recreate()
            return@setOnPreferenceChangeListener true
        }*/

        findPreference<Preference>("cyanea_theme")?.setOnPreferenceClickListener {
            val intent = Intent(context, CustomCyaneaSettingsActivity::class.java)
            startActivity(intent)
            return@setOnPreferenceClickListener true
        }


        /*
        accentColors?.setOnPreferenceChangeListener { _, _ ->
            activity?.recreate()
            return@setOnPreferenceChangeListener true
        }*/

        clearHistory?.summary = "$historyItems item${if (historyItems == 1) "" else "s"}"
        clearHistory?.setOnPreferenceClickListener {
            val alertDialog: AlertDialog? = activity?.let {
                val builder = AlertDialog.Builder(it, R.style.AlertDialogCustom)
                builder.apply {
                    setPositiveButton(
                        "OK"
                    ) { dialog, id ->
                        val amount = removeKeys(VIEW_POS_KEY) + removeKeys(
                            VIEWSTATE_KEY
                        )
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
            if (getKeys(VIEW_POS_KEY).isNotEmpty() || getKeys(
                    VIEWSTATE_KEY
                ).isNotEmpty()
            ) {
                alertDialog?.show()
            }
            return@setOnPreferenceClickListener true
        }
        val clearCache = findPreference("clear_cache") as Preference?
        clearCache?.setOnPreferenceClickListener {
            val glide = Glide.get(getCurrentActivity()!!)
            glide.clearMemory()
            thread {
                glide.clearDiskCache()
            }
            val updateFile = File(activity?.filesDir.toString() + "/Download/apk/update.apk")
            if (updateFile.exists()) {
                updateFile.delete()
            }
            Toast.makeText(context, "Cleared image cache", Toast.LENGTH_LONG).show()
            return@setOnPreferenceClickListener true
        }

        val backupButton = findPreference("backup_btt") as Preference?
        backupButton?.setOnPreferenceClickListener {
            activity?.backup()
            return@setOnPreferenceClickListener true
        }

        val restoreButton = findPreference("restore_btt") as Preference?
        restoreButton?.setOnPreferenceClickListener {
            activity?.restorePrompt()
            return@setOnPreferenceClickListener true
        }

        val donorId = findPreference("donator_id") as Preference?
        val id: String = Settings.Secure.getString(context?.contentResolver, Settings.Secure.ANDROID_ID)

        val encodedString = id.md5()
        donorId?.summary = if (isDonor) "Thanks for the donation :D" else encodedString
        donorId?.setOnPreferenceClickListener {
            val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip: ClipData = ClipData.newPlainText("ID", encodedString)
            //clipboard.primaryClip = clip

            Toast.makeText(
                getCurrentActivity()!!,
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

        val selectedProvidersPreference = findPreference<MultiSelectListPreference>("selected_providers")
        val apiNames = APIS.map { it.name }

        selectedProvidersPreference?.entries = apiNames.toTypedArray()
        selectedProvidersPreference?.entryValues = apiNames.toTypedArray()
        selectedProvidersPreference?.setOnPreferenceChangeListener { preference, newValue ->
            allApi.providersActive = newValue as HashSet<String>

            return@setOnPreferenceChangeListener true
        }
        /*val vidstreamButton = findPreference<SwitchPreference>("alternative_vidstream")!!
        vidstreamButton.setOnPreferenceChangeListener { _, _ ->
            return@setOnPreferenceChangeListener true
        }*/

        val anilistButton = findPreference("anilist_setting_btt") as Preference?
        val isLoggedInAniList = isLoggedIntoAniList()
        val malButton = findPreference("mal_setting_btt") as Preference?

        observe(settingsViewModel!!.hasLoggedIntoMAL) {
            malButton?.summary = if (it) "Logged in" else "Not logged in"
        }
        observe(settingsViewModel!!.hasLoggedIntoAnilist) {
            anilistButton?.summary = if (it) "Logged in" else "Not logged in"
        }

        anilistButton?.summary = if (isLoggedInAniList) "Logged in" else "Not logged in"
        anilistButton?.setOnPreferenceClickListener {
            if (!isLoggedIntoAniList()) {
                activity?.authenticateAniList()
            } else {
                val alertDialog: AlertDialog? = activity?.let {
                    val builder = AlertDialog.Builder(it, R.style.AlertDialogCustom)
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

        val isLoggedInMAL = isLoggedIntoMal()
        malButton?.summary = if (isLoggedInMAL) "Logged in" else "Not logged in"
        malButton?.setOnPreferenceClickListener {
            if (!isLoggedIntoMal()) {
                activity?.authenticateMAL()
            } else {
                val alertDialog: AlertDialog? = activity?.let {
                    val builder = AlertDialog.Builder(it, R.style.AlertDialogCustom)
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
                        ) { _, id ->
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

        (findPreference("fullscreen_notch") as Preference?)?.isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

        // Changelog
        val changeLog = findPreference("changelog") as Preference?
        changeLog?.setOnPreferenceClickListener {
            val alertDialog: AlertDialog? = activity?.let {
                val builder = AlertDialog.Builder(it, R.style.AlertDialogCustom)
                builder.apply {
                    setPositiveButton("OK") { _, _ -> }
                }
                // Set other dialog properties
                builder.setTitle(BuildConfig.VERSION_NAME)
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
                if (context != null && activity != null) {
                    val updateSuccess = requireActivity().runAutoUpdate(getCurrentActivity()!!, false)
                    if (!updateSuccess) {
                        activity?.runOnUiThread {
                            Toast.makeText(activity, "No updates found :(", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            return@setOnPreferenceClickListener true
        }
        val statusBarHidden = findPreference("statusbar_hidden") as SwitchPreference?
        statusBarHidden?.setOnPreferenceChangeListener { _, newValue ->
            activity?.changeStatusBarState(newValue == true)?.let {
                statusHeight = it
            }
            return@setOnPreferenceChangeListener true
        }

        val subToUpdates = findPreference("subscribe_to_updates") as SwitchPreference?
        subToUpdates?.setOnPreferenceChangeListener { _, newValue ->
            subToUpdates.isEnabled = false
            if (newValue == true) {
                Firebase.messaging.subscribeToTopic("subscribe_to_updates")
                    .addOnCompleteListener { task ->
                        val msg = if (task.isSuccessful) {
                            subToUpdates.isChecked = true
                            "Subscribed"
                        } else {
                            "Subscription failed :("
                        }
                        //Log.d(TAG, msg)
                        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
                        subToUpdates.isEnabled = true
                    }
            } else {
                Firebase.messaging.unsubscribeFromTopic("subscribe_to_updates")
                    .addOnCompleteListener { task ->
                        val msg = if (task.isSuccessful) {
                            subToUpdates.isChecked = false
                            "Unsubscribed"
                        } else {
                            "Unsubscribing failed :("
                        }
                        //Log.d(TAG, msg)
                        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
                        subToUpdates.isEnabled = true
                    }
            }
            return@setOnPreferenceChangeListener false
        }

        val subToAnnouncements = findPreference("subscribe_to_announcements") as SwitchPreference?
        subToAnnouncements?.setOnPreferenceChangeListener { _, newValue ->
            subToAnnouncements.isEnabled = false
            if (newValue == true) {
                Firebase.messaging.subscribeToTopic("subscribe_to_announcements")
                    .addOnCompleteListener { task ->
                        val msg = if (task.isSuccessful) {
                            subToAnnouncements.isChecked = true
                            "Subscribed"
                        } else {
                            "Subscription failed :("
                        }
                        //Log.d(TAG, msg)
                        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
                        subToAnnouncements.isEnabled = true
                    }
            } else {
                Firebase.messaging.unsubscribeFromTopic("subscribe_to_announcements")
                    .addOnCompleteListener { task ->
                        val msg = if (task.isSuccessful) {
                            subToAnnouncements.isChecked = false
                            "Unsubscribed"
                        } else {
                            "Unsubscribing failed :("
                        }
                        //Log.d(TAG, msg)
                        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
                        subToAnnouncements.isEnabled = true
                    }
            }
            return@setOnPreferenceChangeListener false
        }


        val useExternalStorage = findPreference("use_external_storage") as SwitchPreference?
        /*useExternalStorage?.summaryOff = ""
        useExternalStorage?.summaryOn =
            if (usingScopedStorage)
                "Files downloaded to Movies/Shiro"
            else "Files downloaded to Download/Shiro"*/

        useExternalStorage?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue == true) {
                if (!activity?.checkWrite()!!) {
                    activity?.requestRW()
                }
            }
            return@setOnPreferenceChangeListener true
        }
        // EASTER EGG THEME
        val versionButton = findPreference("version") as Preference?

        versionButton?.summary = BuildConfig.VERSION_NAME + " Built on " + BuildConfig.BUILDDATE
        versionButton?.setOnPreferenceClickListener {
            /*if (easterEggClicks == 7) {
                if (DataStore.getKey("pink_theme", false) != true) {
                    Toast.makeText(context, "Unlocked pink theme", Toast.LENGTH_LONG).show()
                    DataStore.setKey("pink_theme", true)
                    unlockPinkTheme()
                }
            }
            easterEggClicks++*/
            return@setOnPreferenceClickListener true
        }

        (findPreference("pip_enabled") as Preference?)?.isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

        val forceLandscape = findPreference("force_landscape") as SwitchPreference?
        forceLandscape?.setOnPreferenceChangeListener { _, newValue ->
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

    private fun restoreState(hasEntered: Boolean) {
        if (hasEntered) {
            this.view?.visibility = GONE
        } else {
            this.view?.visibility = VISIBLE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        onWebViewNavigated -= ::restoreState
        isInSettings = false
    }

    override fun onResume() {
        isInSettings = true
        if (tvActivity != null) {
            onWebViewNavigated += ::restoreState
        }
        super.onResume()
    }

}