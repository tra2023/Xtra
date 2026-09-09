package com.github.andreyasadchy.xtra.db

data class BookmarkDeleteInfo(
    val id: Int,
    val videoId: String?,
    val userId: String?,
    val thumbnail: String?,
    val userLogo: String?,
)
