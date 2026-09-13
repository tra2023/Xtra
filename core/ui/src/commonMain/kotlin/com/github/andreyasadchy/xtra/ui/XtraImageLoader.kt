package com.github.andreyasadchy.xtra.ui

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.annotation.ExperimentalCoilApi
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.network.ktor3.KtorNetworkFetcherFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

/**
 * Shared Coil [ImageLoader] factory for Android and JVM desktop.
 *
 * Uses Ktor + CIO so the same code runs on Android (`XtraApp`) and a future
 * desktop `main()` via `setSingletonImageLoaderFactory { createXtraImageLoader(it) }`.
 */
@OptIn(ExperimentalCoilApi::class)
fun createXtraImageLoader(context: PlatformContext): ImageLoader {
    return ImageLoader.Builder(context)
        .components {
            add(
                KtorNetworkFetcherFactory(
                    httpClient = { HttpClient(CIO) },
                    cacheStrategy = { CacheControlCacheStrategy() }
                )
            )
        }
        .build()
}
