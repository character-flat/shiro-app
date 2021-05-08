package com.lagradost.shiro.ui.result

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.common.math.IntMath.mod
import com.lagradost.shiro.R
import com.lagradost.shiro.utils.AppApi.settingsManager
import com.lagradost.shiro.utils.ShiroApi
import kotlinx.android.synthetic.main.episode_expander.view.*

class MasterEpisodeAdapter(
    val context: Context,
    val data: ShiroApi.AnimePageData,
    private val save: Boolean
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    val episodes = data.episodes!!

    data class MasterEpisode(
        val start: Int,
        val end: Int,
        var visible: Boolean = false,
    )

    private val stepSize = 20 //settingsManager!!.getInt("episode_group_size", 50)
    private val items = generateItems()

    private fun generateItems(): MutableList<MasterEpisode> {
        val items = mutableListOf<MasterEpisode>()

        // No groups
        if (stepSize == 0) {
            return items.apply {
                this.add(MasterEpisode(0, episodes.size, true))
            }
        }

        for (i in episodes.indices step stepSize) {
            if (i + stepSize < episodes.size) {
                items.add(
                    MasterEpisode(i, i + stepSize)
                )
            }
        }
        // To account for the loop skipping stuff
        // Double mod to turn 0 -> stepSize
        val overflow = mod(mod(episodes.size, stepSize) - 1, stepSize) + 1
        // Dunno if != 0 is possible, but might as well keep for security
        if (overflow != 0) {
            items.add(
                MasterEpisode(episodes.size - overflow, episodes.size)
            )
        }
        if (items.size == 1) {
            items[0].visible = true
        }
        return items
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return MasterEpisodeViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.episode_expander, parent, false),
            data
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is MasterEpisodeViewHolder -> {
                holder.bind(items[position], context, save)
            }
        }
        holder.itemView.setOnClickListener {
            items[position].visible = !items[position].visible
            notifyItemChanged(position)
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }

    class MasterEpisodeViewHolder
    constructor(
        itemView: View,
        val data: ShiroApi.AnimePageData
    ) :
        RecyclerView.ViewHolder(itemView) {
        fun bind(item: MasterEpisode, context: Context, save: Boolean) {
            println("Bind ${item.start}")
            itemView.cardTitle.text =
                if (item.start + 1 == item.end) "Episode ${item.end}"
                else "Episodes ${item.start + 1} - ${item.end}"

            val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder> = EpisodeAdapter(
                context,
                data,
                itemView.title_season_cards,
                save,
                item.start,
                item.end,
            )
            itemView.title_season_cards.adapter = adapter
            (itemView.title_season_cards.adapter as EpisodeAdapter).notifyDataSetChanged()
            itemView.title_season_cards.visibility = if (item.visible) VISIBLE else GONE
        }
    }

}