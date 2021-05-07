package com.lagradost.shiro.ui.tv

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.lagradost.shiro.utils.ShiroApi

class MainFragmentViewModel : ViewModel() {

    val apiData = MutableLiveData<ShiroApi.ShiroHomePage>().apply {
        ShiroApi.onHomeFetched += ::homeLoaded
    }

    private fun homeLoaded(data: ShiroApi.ShiroHomePage?) {
        apiData.postValue(data!!)
    }
}