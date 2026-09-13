package com.github.andreyasadchy.xtra.model.ui

import kotlinx.serialization.Serializable

@Serializable
class SettingsDragListItem(
    val key: String,
    val text: String,
    var default: Boolean,
    var enabled: Boolean,
)
