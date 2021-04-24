package com.lagradost.shiro.ui.tv

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.lagradost.shiro.R

/** Loads [PlaybackVideoFragment]. */
class PlaybackActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ///theme.applyStyle(R.style.Theme_LeanbackCustom, true)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, NowPlayingFragment())
                .commitAllowingStateLoss()
        }
    }
}