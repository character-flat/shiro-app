package com.lagradost.shiro.ui.tv

import androidx.leanback.widget.AbstractDetailsDescriptionPresenter
import com.lagradost.shiro.ShiroApi

class DetailsDescriptionPresenter : AbstractDetailsDescriptionPresenter() {

    override fun onBindDescription(
        viewHolder: AbstractDetailsDescriptionPresenter.ViewHolder,
        item: Any
    ) {
        val movie = item as ShiroApi.AnimePageData
        viewHolder.title.text = movie.name
        viewHolder.subtitle.text = movie.japanese
        viewHolder.body.text = movie.synopsis
    }
}