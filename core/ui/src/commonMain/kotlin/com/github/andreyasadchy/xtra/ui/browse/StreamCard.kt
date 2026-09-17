package com.github.andreyasadchy.xtra.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.ui.XtraAsyncImage
import kotlin.time.Clock

data class StreamCardState(
    val title: String?,
    val channelName: String?,
    val channelImage: String?,
    val gameName: String?,
    val thumbnail: String?,
    val viewers: String?,
    val uptime: String?,
    val tags: List<String>,
    val thumbnailCacheKey: String,
)

fun streamCardState(
    stream: Stream,
    nameDisplay: String = "0",
    showGame: Boolean = true,
    showTags: Boolean = true,
    viewers: String? = stream.viewerCount?.toString(),
    uptime: String? = null,
    nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
): StreamCardState = StreamCardState(
    title = stream.title?.trim()?.takeIf { it.isNotEmpty() },
    channelName = stream.channelName?.let { name ->
        if (stream.channelLogin != null && !stream.channelLogin.equals(name, true)) {
            when (nameDisplay) {
                "0" -> "$name(${stream.channelLogin})"
                "1" -> name
                else -> stream.channelLogin
            }
        } else name
    },
    channelImage = stream.channelImage,
    gameName = stream.gameName.takeIf { showGame },
    thumbnail = stream.thumbnail.takeIf { stream.thumbnailURL != null },
    viewers = viewers,
    uptime = uptime,
    tags = if (showTags) stream.tags.orEmpty().toList() else emptyList(),
    thumbnailCacheKey = ((nowMillis / 60000L) / 5L * 5L).toString(),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StreamCard(
    state: StreamCardState,
    onStreamClick: () -> Unit,
    onChannelClick: () -> Unit,
    onGameClick: () -> Unit,
    onTagClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    roundUserImage: Boolean = true,
    cardMargin: Dp = 8.dp,
    cornerRadius: Dp = 12.dp,
    compactText: Boolean = false,
) {
    val titleStyle = MaterialTheme.typography.titleMedium.let {
        if (compactText) it.copy(letterSpacing = 0.sp, lineHeight = 18.sp) else it
    }
    val bodyStyle = MaterialTheme.typography.bodyMedium.let {
        if (compactText) it.copy(letterSpacing = 0.sp, lineHeight = 16.sp) else it
    }
    ElevatedCard(
        onClick = onStreamClick,
        modifier = modifier.fillMaxWidth().padding(cardMargin),
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        if (!compact && state.thumbnail != null) {
            Box {
                val context = LocalPlatformContext.current
                val request = remember(context, state.thumbnail, state.thumbnailCacheKey) {
                    ImageRequest.Builder(context)
                        .data(state.thumbnail)
                        .memoryCacheKeyExtra("minutes", state.thumbnailCacheKey)
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                )
                Row(
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val overlayStyle = bodyStyle.copy(fontSize = 13.sp, shadow = Shadow(Color.Black, blurRadius = 4f))
                    Text(text = state.viewers.orEmpty(), style = overlayStyle, color = Color.White)
                    Text(text = state.uptime.orEmpty(), style = overlayStyle, color = Color.White)
                }
            }
        } else if (!compact && (state.viewers != null || state.uptime != null)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = state.viewers.orEmpty(), style = bodyStyle)
                Text(text = state.uptime.orEmpty(), style = bodyStyle)
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = if (compact) 5.dp else 10.dp)) {
            if (state.channelImage != null) {
                XtraAsyncImage(
                    model = state.channelImage,
                    contentDescription = state.channelName,
                    circleCrop = roundUserImage,
                    contentScale = if (roundUserImage) ContentScale.Crop else ContentScale.Fit,
                    modifier = Modifier.padding(top = 12.dp, bottom = 12.dp, end = 10.dp)
                        .size(50.dp).clickable(onClick = onChannelClick),
                )
            }
            Column(modifier = Modifier.weight(1f).padding(vertical = 7.dp)) {
                state.title?.let {
                    Text(text = it, style = titleStyle, maxLines = if (compact) 1 else 3, overflow = TextOverflow.Ellipsis)
                }
                state.channelName?.let {
                    Text(
                        text = it,
                        style = bodyStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (compact) 1 else Int.MAX_VALUE,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(onClick = onChannelClick),
                    )
                }
                state.gameName?.let {
                    Text(
                        text = it,
                        style = bodyStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (compact) 1 else Int.MAX_VALUE,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(onClick = onGameClick),
                    )
                }
            }
            if (compact && (state.viewers != null || state.uptime != null)) {
                Column(
                    modifier = Modifier.align(Alignment.Bottom).padding(start = 5.dp, top = 7.dp, bottom = 7.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    state.viewers?.let { Text(text = it, style = bodyStyle) }
                    state.uptime?.let { Text(text = it, style = bodyStyle) }
                }
            }
        }
        if (state.tags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                state.tags.forEach { tag ->
                    Text(
                        text = tag,
                        style = bodyStyle,
                        modifier = Modifier.clickable { onTagClick(tag) }.padding(horizontal = 5.dp),
                    )
                }
            }
        }
    }
}
