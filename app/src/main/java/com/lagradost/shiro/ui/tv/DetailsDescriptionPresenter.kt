package com.lagradost.shiro.ui.tv

import androidx.leanback.widget.AbstractDetailsDescriptionPresenter
import com.lagradost.shiro.utils.ShiroApi

class DetailsDescriptionPresenter : AbstractDetailsDescriptionPresenter() {

    override fun onBindDescription(
        viewHolder: ViewHolder,
        item: Any
    ) {
        val movie = item as ShiroApi.CommonAnimePage
        viewHolder.title.text = movie.name
    }
}