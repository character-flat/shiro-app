package com.lagradost.shiro.ui.tv

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.lagradost.shiro.utils.DataStore
import com.lagradost.shiro.R
import com.lagradost.shiro.utils.ShiroApi
import kotlin.concurrent.thread

/**
 * Loads [MainFragmentTV].
 */
class TvActivity : FragmentActivity() {
    companion object {
        var tvActivity: FragmentActivity? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tvActivity = this
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