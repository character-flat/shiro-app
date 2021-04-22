package com.lagradost.shiro.ui.tv

import android.os.Bundle
import android.text.TextUtils
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.widget.*
import com.lagradost.shiro.ui.tv.TvActivity.Companion.isInSearch
import com.lagradost.shiro.utils.ShiroApi
import kotlin.concurrent.thread

class MySearchFragment : SearchSupportFragment(), SearchSupportFragment.SearchResultProvider {

    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSearchResultProvider(this)
        isInSearch = true
    }

    override fun onDestroy() {
        isInSearch = false
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
            val header = HeaderItem(0L, "TEST")
            rowsAdapter.add(ListRow(header, listRowAdapter))
        }
        //val header = HeaderItem(i.toLong(), MovieList.MOVIE_CATEGORY[i])
        if (!TextUtils.isEmpty(newQuery)) {
            rowsAdapter
        }
        return true
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        rowsAdapter.clear()
        if (!TextUtils.isEmpty(query)) {

        }
        return true
    }

    companion object {
        private val SEARCH_DELAY_MS = 300
    }
}