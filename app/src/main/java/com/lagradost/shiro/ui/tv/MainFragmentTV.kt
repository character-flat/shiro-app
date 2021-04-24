package com.lagradost.shiro.ui.tv

import android.annotation.SuppressLint
import java.util.Timer
import java.util.TimerTask

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import androidx.leanback.app.BackgroundManager
import androidx.leanback.app.BrowseFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.core.content.ContextCompat
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.leanback.app.BrowseSupportFragment

import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.lagradost.shiro.R
import com.lagradost.shiro.utils.ShiroApi
import com.lagradost.shiro.ui.result.ResultFragment
import com.lagradost.shiro.utils.AppApi.getColorFromAttr
import com.lagradost.shiro.utils.ShiroApi.Companion.requestHome

/**
 * Loads a grid of cards with movies to browse.
 */
class MainFragmentTV : BrowseSupportFragment() {

    private val mHandler = Handler()
    private lateinit var mBackgroundManager: BackgroundManager
    private var mDefaultBackground: Drawable? = null
    private lateinit var mMetrics: DisplayMetrics
    private var mBackgroundTimer: Timer? = null
    private var mBackgroundUri: String? = null
    private val MOVIE_CATEGORY = arrayOf(
        "Favorites",
        "Trending",
        "Recently Updated",
        "Ongoing",
        "Latest anime"
    )

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        Log.i(TAG, "onCreate")
        super.onActivityCreated(savedInstanceState)

        prepareBackgroundManager()

        setupUIElements()

        ShiroApi.onHomeFetched += ::loadRows

        requestHome(true)

        setupEventListeners()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: " + mBackgroundTimer?.toString())
        mBackgroundTimer?.cancel()
    }

    @SuppressLint("UseRequireInsteadOfGet")
    private fun prepareBackgroundManager() {
        mBackgroundManager = BackgroundManager.getInstance(activity)
        //mBackgroundManager.attach(activity!!.window)
        mDefaultBackground = ContextCompat.getDrawable(requireActivity(), R.drawable.default_background)
        mMetrics = DisplayMetrics()
        requireActivity().windowManager.defaultDisplay.getMetrics(mMetrics)
    }

    private fun setupUIElements() {
        title = getString(R.string.browse_title)
        // over title
        headersState = BrowseFragment.HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true

        // set fastLane (or headers) background color
        brandColor = requireActivity().getColorFromAttr(R.attr.colorPrimaryDarker)
        // set search icon color
        searchAffordanceColor = requireActivity().getColorFromAttr(R.attr.colorPrimaryDark)
    }

    private fun loadRows(list: ShiroApi.ShiroHomePage?) {

        val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        val cardPresenter = CardPresenter()

        for (i in 0 until NUM_ROWS) {
            /*if (i != 0) {
                Collections.shuffle(list)
            }*/
            val data = when (i) {
                /*0 -> {
                    list?.recentlySeen?.map { it?.id }
                }*/
                0 -> {
                    list?.favorites
                }
                1 -> {
                    list?.data?.trending_animes
                }
                2 -> {
                    list?.data?.latest_episodes?.map { it.anime }
                }
                3 -> {
                    list?.data?.ongoing_animes
                }
                4 -> {
                    list?.data?.latest_animes
                }
                else -> listOf()
            }
            val listRowAdapter = ArrayObjectAdapter(cardPresenter)
            /*for (j in 0 until NUM_COLS) {
                listRowAdapter.add(data[j % 5])
            }*/
            data?.forEach {
                listRowAdapter.add(it as ShiroApi.CommonAnimePage)
            }
            val header = HeaderItem(i.toLong(), MOVIE_CATEGORY[i])
            rowsAdapter.add(ListRow(header, listRowAdapter))
        }

        /*val gridHeader = HeaderItem(NUM_ROWS.toLong(), "PREFERENCES")

        val mGridPresenter = GridItemPresenter()
        val gridRowAdapter = ArrayObjectAdapter(mGridPresenter)
        gridRowAdapter.add(activity?.resources?.getString(R.string.grid_view) ?: "STRING1")
        gridRowAdapter.add(activity?.getString(R.string.error_fragment))
        gridRowAdapter.add(activity?.resources?.getString(R.string.personal_settings) ?: "SQTring2")
        rowsAdapter.add(ListRow(gridHeader, gridRowAdapter))*/

        activity?.runOnUiThread {
            adapter = rowsAdapter
        }
    }

    private fun setupEventListeners() {
        setOnSearchClickedListener {
            activity?.supportFragmentManager?.beginTransaction()
                ?.setCustomAnimations(R.anim.enter, R.anim.exit, R.anim.pop_enter, R.anim.pop_exit)
                ?.replace(R.id.main_browse_fragment, MySearchFragment())
                ?.commit()
        }
        onItemViewClickedListener = ItemViewClickedListener()
        onItemViewSelectedListener = ItemViewSelectedListener()
    }

    inner class ItemViewClickedListener : OnItemViewClickedListener {
        override fun onItemClicked(
            itemViewHolder: Presenter.ViewHolder,
            item: Any,
            rowViewHolder: RowPresenter.ViewHolder,
            row: Row
        ) {
            if (item is ShiroApi.CommonAnimePage) {
                Log.d(TAG, "Item: $item")
                activity?.supportFragmentManager?.beginTransaction()
                    ?.setCustomAnimations(R.anim.enter, R.anim.exit, R.anim.pop_enter, R.anim.pop_exit)
                    ?.add(R.id.main_browse_fragment, ResultFragment.newInstance(item.slug))
                    ?.addToBackStack("ResultFragment")
                    ?.commitAllowingStateLoss()
                /*val intent = Intent(activity, DetailsActivityTV::class.java)
                    intent.putExtra(DetailsActivityTV.MOVIE, mapper.writeValueAsString(item))

                    val bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                        requireActivity(),
                        (itemViewHolder.view as ImageCardView).mainImageView,
                        DetailsActivityTV.SHARED_ELEMENT_NAME
                    )
                        .toBundle()
                    requireActivity().startActivity(intent, bundle)*/

            } else if (item is String) {
                if (item.contains(getString(R.string.error_fragment))) {
                    val intent = Intent(activity, BrowseErrorActivity::class.java)
                    startActivity(intent)
                } else {
                    Toast.makeText(activity, item, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private inner class ItemViewSelectedListener : OnItemViewSelectedListener {
        override fun onItemSelected(
            itemViewHolder: Presenter.ViewHolder?, item: Any?,
            rowViewHolder: RowPresenter.ViewHolder, row: Row
        ) {
            /*if (item is Movie) {
                mBackgroundUri = item.backgroundImageUrl
                startBackgroundTimer()
            }*/
        }
    }

    private fun updateBackground(uri: String?) {
        val width = mMetrics.widthPixels
        val height = mMetrics.heightPixels
        Glide.with(requireActivity())
            .load(uri)
            .centerCrop()
            .error(mDefaultBackground)
            .into<CustomTarget<Drawable>>(
                object : CustomTarget<Drawable>(width, height) {
                    override fun onLoadCleared(placeholder: Drawable?) {

                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        transition: com.bumptech.glide.request.transition.Transition<in Drawable>?
                    ) {
                        mBackgroundManager.drawable = resource
                    }
                })
        mBackgroundTimer?.cancel()

    }

    private fun startBackgroundTimer() {
        mBackgroundTimer?.cancel()
        mBackgroundTimer = Timer()
        mBackgroundTimer?.schedule(UpdateBackgroundTask(), BACKGROUND_UPDATE_DELAY.toLong())
    }

    private inner class UpdateBackgroundTask : TimerTask() {

        override fun run() {
            mHandler.post { updateBackground(mBackgroundUri) }
        }
    }

    private inner class GridItemPresenter : Presenter() {
        override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
            val view = TextView(parent.context)
            view.layoutParams = ViewGroup.LayoutParams(GRID_ITEM_WIDTH, GRID_ITEM_HEIGHT)
            view.isFocusable = true
            view.isFocusableInTouchMode = true
            view.setBackgroundColor(requireActivity().getColorFromAttr(R.attr.background))
            view.setTextColor(Color.WHITE)
            view.gravity = Gravity.CENTER
            return ViewHolder(view)
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
            (viewHolder.view as TextView).text = item as String
        }

        override fun onUnbindViewHolder(viewHolder: ViewHolder) {}
    }

    companion object {
        private const val TAG = "MainFragmentTV"

        private const val BACKGROUND_UPDATE_DELAY = 300
        private const val GRID_ITEM_WIDTH = 200
        private const val GRID_ITEM_HEIGHT = 200
        private const val NUM_ROWS = 5
    }
}