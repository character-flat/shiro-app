package com.lagradost.shiro.utils

import org.jsoup.Jsoup

object FillerEpisodeCheck {
    private const val MAIN_URL = "https://www.animefillerlist.com"

    var list: HashMap<String, String>? = null

    private fun fixName(name: String): String {
        return name.lowercase()/*.replace(" ", "")*/.replace("-", " ").replace("[^a-zA-Z0-9 ]".toRegex(), "")
    }

    private fun getFillerList(): Boolean {
        if (list != null) return true
        try {
            val result = khttp.get("$MAIN_URL/shows")
            val documented = Jsoup.parse(result.text)
            val localHTMLList = documented.select("div#ShowList > div.Group > ul > li > a")
            val localList = HashMap<String, String>()
            for (i in localHTMLList) {
                val name = i.text()

                if (name.lowercase().contains("manga only")) continue

                val href = i.attr("href")
                if (name.isNullOrEmpty() || href.isNullOrEmpty()) {
                    continue
                }

                val values = "(.*) \\((.*)\\)".toRegex().matchEntire(name)?.groups
                if (values != null) {
                    for (index in 1 until values.size) {
                        val localName = values[index]?.value ?: continue
                        localList[fixName(localName)] = href
                    }
                } else {
                    localList[fixName(name)] = href
                }
            }
            if (localList.size > 0) {
                list = localList
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    fun getFillerEpisodes(query: String): HashMap<Int, Boolean>? {
        try {
            if (!getFillerList()) return null
            val localList = list ?: return null

            // Strips these from the name
            val blackList = listOf(
                "TV Dubbed",
                "(Dub)",
                "Subbed",
                "(TV)",
                "(Uncensored)",
                "(Censored)",
                "(\\d+)" // year
            )
            val blackListRegex =
                Regex(""" (${blackList.joinToString(separator = "|").replace("(", "\\(").replace(")", "\\)")})""")

            val realQuery = fixName(query.replace(blackListRegex, "")).replace("shippuuden", "shippuden")
            if (!localList.containsKey(realQuery)) return null
            val href = localList[realQuery]?.replace(MAIN_URL, "") ?: return null // JUST IN CASE
            val result = khttp.get("$MAIN_URL$href")
            val documented = Jsoup.parse(result.text) ?: return null
            val hashMap = HashMap<Int, Boolean>()
            documented.select("table.EpisodeList > tbody > tr").forEach {
                val type = it.selectFirst("td.Type > span").text() == "Filler"
                val episodeNumber = it.selectFirst("td.Number").text().toIntOrNull()
                if (episodeNumber != null) {
                    hashMap[episodeNumber] = type
                }
            }
            return hashMap
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}