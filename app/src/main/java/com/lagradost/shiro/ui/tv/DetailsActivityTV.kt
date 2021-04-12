package com.lagradost.shiro.ui.tv

import android.app.Activity
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.lagradost.shiro.R

/**
 * Details activity class that loads [VideoDetailsFragment] class.
 */
class DetailsActivityTV : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_details_tv)
    }

    companion object {
        const val SHARED_ELEMENT_NAME = "hero"
        const val MOVIE = "Movie"
    }
}