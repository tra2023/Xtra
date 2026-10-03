package com.github.andreyasadchy.xtra.model.ui

/**
 * Form values of the download dialog. Lives here (rather than in `:core:ui`) because the
 * `:core:database` Android view model that owns the quality lookup also reads it.
 */
data class DownloadFormState(
    val initialized: Boolean = false,
    val quality: Int = 0,
    val from: String = "",
    val to: String = "",
    val fromError: String? = null,
    val toError: String? = null,
    val location: Int = 0,
    val storage: Int = 0,
    val directory: String? = null,
    val downloadChat: Boolean = false,
    val downloadChatEmotes: Boolean = false,
)
