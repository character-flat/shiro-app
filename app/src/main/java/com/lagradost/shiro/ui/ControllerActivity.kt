package com.lagradost.shiro.ui

import android.os.Bundle
import android.view.Menu
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.media.uicontroller.UIController
import com.google.android.gms.cast.framework.media.widget.ExpandedControllerActivity
import com.lagradost.shiro.R
import org.json.JSONObject

class SkipOpController(val view: ImageView) : UIController() {
    override fun onMediaStatusUpdated() {
        super.onMediaStatusUpdated()
        view.setImageResource(R.drawable.exo_controls_fastforward)
        view.setOnClickListener {
            remoteMediaClient.seek(remoteMediaClient.approximateStreamPosition + 85000)
        }
    }
}

class SelectSourceController(val view: ImageView) : UIController() {
    override fun onMediaStatusUpdated() {
        super.onMediaStatusUpdated()
        println("Status update")
        var overrideClick = false
        view.setImageResource(R.drawable.ic_baseline_playlist_play_24)
        view.setOnClickListener {
            remoteMediaClient.queueSetRepeatMode(MediaStatus.REPEAT_MODE_REPEAT_ALL, JSONObject())
            //remoteMediaClient.mediaQueue.itemCount
            //println(remoteMediaClient.mediaInfo.customData)
            //remoteMediaClient.queueJumpToItem()
            lateinit var dialog: AlertDialog
            val items = mutableListOf<Pair<Int, String>>()
            for (i in 0 until remoteMediaClient.mediaQueue.itemCount) {
                (remoteMediaClient.mediaQueue.getItemAtIndex(i)?.media?.customData?.get("data") as? String)?.let { name ->
                    items.add(
                        remoteMediaClient.mediaQueue.getItemAtIndex(i)!!.itemId to name
                    )
                }
            }
            // TODO FIX
            if (items.isNotEmpty()) {
                val builder = AlertDialog.Builder(view.context, R.style.AlertDialogCustom)
                builder.setTitle("Pick source")

                builder.setSingleChoiceItems(
                    items.map{it.second}.toTypedArray(),
                    -1
                ) { _, which ->
                    println(remoteMediaClient.queueJumpToItem(items[which].first, remoteMediaClient.approximateStreamPosition, null))
                    dialog.dismiss()
                }
                dialog = builder.create()
                dialog.show()
            } else if (!overrideClick){
                // HACK! Because it doesn't work on the first click
                overrideClick = true
                view.performClick()
            }

        }
    }
}

class ControllerActivity : ExpandedControllerActivity() {
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.cast_expanded_controller_menu, menu)
        CastButtonFactory.setUpMediaRouteButton(this, menu, R.id.media_route_menu_item)
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sourcesButton: ImageView = getButtonImageViewAt(0)
        val skipOpButton: ImageView = getButtonImageViewAt(3)
        uiMediaController.bindViewToUIController(sourcesButton, SelectSourceController(sourcesButton))
        uiMediaController.bindViewToUIController(skipOpButton, SkipOpController(skipOpButton))
    }
}