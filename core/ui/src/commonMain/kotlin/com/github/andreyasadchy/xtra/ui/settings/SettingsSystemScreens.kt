package com.github.andreyasadchy.xtra.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.util.C

/** Mirrors `buffer_preferences.xml` (all free-form numeric strings). */
@Composable
fun BufferSettingsScreen(strings: SettingsStrings, modifier: Modifier = Modifier) {
    SettingsList(modifier = modifier) {
        settingsTextPref(C.PLAYER_BUFFER_MIN, "15000", strings)
        settingsTextPref(C.PLAYER_BUFFER_MAX, "50000", strings)
        settingsTextPref(C.PLAYER_BUFFER_PLAYBACK, "2000", strings)
        settingsTextPref(C.PLAYER_BUFFER_REBUFFER, "2000", strings)
        settingsTextPref(C.PLAYER_LIVE_MIN_SPEED, "", strings)
        settingsTextPref(C.PLAYER_LIVE_MAX_SPEED, "", strings)
        settingsTextPref(C.PLAYER_LIVE_TARGET_OFFSET, "2000", strings)
    }
}

/** Mirrors `playback_preferences.xml`. */
@Composable
fun PlaybackSettingsScreen(
    strings: SettingsStrings,
    onNavigate: (SettingsRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsList(modifier = modifier) {
        settingsBooleanPref(C.PLAYER_HIDE_ADS, false, strings)
        settingsTextPref(C.TOKEN_SUPPORTED_CODECS, "av1,h265,h264", strings)
        settingsBooleanPref(C.TOKEN_INCLUDE_TOKEN_STREAM, true, strings)
        settingsBooleanPref(C.TOKEN_INCLUDE_TOKEN_VIDEO, true, strings)
    }
}

/** Mirrors `api_token_preferences.xml` (login + client ids in prefs, tokens in tokenPrefs). */
@Composable
fun ApiTokenSettingsScreen(strings: SettingsStrings, modifier: Modifier = Modifier) {
    SettingsList(modifier = modifier) {
        settingsListPref(C.API_LOGIN, "0", strings)
        settingsTextPref(C.HELIX_CLIENT_ID, "ilfexgv3nnljz3isbm257gzwrzr7bi", strings)
        settingsTextPref(C.HELIX_REDIRECT, "https://localhost", strings)
        settingsTextPref(C.GQL_CLIENT_ID2, "kd1unb4b3q4t58fwlpcbzcbnm76a8fp", strings)
        item(key = "header:current_login") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    strings.title("api_current_login"),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        settingsTextPref(C.USER_ID, null, strings)
        settingsTextPref(C.USERNAME, null, strings)
        settingsTextPref(C.TOKEN, null, strings)
        settingsTextPref(C.GQL_TOKEN2, null, strings)
        settingsTextPref(C.GQL_TOKEN_WEB, null, strings)
        settingsBooleanPref(C.VALIDATE_TOKENS, true, strings)
    }
}

/** Mirrors `download_preferences.xml`. */
@Composable
fun DownloadSettingsScreen(
    strings: SettingsStrings,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsList(modifier = modifier) {
        settingsBooleanPref(C.DOWNLOAD_PLAYLIST_TO_FILE, false, strings)
        settingsBooleanPref(C.DOWNLOAD_WIFI_ONLY, false, strings)
        settingsSliderPref(C.DOWNLOAD_LIMIT, 2, 1..10, strings)
        settingsSliderPref(C.DOWNLOAD_CONCURRENT_LIMIT, 10, 1..20, strings)
        settingsTextPref(C.DOWNLOAD_STREAM_LIVE_CHECK, "2", strings)
        settingsTextPref(C.DOWNLOAD_STREAM_OFFLINE_CHECK, "10", strings)
        settingsTextPref(C.DOWNLOAD_STREAM_START_WAIT, "120", strings)
        settingsTextPref(C.DOWNLOAD_STREAM_END_WAIT, "15", strings)
        settingsClick(
            key = "import_app_downloads",
            title = strings.title("import_app_downloads"),
            summary = strings.summary("import_app_downloads"),
            onClick = { onAction(SettingsAction.ImportDownloads) },
        )
    }
}

/** Mirrors `update_preferences.xml`. */
@Composable
fun UpdateSettingsScreen(strings: SettingsStrings, modifier: Modifier = Modifier) {
    SettingsList(modifier = modifier) {
        settingsTextPref(C.UPDATE_URL, "https://api.github.com/repos/crackededed/xtra/releases/tags/latest", strings)
        settingsBooleanPref(C.UPDATE_CHECK_ENABLED, false, strings)
        settingsTextPref(C.UPDATE_CHECK_FREQUENCY, "7", strings)
        settingsBooleanPref(C.UPDATE_USE_BROWSER, false, strings)
    }
}

/** Mirrors `debug_preferences.xml`. */
@Composable
fun DebugSettingsScreen(
    strings: SettingsStrings,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsList(modifier = modifier) {
        settingsBooleanPref(C.DEBUG_CHAT_FULL_MSG, false, strings)
        settingsBooleanPref(C.DEBUG_API_COMMANDS, true, strings)
        settingsBooleanPref(C.DEBUG_API_CHAT_MESSAGES, true, strings)
        settingsBooleanPref(C.DEBUG_WEBSOCKET_INFO, false, strings)
        settingsBooleanPref(C.DEBUG_EVENT_SUB_CHAT, false, strings)
        settingsBooleanPref(C.DEBUG_PLAYER_MENU_PLAYLIST_TAGS, false, strings)
        settingsBooleanPref(C.ENABLE_INTEGRITY, false, strings)
        settingsBooleanPref(C.USE_WEBVIEW_INTEGRITY, true, strings)
        settingsBooleanPref(C.GET_ALL_GQL_HEADERS, false, strings)
        settingsTextPref(C.GQL_HEADERS, null, strings)
        settingsClick(
            key = "get_integrity_token",
            title = strings.title("get_integrity_token"),
            summary = strings.summary("get_integrity_token"),
            onClick = { onAction(SettingsAction.GetIntegrityToken) },
        )
    }
}
