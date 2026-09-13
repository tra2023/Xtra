package com.github.andreyasadchy.xtra

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.util.DebugLogger
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

class XtraApp : Application(), SingletonImageLoader.Factory {

    companion object {
        lateinit var INSTANCE: Application
    }

    lateinit var xtraModule: XtraModule

    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
        xtraModule = XtraModule(this)
    }

    @OptIn(ExperimentalCoilApi::class)
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context).apply {
            if (BuildConfig.DEBUG) {
                logger(DebugLogger())
            }
            components {
                // Ktor + CIO works on Android and JVM desktop (Compose Multiplatform).
                // The rest of the app still uses OkHttp for API/downloads — only images go via Ktor.
                add(KtorNetworkFetcherFactory(
                    httpClient = { HttpClient(CIO) },
                    cacheStrategy = { CacheControlCacheStrategy() }
                ))
            }
        }.build()
    }
}
