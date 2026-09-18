package com.github.andreyasadchy.xtra.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.ui.collections.ChannelCollectionRow
import com.github.andreyasadchy.xtra.ui.collections.collectionName
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs

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
    val context = LocalContext.current
    ChannelCollectionRow(
        name = context.collectionName(user.name, user.login),
        image = user.profileImage,
        roundImage = context.prefs().getBoolean(C.UI_ROUND_USER_IMAGE, true),
        details = details,
        labels = labels,
        onClick = { onClick(user) },
    )
}
