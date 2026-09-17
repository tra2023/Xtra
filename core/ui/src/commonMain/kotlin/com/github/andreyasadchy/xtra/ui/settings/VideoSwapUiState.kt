package com.github.andreyasadchy.xtra.ui.settings

data class VideoSwapListItem(
    val id: Int,
    val platform: String?,
    val playerType: String?,
    val enabled: Boolean = true,
    val isDefault: Boolean = false,
) {
    val description: String
        get() = listOfNotNull(platform?.takeIf { it.isNotBlank() }, playerType?.takeIf { it.isNotBlank() }).joinToString(", ")
}

data class VideoSwapSettingsUiState(
    val items: List<VideoSwapListItem> = emptyList(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
)

data class VideoSwapLabels(
    val add: String,
    val defaultValues: String,
    val platform: String,
    val playerType: String,
    val enabled: String,
    val edit: String,
    val delete: String,
    val deleteMessage: String,
    val confirm: String,
    val cancel: String,
    val reorder: String,
    val moveUp: String,
    val moveDown: String,
    val retry: String,
)
