package com.github.andreyasadchy.xtra.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.util.C

/**
 * Top-level settings. Mirrors `root_preferences.xml` 1:1; platform side
 * effects (locale, notifications, updates, backup/restore) go through
 * [onAction], sub-screens through [onNavigate].
 */
@Composable
fun RootSettingsScreen(
    strings: SettingsStrings,
    supportsPip: Boolean = true,
    onNavigate: (SettingsRoute) -> Unit,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsList(modifier = modifier) {
        settingsListPref(C.UI_LANGUAGE, "auto", strings, onCommitted = {
            onAction(SettingsAction.LanguageChanged(it))
        })
        settingsBooleanPref(C.UI_DRAW_BEHIND_CUTOUTS, false, strings, onChanged = {
            onAction(SettingsAction.ResultChanged)
            onAction(SettingsAction.Recreate)
        })
        item(key = "bool:live_notifications_enabled") {
            val setting = rememberBooleanSetting(C.LIVE_NOTIFICATIONS_ENABLED, false)
            SettingsNotificationsRow(
                title = strings.title("live_notifications_enabled"),
                summary = strings.summary("live_notifications_enabled"),
                enabledLabel = strings.enabled,
                disabledLabel = strings.disabled,
                checked = setting.value,
                onCheckedChange = {
                    setting.value = it
                    onAction(SettingsAction.RequestNotificationPermission)
                    onAction(SettingsAction.ToggleNotifications(it))
                },
            )
        }
        settingsClickNav("theme_settings", strings, onNavigate, SettingsRoute.Theme)
        settingsClickNav("ui_settings", strings, onNavigate, SettingsRoute.Ui)
        settingsClickNav("chat_settings", strings, onNavigate, SettingsRoute.Chat)
        if (supportsPip) {
            settingsBooleanPref(C.PLAYER_PICTURE_IN_PICTURE, true, strings)
        }
        settingsBooleanPref(C.PLAYER_BACKGROUND_AUDIO, true, strings)
        settingsBooleanPref(C.PLAYER_BACKGROUND_AUDIO_LOCKED, true, strings)
        settingsClickNav("player_settings", strings, onNavigate, SettingsRoute.Player)
        settingsClickNav("player_button_settings", strings, onNavigate, SettingsRoute.PlayerButtons)
        settingsClickNav("buffer_settings", strings, onNavigate, SettingsRoute.Buffer)
        settingsClickNav("playback_settings", strings, onNavigate, SettingsRoute.Playback)
        settingsClickNav("api_token_settings", strings, onNavigate, SettingsRoute.ApiToken)
        settingsClickNav("download_settings", strings, onNavigate, SettingsRoute.Download)
        settingsClick("check_updates", strings, onClick = { onAction(SettingsAction.CheckUpdates) })
        settingsClickNav("update_settings", strings, onNavigate, SettingsRoute.Update)
        settingsClick("backup_settings", strings, onClick = { onAction(SettingsAction.Backup) })
        settingsClick("restore_settings", strings, onClick = { onAction(SettingsAction.Restore) })
        settingsClickNav("debug_settings", strings, onNavigate, SettingsRoute.Debug)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.settingsClickNav(
    key: String,
    strings: SettingsStrings,
    onNavigate: (SettingsRoute) -> Unit,
    route: SettingsRoute,
) {
    settingsClick(key, strings, onClick = { onNavigate(route) })
}

private fun androidx.compose.foundation.lazy.LazyListScope.settingsClick(
    key: String,
    strings: SettingsStrings,
    onClick: () -> Unit,
) {
    settingsClick(key = key, title = strings.title(key), summary = strings.summary(key), onClick = onClick)
}

@Composable
private fun SettingsNotificationsRow(
    title: String,
    summary: String?,
    enabledLabel: String,
    disabledLabel: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(2.dp))
            Text(
                summary ?: if (checked) enabledLabel else disabledLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = null)
    }
    HorizontalDivider()
}

/** Searchable index entry: preference key + the screen that owns it. */
data class SettingsSearchEntry(val key: String, val route: SettingsRoute)

/** Every searchable preference key, mirroring the old search indexer. */
val SettingsSearchIndex: List<SettingsSearchEntry> = listOf(
    // root
    SettingsSearchEntry(C.UI_LANGUAGE, SettingsRoute.Root),
    SettingsSearchEntry(C.UI_DRAW_BEHIND_CUTOUTS, SettingsRoute.Root),
    SettingsSearchEntry(C.LIVE_NOTIFICATIONS_ENABLED, SettingsRoute.Root),
    SettingsSearchEntry("theme_settings", SettingsRoute.Root),
    SettingsSearchEntry("ui_settings", SettingsRoute.Root),
    SettingsSearchEntry("chat_settings", SettingsRoute.Root),
    SettingsSearchEntry(C.PLAYER_PICTURE_IN_PICTURE, SettingsRoute.Root),
    SettingsSearchEntry(C.PLAYER_BACKGROUND_AUDIO, SettingsRoute.Root),
    SettingsSearchEntry(C.PLAYER_BACKGROUND_AUDIO_LOCKED, SettingsRoute.Root),
    SettingsSearchEntry("player_settings", SettingsRoute.Root),
    SettingsSearchEntry("player_button_settings", SettingsRoute.Root),
    SettingsSearchEntry("buffer_settings", SettingsRoute.Root),
    SettingsSearchEntry("playback_settings", SettingsRoute.Root),
    SettingsSearchEntry("api_token_settings", SettingsRoute.Root),
    SettingsSearchEntry("download_settings", SettingsRoute.Root),
    SettingsSearchEntry("check_updates", SettingsRoute.Root),
    SettingsSearchEntry("update_settings", SettingsRoute.Root),
    SettingsSearchEntry("backup_settings", SettingsRoute.Root),
    SettingsSearchEntry("restore_settings", SettingsRoute.Root),
    SettingsSearchEntry("debug_settings", SettingsRoute.Root),
    // theme
    SettingsSearchEntry(C.THEME, SettingsRoute.Theme),
    SettingsSearchEntry(C.UI_THEME_FOLLOW_SYSTEM, SettingsRoute.Theme),
    SettingsSearchEntry(C.UI_THEME_DARK_ON, SettingsRoute.Theme),
    SettingsSearchEntry(C.UI_THEME_DARK_OFF, SettingsRoute.Theme),
    SettingsSearchEntry(C.UI_THEME_ROUNDED_CORNERS, SettingsRoute.Theme),
    SettingsSearchEntry(C.UI_THEME_REDUCED_PADDING, SettingsRoute.Theme),
    SettingsSearchEntry(C.UI_THEME_COMPACT_TEXT, SettingsRoute.Theme),
    SettingsSearchEntry(C.UI_THEME_APPBAR_LIFT, SettingsRoute.Theme),
    SettingsSearchEntry(C.UI_THEME_BOTTOM_NAV_COLOR, SettingsRoute.Theme),
    SettingsSearchEntry(C.UI_THEME_MATERIAL3, SettingsRoute.Theme),
    // ui
    SettingsSearchEntry(C.PORTRAIT_COLUMN_COUNT, SettingsRoute.Ui),
    SettingsSearchEntry(C.LANDSCAPE_COLUMN_COUNT, SettingsRoute.Ui),
    SettingsSearchEntry(C.UI_FOLLOW_BUTTON, SettingsRoute.Ui),
    SettingsSearchEntry(C.UI_ACTIVATE_NOTIFICATIONS_WHEN_FOLLOWING, SettingsRoute.Ui),
    SettingsSearchEntry(C.UI_START_ON_FOLLOWED, SettingsRoute.Ui),
    SettingsSearchEntry("ui_navigation_tab_list_dialog", SettingsRoute.Ui),
    SettingsSearchEntry("ui_following_tabs_dialog", SettingsRoute.Ui),
    SettingsSearchEntry("ui_saved_tabs_dialog", SettingsRoute.Ui),
    SettingsSearchEntry("ui_channel_tabs_dialog", SettingsRoute.Ui),
    SettingsSearchEntry("ui_game_tabs_dialog", SettingsRoute.Ui),
    SettingsSearchEntry("ui_search_tabs_dialog", SettingsRoute.Ui),
    SettingsSearchEntry(C.COMPACT_STREAMS, SettingsRoute.Ui),
    SettingsSearchEntry(C.UI_NAME_DISPLAY, SettingsRoute.Ui),
    SettingsSearchEntry(C.UI_ROUND_USER_IMAGE, SettingsRoute.Ui),
    SettingsSearchEntry(C.UI_TRUNCATE_VIEW_COUNT, SettingsRoute.Ui),
    SettingsSearchEntry(C.UI_UPTIME, SettingsRoute.Ui),
    SettingsSearchEntry(C.UI_TAGS, SettingsRoute.Ui),
    SettingsSearchEntry(C.UI_BROADCASTERS_COUNT, SettingsRoute.Ui),
    SettingsSearchEntry("ui_followpager", SettingsRoute.Ui),
    SettingsSearchEntry("ui_savedpager", SettingsRoute.Ui),
    SettingsSearchEntry("ui_gamepager", SettingsRoute.Ui),
    SettingsSearchEntry(C.UI_BOOKMARK_TIME_LEFT, SettingsRoute.Ui),
    SettingsSearchEntry(C.UI_STORE_RECENT_SEARCHES, SettingsRoute.Ui),
    SettingsSearchEntry("delete_recent_searches", SettingsRoute.Ui),
    // chat
    SettingsSearchEntry(C.CHAT_BOLD_NAMES, SettingsRoute.Chat),
    SettingsSearchEntry(C.CHAT_RANDOM_COLOR, SettingsRoute.Chat),
    SettingsSearchEntry(C.CHAT_THEME_ADAPTED_USERNAME_COLOR, SettingsRoute.Chat),
    SettingsSearchEntry(C.CHAT_TIMESTAMPS, SettingsRoute.Chat),
    SettingsSearchEntry(C.CHAT_TIMESTAMP_FORMAT, SettingsRoute.Chat),
    SettingsSearchEntry(C.CHAT_USE_WEBP, SettingsRoute.Chat),
    SettingsSearchEntry(C.CHAT_IMAGE_QUALITY, SettingsRoute.Chat),
    SettingsSearchEntry(C.CHAT_SIZE_MODIFIER, SettingsRoute.Chat),
    SettingsSearchEntry(C.CHAT_TEXT_SIZE, SettingsRoute.Chat),
    SettingsSearchEntry(C.CHAT_EMOTE_SIZE, SettingsRoute.Chat),
    SettingsSearchEntry(C.CHAT_BADGE_SIZE, SettingsRoute.Chat),
    SettingsSearchEntry(C.CHAT_SHOW_GIF_MESSAGES, SettingsRoute.Chat),
    SettingsSearchEntry(C.CHAT_GIF_SIZE, SettingsRoute.Chat),
    SettingsSearchEntry("chatWidth", SettingsRoute.Chat),
    SettingsSearchEntry(C.CHAT_LIMIT, SettingsRoute.Chat),
    SettingsSearchEntry(C.CHAT_RECENT, SettingsRoute.Chat),
    SettingsSearchEntry(C.CHAT_RECENT_MESSAGES_URL, SettingsRoute.Chat),
    SettingsSearchEntry(C.CHAT_RECENT_LIMIT, SettingsRoute.Chat),
    SettingsSearchEntry(C.PLAYER_KEEP_CHAT_OPEN, SettingsRoute.Chat),
    SettingsSearchEntry(C.CHAT_USE_WEBSOCKET, SettingsRoute.Chat),
    SettingsSearchEntry(C.CHAT_USE_SSL, SettingsRoute.Chat),
    SettingsSearchEntry(C.CHAT_PUB_SUB_ENABLED, SettingsRoute.Chat),
    SettingsSearchEntry(C.CHAT_POINTS_COLLECT, SettingsRoute.Chat),
    SettingsSearchEntry(C.CHAT_POINTS_NOTIFY, SettingsRoute.Chat),
    SettingsSearchEntry(C.CHAT_RAIDS_SHOW, SettingsRoute.Chat),
    SettingsSearchEntry(C.CHAT_RAIDS_AUTO_SWITCH, SettingsRoute.Chat),
    SettingsSearchEntry(C.CHAT_POLLS_SHOW, SettingsRoute.Chat),
    SettingsSearchEntry(C.CHAT_PREDICTIONS_SHOW, SettingsRoute.Chat),
    SettingsSearchEntry(C.CHAT_SHOW_PAINTS, SettingsRoute.Chat),
    SettingsSearchEntry(C.CHAT_SHOW_STV_BADGES, SettingsRoute.Chat),
    SettingsSearchEntry(C.CHAT_SHOW_PERSONAL_EMOTES, SettingsRoute.Chat),
    SettingsSearchEntry(C.CHAT_STV_LIVE_UPDATES, SettingsRoute.Chat),
    SettingsSearchEntry(C.CHAT_ENABLE_STV, SettingsRoute.Chat),
    SettingsSearchEntry(C.CHAT_ENABLE_BTTV, SettingsRoute.Chat),
    SettingsSearchEntry(C.CHAT_ENABLE_FFZ, SettingsRoute.Chat),
    SettingsSearchEntry(C.CHAT_SHOW_USER_NOTICE, SettingsRoute.Chat),
    SettingsSearchEntry(C.CHAT_SHOW_CLEAR_MSG, SettingsRoute.Chat),
    SettingsSearchEntry(C.CHAT_SHOW_CLEAR_CHAT, SettingsRoute.Chat),
    SettingsSearchEntry(C.CHAT_FIRST_MSG_VISIBILITY, SettingsRoute.Chat),
    SettingsSearchEntry(C.ANIMATED_EMOTES, SettingsRoute.Chat),
    SettingsSearchEntry(C.CHAT_ZERO_WIDTH, SettingsRoute.Chat),
    SettingsSearchEntry(C.CHAT_SYSTEM_MESSAGE_EMOTES, SettingsRoute.Chat),
    SettingsSearchEntry(C.CHAT_DISABLE, SettingsRoute.Chat),
    // player
    SettingsSearchEntry(C.PLAYER_DISABLE_BACKGROUND_VIDEO, SettingsRoute.Player),
    SettingsSearchEntry(C.PLAYER_USE_BACKGROUND_AUDIO_TRACK, SettingsRoute.Player),
    SettingsSearchEntry(C.PLAYER_BACKGROUND_AUDIO_PIP_CLOSED, SettingsRoute.Player),
    SettingsSearchEntry(C.PLAYER_BACKGROUND_AUDIO_PIP_LOCKED, SettingsRoute.Player),
    SettingsSearchEntry(C.PLAYER_KEEP_SCREEN_ON_WHEN_PAUSED, SettingsRoute.Player),
    SettingsSearchEntry(C.PLAYER_AUDIO_FOCUS, SettingsRoute.Player),
    SettingsSearchEntry(C.PLAYER_HANDLE_AUDIO_BECOMING_NOISY, SettingsRoute.Player),
    SettingsSearchEntry(C.PLAYER_SHOW_STREAM_NOTIFICATION_SEEKBAR, SettingsRoute.Player),
    SettingsSearchEntry(C.PLAYER_DEFAULT_QUALITY, SettingsRoute.Player),
    SettingsSearchEntry(C.PLAYER_DEFAULT_CELLULAR_QUALITY, SettingsRoute.Player),
    SettingsSearchEntry(C.PLAYER_ROUNDED_CORNER_PADDING, SettingsRoute.Player),
    SettingsSearchEntry(C.PLAYER_MOVE_FREELY, SettingsRoute.Player),
    SettingsSearchEntry(C.PLAYER_USE_VIDEO_POSITIONS, SettingsRoute.Player),
    SettingsSearchEntry("delete_video_positions", SettingsRoute.Player),
    // player buttons
    SettingsSearchEntry(C.PLAYER_MINIMIZE, SettingsRoute.PlayerButtons),
    SettingsSearchEntry(C.PLAYER_DOWNLOAD, SettingsRoute.PlayerButtons),
    SettingsSearchEntry(C.PLAYER_FOLLOW, SettingsRoute.PlayerButtons),
    SettingsSearchEntry(C.PLAYER_SLEEP, SettingsRoute.PlayerButtons),
    SettingsSearchEntry(C.SLEEP_TIMER_USE_TIME_PICKER, SettingsRoute.PlayerButtons),
    SettingsSearchEntry(C.SLEEP_TIMER_LOCK, SettingsRoute.PlayerButtons),
    SettingsSearchEntry("admin_settings", SettingsRoute.PlayerButtons),
    SettingsSearchEntry(C.PLAYER_ASPECT, SettingsRoute.PlayerButtons),
    SettingsSearchEntry(C.PLAYER_SPEED_BUTTON, SettingsRoute.PlayerButtons),
    SettingsSearchEntry(C.PLAYER_SPEED_LIST, SettingsRoute.PlayerButtons),
    SettingsSearchEntry(C.PLAYER_SETTINGS, SettingsRoute.PlayerButtons),
    SettingsSearchEntry(C.PLAYER_MENU, SettingsRoute.PlayerButtons),
    SettingsSearchEntry("player_menu_settings", SettingsRoute.PlayerButtons),
    SettingsSearchEntry(C.PLAYER_GAMES_BUTTON, SettingsRoute.PlayerButtons),
    SettingsSearchEntry(C.PLAYER_RESTART, SettingsRoute.PlayerButtons),
    SettingsSearchEntry(C.PLAYER_SEEK_LIVE, SettingsRoute.PlayerButtons),
    SettingsSearchEntry(C.PLAYER_VOLUME_BUTTON, SettingsRoute.PlayerButtons),
    SettingsSearchEntry(C.PLAYER_AUDIO_COMPRESSOR_BUTTON, SettingsRoute.PlayerButtons),
    SettingsSearchEntry(C.PLAYER_MODE, SettingsRoute.PlayerButtons),
    SettingsSearchEntry(C.PLAYER_SUBTITLES, SettingsRoute.PlayerButtons),
    SettingsSearchEntry(C.PLAYER_CHAT_BAR_TOGGLE, SettingsRoute.PlayerButtons),
    SettingsSearchEntry(C.PLAYER_CHAT_TOGGLE, SettingsRoute.PlayerButtons),
    SettingsSearchEntry(C.PLAYER_FULLSCREEN, SettingsRoute.PlayerButtons),
    SettingsSearchEntry(C.PLAYER_DOUBLE_TAP, SettingsRoute.PlayerButtons),
    SettingsSearchEntry(C.PLAYER_REWIND, SettingsRoute.PlayerButtons),
    SettingsSearchEntry(C.PLAYER_FORWARD, SettingsRoute.PlayerButtons),
    SettingsSearchEntry(C.PLAYER_CHANNEL, SettingsRoute.PlayerButtons),
    SettingsSearchEntry(C.PLAYER_TITLE, SettingsRoute.PlayerButtons),
    SettingsSearchEntry(C.PLAYER_CATEGORY, SettingsRoute.PlayerButtons),
    SettingsSearchEntry(C.PLAYER_SHOW_UPTIME, SettingsRoute.PlayerButtons),
    SettingsSearchEntry(C.PLAYER_VIEWER_LIST, SettingsRoute.PlayerButtons),
    SettingsSearchEntry(C.PLAYER_VIEWER_ICON, SettingsRoute.PlayerButtons),
    SettingsSearchEntry(C.PLAYER_PAUSE, SettingsRoute.PlayerButtons),
    // player menu
    SettingsSearchEntry(C.PLAYER_MENU_QUALITY, SettingsRoute.PlayerMenu),
    SettingsSearchEntry(C.PLAYER_MENU_SPEED, SettingsRoute.PlayerMenu),
    SettingsSearchEntry(C.PLAYER_MENU_VIEWER_LIST, SettingsRoute.PlayerMenu),
    SettingsSearchEntry(C.PLAYER_MENU_GAMES, SettingsRoute.PlayerMenu),
    SettingsSearchEntry(C.PLAYER_MENU_DOWNLOAD, SettingsRoute.PlayerMenu),
    SettingsSearchEntry(C.PLAYER_MENU_BOOKMARK, SettingsRoute.PlayerMenu),
    SettingsSearchEntry(C.PLAYER_MENU_SHARE, SettingsRoute.PlayerMenu),
    SettingsSearchEntry(C.PLAYER_MENU_FIND_VOD, SettingsRoute.PlayerMenu),
    SettingsSearchEntry(C.PLAYER_MENU_SLEEP, SettingsRoute.PlayerMenu),
    SettingsSearchEntry(C.PLAYER_MENU_ASPECT, SettingsRoute.PlayerMenu),
    SettingsSearchEntry(C.PLAYER_MENU_VOLUME, SettingsRoute.PlayerMenu),
    SettingsSearchEntry(C.PLAYER_MENU_SUBTITLES, SettingsRoute.PlayerMenu),
    SettingsSearchEntry(C.PLAYER_MENU_RESTART, SettingsRoute.PlayerMenu),
    SettingsSearchEntry(C.PLAYER_MENU_CHAT_BAR, SettingsRoute.PlayerMenu),
    SettingsSearchEntry(C.PLAYER_MENU_CHAT_TOGGLE, SettingsRoute.PlayerMenu),
    SettingsSearchEntry(C.PLAYER_MENU_RELOAD_EMOTES, SettingsRoute.PlayerMenu),
    SettingsSearchEntry(C.PLAYER_MENU_CHAT_DISCONNECT, SettingsRoute.PlayerMenu),
    // buffer
    SettingsSearchEntry(C.PLAYER_BUFFER_MIN, SettingsRoute.Buffer),
    SettingsSearchEntry(C.PLAYER_BUFFER_MAX, SettingsRoute.Buffer),
    SettingsSearchEntry(C.PLAYER_BUFFER_PLAYBACK, SettingsRoute.Buffer),
    SettingsSearchEntry(C.PLAYER_BUFFER_REBUFFER, SettingsRoute.Buffer),
    SettingsSearchEntry(C.PLAYER_LIVE_MIN_SPEED, SettingsRoute.Buffer),
    SettingsSearchEntry(C.PLAYER_LIVE_MAX_SPEED, SettingsRoute.Buffer),
    SettingsSearchEntry(C.PLAYER_LIVE_TARGET_OFFSET, SettingsRoute.Buffer),
    // playback
    SettingsSearchEntry(C.PLAYER_USE_VIDEO_SWAP, SettingsRoute.Playback),
    SettingsSearchEntry("video_swap_settings", SettingsRoute.Playback),
    SettingsSearchEntry(C.PLAYER_HIDE_ADS, SettingsRoute.Playback),
    SettingsSearchEntry(C.TOKEN_SUPPORTED_CODECS, SettingsRoute.Playback),
    SettingsSearchEntry(C.TOKEN_INCLUDE_TOKEN_STREAM, SettingsRoute.Playback),
    SettingsSearchEntry(C.TOKEN_INCLUDE_TOKEN_VIDEO, SettingsRoute.Playback),
    // api token
    SettingsSearchEntry(C.API_LOGIN, SettingsRoute.ApiToken),
    SettingsSearchEntry(C.HELIX_CLIENT_ID, SettingsRoute.ApiToken),
    SettingsSearchEntry(C.HELIX_REDIRECT, SettingsRoute.ApiToken),
    SettingsSearchEntry(C.GQL_CLIENT_ID2, SettingsRoute.ApiToken),
    SettingsSearchEntry(C.USER_ID, SettingsRoute.ApiToken),
    SettingsSearchEntry(C.USERNAME, SettingsRoute.ApiToken),
    SettingsSearchEntry(C.TOKEN, SettingsRoute.ApiToken),
    SettingsSearchEntry(C.GQL_TOKEN2, SettingsRoute.ApiToken),
    SettingsSearchEntry(C.GQL_TOKEN_WEB, SettingsRoute.ApiToken),
    SettingsSearchEntry(C.VALIDATE_TOKENS, SettingsRoute.ApiToken),
    // download
    SettingsSearchEntry(C.DOWNLOAD_PLAYLIST_TO_FILE, SettingsRoute.Download),
    SettingsSearchEntry(C.DOWNLOAD_WIFI_ONLY, SettingsRoute.Download),
    SettingsSearchEntry(C.DOWNLOAD_LIMIT, SettingsRoute.Download),
    SettingsSearchEntry(C.DOWNLOAD_CONCURRENT_LIMIT, SettingsRoute.Download),
    SettingsSearchEntry(C.DOWNLOAD_STREAM_LIVE_CHECK, SettingsRoute.Download),
    SettingsSearchEntry(C.DOWNLOAD_STREAM_OFFLINE_CHECK, SettingsRoute.Download),
    SettingsSearchEntry(C.DOWNLOAD_STREAM_START_WAIT, SettingsRoute.Download),
    SettingsSearchEntry(C.DOWNLOAD_STREAM_END_WAIT, SettingsRoute.Download),
    SettingsSearchEntry("import_app_downloads", SettingsRoute.Download),
    // update
    SettingsSearchEntry(C.UPDATE_URL, SettingsRoute.Update),
    SettingsSearchEntry(C.UPDATE_CHECK_ENABLED, SettingsRoute.Update),
    SettingsSearchEntry(C.UPDATE_CHECK_FREQUENCY, SettingsRoute.Update),
    SettingsSearchEntry(C.UPDATE_USE_BROWSER, SettingsRoute.Update),
    // debug
    SettingsSearchEntry(C.DEBUG_CHAT_FULL_MSG, SettingsRoute.Debug),
    SettingsSearchEntry(C.DEBUG_API_COMMANDS, SettingsRoute.Debug),
    SettingsSearchEntry(C.DEBUG_API_CHAT_MESSAGES, SettingsRoute.Debug),
    SettingsSearchEntry(C.DEBUG_WEBSOCKET_INFO, SettingsRoute.Debug),
    SettingsSearchEntry(C.DEBUG_EVENT_SUB_CHAT, SettingsRoute.Debug),
    SettingsSearchEntry(C.DEBUG_PLAYER_MENU_PLAYLIST_TAGS, SettingsRoute.Debug),
    SettingsSearchEntry(C.ENABLE_INTEGRITY, SettingsRoute.Debug),
    SettingsSearchEntry(C.USE_WEBVIEW_INTEGRITY, SettingsRoute.Debug),
    SettingsSearchEntry(C.GET_ALL_GQL_HEADERS, SettingsRoute.Debug),
    SettingsSearchEntry(C.GQL_HEADERS, SettingsRoute.Debug),
    SettingsSearchEntry("get_integrity_token", SettingsRoute.Debug),
)

/**
 * Compose replacement for `SettingsSearchFragment`. Filters [SettingsSearchIndex]
 * by title/summary and reports the owning screen for navigation.
 */
@Composable
fun SettingsSearchScreen(
    strings: SettingsStrings,
    query: String,
    onQueryChange: (String) -> Unit,
    onResultClick: (SettingsSearchEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = LocalXtraSettings.current
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(settings) {
        settings.observeChanges().collect { tick++ }
    }
    remember(tick) { tick }
    val results = remember(query, tick) {
        if (query.isBlank()) emptyList()
        else SettingsSearchIndex.filter { entry ->
            val title = strings.title(entry.key)
            val summary = strings.summary(entry.key)
            title.contains(query, ignoreCase = true) || (summary?.contains(query, ignoreCase = true) == true)
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(results) {
        if (results.isNotEmpty()) listState.scrollToItem(0)
    }
    Column(modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text(strings.search) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
            items(results, key = { it.key }) { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { onResultClick(entry) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(strings.title(entry.key), style = MaterialTheme.typography.bodyLarge)
                        strings.summary(entry.key)?.let {
                            Spacer(Modifier.height(2.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
