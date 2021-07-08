package com.lagradost.shiro.ui.settings

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.*
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.lagradost.shiro.R
import com.lagradost.shiro.ui.GlideApp
import com.lagradost.shiro.ui.MainActivity
import com.lagradost.shiro.ui.MainActivity.Companion.statusHeight
import com.lagradost.shiro.ui.WebViewFragment.Companion.onWebViewNavigated
import com.lagradost.shiro.ui.tv.TvActivity.Companion.tvActivity
import com.lagradost.shiro.utils.*
import com.lagradost.shiro.utils.AppUtils.changeStatusBarState
import com.lagradost.shiro.utils.AppUtils.getCurrentActivity
import com.lagradost.shiro.utils.AppUtils.observe
import kotlinx.android.synthetic.main.fragment_settings.*

class SettingsFragmentNew : Fragment() {
    companion object {
        var isInSettings: Boolean = false
        var restoreFileSelector: ActivityResultLauncher<String>? = null
        var settingsViewModel: SettingsViewModel? = null

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val array = arrayOf(
            Pair("General", R.xml.settings_general),
            Pair("Style", R.xml.custom_pref_cyanea),
            Pair("Player", R.xml.settings_player),
            Pair("Accounts", R.xml.settings_accounts),
            Pair("History", R.xml.settings_history),
            Pair("Updates", R.xml.settings_update_info),
            Pair("About", R.xml.settings_about),
        )

        settingsViewModel =
            settingsViewModel ?: activity?.let {
                ViewModelProvider(it).get(
                    SettingsViewModel::class.java
                )
            }

        val topParams: LinearLayout.LayoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, // view width
            MainActivity.statusHeight // view height
        )
        top_padding_settings?.layoutParams = topParams

        // Because the user isn't necessarily fetched
        if (DataStore.getKey<String>(MAL_TOKEN_KEY, MAL_ACCOUNT_ID, null) != null
            && DataStore.getKeys(MAL_USER_KEY).isEmpty()
        ) {
            MALApi.getUser()
        }

        context?.let { context ->
            settings_listview?.adapter = ArrayAdapter(context, R.layout.listview_single_item, array.map { it.first })
            settings_listview.setOnItemClickListener { _, _, position, _ ->
                openSettingSubMenu(array[position].second)
            }
            loadProfile()
            observe(settingsViewModel!!.hasLoggedIntoMAL) {
                loadProfile()
            }
            observe(settingsViewModel!!.hasLoggedIntoAnilist) {
                loadProfile()
            }


        }
    }

    private fun loadProfile() {
        var userImage: String? = null
        var userName: String? = null
        DataStore.getKeys(ANILIST_USER_KEY).forEach { key ->
            DataStore.getKey<AniListApi.AniListUser>(key, null)?.let {
                userImage = it.picture
                userName = it.name
            }
        }

        if (userImage == null || userName == null) {
            DataStore.getKeys(MAL_USER_KEY).forEach { key ->
                DataStore.getKey<MALApi.MalUser>(key, null)?.let {
                    userImage = userImage ?: it.picture
                    userName = userName ?: it.name
                }
            }
        }

        if (userName != null) {
            name_text?.text = userName
            name_text?.visibility = VISIBLE
        } else {
            name_text?.visibility = INVISIBLE
        }

        icon_image?.setOnClickListener {
            name_text?.isVisible = !(name_text?.isVisible ?: false)
        }

        context?.let { context ->
            icon_image?.let {
                GlideApp.with(context)
                    .load(userImage ?: "")
                    .transition(DrawableTransitionOptions.withCrossFade(100))
                    .error(R.drawable.shiro_logo_rounded)
                    .into(it)
            }
        }
    }

    private fun openSettingSubMenu(xml: Int) {
        val intent = Intent(getCurrentActivity()!!, SettingsActivity::class.java).apply {
            putExtra(XML_KEY, xml)
        }
        startActivity(intent)
    }

    private fun restoreState(hasEntered: Boolean) {
        if (hasEntered) {
            this.view?.visibility = GONE
        } else {
            this.view?.visibility = VISIBLE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        onWebViewNavigated -= ::restoreState
        isInSettings = false
    }

    override fun onResume() {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(activity)
        activity?.changeStatusBarState(settingsManager.getBoolean("statusbar_hidden", true))?.let {
            statusHeight = it
        }
        activity?.requestedOrientation = if (settingsManager.getBoolean("force_landscape", false)) {
            ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        isInSettings = true
        if (tvActivity != null) {
            onWebViewNavigated += ::restoreState
        }
        super.onResume()
    }

}