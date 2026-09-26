package com.github.andreyasadchy.xtra.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.network.NetworkHeaders
import com.github.andreyasadchy.xtra.model.chat.Emote
import com.github.andreyasadchy.xtra.ui.XtraAsyncImage

/**
 * Shared emote grid for the chat emote panels: 32dp cells in an adaptive grid,
 * matching `fragment_emotes.xml` + `fragment_emotes_list_item.xml`.
 *
 * [imageUrl] resolves the density variant, so the caller owns the image-quality
 * preference. [thirdPartyUserAgent] is attached to the requests of emotes that
 * come from STV/BTTV/FFZ, which reject requests without it.
 */
@Composable
fun EmoteGrid(
    emotes: List<Emote>,
    imageUrl: (Emote) -> String?,
    onClick: (Emote) -> Unit,
    modifier: Modifier = Modifier,
    thirdPartyUserAgent: String? = null,
    animate: Boolean = true,
) {
    val thirdPartyHeaders = remember(thirdPartyUserAgent) {
        thirdPartyUserAgent?.let { agent ->
            NetworkHeaders.Builder().apply { add("User-Agent", agent) }.build()
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 50.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        items(emotes, key = { it.name ?: it.hashCode() }) { emote ->
            Box(
                Modifier
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .size(32.dp)
                    .clickable { onClick(emote) },
            ) {
                val url = imageUrl(emote)
                if (url != null) {
                    XtraAsyncImage(
                        model = url,
                        contentDescription = emote.name,
                        contentScale = ContentScale.Fit,
                        httpHeaders = if (emote.thirdParty) thirdPartyHeaders else null,
                        modifier = Modifier.fillMaxSize(),
                        animate = animate,
                    )
                }
            }
        }
    }
}
