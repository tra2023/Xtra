package com.github.andreyasadchy.xtra.ui

import coil3.Extras
import coil3.decode.Decoder
import coil3.getExtra
import coil3.request.ImageRequest
import coil3.request.Options

private const val ANIMATED_EMOTES_MEMORY_CACHE_KEY = "xtra_animated_emotes"

private val disableAnimatedEmotesKey = Extras.Key(default = false)

/**
 * Returns a Coil [Decoder.Factory] that decodes animated images (GIF / animated WebP), or
 * `null` if the current platform has no implementation.
 *
 * This is `expect/actual` so the Android implementation (which uses
 * `android.graphics.ImageDecoder`) stays isolated in `androidMain` and does not pull an
 * Android-only dependency into common code. Only small animated emotes rely on this; the
 * old full-size GIF chat messages are intentionally not restored.
 */
expect fun xtraAnimatedImageDecoderFactory(): Decoder.Factory?

/**
 * Opt out of animated decoding for this request, rendering animated emotes as their first
 * static frame instead.
 *
 * Surfaces that can start/stop the animation themselves (the chat message list) don't need
 * this, but Compose surfaces using Coil's `DrawablePainter` animate automatically, so this
 * lets them honor the "animated emotes" preference.
 */
fun ImageRequest.Builder.disableAnimatedEmotes(disable: Boolean = true) = apply {
    extras[disableAnimatedEmotesKey] = disable
    memoryCacheKeyExtra(ANIMATED_EMOTES_MEMORY_CACHE_KEY, (!disable).toString())
}

val Options.disableAnimatedEmotes: Boolean
    get() = getExtra(disableAnimatedEmotesKey)
