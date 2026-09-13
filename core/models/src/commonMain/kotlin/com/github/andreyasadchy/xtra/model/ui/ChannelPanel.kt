package com.github.andreyasadchy.xtra.model.ui

import kotlinx.serialization.Serializable

@Serializable
class ChannelPanel(
    val title: String? = null,
    val imageUrl: String? = null,
    val linkUrl: String? = null,
    val description: String? = null,
)
