package com.lagradost.shiro.utils

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.shiro.utils.extractors.*

data class ExtractorLink(
    @JsonProperty("name") val name: String,
    @JsonProperty("url")val url: String,
    @JsonProperty("referer") val referer: String,
    @JsonProperty("quality") val quality: Int,
    @JsonProperty("isM3u8") val isM3u8: Boolean = false,
)

enum class Qualities(var value: Int) {
    Unknown(0),
    SD(-1), // 360p - 480p
    HD(1), // 720p
    FullHd(2), // 1080p
    UHD(3) // 4k
}

private val packedRegex = Regex("""eval\(function\(p,a,c,k,e,.*\)\)""")
fun getPacked(string: String): String? {
    return packedRegex.find(string)?.value
}

fun getAndUnpack(string: String): String? {
    val packedText = getPacked(string)
    return JsUnpacker(packedText).unpack()
}

val APIS: Array<ExtractorApi> = arrayOf(
    // AllProvider(),
    Shiro(),
    MultiQuality(),
    Mp4Upload(),
    StreamTape(),
    MixDrop(),
    XStreamCdn(),
    StreamSB()
)

/*val linKLoadingTimeout: Double
    get() {
        return (settingsManager?.getInt("link_loading_timeout", 15) ?: 15).toDouble()
    }*/

fun httpsify(url: String): String {
    return if (url.startsWith("//")) "https:$url" else url
}

abstract class ExtractorApi {
    abstract val name: String
    abstract val mainUrl: String
    abstract val requiresReferer: Boolean

    abstract fun getUrl(url: String, referer: String? = null): List<ExtractorLink>?

    open fun getExtractorUrl(id: String): String {
        return id
    }
}