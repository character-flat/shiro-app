package com.lagradost.shiro.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.shiro.*
import com.lagradost.shiro.ui.BookmarkedTitle
import com.lagradost.shiro.ui.LastEpisodeInfo
import com.lagradost.shiro.utils.AppApi.displayCardData
import com.lagradost.shiro.utils.ShiroApi
import kotlinx.android.synthetic.main.home_card.view.*
import kotlinx.android.synthetic.main.vertical_grid_view_child.view.*


class MasterCardAdapter(
    context: FragmentActivity,
    animeList: ShiroApi.ShiroHomePage?,
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var data = animeList
    var activity: FragmentActivity? = context
    private val filtered = arrayOf(
        Pair(data?.recentlySeen, activity?.getString(R.string.continue_watching)),
        Pair(data?.favorites, activity?.getString(R.string.favorites)),
        Pair(data?.data?.trending_animes, activity?.getString(R.string.trending_anime)),
        Pair(data?.data?.latest_episodes?.map { it.anime }, activity?.getString(R.string.home_recently_updated)),
        Pair(data?.data?.ongoing_animes, activity?.getString(R.string.home_ongoing)),
        Pair(data?.data?.latest_animes, activity?.getString(R.string.latest_anime))
    ).filter {
        it.first != null && it.first?.isNotEmpty() == true
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return MasterCardViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.vertical_grid_view_child, parent, false),
            activity!!
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is MasterCardViewHolder -> {
                holder.bind(filtered[position])
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        holder.itemView.horizontalGridView.adapter = null
        holder.itemView.visibility = GONE
        super.onViewRecycled(holder)
    }

    override fun getItemCount(): Int {
        return filtered.size
    }

    class MasterCardViewHolder(itemView: View, _context: FragmentActivity) : RecyclerView.ViewHolder(itemView) {
        val activity = _context
        val card: View = itemView

        fun bind(pair: Pair<List<Any?>?, String?>) {
            card.expand_text.text = pair.second
            card.visibility = VISIBLE
            if (pair.first as? List<ShiroApi.CommonAnimePage?> != null) {
                activity.displayCardData(
                    pair.first as List<ShiroApi.CommonAnimePage?>?,
                    card.horizontalGridView,
                    card.expand_text
                )
            } else if (pair.first as? List<LastEpisodeInfo?> != null) {
                activity.displayCardData(
                    pair.first as? List<LastEpisodeInfo>,
                    card.horizontalGridView,
                )
            }
        }
    }
}
