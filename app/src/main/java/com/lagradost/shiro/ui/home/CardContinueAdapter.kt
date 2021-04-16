package com.lagradost.shiro.ui.home

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.lagradost.shiro.ui.result.ResultFragment
import com.lagradost.shiro.*
import com.lagradost.shiro.utils.ShiroApi.Companion.getFullUrlCdn
import com.lagradost.shiro.utils.ShiroApi.Companion.requestHome
import com.lagradost.shiro.ui.MainActivity.Companion.activity
import com.lagradost.shiro.ui.GlideApp
import com.lagradost.shiro.ui.LastEpisodeInfo
import com.lagradost.shiro.ui.MainActivity
import com.lagradost.shiro.utils.AppApi.loadPlayer
import com.lagradost.shiro.utils.DataStore
import com.lagradost.shiro.utils.VIEW_LST_KEY
import kotlinx.android.synthetic.main.home_card.view.home_card_root
import kotlinx.android.synthetic.main.home_card.view.imageText
import kotlinx.android.synthetic.main.home_card.view.imageView
import kotlinx.android.synthetic.main.home_card_recently_seen.view.*


class CardContinueAdapter(
    context: Context,
    animeList: List<LastEpisodeInfo?>?
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var cardList = animeList
    var context: Context? = context

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return CardViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.home_card_recently_seen, parent, false),
            context!!
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is CardViewHolder -> {
                holder.bind(cardList?.get(position))
            }

        }
    }

    override fun getItemCount(): Int {
        return if (cardList?.size == null) 0 else cardList!!.size
    }

    class CardViewHolder(itemView: View, _context: Context) : RecyclerView.ViewHolder(itemView) {
        val context = _context
        val card: ImageView = itemView.imageView
        fun bind(cardInfo: LastEpisodeInfo?) {
            if (cardInfo != null) {
                val glideUrl =
                    GlideUrl(cardInfo.id?.let { getFullUrlCdn(it.image) })
                //  activity?.runOnUiThread {
                context.let {
                    GlideApp.with(it)
                        .load(glideUrl)
                        .transition(DrawableTransitionOptions.withCrossFade(100))
                        .into(card.imageView)
                }
                itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).start()
                itemView.imageText.text =
                    if (cardInfo.id?.name?.endsWith("Dubbed") == true) "✦ Episode ${cardInfo.episodeIndex + 1}" else "Episode ${cardInfo.episodeIndex + 1}"
                if (cardInfo.id != null) {
                    itemView.infoButton.visibility = VISIBLE
                    itemView.infoButton.setOnClickListener {
                        activity?.supportFragmentManager?.beginTransaction()
                            ?.setCustomAnimations(R.anim.enter, R.anim.exit, R.anim.pop_enter, R.anim.pop_exit)
                            ?.add(R.id.homeRoot, ResultFragment.newInstance(cardInfo.id.slug))
                            ?.commitAllowingStateLoss()

                    }
                } else {
                    itemView.infoButton.visibility = GONE
                }
                itemView.home_card_root.setOnLongClickListener {
                    Toast.makeText(context, cardInfo.id?.name, Toast.LENGTH_SHORT).show()
                    return@setOnLongClickListener true
                }
                itemView.home_card_root.setOnClickListener {
                    cardInfo.id?.let { it1 -> activity?.loadPlayer(cardInfo.episodeIndex, cardInfo.pos, it1) }
                }
                itemView.removeButton.setOnClickListener {
                    DataStore.removeKey(VIEW_LST_KEY, cardInfo.aniListId)
                    requestHome(true)
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
