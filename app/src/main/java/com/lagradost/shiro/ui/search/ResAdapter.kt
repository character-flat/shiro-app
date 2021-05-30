package com.lagradost.shiro.ui.search

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.lagradost.shiro.ui.result.ResultFragment
import com.lagradost.shiro.*
import com.lagradost.shiro.utils.ShiroApi.Companion.getFullUrlCdn
import com.lagradost.shiro.utils.ShiroApi.Companion.requestHome
import com.lagradost.shiro.ui.MainActivity.Companion.activity
import com.lagradost.shiro.ui.AutofitRecyclerView
import com.lagradost.shiro.ui.BookmarkedTitle
import com.lagradost.shiro.ui.GlideApp
import com.lagradost.shiro.ui.toPx
import com.lagradost.shiro.utils.AppUtils.fixCardTitle
import com.lagradost.shiro.utils.AppUtils.onLongCardClick
import com.lagradost.shiro.utils.AppUtils.settingsManager
import com.lagradost.shiro.utils.BOOKMARK_KEY
import com.lagradost.shiro.utils.DataStore
import com.lagradost.shiro.utils.ShiroApi
import kotlinx.android.synthetic.main.search_result.view.imageText
import kotlinx.android.synthetic.main.search_result.view.imageView
import kotlinx.android.synthetic.main.search_result_compact.view.*
import kotlin.concurrent.thread
import kotlin.math.roundToInt

class ResAdapter(
    context: Context,
    animeList: ArrayList<ShiroApi.CommonAnimePage>,
    resView: AutofitRecyclerView,
    forceDisableCompact: Boolean = false
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var cardList = animeList
    var context: Context? = context
    private var resView: AutofitRecyclerView? = resView
    private val compactView =
        settingsManager?.getBoolean("compact_search_enabled", true) == true && !forceDisableCompact

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val hideDubbed = settingsManager?.getBoolean("hide_dubbed", false) == true
        if (hideDubbed) {
            cardList = cardList.filter { !it.name.endsWith("Dubbed") } as ArrayList<ShiroApi.CommonAnimePage>
        }

        val layout = if (compactView) R.layout.search_result_compact else R.layout.search_result
        return CardViewHolder(
            LayoutInflater.from(parent.context).inflate(layout, parent, false),
            context!!,
            resView!!,
            compactView
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is CardViewHolder -> {
                holder.bind(cardList[position])
            }

        }
    }

    override fun getItemCount(): Int {
        return cardList.size
    }

    class CardViewHolder
    constructor(itemView: View, _context: Context, resView: AutofitRecyclerView, private val compactView: Boolean) :
        RecyclerView.ViewHolder(itemView) {
        val context = _context
        val cardView: ImageView = itemView.imageView
        private val coverHeight: Int = if (compactView) 80.toPx else (resView.itemWidth / 0.68).roundToInt()
        fun bind(card: ShiroApi.CommonAnimePage) {
            if (compactView) {
                // COPIED -----------------------------------------
                var isBookmarked = DataStore.containsKey(BOOKMARK_KEY, card.slug)
                fun toggleHeartVisual(_isBookmarked: Boolean) {
                    if (_isBookmarked) {
                        itemView.title_bookmark.setImageResource(R.drawable.filled_heart)
                    } else {
                        itemView.title_bookmark.setImageResource(R.drawable.outlined_heart)
                    }
                }

                fun toggleHeart(_isBookmarked: Boolean) {
                    isBookmarked = _isBookmarked
                    toggleHeartVisual(_isBookmarked)
                    /*Saving the new bookmark in the database*/
                    if (_isBookmarked) {
                        DataStore.setKey(
                            BOOKMARK_KEY,
                            card.slug,
                            BookmarkedTitle(
                                card.name,
                                card.image,
                                card.slug
                            )
                        )
                    } else {
                        DataStore.removeKey(BOOKMARK_KEY, card.slug)
                    }
                    thread {
                        requestHome(true)
                    }
                }
                toggleHeartVisual(isBookmarked)
                itemView.bookmark_holder.setOnClickListener {
                    toggleHeart(!isBookmarked)
                }
                // ------------------------------------------------
                itemView.backgroundCard.setOnClickListener {
                    activity?.supportFragmentManager?.beginTransaction()
                        ?.setCustomAnimations(R.anim.enter, R.anim.exit, R.anim.pop_enter, R.anim.pop_exit)
                        ?.add(R.id.homeRoot, ResultFragment.newInstance(card.slug))
                        ?.commitAllowingStateLoss()
                }
                cardView.setOnLongClickListener {
                    if (context.onLongCardClick(card)) toggleHeart(!isBookmarked)
                    return@setOnLongClickListener true
                }
            } else {
                cardView.setOnLongClickListener {
                    context.onLongCardClick(card)
                    return@setOnLongClickListener true
                }
            }

            itemView.apply {
                layoutParams = LinearLayout.LayoutParams(
                    MATCH_PARENT,
                    coverHeight
                )
            }
            itemView.imageText.text = fixCardTitle(card.name)
            cardView.setOnClickListener {
                activity?.supportFragmentManager?.beginTransaction()
                    ?.setCustomAnimations(R.anim.enter, R.anim.exit, R.anim.pop_enter, R.anim.pop_exit)
                    ?.add(R.id.homeRoot, ResultFragment.newInstance(card.slug))
                    ?.commitAllowingStateLoss()


                /*MainActivity.loadPage(card)*/
            }

            val glideUrl =
                GlideUrl(getFullUrlCdn(card.image)) { ShiroApi.currentHeaders }
            context.let {
                GlideApp.with(it)
                    .load(glideUrl)
                    .transition(DrawableTransitionOptions.withCrossFade(100))
                    .into(cardView)
            }


        }

    }
}
