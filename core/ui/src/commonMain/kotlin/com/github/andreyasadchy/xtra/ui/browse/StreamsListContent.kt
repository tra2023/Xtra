package com.github.andreyasadchy.xtra.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.ui.XtraAsyncImage

/**
 * Shared top-streams list (see [GamesGridContent] for why this takes a plain
 * [List] instead of paging items). Mirrors the `StreamsAdapter` row: thumbnail,
 * avatar, title, channel name. Compact mode (`C.COMPACT_STREAMS == "all"`) stays
 * an Android-only adapter switch until desktop settings UI exists.
 */
@Composable
fun StreamsListContent(
    streams: List<Stream>,
    onStreamClick: (Stream) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        items(streams, key = { it.id ?: it.channelLogin ?: it.hashCode().toString() }) { stream ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clickable { onStreamClick(stream) },
            ) {
                Column {
                    XtraAsyncImage(
                        model = stream.thumbnail,
                        contentDescription = stream.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(8.dp),
                    ) {
                        XtraAsyncImage(
                            model = stream.channelImage,
                            contentDescription = stream.channelName,
                            circleCrop = true,
                            modifier = Modifier.size(40.dp),
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(
                                text = stream.title.orEmpty(),
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                            )
                            Text(
                                text = stream.channelName.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
