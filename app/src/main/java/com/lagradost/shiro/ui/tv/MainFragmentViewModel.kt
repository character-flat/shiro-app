package com.lagradost.shiro.ui.tv

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.lagradost.shiro.ui.BookmarkedTitle
import com.lagradost.shiro.utils.ShiroApi

class MainFragmentViewModel : ViewModel() {

    val apiData = MutableLiveData<ShiroApi.ShiroHomePage>().apply {
        ShiroApi.onHomeFetched += ::homeLoaded
    }

    var favorites = MutableLiveData<List<BookmarkedTitle?>?>()
    val subscribed = MutableLiveData<List<BookmarkedTitle?>?>()

    private fun homeLoaded(data: ShiroApi.ShiroHomePage?) {
        favorites.postValue(data?.favorites)
        subscribed.postValue(data?.subscribed)
        apiData.postValue(data!!)
    }
}