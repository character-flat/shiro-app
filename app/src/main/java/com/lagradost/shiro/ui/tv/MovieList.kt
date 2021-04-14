package com.lagradost.shiro.ui.tv

import com.lagradost.shiro.utils.ShiroApi
import com.lagradost.shiro.utils.ShiroApi.Companion.getHome

object MovieList {
    val MOVIE_CATEGORY = arrayOf(
        "Continue watching",
        "Favorites",
        "Trending",
        "Recently Updated",
        "Ongoing",
        "Latest anime"
    )

    val home: ShiroApi.ShiroHomePage? = setupMovies()

    private fun setupMovies():  ShiroApi.ShiroHomePage? {
        Thread.sleep(5000L)
        return getHome(true)
    }

}