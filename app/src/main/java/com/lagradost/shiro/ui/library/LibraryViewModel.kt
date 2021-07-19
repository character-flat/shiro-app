package com.lagradost.shiro.ui.library

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.lagradost.shiro.utils.MALApi
import com.lagradost.shiro.utils.MALApi.Companion.fromIntToAnimeStatus
import com.lagradost.shiro.utils.MALApi.Companion.malStatusAsString

class LibraryViewModel : ViewModel() {
    val sortedMalList = MutableLiveData<Array<Array<MALApi.Companion.Data>>>()

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

    fun updateList(list: Array<MALApi.Companion.Data>) {
        sortedMalList.postValue(
            arrayOf(
                list,
                list.filter { convertToStatus(it.list_status.status) == MALApi.Companion.MalStatusType.Watching }
                    .toTypedArray(),
                list.filter { convertToStatus(it.list_status.status) == MALApi.Companion.MalStatusType.Completed }
                    .toTypedArray(),
                list.filter { convertToStatus(it.list_status.status) == MALApi.Companion.MalStatusType.OnHold }
                    .toTypedArray(),
                list.filter { convertToStatus(it.list_status.status) == MALApi.Companion.MalStatusType.Dropped }
                    .toTypedArray(),
                list.filter { convertToStatus(it.list_status.status) == MALApi.Companion.MalStatusType.PlanToWatch }
                    .toTypedArray(),
            )
        )
    }
}