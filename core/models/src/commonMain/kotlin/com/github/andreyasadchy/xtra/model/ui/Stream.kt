package com.github.andreyasadchy.xtra.model.ui

import com.github.andreyasadchy.xtra.util.TwitchImageUrls
import kotlinx.serialization.Serializable

@Serializable
class Stream(
    var id: String? = null,
    val channelId: String? = null,
    val channelLogin: String? = null,
    val channelName: String? = null,
    var channelImageURL: String? = null,
    var gameId: String? = null,
    var gameSlug: String? = null,
    var gameName: String? = null,
    var title: String? = null,
    val thumbnailURL: String? = null,
    var createdAt: String? = null,
    var viewerCount: Int? = null,
    val tags: List<String>? = null,
) {

    val channelImage: String?
        get() = TwitchImageUrls.getProfileImage(channelImageURL)
    val thumbnail: String?
        get() = TwitchImageUrls.getStreamThumbnail(thumbnailURL)
}
