package com.github.andreyasadchy.xtra.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.util.C

/** Mirrors `chat_preferences.xml`. `chatWidth` also updates the computed landscape pixel width via :app. */
@Composable
fun ChatSettingsScreen(
    strings: SettingsStrings,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsList(modifier = modifier) {
        settingsBooleanPref(C.CHAT_BOLD_NAMES, false, strings)
        settingsBooleanPref(C.CHAT_RANDOM_COLOR, true, strings)
        settingsBooleanPref(C.CHAT_THEME_ADAPTED_USERNAME_COLOR, true, strings)
        settingsBooleanPref(C.CHAT_TIMESTAMPS, false, strings)
        settingsListPref(C.CHAT_TIMESTAMP_FORMAT, "0", strings)
        settingsBooleanPref(C.CHAT_USE_WEBP, true, strings)
        settingsListPref(C.CHAT_IMAGE_QUALITY, "4", strings)
        settingsSliderPref(C.CHAT_SIZE_MODIFIER, 100, 1..200, strings)
        settingsTextPref(C.CHAT_TEXT_SIZE, "14", strings)
        settingsTextPref(C.CHAT_EMOTE_SIZE, "29.5", strings)
        settingsTextPref(C.CHAT_BADGE_SIZE, "18.5", strings)
        settingsSliderPref("chatWidth", 30, 0..100, strings, onCommitted = {
            onAction(SettingsAction.ChatWidthChanged(it))
            onAction(SettingsAction.ResultChanged)
        })
        settingsSliderPref(C.CHAT_LIMIT, 600, 1..1000, strings)
        settingsBooleanPref(C.CHAT_RECENT, true, strings)
        settingsTextPref(C.CHAT_RECENT_MESSAGES_URL, "https://recent-messages.robotty.de/api/v2/recent-messages/\$channel", strings)
        settingsSliderPref(C.CHAT_RECENT_LIMIT, 100, 1..800, strings)
        settingsBooleanPref(C.PLAYER_KEEP_CHAT_OPEN, false, strings)
        settingsBooleanPref(C.CHAT_USE_WEBSOCKET, true, strings)
        settingsBooleanPref(C.CHAT_USE_SSL, true, strings)
        settingsBooleanPref(C.CHAT_PUB_SUB_ENABLED, true, strings)
        settingsBooleanPref(C.CHAT_POINTS_COLLECT, true, strings)
        settingsBooleanPref(C.CHAT_POINTS_NOTIFY, false, strings)
        settingsBooleanPref(C.CHAT_RAIDS_SHOW, true, strings)
        settingsBooleanPref(C.CHAT_RAIDS_AUTO_SWITCH, true, strings)
        settingsBooleanPref(C.CHAT_POLLS_SHOW, true, strings)
        settingsBooleanPref(C.CHAT_PREDICTIONS_SHOW, true, strings)
        settingsBooleanPref(C.CHAT_SHOW_PAINTS, true, strings)
        settingsBooleanPref(C.CHAT_SHOW_STV_BADGES, true, strings)
        settingsBooleanPref(C.CHAT_SHOW_PERSONAL_EMOTES, true, strings)
        settingsBooleanPref(C.CHAT_STV_LIVE_UPDATES, true, strings)
        settingsBooleanPref(C.CHAT_ENABLE_STV, true, strings)
        settingsBooleanPref(C.CHAT_ENABLE_BTTV, true, strings)
        settingsBooleanPref(C.CHAT_ENABLE_FFZ, true, strings)
        settingsBooleanPref(C.CHAT_SHOW_USER_NOTICE, true, strings)
        settingsBooleanPref(C.CHAT_SHOW_CLEAR_MSG, true, strings)
        settingsBooleanPref(C.CHAT_SHOW_CLEAR_CHAT, true, strings)
        settingsListPref(C.CHAT_FIRST_MSG_VISIBILITY, "0", strings)
        settingsBooleanPref(C.ANIMATED_EMOTES, true, strings)
        settingsBooleanPref(C.CHAT_ZERO_WIDTH, true, strings)
        settingsBooleanPref(C.CHAT_SYSTEM_MESSAGE_EMOTES, true, strings)
        settingsBooleanPref(C.CHAT_DISABLE, false, strings)
    }
}

/** Mirrors `player_preferences.xml`. PiP-gated rows hidden when unsupported. */
@Composable
fun PlayerSettingsScreen(
    strings: SettingsStrings,
    supportsPip: Boolean = true,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsList(modifier = modifier) {
        settingsBooleanPref(C.PLAYER_DISABLE_BACKGROUND_VIDEO, true, strings)
        settingsBooleanPref(C.PLAYER_USE_BACKGROUND_AUDIO_TRACK, false, strings)
        if (supportsPip) {
            settingsBooleanPref(C.PLAYER_BACKGROUND_AUDIO_PIP_CLOSED, false, strings)
            settingsBooleanPref(C.PLAYER_BACKGROUND_AUDIO_PIP_LOCKED, true, strings)
        }
        settingsBooleanPref(C.PLAYER_KEEP_SCREEN_ON_WHEN_PAUSED, false, strings)
        settingsBooleanPref(C.PLAYER_AUDIO_FOCUS, false, strings)
        settingsBooleanPref(C.PLAYER_HANDLE_AUDIO_BECOMING_NOISY, true, strings)
        settingsBooleanPref(C.PLAYER_SHOW_STREAM_NOTIFICATION_SEEKBAR, true, strings)
        settingsListPref(C.PLAYER_DEFAULT_QUALITY, "saved", strings)
        settingsListPref(C.PLAYER_DEFAULT_CELLULAR_QUALITY, "saved", strings)
        settingsBooleanPref(C.PLAYER_ROUNDED_CORNER_PADDING, false, strings)
        settingsBooleanPref(C.PLAYER_MOVE_FREELY, false, strings)
        settingsBooleanPref(C.PLAYER_USE_VIDEO_POSITIONS, true, strings)
        item(key = "click:delete_video_positions") {
            var confirm by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable(onClick = { confirm = true })
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(strings.title("delete_video_positions"), style = MaterialTheme.typography.bodyLarge)
            }
            HorizontalDivider()
            if (confirm) {
                SettingsConfirmDialog(
                    message = strings.message("delete_video_positions"),
                    confirmLabel = strings.yes,
                    dismissLabel = strings.no,
                    onConfirm = {
                        confirm = false
                        onAction(SettingsAction.DeletePositions)
                    },
                    onDismiss = { confirm = false },
                )
            }
        }
    }
}

/**
 * Mirrors `player_button_preferences.xml`. Enabling the lock-screen sleep
 * timer requests device admin; rewind/forward show localized seconds.
 */
@Composable
fun PlayerButtonSettingsScreen(
    strings: SettingsStrings,
    onNavigate: (SettingsRoute) -> Unit,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsList(modifier = modifier) {
        settingsBooleanPref(C.PLAYER_MINIMIZE, true, strings)
        settingsBooleanPref(C.PLAYER_DOWNLOAD, false, strings)
        settingsBooleanPref(C.PLAYER_FOLLOW, false, strings)
        settingsBooleanPref(C.PLAYER_SLEEP, false, strings)
        settingsBooleanPref(C.SLEEP_TIMER_LOCK, false, strings, onChanged = {
            if (it) onAction(SettingsAction.RequestDeviceAdmin)
        })
        settingsClick(
            key = "admin_settings",
            title = strings.title("admin_settings"),
            summary = strings.summary("admin_settings"),
            onClick = { onAction(SettingsAction.OpenDeviceAdminSettings) },
        )
        settingsBooleanPref(C.PLAYER_ASPECT, true, strings)
        settingsBooleanPref(C.PLAYER_SPEED_BUTTON, true, strings)
        settingsTextPref(C.PLAYER_SPEED_LIST, "0.25\n0.5\n0.75\n1.0\n1.25\n1.5\n1.75\n2.0\n3.0\n4.0\n8.0", strings)
        settingsBooleanPref(C.PLAYER_SETTINGS, true, strings)
        settingsBooleanPref(C.PLAYER_MENU, true, strings)
        settingsClick(
            key = "player_menu_settings",
            title = strings.title("player_menu_settings"),
            summary = strings.summary("player_menu_settings"),
            onClick = { onNavigate(SettingsRoute.PlayerMenu) },
        )
        settingsBooleanPref(C.PLAYER_GAMES_BUTTON, true, strings)
        settingsBooleanPref(C.PLAYER_RESTART, true, strings)
        settingsBooleanPref(C.PLAYER_SEEK_LIVE, false, strings)
        settingsBooleanPref(C.PLAYER_VOLUME_BUTTON, true, strings)
        settingsBooleanPref(C.PLAYER_AUDIO_COMPRESSOR_BUTTON, true, strings)
        settingsBooleanPref(C.PLAYER_MODE, false, strings)
        settingsBooleanPref(C.PLAYER_SUBTITLES, false, strings)
        settingsBooleanPref(C.PLAYER_CHAT_BAR_TOGGLE, false, strings)
        settingsBooleanPref(C.PLAYER_CHAT_TOGGLE, true, strings)
        settingsBooleanPref(C.PLAYER_FULLSCREEN, true, strings)
        settingsBooleanPref(C.PLAYER_DOUBLE_TAP, true, strings)
        item(key = "rewindforward") {
            RewindForwardRows(strings)
        }
        settingsBooleanPref(C.PLAYER_CHANNEL, true, strings)
        settingsBooleanPref(C.PLAYER_TITLE, true, strings)
        settingsBooleanPref(C.PLAYER_CATEGORY, true, strings)
        settingsBooleanPref(C.PLAYER_SHOW_UPTIME, true, strings)
        settingsBooleanPref(C.PLAYER_VIEWER_LIST, false, strings)
        settingsBooleanPref(C.PLAYER_VIEWER_ICON, true, strings)
        settingsBooleanPref(C.PLAYER_PAUSE, false, strings)
    }
}

@Composable
private fun RewindForwardRows(strings: SettingsStrings) {
    val rewind = rememberStringSetting(C.PLAYER_REWIND, "10")
    val forward = rememberStringSetting(C.PLAYER_FORWARD, "10")
    var open by remember { mutableStateOf<String?>(null) }
    SecondsRow(strings.title(C.PLAYER_REWIND), strings.seconds(rewind.value ?: "10")) { open = C.PLAYER_REWIND }
    SecondsRow(strings.title(C.PLAYER_FORWARD), strings.seconds(forward.value ?: "10")) { open = C.PLAYER_FORWARD }
    when (open) {
        C.PLAYER_REWIND -> SettingsTextDialog(
            title = strings.title(C.PLAYER_REWIND),
            initial = rewind.value.orEmpty(),
            confirmLabel = strings.ok,
            dismissLabel = strings.cancel,
            onConfirm = { rewind.value = it; open = null },
            onDismiss = { open = null },
        )
        C.PLAYER_FORWARD -> SettingsTextDialog(
            title = strings.title(C.PLAYER_FORWARD),
            initial = forward.value.orEmpty(),
            confirmLabel = strings.ok,
            dismissLabel = strings.cancel,
            onConfirm = { forward.value = it; open = null },
            onDismiss = { open = null },
        )
    }
}

@Composable
private fun SecondsRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider()
}

/** Mirrors `player_menu_preferences.xml` (pure switches). */
@Composable
fun PlayerMenuSettingsScreen(
    strings: SettingsStrings,
    modifier: Modifier = Modifier,
) {
    SettingsList(modifier = modifier) {
        settingsBooleanPref(C.PLAYER_MENU_QUALITY, false, strings)
        settingsBooleanPref(C.PLAYER_MENU_SPEED, false, strings)
        settingsBooleanPref(C.PLAYER_MENU_VIEWER_LIST, true, strings)
        settingsBooleanPref(C.PLAYER_MENU_GAMES, false, strings)
        settingsBooleanPref(C.PLAYER_MENU_DOWNLOAD, true, strings)
        settingsBooleanPref(C.PLAYER_MENU_BOOKMARK, true, strings)
        settingsBooleanPref(C.PLAYER_MENU_SHARE, true, strings)
        settingsBooleanPref(C.PLAYER_MENU_FIND_VOD, true, strings)
        settingsBooleanPref(C.PLAYER_MENU_SLEEP, true, strings)
        settingsBooleanPref(C.PLAYER_MENU_ASPECT, false, strings)
        settingsBooleanPref(C.PLAYER_MENU_VOLUME, false, strings)
        settingsBooleanPref(C.PLAYER_MENU_SUBTITLES, true, strings)
        settingsBooleanPref(C.PLAYER_MENU_RESTART, false, strings)
        settingsBooleanPref(C.PLAYER_MENU_CHAT_BAR, true, strings)
        settingsBooleanPref(C.PLAYER_MENU_CHAT_TOGGLE, false, strings)
        settingsBooleanPref(C.PLAYER_MENU_RELOAD_EMOTES, true, strings)
        settingsBooleanPref(C.PLAYER_MENU_CHAT_DISCONNECT, true, strings)
    }
}
