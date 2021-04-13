package com.lagradost.shiro.ui.tv

import android.app.Activity
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import com.lagradost.shiro.DataStore
import com.lagradost.shiro.MainActivity.Companion.activity
import com.lagradost.shiro.R
import com.lagradost.shiro.ShiroApi
import kotlin.concurrent.thread

/**
 * Loads [MainFragmentTV].
 */
class TvActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ----- Themes ----

        theme.applyStyle(R.style.AppTheme, true)

        // -----------------
        setContentView(R.layout.activity_tv)
        DataStore.init(this)
        thread {
            ShiroApi.init()
        }
    }
}