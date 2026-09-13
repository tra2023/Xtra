package com.github.andreyasadchy.xtra.model.ui

import com.github.andreyasadchy.xtra.util.TwitchImageUrls
import kotlinx.serialization.Serializable

@Serializable
class Clip(
    val id: String? = null,
    val channelId: String? = null,
    var channelLogin: String? = null,
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
    val videoId: String? = null,
    val videoOffsetSeconds: Int? = null,
    val videoCreatedAt: String? = null,
    val videoAnimatedPreviewURL: String? = null,
) {

    val channelImage: String?
        get() = TwitchImageUrls.getProfileImage(channelImageURL)
    val thumbnail: String?
        get() = TwitchImageUrls.getClipThumbnail(thumbnailURL)
}
