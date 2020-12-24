package com.example.fastani.ui.home

import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.view.*
import android.widget.AbsListView
import android.widget.ScrollView
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import com.bumptech.glide.load.model.GlideUrl
import com.example.fastani.*
import com.example.fastani.FastAniApi.Companion.requestHome
import com.example.fastani.ui.GlideApp
import com.example.fastani.ui.dashboard.toPx
import kotlinx.android.synthetic.main.fragment_home.*
import kotlinx.android.synthetic.main.home_card.view.*
import kotlin.concurrent.thread

const val MAXIMUM_FADE = 0.3f
const val FADE_SCROLL_DISTANCE = 700f

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

    private fun homeLoaded(data: FastAniApi.HomePageResponse?) {
        activity?.runOnUiThread {
            trendingScrollView.removeAllViews()
            recentScrollView.removeAllViews()

            val cardInfo = data?.homeSlidesData?.get(0)
            val glideUrl = GlideUrl("https://fastani.net/" + cardInfo?.bannerImage) { FastAniApi.currentHeaders }
            context?.let {
                GlideApp.with(it)
                    .load(glideUrl)
                    .into(main_backgroundImage)
            }

            val glideUrlMain =
                GlideUrl("https://fastani.net/" + cardInfo?.coverImage?.large) { FastAniApi.currentHeaders }
            context?.let {
                GlideApp.with(it)
                    .load(glideUrlMain)
                    .into(main_poster)
            }

            main_name.text = cardInfo?.title?.english
            main_genres.text = cardInfo?.genres?.joinToString(prefix = "", postfix = "", separator = " • ")

            data?.trendingData?.forEach { cardInfo ->
                val card: View = layoutInflater.inflate(R.layout.home_card, null)
                val glideUrl =
                    GlideUrl("https://fastani.net/" + cardInfo.coverImage.large) { FastAniApi.currentHeaders }
                activity?.runOnUiThread {
                    context?.let {
                        GlideApp.with(it)
                            .load(glideUrl)
                            .into(card.imageView)
                    }
                    card.imageView.setOnLongClickListener {
                        Toast.makeText(context, cardInfo.title.english, Toast.LENGTH_SHORT).show()
                        return@setOnLongClickListener true
                    }
                    trendingScrollView.addView(card)
                }
            }
            data?.recentlyAddedData?.forEach { cardInfo ->
                val card: View = layoutInflater.inflate(R.layout.home_card, null)
                val glideUrl =
                    GlideUrl("https://fastani.net/" + cardInfo.coverImage.large) { FastAniApi.currentHeaders }
                activity?.runOnUiThread {
                    context?.let {
                        GlideApp.with(it)
                            .load(glideUrl)
                            .into(card.imageView)
                    }
                    card.imageView.setOnLongClickListener {
                        Toast.makeText(context, cardInfo.title.english, Toast.LENGTH_SHORT).show()
                        return@setOnLongClickListener true
                    }
                    recentScrollView.addView(card)
                }
            }
        }
    }

    override fun onDestroy() {
        FastAniApi.onHomeFetched -= ::homeLoaded;
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        main_name.setPadding(0,MainActivity.statusHeight,0,0)
                /*
                activity?.getWindow()?.setFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS, WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

                val rectangle = Rect()
                val window: Window? = activity?.getWindow()
                window?.getDecorView()?.getWindowVisibleDisplayFrame(rectangle)
                val statusBarHeight: Int = rectangle.top
                val contentViewTop: Int = window.findViewById(Window.ID_ANDROID_CONTENT).getTop()
                val titleBarHeight = contentViewTop - statusBarHeight

               */
        main_scroll.setOnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
            val fade = (FADE_SCROLL_DISTANCE - scrollY)/FADE_SCROLL_DISTANCE
            val gray : Int = Color.argb(fade,0f,fade,0f)
            main_backgroundImage.alpha = maxOf(0f, MAXIMUM_FADE * fade)
        }
        homeViewModel.apiData.observe(viewLifecycleOwner) {
            homeLoaded(it)
        }
        /*FastAniApi.onHomeFetched += ::homeLoaded;
        thread {
            // NOTE THAT THIS WILL RESULT IN NOTHING ON FIRST LOAD BECAUSE TOKEN IS NOT LAODED
            requestHome(true)
        }*/
    }
}
