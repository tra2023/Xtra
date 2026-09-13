package com.github.andreyasadchy.xtra.model.ui

import com.github.andreyasadchy.xtra.util.TwitchImageUrls
import kotlinx.serialization.Serializable

@Serializable
class Video(
    val id: String? = null,
    val channelId: String? = null,
    val channelLogin: String? = null,
    val channelName: String? = null,
    var channelImageURL: String? = null,
    var gameId: String? = null,
    var gameSlug: String? = null,
    var gameName: String? = null,
    val title: String? = null,
    val thumbnailURL: String? = null,
    val createdAt: String? = null,
    val viewCount: Int? = null,
    val durationSeconds: Int? = null,
    val type: String? = null,
    val animatedPreviewURL: String? = null,
) {

    val channelImage: String?
        get() = TwitchImageUrls.getProfileImage(channelImageURL)
    val thumbnail: String?
        get() = TwitchImageUrls.getVideoThumbnail(thumbnailURL)
}
