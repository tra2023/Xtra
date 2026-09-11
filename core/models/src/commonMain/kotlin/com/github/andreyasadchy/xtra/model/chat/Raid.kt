package com.github.andreyasadchy.xtra.model.chat

import com.github.andreyasadchy.xtra.util.TwitchImageUrls

class Raid(
    val raidId: String? = null,
    val targetId: String? = null,
    val targetLogin: String? = null,
    val targetName: String? = null,
    val targetImageURL: String? = null,
    val viewerCount: Int? = null,
    val openStream: Boolean,
) {

    val targetImage: String?
        get() = TwitchImageUrls.getProfileImage(targetImageURL)
}