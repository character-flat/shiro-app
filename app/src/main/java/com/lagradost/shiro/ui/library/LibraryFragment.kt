package com.lagradost.shiro.ui.library

import DataStore.setKey
import MAL_SHOULD_UPDATE_LIST
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView.CHOICE_MODE_SINGLE
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayoutMediator
import com.jaredrummler.cyanea.Cyanea
import com.lagradost.shiro.R
import com.lagradost.shiro.ui.MainActivity
import com.lagradost.shiro.ui.library.LibraryFragment.Companion.libraryViewModel
import com.lagradost.shiro.ui.library.LibraryFragment.Companion.onMenuCollapsed
import com.lagradost.shiro.utils.AppUtils.getCurrentActivity
import com.lagradost.shiro.utils.AppUtils.getTextColor
import com.lagradost.shiro.utils.AppUtils.settingsManager
import com.lagradost.shiro.utils.Event
import com.lagradost.shiro.utils.MALApi
import com.lagradost.shiro.utils.MALApi.Companion.getMalAnimeListSmart
import com.lagradost.shiro.utils.mvvm.observe
import kotlinx.android.synthetic.main.bottom_sheet.*
import kotlinx.android.synthetic.main.fragment_library.*
import kotlinx.android.synthetic.main.mal_list.view.*
import kotlin.concurrent.thread

val tabs = listOf(
    "Currently watching",
    "Plan to Watch",
    "On Hold",
    "Completed",
    "Dropped",
    "All Anime",
)

class CustomSearchView(context: Context) : SearchView(context) {
    override fun onActionViewCollapsed() {
        onMenuCollapsed.invoke(true)
        super.onActionViewCollapsed()
    }
}

class LibraryFragment : Fragment() {
    data class SortingMethod(val name: String, val id: Int)

    private val normalSortingMethods = arrayOf(
        //SortingMethod("Default", DEFAULT_SORT),
        SortingMethod("Recently updated (New to Old)", LATEST_UPDATE),
        SortingMethod("Recently updated (Old to New)", LATEST_UPDATE_REVERSED),
        SortingMethod("Alphabetical (A-Z)", ALPHA_SORT),
        SortingMethod("Alphabetical (Z-A)", REVERSE_ALPHA_SORT),
        SortingMethod("Score (High to Low)", SCORE_SORT),
        SortingMethod("Score (Low to High)", SCORE_SORT_REVERSED),
        SortingMethod("Rank (High to Low)", RANK_SORT),
        SortingMethod("Rank (Low to High)", RANK_SORT_REVERSED),
    )

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

    private fun sortCurrentList(sortingMethod: Int? = null, text: String? = null) {
        result_tabs?.selectedTabPosition?.let { it ->
            (sortingMethod ?: libraryViewModel?.sortMethods?.get(it))?.let { sortingMethodFixed ->
                if (sortingMethodFixed != SEARCH) libraryViewModel?.sortMethods?.set(it, sortingMethodFixed)
                libraryViewModel?.sortedMalList?.value?.let { fullArray ->
                    libraryViewModel?.sortNormalArray(fullArray[it], sortingMethodFixed, text)?.let { sorted ->
                        libraryViewModel?.sortedMalList?.postValue(libraryViewModel?.sortedMalList?.value?.apply {
                            this[it] = sorted
                        })
                    }
                }
            }

        }
    }

    private fun sortCurrentListEventFunction(boolean: Boolean) {
        libraryViewModel?.sortedMalList?.value?.getOrNull(5)?.let {
            libraryViewModel?.updateList(it)
        }
    }

    override fun onResume() {
        onMenuCollapsed += ::sortCurrentListEventFunction
        super.onResume()
    }

    override fun onStop() {
        onMenuCollapsed -= ::sortCurrentListEventFunction
        super.onStop()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        result_tabs?.removeAllTabs()

        /*tabs.forEach {
            result_tabs?.addTab(result_tabs.newTab().setText(it))
        }*/
        fragment_list_root.setPadding(0, MainActivity.statusHeight, 0, 0)

//        swipe_container?.setOnRefreshListener {
//            context?.setKey(MAL_SHOULD_UPDATE_LIST, true)
//            requestMalList()
//        }

        val searchView: CustomSearchView =
            library_toolbar.menu.findItem(R.id.action_search).actionView as CustomSearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                sortCurrentList(SEARCH, query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                sortCurrentList(SEARCH, newText)
                return true
            }
        })


        library_toolbar?.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_reload -> {
                    context?.setKey(MAL_SHOULD_UPDATE_LIST, true)
                    requestMalList()
                    Toast.makeText(context, "Refreshing your list", Toast.LENGTH_SHORT).show()
                }

                R.id.action_sort -> {
                    val bottomSheetDialog = BottomSheetDialog(getCurrentActivity()!!, R.style.AppBottomSheetDialogTheme)
                    bottomSheetDialog.setContentView(R.layout.bottom_sheet)
                    bottomSheetDialog.main_text.text = "Sort by"
                    bottomSheetDialog.bottom_sheet_top_bar.backgroundTintList =
                        ColorStateList.valueOf(Cyanea.instance.backgroundColorDark)

                    val res = bottomSheetDialog.findViewById<ListView>(R.id.sort_click)!!
                    val arrayAdapter = ArrayAdapter<String>(
                        requireContext(),
                        R.layout.bottom_single_choice
                    ) // checkmark_select_dialog
                    res.choiceMode = CHOICE_MODE_SINGLE

                    arrayAdapter.addAll(ArrayList(normalSortingMethods.map { t -> t.name }))
                    res.adapter = arrayAdapter

                    res.setItemChecked(
                        normalSortingMethods.indexOfFirst { t ->
                            t.id == libraryViewModel?.sortMethods?.getOrNull(
                                result_tabs?.selectedTabPosition ?: -1
                            ) ?: 0
                        },
                        true
                    )
                    res.setOnItemClickListener { _, _, position, _ ->
                        val sel = normalSortingMethods[position].id
                        sortCurrentList(sel)
                        bottomSheetDialog.dismiss()
                    }

                    bottomSheetDialog.show()
                }
                else -> {
                }
            }
            return@setOnMenuItemClickListener true
        }

        viewpager?.adapter = CustomPagerAdapter(getCurrentActivity()!!)
        //viewpager?.adapter?.notifyDataSetChanged()

        result_tabs?.tabTextColors = ColorStateList.valueOf(getCurrentActivity()!!.getTextColor())
        result_tabs?.setSelectedTabIndicatorColor(getCurrentActivity()!!.getTextColor())

        TabLayoutMediator(
            result_tabs,
            viewpager,
        ) { tab, position ->
            tab.text = tabs.getOrNull(position) ?: ""
        }.attach()


        observe(libraryViewModel!!.sortedMalList) { list ->
            for (i in 0..tabs.size) {
                val size = list?.getOrNull(i)?.size ?: 0
                result_tabs?.getTabAt(i)?.text = tabs[i] + " ($size)"
            }
        }
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
        }

    }

    companion object {
        var libraryViewModel: LibraryViewModel? = null
        val onMenuCollapsed = Event<Boolean>()

        fun newInstance() =
            LibraryFragment().apply {
                /*arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                }*/
            }
    }
}

class CustomPagerAdapter(val context: FragmentActivity) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun getItemCount(): Int {
        return tabs.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return CardViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.mal_list, parent, false),
            context
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is CardViewHolder -> {
                holder.bind(position)
            }
        }
    }

    class CardViewHolder
    constructor(itemView: View, val context: FragmentActivity) :
        RecyclerView.ViewHolder(itemView) {
        private val spanCountPortrait = settingsManager?.getInt("library_span_count", 1) ?: 1

        fun bind(position: Int) {
            val orientation = context.resources.configuration.orientation
            if (orientation == Configuration.ORIENTATION_LANDSCAPE || settingsManager?.getBoolean(
                    "force_landscape",
                    false
                ) == true
            ) {
                itemView.library_card_space?.spanCount = spanCountPortrait * 2
            } else {
                itemView.library_card_space?.spanCount = spanCountPortrait
            }

            fun generateList(list: Array<MALApi.Companion.Data>) {
                if (itemView.library_card_space?.adapter == null) {
                    itemView.library_card_space?.adapter = LibraryCardAdapter(context, list)
                } else {
                    (itemView.library_card_space?.adapter as? LibraryCardAdapter)?.list = list
                    itemView.library_card_space?.adapter?.notifyDataSetChanged()
                }
            }

            libraryViewModel?.sortedMalList?.value?.getOrNull(position)?.let {
                if (!(itemView.library_card_space?.adapter as? LibraryCardAdapter)?.list.contentEquals(it)) {
                    generateList(it)
                }
            }
            context.observe(libraryViewModel!!.sortedMalList) { list ->
                list?.getOrNull(position)?.let { generateList(it) }
            }
        }
    }
}