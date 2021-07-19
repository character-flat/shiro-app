package com.lagradost.shiro.ui.library

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.lagradost.shiro.utils.MALApi
import com.lagradost.shiro.utils.MALApi.Companion.fromIntToAnimeStatus
import com.lagradost.shiro.utils.MALApi.Companion.malStatusAsString

class LibraryViewModel : ViewModel() {
    val fullMalList = MutableLiveData<Array<MALApi.Companion.Data>>()
    val sortedMalList = MutableLiveData<Array<MALApi.Companion.Data>>()

    /**
    "All Anime",
    "Currently watching",
    "Completed",
    "On Hold",
    "Dropped",
    "Plan to Watch"
     **/

    private fun convertToStatus(string: String): MALApi.Companion.MalStatusType {
        return fromIntToAnimeStatus(malStatusAsString.indexOf(string))
    }

    fun generateListByTab(tab: Int) {
        when (tab) {
            0 -> sortedMalList.postValue(fullMalList.value)
            1 -> sortedMalList.postValue(fullMalList.value?.filter { convertToStatus(it.list_status.status) == MALApi.Companion.MalStatusType.Watching }
                ?.toTypedArray())
            2 -> sortedMalList.postValue(fullMalList.value?.filter { convertToStatus(it.list_status.status) == MALApi.Companion.MalStatusType.Completed }
                ?.toTypedArray())
            3 -> sortedMalList.postValue(fullMalList.value?.filter { convertToStatus(it.list_status.status) == MALApi.Companion.MalStatusType.OnHold }
                ?.toTypedArray())
            4 -> sortedMalList.postValue(fullMalList.value?.filter { convertToStatus(it.list_status.status) == MALApi.Companion.MalStatusType.Dropped }
                ?.toTypedArray())
            5 -> sortedMalList.postValue(fullMalList.value?.filter { convertToStatus(it.list_status.status) == MALApi.Companion.MalStatusType.PlanToWatch }
                ?.toTypedArray())
        }
    }
}