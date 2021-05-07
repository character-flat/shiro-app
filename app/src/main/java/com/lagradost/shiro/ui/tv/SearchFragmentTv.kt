package com.lagradost.shiro.ui.tv

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.leanback.widget.SearchBar
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.shiro.R
import com.lagradost.shiro.ui.home.CardAdapter
import com.lagradost.shiro.ui.tv.TvActivity.Companion.isInSearch
import com.lagradost.shiro.utils.AppApi.displayCardData
import com.lagradost.shiro.utils.AppApi.settingsManager
import com.lagradost.shiro.utils.ShiroApi
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
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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