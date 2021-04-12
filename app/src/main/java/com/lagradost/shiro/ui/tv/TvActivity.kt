package com.lagradost.shiro.ui.tv

import android.app.Activity
import android.os.Bundle
import com.lagradost.shiro.DataStore
import com.lagradost.shiro.R
import com.lagradost.shiro.ShiroApi
import kotlin.concurrent.thread

/**
 * Loads [MainFragmentTV].
 */
class TvActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tv)
        DataStore.init(this)
        thread {
            ShiroApi.init()
        }
    }
}