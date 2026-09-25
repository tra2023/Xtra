package com.github.andreyasadchy.xtra.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
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

/**
 * Mirrors `theme_preferences.xml`. Every change recreates the activity
 * (theme overlay switch) and marks the result changed.
 */
@Composable
fun ThemeSettingsScreen(
    strings: SettingsStrings,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val recreate: (Boolean) -> Unit = {
        onAction(SettingsAction.ResultChanged)
        onAction(SettingsAction.Recreate)
    }
    val recreateValue: (String) -> Unit = {
        onAction(SettingsAction.ResultChanged)
        onAction(SettingsAction.Recreate)
    }
    SettingsList(modifier = modifier) {
        settingsListPref(C.THEME, "0", strings, onCommitted = recreateValue)
        settingsBooleanPref(C.UI_THEME_FOLLOW_SYSTEM, false, strings, onChanged = recreate)
        settingsListPref(C.UI_THEME_DARK_ON, "0", strings, onCommitted = recreateValue)
        settingsListPref(C.UI_THEME_DARK_OFF, "2", strings, onCommitted = recreateValue)
        settingsListPref(C.UI_THEME_ROUNDED_CORNERS, "0", strings, onCommitted = recreateValue)
        settingsBooleanPref(C.UI_THEME_REDUCED_PADDING, false, strings, onChanged = recreate)
        settingsBooleanPref(C.UI_THEME_COMPACT_TEXT, false, strings, onChanged = recreate)
        settingsBooleanPref(C.UI_THEME_APPBAR_LIFT, true, strings, onChanged = recreate)
        settingsBooleanPref(C.UI_THEME_BOTTOM_NAV_COLOR, true, strings, onChanged = recreate)
        settingsBooleanPref(C.UI_THEME_MATERIAL3, true, strings, onChanged = recreate)
    }
}

/**
 * Mirrors `ui_preferences.xml`. Tab rows open the shared [TabListDialog];
 * display-affecting switches report [SettingsAction.ResultChanged].
 */
@Composable
fun UiSettingsScreen(
    strings: SettingsStrings,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tabDialog by remember { mutableStateOf<TabDialogRequest?>(null) }
    val resultChanged = { onAction(SettingsAction.ResultChanged) }
    SettingsList(modifier = modifier) {
        settingsListPref(C.PORTRAIT_COLUMN_COUNT, "1", strings, onCommitted = { resultChanged() })
        settingsListPref(C.LANDSCAPE_COLUMN_COUNT, "2", strings, onCommitted = { resultChanged() })
        settingsListPref(C.UI_FOLLOW_BUTTON, "0", strings)
        settingsBooleanPref(C.UI_ACTIVATE_NOTIFICATIONS_WHEN_FOLLOWING, true, strings)
        settingsListPref(C.UI_START_ON_FOLLOWED, "1", strings)
        tabRow("ui_navigation_tab_list_dialog", C.UI_NAVIGATION_TAB_LIST, C.DEFAULT_NAVIGATION_TAB_LIST, "navigation", strings) {
            tabDialog = it
        }
        tabRow("ui_following_tabs_dialog", C.UI_FOLLOWING_TABS, C.DEFAULT_FOLLOWING_TABS, "following", strings) {
            tabDialog = it
        }
        tabRow("ui_saved_tabs_dialog", C.UI_SAVED_TABS, C.DEFAULT_SAVED_TABS, "saved", strings) {
            tabDialog = it
        }
        tabRow("ui_channel_tabs_dialog", C.UI_CHANNEL_TABS, C.DEFAULT_CHANNEL_TABS, "channel", strings) {
            tabDialog = it
        }
        tabRow("ui_game_tabs_dialog", C.UI_GAME_TABS, C.DEFAULT_GAME_TABS, "game", strings) {
            tabDialog = it
        }
        tabRow("ui_search_tabs_dialog", C.UI_SEARCH_TABS, C.DEFAULT_SEARCH_TABS, "search", strings) {
            tabDialog = it
        }
        settingsListPref(C.COMPACT_STREAMS, "disabled", strings, onCommitted = { resultChanged() })
        settingsListPref(C.UI_NAME_DISPLAY, "0", strings)
        settingsBooleanPref(C.UI_ROUND_USER_IMAGE, true, strings, onChanged = { resultChanged() })
        settingsBooleanPref(C.UI_TRUNCATE_VIEW_COUNT, true, strings, onChanged = { resultChanged() })
        settingsBooleanPref(C.UI_UPTIME, true, strings, onChanged = { resultChanged() })
        settingsBooleanPref(C.UI_TAGS, true, strings, onChanged = { resultChanged() })
        settingsBooleanPref(C.UI_BROADCASTERS_COUNT, true, strings, onChanged = { resultChanged() })
        settingsBooleanPref("ui_followpager", true, strings)
        settingsBooleanPref("ui_savedpager", true, strings)
        settingsBooleanPref("ui_gamepager", true, strings)
        settingsBooleanPref(C.UI_BOOKMARK_TIME_LEFT, true, strings)
        settingsBooleanPref(C.UI_STORE_RECENT_SEARCHES, true, strings)
        item(key = "click:delete_recent_searches") {
            var confirm by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable(onClick = { confirm = true })
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(strings.title("delete_recent_searches"), style = MaterialTheme.typography.bodyLarge)
            }
            HorizontalDivider()
            if (confirm) {
                SettingsConfirmDialog(
                    message = strings.message("delete_recent_searches"),
                    confirmLabel = strings.yes,
                    dismissLabel = strings.no,
                    onConfirm = {
                        confirm = false
                        onAction(SettingsAction.DeleteSearches)
                    },
                    onDismiss = { confirm = false },
                )
            }
        }
    }
    tabDialog?.let { request ->
        TabListDialog(
            prefKey = request.prefKey,
            defaultValue = request.defaultValue,
            group = request.group,
            title = request.title,
            strings = strings,
            onChanged = { onAction(SettingsAction.ResultChanged) },
            onDismiss = { tabDialog = null },
        )
    }
}

internal data class TabDialogRequest(
    val prefKey: String,
    val defaultValue: String,
    val group: String,
    val title: String,
)

private fun LazyListScope.tabRow(
    dialogKey: String,
    prefKey: String,
    defaultValue: String,
    group: String,
    strings: SettingsStrings,
    onOpen: (TabDialogRequest) -> Unit,
) {
    settingsClick(
        key = dialogKey,
        title = strings.title(dialogKey),
        summary = null,
        onClick = { onOpen(TabDialogRequest(prefKey, defaultValue, group, strings.title(dialogKey))) },
    )
}
