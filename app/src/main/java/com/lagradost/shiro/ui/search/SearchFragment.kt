package com.lagradost.shiro.ui.search

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.transition.ChangeBounds
import android.transition.Transition
import android.transition.TransitionManager
import android.view.*
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.appcompat.widget.SearchView
import androidx.core.view.setMargins
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.shiro.R
import com.lagradost.shiro.utils.ShiroApi
import com.lagradost.shiro.ui.MainActivity
import com.lagradost.shiro.ui.toPx
import com.lagradost.shiro.ui.result.ResultFragment.Companion.isInResults
import com.lagradost.shiro.utils.AppUtils.settingsManager
import kotlinx.android.synthetic.main.fragment_search.*
import kotlin.concurrent.thread

class SearchFragment : Fragment() {
    private lateinit var searchViewModel: SearchViewModel
    private val compactView = settingsManager!!.getBoolean("compact_search_enabled", true)
    private val spanCountLandscape = if (compactView) 2 else 6
    private val spanCountPortrait = if (compactView) 1 else 3

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        super.onViewCreated(view, savedInstanceState)
        if (!isInResults && this.isVisible) {
            activity?.window?.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
            )
        }
        val orientation = resources.configuration.orientation
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            cardSpace.spanCount = spanCountLandscape
        } else {
            cardSpace.spanCount = spanCountPortrait
        }

        val topParams: LinearLayout.LayoutParams = LinearLayout.LayoutParams(
            LinearLayoutCompat.LayoutParams.MATCH_PARENT, // view width
            MainActivity.statusHeight // view height
        )
        top_padding.layoutParams = topParams

        progress_bar.visibility = View.GONE
        val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>? = context?.let {
            ResAdapter(
                it,
                ArrayList(),
                cardSpace,
            )
        }
        cardSpace.adapter = adapter
        val hideDubbed = settingsManager!!.getBoolean("hide_dubbed", false)
        main_search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                progress_bar.visibility = View.VISIBLE
                (cardSpace.adapter as ResAdapter).cardList.clear()
                thread {
                    val data = ShiroApi.search(query)
                    activity?.runOnUiThread {
                        if (data == null) {
                            Toast.makeText(activity, "Server error", Toast.LENGTH_LONG).show()
                            progress_bar?.visibility = View.GONE
                        } else {
                            val filteredData =
                                if (hideDubbed) data.filter { !it.name.endsWith("Dubbed") } else data
                            progress_bar?.visibility = View.GONE // GONE for remove space, INVISIBLE for just alpha = 0
                            (cardSpace?.adapter as ResAdapter?)?.cardList =
                                filteredData as ArrayList<ShiroApi.CommonAnimePage>
                            (cardSpace?.adapter as ResAdapter?)?.notifyDataSetChanged()
                        }
                    }
                }
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                (cardSpace.adapter as ResAdapter).cardList.clear()
                if (newText != "") {
                    progress_bar.visibility = View.VISIBLE
                    thread {
                        val data = ShiroApi.quickSearch(newText)
                        activity?.runOnUiThread {
                            // Nullable since takes time to get data
                            if (data == null) {
                                Toast.makeText(activity, "Server error", Toast.LENGTH_LONG).show()
                                progress_bar?.visibility = View.GONE
                            } else {
                                progress_bar?.visibility =
                                    View.GONE // GONE for remove space, INVISIBLE for just alpha = 0
                                val filteredData =
                                    if (hideDubbed) data.filter { !it.name.endsWith("Dubbed") } else data
                                (cardSpace?.adapter as ResAdapter?)?.cardList =
                                    filteredData as ArrayList<ShiroApi.CommonAnimePage>
                                (cardSpace?.adapter as ResAdapter?)?.notifyDataSetChanged()
                            }
                        }
                    }
                }
                return true
            }
        })
        main_search.setOnQueryTextFocusChangeListener { view, b ->
            val searchParams: LinearLayout.LayoutParams = LinearLayout.LayoutParams(
                LinearLayoutCompat.LayoutParams.MATCH_PARENT, // view width
                60.toPx // view height
            )
            val transition: Transition = ChangeBounds()
            transition.duration = 100 // DURATION OF ANIMATION IN MS

            TransitionManager.beginDelayedTransition(main_search, transition)

            val margins = if (b) 0 else 6.toPx
            searchParams.height -= margins * 2 // TO KEEP
            searchParams.setMargins(margins)
            main_search.layoutParams = searchParams
        }
        main_search.onActionViewExpanded()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        searchViewModel =
            ViewModelProviders.of(this).get(SearchViewModel::class.java)

        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    override fun onStop() {
        super.onStop()
        activity?.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            cardSpace.spanCount = spanCountLandscape
            //Toast.makeText(activity, "landscape", Toast.LENGTH_SHORT).show();
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            cardSpace.spanCount = spanCountPortrait
            //Toast.makeText(activity, "portrait", Toast.LENGTH_SHORT).show();
        }
    }

}
