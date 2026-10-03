package com.github.andreyasadchy.xtra.ui.common

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Platform strings (and plural formatting) for the shared list rows and paging
 * scaffold. Android builds this from `R`; the desktop app supplies its own.
 */
data class XtraStrings(
    val download: String,
    val share: String,
    val options: String,
    val resume: String,
    val addBookmark: String,
    val removeBookmark: String,
    val error: (String) -> String,
    val nothingHere: String,
    val retry: String,
    val sort: String,
    val viewers: (Int) -> String,
    val views: (Int) -> String,
    val broadcasters: (Int) -> String,
    val followers: (Int) -> String,
    val uptime: (String) -> String,
    val videoType: (String?) -> String?,
    // Channel header (see ChannelBannerContent).
    val createdAt: (String?) -> String,
    val lastBroadcastDate: (String?) -> String,
    val openPlayer: String,
    val watchLive: String,
    val userPartner: String,
    val userAffiliate: String,
    val userStaff: String,
    // Shared top app bar (see XtraTopBar).
    val search: String,
    val settings: String,
    val logIn: String,
    val logOut: String,
)

val LocalXtraStrings = staticCompositionLocalOf<XtraStrings> {
    error("XtraStrings not provided")
}
