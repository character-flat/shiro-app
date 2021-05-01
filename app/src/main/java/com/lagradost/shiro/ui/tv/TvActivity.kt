package com.lagradost.shiro.ui.tv

import android.content.Intent
import android.os.Bundle
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.ChangeBounds
import androidx.transition.Transition
import com.lagradost.shiro.utils.DataStore
import com.lagradost.shiro.R
import com.lagradost.shiro.ui.LastEpisodeInfo
import com.lagradost.shiro.ui.MainActivity
import com.lagradost.shiro.ui.home.CardAdapter
import com.lagradost.shiro.ui.home.CardContinueAdapter
import com.lagradost.shiro.ui.home.ExpandedHomeFragment
import com.lagradost.shiro.ui.home.MasterCardAdapter
import com.lagradost.shiro.ui.result.ResultFragment.Companion.isInResults
import com.lagradost.shiro.utils.AppApi.displayCardData
import com.lagradost.shiro.utils.AppApi.init
import com.lagradost.shiro.utils.AppApi.isTv
import com.lagradost.shiro.utils.DownloadManager
import com.lagradost.shiro.utils.InAppUpdater.runAutoUpdate
import com.lagradost.shiro.utils.ShiroApi
import kotlinx.android.synthetic.main.activity_tv_custom.*
import kotlinx.android.synthetic.main.fragment_home.*
import kotlin.concurrent.thread

/**
 * Loads [MainFragmentTV].
 */
class TvActivity : FragmentActivity() {
    companion object {
        var tvActivity: FragmentActivity? = null
        var isInSearch = false
    }

    override fun onBackPressed() {
        if (isInSearch && !isInResults) {
            this.supportFragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.enter, R.anim.exit, R.anim.pop_enter, R.anim.pop_exit)
                .replace(R.id.main_browse_fragment, MainFragmentTV())
                .commit()
        } else {
            super.onBackPressed()
        }
    }

    private fun homeLoaded(data: ShiroApi.ShiroHomePage?) {
        this.runOnUiThread {

            /*trending_anime_scroll_view.removeAllViews()
            recentlySeenScrollView.removeAllViews()
            recently_updated_scroll_view.removeAllViews()
            favouriteScrollView.removeAllViews()
            scheduleScrollView.removeAllViews()
*/
            //val cardInfo = data?.homeSlidesData?.shuffled()?.take(1)?.get(0)
            /*val glideUrl = GlideUrl("https://fastani.net/" + cardInfo?.bannerImage) { FastAniApi.currentHeaders }
            context?.let {
                GlideApp.with(it)
                    .load(glideUrl)
                    .into(main_backgroundImage)
            }*/


            //"http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"

            /*main_poster.setOnClickListener {
                MainActivity.loadPage(cardInfo!!)
                // MainActivity.loadPlayer(0, 0, cardInfo!!)
            }*/

            println("DATA")
            val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder> = MasterCardAdapter(
                this,
                data,
            )
            vertical_grid_view.adapter = adapter
            (vertical_grid_view.adapter as MasterCardAdapter).data = data
            (vertical_grid_view.adapter as MasterCardAdapter).notifyDataSetChanged()

            /*if (data != null) {
                displayCardData(data.data.trending_animes, trending_anime_scroll_view, trending_text, main_scroll_view)
                displayCardData(
                    data.data.latest_episodes.map { it.anime },
                    recently_updated_scroll_view,
                    recently_updated_text
                )
                displayCardData(
                    data.data.ongoing_animes,
                    ongoing_anime_scroll_view,
                    ongoing_anime_text,
                    main_scroll_view
                )
                displayCardData(data.data.latest_animes, latest_anime_scroll_view, latest_anime_text, main_scroll_view)
            }
            //displayCardData(data?.recentlyAddedData, recentScrollView)

            // RELOAD ON NEW FAV!
            if (data?.favorites?.isNotEmpty() == true) {
                favouriteRoot.visibility = VISIBLE
                //println(data.favorites!!.map { it?.title?.english})
                displayCardData(data.favorites, favouriteScrollView, favorites_text, main_scroll_view)
            } else {
                favouriteRoot.visibility = GONE
            }

            /*
            if (data?.schedule?.isNotEmpty() == true) {
                scheduleRoot.visibility = VISIBLE
                //println(data.favorites!!.map { it?.title?.english})
                displayCardData(data.schedule, scheduleScrollView)
            } else {
                scheduleRoot.visibility = GONE
            }

*/
            val transition: Transition = ChangeBounds()
            transition.duration = 100
            if (data?.recentlySeen?.isNotEmpty() == true) {
                recentlySeenRoot.visibility = VISIBLE
                //println(data.recentlySeen)
                displayCardData(data.recentlySeen, recentlySeenScrollView, main_scroll_view)
            } else {
                recentlySeenRoot.visibility = GONE
            }
/*            TransitionManager.beginDelayedTransition(main_scroll, transition)
            main_load.alpha = 0f
            main_scroll.alpha = 1f*/

            // This somehow crashes, hope this null check helps ¯\_(ツ)_/¯*/
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        /*if (!isTv()) {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
            return
        }*/
        // ------ Init -----
        tvActivity = this
        DataStore.init(this)
        DownloadManager.init(this)
        init()
        thread {
            ShiroApi.init()
        }
        thread {
            runAutoUpdate(this)
        }
        // ----- Theme -----
        theme.applyStyle(R.style.AppTheme, true)
        theme.applyStyle(R.style.Theme_LeanbackCustom, true)
        // -----------------
        ShiroApi.onHomeFetched += ::homeLoaded
        setContentView(R.layout.activity_tv_custom)

    }

    override fun onResume() {
        super.onResume()
        // This is needed to avoid NPE crash due to missing context
        DataStore.init(this)
        DownloadManager.init(this)
        init()

    }
}