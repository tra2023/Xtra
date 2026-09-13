package com.github.andreyasadchy.xtra.model.ui

import com.github.andreyasadchy.xtra.util.TwitchImageUrls
import kotlinx.serialization.Serializable

@Serializable
class User(
    val id: String? = null,
    val login: String? = null,
    val name: String? = null,
    var profileImageURL: String? = null,
    val type: String? = null,
    val broadcasterType: String? = null,
    val createdAt: String? = null,
    val followerCount: Int? = null,
    val bannerImageURL: String? = null,
    var lastBroadcast: String? = null,
    val isLive: Boolean? = false,
    var followedAt: String? = null,
    var accountFollow: Boolean = false,
    val localFollow: Boolean = false,
) {

    val profileImage: String?
        get() = TwitchImageUrls.getProfileImage(profileImageURL)
}
