package com.lagradost.shiro.ui.tv

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.AutoTransition
import androidx.transition.Transition
import androidx.transition.TransitionManager
import com.lagradost.shiro.R
import com.lagradost.shiro.ui.home.MasterCardAdapter
import com.lagradost.shiro.utils.ShiroApi
import com.lagradost.shiro.utils.ShiroApi.Companion.requestHome
import kotlinx.android.synthetic.main.fragment_main_tv.*


class MainFragment : Fragment() {
    private lateinit var mainViewModel: MainFragmentViewModel

    private fun homeLoaded(data: ShiroApi.ShiroHomePage?) {
        activity?.runOnUiThread {
            val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder> = MasterCardAdapter(
                requireActivity(),
                data,
            )
            vertical_grid_view.adapter = adapter
            (vertical_grid_view.adapter as MasterCardAdapter).data = data
            (vertical_grid_view.adapter as MasterCardAdapter).notifyDataSetChanged()
            //val snapHelper = LinearSnapHelper()
            //snapHelper.attachToRecyclerView(vertical_grid_view)

        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        activity?.theme?.applyStyle(R.style.AppTheme, true)
        activity?.theme?.applyStyle(R.style.Theme_LeanbackCustom, true)

        mainViewModel =
            activity?.let { ViewModelProviders.of(it).get(MainFragmentViewModel::class.java) }!!
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_main_tv, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        search_icon.setOnFocusChangeListener { v, hasFocus ->
            val transition: Transition = AutoTransition()
            transition.duration = 2000 // DURATION OF ANIMATION IN MS

            TransitionManager.beginDelayedTransition(tv_menu_bar, transition)
            val scale = if (hasFocus) 0.8f else 0.5f
            search_icon.scaleX = scale
            search_icon.scaleY = scale
        }
        /*settings_button.setOnClickListener {
            activity?.supportFragmentManager?.beginTransaction()
                ?.replace(R.id.main_browse_fragment, SettingsFragment())
                ?.commit()
        }*/
        search_icon.setOnClickListener {
            activity?.supportFragmentManager?.beginTransaction()
                ?.replace(R.id.home_root_tv, SearchFragmentTv())
                ?.commit()
        }
        mainViewModel.apiData.observe(viewLifecycleOwner) {
            homeLoaded(it)
        }
    }

    override fun onResume() {
        requestHome()
        mainViewModel.apiData.observe(viewLifecycleOwner) {
            homeLoaded(it)
        }
        super.onResume()
    }

    companion object {
        fun newInstance() =
            MainFragment().apply {
                arguments = Bundle().apply {
                }
            }
    }
}