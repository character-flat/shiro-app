package com.lagradost.shiro.ui.library

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.jaredrummler.cyanea.Cyanea
import com.lagradost.shiro.R
import com.lagradost.shiro.ui.GlideApp
import com.lagradost.shiro.ui.toPx
import com.lagradost.shiro.utils.AppUtils.getCurrentActivity
import com.lagradost.shiro.utils.AppUtils.loadPage
import com.lagradost.shiro.utils.AppUtils.settingsManager
import com.lagradost.shiro.utils.MALApi
import com.lagradost.shiro.utils.MALApi.Companion.convertToStatus
import kotlinx.android.synthetic.main.list_card_compact.view.*
import java.util.*
import kotlin.math.ceil
import kotlin.math.sqrt

class LibraryCardAdapter(val context: Context, var list: Array<MALApi.Companion.Data>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return LibraryCardViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.list_card_compact, parent, false),
            context
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is LibraryCardViewHolder -> {
                holder.bind(list[position])
            }
        }
    }

    override fun getItemCount(): Int {
        return list.size
    }

    class LibraryCardViewHolder
    constructor(itemView: View, val context: Context) : RecyclerView.ViewHolder(itemView) {
        fun bind(item: MALApi.Companion.Data) {
            val coverHeight: Int = (settingsManager?.getInt("library_view_height", 80) ?: 80).toPx

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
            itemView.imageView.apply {
                layoutParams = FrameLayout.LayoutParams(
                    ceil(coverHeight / sqrt(2.0)).toInt(),
                    coverHeight
                )
            }
            val marginParams = FrameLayout.LayoutParams(
                LinearLayoutCompat.LayoutParams.MATCH_PARENT, // view width
                LinearLayoutCompat.LayoutParams.MATCH_PARENT, // view height
            )
            marginParams.setMargins(ceil(coverHeight / sqrt(2.0)).toInt(), 0, 0, 0)
            itemView.text_holder.layoutParams = marginParams

            itemView.imageText?.text = item.node.title
            itemView.imageSubText?.visibility = VISIBLE

            val statusColor = when (convertToStatus(item.node.my_list_status.status)) {
                MALApi.Companion.MalStatusType.Watching -> R.color.colorWatching
                MALApi.Companion.MalStatusType.Completed -> R.color.colorCompleted
                MALApi.Companion.MalStatusType.OnHold -> R.color.colorOnHold
                MALApi.Companion.MalStatusType.Dropped -> R.color.colorDropped
                MALApi.Companion.MalStatusType.PlanToWatch -> R.color.colorPlanToWatch
                else -> R.color.colorWatching
            }

            itemView.episode_progress?.max = item.node.num_episodes
            itemView.episode_progress?.progress = item.node.my_list_status.num_episodes_watched
            /*episode_progress?.progressDrawable?.setColorFilter(
                ContextCompat.getColor(context, statusColor), android.graphics.PorterDuff.Mode.SRC_IN
            )*/
            itemView.episode_progress?.progressTintList =
                ColorStateList.valueOf(ContextCompat.getColor(context, statusColor))

            val scoreText = if (item.node.my_list_status.score != 0) "★" + item.node.my_list_status.score else ""
            val seasonText = item.node.start_season?.let {
                "${
                    it.season.replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(
                            Locale.getDefault()
                        ) else it.toString()
                    }
                } ${it.year}"
            } ?: ""
            val episodeText =
                "${item.node.my_list_status.num_episodes_watched}/${if (item.node.num_episodes != 0) item.node.num_episodes else "???"}"

            itemView.imageTextSecond?.text = episodeText
            itemView.imageSubText?.text = scoreText
            itemView.imageSubTextSecond?.text = seasonText
            itemView.setOnClickListener {
                getCurrentActivity()?.loadPage(item.node.id.toString(), item.node.title, true)
            }

            itemView.imageView.setOnClickListener {
                getCurrentActivity()?.loadPage(item.node.id.toString(), item.node.title, true)
                //activity?.loadPage(card.slug, card.name)
                /*MainActivity.loadPage(card)*/
            }

            val glideUrl =
                GlideUrl(item.node.main_picture.medium)
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
}