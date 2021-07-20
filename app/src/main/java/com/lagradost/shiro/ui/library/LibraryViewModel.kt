package com.lagradost.shiro.ui.library

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.lagradost.shiro.utils.MALApi
import com.lagradost.shiro.utils.MALApi.Companion.convertToStatus

const val DEFAULT_SORT = 0
const val ALPHA_SORT = 1
const val REVERSE_ALPHA_SORT = 2
const val SCORE_SORT = 3
const val SCORE_SORT_REVERSED = 4
const val RANK_SORT = 5
const val RANK_SORT_REVERSED = 6


class LibraryViewModel : ViewModel() {
    val sortedMalList = MutableLiveData<Array<Array<MALApi.Companion.Data>>>()
    val sortMethods = mutableListOf(1, 1, 1, 1, 1, 1)

    /**
    "All Anime",
    "Currently watching",
    "Completed",
    "On Hold",
    "Dropped",
    "Plan to Watch"
     **/

    fun sortNormalArray(
        array: Array<MALApi.Companion.Data>,
        sortMethod: Int? = null,
    ): Array<MALApi.Companion.Data> {

        return when (sortMethod) {
            DEFAULT_SORT -> {
                array.sortBy { t -> t.node.title }
                array
            }
            ALPHA_SORT -> {
                array.sortBy { t -> t.node.title }
                array
            }
            REVERSE_ALPHA_SORT -> {
                array.sortBy { t -> t.node.title }
                array.reverse()
                array
            }
            SCORE_SORT -> {
                array.sortBy { -it.node.my_list_status.score }
                array
            }
            SCORE_SORT_REVERSED -> {
                array.sortBy { it.node.my_list_status.score }
                array
            }
            RANK_SORT -> {
                array.sortBy { if (it.node.rank == 0) Int.MAX_VALUE else it.node.rank }
                array
            }
            RANK_SORT_REVERSED -> {
                array.sortBy { -it.node.rank }
                array
            }
            else -> array
        }

    }

    fun updateList(list: Array<MALApi.Companion.Data>) {
        sortedMalList.postValue(
            arrayOf(
                sortNormalArray(list, sortMethods[0]),
                sortNormalArray(list.filter { convertToStatus(it.node.my_list_status.status) == MALApi.Companion.MalStatusType.Watching }
                    .toTypedArray(), sortMethods[1]),
                sortNormalArray(list.filter { convertToStatus(it.node.my_list_status.status) == MALApi.Companion.MalStatusType.Completed }
                    .toTypedArray(), sortMethods[2]),
                sortNormalArray(list.filter { convertToStatus(it.node.my_list_status.status) == MALApi.Companion.MalStatusType.OnHold }
                    .toTypedArray(), sortMethods[3]),
                sortNormalArray(list.filter { convertToStatus(it.node.my_list_status.status) == MALApi.Companion.MalStatusType.Dropped }
                    .toTypedArray(), sortMethods[4]),
                sortNormalArray(list.filter { convertToStatus(it.node.my_list_status.status) == MALApi.Companion.MalStatusType.PlanToWatch }
                    .toTypedArray(), sortMethods[5]),
            )
        )
    }
}