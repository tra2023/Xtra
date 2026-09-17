package com.github.andreyasadchy.xtra.ui.media

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade

data class MediaRowData(
    val thumbnail: String?,
    val title: String?,
    val channelName: String?,
    val channelImage: String?,
    val gameName: String?,
    val date: String?,
    val views: String?,
    val duration: String?,
    val type: String? = null,
    val progress: Float? = null,
)

data class MediaRowAction(
    val text: String,
    val onClick: () -> Unit,
)

@Composable
fun MediaRow(
    data: MediaRowData,
    optionsText: String,
    downloadText: String,
    actions: List<MediaRowAction>,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onChannelClick: () -> Unit,
    onGameClick: () -> Unit,
    modifier: Modifier = Modifier,
    roundChannelImage: Boolean = true,
    cardMargin: Dp = 8.dp,
    cornerRadius: Dp = 12.dp,
    compactText: Boolean = false,
    material3: Boolean = true,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalPlatformContext.current
    val thumbnailRequest = remember(context, data.thumbnail) {
        ImageRequest.Builder(context)
            .data(data.thumbnail)
            .diskCachePolicy(CachePolicy.DISABLED)
            .crossfade(true)
            .build()
    }
    val channelRequest = remember(context, data.channelImage) {
        ImageRequest.Builder(context)
            .data(data.channelImage)
            .crossfade(true)
            .build()
    }
    val titleStyle = MaterialTheme.typography.titleMedium.let {
        if (compactText) it.copy(lineHeight = 18.sp, letterSpacing = 0.sp) else it
    }
    val bodyStyle = MaterialTheme.typography.bodyMedium.let {
        if (compactText) it.copy(lineHeight = 16.sp, letterSpacing = 0.sp) else it
    }
    ElevatedCard(
        modifier = modifier.fillMaxWidth().padding(cardMargin),
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (material3) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (material3) 1.dp else 0.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().combinedClickable(
                onClick = onOpen,
                onLongClickLabel = downloadText,
                onLongClick = onDownload,
            ),
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                AsyncImage(
                    model = thumbnailRequest,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
                Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(10.dp)) {
                    Box(Modifier.weight(1f)) {
                        data.duration?.let { ThumbnailLabel(it) }
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopEnd) {
                        data.type?.let { ThumbnailLabel(it) }
                    }
                }
                Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(5.dp)) {
                    Box(Modifier.weight(1f)) {
                        data.views?.let { ThumbnailLabel(it) }
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.BottomEnd) {
                        data.date?.let { ThumbnailLabel(it) }
                    }
                }
            }
            data.progress?.let { progress ->
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                }
            }
            Row(Modifier.fillMaxWidth()) {
                if (data.channelImage != null) {
                    AsyncImage(
                        model = channelRequest,
                        contentDescription = data.channelName,
                        contentScale = if (roundChannelImage) ContentScale.Crop else ContentScale.Fit,
                        modifier = Modifier.padding(start = 10.dp, top = 12.dp, bottom = 12.dp)
                            .size(50.dp)
                            .then(if (roundChannelImage) Modifier.clip(CircleShape) else Modifier)
                            .clickable(onClick = onChannelClick),
                    )
                }
                Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 7.dp)) {
                    data.title?.let { Text(text = it, style = titleStyle) }
                    data.channelName?.let {
                        Text(
                            text = it,
                            style = bodyStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable(onClick = onChannelClick),
                        )
                    }
                    data.gameName?.let {
                        Text(
                            text = it,
                            style = bodyStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable(onClick = onGameClick),
                        )
                    }
                }
                Box(Modifier.padding(top = 5.dp)) {
                    IconButton(onClick = { menuExpanded = true }) {
                        val color = MaterialTheme.colorScheme.onSurfaceVariant
                        Canvas(Modifier.size(24.dp).semantics { contentDescription = optionsText }) {
                            for (position in 1..3) {
                                drawCircle(color = color, radius = 2.dp.toPx(), center = Offset(size.width / 2, size.height * position / 4))
                            }
                        }
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        actions.forEach { action ->
                            DropdownMenuItem(
                                text = { Text(action.text) },
                                onClick = {
                                    menuExpanded = false
                                    action.onClick()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThumbnailLabel(text: String) {
    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, shadow = Shadow(Color.Black, blurRadius = 4f)),
        modifier = Modifier.background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(2.dp)).padding(horizontal = 2.dp),
    )
}
