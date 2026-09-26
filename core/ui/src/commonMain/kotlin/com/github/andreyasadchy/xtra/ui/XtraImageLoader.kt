package com.github.andreyasadchy.xtra.ui

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.annotation.ExperimentalCoilApi
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.util.DebugLogger
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

/**
 * Shared Coil [ImageLoader] factory for Android and JVM desktop.
 *
 * Uses Ktor + CIO so the same code runs on Android (`XtraApp`) and a future
 * desktop `main()` via `setSingletonImageLoaderFactory { createXtraImageLoader(it) }`.
 *
 * Animated images (GIF / animated WebP) are decoded through the platform
 * [xtraAnimatedImageDecoderFactory], which is Android-only for now, so small animated
 * emotes come back without re-adding the Android-only `coil-gif` artifact to common code.
 */
@OptIn(ExperimentalCoilApi::class)
fun createXtraImageLoader(context: PlatformContext, debug: Boolean = false): ImageLoader {
    return ImageLoader.Builder(context)
        .apply {
            if (debug) logger(DebugLogger())
        }
        .components {
            add(
                KtorNetworkFetcherFactory(
                    httpClient = { HttpClient(CIO) },
                    cacheStrategy = { CacheControlCacheStrategy() }
                )
            )
            xtraAnimatedImageDecoderFactory()?.let { add(it) }
        }
        .build()
}
