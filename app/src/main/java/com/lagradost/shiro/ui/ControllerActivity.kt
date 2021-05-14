package com.lagradost.shiro.ui

import android.os.Bundle
import android.view.Menu
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.media.widget.ExpandedControllerActivity
import com.lagradost.shiro.R

class ControllerActivity : ExpandedControllerActivity() {
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.cast_expanded_controller_menu, menu)
        CastButtonFactory.setUpMediaRouteButton(this, menu, R.id.media_route_menu_item)
        return true
    }

    /*override fun onCreate(p0: Bundle?) {
        val customButtonView = getButtonImageViewAt(0)
        customButtonView.setImageResource(R.drawable.netflix_download)
        customButtonView.setOnClickListener {
            println("YEEETUS")
        }
        super.onCreate(p0)
    }*/
}