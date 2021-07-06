package com.lagradost.shiro.ui.result

import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import com.google.common.math.IntMath.mod
import com.jaredrummler.cyanea.Cyanea
import com.lagradost.shiro.R
import com.lagradost.shiro.utils.AppUtils.dubbify
import com.lagradost.shiro.utils.AppUtils.getTextColor
import com.lagradost.shiro.utils.AppUtils.getViewKey
import com.lagradost.shiro.utils.DataStore
import com.lagradost.shiro.utils.ShiroApi
import com.lagradost.shiro.utils.VIEWSTATE_KEY
import kotlinx.android.synthetic.main.episode_expander.view.*

class MasterEpisodeAdapter(
    val activity: FragmentActivity,
    var data: ShiroApi.AnimePageData,
    var isFiller: HashMap<Int, Boolean>? = null,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    val episodes = data.episodes!!

    data class MasterEpisode(
        val start: Int,
        val end: Int,
        var visible: Boolean = false,
    )

    var items = generateItems(episodes, data.slug)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return MasterEpisodeViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.episode_expander, parent, false),
            activity,
            isFiller
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is MasterEpisodeViewHolder -> {
                holder.bind(items[position], position, data)
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
        val activity: FragmentActivity,
        private val fillerList: HashMap<Int, Boolean>? = null,
    ) :
        RecyclerView.ViewHolder(itemView) {
        fun bind(
            item: MasterEpisode,
            position: Int,
            data: ShiroApi.AnimePageData,
        ) {
            val episodeOffset = if (data.episodes?.filter { it.episode_number == 0 }.isNullOrEmpty()) 0 else -1

            //println("BIND $position" + "|" + (fillerList?.size ?: "NULLL"))
            itemView.cardTitle.text =
                if (item.start + 1 == item.end) "Episode ${item.end + episodeOffset}"
                else "Episodes ${item.start + 1 + episodeOffset} - ${item.end + episodeOffset}"

            val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder> = EpisodeAdapter(
                activity,
                data,
                itemView,
                position,
                item.start,
                item.end,
                fillerList,
            )
            itemView.episodes_res_view.adapter = adapter
            (itemView.episodes_res_view.adapter as EpisodeAdapter).notifyDataSetChanged()

            //val transition: Transition = ChangeTransform()
            //transition.duration = 3000
            //TransitionManager.beginDelayedTransition(itemView.cardBg, transition)

            itemView.expand_icon.rotation = if (item.visible) 90f else 0f
            itemView.episodes_res_view.visibility = if (item.visible) VISIBLE else GONE

            var isSeen = false
            for (episode in item.start..item.end) {
                val key = getViewKey(data.slug.dubbify(false), episode)
                val keyDubbed = getViewKey(data.slug.dubbify(true), episode)
                if (DataStore.containsKey(VIEWSTATE_KEY, key) || DataStore.containsKey(VIEWSTATE_KEY, keyDubbed)) {
                    isSeen = true
                }
            }

            if (isSeen) {
                activity.let {
                    itemView.cardBg.setCardBackgroundColor(
                        Cyanea.instance.primaryDark
                    )
                    itemView.expand_icon.setColorFilter(
                        ContextCompat.getColor(it, R.color.textColor)
                    )
                    itemView.cardTitle.setTextColor(
                        ContextCompat.getColor(it, R.color.textColor)
                    )
                }
            } else {
                activity.let {
                    itemView.cardBg.setCardBackgroundColor(
                        Cyanea.instance.backgroundColorDark
                    )
                    itemView.expand_icon.setColorFilter(
                        activity.getTextColor()
                    )
                    itemView.cardTitle.setTextColor(
                        activity.getTextColor()
                    )
                }
            }
        }
    }

}

fun generateItems(
    episodes: List<ShiroApi.ShiroEpisodes>,
    slug: String
): MutableList<MasterEpisodeAdapter.MasterEpisode> {
    val stepSize = 20 //settingsManager!!.getInt("episode_group_size", 50)
    val items = mutableListOf<MasterEpisodeAdapter.MasterEpisode>()

    /*
    if (stepSize == 0) {
        return items.apply {
            this.add(MasterEpisodeAdapter.MasterEpisode(0, episodes.size, isSeen = false, visible = true))
        }
    }*/

    for (i in episodes.indices step stepSize) {
        if (i + stepSize < episodes.size) {
            items.add(
                MasterEpisodeAdapter.MasterEpisode(i, i + stepSize)
            )
        }
    }
    // To account for the loop skipping stuff
    // Double mod to turn 0 -> stepSize
    val overflow = mod(mod(episodes.size, stepSize) - 1, stepSize) + 1
    // Dunno if != 0 is possible, but might as well keep for security
    if (overflow != 0) {
        var isSeen = false
        for (episode in episodes.size - overflow..episodes.size) {
            val key = getViewKey(slug, episode)
            if (DataStore.containsKey(VIEWSTATE_KEY, key)) {
                isSeen = true
            }
        }
        items.add(
            MasterEpisodeAdapter.MasterEpisode(episodes.size - overflow, episodes.size, isSeen)
        )
    }
    if (items.size == 1) {
        items[0].visible = true
    }
    return items
}
