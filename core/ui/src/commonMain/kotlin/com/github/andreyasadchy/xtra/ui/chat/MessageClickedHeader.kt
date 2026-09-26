package com.github.andreyasadchy.xtra.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.ui.XtraAsyncImage

/**
 * Inspected-user header of the message dialog, matching the `userLayout` part of
 * `dialog_chat_message_click.xml`: the banner image behind the avatar, followed by the user
 * name, the account creation date and the follow date.
 */
@Composable
fun MessageClickedHeader(
    user: User?,
    userFailed: Boolean,
    nameDisplay: String?,
    roundUserImage: Boolean,
    createdAtLabel: (String?) -> String,
    followedAtLabel: (String?) -> String,
    modifier: Modifier = Modifier,
    onViewProfile: ((User) -> Unit)? = null,
) {
    if (user != null && (user.bannerImageURL != null || user.profileImage != null || user.name != null || user.createdAt != null || user.followedAt != null)) {
        val hasBanner = user.bannerImageURL != null
        Column(modifier = modifier.fillMaxSize()) {
            if (hasBanner) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(MaterialTheme.shapes.extraLarge),
                ) {
                    XtraAsyncImage(
                        model = user.bannerImageURL,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Same 55% black overlay the layout applies on top of the banner.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                user.profileImage?.let { profileImage ->
                    XtraAsyncImage(
                        model = profileImage,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        circleCrop = roundUserImage,
                        modifier = Modifier
                            .padding(vertical = 5.dp)
                            .size(48.dp)
                            .clickable(enabled = onViewProfile != null) { onViewProfile?.invoke(user) },
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    user.name?.let { name ->
                        Text(
                            text = if (user.login != null && !user.login.equals(name, true)) {
                                when (nameDisplay) {
                                    "0" -> "$name(${user.login})"
                                    "1" -> name
                                    else -> user.login.orEmpty()
                                }
                            } else {
                                name
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = if (hasBanner) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.clickable(enabled = onViewProfile != null) { onViewProfile?.invoke(user) },
                        )
                    }
                    user.createdAt?.let { createdAt ->
                        Text(
                            text = createdAtLabel(createdAt),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (hasBanner) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    user.followedAt?.let { followedAt ->
                        Text(
                            text = followedAtLabel(followedAt),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (hasBanner) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    } else if (userFailed) {
        // Error path of `updateUserLayout`: the server returned an error and no user data.
    }
}
