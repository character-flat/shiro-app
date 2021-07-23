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
import kotlinx.android.synthetic.main.list_card_compact.view.*
import java.util.*
import kotlin.math.ceil
import kotlin.math.sqrt


enum class LibraryStatusType(var value: Int) {
    Watching(0),
    Completed(1),
    Paused(2),
    Dropped(3),
    Planning(4),
    Rewatching(5),
    None(-1)
}

data class LibraryObject(
    val title: String,
    val poster: String,
    val id: String,
    val score: Int,
    val progress: Int,
    val episodes: Int,
    val season: String?,
    val year: Int?,
    val status: Int,
    val nextEpisode: String?,
)

class LibraryCardAdapter(val context: Context, var list: List<LibraryObject>) :
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
        fun bind(item: LibraryObject) {
            val coverHeight: Int = (settingsManager?.getInt("library_view_height", 80) ?: 80).toPx
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
            itemView.backgroundCard.backgroundTintList = ColorStateList.valueOf(
                Cyanea.instance.backgroundColorDark
            )

            itemView.backgroundCard.setOnClickListener {
                getCurrentActivity()?.loadPage(item.id, item.title, true)
            }

            itemView.imageText?.text = item.title
            itemView.imageSubText?.visibility = VISIBLE

            val statusColor = when (item.status) {
                LibraryStatusType.Watching.value -> R.color.colorWatching
                LibraryStatusType.Completed.value -> R.color.colorCompleted
                LibraryStatusType.Paused.value -> R.color.colorOnHold
                LibraryStatusType.Dropped.value -> R.color.colorDropped
                LibraryStatusType.Planning.value -> R.color.colorPlanToWatch
                else -> R.color.colorWatching
            }

            itemView.episode_progress?.max = item.episodes
            itemView.episode_progress?.progress = item.progress
            /*episode_progress?.progressDrawable?.setColorFilter(
                ContextCompat.getColor(context, statusColor), android.graphics.PorterDuff.Mode.SRC_IN
            )*/
            itemView.episode_progress?.progressTintList =
                ColorStateList.valueOf(ContextCompat.getColor(context, statusColor))

            val scoreText = if (item.score != 0) "★ " + item.score else null
            val separator = if (scoreText != null && item.nextEpisode != null) " - " else ""

            val seasonText = (item.season?.let {
                it.lowercase().replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(
                        Locale.getDefault()
                    ) else it.toString()
                }
            } ?: "") + " " + (item.year ?: "")
            val episodeText =
                "${item.progress}/${if (item.episodes != 0) item.episodes else "???"}"

            itemView.imageTextSecond?.text = episodeText
            itemView.imageSubText?.text = "${scoreText ?: ""}$separator${item.nextEpisode ?: ""}"
            itemView.imageSubTextSecond?.text = seasonText
            itemView.setOnClickListener {
                getCurrentActivity()?.loadPage(item.id, item.title, true)
            }

            itemView.imageView.setOnClickListener {
                getCurrentActivity()?.loadPage(item.id, item.title, true)
                //activity?.loadPage(card.slug, card.name)
                /*MainActivity.loadPage(card)*/
            }

            val glideUrl =
                GlideUrl(item.poster)
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