package com.lagradost.shiro.ui.home

import android.os.Bundle
import android.view.*
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.view.setMargins
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.transition.ChangeBounds
import androidx.transition.Transition
import androidx.transition.TransitionManager
import com.bumptech.glide.load.model.GlideUrl
import com.lagradost.shiro.*
import com.lagradost.shiro.utils.ShiroApi.Companion.cachedHome
import com.lagradost.shiro.utils.ShiroApi.Companion.getAnimePage
import com.lagradost.shiro.utils.ShiroApi.Companion.getFullUrlCdn
import com.lagradost.shiro.utils.ShiroApi.Companion.getRandomAnimePage
import com.lagradost.shiro.utils.ShiroApi.Companion.requestHome
import com.lagradost.shiro.ui.*
import com.lagradost.shiro.utils.AppUtils.displayCardData
import com.lagradost.shiro.utils.AppUtils.getNextEpisode
import com.lagradost.shiro.utils.AppUtils.loadPage
import com.lagradost.shiro.utils.AppUtils.loadPlayer
import com.lagradost.shiro.utils.AppUtils.settingsManager
import com.lagradost.shiro.utils.ShiroApi
import kotlinx.android.synthetic.main.download_card.*
import kotlinx.android.synthetic.main.fragment_home.*
import kotlin.concurrent.thread

//const val MAXIMUM_FADE = 0.3f
//const val FADE_SCROLL_DISTANCE = 700f

class HomeFragment : Fragment() {

    private lateinit var homeViewModel: HomeViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        homeViewModel =
            activity?.let { ViewModelProviders.of(it).get(HomeViewModel::class.java) }!!

        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    private fun homeLoaded(data: ShiroApi.ShiroHomePage?) {
        activity?.runOnUiThread {

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
            home_swipe_refresh.setOnRefreshListener {
                generateRandom()
                home_swipe_refresh.isRefreshing = false
            }

            generateRandom(data?.random)



            if (data != null) {
                activity?.displayCardData(data.data.trending_animes, trending_anime_scroll_view, trending_text)
                activity?.displayCardData(
                    data.data.latest_episodes.map { it.anime } as? List<ShiroApi.CommonAnimePage>,
                    recently_updated_scroll_view,
                    recently_updated_text
                )
                activity?.displayCardData(data.data.ongoing_animes, ongoing_anime_scroll_view, ongoing_anime_text)
                activity?.displayCardData(data.data.latest_animes, latest_anime_scroll_view, latest_anime_text)
            }
            //displayCardData(data?.recentlyAddedData, recentScrollView)

            // RELOAD ON NEW FAV!
            if (data?.favorites?.isNotEmpty() == true) {
                favouriteRoot.visibility = VISIBLE
                //println(data.favorites!!.map { it?.title?.english})
                activity?.displayCardData(
                    data.favorites?.sortedWith(compareBy { it?.name })?.toList(),
                    favouriteScrollView,
                    favorites_text,
                    overrideHideDubbed = true
                )
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
                activity?.displayCardData(data.recentlySeen, recentlySeenScrollView)
            } else {
                recentlySeenRoot.visibility = GONE
            }
            TransitionManager.beginDelayedTransition(main_scroll, transition)
            main_load.alpha = 0f
            main_scroll.alpha = 1f

            // This somehow crashes, hope this null check helps ¯\_(ツ)_/¯
            if (main_reload_data_btt != null) {
                main_reload_data_btt.alpha = 0f
                main_reload_data_btt.isClickable = false
            }
            main_layout.setPadding(0, MainActivity.statusHeight, 0, 0)
        }
    }

    private fun generateRandom(randomPage: ShiroApi.AnimePage? = null) {
        thread {
            val hideDubbed = settingsManager!!.getBoolean("hide_dubbed", false)
            val random: ShiroApi.AnimePage? = randomPage ?: getRandomAnimePage()
            cachedHome?.random = random
            val randomData = random?.data
            // Hack, assuming all dubbed shows has a normal equivalent
            if (hideDubbed && randomData != null) {
                randomData.slug = randomData.slug.removeSuffix("-dubbed")
                randomData.name = randomData.name.removeSuffix("Dubbed")
            }
            activity?.runOnUiThread {
                try {
                    if (randomData != null) {
                        // This can throw NPE as main_layout isn't guaranteed to be inflated
                        val transition: Transition = ChangeBounds()
                        transition.duration = 100 // DURATION OF ANIMATION IN MS
                        TransitionManager.beginDelayedTransition(main_layout, transition)
                        main_poster_holder.visibility = VISIBLE
                        main_poster_text_holder.visibility = VISIBLE
                        val marginParams: FrameLayout.LayoutParams = FrameLayout.LayoutParams(
                            LinearLayoutCompat.LayoutParams.MATCH_PARENT, // view width
                            LinearLayoutCompat.LayoutParams.WRAP_CONTENT, // view height
                        )

                        marginParams.setMargins(0, 250.toPx, 0, 0)
                        main_layout.layoutParams = marginParams

                        val glideUrlMain =
                            GlideUrl(getFullUrlCdn(randomData.image)) { ShiroApi.currentHeaders }
                        context?.let {
                            GlideApp.with(it)
                                .load(glideUrlMain)
                                .into(main_poster)
                        }

                        main_name.text = randomData.name
                        main_genres.text =
                            randomData.genres?.joinToString(prefix = "", postfix = "", separator = " • ")
                        main_watch_button.setOnClickListener {
                            //MainActivity.loadPage(cardInfo!!)
                            Toast.makeText(activity, "Loading link", Toast.LENGTH_SHORT).show()
                            thread {
                                // LETTING USER PRESS STUFF WHEN THIS LOADS CAN CAUSE BUGS
                                val page = getAnimePage(randomData.slug)
                                if (page != null) {
                                    val nextEpisode = getNextEpisode(page.data)
                                    activity?.loadPlayer(nextEpisode.episodeIndex, 0L, page.data)
                                } else {
                                    activity?.runOnUiThread {
                                        Toast.makeText(activity, "Loading link failed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                        main_watch_button.setOnLongClickListener {
                            //MainActivity.loadPage(cardInfo!!)
                            if (cardInfo != null) {
                                val nextEpisode = getNextEpisode(randomData)
                                Toast.makeText(
                                    activity,
                                    "Episode ${nextEpisode.episodeIndex + 1}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            return@setOnLongClickListener true
                        }
                        main_info_button.setOnClickListener {
                            activity?.loadPage(randomData)
                        }
                    } else {
                        main_poster_holder.visibility = GONE
                        main_poster_text_holder.visibility = GONE
                        val marginParams: FrameLayout.LayoutParams = FrameLayout.LayoutParams(
                            LinearLayoutCompat.LayoutParams.MATCH_PARENT, // view width
                            LinearLayoutCompat.LayoutParams.WRAP_CONTENT, // view height
                        )

                        marginParams.setMargins(0)
                        main_layout.layoutParams = marginParams
                    }
                } catch (e: java.lang.NullPointerException) {
                    println("NPE in generateRandom!")
                }


            }
        }


    }

    private fun onHomeErrorCatch(fullRe: Boolean) {
        // Null check because somehow this can crash
        activity?.runOnUiThread {
            // ?. because it somehow crashes anyways without it for one person
            if (main_reload_data_btt != null) {
                main_reload_data_btt?.alpha = 1f
                main_load?.alpha = 0f
                main_reload_data_btt?.isClickable = true
                main_reload_data_btt?.setOnClickListener {
                    main_reload_data_btt?.alpha = 0f
                    main_load?.alpha = 1f
                    main_reload_data_btt?.isClickable = false
                    thread {
                        if (fullRe) {
                            ShiroApi.init()
                        } else {
                            requestHome(false)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        homeViewModel.apiData.let {
            it.observe(viewLifecycleOwner) { homePage ->
                homeLoaded(homePage)
            }
            if (it.value != null && main_load.alpha == 1.0f) {
                homeLoaded(it.value)
            }
        }
        super.onResume()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        main_scroll.alpha = 0f
        ShiroApi.onHomeError += ::onHomeErrorCatch
        if (ShiroApi.hasThrownError != -1) {
            onHomeErrorCatch(ShiroApi.hasThrownError == 1)
        }

        // This gets overwritten when data is loaded
        home_swipe_refresh.setOnRefreshListener {
            home_swipe_refresh.isRefreshing = false
        }

        // CAUSES CRASH ON 6.0.0
        /*main_scroll.setOnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
               val fade = (FADE_SCROLL_DISTANCE - scrollY) / FADE_SCROLL_DISTANCE
               // COLOR ARGB INTRODUCED IN 26!
               val gray: Int = Color.argb(fade, 0f, fade, 0f)
            //   main_backgroundImage.alpha = maxOf(0f, MAXIMUM_FADE * fade) // DONT DUE TO ALPHA FADING HINDERING FORGOUND GRADIENT
        }*/

    }
}
