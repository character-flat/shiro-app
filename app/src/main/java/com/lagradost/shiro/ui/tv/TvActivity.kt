package com.lagradost.shiro.ui.tv

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.lagradost.shiro.utils.DataStore
import com.lagradost.shiro.R
import com.lagradost.shiro.ui.MainActivity
import com.lagradost.shiro.ui.result.ResultFragment.Companion.isInResults
import com.lagradost.shiro.utils.AppApi.init
import com.lagradost.shiro.utils.AppApi.isTv
import com.lagradost.shiro.utils.DownloadManager
import com.lagradost.shiro.utils.InAppUpdater.runAutoUpdate
import com.lagradost.shiro.utils.ShiroApi
import kotlin.concurrent.thread

/**
 * Loads [MainFragmentTV].
 */
class TvActivity : FragmentActivity() {
    companion object {
        var tvActivity: FragmentActivity? = null
        var isInSearch = false
    }

    override fun onBackPressed() {
        if (isInSearch && !isInResults) {
            this.supportFragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.enter, R.anim.exit, R.anim.pop_enter, R.anim.pop_exit)
                .replace(R.id.main_browse_fragment, MainFragmentTV())
                .commit()
        } else {
            super.onBackPressed()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        /*if (!isTv()) {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
            return
        }*/
        // ------ Init -----
        tvActivity = this
        DataStore.init(this)
        DownloadManager.init(this)
        init()
        thread {
            ShiroApi.init()
        }
        thread {
            runAutoUpdate(this)
        }
        // ----- Theme -----
        theme.applyStyle(R.style.AppTheme, true)
        theme.applyStyle(R.style.Theme_LeanbackCustom, true)
        // -----------------

        setContentView(R.layout.activity_tv)
    }

    override fun onResume() {
        super.onResume()
        // This is needed to avoid NPE crash due to missing context
        DataStore.init(this)
        DownloadManager.init(this)
        init()

    }
}