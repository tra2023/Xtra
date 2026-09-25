package com.github.andreyasadchy.xtra.ui.settings

/**
 * Shared settings navigation + platform-action contracts.
 *
 * Screens live in `:core:ui` (Compose Multiplatform) and stay free of Android
 * APIs. Anything needing Context (locale, permissions, file pickers, device
 * admin, update download, integrity dialog) is reported through
 * [SettingsAction] and executed by `:app`.
 */
enum class SettingsRoute {
    Root,
    Theme,
    Ui,
    Chat,
    Player,
    PlayerButtons,
    PlayerMenu,
    Buffer,
    Playback,
    ApiToken,
    Download,
    Update,
    Debug,
    Search,
    VideoSwap,
}

sealed interface SettingsAction {
    /** A pref changed that requires setResult(RESULT_OK) in :app. */
    data object ResultChanged : SettingsAction

    /** Theme / locale / cutout change requiring Activity.recreate(). */
    data object Recreate : SettingsAction

    data class LanguageChanged(val value: String) : SettingsAction
    data class ToggleNotifications(val enabled: Boolean) : SettingsAction
    data object RequestNotificationPermission : SettingsAction
    data class ChatWidthChanged(val percent: Int) : SettingsAction
    data object CheckUpdates : SettingsAction
    data object Backup : SettingsAction
    data object Restore : SettingsAction
    data object DeletePositions : SettingsAction
    data object DeleteSearches : SettingsAction
    data object ImportDownloads : SettingsAction
    data object RequestDeviceAdmin : SettingsAction
    data object OpenDeviceAdminSettings : SettingsAction
    data object GetIntegrityToken : SettingsAction
}

/**
 * All user-visible strings for the settings screens.
 *
 * `:app` builds this from `R.string` / `R.array` (see `SettingsStringsFactory`);
 * the desktop app supplies its own. Titles/summaries/list entries are keyed by
 * preference key so core never touches Android resources:
 * - [titles]/[summaries]: pref key -> text (`%s` already resolved by :app
 *   where the XML used `android:summary="%s"`).
 * - [listEntries]/[listEntryValues]: pref key -> parallel arrays.
 * - [tabLabels]: `"$group:$key" -> localized tab name` for the drag-list
 *   dialogs (groups: navigation, following, saved, channel, game, search).
 */
data class SettingsStrings(
    val search: String,
    val enabled: String,
    val disabled: String,
    val ok: String,
    val cancel: String,
    val yes: String,
    val no: String,
    val retry: String,
    val seconds: (String) -> String,
    val titles: Map<String, String> = emptyMap(),
    val summaries: Map<String, String?> = emptyMap(),
    val messages: Map<String, String> = emptyMap(),
    val listEntries: Map<String, List<String>> = emptyMap(),
    val listEntryValues: Map<String, List<String>> = emptyMap(),
    val tabLabels: Map<String, String> = emptyMap(),
) {
    fun title(key: String): String = titles[key] ?: key

    fun summary(key: String): String? = summaries[key]

    fun message(key: String): String = messages[key] ?: key

    fun entries(key: String): List<String> = listEntries[key] ?: emptyList()

    fun entryValues(key: String): List<String> = listEntryValues[key] ?: emptyList()

    /** Display label for the stored [value] of a list preference [key]. */
    fun entryLabel(key: String, value: String?): String? {
        val values = entryValues(key)
        val labels = entries(key)
        val index = values.indexOf(value).takeIf { it >= 0 } ?: return null
        return labels.getOrNull(index)
    }

    fun tabLabel(group: String, key: String, fallback: String): String =
        tabLabels["$group:$key"] ?: fallback
}

/** Reorderable tab row stored as `key:default:enabled` CSV (see C.DEFAULT_*). */
data class DragListItem(
    val key: String,
    val text: String,
    var default: Boolean,
    var enabled: Boolean,
)

fun parseDragList(raw: String?, default: String, labels: (String) -> String): List<DragListItem> {
    val defaults = default.split(",")
    val items = (raw ?: default).split(",").filter { it.isNotBlank() }.mapNotNull { entry ->
        val parts = entry.split(":")
        if (parts.isEmpty()) return@mapNotNull null
        val key = parts[0]
        if (defaults.none { it.startsWith("$key:") }) return@mapNotNull null
        DragListItem(
            key = key,
            text = labels(key),
            default = parts.getOrNull(1) != "0",
            enabled = parts.getOrNull(2) != "0",
        )
    }.toMutableList()
    defaults.forEachIndexed { index, item ->
        val key = item.substringBefore(":")
        if (items.none { it.key == key }) {
            items.add(index.coerceAtMost(items.size), DragListItem(key, labels(key), false, true))
        }
    }
    return items
}

fun serializeDragList(items: List<DragListItem>): String =
    items.joinToString(",") { "${it.key}:${if (it.default) "1" else "0"}:${if (it.enabled) "1" else "0"}" }
