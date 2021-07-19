package com.lagradost.shiro.ui.library

import android.content.res.ColorStateList
import android.view.View.VISIBLE
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import androidx.preference.PreferenceManager
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.jaredrummler.cyanea.Cyanea
import com.lagradost.shiro.R
import com.lagradost.shiro.ui.GlideApp
import com.lagradost.shiro.ui.toPx
import com.lagradost.shiro.utils.AppUtils.getCurrentActivity
import com.lagradost.shiro.utils.AppUtils.loadPage
import com.lagradost.shiro.utils.MALApi
import kotlinx.android.synthetic.main.search_result.view.imageText
import kotlinx.android.synthetic.main.search_result.view.imageView
import kotlinx.android.synthetic.main.search_result_compact.view.*

class CardListAdapter : GenericListAdapter<MALApi.Companion.Data>(
    R.layout.list_card_compact,
    bind = { item, holder, itemCount ->
        with(holder.itemView) {
            val itemView = this.rootView
            val coverHeight: Int = 80.toPx
            // ------------------------------------------------
            itemView.backgroundCard.backgroundTintList = ColorStateList.valueOf(
                Cyanea.instance.backgroundColorDark
            )

            itemView.backgroundCard.setOnClickListener {
                getCurrentActivity()?.loadPage(item.node.id.toString(), item.node.title, true)
            }

            itemView.apply {
                layoutParams = LinearLayout.LayoutParams(
                    MATCH_PARENT,
                    coverHeight
                )
            }
            itemView.imageText?.text = item.node.title
            itemView.imageSubText?.visibility = VISIBLE
            itemView.imageSubText?.text = "★ ${item.list_status.score}"
            itemView.setOnClickListener {
                getCurrentActivity()?.loadPage(item.node.id.toString(), item.node.title, true)
            }

            itemView.imageView.setOnClickListener {
                getCurrentActivity()?.loadPage(item.node.id.toString(), item.node.title, true)
                //activity?.loadPage(card.slug, card.name)
                /*MainActivity.loadPage(card)*/
            }

            val glideUrl =
                GlideUrl(item.node.mainPicture.medium)
            context.let {
                val settingsManager = PreferenceManager.getDefaultSharedPreferences(it)
                val savingData = settingsManager.getBoolean("data_saving", false)
                GlideApp.with(it)
                    .load(glideUrl)
                    .transition(DrawableTransitionOptions.withCrossFade(100))
                    .onlyRetrieveFromCache(savingData)
                    .into(itemView.imageView)
            }
        }

    }
)