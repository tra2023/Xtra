package com.github.andreyasadchy.xtra.ui.channel

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.ui.XtraAsyncImage
import com.github.andreyasadchy.xtra.ui.common.LocalXtraStrings
import com.github.andreyasadchy.xtra.ui.common.displayName
import com.github.andreyasadchy.xtra.ui.settings.LocalXtraSettings
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.formatChatDate
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Collapsible channel header content: banner image with the channel identity
 * overlaid, then the live stream details (title, game, viewers, uptime) and the
 * watch-live affordance. The collapsing container lives with the caller (see
 * `CollapsingBanner`); this is just the content.
 */
@Composable
fun ChannelBannerContent(
    stream: Stream?,
    user: User?,
    fallbackName: String?,
    fallbackImage: String?,
    onWatchLive: () -> Unit,
    onGameClick: (Stream) -> Unit,
) {
    val settings = LocalXtraSettings.current
    val strings = LocalXtraStrings.current
    val nameDisplay = settings.getString(C.UI_NAME_DISPLAY, "0") ?: "0"
    val name = displayName(
        user?.name ?: stream?.channelName ?: fallbackName,
        user?.login ?: stream?.channelLogin,
        nameDisplay,
    ) ?: fallbackName
    val image = user?.profileImage ?: stream?.channelImage ?: fallbackImage
    val banner = user?.bannerImageURL
    val onBanner = banner != null
    val roundImage = settings.getBoolean(C.UI_ROUND_USER_IMAGE, true)

    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth()) {
            if (banner != null) {
                XtraAsyncImage(
                    model = banner,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(Modifier.matchParentSize().background(Color(0x8C000000)))
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (image != null) {
                    XtraAsyncImage(
                        model = image,
                        contentDescription = name,
                        modifier = Modifier.size(100.dp),
                        circleCrop = roundImage,
                    )
                }
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    if (!name.isNullOrBlank()) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleMedium.withBannerShadow(onBanner),
                            color = if (onBanner) Color.White else Color.Unspecified,
                        )
                    }
                    val createdAt = remember(user?.createdAt) { user?.createdAt.toChatDate() }
                    if (createdAt != null) {
                        Text(
                            text = stringResource(R.string.created_at, createdAt),
                            style = MaterialTheme.typography.bodyMedium.withBannerShadow(onBanner),
                            color = if (onBanner) Color.LightGray else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val followers = user?.followerCount
                    if (followers != null) {
                        Text(
                            text = strings.followers(followers),
                            style = MaterialTheme.typography.bodyMedium.withBannerShadow(onBanner),
                            color = if (onBanner) Color.LightGray else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val broadcasterType = when (user?.broadcasterType?.lowercase()) {
                        "partner" -> stringResource(R.string.user_partner)
                        "affiliate" -> stringResource(R.string.user_affiliate)
                        else -> null
                    }
                    val type = when (user?.type?.lowercase()) {
                        "staff" -> stringResource(R.string.user_staff)
                        else -> null
                    }
                    val typeString = if (broadcasterType != null && type != null) "$broadcasterType, $type" else broadcasterType ?: type
                    if (typeString != null) {
                        Text(
                            text = typeString,
                            style = MaterialTheme.typography.bodyMedium.withBannerShadow(onBanner),
                            color = if (onBanner) Color.LightGray else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        val title = stream?.title?.trim()?.takeIf { it.isNotBlank() }
        val gameName = stream?.gameName?.takeIf { it.isNotBlank() }
        val viewerCount = stream?.viewerCount
        val uptime = remember(stream?.createdAt) {
            if (!settings.getBoolean(C.UI_UPTIME, true)) null
            else stream?.createdAt.toUptime()
        }
        if (title != null || gameName != null || viewerCount != null || uptime != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp)) {
                Column(Modifier.weight(1f).padding(end = 5.dp)) {
                    if (title != null) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                    }
                    if (gameName != null) {
                        Text(
                            text = gameName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable(enabled = true) { stream.let(onGameClick) },
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (viewerCount != null) {
                        Text(strings.viewers(viewerCount), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (uptime != null) {
                        Text(strings.uptime(uptime), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        if (viewerCount == null) {
            val lastBroadcast = remember(user?.lastBroadcast) { user?.lastBroadcast.toChatDate() }
            if (lastBroadcast != null) {
                Text(
                    text = stringResource(R.string.last_broadcast_date, lastBroadcast),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                )
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(50.dp).clickable(onClick = onWatchLive),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(if (viewerCount != null) R.string.watch_live else R.string.open_player),
                style = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center),
            )
        }
    }
}

private fun androidx.compose.ui.text.TextStyle.withBannerShadow(onBanner: Boolean) =
    if (onBanner) copy(shadow = Shadow(Color.Black, Offset.Zero, 4f)) else this

private fun String?.toChatDate(): String? =
    this?.let { Instant.parseOrNull(it)?.toEpochMilliseconds()?.takeIf { ms -> ms > 0 }?.let(::formatChatDate) }

private fun String?.toUptime(): String? =
    this?.let { Instant.parseOrNull(it)?.takeIf { time -> time.toEpochMilliseconds() > 0 }?.let { createdAt ->
        val uptime = Clock.System.now() - createdAt
        if (uptime.isPositive()) DateUtils.formatElapsedTime(uptime.inWholeSeconds) else null
    } }
