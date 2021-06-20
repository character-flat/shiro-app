package com.lagradost.shiro.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.widget.ImageView
import androidx.core.view.updateMargins
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.lagradost.shiro.R
import com.lagradost.shiro.ui.GlideApp
import com.lagradost.shiro.ui.LastEpisodeInfo
import com.lagradost.shiro.ui.result.RESULT_FRAGMENT_TAG
import com.lagradost.shiro.ui.result.ResultFragment
import com.lagradost.shiro.ui.toPx
import com.lagradost.shiro.ui.tv.TvActivity.Companion.tvActivity
import com.lagradost.shiro.utils.AppUtils.addFragmentOnlyOnce
import com.lagradost.shiro.utils.AppUtils.loadPage
import com.lagradost.shiro.utils.AppUtils.loadPlayer
import com.lagradost.shiro.utils.AppUtils.onLongCardClick
import com.lagradost.shiro.utils.DataStore
import com.lagradost.shiro.utils.ShiroApi.Companion.getFullUrlCdn
import com.lagradost.shiro.utils.ShiroApi.Companion.requestHome
import com.lagradost.shiro.utils.VIEW_LST_KEY
import kotlinx.android.synthetic.main.home_card.view.home_card_root
import kotlinx.android.synthetic.main.home_card.view.imageText
import kotlinx.android.synthetic.main.home_card.view.imageView
import kotlinx.android.synthetic.main.home_card_recently_seen.view.*


class CardContinueAdapter(
    activity: FragmentActivity,
    animeList: List<LastEpisodeInfo?>?,
    private val isOnTop: Boolean = false
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var cardList = animeList
    var activity: FragmentActivity? = activity

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return CardViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.home_card_recently_seen, parent, false),
            activity!!
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is CardViewHolder -> {
                holder.bind(cardList?.get(position))
            }
        }


        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            val subFocus =
                view.tv_button_info.hasFocus() || view.tv_button_cancel.hasFocus() || view.tv_button_remove.hasFocus()
            val toSize = if (hasFocus) 1.1f else 1.0f
            val fromSize = if (!hasFocus) 1.1f else 1.0f
            val animation = ScaleAnimation(
                fromSize,
                toSize,
                fromSize,
                toSize,
                Animation.RELATIVE_TO_SELF,
                0.5f,
                Animation.RELATIVE_TO_SELF,
                0.5f
            )
            animation.duration = 100
            animation.isFillEnabled = true
            animation.fillAfter = true
            view.startAnimation(animation)
            if (!subFocus) view.menu_root.visibility = GONE
            view.home_card_recently_seen.radius = if (hasFocus) 0F else 6.toPx.toFloat()
            if (isOnTop) {
                activity?.findViewById<View>(R.id.tv_menu_bar)?.visibility = VISIBLE
            } else {
                activity?.findViewById<View>(R.id.tv_menu_bar)?.visibility = GONE
            }
        }

    }

    override fun getItemCount(): Int {
        return if (cardList?.size == null) 0 else cardList!!.size
    }

    class CardViewHolder(itemView: View, val activity: FragmentActivity) : RecyclerView.ViewHolder(itemView) {
        val card: ImageView = itemView.imageView
        fun bind(cardInfo: LastEpisodeInfo?) {
            if (cardInfo != null) {
                if (tvActivity != null) {
                    val param = itemView.layoutParams as ViewGroup.MarginLayoutParams
                    param.updateMargins(
                        5.toPx,
                        10.toPx,
                        5.toPx,
                        10.toPx
                    )
                    itemView.layoutParams = param
                }
                val glideUrl =
                    GlideUrl(cardInfo.id?.let { getFullUrlCdn(it.image) })
                //  activity?.runOnUiThread {

                GlideApp.with(activity)
                    .load(glideUrl)
                    .transition(DrawableTransitionOptions.withCrossFade(100))
                    .into(card.imageView)

                itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).start()
                itemView.imageText.text =
                    if (cardInfo.id?.name?.endsWith("Dubbed") == true) "✦ Episode ${cardInfo.episodeIndex + 1}" else "Episode ${cardInfo.episodeIndex + 1}"
                if (cardInfo.id != null && tvActivity == null) {
                    itemView.infoButton.visibility = VISIBLE
                    itemView.infoButton.setOnClickListener {
                        activity.loadPage(cardInfo.id.slug, cardInfo.title)
                    }
                } else if (cardInfo.id != null) {
                    // TV INFO BUTTON
                    itemView.infoButton.visibility = GONE
                    itemView.tv_button_info.setOnClickListener {
                        activity.addFragmentOnlyOnce(
                            R.id.home_root_tv,
                            ResultFragment.newInstance(cardInfo.id.slug, cardInfo.title),
                            RESULT_FRAGMENT_TAG
                        )
                    }
                }

                itemView.home_card_root.setOnLongClickListener {
                    if (tvActivity != null) {
                        itemView.menu_root.visibility = VISIBLE
                        itemView.tv_button_info.requestFocus()
                        itemView.home_card_root.isFocusable = false
                    } else {
                        cardInfo.id?.let { card ->
                            itemView.scaleY = 0.9f
                            itemView.scaleX = 0.9f
                            itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).start()
                            activity.onLongCardClick(card)
                        }
                    }
                    return@setOnLongClickListener true
                }
                itemView.home_card_root.setOnClickListener {
                    cardInfo.id?.let { data ->
                        activity.loadPlayer(cardInfo.episodeIndex, cardInfo.pos, data, cardInfo.anilistID, cardInfo.malID)
                    }
                }
                if (tvActivity != null) {
                    itemView.removeButton.visibility = GONE
                    itemView.tv_button_cancel.setOnClickListener {
                        itemView.home_card_root.isFocusable = true
                        itemView.menu_root.visibility = GONE
                        itemView.home_card_root.requestFocus()
                    }
                    itemView.tv_button_remove.setOnClickListener {
                        DataStore.removeKey(VIEW_LST_KEY, cardInfo.aniListId)
                        requestHome(true)
                    }
                } else {
                    itemView.removeButton.visibility = VISIBLE
                    itemView.removeButton.setOnClickListener {
                        DataStore.removeKey(VIEW_LST_KEY, cardInfo.aniListId)
                        requestHome(true)
                    }
                }
                if (cardInfo.dur > 0 && cardInfo.pos > 0) {
                    var progress: Int = (cardInfo.pos * 100L / cardInfo.dur).toInt()
                    if (progress < 5) {
                        progress = 5
                    } else if (progress > 95) {
                        progress = 100
                    }
                    itemView.video_progress.progress = progress
                } else {
                    itemView.video_progress.alpha = 0f
                }
            }
        }

    }
}
