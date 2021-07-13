package com.lagradost.shiro.ui.search

import DataStore.getKey
import DataStore.setKey
import HAS_DISMISSED_SEARCH_INFO
import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.INPUT_METHOD_SERVICE
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.transition.ChangeBounds
import android.transition.Transition
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.setMargins
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.jaredrummler.cyanea.Cyanea
import com.lagradost.shiro.R
import com.lagradost.shiro.ui.MainActivity
import com.lagradost.shiro.ui.player.PlayerFragment.Companion.isInPlayer
import com.lagradost.shiro.ui.result.ResultFragment.Companion.isInResults
import com.lagradost.shiro.ui.toPx
import com.lagradost.shiro.utils.AppUtils.filterCardList
import com.lagradost.shiro.utils.AppUtils.getCurrentActivity
import com.lagradost.shiro.utils.AppUtils.observe
import com.lagradost.shiro.utils.AppUtils.settingsManager
import com.lagradost.shiro.utils.ShiroApi
import com.lagradost.shiro.utils.ShiroApi.Companion.getSearchMethods
import kotlinx.android.synthetic.main.fragment_search.*
import kotlinx.android.synthetic.main.genres_search.*
import kotlin.concurrent.thread

class SearchFragment : Fragment() {
    private var searchViewModel: SearchViewModel? = null
    private val compactView = settingsManager!!.getBoolean("compact_search_enabled", true)
    private val spanCountLandscape = if (compactView) 2 else 6
    private val spanCountPortrait = if (compactView) 1 else 3

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (context?.getKey(HAS_DISMISSED_SEARCH_INFO, false) == false) {
            val builder: AlertDialog.Builder =
                AlertDialog.Builder(getCurrentActivity()!!, R.style.AlertDialogCustom)
            builder.setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                context?.setKey(HAS_DISMISSED_SEARCH_INFO, true)
            }
            builder.setMessage("Press the return/search button on your keyboard to search for more than 5 titles.")
                .setTitle("Search info")
            val dialog = builder.create()
            dialog.setCanceledOnTouchOutside(false)
            dialog.setCancelable(false)
            dialog.show()
        }

        /*if (!isInResults && this.isVisible) {
            activity?.window?.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
            )
        }*/
        val orientation = resources.configuration.orientation
        if (orientation == Configuration.ORIENTATION_LANDSCAPE || settingsManager!!.getBoolean(
                "force_landscape",
                false
            )
        ) {
            cardSpace?.spanCount = spanCountLandscape
        } else {
            cardSpace?.spanCount = spanCountPortrait
        }

        val topParams: LinearLayout.LayoutParams = LinearLayout.LayoutParams(
            LinearLayoutCompat.LayoutParams.MATCH_PARENT, // view width
            MainActivity.statusHeight // view height
        )
        top_padding.layoutParams = topParams

        search_results_layout?.background = ColorDrawable(Cyanea.instance.backgroundColor)
        main_search?.background = ColorDrawable(Cyanea.instance.backgroundColorDark)

        progress_bar?.visibility = GONE
        val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>? = context?.let {
            ResAdapter(
                it,
                ArrayList(),
                cardSpace,
            )
        }
        cardSpace?.adapter = adapter
        search_fab_button.backgroundTintList = ColorStateList.valueOf(
            Cyanea.instance.primaryDark
        )
        search_fab_button.setOnClickListener {
            activity?.let { activity ->
                if (searchViewModel!!.searchOptions.value == null) {
                    thread {
                        searchViewModel!!.searchOptions.postValue(getSearchMethods())
                    }
                }

                val tags = searchViewModel!!.searchOptions.value?.genres?.sortedBy { it.name }
                val bottomSheetDialog = BottomSheetDialog(activity, R.style.AppBottomSheetDialogTheme)
                bottomSheetDialog.setContentView(R.layout.genres_search)

                bottomSheetDialog.genres_top_bar.backgroundTintList = ColorStateList.valueOf(
                    Cyanea.instance.primaryDark
                )

                val filterButton = bottomSheetDialog.findViewById<MaterialButton>(R.id.filter_button)!!
                val searchTags = bottomSheetDialog.findViewById<MyFlowLayout>(R.id.search_tags)!!

                tags?.forEachIndexed { index, tag ->
                    val viewBtt = layoutInflater.inflate(R.layout.genre_tag, null)
                    val btt = viewBtt.findViewById<MaterialButton>(R.id.result_tag_card)
                    btt.text = tag.name
                    activity.changeTagState(btt, tag)

                    btt.setOnClickListener {
                        activity.changeTagState(btt, tag, true)
                    }

                    searchTags.addView(viewBtt, index)
                }

                filterButton.setOnClickListener {
                    searchViewModel!!.selectedGenres.postValue(listOf())
                    bottomSheetDialog.dismiss()
                }

                bottomSheetDialog.setOnDismissListener {
                    //  MainActivity.semihideNavbar()
                }
                bottomSheetDialog.show()
                //  MainActivity.showNavbar()
            }
        }

        observe(searchViewModel!!.selectedGenres) {
            if (!it.isNullOrEmpty()) {
                (cardSpace?.adapter as ResAdapter).cardList.clear()
                progress_bar.visibility = View.VISIBLE

                thread {
                    val data = ShiroApi.search("", genresInput = it)
                    activity?.runOnUiThread {
                        if (data == null) {
                            //Toast.makeText(activity, "Server error", Toast.LENGTH_LONG).show()
                            progress_bar?.visibility = View.GONE
                        } else {
                            val filteredData =
                                filterCardList(data)
                            progress_bar?.visibility =
                                GONE // GONE for remove space, INVISIBLE for just alpha = 0
                            (cardSpace?.adapter as ResAdapter?)?.cardList =
                                filteredData as ArrayList<ShiroApi.CommonAnimePage>
                            (cardSpace?.adapter as ResAdapter?)?.notifyDataSetChanged()
                        }
                    }
                }
            }
        }

        main_search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                progress_bar.visibility = View.VISIBLE
                (cardSpace?.adapter as ResAdapter).cardList.clear()
                thread {
                    val data = ShiroApi.search(query, genresInput = searchViewModel!!.selectedGenres.value)
                    activity?.runOnUiThread {
                        if (data == null) {
                            //Toast.makeText(activity, "Server error", Toast.LENGTH_LONG).show()
                            progress_bar?.visibility = View.GONE
                        } else {
                            val filteredData =
                                filterCardList(data)
                            progress_bar?.visibility =
                                View.GONE // GONE for remove space, INVISIBLE for just alpha = 0
                            (cardSpace?.adapter as ResAdapter?)?.cardList =
                                filteredData as ArrayList<ShiroApi.CommonAnimePage>
                            (cardSpace?.adapter as ResAdapter?)?.notifyDataSetChanged()
                        }
                    }
                }
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                (cardSpace?.adapter as ResAdapter).cardList.clear()
                if (newText != "" && searchViewModel!!.selectedGenres.value?.isNullOrEmpty() != false) {
                    progress_bar.visibility = View.VISIBLE
                    thread {
                        val data = ShiroApi.quickSearch(newText)
                        activity?.runOnUiThread {
                            // Nullable since takes time to get data
                            if (data == null) {
                                //Toast.makeText(activity, "Server error", Toast.LENGTH_LONG).show()
                                progress_bar?.visibility = View.GONE
                            } else {
                                progress_bar?.visibility =
                                    View.GONE // GONE for remove space, INVISIBLE for just alpha = 0
                                val filteredData =
                                    filterCardList(data)
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

            if (b) {
                // https://stackoverflow.com/questions/12022715/unable-to-show-keyboard-automatically-in-the-searchview
                view.postDelayed({
                    run {
                        if (!isInResults && !isInPlayer) {
                            val imm: InputMethodManager? =
                                activity?.getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager?
                            imm?.showSoftInput(view.findFocus(), 0)
                            activity?.findViewById<View>(R.id.search_mag_icon)?.visibility = GONE
                        }
                    }
                }, 200)
            }
            val transition: Transition = ChangeBounds()
            transition.duration = 100 // DURATION OF ANIMATION IN MS

            TransitionManager.beginDelayedTransition(main_search, transition)

            val margins = if (b) 0 else 6.toPx
            searchParams.height -= margins * 2 // TO KEEP
            searchParams.setMargins(margins)
            main_search.layoutParams = searchParams
        }
        main_search.onActionViewExpanded()

        //main_search.findViewById<EditText>(R.id.search_src_text).requestFocus()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        searchViewModel =
            searchViewModel ?: ViewModelProvider(getCurrentActivity()!!).get(SearchViewModel::class.java)

        if (searchViewModel!!.searchOptions.value == null) {
            thread {
                searchViewModel!!.searchOptions.postValue(getSearchMethods())
            }
        }
        return inflater.inflate(R.layout.fragment_search, container, false)
    }


    private fun Context.changeTagState(view: MaterialButton, tag: ShiroApi.Genre, changed: Boolean = false) {
        val contains = (searchViewModel!!.selectedGenres.value ?: listOf()).contains(tag) == changed

        activity?.let {
            if (!contains) {
                if (changed) {
                    // Same as .add(tag)
                    val newGenres =
                        listOf(searchViewModel!!.selectedGenres.value ?: listOf(), listOf(tag)).flatten()
                    searchViewModel!!.selectedGenres.postValue(newGenres)
                }
                view.backgroundTintList = ColorStateList.valueOf(
                    Cyanea.instance.primaryLight
                )
                //view.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            } else if (changed) {
                val newGenres = searchViewModel!!.selectedGenres.value?.filter { genre -> genre != tag }
                searchViewModel!!.selectedGenres.postValue(newGenres)
                view.backgroundTintList =
                    ColorStateList.valueOf(ContextCompat.getColor(this, R.color.transparent))
                /*view.setTextColor(
                    it.getColorFromAttr(R.attr.colorAccent)
                )*/

            }
        }
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
            cardSpace?.spanCount = spanCountLandscape
            //Toast.makeText(activity, "landscape", Toast.LENGTH_SHORT).show();
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            cardSpace?.spanCount = spanCountPortrait
            //Toast.makeText(activity, "portrait", Toast.LENGTH_SHORT).show();
        }
    }

}
