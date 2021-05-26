package com.lagradost.shiro.ui.tv

import android.os.Bundle
import android.text.TextUtils
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.widget.*
import com.lagradost.shiro.R
import com.lagradost.shiro.ui.result.ResultFragment
import com.lagradost.shiro.utils.ShiroApi
import kotlin.concurrent.thread

class MySearchFragment : SearchSupportFragment(), SearchSupportFragment.SearchResultProvider {

    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSearchResultProvider(this)
        //isInSearch = true
        setOnItemViewClickedListener { _, item, _, _ ->
            println("Clicked ${item.javaClass.simpleName}")
            if (item is ShiroApi.ShiroSearchResponseShow) {
                activity?.supportFragmentManager?.beginTransaction()
                    ?.setCustomAnimations(R.anim.enter, R.anim.exit, R.anim.pop_enter, R.anim.pop_exit)
                    ?.add(R.id.home_root_tv, ResultFragment.newInstance(item.slug))
                    ?.addToBackStack("ResultFragment")
                    ?.commitAllowingStateLoss()
            }
        }
    }

    override fun onDestroy() {
        //isInSearch = false
        super.onDestroy()
    }

    override fun getResultsAdapter(): ObjectAdapter {
        return rowsAdapter
    }

    override fun onQueryTextChange(newQuery: String): Boolean {
        rowsAdapter.clear()
        /*for (j in 0 until NUM_COLS) {
            listRowAdapter.add(data[j % 5])
        }*/
        //val header = HeaderItem(i.toLong(), MovieList.MOVIE_CATEGORY[i])
        if (!TextUtils.isEmpty(newQuery)) {
            thread {
                //val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
                val cardPresenter = CardPresenter()

                val data = ShiroApi.quickSearch(newQuery)
                val listRowAdapter = ArrayObjectAdapter(cardPresenter)
                /*for (j in 0 until NUM_COLS) {
                    listRowAdapter.add(data[j % 5])
                }*/
                data?.forEach {
                    listRowAdapter.add(it)
                }
                val header = HeaderItem(0L, "Search results for \"$newQuery\"")
                try {
                    rowsAdapter.add(ListRow(header, listRowAdapter))
                } catch (e: Exception){
                    // Cannot call this method while RecyclerView is computing a layout or scrolling androidx.leanback.widget.VerticalGridView
                }
            }
        }
        return true
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        rowsAdapter.clear()
        if (!TextUtils.isEmpty(query)) {
            thread {
                //val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
                val cardPresenter = CardPresenter()

                val data = ShiroApi.search(query)
                val listRowAdapter = ArrayObjectAdapter(cardPresenter)
                /*for (j in 0 until NUM_COLS) {
                    listRowAdapter.add(data[j % 5])
                }*/
                data?.forEach {
                    listRowAdapter.add(it)
                }
                val header = HeaderItem(0L, "Search results for \"$query\"")
                rowsAdapter.add(ListRow(header, listRowAdapter))
            }
        }
        return true
    }
}