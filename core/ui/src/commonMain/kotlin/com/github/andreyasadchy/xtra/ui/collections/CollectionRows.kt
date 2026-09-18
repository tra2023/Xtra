package com.github.andreyasadchy.xtra.ui.collections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade

val LocalCollectionCardMargin = compositionLocalOf { 8.dp }
val LocalCollectionCardShape = compositionLocalOf<Shape> { RoundedCornerShape(12.dp) }
val LocalCollectionCardElevation = compositionLocalOf { 1.dp }
val LocalCollectionCardColor = compositionLocalOf { Color.Unspecified }

@Composable
private fun CollectionCard(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.padding(LocalCollectionCardMargin.current).fillMaxWidth(),
        shape = LocalCollectionCardShape.current,
        color = LocalCollectionCardColor.current.takeUnless { it == Color.Unspecified }
            ?: MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = LocalCollectionCardElevation.current,
    ) {
        Column(Modifier.clickable(onClick = onClick)) {
            content()
        }
    }
}

@Composable
private fun CollectionImage(
    image: String,
    width: Dp,
    height: Dp = width,
    round: Boolean = false,
    diskCache: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalPlatformContext.current)
            .data(image)
            .diskCachePolicy(if (diskCache) CachePolicy.ENABLED else CachePolicy.DISABLED)
            .crossfade(true)
            .build(),
        contentDescription = null,
        contentScale = if (round) ContentScale.Crop else ContentScale.Fit,
        modifier = Modifier.size(width, height)
            .then(if (round) Modifier.clip(CircleShape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    )
}

@Composable
private fun Detail(text: String?, modifier: Modifier = Modifier, singleLine: Boolean = false) {
    if (text != null) {
        Text(
            text = text,
            modifier = modifier,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (singleLine) 1 else Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Labels(labels: List<String>, modifier: Modifier = Modifier) {
    if (labels.isNotEmpty()) {
        Column(modifier, horizontalAlignment = Alignment.End) {
            labels.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun <T> CollectionTags(tags: List<T>, label: (T) -> String, enabled: (T) -> Boolean, select: (T) -> Unit) {
    if (tags.isNotEmpty()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            tags.forEach { tag ->
                Text(
                    text = label(tag),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .then(if (enabled(tag)) Modifier.clickable { select(tag) } else Modifier)
                        .padding(horizontal = 5.dp),
                )
            }
        }
    }
}

@Composable
fun <T> GameCollectionRow(
    name: String?,
    image: String?,
    viewers: String?,
    broadcasters: String?,
    tags: List<T>,
    tagLabel: (T) -> String,
    tagEnabled: (T) -> Boolean,
    onTagClick: (T) -> Unit,
    onClick: () -> Unit,
    followLabels: List<String> = emptyList(),
    diskCache: Boolean = true,
) {
    CollectionCard(onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (image != null) {
                CollectionImage(image, 49.dp, 65.dp, diskCache = diskCache)
            }
            Column(Modifier.weight(1f)) {
                if (name != null) Text(name, style = MaterialTheme.typography.titleMedium)
                Detail(viewers)
                Detail(broadcasters)
            }
            Labels(followLabels, Modifier.align(Alignment.Bottom))
        }
        CollectionTags(tags, tagLabel, tagEnabled, onTagClick)
    }
}

@Composable
fun ChannelCollectionRow(
    name: String?,
    image: String?,
    roundImage: Boolean,
    details: List<String>,
    labels: List<String>,
    onClick: () -> Unit,
) {
    CollectionCard(onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (image != null) CollectionImage(image, 50.dp, round = roundImage)
            Column(Modifier.weight(1f)) {
                if (name != null) Text(name, style = MaterialTheme.typography.titleMedium)
                details.forEach { Detail(it) }
            }
            Labels(labels, Modifier.align(Alignment.Bottom))
        }
    }
}

@Composable
fun TeamMemberCollectionRow(
    name: String?,
    image: String?,
    roundImage: Boolean,
    title: String?,
    game: String?,
    viewers: String?,
    uptime: String?,
    tags: List<String>,
    onClick: () -> Unit,
    onChannelClick: () -> Unit,
    onGameClick: () -> Unit,
    onTagClick: (String) -> Unit,
) {
    CollectionCard(onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (image != null) CollectionImage(image, 50.dp, round = roundImage, onClick = onChannelClick)
            Column(Modifier.weight(1f)) {
                if (title != null) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Detail(name, Modifier.clickable(onClick = onChannelClick), singleLine = true)
                Detail(game, Modifier.clickable(onClick = onGameClick), singleLine = true)
            }
            Labels(listOfNotNull(viewers, uptime), Modifier.align(Alignment.Bottom))
        }
        CollectionTags(tags, { it }, { true }, onTagClick)
    }
}

@Composable
fun RecentSearchCollectionRow(
    query: String,
    historyIcon: Painter,
    deleteIcon: Painter,
    deleteLabel: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(historyIcon, contentDescription = null, modifier = Modifier.size(24.dp))
        Detail(query, Modifier.weight(1f).padding(horizontal = 8.dp), singleLine = true)
        IconButton(onClick = onDelete) {
            Icon(deleteIcon, contentDescription = deleteLabel, modifier = Modifier.size(24.dp))
        }
    }
}
