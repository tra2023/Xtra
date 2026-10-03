package com.github.andreyasadchy.xtra.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
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
 * - `diskCachePolicy(DISABLED)` -> [diskCache] = false, for locally stored images.
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
    diskCache: Boolean = true,
    animate: Boolean = true,
) {
    val context = LocalPlatformContext.current
    val headers = httpHeaders
    // Rebuilding the request on every composition would invalidate Coil's remembered painter and
    // churn allocations, which is very visible while scrolling a chat full of emotes.
    val request = remember(context, model, crossfade, headers, diskCache, animate) {
        ImageRequest.Builder(context)
            .data(model)
            .diskCachePolicy(if (diskCache) CachePolicy.ENABLED else CachePolicy.DISABLED)
            .disableAnimatedEmotes(!animate)
            .apply {
                if (crossfade) crossfade(true)
                headers?.let { httpHeaders(it) }
            }
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = if (circleCrop) modifier.clip(CircleShape) else modifier,
        contentScale = contentScale,
    )
}
