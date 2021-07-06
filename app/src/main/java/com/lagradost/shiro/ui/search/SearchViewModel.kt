package com.lagradost.shiro.ui.search

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.lagradost.shiro.utils.ShiroApi

class SearchViewModel : ViewModel() {
    val searchOptions = MutableLiveData<ShiroApi.AllSearchMethodsData>()
    val selectedGenres = MutableLiveData<List<ShiroApi.Genre>>()

    private fun searchLoaded(data: ShiroApi.AllSearchMethodsData?) {
        searchOptions.postValue(data!!)
    }
}