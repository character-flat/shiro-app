package com.lagradost.shiro.ui.home

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.lagradost.shiro.utils.ShiroApi

class HomeViewModel : ViewModel() {

    val apiData = MutableLiveData<ShiroApi.ShiroHomePage>().apply {
        ShiroApi.onHomeFetched += ::homeLoaded
    }

    private fun homeLoaded(data: ShiroApi.ShiroHomePage?) {
        apiData.postValue(data!!)
    }
}