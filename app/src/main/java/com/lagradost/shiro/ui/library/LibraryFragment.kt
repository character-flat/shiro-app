package com.lagradost.shiro.ui.library

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.lagradost.shiro.R
import com.lagradost.shiro.utils.AppUtils.getCurrentActivity
import com.lagradost.shiro.utils.AppUtils.settingsManager
import com.lagradost.shiro.utils.MALApi.Companion.getMalAnimeListSmart
import kotlinx.android.synthetic.main.fragment_library.*
import kotlinx.android.synthetic.main.fragment_list.*
import kotlin.concurrent.thread

val tabs = listOf(
    "All Anime",
    "Currently watching",
    "Completed",
    "On Hold",
    "Dropped",
    "Plan to Watch"
)

class LibraryFragment : Fragment() {
    private val spanCountLandscape = 2
    private val spanCountPortrait = 1

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
        return inflater.inflate(R.layout.fragment_library, container, false)
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

        /*tabs.forEach {
            result_tabs?.addTab(result_tabs.newTab().setText(it))
        }

        swipe_container?.setOnRefreshListener {
            context?.setKey(MAL_SHOULD_UPDATE_LIST, true)
            requestMalList()
        }*/

        viewpager?.adapter = PagerAdapter(getCurrentActivity()!!.supportFragmentManager, lifecycle)
        viewpager?.adapter?.notifyDataSetChanged()

        TabLayoutMediator(result_tabs, viewpager) { tab, position ->
            tab.text = tabs.getOrNull(position) ?: ""
        }.attach()

        /*result_tabs?.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val pos = tab?.position
                if (pos != null) {
                    libraryViewModel?.generateListByTab(pos)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })*/
        requestMalList()
    }

    private fun requestMalList() {
        thread {
            context?.getMalAnimeListSmart()?.let {
                libraryViewModel?.updateList(it)
            }
            /*main {
                swipe_container?.isRefreshing = false
            }*/
        }

    }

    companion object {
        var libraryViewModel: LibraryViewModel? = null

        fun newInstance() =
            LibraryFragment().apply {
                /*arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                }*/
            }
    }
}

class PagerAdapter(fragmentManager: FragmentManager, lifecycle: Lifecycle) :
    FragmentStateAdapter(fragmentManager, lifecycle) {

    override fun getItemCount(): Int {
        return tabs.size
    }

    override fun createFragment(position: Int): Fragment {
        return ListFragment.newInstance(position)
    }

}