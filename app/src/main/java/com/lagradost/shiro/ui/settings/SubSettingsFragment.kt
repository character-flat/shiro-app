package com.lagradost.shiro.ui.settings

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import com.lagradost.shiro.BuildConfig
import com.lagradost.shiro.R
import com.lagradost.shiro.ui.toPx
import com.lagradost.shiro.utils.*
import com.lagradost.shiro.utils.AniListApi.Companion.authenticateAniList
import com.lagradost.shiro.utils.AppUtils.allApi
import com.lagradost.shiro.utils.AppUtils.checkWrite
import com.lagradost.shiro.utils.AppUtils.getCurrentActivity
import com.lagradost.shiro.utils.AppUtils.getNavigationBarSizeFake
import com.lagradost.shiro.utils.AppUtils.observe
import com.lagradost.shiro.utils.AppUtils.requestRW
import com.lagradost.shiro.utils.BackupUtils.backup
import com.lagradost.shiro.utils.BackupUtils.restorePrompt
import com.lagradost.shiro.utils.DataStore.getKeys
import com.lagradost.shiro.utils.DataStore.removeKeys
import com.lagradost.shiro.utils.InAppUpdater.runAutoUpdate
import com.lagradost.shiro.utils.MALApi.Companion.authenticateMAL
import java.io.File
import kotlin.concurrent.thread

const val XML_KEY = "xml"

class SubSettingsFragment : PreferenceFragmentCompat() {
    private var xmlFile: Int? = null

    companion object {
        var settingsViewModel: SettingsViewModel? = null

        fun newInstance(xml: Int) =
            SubSettingsFragment().apply {
                arguments = Bundle().apply {
                    putInt(XML_KEY, xml)
                }
            }
    }

   override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val rv: RecyclerView = listView; // This holds the PreferenceScreen's items
        rv.setPadding(0, getCurrentActivity()!!.getNavigationBarSizeFake() + 20.toPx, 0, 0)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        arguments?.getInt(XML_KEY)?.let {
            xmlFile = it
        }
    }

    fun setTitle(title: String) {
        getCurrentActivity()?.title = title
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        xmlFile?.let { setPreferencesFromResource(it, rootKey) }

        when (xmlFile) {

            R.xml.settings_general -> {

                /** General settings */

                setTitle("General settings")

                findPreference<SwitchPreference>("force_landscape")?.setOnPreferenceChangeListener { _, newValue ->
                    if (newValue == true) {
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
                    } else {
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                    return@setOnPreferenceChangeListener true
                }

                findPreference<SwitchPreference?>("use_external_storage")?.setOnPreferenceChangeListener { _, newValue ->
                    if (newValue == true) {
                        if (!activity?.checkWrite()!!) {
                            activity?.requestRW()
                        }
                    }
                    return@setOnPreferenceChangeListener true
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

                /** End of General settings */
            }

            R.xml.settings_player -> {

                /** Player settings */

                setTitle("Player settings")

                findPreference<Preference?>("pip_enabled")?.isVisible =
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                findPreference<Preference>("fullscreen_notch")?.isVisible =
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

                val selectedProvidersPreference = findPreference<MultiSelectListPreference?>("selected_providers")
                val apiNames = APIS.map { it.name }

                selectedProvidersPreference?.entries = apiNames.toTypedArray()
                selectedProvidersPreference?.entryValues = apiNames.toTypedArray()
                selectedProvidersPreference?.setOnPreferenceChangeListener { _, newValue ->
                    allApi.providersActive = newValue as HashSet<String>

                    return@setOnPreferenceChangeListener true
                }

                /** End of Player settings */

            }

            R.xml.settings_accounts -> {

                /** Account settings */

                setTitle("Account settings")

                settingsViewModel =
                    settingsViewModel ?: getCurrentActivity()?.let {
                        ViewModelProvider(it).get(
                            SettingsViewModel::class.java
                        )
                    }

                fun isLoggedIntoMal(): Boolean {
                    return DataStore.getKey<String>(MAL_TOKEN_KEY, MAL_ACCOUNT_ID, null) != null
                }

                fun isLoggedIntoAniList(): Boolean {
                    return DataStore.getKey<String>(ANILIST_TOKEN_KEY, ANILIST_ACCOUNT_ID, null) != null
                }

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
                                ) { _, _ ->
                                    DataStore.removeKey(ANILIST_UNIXTIME_KEY, ANILIST_ACCOUNT_ID)
                                    DataStore.removeKey(ANILIST_TOKEN_KEY, ANILIST_ACCOUNT_ID)
                                    DataStore.removeKey(ANILIST_USER_KEY, ANILIST_ACCOUNT_ID)
                                    anilistButton.summary = if (isLoggedIntoMal()) "Logged in" else "Not logged in"
                                }
                                setNegativeButton(
                                    "Cancel"
                                ) { _, _ ->
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
                                ) { _, _ ->
                                    DataStore.removeKey(MAL_TOKEN_KEY, MAL_ACCOUNT_ID)
                                    DataStore.removeKey(MAL_REFRESH_TOKEN_KEY, MAL_ACCOUNT_ID)
                                    DataStore.removeKey(MAL_USER_KEY, MAL_ACCOUNT_ID)
                                    DataStore.removeKey(MAL_UNIXTIME_KEY, MAL_ACCOUNT_ID)
                                    malButton.summary = if (isLoggedIntoMal()) "Logged in" else "Not logged in"
                                }
                                setNegativeButton(
                                    "Cancel"
                                ) { _, _ ->
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

                /** End of Account settings */
            }

            R.xml.settings_history -> {

                /** History settings */

                setTitle("History settings")

                val clearHistory = findPreference<Preference?>("clear_history")
                val historyItems = getKeys(VIEW_POS_KEY).size + getKeys(
                    VIEWSTATE_KEY
                ).size
                clearHistory?.summary = "$historyItems item${if (historyItems == 1) "" else "s"}"
                clearHistory?.setOnPreferenceClickListener {
                    val alertDialog: AlertDialog? = activity?.let {
                        val builder = AlertDialog.Builder(it, R.style.AlertDialogCustom)
                        builder.apply {
                            setPositiveButton(
                                "OK"
                            ) { _, _ ->
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
                            ) { _, _ ->
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

                /** End of History settings */
            }

            R.xml.settings_update_info -> {

                /** Update info settings */

                setTitle("Update info")

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

                /** End of Update info settings */

            }

            R.xml.settings_info -> {

                /** Info settings */

                setTitle("Info")

                findPreference<Preference?>("backup_btt")?.setOnPreferenceClickListener {
                    activity?.backup()
                    return@setOnPreferenceClickListener true
                }

                findPreference<Preference?>("restore_btt")?.setOnPreferenceClickListener {
                    activity?.restorePrompt()
                    return@setOnPreferenceClickListener true
                }

                /** End of Info settings */

            }

        }

    }
}