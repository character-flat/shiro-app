package com.lagradost.shiro.ui.tv

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.leanback.widget.SearchBar
import androidx.leanback.widget.SpeechOrbView
import com.lagradost.shiro.R
import com.lagradost.shiro.ui.home.CardAdapter
import com.lagradost.shiro.ui.result.ResultFragment.Companion.onResultsNavigated
import com.lagradost.shiro.ui.tv.TvActivity.Companion.isInSearch
import com.lagradost.shiro.utils.AppUtils.displayCardData
import com.lagradost.shiro.utils.ShiroApi.Companion.quickSearch
import com.lagradost.shiro.utils.ShiroApi.Companion.search
import kotlinx.android.synthetic.main.fragment_search_tv.*
import kotlin.concurrent.thread

class SearchFragmentTv : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        isInSearch = true
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_search_tv, container, false)
    }

    override fun onDestroy() {
        isInSearch = false
        onResultsNavigated -= ::restoreState
        super.onDestroy()
    }

    private fun restoreState(hasEntered: Boolean) {
        if (hasEntered) {
            this.view?.visibility = GONE
        } else {
            this.view?.visibility = VISIBLE
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val speechOrbView: SpeechOrbView = view.findViewById(R.id.lb_search_bar_speech_orb)
        speechOrbView.visibility = GONE

        onResultsNavigated += ::restoreState
        //val snapHelper = PagerSnapHelper()
        //snapHelper.attachToRecyclerView(scrollView)

        val adapter = CardAdapter(
            requireActivity(),
            ArrayList(),
            false
        )

        search_bar.setSearchBarListener(object : SearchBar.SearchBarListener {
            override fun onSearchQueryChange(query: String?) {
                if (query == null) return
                thread {
                    val data = quickSearch(query)
                    data?.let {
                        activity?.runOnUiThread {
                            activity?.displayCardData(it, search_recycler, expand_text, adapter = adapter)
                        }
                    }
                }
            }

            override fun onSearchQuerySubmit(query: String?) {
                if (query == null) return
                thread {
                    val data = search(query)
                    data?.let {
                        activity?.runOnUiThread {
                            activity?.displayCardData(data, search_recycler, expand_text, adapter = adapter)
                        }
                    }
                }
            }

            override fun onKeyboardDismiss(query: String) {

            }
        })
    }

    companion object {
        fun newInstance() =
            SearchFragmentTv().apply {
                arguments = Bundle().apply {

                }
            }
    }
}