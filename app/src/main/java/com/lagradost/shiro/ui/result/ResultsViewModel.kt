package com.lagradost.shiro.ui.result

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.lagradost.shiro.ui.BookmarkedTitle
import com.lagradost.shiro.utils.ShiroApi

class ResultsViewModel : ViewModel() {
    var currentAniListId = MutableLiveData<Int?>()
    var currentMalId = MutableLiveData<Int?>()
    val visibleEpisodeProgress = MutableLiveData<Int?>()
}