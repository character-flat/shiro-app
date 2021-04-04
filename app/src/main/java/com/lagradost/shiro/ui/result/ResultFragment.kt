package com.lagradost.shiro.ui.result

import android.content.Context
import android.os.Build
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
import android.widget.*
import androidx.core.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.mediarouter.app.MediaRouteButton
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.load.model.GlideUrl
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.CastStateListener
import com.lagradost.shiro.*
import com.lagradost.shiro.DataStore.mapper
import com.lagradost.shiro.ShiroApi.Companion.getAnimePage
import com.lagradost.shiro.ShiroApi.Companion.getFullUrlCdn
import com.lagradost.shiro.ShiroApi.Companion.requestHome
import com.lagradost.shiro.MainActivity.Companion.hideKeyboard
import com.lagradost.shiro.MainActivity.Companion.isCastApiAvailable
import com.lagradost.shiro.MainActivity.Companion.popCurrentPage
import com.lagradost.shiro.ui.GlideApp
import com.lagradost.shiro.ui.PlayerFragment
import kotlinx.android.synthetic.main.episode_result.view.*
import kotlinx.android.synthetic.main.fragment_results_new.*
import java.util.*
import kotlin.concurrent.schedule
import kotlin.concurrent.thread


const val DESCRIPTION_LENGTH1 = 200

class ResultFragment : Fragment() {
    var data: ShiroApi.AnimePageData? = null
    var dataOther: ShiroApi.AnimePageData? = null
    var isDefaultData = true

    private lateinit var resultViewModel: ResultViewModel
    private var isMovie: Boolean = false
    var isBookmarked = false
    var isSubbed: Boolean? = null


    companion object {
        var isInResults: Boolean = false
        var isViewState: Boolean = true
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
                if (formatBefore) {
                    title = "E$epNum $title" //•
                } else {
                    title = "$epNum. $title"
                }
            }
            return title
        }

        fun newInstance(data: ShiroApi.ShiroSearchResponseShow) =
            ResultFragment().apply {
                arguments = Bundle().apply {
                    //println(data)
                    putString("ShiroSearchResponseShow", mapper.writeValueAsString(data))
                }
            }

        fun newInstance(data: ShiroApi.AnimePageData) =
            ResultFragment().apply {
                arguments = Bundle().apply {
                    //println(data)
                    putString("AnimePageData", mapper.writeValueAsString(data))
                }
            }

        /*Creating a new Instance of the given data*/
        fun newInstance(data: BookmarkedTitle) =
            ResultFragment().apply {
                arguments = Bundle().apply {
                    //println(data)
                    putString("BookmarkedTitle", mapper.writeValueAsString(data))
                }
            }
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        resultViewModel =
            activity?.let { ViewModelProviders.of(it).get(ResultViewModel::class.java) }!!

        return inflater.inflate(R.layout.fragment_results_new, container, false)
    }

    var onLoaded = Event<Boolean>()
    var onLoadedOther = Event<Boolean>()

    private fun onLoadOtherEvent(isSucc: Boolean) {
        activity?.runOnUiThread {
            val transition: Transition = ChangeBounds()
            transition.duration = 100

            language_button.visibility = VISIBLE
            TransitionManager.beginDelayedTransition(episodes_text_holder, transition)
        }
    }


    private fun onLoadEvent(isSucc: Boolean) {
        if (isSucc) {
            val data = if (isDefaultData) data else dataOther
            activity?.runOnUiThread {
                if (data == null) {
                    Toast.makeText(activity, "Error loading anime page!", Toast.LENGTH_LONG).show()
                    popCurrentPage()
                    return@runOnUiThread
                }
                val fadeAnimation = AlphaAnimation(1f, 0f)

                fadeAnimation.duration = 300
                fadeAnimation.isFillEnabled = true
                fadeAnimation.fillAfter = true
                loading_overlay.startAnimation(fadeAnimation)
                loadSeason()

                // Somehow the above animation doesn't trigger sometimes on lower android versions
                thread {
                    Timer().schedule(500){
                        activity?.runOnUiThread {
                            loading_overlay.alpha = 0f
                        }
                    }
                }

                val glideUrl =
                    GlideUrl(
                        getFullUrlCdn(data.image)
                    )
                context?.let {
                    GlideApp.with(it)
                        .load(glideUrl)
                        .into(title_background)
                }

                val textColor = resources.getString(R.color.textColor).substring(3)
                val textColorGrey = resources.getString(R.color.textColorGray).substring(3)
                if (data.status != null) {
                    // fromHtml is depreciated, but works on android 6 as opposed to the new
                    title_status.text =
                        Html.fromHtml(
                            "<font color=#${textColor}>Status:</font><font color=#${textColorGrey}> ${data.status}</font>"/*,
                            FROM_HTML_MODE_COMPACT*/
                        )
                } else {
                    title_status.visibility = GONE
                }
                isBookmarked = DataStore.containsKey(BOOKMARK_KEY, data.slug)
                toggleHeartVisual(isBookmarked)
                title_episodes.text =
                    Html.fromHtml(
                        "<font color=#${textColor}>Episodes:</font><font color=#${textColorGrey}> ${data.episodeCount}</font>"/*,
                        FROM_HTML_MODE_COMPACT*/
                    )

                if (data.year != null) {
                    title_year.text =
                        Html.fromHtml(
                            "<font color=#${textColor}>Year:</font><font color=#${textColorGrey}> ${data.year}</font>"/*,
                            FROM_HTML_MODE_COMPACT*/
                        )
                } else {
                    title_year.visibility = GONE
                }

                if (data.genres != null) {
                    title_genres.text =
                        Html.fromHtml(
                            "<font color=#${textColor}>Status:</font><font color=#${textColorGrey}> ${
                                data.genres?.joinToString(
                                    ", "
                                )
                            }</font>"/*,
                            FROM_HTML_MODE_COMPACT*/
                        )
                } else {
                    title_genres.visibility = GONE
                }

                if (data.schedule != null && data.status != "finished") {
                    title_day_of_week.text =
                        Html.fromHtml(
                            "<font color=#${textColor}>Schedule:</font><font color=#${textColorGrey}> ${
                                data.schedule
                            }</font>"/*,
                            FROM_HTML_MODE_COMPACT*/
                        )
                } else {
                    title_day_of_week.visibility = GONE
                }

                title_name.text = data.name
                val fullDescription = data.synopsis
                    .replace("<br>", "")
                    .replace("<i>", "")
                    .replace("</i>", "")
                    .replace("\n", " ")

                // Somehow for an unknown reason (I haven't even found online) setting a textview with large amount of text
                // 'crashes' (hangs for a good while) the application on low android versions. I'm betting Nougat+ works.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
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
                }

                /*var ratTxt = (data!!.averageScore / 10f).toString().replace(',', '.') // JUST IN CASE DUE TO LANG SETTINGS
                if (!ratTxt.contains('.')) ratTxt += ".0"
                title_rating.text = "Rated: $ratTxt"
                */
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        println("Attached result fragment")
        arguments?.getString("ShiroSearchResponseShow")?.let {
            thread {
                data = getAnimePage(mapper.readValue(it, ShiroApi.ShiroSearchResponseShow::class.java))?.data
                onLoaded.invoke(true)
                isSubbed = data?.slug?.endsWith("-dubbed")?.not()

                dataOther = if (isSubbed == true) {
                    data?.let { it1 -> getAnimePage(it1.slug + "-dubbed")?.data }
                } else {
                    data?.let { it1 -> getAnimePage(it1.slug.substring(0, it1.slug.length - 7))?.data }
                }
                if (dataOther != null) {
                    onLoadedOther.invoke(true)
                }
            }
        }
        // Kinda hacky solution, but works
        arguments?.getString("AnimePageData")?.let {
            thread {
                val pageData = mapper.readValue(it, ShiroApi.AnimePageData::class.java)
                println("DATA $pageData")
                data = getAnimePage(
                    pageData.slug
                )?.data
                isSubbed = data?.slug?.endsWith("-dubbed")?.not()
                onLoaded.invoke(true)

                dataOther = if (isSubbed == true) {
                    data?.let { it1 -> getAnimePage(it1.slug + "-dubbed")?.data }
                } else {
                    data?.let { it1 -> getAnimePage(it1.slug.substring(0, it1.slug.length - 7))?.data }
                }
                if (dataOther != null) {
                    onLoadedOther.invoke(true)
                }


            }
        }

        /*Calling the getAnimePage function to get the page*/
        arguments?.getString("BookmarkedTitle")?.let {
            thread {
                data = getAnimePage(mapper.readValue(it, BookmarkedTitle::class.java))?.data
                isSubbed = data?.slug?.endsWith("-dubbed")?.not()
                onLoaded.invoke(true)

                dataOther = if (isSubbed == true) {
                    data?.let { it1 -> getAnimePage(it1.slug + "-dubbed")?.data }
                } else {
                    data?.let { it1 -> getAnimePage(it1.slug.substring(0, it1.slug.length - 7))?.data }
                }
                if (dataOther != null) {
                    onLoadedOther.invoke(true)
                }
            }
        }
        //isMovie = data!!.episodes == 1 && data!!.status == "FINISHED"
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
            title_bookmark.setImageResource(R.drawable.filled_heart)
        } else {
            title_bookmark.setImageResource(R.drawable.outlined_heart)
        }
    }

    private fun toggleHeart(_isBookmarked: Boolean) {
        this.isBookmarked = _isBookmarked
        toggleHeartVisual(_isBookmarked)
        val data = (if (isDefaultData) data else dataOther) ?: return
        /*Saving the new bookmark in the database*/
        if (_isBookmarked) {
            DataStore.setKey<BookmarkedTitle>(
                BOOKMARK_KEY,
                data.slug,
                BookmarkedTitle(
                    data.name,
                    data.image,
                    data.slug
                )
            )
        } else {
            DataStore.removeKey(BOOKMARK_KEY, data.slug)
        }
        thread {
            requestHome(true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        println("onCreate")
        if (savedInstanceState != null) {
            data = savedInstanceState.getString("data")?.let { mapper.readValue(it) }
            dataOther = savedInstanceState.getString("dataOther")?.let { mapper.readValue(it) }
        }
        if (data != null) {
            onLoaded.invoke(true)
        }
        super.onCreate(savedInstanceState)

    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (data != null) {
            outState.putString("data", mapper.writeValueAsString(data))
        }
        if (dataOther != null) {
            outState.putString("dataOther", mapper.writeValueAsString(data))
        }
        super.onSaveInstanceState(outState)
    }

    private fun loadSeason() {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(MainActivity.activity)
        val save = settingsManager.getBoolean("save_history", true)
        val data = if (isDefaultData) data else dataOther
        if (data?.episodes?.isNotEmpty() == true) {
            val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>? = context?.let {
                EpisodeAdapter(
                    it,
                    data,
                    title_season_cards,
                    save,
                )
            }
            title_season_cards.adapter = adapter
            (title_season_cards.adapter as EpisodeAdapter).episodes =
                data.episodes
            (title_season_cards.adapter as EpisodeAdapter).notifyDataSetChanged()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        PlayerFragment.onLeftPlayer -= ::onLeftVideoPlayer
        DownloadManager.downloadStartEvent -= ::onDownloadStarted
        isInResults = false
    }

    fun onLeftVideoPlayer(event: Boolean) {
        loadSeason()
    }

    fun onDownloadStarted(id: String) {
        requireActivity().runOnUiThread {
            (title_season_cards.adapter as EpisodeAdapter).notifyDataSetChanged()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isInResults = true


        hideKeyboard()
        //title_duration.text = data!!.duration.toString() + "min"
        if (isCastApiAvailable()) {
            val mMediaRouteButton = view.findViewById<MediaRouteButton>(R.id.media_route_button)

            CastButtonFactory.setUpMediaRouteButton(activity, mMediaRouteButton);
            val castContext = CastContext.getSharedInstance(requireActivity().applicationContext)

            if (castContext.castState != CastState.NO_DEVICES_AVAILABLE) media_route_button.visibility = View.VISIBLE
            castContext.addCastStateListener(CastStateListener { state ->
                if (media_route_button != null) {
                    if (state == CastState.NO_DEVICES_AVAILABLE) media_route_button.visibility = View.GONE else {
                        if (media_route_button.visibility == GONE) media_route_button.visibility = View.VISIBLE
                    }
                }
            })
        }
        //isViewState = false
        PlayerFragment.onLeftPlayer += ::onLeftVideoPlayer
        DownloadManager.downloadStartEvent += ::onDownloadStarted

        results_root.setPadding(0, MainActivity.statusHeight, 0, 0)
        //media_route_button_holder.setPadding(0, MainActivity.statusHeight, 0, 0)
        //media_route_button.layoutParams = LinearLayout.LayoutParams(20.toPx, 20.toPx + MainActivity.statusHeight)  //setPadding(0, MainActivity.statusHeight, 0, 0)
        title_go_back.setOnClickListener {
            popCurrentPage()
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
        // SEASON SELECTOR
        onLoadedOther += ::onLoadOtherEvent
        onLoaded += ::onLoadEvent
    }
}
