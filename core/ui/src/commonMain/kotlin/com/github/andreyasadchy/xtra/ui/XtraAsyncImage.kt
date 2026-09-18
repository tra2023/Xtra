package com.github.andreyasadchy.xtra.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * Shared image composable for Android and future JVM desktop.
 *
 * Replacement for the ~25 `context.imageLoader.enqueue(ImageRequest...target(imageView))`
 * call sites in `:app` (e.g. `StreamsAdapter`, `ChatAdapterUtils`, `ChannelPagerFragment`).
 *
 * Mapping from the old View code:
 * - `crossfade(true)` -> [crossfade] (default true)
 * - `transformations(CircleCropTransformation())` -> [circleCrop] = true, implemented with
 *   `Modifier.clip(CircleShape)` because Coil's `CircleCropTransformation` is Android-only
 *   and has no commonMain equivalent.
 * - `target(imageView)` -> this composable itself (no target needed)
 * - `httpHeaders(...)` -> [httpHeaders], for hosts that reject requests without a
 *   User-Agent (the third-party emote providers).
 */
@Composable
fun XtraAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    circleCrop: Boolean = false,
    crossfade: Boolean = true,
    httpHeaders: NetworkHeaders? = null,
) {
    val context = LocalPlatformContext.current
    val headers = httpHeaders
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(model)
            .apply {
                if (crossfade) crossfade(true)
                headers?.let { httpHeaders(it) }
            }
            .build(),
        contentDescription = contentDescription,
        modifier = if (circleCrop) modifier.clip(CircleShape) else modifier,
        contentScale = contentScale,
    )
}
