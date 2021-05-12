package com.lagradost.shiro.utils.extractors

import com.lagradost.shiro.utils.ExtractorApi
import com.lagradost.shiro.utils.ExtractorLink
import com.lagradost.shiro.utils.Qualities

class StreamTape : ExtractorApi() {
    override val name: String = "StreamTape"
    override val mainUrl: String = "https://streamtape.com"
    private val linkRegex = Regex("""(id=.*?&expires=.*?&ip=.*?&token=.*)'""")
    override fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        with(khttp.get(url)) {
            linkRegex.find(this.text)?.let{
                val extractedUrl = "https://streamtape.com/get_video?${it.groupValues[1]}"
                return listOf(
                    ExtractorLink(
                        extractedUrl,
                        url,
                        Qualities.Unknown.value
                    )
                )
            }
        }
        return null
    }
}