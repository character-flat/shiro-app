package com.lagradost.shiro.utils

import android.os.Environment
import android.provider.ContactsContract
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
    data class BackupFile(
        @JsonProperty("_Bool") val _Bool: Map<String, Boolean>?,
        @JsonProperty("_Int") val _Int: Map<String, Int>?,
        @JsonProperty("_String") val _String: Map<String, String>?,
        @JsonProperty("_Float") val _Float: Map<String, Float>?,
        @JsonProperty("_Long") val _Long: Map<String, Long>?,
        @JsonProperty("_StringSet") val _StringSet: Map<String, Set<String>?>?,
    )

    fun FragmentActivity.backup() {
        if (checkWrite()) {
            val downloadDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString() + "/Shiro/"
            val date = SimpleDateFormat("yyyy_MM_dd_HH_mm").format(Date(currentTimeMillis()))
            val allDataFile = File(downloadDir + "Shiro_Backup_ALL_${date}.xml")

            val allData = DataStore.getSharedPrefs().all
            val bool = allData.filter { it.value is Boolean } as? Map<String, Boolean>
            val int = allData.filter { it.value is Int } as? Map<String, Int>
            val string = allData.filter { it.value is String } as? Map<String, String>
            val float = allData.filter { it.value is Float } as? Map<String, Float>
            val long = allData.filter { it.value is Long } as? Map<String, Long>
            val stringSet = allData.filter { it.value as? Set<String> != null } as? Map<String, Set<String>>

            val allDataSorted = BackupFile(
                bool,
                int,
                string,
                float,
                long,
                stringSet
            )
            allDataFile.writeText(mapper.writeValueAsString(allDataSorted))

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

    private fun <T> restoreMap(map: Map<String, T>?) {
        map?.forEach {
            DataStore.setKeyRaw(it.key, it.value)
        }
    }

    fun restore(backupFile: BackupFile) {
        restoreMap(backupFile._Bool)
        restoreMap(backupFile._Int)
        restoreMap(backupFile._String)
        restoreMap(backupFile._Float)
        restoreMap(backupFile._Long)
        restoreMap(backupFile._StringSet)
    }
}