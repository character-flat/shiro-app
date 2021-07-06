/*
 * Copyright (C) 2018 Jared Rummler
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lagradost.shiro.ui.settings

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.TypedValue
import android.view.MenuItem
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.jaredrummler.cyanea.Cyanea
import com.jaredrummler.cyanea.app.CyaneaAppCompatActivity
import com.lagradost.shiro.R
import com.lagradost.shiro.ui.MainActivity.Companion.statusHeight
import com.lagradost.shiro.utils.AppUtils.addFragmentOnlyOnce
import com.lagradost.shiro.utils.AppUtils.changeStatusBarState
import com.lagradost.shiro.utils.AppUtils.getNavigationBarSize
import com.lagradost.shiro.utils.AppUtils.transparentStatusAndNavigation

/**
 * Activity to show Cyanea preferences allowing the user to modify the primary, accent and background color of the app.
 */
open class SettingsActivity : CyaneaAppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsActivity = this
        supportActionBar?.show()
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        if (cyanea.isDark) {
            theme.applyStyle(R.style.lightText, true)
        } else {
            theme.applyStyle(R.style.darkText, true)
        }
        //transparentStatusAndNavigation()

        //getNavigationBarSize()

        // Hack to make tinting work
        delegate.localNightMode =
            if (Cyanea.instance.isLight) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO

        when (val xml = intent.getIntExtra(XML_KEY, -1)) {
            R.xml.custom_pref_cyanea -> {
                this.addFragmentOnlyOnce(android.R.id.content, CyaneaSettingsFragment.newInstance(), "SETTINGS")
            }
            -1 -> finish()
            else -> {
                this.addFragmentOnlyOnce(android.R.id.content, SubSettingsFragment.newInstance(xml), "SETTINGS")
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> {
            finish()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        statusHeight = changeStatusBarState(settingsManager.getBoolean("statusbar_hidden", true))
        super.onResume()
    }

    companion object {
        var settingsActivity: SettingsActivity? = null
    }

    override fun onDestroy() {
        settingsActivity = null
        super.onDestroy()
    }
}
