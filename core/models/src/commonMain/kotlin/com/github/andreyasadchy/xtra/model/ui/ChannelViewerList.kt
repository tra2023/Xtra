package com.github.andreyasadchy.xtra.model.ui

import kotlinx.serialization.Serializable

@Serializable
class ChannelViewerList(
    val broadcasters: List<String>,
    val moderators: List<String>,
    val vips: List<String>,
    val viewers: List<String>,
    val count: Int?,
)
