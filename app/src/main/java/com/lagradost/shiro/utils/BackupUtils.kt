package com.lagradost.shiro.utils

import android.os.Environment
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.shiro.ui.settings.SettingsFragment.Companion.restoreFileSelector
import com.lagradost.shiro.utils.AppUtils.checkWrite
import com.lagradost.shiro.utils.AppUtils.requestRW
import com.lagradost.shiro.utils.DataStore.mapper
import java.io.File
import java.lang.System.currentTimeMillis
import java.text.SimpleDateFormat
import java.util.*

object BackupUtils {
    // Kinda hack, but I couldn't think of a better way
    data class BackupVars(
        @JsonProperty("_Bool") val _Bool: Map<String, Boolean>?,
        @JsonProperty("_Int") val _Int: Map<String, Int>?,
        @JsonProperty("_String") val _String: Map<String, String>?,
        @JsonProperty("_Float") val _Float: Map<String, Float>?,
        @JsonProperty("_Long") val _Long: Map<String, Long>?,
        @JsonProperty("_StringSet") val _StringSet: Map<String, Set<String>?>?,
    )

    data class BackupFile(
        @JsonProperty("datastore") val datastore: BackupVars,
        @JsonProperty("settings") val settings: BackupVars
    )

    fun FragmentActivity.backup() {
        if (checkWrite()) {
            val downloadDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString() + "/Shiro/"
            val date = SimpleDateFormat("yyyy_MM_dd_HH_mm").format(Date(currentTimeMillis()))
            val allDataFile = File(downloadDir + "Shiro_Backup_${date}.xml")

            val allData = DataStore.getSharedPrefs().all
            val allSettings = DataStore.getDefaultSharedPrefs().all

            val allDataSorted = BackupVars(
                allData.filter { it.value is Boolean } as? Map<String, Boolean>,
                allData.filter { it.value is Int } as? Map<String, Int>,
                allData.filter { it.value is String } as? Map<String, String>,
                allData.filter { it.value is Float } as? Map<String, Float>,
                allData.filter { it.value is Long } as? Map<String, Long>,
                allData.filter { it.value as? Set<String> != null } as? Map<String, Set<String>>
            )

            val allSettingsSorted = BackupVars(
                allSettings.filter { it.value is Boolean } as? Map<String, Boolean>,
                allSettings.filter { it.value is Int } as? Map<String, Int>,
                allSettings.filter { it.value is String } as? Map<String, String>,
                allSettings.filter { it.value is Float } as? Map<String, Float>,
                allSettings.filter { it.value is Long } as? Map<String, Long>,
                allSettings.filter { it.value as? Set<String> != null } as? Map<String, Set<String>>
            )

            val backupFile = BackupFile(
                allDataSorted,
                allSettingsSorted
            )

            allDataFile.writeText(mapper.writeValueAsString(backupFile))

            /*val customPreferences = File(filesDir.parent + "/shared_prefs/${packageName}_preferences.xml")
            val customPreferencesNew = File(downloadDir + "Shiro_Backup_Data_${date}.xml")
            val settingsPreferences = File(filesDir.parent + "/shared_prefs/rebuild_preference.xml")
            val settingsPreferencesNew = File(downloadDir + "Shiro_Backup_Settings_${date}.xml")

            customPreferences.copyTo(customPreferencesNew)
            settingsPreferences.copyTo(settingsPreferencesNew)*/
            Toast.makeText(this, "Successfully stored settings and data to $downloadDir", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Storage permissions missing, please try again", Toast.LENGTH_LONG).show()
            requestRW()
            return
        }

        /*SharedPreferencesBackupHelper(this)
            val all = DataStore.getSharedPrefs().all
            //println(all)*/
    }

    fun FragmentActivity.restorePrompt() {
        runOnUiThread {
            restoreFileSelector?.launch("*/*")
        }
    }

    private fun <T> restoreMap(map: Map<String, T>?, isEditingAppSettings: Boolean = false) {
        val blackList = listOf(
            "cool_mode",
            "beta_theme",
            "purple_theme",
            "subscribe_to_updates",
            "subscribe_to_announcements",
            "subscriptions_bookmarked",
            "subscriptions",
            "legacy_bookmarks"
        )
        val filterRegex = Regex("""^(${blackList.joinToString(separator = "|") })""")
        map?.filter { !filterRegex.containsMatchIn(it.key) }?.forEach {
            DataStore.setKeyRaw(it.key, it.value, isEditingAppSettings)
        }
    }

    fun restore(backupFile: BackupFile, restoreSettings: Boolean, restoreDataStore: Boolean) {
        if (restoreSettings) {
            restoreMap(backupFile.settings._Bool, true)
            restoreMap(backupFile.settings._Int, true)
            restoreMap(backupFile.settings._String, true)
            restoreMap(backupFile.settings._Float, true)
            restoreMap(backupFile.settings._Long, true)
            restoreMap(backupFile.settings._StringSet, true)
        }

        if (restoreDataStore) {
            restoreMap(backupFile.datastore._Bool)
            restoreMap(backupFile.datastore._Int)
            restoreMap(backupFile.datastore._String)
            restoreMap(backupFile.datastore._Float)
            restoreMap(backupFile.datastore._Long)
            restoreMap(backupFile.datastore._StringSet)
        }
    }
}