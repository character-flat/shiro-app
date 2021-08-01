package com.lagradost.shiro.ui.library

import ANILIST_SHOULD_UPDATE_LIST
import ANILIST_TOKEN_KEY
import DataStore.getKey
import DataStore.setKey
import LIBRARY_IS_MAL
import MAL_SHOULD_UPDATE_LIST
import MAL_TOKEN_KEY
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView.CHOICE_MODE_SINGLE
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.PagerAdapter
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayoutMediator
import com.jaredrummler.cyanea.Cyanea
import com.lagradost.shiro.R
import com.lagradost.shiro.ui.MainActivity
import com.lagradost.shiro.ui.library.LibraryFragment.Companion.libraryViewModel
import com.lagradost.shiro.ui.library.LibraryFragment.Companion.onMenuCollapsed
import com.lagradost.shiro.utils.*
import com.lagradost.shiro.utils.AniListApi.Companion.convertAnilistStringToStatus
import com.lagradost.shiro.utils.AniListApi.Companion.secondsToReadable
import com.lagradost.shiro.utils.AppUtils.getCurrentActivity
import com.lagradost.shiro.utils.AppUtils.getTextColor
import com.lagradost.shiro.utils.AppUtils.settingsManager
import com.lagradost.shiro.utils.Coroutines.main
import com.lagradost.shiro.utils.MALApi.Companion.convertJapanTimeToTimeRemaining
import com.lagradost.shiro.utils.MALApi.Companion.convertToStatus
import com.lagradost.shiro.utils.mvvm.observe
import kotlinx.android.synthetic.main.bottom_sheet.*
import kotlinx.android.synthetic.main.fragment_library.*
import kotlinx.android.synthetic.main.mal_list.view.*

class CustomSearchView(context: Context) : SearchView(context) {
    override fun onActionViewCollapsed() {
        onMenuCollapsed.invoke(true)
        super.onActionViewCollapsed()
    }
}

val tabs = listOf(
    Pair("Currently watching", 0),
    Pair("Plan to Watch", 1),
    Pair("On Hold", 2),
    Pair("Completed", 3),
    Pair("Dropped", 4),
    Pair("All Anime", 5),
)

class LibraryFragment : Fragment() {
    data class SortingMethod(val name: String, val id: Int)

    private val malSortingMethods = arrayOf(
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

    private val anilistSortingMethods = arrayOf(
        //SortingMethod("Default", DEFAULT_SORT),
        SortingMethod("Recently updated (New to Old)", LATEST_UPDATE),
        SortingMethod("Recently updated (Old to New)", LATEST_UPDATE_REVERSED),
        SortingMethod("Alphabetical (A-Z)", ALPHA_SORT),
        SortingMethod("Alphabetical (Z-A)", REVERSE_ALPHA_SORT),
        SortingMethod("Score (High to Low)", SCORE_SORT),
        SortingMethod("Score (Low to High)", SCORE_SORT_REVERSED),
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        libraryViewModel =
            libraryViewModel ?: ViewModelProvider(getCurrentActivity()!!).get(LibraryViewModel::class.java)
        val hasMAL = getCurrentActivity()!!.getKey<String>(MAL_TOKEN_KEY, MAL_ACCOUNT_ID, null) != null
        val hasAniList = getCurrentActivity()!!.getKey<String>(
            ANILIST_TOKEN_KEY,
            ANILIST_ACCOUNT_ID,
            null
        ) != null

        libraryViewModel?.isMal = (getCurrentActivity()!!.getKey(LIBRARY_IS_MAL, true) == true && hasMAL) || !hasAniList
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_library, container, false)
    }

    private fun getCurrentTabCorrected(): Int {
        return tabs[(result_tabs?.selectedTabPosition ?: 0)].second
    }

    private fun sortCurrentListEventFunction(boolean: Boolean) {
        libraryViewModel?.displayList()
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

        val hasAniList = getCurrentActivity()!!.getKey<String>(
            ANILIST_TOKEN_KEY,
            ANILIST_ACCOUNT_ID,
            null
        ) != null
        val hasMAL = getCurrentActivity()!!.getKey<String>(MAL_TOKEN_KEY, MAL_ACCOUNT_ID, null) != null
        login_overlay?.background = ColorDrawable(Cyanea.instance.backgroundColor)
        login_overlay?.isVisible = !hasAniList && !hasMAL
        library_toolbar?.navigationIcon = if (hasAniList && hasMAL) ContextCompat.getDrawable(
            getCurrentActivity()!!,
            R.drawable.ic_baseline_swap_vert_24
        ) else null
        library_toolbar?.children?.forEach {
            if (it is ImageButton) {
                it.setOnClickListener {
                    val newIsMal = !(libraryViewModel?.isMal ?: true)
                    libraryViewModel?.isMal = newIsMal
                    context?.setKey(LIBRARY_IS_MAL, newIsMal)
                    libraryViewModel?.displayList()
                }
                return@forEach
            }
        }

        val searchView: CustomSearchView =
            library_toolbar.menu.findItem(R.id.action_search).actionView as CustomSearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                libraryViewModel?.sortCurrentList(getCurrentTabCorrected(), SEARCH, query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                libraryViewModel?.sortCurrentList(getCurrentTabCorrected(), SEARCH, newText)
                return true
            }
        })

        library_toolbar?.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_reload -> {
                    context?.setKey(MAL_SHOULD_UPDATE_LIST, true)
                    context?.setKey(ANILIST_SHOULD_UPDATE_LIST, true)
                    libraryViewModel?.requestMalList(context)
                    libraryViewModel?.requestAnilistList(context)
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

                    val sortingMethods =
                        if (libraryViewModel?.isMal == true) malSortingMethods else anilistSortingMethods

                    arrayAdapter.addAll(ArrayList(sortingMethods.map { t -> t.name }))
                    res.adapter = arrayAdapter

                    res.setItemChecked(
                        sortingMethods.indexOfFirst { t ->
                            t.id == libraryViewModel?.sortMethods?.getOrNull(
                                result_tabs?.selectedTabPosition ?: -1
                            ) ?: 0
                        },
                        true
                    )
                    res.setOnItemClickListener { _, _, position, _ ->
                        val sel = sortingMethods[position].id
                        libraryViewModel?.sortCurrentList(getCurrentTabCorrected(), sel)
                        bottomSheetDialog.dismiss()
                    }

                    bottomSheetDialog.show()
                }
                else -> {
                }
            }
            return@setOnMenuItemClickListener true
        }

        //viewpager?.adapter = CustomFragmentPagerAdapter() // CustomPagerAdapter(getCurrentActivity()!!)
        viewpager?.adapter = CustomPagerAdapter()

        viewpager?.adapter?.notifyDataSetChanged()
        //result_tabs?.setupWithViewPager(viewpager)
        result_tabs?.tabTextColors = ColorStateList.valueOf(getCurrentActivity()!!.getTextColor())
        result_tabs?.setSelectedTabIndicatorColor(getCurrentActivity()!!.getTextColor())

        TabLayoutMediator(
            result_tabs,
            viewpager,
        ) { tab, position ->
            tab.text = tabs.getOrNull(position)?.first ?: ""
        }.attach()

        /*tabs.forEach {
            result_tabs?.addTab(result_tabs.newTab().setText(it.first))
        }*/

        library_toolbar?.title = if (libraryViewModel?.isMal == true) "MAL" else "Anilist"
        observe(libraryViewModel!!.currentList) { list ->
            for (i in tabs.indices) {
                val size = list.getOrNull(tabs[i].second)?.size ?: 0
                main {
                    result_tabs?.getTabAt(i)?.text = tabs[i].first + " ($size)"
                }
            }
            viewpager?.adapter?.notifyDataSetChanged()
            library_toolbar?.title = if (libraryViewModel?.isMal == true) "MAL" else "Anilist"
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
        libraryViewModel?.requestMalList(context)
        libraryViewModel?.requestAnilistList(context)
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

class CustomFragmentPagerAdapter : PagerAdapter() {
    override fun getCount(): Int {
        return tabs.size
    }

    override fun isViewFromObject(view: View, `object`: Any): Boolean {
        return view == `object`
    }

    override fun instantiateItem(container: ViewGroup, position: Int) {
        val view = LayoutInflater.from(getCurrentActivity()!!).inflate(R.layout.mal_list, container, false)
        view.layoutParams =
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        val orientation = container.context.resources.configuration.orientation
        if (orientation == Configuration.ORIENTATION_LANDSCAPE || settingsManager?.getBoolean(
                "force_landscape",
                false
            ) == true
        ) {
            view.library_card_space?.spanCount = spanCountPortrait * 2
        } else {
            view.library_card_space?.spanCount = spanCountPortrait
        }
        fun displayList(list: List<LibraryObject>) {
            if (view.library_card_space?.adapter == null) {
                view.library_card_space?.adapter = LibraryCardAdapter(container.context, list)
                println((view.library_card_space?.adapter as? LibraryCardAdapter))
            } else {
                (view.library_card_space?.adapter as? LibraryCardAdapter)?.list = list
                view.library_card_space?.adapter?.notifyDataSetChanged()
            }
        }

        libraryViewModel?.currentList?.value?.getOrNull(tabs[position].second)?.let {
            val list = generateLibraryObject(it)
            if ((view.library_card_space?.adapter as? LibraryCardAdapter)?.list != list) {
                displayList(list)
            }
        }
        container.addView(view)
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        //container.removeView(`object` as View)
    }

}

private val spanCountPortrait = settingsManager?.getInt("library_span_count", 1) ?: 1
private fun generateLibraryObject(list: List<Any>): List<LibraryObject> {
    if (list.firstOrNull() is MALApi.Companion.Data) {
        (list as? List<MALApi.Companion.Data>)?.let {
            return it.map { data ->
                LibraryObject(
                    data.node.title,
                    data.node.main_picture?.medium ?: "",
                    data.node.id.toString(),
                    data.list_status?.score ?: 0,
                    data.list_status?.num_episodes_watched ?: 0,
                    data.node.num_episodes,
                    data.node.start_season?.season,
                    data.node.start_season?.year,
                    convertToStatus(data.list_status?.status ?: "").value,
                    data.node.broadcast?.day_of_the_week?.plus(" ")?.plus(data.node.broadcast.start_time)?.let {
                        convertJapanTimeToTimeRemaining(it, data.node.end_date)
                    }
                )
            }
        }
    } else if (list.firstOrNull() is AniListApi.Companion.Entries) {
        (list as? List<AniListApi.Companion.Entries>)?.let {
            return it.map {
                LibraryObject(
                    it.media.title.english ?: it.media.title.romaji ?: "",
                    it.media.coverImage.medium,
                    it.media.idMal.toString(),
                    it.score,
                    it.progress,
                    it.media.episodes,
                    it.media.season,
                    if (it.media.seasonYear == 0) null else it.media.seasonYear,
                    convertAnilistStringToStatus(it.status ?: "").value,
                    it.media.nextAiringEpisode?.timeUntilAiring?.let { it -> secondsToReadable(it, "Now") }
                )
            }
        }
    }

    return listOf()
}


class CustomPagerAdapter() :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    override fun getItemCount(): Int {
        return tabs.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return CardViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.mal_list, parent, false),
            parent.context
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
    constructor(itemView: View, val context: Context) :
        RecyclerView.ViewHolder(itemView) {

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
            fun displayList(list: List<LibraryObject>) {
                if (itemView.library_card_space?.adapter == null) {
                    itemView.library_card_space?.adapter = LibraryCardAdapter(context, list)
                } else {
                    (itemView.library_card_space?.adapter as? LibraryCardAdapter)?.list = list
                    itemView.library_card_space?.adapter?.notifyDataSetChanged()
                }
            }

            libraryViewModel?.currentList?.value?.getOrNull(tabs[position].second)?.let {
                val list = generateLibraryObject(it)
                if ((itemView.library_card_space?.adapter as? LibraryCardAdapter)?.list != list) {
                    displayList(list)
                }
            }
        }
    }
}