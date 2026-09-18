package com.github.andreyasadchy.xtra.ui.common

import androidx.compose.runtime.Composable
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.ui.collections.ChannelCollectionRow
import com.github.andreyasadchy.xtra.ui.settings.LocalXtraSettings
import com.github.andreyasadchy.xtra.util.C

/**
 * Shared channel/user row for the Compose paging lists. Callers build the
 * [details] and [labels] lines (follow dates, follower count, live badge).
 */
@Composable
fun UserListItem(
    user: User,
    details: List<String>,
    labels: List<String>,
    onClick: (User) -> Unit,
) {
    val settings = LocalXtraSettings.current
    ChannelCollectionRow(
        name = displayName(user.name, user.login, settings.getString(C.UI_NAME_DISPLAY, "0") ?: "0"),
        image = user.profileImage,
        roundImage = settings.getBoolean(C.UI_ROUND_USER_IMAGE, true),
        details = details,
        labels = labels,
        onClick = { onClick(user) },
    )
}
