package com.lagradost.shiro.ui.home

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.widget.ScrollView
import android.widget.Toast
import androidx.core.view.updateMargins
import androidx.core.view.updateMarginsRelative
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.lagradost.shiro.ui.result.ResultFragment
import com.lagradost.shiro.*
import com.lagradost.shiro.utils.ShiroApi.Companion.getFullUrlCdn
import com.lagradost.shiro.ui.MainActivity.Companion.activity
import com.lagradost.shiro.ui.GlideApp
import com.lagradost.shiro.ui.toPx
import com.lagradost.shiro.utils.AppApi.fixCardTitle
import com.lagradost.shiro.utils.ShiroApi
import kotlinx.android.synthetic.main.home_card.view.*


class CardAdapter(
    context: Context,
    animeList: ArrayList<ShiroApi.CommonAnimePage?>,
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var cardList = animeList
    var context: Context? = context

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return CardViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.home_card, parent, false),
            context!!
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is CardViewHolder -> {
                holder.bind(cardList[position])
            }
        }
        holder.itemView.setOnFocusChangeListener { v, hasFocus ->
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
            v.startAnimation(animation)
        }
    }


    override fun getItemCount(): Int {
        return cardList.size
    }

    class CardViewHolder(itemView: View, _context: Context) : RecyclerView.ViewHolder(itemView) {
        val context = _context
        val card: View = itemView

        fun bind(cardInfo: ShiroApi.CommonAnimePage?) {
            val param = card.layoutParams as ViewGroup.MarginLayoutParams
            param.updateMargins(
                5.toPx,
                10.toPx,
                5.toPx,
                10.toPx
            )
            card.layoutParams = param
            if (cardInfo != null) {
                val glideUrl =
                    GlideUrl(getFullUrlCdn(cardInfo.image))
                //  activity?.runOnUiThread {
                context.let {
                    GlideApp.with(it)
                        .load(glideUrl)
                        .transition(DrawableTransitionOptions.withCrossFade(100))
                        .into(card.imageView)
                }
                itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).start()
                itemView.imageText.text = fixCardTitle(cardInfo.name)

                itemView.home_card_root.setOnLongClickListener {
                    Toast.makeText(context, cardInfo.name, Toast.LENGTH_SHORT).show()
                    return@setOnLongClickListener true
                }
                itemView.home_card_root.setOnClickListener {
                    println("SLIG ${cardInfo.slug}")
                    activity?.supportFragmentManager?.beginTransaction()
                        ?.setCustomAnimations(R.anim.enter, R.anim.exit, R.anim.pop_enter, R.anim.pop_exit)
                        ?.add(R.id.homeRoot, ResultFragment.newInstance(cardInfo.slug))
                        ?.commitAllowingStateLoss()
                }
            }
        }
    }
}
