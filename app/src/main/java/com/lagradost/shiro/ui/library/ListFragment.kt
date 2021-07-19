package com.lagradost.shiro.ui.library

import DataStore.setKey
import MAL_SHOULD_UPDATE_LIST
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.tabs.TabLayout
import com.lagradost.shiro.R
import com.lagradost.shiro.utils.AppUtils.getCurrentActivity
import com.lagradost.shiro.utils.AppUtils.settingsManager
import com.lagradost.shiro.utils.Coroutines.main
import com.lagradost.shiro.utils.MALApi.Companion.getMalAnimeListSmart
import com.lagradost.shiro.utils.mvvm.observe
import kotlinx.android.synthetic.main.fragment_list.*
import kotlin.concurrent.thread

class ListFragment : Fragment() {
    private val spanCountLandscape = 2
    private val spanCountPortrait = 1
    var libraryViewModel: LibraryViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        /*arguments?.let {
            param1 = it.getString(ARG_PARAM1)
        }*/
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        libraryViewModel =
            libraryViewModel ?: ViewModelProvider(getCurrentActivity()!!).get(LibraryViewModel::class.java)
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_list, container, false)
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val orientation = resources.configuration.orientation
        if (orientation == Configuration.ORIENTATION_LANDSCAPE || settingsManager?.getBoolean(
                "force_landscape",
                false
            ) == true
        ) {
            library_card_space?.spanCount = spanCountLandscape
        } else {
            library_card_space?.spanCount = spanCountPortrait
        }
        result_tabs?.removeAllTabs()
        listOf(
            "All Anime",
            "Currently watching",
            "Completed",
            "On Hold",
            "Dropped",
            "Plan to Watch"
        ).forEach {
            result_tabs?.addTab(result_tabs.newTab().setText(it))
        }

        swipe_container?.setOnRefreshListener {
            context?.setKey(MAL_SHOULD_UPDATE_LIST, true)
            requestMalList()
        }

        result_tabs?.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val pos = tab?.position
                if (pos != null) {
                    libraryViewModel?.generateListByTab(pos)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        observe(libraryViewModel!!.fullMalList) {
            result_tabs?.selectedTabPosition?.let { it -> libraryViewModel?.generateListByTab(it) }
        }

        observe(libraryViewModel!!.sortedMalList) { list ->
            if (library_card_space?.adapter == null) {
                library_card_space?.adapter = CardListAdapter()
                (library_card_space?.adapter as? CardListAdapter)?.submitList(list.toList())
            } else {
                (library_card_space?.adapter as? CardListAdapter)?.submitList(list.toList())
            }
        }
        requestMalList()

    }

    private fun requestMalList() {
        thread {
            context?.getMalAnimeListSmart()?.let {
                libraryViewModel?.fullMalList?.postValue(it)
            }
            main {
                 swipe_container?.isRefreshing = false
            }
        }

    }

    companion object {
        fun newInstance() =
            ListFragment().apply {
                /*arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                }*/
            }
    }
}