package com.lagradost.shiro.ui.library

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.lagradost.shiro.R
import com.lagradost.shiro.ui.library.LibraryFragment.Companion.libraryViewModel
import com.lagradost.shiro.utils.AppUtils.settingsManager
import com.lagradost.shiro.utils.MALApi
import com.lagradost.shiro.utils.mvvm.observe
import kotlinx.android.synthetic.main.fragment_list.*

class ListFragment : Fragment() {
    private val spanCountLandscape = 2
    private val spanCountPortrait = 1
    private val PAGE = "page_param"
    var page = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            page = it.getInt(PAGE)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
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

        /*observe(libraryViewModel!!.sortedMalList) { list ->
            if (library_card_space?.adapter == null) {
                library_card_space?.adapter = CardListAdapter()
                (library_card_space?.adapter as? CardListAdapter)?.submitList(list.toList())
            } else {
                (library_card_space?.adapter as? CardListAdapter)?.submitList(list.toList())
            }
        }*/

        fun generateList(list: Array<MALApi.Companion.Data>) {
            if (library_card_space?.adapter == null) {
                library_card_space?.adapter = CardListAdapter()
                (library_card_space?.adapter as? CardListAdapter)?.submitList(list.toList())
            } else {
                (library_card_space?.adapter as? CardListAdapter)?.submitList(list.toList())
            }
        }

        libraryViewModel?.sortedMalList?.value?.getOrNull(page)?.let { generateList(it) }
        observe(libraryViewModel!!.sortedMalList) { list ->
            list?.getOrNull(page)?.let { generateList(it) }
        }
    }

    companion object {
        fun newInstance(page: Int) =
            ListFragment().apply {
                arguments = Bundle().apply {
                    putInt(PAGE, page)
                }
            }
    }
}