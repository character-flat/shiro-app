package com.lagradost.shiro.ui.result

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ResultsViewModel : ViewModel() {
    var currentAniListId = MutableLiveData<Int?>()
    var currentMalId = MutableLiveData<Int?>()
    val visibleEpisodeProgress = MutableLiveData<Int?>()
    val episodes = MutableLiveData<Int?>()
    var slug = MutableLiveData<String?>()
}