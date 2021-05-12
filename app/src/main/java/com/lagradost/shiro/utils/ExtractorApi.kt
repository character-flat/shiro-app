package com.lagradost.shiro.utils

import com.lagradost.shiro.utils.extractors.Mp4Upload
import com.lagradost.shiro.utils.extractors.Shiro
import com.lagradost.shiro.utils.extractors.StreamTape

data class ExtractorLink(
    val url: String,
    val referer: String,
    val quality: Int
)

enum class Qualities(var value: Int) {
    Unknown(-1),
    SD(0), // 360p - 480p
    HD(1), // 720p
    FullHd(2), // 1080p
    UHD(3) // 4k
}

private val packedRegex = Regex("""eval\(function\(p,a,c,k,e,.*\)\)\)""")
fun getPacked(string: String): String? {
    return packedRegex.find(string)?.value
}

fun getAndUnpack(string: String): String? {
    val packedText = getPacked(string)
    return JsUnpacker(packedText).unpack()
}

val APIS: Array<ExtractorApi> = arrayOf(
    //AllProvider(),
    Shiro(),
    Mp4Upload(),
    StreamTape()
)


abstract class ExtractorApi {
    open val name: String = "NONE"
    open val mainUrl: String = "NONE"

    open fun getUrl(url: String, referer: String? = null): List<ExtractorLink>? {
        return null
    }

    open fun getExtractorUrl(id: String): String{
        return id
    }
}