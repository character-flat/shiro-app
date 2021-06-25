package com.lagradost.shiro.ui

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.lagradost.shiro.ui.player.PlayerData

class MasterViewModel : ViewModel() {
    val playerData = MutableLiveData<PlayerData>()
}