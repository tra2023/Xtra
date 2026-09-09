package com.github.andreyasadchy.xtra.model.chat

class TwitchEmote(
    val id: String? = null,
    val name: String? = null,
    val localData: Pair<Long, Int>? = null,
    url1x: String? = null,
    url2x: String? = null,
    url3x: String? = null,
    url4x: String? = null,
    val format: String? = "gif",
    val isAnimated: Boolean = true,
    var begin: Int = 0,
    var end: Int = 0,
    val setId: String? = null,
    val ownerId: String? = null,
) {
    private val _url1x = url1x
    private val _url2x = url2x
    private val _url3x = url3x
    private val _url4x = url4x

    val url1x: String?
        get() = _url1x ?: id?.let { "https://static-cdn.jtvnw.net/emoticons/v2/$it/default/dark/1.0" }
    val url2x: String?
        get() = _url2x ?: id?.let { "https://static-cdn.jtvnw.net/emoticons/v2/$it/default/dark/2.0" }
    val url3x: String?
        get() = _url3x ?: id?.let { "https://static-cdn.jtvnw.net/emoticons/v2/$it/default/dark/2.0" }
    val url4x: String?
        get() = _url4x ?: id?.let { "https://static-cdn.jtvnw.net/emoticons/v2/$it/default/dark/3.0" }
}
