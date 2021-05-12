package com.lagradost.shiro.utils.extractors

import com.lagradost.shiro.utils.*

class Mp4Upload : ExtractorApi() {
    override val name: String = "Mp4Upload"
    override val mainUrl: String = "https://www.mp4upload.com"
    private val srcRegex = Regex("""player\.src\("(.*?)"""")

    override fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        try {
            with(khttp.get(url)) {
                getAndUnpack(this.text)?.let { unpackedText ->
                    srcRegex.find(unpackedText)?.groupValues?.get(1)?.let { link ->
                        return listOf(
                            ExtractorLink(
                                link,
                                url,
                                Qualities.Unknown.value
                            )
                        )
                    }
                }
            }
            return null
        } catch (e: Exception) {
            return null
        }
    }
}