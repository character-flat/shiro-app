package com.lagradost.shiro.ui.result

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Html
import android.transition.ChangeBounds
import android.transition.Transition
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.AbsListView.CHOICE_MODE_SINGLE
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.mediarouter.app.MediaRouteButton
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions.bitmapTransform
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import com.jaredrummler.cyanea.Cyanea
import com.lagradost.shiro.R
import com.lagradost.shiro.ui.BookmarkedTitle
import com.lagradost.shiro.ui.GlideApp
import com.lagradost.shiro.ui.MainActivity
import com.lagradost.shiro.ui.WebViewFragment.Companion.onWebViewNavigated
import com.lagradost.shiro.ui.home.ExpandedHomeFragment.Companion.isInExpandedView
import com.lagradost.shiro.ui.home.HomeFragment.Companion.homeViewModel
import com.lagradost.shiro.ui.player.PlayerFragment.Companion.isInPlayer
import com.lagradost.shiro.ui.player.PlayerFragment.Companion.onPlayerNavigated
import com.lagradost.shiro.ui.toPx
import com.lagradost.shiro.ui.tv.TvActivity.Companion.tvActivity
import com.lagradost.shiro.utils.*
import com.lagradost.shiro.utils.AniListApi.Companion.fromIntToAnimeStatus
import com.lagradost.shiro.utils.AniListApi.Companion.getDataAboutId
import com.lagradost.shiro.utils.AniListApi.Companion.getShowId
import com.lagradost.shiro.utils.AniListApi.Companion.postDataAboutId
import com.lagradost.shiro.utils.AniListApi.Companion.secondsToReadable
import com.lagradost.shiro.utils.AppUtils.canPlayNextEpisode
import com.lagradost.shiro.utils.AppUtils.dubbify
import com.lagradost.shiro.utils.AppUtils.expandTouchArea
import com.lagradost.shiro.utils.AppUtils.getColorFromAttr
import com.lagradost.shiro.utils.AppUtils.getCurrentActivity
import com.lagradost.shiro.utils.AppUtils.getLatestSeenEpisode
import com.lagradost.shiro.utils.AppUtils.getNavigationBarSize
import com.lagradost.shiro.utils.AppUtils.getTextColor
import com.lagradost.shiro.utils.AppUtils.getViewPosDur
import com.lagradost.shiro.utils.AppUtils.hideKeyboard
import com.lagradost.shiro.utils.AppUtils.isCastApiAvailable
import com.lagradost.shiro.utils.AppUtils.loadPlayer
import com.lagradost.shiro.utils.AppUtils.observe
import com.lagradost.shiro.utils.AppUtils.openBrowser
import com.lagradost.shiro.utils.AppUtils.popCurrentPage
import com.lagradost.shiro.utils.AppUtils.settingsManager
import com.lagradost.shiro.utils.MALApi.Companion.malStatusAsString
import com.lagradost.shiro.utils.MALApi.Companion.setScoreRequest
import com.lagradost.shiro.utils.ShiroApi.Companion.currentToken
import com.lagradost.shiro.utils.ShiroApi.Companion.getAnimePage
import com.lagradost.shiro.utils.ShiroApi.Companion.getFav
import com.lagradost.shiro.utils.ShiroApi.Companion.getFullUrlCdn
import com.lagradost.shiro.utils.ShiroApi.Companion.getSubbed
import com.lagradost.shiro.utils.ShiroApi.Companion.onTokenFetched
import jp.wasabeef.glide.transformations.BlurTransformation
import kotlinx.android.synthetic.main.bottom_sheet.*
import kotlinx.android.synthetic.main.fragment_results.*
import kotlinx.android.synthetic.main.fragment_results.aniList_progressbar
import kotlinx.android.synthetic.main.fragment_results.anilist_btt_holder
import kotlinx.android.synthetic.main.fragment_results.anilist_holder
import kotlinx.android.synthetic.main.fragment_results.anilist_progress_txt
import kotlinx.android.synthetic.main.fragment_results.edit_episodes_btt
import kotlinx.android.synthetic.main.fragment_results.rating_btt
import kotlinx.android.synthetic.main.fragment_results.rating_btt_holder
import kotlinx.android.synthetic.main.fragment_results.rating_text
import kotlinx.android.synthetic.main.fragment_results.status_btt
import kotlinx.android.synthetic.main.fragment_results.status_btt_holder
import kotlinx.android.synthetic.main.fragment_results.status_text
import kotlinx.android.synthetic.main.fragment_results.subscribe_holder
import kotlinx.android.synthetic.main.fragment_results.subscribe_image
import kotlinx.android.synthetic.main.fragment_results.title_anilist
import kotlinx.android.synthetic.main.fragment_results.title_anilist_holder
import kotlinx.android.synthetic.main.fragment_results.title_mal
import kotlinx.android.synthetic.main.fragment_results.title_mal_holder
import kotlinx.android.synthetic.main.fragment_results.title_subscribe_holder
import kotlinx.android.synthetic.main.fragment_results_new.*
import kotlinx.android.synthetic.main.fragment_results_new.bookmark_holder
import kotlinx.android.synthetic.main.fragment_results_new.episodes_res_view
import kotlinx.android.synthetic.main.fragment_results_new.language_button
import kotlinx.android.synthetic.main.fragment_results_new.loading_overlay
import kotlinx.android.synthetic.main.fragment_results_new.media_route_button
import kotlinx.android.synthetic.main.fragment_results_new.next_episode_btt
import kotlinx.android.synthetic.main.fragment_results_new.result_poster_blur
import kotlinx.android.synthetic.main.fragment_results_new.results_root
import kotlinx.android.synthetic.main.fragment_results_new.share_holder
import kotlinx.android.synthetic.main.fragment_results_new.title_background
import kotlinx.android.synthetic.main.fragment_results_new.title_bookmark
import kotlinx.android.synthetic.main.fragment_results_new.title_day_of_week
import kotlinx.android.synthetic.main.fragment_results_new.title_descript
import kotlinx.android.synthetic.main.fragment_results_new.title_episodes
import kotlinx.android.synthetic.main.fragment_results_new.title_genres
import kotlinx.android.synthetic.main.fragment_results_new.title_go_back
import kotlinx.android.synthetic.main.fragment_results_new.title_holder
import kotlinx.android.synthetic.main.fragment_results_new.title_name
import kotlinx.android.synthetic.main.fragment_results_new.title_status
import kotlinx.android.synthetic.main.fragment_results_new.title_year
import kotlinx.android.synthetic.main.number_picker_dialog.*
import kotlinx.android.synthetic.main.number_picker_dialog.episode_progress_btt
import kotlinx.android.synthetic.main.number_picker_dialog_tv.*
import java.util.*
import kotlin.concurrent.schedule
import kotlin.concurrent.thread


const val DESCRIPTION_LENGTH1 = 200
const val SLUG = "slug"
const val NAME = "name"
const val RESULT_FRAGMENT_TAG = "RESULT_FRAGMENT_TAG"

class ResultFragment : Fragment() {
    private var data: ShiroApi.AnimePageData? = null
    private var slug: String? = null
    private var fillerEpisodes: HashMap<Int, Boolean>? = null
    private var dataOther: ShiroApi.AnimePageData? = null
    private var isDefaultData = true
    private var isBookmarked = false
    private var isSubbed: Boolean? = null

    private var hasLoadedAnilist = false
    private var anilistPage: AniListApi.GetSearchMedia? = null

    companion object {
        //var lastSelectedEpisode = 0
        var isInResults: Boolean = false
        var isViewState: Boolean = true
        var resultViewModel: ResultsViewModel? = null
        val onResultsNavigated = Event<Boolean>()
        fun fixEpTitle(
            _title: String?,
            epNum: Int,
            isMovie: Boolean,
            formatBefore: Boolean = false,
        ): String {
            var title = _title
            if (title == null || title.replace(" ", "") == "") {
                title = "Episode $epNum"
            }
            if (!isMovie) {
                title = if (formatBefore) {
                    "E$epNum $title" //•
                } else {
                    "$epNum. $title"
                }
            }
            return title
        }

        fun newInstance(data: String, name: String) =
            ResultFragment().apply {
                arguments = Bundle().apply {
                    putString(SLUG, data)
                    putString(NAME, name)
                }
            }
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val useNewLayout = settingsManager!!.getBoolean("new_results_page", false)
        // TV has its own overlay
        val layout =
            if (tvActivity == null) (if (useNewLayout) R.layout.fragment_results_new else R.layout.fragment_results) else R.layout.fragment_results_tv
        resultViewModel = resultViewModel ?: ViewModelProvider(getCurrentActivity()!!).get(ResultsViewModel::class.java)

        return inflater.inflate(layout, container, false)

    }

    private var onLoaded = Event<Boolean>()
    private var onLoadedOther = Event<Boolean>()

    private fun onLoadOtherEvent(isSucc: Boolean) {
        activity?.runOnUiThread {
            val transition: Transition = ChangeBounds()
            transition.duration = 100
            language_button?.visibility = VISIBLE
            results_root?.findViewById<FrameLayout?>(R.id.language_button_holder)?.visibility = VISIBLE
            results_root?.let {
                TransitionManager.beginDelayedTransition(it, transition)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (tvActivity != null) {
            onWebViewNavigated += ::restoreState
        }
        onPlayerNavigated += ::handleVideoPlayerNavigation
       // DownloadManager.downloadStartEvent += ::onDownloadStarted
        isInResults = true
        onResultsNavigated.invoke(true)
    }

    private fun onLoadEvent(isSucc: Boolean) {
        if (isSucc) {
            val data = if (isDefaultData) data else dataOther
            activity?.runOnUiThread {
                if (data == null) {
                    Toast.makeText(activity, "Error loading anime page!", Toast.LENGTH_LONG).show()
                    activity?.popCurrentPage(isInPlayer, isInExpandedView, isInResults)
                    return@runOnUiThread
                }
                if (resultViewModel?.currentAniListId != null) {
                    thread {
                        loadGetDataAboutId()
                    }
                }
                val fadeAnimation = AlphaAnimation(1f, 0f)

                fadeAnimation.duration = 300
                fadeAnimation.isFillEnabled = true
                fadeAnimation.fillAfter = true
                loading_overlay?.startAnimation(fadeAnimation)
                loadSeason()

                thread {
                    val hasAniList = DataStore.getKey<String>(
                        ANILIST_TOKEN_KEY,
                        ANILIST_ACCOUNT_ID,
                        null
                    ) != null
                    val hasMAL = DataStore.getKey<String>(MAL_TOKEN_KEY, MAL_ACCOUNT_ID, null) != null

                    if (!hasLoadedAnilist && (hasAniList || hasMAL)) {
                        hasLoadedAnilist = true
                        anilistPage = getShowId(data.name, data.year?.toInt())
                        resultViewModel?.currentAniListId?.postValue(anilistPage?.id ?: 0)
                        resultViewModel?.currentMalId?.postValue(anilistPage?.idMal)
                    }
                }

                // Somehow the above animation doesn't trigger sometimes on lower android versions
                thread {
                    Timer().schedule(500) {
                        activity?.runOnUiThread {
                            loading_overlay?.alpha = 0f
                        }
                    }
                }

                val glideUrl =
                    GlideUrl(
                        getFullUrlCdn(data.image)
                    )

                context?.let {
                    val settingsManager = PreferenceManager.getDefaultSharedPreferences(it)
                    val savingData = settingsManager.getBoolean("data_saving", false)

                    GlideApp.with(it)
                        .load(glideUrl)
                        .transition(DrawableTransitionOptions.withCrossFade(200))
                        .onlyRetrieveFromCache(savingData)
                        .into(title_background)
                    GlideApp.with(it)
                        .load(glideUrl)
                        .transition(DrawableTransitionOptions.withCrossFade(200))
                        .apply(bitmapTransform(BlurTransformation(100, 3)))
                        .onlyRetrieveFromCache(savingData)
                        .into(result_poster_blur)
                }

                if (data.episodes?.isNotEmpty() == true) {
                    next_episode_btt.visibility = VISIBLE
                    next_episode_btt.setOnClickListener {
                        val lastNormal = getLatestSeenEpisode(data.dubbify(false))
                        val lastDubbed = getLatestSeenEpisode(data.dubbify(true))
                        val isEpisodeDubbed = lastDubbed.episodeIndex >= lastNormal.episodeIndex
                        val episode = if (isEpisodeDubbed) lastDubbed else lastNormal

                        val episodePos = getViewPosDur(data.slug, episode.episodeIndex)
                        val next = canPlayNextEpisode(data, episode.episodeIndex)
                        if (next.isFound && episodePos.viewstate) {
                            val pos = getViewPosDur(data.slug, episode.episodeIndex)
                            Toast.makeText(activity, "Playing episode ${next.episodeIndex + 1}", Toast.LENGTH_SHORT)
                                .show()
                            activity?.loadPlayer(
                                next.episodeIndex,
                                pos.pos,
                                data,
                                resultViewModel?.currentAniListId?.value,
                                resultViewModel?.currentMalId?.value,
                                fillerEpisodes
                            )
                        } else {
                            Toast.makeText(activity, "Playing episode ${episode.episodeIndex + 1}", Toast.LENGTH_SHORT)
                                .show()
                            activity?.loadPlayer(
                                episode.episodeIndex,
                                episodePos.pos,
                                data,
                                resultViewModel?.currentAniListId?.value,
                                resultViewModel?.currentMalId?.value,
                                fillerEpisodes
                            )
                        }
                    }
                } else {
                    next_episode_btt.visibility = GONE
                }


                val textColor = Integer.toHexString(getCurrentActivity()!!.getTextColor()).substring(2)
                val textColorGrey =
                    Integer.toHexString(getCurrentActivity()!!.getTextColor(true)).substring(2)
                if (data.status != null) {
                    // fromHtml is depreciated, but works on android 6 as opposed to the new
                    title_status.text =
                        Html.fromHtml(
                            "<font color=#${textColorGrey}>Status:</font><font color=#${textColor}> ${
                                data.status.replaceFirstChar {
                                    if (it.isLowerCase()) it.titlecase(
                                        Locale.getDefault()
                                    ) else it.toString()
                                }
                            }</font>"/*,
                            FROM_HTML_MODE_COMPACT*/
                        )
                } else {
                    title_status.visibility = GONE
                }
                isBookmarked = DataStore.containsKey(BOOKMARK_KEY, data.slug)
                toggleHeartVisual(isBookmarked)
                title_episodes.text =
                    Html.fromHtml(
                        "<font color=#${textColorGrey}>Episodes:</font><font color=#${textColor}> ${data.episodeCount}</font>"/*,
                        FROM_HTML_MODE_COMPACT*/
                    )

                if (data.year != null) {
                    title_year.text =
                        Html.fromHtml(
                            "<font color=#${textColorGrey}>Year:</font><font color=#${textColor}> ${data.year}</font>"/*,
                            FROM_HTML_MODE_COMPACT*/
                        )
                } else {
                    title_year.visibility = GONE
                }

                if (data.genres != null) {
                    title_genres.text =
                        Html.fromHtml(
                            "<font color=#${textColorGrey}>Genres:</font><font color=#${textColor}> ${
                                data.genres.joinToString(
                                    ", "
                                )
                            }</font>"/*,
                            FROM_HTML_MODE_COMPACT*/
                        )
                } else {
                    title_genres.visibility = GONE
                }

                activity?.displayDate()

                title_name.text = data.name
                val fullDescription = data.synopsis
                    .replace("<br>", "")
                    .replace("<i>", "")
                    .replace("</i>", "")
                    .replace("\n", " ")


                share_holder?.setOnClickListener {
                    val intent = Intent()
                    intent.action = Intent.ACTION_SEND
                    intent.putExtra(Intent.EXTRA_TEXT, "https://shiro.is/anime/${data.slug}")
                    intent.type = "text/plain"
                    startActivity(Intent.createChooser(intent, "Share To:"))
                }


                data.slug.let { slug ->
                    if (title_subscribe_holder == null) return@let
                    title_subscribe_holder?.visibility = VISIBLE
                    val subbedBookmark = DataStore.getKey<BookmarkedTitle>(SUBSCRIPTIONS_BOOKMARK_KEY, slug, null)
                    val isSubbedOld = DataStore.getKey(SUBSCRIPTIONS_KEY, slug, false)!!
                    val isSubbed = isSubbedOld || subbedBookmark != null

                    val drawable =
                        if (isSubbed) R.drawable.ic_baseline_notifications_active_24 else R.drawable.ic_baseline_notifications_none_24
                    subscribe_image?.setImageResource(drawable)
                    subscribe_holder?.setOnClickListener {
                        val subbedBookmark = DataStore.getKey<BookmarkedTitle>(SUBSCRIPTIONS_BOOKMARK_KEY, slug, null)
                        val isSubbedOld = DataStore.getKey(SUBSCRIPTIONS_KEY, slug, false)!!
                        val isSubbed = isSubbedOld || subbedBookmark != null

                        if (isSubbed) {
                            Firebase.messaging.unsubscribeFromTopic(slug)
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        subscribe_image?.setImageResource(R.drawable.ic_baseline_notifications_none_24)
                                        DataStore.removeKey(SUBSCRIPTIONS_BOOKMARK_KEY, slug)
                                        DataStore.removeKey(SUBSCRIPTIONS_KEY, slug)
                                    }
                                    var msg = "Unsubscribed to ${data.name}"//getString(R.string.msg_subscribed)
                                    if (!task.isSuccessful) {
                                        msg = "Unsubscribing failed :("//getString(R.string.msg_subscribe_failed)
                                    }
                                    thread {
                                        homeViewModel?.subscribed?.postValue(getSubbed())
                                    }
                                    //Log.d(TAG, msg)
                                    Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
                                }
                        } else {
                            Firebase.messaging.subscribeToTopic(slug)
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        subscribe_image?.setImageResource(R.drawable.ic_baseline_notifications_active_24)
                                        DataStore.setKey(
                                            SUBSCRIPTIONS_BOOKMARK_KEY, slug, BookmarkedTitle(
                                                data.name,
                                                data.image,
                                                data.slug,
                                                data.english
                                            )
                                        )
                                    }
                                    var msg = "Subscribed to ${data.name}"//getString(R.string.msg_subscribed)
                                    if (!task.isSuccessful) {
                                        msg = "Subscription failed :("//getString(R.string.msg_subscribe_failed)
                                    }
                                    thread {
                                        homeViewModel?.subscribed?.postValue(getSubbed())
                                    }
                                    //Log.d(TAG, msg)
                                    Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
                }


                // Somehow for an unknown reason (I haven't even found online) setting a textview with large amount of text
                // 'crashes' (hangs for a good while), so far only observed on samsung phones
                /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    title_descript.text = if (fullDescription.length > 200) Html.fromHtml(
                        fullDescription.substring(0, minOf(fullDescription.length, DESCRIPTION_LENGTH1 - 3)) +
                                "<font color=#${textColorGrey}>...<i> Read more</i></font>"/*,
                            FROM_HTML_MODE_COMPACT*/
                    ) else fullDescription
                    title_descript.setOnClickListener {
                        //val transition: Transition = ChangeBounds()
                        //transition.duration = 100
                        if (title_descript.text.length <= 200 + 13) {
                            title_descript.text = fullDescription
                        } else {
                            title_descript.text = Html.fromHtml(
                                fullDescription.substring(0, minOf(fullDescription.length, DESCRIPTION_LENGTH1 - 3)) +
                                        "<font color=#${textColorGrey}>...<i> Read more</i></font>"/*,
                        FROM_HTML_MODE_COMPACT*/
                            )
                        }
                        //TransitionManager.beginDelayedTransition(description_holder, transition)
                    }

                } else {
                    title_descript.text = fullDescription.substring(0, DESCRIPTION_LENGTH1 - 3) + "..."
                }*/
                title_descript.text =
                    fullDescription.substring(0, minOf(DESCRIPTION_LENGTH1 - 3, fullDescription.length)) + "..."
                title_descript.setOnClickListener {
                    if (activity != null) {
                        val builder: AlertDialog.Builder =
                            AlertDialog.Builder(requireActivity(), R.style.AlertDialogCustom)
                        builder.setMessage(fullDescription).setTitle("Synopsis")
                            .show()
                    }
                }
                /*var ratTxt = (data!!.averageScore / 10f).toString().replace(',', '.') // JUST IN CASE DUE TO LANG SETTINGS
                if (!ratTxt.contains('.')) ratTxt += ".0"
                title_rating.text = "Rated: $ratTxt"
                */
            }
        }
    }


    private fun loadGetDataAboutId() {
        try {
            activity?.let { activity ->
                val hasAniList = DataStore.getKey<String>(
                    ANILIST_TOKEN_KEY,
                    ANILIST_ACCOUNT_ID,
                    null
                ) != null
                val hasMAL = DataStore.getKey<String>(MAL_TOKEN_KEY, MAL_ACCOUNT_ID, null) != null

                val holder = if (hasAniList) resultViewModel?.currentAniListId?.value?.let {
                    activity.getDataAboutId(
                        it
                    )
                } else null
                val malHolder =
                    if (hasMAL && holder == null) resultViewModel?.currentMalId?.value?.let { MALApi.getDataAboutId(it) } else null
                //setAllMalData()
                //MALApi.allTitles.get(currentMalId)

                if (holder != null || malHolder != null) {
                    class CardAniListInfo {
                        // Sets to watching if anything is done
                        fun typeGetter(): AniListApi.Companion.AniListStatusType {
                            return if (holder != null) {
                                fromIntToAnimeStatus(holder.type.value)
                            } else {
                                fromIntToAnimeStatus(malStatusAsString.indexOf(malHolder?.my_list_status?.status ?: 0))
                            }
                            /*return if (malHolder != null) {
                                val type =
                                    fromIntToAnimeStatus(malStatusAsString.indexOf(malHolder.my_list_status?.status))
                                /*type =
                                    if (type.value == MALApi.Companion.MalStatusType.None.value) AniListApi.Companion.AniListStatusType.Watching else type*/
                                type
                            } else {
                                val type = holder?.type
                                /*val type =
                                    if (holder?.type == AniListApi.Companion.AniListStatusType.None) AniListApi.Companion.AniListStatusType.Watching else holder?.type*/
                                fromIntToAnimeStatus(type?.value ?: 0)
                            }*/
                        }

                        var type = typeGetter()
                            set(value) {
                                //field = value
                                println("Changed type")
                                field = fromIntToAnimeStatus(this.typeValue)
                            }

                        // This is helper class to type.value because setter on type.value isn't working.
                        var typeValue = type.value
                            set(value) {
                                field = value
                                // Invoke setter
                                // println("Invoked setter")
                                activity.runOnUiThread {
                                    status_text?.text = fromIntToAnimeStatus(this.typeValue).name
                                }
                                this::type.setter.call(type)
                            }
                        var progress =
                            holder?.progress ?: malHolder?.my_list_status?.num_episodes_watched ?: 0
                            set(value) {
                                field = maxOf(0, minOf(value, episodes))
                                getCurrentActivity()!!.runOnUiThread {
                                    aniList_progressbar.progress = field * 100 / episodes
                                    anilist_progress_txt.text = "${field}/${episodes}"
                                    status_text.text = type.name
                                }
                                if (typeValue == AniListApi.Companion.AniListStatusType.None.value) {
                                    typeValue = AniListApi.Companion.AniListStatusType.Watching.value
                                }
                                if (progress == episodes && typeValue != AniListApi.Companion.AniListStatusType.Completed.value) {
                                    activity.runOnUiThread {
                                        Toast.makeText(
                                            activity,
                                            "All episodes seen, marking as Completed",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                    typeValue = AniListApi.Companion.AniListStatusType.Completed.value
                                }
                                if (progress != episodes && typeValue == AniListApi.Companion.AniListStatusType.Completed.value) {
                                    activity.runOnUiThread {
                                        Toast.makeText(
                                            activity,
                                            "Marking as Watching",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                    typeValue = AniListApi.Companion.AniListStatusType.Watching.value
                                }
                                /*if (field == holder.episodes) {
                                    this.type.value = AniListStatusType.Completed.value
                                } else if (field != holder.episodes && this.type.value == AniListStatusType.Completed.value) {
                                    this.type.value = AniListStatusType.Watching.value
                                }*/
                            }
                        var score = holder?.score ?: malHolder?.my_list_status?.score ?: 0
                            set(value) {
                                field = value
                                if (typeValue == AniListApi.Companion.AniListStatusType.None.value) {
                                    typeValue = AniListApi.Companion.AniListStatusType.Watching.value
                                }
                                activity.runOnUiThread {
                                    rating_text.text = if (value == 0) "Rate" else value.toString()
                                    status_text.text = type.name
                                }
                            }
                        var episodes = holder?.episodes ?: malHolder?.num_episodes ?: 1

                        fun syncData() {
                            thread {
                                val anilistPost =
                                    if (hasAniList) resultViewModel?.currentAniListId?.value?.let {
                                        activity.postDataAboutId(
                                            it,
                                            fromIntToAnimeStatus(typeValue),
                                            score,
                                            progress
                                        )
                                    } else true
                                val malPost = if (hasMAL)
                                    resultViewModel?.currentMalId?.value?.let {
                                        setScoreRequest(
                                            it,
                                            MALApi.fromIntToAnimeStatus(typeValue),
                                            score,
                                            progress
                                        )
                                    } else true
                                if (!anilistPost!! || malPost?.not() == true) {
                                    activity.runOnUiThread {
                                        Toast.makeText(
                                            activity,
                                            "Error updating episode progress",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        }
                    }

                    val info = CardAniListInfo()
                    info.episodes = maxOf(
                        info.episodes,
                        data?.episodes?.size ?: 1
                    ) // TO REMOVE DIVIDE BY 0 ERROR
                    activity.runOnUiThread {
                        val transition: Transition = ChangeBounds()
                        transition.duration = 100 // DURATION OF ANIMATION IN MS
                        //sync_title.visibility = VISIBLE
                        anilist_holder?.visibility = VISIBLE
                        aniList_progressbar?.progress = info.progress * 100 / info.episodes
                        anilist_progress_txt?.text = "${info.progress}/${info.episodes}"

                        resultViewModel?.let { viewModel ->
                            observe(viewModel.visibleEpisodeProgress) {
                                it?.let {
                                    info.progress = it
                                }
                            }
                        }

                        anilist_btt_holder?.visibility = VISIBLE
                        status_text?.text =
                            if (info.type.value == AniListApi.Companion.AniListStatusType.None.value) "Status" else info.type.name
                        rating_text?.text = if (info.score == 0) "Rate" else info.score.toString()
                        title_holder?.let { TransitionManager.beginDelayedTransition(it, transition) }

                        edit_episodes_btt?.setOnClickListener {
                            val dialog = Dialog(activity, R.style.AlertDialogCustom)
                            dialog.setTitle("Select episodes seen")
                            if (tvActivity != null) {
                                //dialog.window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                                dialog.setContentView(R.layout.number_picker_dialog_tv)
                                dialog.current_progress?.text = info.progress.toString()

                                fun getProgress(): Int {
                                    return dialog.current_progress?.text?.toString()?.toInt() ?: info.progress
                                }

                                fun setProgress(diff: Int) {
                                    val progress = minOf(maxOf(getProgress() + diff, 0), info.episodes)
                                    dialog.current_progress?.text = progress.toString()
                                }

                                if (info.episodes > 100) {
                                    dialog.progress_plus_100.visibility = VISIBLE
                                    dialog.progress_minus_100.visibility = VISIBLE
                                    dialog.progress_plus_100.setOnClickListener { setProgress(100) }
                                    dialog.progress_minus_100.setOnClickListener { setProgress(-100) }
                                } else {
                                    dialog.progress_plus_100.visibility = GONE
                                    dialog.progress_minus_100.visibility = GONE
                                }
                                dialog.progress_plus_10.setOnClickListener { setProgress(10) }
                                dialog.progress_minus_10.setOnClickListener { setProgress(-10) }
                                dialog.progress_plus_1.setOnClickListener { setProgress(1) }
                                dialog.progress_minus_1.setOnClickListener { setProgress(-1) }

                                dialog.episode_progress_btt.setOnClickListener {
                                    thread {
                                        // Applying progress after is needed
                                        info.progress = getProgress()
                                        info.syncData()
                                        dialog.dismiss()
                                    }
                                }
                                dialog.show()
                            } else {
                                //dialog.window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                                dialog.setContentView(R.layout.number_picker_dialog)
                                dialog.number_picker_episode_text.setText(info.progress.toString())
                                dialog.number_picker_episode_up.setOnClickListener {
                                    val number =
                                        if (dialog.number_picker_episode_text.text.toString().toIntOrNull() == null
                                        ) 1 else minOf(
                                            dialog.number_picker_episode_text.text.toString().toInt() + 1,
                                            info.episodes
                                        )
                                    dialog.number_picker_episode_text.setText(number.toString())
                                }
                                dialog.number_picker_episode_down.setOnClickListener {
                                    val number =
                                        if (dialog.number_picker_episode_text.text.toString().toIntOrNull() == null
                                        ) 0 else maxOf(dialog.number_picker_episode_text.text.toString().toInt() - 1, 0)
                                    dialog.number_picker_episode_text.setText(number.toString())
                                }
                                dialog.episode_progress_btt.setOnClickListener {
                                    thread {
                                        val progress =
                                            if (dialog.number_picker_episode_text.text.toString().toIntOrNull() == null
                                            ) 0 else minOf(
                                                dialog.number_picker_episode_text.text.toString().toInt(),
                                                info.episodes
                                            )
                                        // Applying progress after is needed
                                        info.progress = progress
                                        info.syncData()
                                        dialog.dismiss()
                                    }
                                }
                                dialog.show()
                            }
                        }

                        // Expands touch hitbox
                        expandTouchArea(rating_btt_holder, rating_btt, 30.toPx)
                        expandTouchArea(status_btt_holder, status_btt, 30.toPx)
                        expandTouchArea(title_anilist_holder, title_anilist, 30.toPx)

                        rating_btt?.setOnClickListener {
                            val arrayAdapter = ArrayAdapter<String>(activity, R.layout.bottom_single_choice)
                            val choices = listOf(
                                "No rating",
                                "1 - Appalling",
                                "2 - Horrible",
                                "3 - Very bad",
                                "4 - Bad",
                                "5 - Average",
                                "6 - Fine",
                                "7 - Good",
                                "8 - Very good",
                                "9 - Great",
                                "10 - Masterpiece",
                            )
                            arrayAdapter.addAll(ArrayList(choices))

                            if (tvActivity != null) {
                                val builder = AlertDialog.Builder(activity, R.style.AlertDialogCustom)
                                builder.setTitle("Rating")

                                lateinit var dialog: Dialog
                                // Using the arrayAdapter here apparently fucks up highlighting. I don't know why
                                builder.setSingleChoiceItems(choices.toTypedArray(), info.score) { _, position ->
                                    info.score = position
                                    info.syncData()
                                    dialog.dismiss()
                                }
                                dialog = builder.create()
                                dialog.show()
                            } else {
                                val bottomSheetDialog = BottomSheetDialog(activity, R.style.AppBottomSheetDialogTheme)
                                bottomSheetDialog.setContentView(R.layout.bottom_sheet)
                                val res = bottomSheetDialog.findViewById<ListView>(R.id.sort_click)!!
                                res.choiceMode = CHOICE_MODE_SINGLE
                                res.adapter = arrayAdapter
                                res.setItemChecked(
                                    info.score,
                                    true
                                )
                                res.setOnItemClickListener { _, _, position, _ ->
                                    info.score = position
                                    info.syncData()
                                    bottomSheetDialog.dismiss()
                                }
                                bottomSheetDialog.main_text.text = "Rating"
                                bottomSheetDialog.show()
                            }
                        }

                        status_btt?.setOnClickListener {
                            val bottomSheetDialog = BottomSheetDialog(activity, R.style.AppBottomSheetDialogTheme)
                            bottomSheetDialog.setContentView(R.layout.bottom_sheet)
                            val res = bottomSheetDialog.findViewById<ListView>(R.id.sort_click)!!
                            val arrayAdapter = ArrayAdapter<String>(activity, R.layout.bottom_single_choice)
                            val choices = listOf(
                                "Watching",
                                "Completed",
                                "Paused",
                                "Dropped",
                                "Planning to watch",
                                "Rewatching"
                            ).subList(
                                // Rewatching doesn't exist on MAL
                                0, if (hasAniList) 6 else 5
                            )
                            arrayAdapter.addAll(ArrayList(choices))

                            if (tvActivity != null) {
                                val builder = AlertDialog.Builder(activity, R.style.AlertDialogCustom)
                                builder.setTitle("Status")

                                lateinit var dialog: Dialog
                                builder.setSingleChoiceItems(choices.toTypedArray(), info.typeValue) { _, position ->
                                    info.typeValue = position

                                    if (position == AniListApi.Companion.AniListStatusType.Completed.value) {
                                        info.progress = info.episodes
                                    }

                                    info.syncData()
                                    dialog.dismiss()
                                }
                                dialog = builder.create()
                                dialog.show()
                            } else {
                                res.choiceMode = CHOICE_MODE_SINGLE
                                res.adapter = arrayAdapter
                                res.setItemChecked(
                                    info.typeValue,
                                    true
                                )
                                res.setOnItemClickListener { _, _, position, _ ->
                                    info.typeValue = position

                                    if (position == AniListApi.Companion.AniListStatusType.Completed.value) {
                                        info.progress = info.episodes
                                    }

                                    info.syncData()
                                    bottomSheetDialog.dismiss()
                                }
                                bottomSheetDialog.main_text.text = "Status"
                                bottomSheetDialog.show()
                            }
                        }

                        title_anilist?.setOnClickListener {
                            activity.openBrowser("https://anilist.co/anime/${resultViewModel?.currentAniListId?.value}")
                        }

                        resultViewModel?.currentMalId?.value?.let { currentMalId ->
                            title_mal_holder?.visibility = VISIBLE
                            expandTouchArea(title_mal_holder, title_mal, 30.toPx)

                            title_mal?.setOnClickListener {
                                activity.openBrowser("https://myanimelist.net/anime/${currentMalId}")
                            }
                        }
                        activity.displayDate()

                    }
                }
            }
        } catch (e: Exception) {
            println("ERROR LOADING ID ${resultViewModel?.currentAniListId?.value}")
        }
    }


    private fun Context.displayDate() {
        val textColor = Integer.toHexString(getCurrentActivity()!!.getTextColor()).substring(2)
        val textColorGrey =
            Integer.toHexString(getCurrentActivity()!!.getTextColor(true)).substring(2)
        if (anilistPage?.nextAiringEpisode != null) {
            anilistPage?.nextAiringEpisode?.let { airingEpisode ->
                title_day_of_week?.visibility = VISIBLE
                if (data?.schedule != null) {
                    data?.schedule?.let {
                        title_day_of_week?.text =
                            Html.fromHtml(
                                "<font color=#${textColorGrey}>Schedule:</font><font color=#${textColor}> ${
                                    // Capitalize
                                    it.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                                } - ${secondsToReadable(airingEpisode.timeUntilAiring, "Now")}</font>"/*,
                            FROM_HTML_MODE_COMPACT*/
                            )
                    }
                } else {
                    title_day_of_week?.text =
                        Html.fromHtml(
                            "<font color=#${textColorGrey}>Schedule:</font><font color=#${textColor}> ${
                                secondsToReadable(airingEpisode.timeUntilAiring, "Now")
                            }</font>"/*,
                            FROM_HTML_MODE_COMPACT*/
                        )
                }
            }
        } else if (data?.schedule != null && data?.status != "finished") {
            title_day_of_week?.text =
                Html.fromHtml(
                    "<font color=#${textColorGrey}>Schedule:</font><font color=#${textColor}> ${
                        // Capitalize
                        data?.schedule?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                    }</font>"/*,
                            FROM_HTML_MODE_COMPACT*/
                )
        } else {
            title_day_of_week?.visibility = GONE
        }

    }

    private fun initData() {
        slug = data?.slug
        onLoaded.invoke(true)
        isSubbed = slug?.endsWith("-dubbed")?.not()

        dataOther = if (isSubbed == true) {
            // Manual override for naruto shippuden
            data?.let { it1 -> getAnimePage(it1.slug.replace("shippuden", "shippuuden") + "-dubbed")?.data }
        } else {
            data?.let { it1 ->
                getAnimePage(
                    it1.slug.replace("shippuuden", "shippuden").removeSuffix("-dubbed")
                )?.data
            }
        }
        if (dataOther != null) {
            onLoadedOther.invoke(true)
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        arguments?.getString(SLUG)?.let { slug ->
            thread {
                if (currentToken != null) {
                    data = getAnimePage(slug)?.data
                    initData()
                } else {
                    onTokenFetched += ::loadDataWhenTokenIsLoaded
                }
            }
        }


        //isMovie = data!!.episodes == 1 && data!!.status == "FINISHED"
    }

    private fun loadDataWhenTokenIsLoaded(bool: Boolean) {
        arguments?.getString(SLUG)?.let { slug ->
            thread {
                data = getAnimePage(slug)?.data
                initData()
            }
        }
        onTokenFetched -= ::loadDataWhenTokenIsLoaded
    }
    /*
    private fun ToggleViewState(_isViewState: Boolean) {
        isViewState = _isViewState
        if (isViewState) {
            title_viewstate.setImageResource(R.drawable.filled_viewstate)
        } else {
            title_viewstate.setImageResource(R.drawable.outlined_viewstate)
        }
    }*/

    private fun toggleHeartVisual(_isBookmarked: Boolean) {
        if (_isBookmarked) {
            title_bookmark?.setImageResource(R.drawable.filled_heart)
            title_bookmark?.imageTintList = ColorStateList.valueOf(Cyanea.instance.primary)
        } else {
            title_bookmark?.setImageResource(R.drawable.outlined_heart)
            title_bookmark?.imageTintList =
                ColorStateList.valueOf(getCurrentActivity()!!.getColorFromAttr(R.attr.white))
        }
    }

    private fun toggleHeart(_isBookmarked: Boolean) {
        this.isBookmarked = _isBookmarked
        toggleHeartVisual(_isBookmarked)
        val data = (if (isDefaultData) data else dataOther) ?: return
        /*Saving the new bookmark in the database*/
        if (_isBookmarked) {
            DataStore.setKey(
                BOOKMARK_KEY,
                data.slug,
                BookmarkedTitle(
                    data.name,
                    data.image,
                    data.slug,
                    data.english
                )
            )
        } else {
            DataStore.removeKey(BOOKMARK_KEY, data.slug)
        }
        thread {
            homeViewModel!!.favorites.postValue(getFav())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            slug = savedInstanceState.getString(SLUG)
            thread {
                data = slug?.let { getAnimePage(it)?.data }
                initData()
            }
        }
        super.onCreate(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (data != null) {
            outState.putString(SLUG, data!!.slug)
        }
        super.onSaveInstanceState(outState)
    }

    private fun loadSeason() {
        settingsManager!!.getBoolean("save_history", true)
        val data = if (isDefaultData) data else dataOther
        if (data?.episodes?.isNotEmpty() == true) {
            if (episodes_res_view?.adapter == null) {
                val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>? = activity?.let {
                    MasterEpisodeAdapter(
                        it,
                        data,
                        fillerEpisodes
                    )
                }
                episodes_res_view.adapter = adapter
                (episodes_res_view.adapter as MasterEpisodeAdapter).notifyDataSetChanged()
            } else {
                (episodes_res_view.adapter as MasterEpisodeAdapter).data = data
                (episodes_res_view.adapter as MasterEpisodeAdapter).items = generateItems(data.episodes!!, data.slug)
                (episodes_res_view.adapter as MasterEpisodeAdapter).isFiller = fillerEpisodes
                (episodes_res_view.adapter as MasterEpisodeAdapter).notifyDataSetChanged()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isInResults = false
        hasLoadedAnilist = false
        onResultsNavigated.invoke(false)
        resultViewModel?.currentAniListId?.postValue(null)
        resultViewModel?.currentMalId?.postValue(null)
        resultViewModel?.visibleEpisodeProgress?.postValue(null)

        onWebViewNavigated -= ::restoreState
        onPlayerNavigated -= ::handleVideoPlayerNavigation
       // DownloadManager.downloadStartEvent -= ::onDownloadStarted
        onLoadedOther -= ::onLoadOtherEvent
        onLoaded -= ::onLoadEvent
    }

    private fun handleVideoPlayerNavigation(hasEntered: Boolean) {
        if (hasEntered) {
            this.view?.visibility = GONE
        } else {
            this.view?.visibility = VISIBLE
            (episodes_res_view?.adapter as? MasterEpisodeAdapter)?.notifyDataSetChanged()
        }
        //loadSeason()
        /*if (tvActivity != null) {
            title_season_cards.requestFocus()
            //title_season_cards.layoutManager?.scrollToPosition(lastSelectedEpisode)
            //title_season_cards.adapter?.notifyItemChanged(lastSelectedEpisode)
            //title_season_cards.layoutManager?.findViewByPosition(lastSelectedEpisode)?.requestFocus()
        }*/
    }

    private fun onDownloadStarted(id: String) {
        activity?.runOnUiThread {
            // Cast failure when going out of the page, making it catch to fully stop any of those crashes
            try {
                (episodes_res_view.adapter as MasterEpisodeAdapter).notifyDataSetChanged()
            } catch (e: java.lang.NullPointerException) {
            }
        }
    }


    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ORIENTATION_PORTRAIT not working for some reason so I chose this
        if (!settingsManager!!.getBoolean("force_landscape", false)) {
            val navBarSize = getCurrentActivity()!!.getNavigationBarSize()
            val min = minOf(navBarSize.y, navBarSize.x)
            fragments_new_nav_view?.setPadding(0, 0, 0, min)
        }

        title_holder.backgroundTintList = ColorStateList.valueOf(
            Cyanea.instance.backgroundColor
        )
        loading_overlay.background = ColorDrawable(Cyanea.instance.backgroundColor)

        hideKeyboard()
        //title_duration.text = data!!.duration.toString() + "min"
        if (activity?.isCastApiAvailable() == true) {
            val mMediaRouteButton = view.findViewById<MediaRouteButton>(R.id.media_route_button)

            CastButtonFactory.setUpMediaRouteButton(activity, mMediaRouteButton)
            val castContext = CastContext.getSharedInstance(requireActivity().applicationContext)

            if (castContext.castState != CastState.NO_DEVICES_AVAILABLE) media_route_button?.visibility = VISIBLE
            castContext.addCastStateListener { state ->
                if (media_route_button != null) {
                    if (state == CastState.NO_DEVICES_AVAILABLE) media_route_button.visibility = GONE else {
                        if (media_route_button.visibility == GONE) media_route_button.visibility = VISIBLE
                    }
                }
            }
        }
        observe(resultViewModel!!.currentAniListId) {
            thread {
                loadGetDataAboutId()
            }
        }
        //isViewState = false

        results_root.setPadding(0, MainActivity.statusHeight, 0, 0)
        //media_route_button_holder.setPadding(0, MainActivity.statusHeight, 0, 0)
        //media_route_button.layoutParams = LinearLayout.LayoutParams(20.toPx, 20.toPx + MainActivity.statusHeight)  //setPadding(0, MainActivity.statusHeight, 0, 0)
        title_go_back.setOnClickListener {
            activity?.onBackPressed()
        }

        bookmark_holder.setOnClickListener {
            toggleHeart(!isBookmarked)
        }

        language_button.setOnClickListener {
            if (dataOther != null) {
                isDefaultData = !isDefaultData
                onLoadEvent(true)
            }
        }
        /*
        title_viewstate.setOnClickListener {
            ToggleViewState(!isViewState)
        }*/

        view.setOnTouchListener { _, _ -> return@setOnTouchListener true } // VERY IMPORTANT https://stackoverflow.com/questions/28818926/prevent-clicking-on-a-button-in-an-activity-while-showing-a-fragment

        title_holder.setPadding(
            title_holder.paddingLeft,
            MainActivity.statusHeight, //+ title_background.minimumHeight - 44.toPx,
            title_holder.paddingRight,
            0,
        )

        /*if (data!!.trailer != null) {
            title_background.setOnLongClickListener {
                Toast.makeText(context, data!!.title.english + " - Trailer", Toast.LENGTH_SHORT).show()
                return@setOnLongClickListener true
            }

            title_background.setOnClickListener() {
                MainActivity.loadPlayer(
                    data!!.title.english + " - Trailer",
                    "https://fastani.net/" + data!!.trailer!!,
                    null
                )
            }
        } else {
        }
        title_trailer_btt.alpha = 0f
    */
        val localName = arguments?.getString(NAME)
        if (localName != null && settingsManager?.getBoolean("search_for_filler_episodes", true) == true) {
            thread {
                fillerEpisodes =
                    FillerEpisodeCheck.getFillerEpisodes(localName)
                activity?.runOnUiThread {
                    try {
                        if (episodes_res_view.adapter != null) {
                            (episodes_res_view.adapter as MasterEpisodeAdapter).isFiller = fillerEpisodes
                            (episodes_res_view.adapter as MasterEpisodeAdapter).notifyDataSetChanged()
                        }
                    } catch (e: java.lang.NullPointerException) {
                        e.printStackTrace()
                    }
                }
            }
        }

        onLoadedOther += ::onLoadOtherEvent
        onLoaded += ::onLoadEvent
    }

    private fun restoreState(hasEntered: Boolean) {
        if (tvActivity != null) {
            if (hasEntered) {
                this.view?.visibility = GONE
            } else {
                this.view?.visibility = VISIBLE
            }
        }
    }
}
