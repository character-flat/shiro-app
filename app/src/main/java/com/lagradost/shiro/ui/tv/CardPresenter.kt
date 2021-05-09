package com.lagradost.shiro.ui.tv

import android.graphics.drawable.Drawable
import android.text.TextUtils
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import androidx.core.content.ContextCompat
import android.util.Log
import android.view.ViewGroup
import android.widget.TextView

import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.lagradost.shiro.R
import com.lagradost.shiro.utils.ShiroApi
import com.lagradost.shiro.utils.ShiroApi.Companion.getFullUrlCdn
import com.lagradost.shiro.ui.GlideApp
import com.lagradost.shiro.utils.AppApi.fixCardTitle
import com.lagradost.shiro.utils.AppApi.getColorFromAttr
import kotlin.properties.Delegates

/**
 * A CardPresenter is used to generate Views and bind Objects to them on demand.
 * It contains an ImageCardView.
 */
class CardPresenter : Presenter() {
    private var mDefaultCardImage: Drawable? = null
    private var sSelectedBackgroundColor: Int by Delegates.notNull()
    private var sDefaultBackgroundColor: Int by Delegates.notNull()

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {

        sDefaultBackgroundColor = parent.context.getColorFromAttr(R.attr.backgroundLight)
        sSelectedBackgroundColor = parent.context.getColorFromAttr(R.attr.colorPrimaryDarker)
        mDefaultCardImage = ContextCompat.getDrawable(parent.context, R.drawable.movie)

        val cardView = object : ImageCardView(parent.context) {
            override fun setSelected(selected: Boolean) {
                updateCardBackgroundColor(this, selected)
                super.setSelected(selected)
            }
        }

        cardView.isFocusable = true
        cardView.isFocusableInTouchMode = true
        updateCardBackgroundColor(cardView, false)
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val data = item as ShiroApi.CommonAnimePage
        val cardView = viewHolder.view as ImageCardView

        cardView.titleText = fixCardTitle(data.name)
        //cardView.contentText = movie.japanese
        cardView.setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT)

        GlideApp.with(viewHolder.view.context)
            .load(getFullUrlCdn(data.image))
            .transition(DrawableTransitionOptions.withCrossFade(100))
            .centerCrop()
            .into(cardView.mainImageView)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = viewHolder.view as ImageCardView
        // Remove references to images so that the garbage collector can free up memory
        cardView.badgeImage = null
        cardView.mainImage = null
    }

    private fun updateCardBackgroundColor(view: ImageCardView, selected: Boolean) {
        val color = if (selected) sSelectedBackgroundColor else sDefaultBackgroundColor
        // Both background colors should be set because the view"s background is temporarily visible
        // during animations.
        view.setBackgroundColor(color)
        view.setInfoAreaBackgroundColor(color)
        val textView = view.findViewById<TextView?>(R.id.title_text)
        //textView.isSingleLine = true
        if (selected) {
            // https://developer.android.com/reference/android/widget/TextView#attr_android:ellipsize
            textView?.ellipsize = TextUtils.TruncateAt.MARQUEE
            //textView.maxLines = 2
        } else {
            textView?.ellipsize = TextUtils.TruncateAt.END
            //textView.maxLines = 1
        }
    }


    companion object {
        private const val CARD_WIDTH = 175
        private const val CARD_HEIGHT = 200
    }
}