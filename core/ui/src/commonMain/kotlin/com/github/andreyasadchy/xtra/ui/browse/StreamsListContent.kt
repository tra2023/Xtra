package com.github.andreyasadchy.xtra.ui.browse

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.model.ui.Stream

@Composable
fun StreamsListContent(
    streams: List<Stream>,
    onStreamClick: (Stream) -> Unit,
    modifier: Modifier = Modifier,
    onChannelClick: (Stream) -> Unit = {},
    onGameClick: (Stream) -> Unit = {},
    onTagClick: (String) -> Unit = {},
    compact: Boolean = false,
    roundUserImage: Boolean = true,
    cardState: (Stream) -> StreamCardState = { streamCardState(it) },
    listState: LazyListState = rememberLazyListState(),
    cardMargin: Dp = 8.dp,
    cornerRadius: Dp = 12.dp,
    compactText: Boolean = false,
) {
    LazyColumn(modifier = modifier, state = listState) {
        itemsIndexed(streams, key = { index, stream -> stream.id ?: stream.channelId ?: stream.channelLogin ?: "placeholder:$index" }) { _, stream ->
            StreamCard(
                state = cardState(stream),
                onStreamClick = { onStreamClick(stream) },
                onChannelClick = { onChannelClick(stream) },
                onGameClick = { onGameClick(stream) },
                onTagClick = onTagClick,
                compact = compact,
                roundUserImage = roundUserImage,
                cardMargin = cardMargin,
                cornerRadius = cornerRadius,
                compactText = compactText,
            )
        }
    }
}
