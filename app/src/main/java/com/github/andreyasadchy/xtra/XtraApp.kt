package com.github.andreyasadchy.xtra

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.util.DebugLogger
import com.github.andreyasadchy.xtra.util.coil.CacheControlCacheStrategy
import org.conscrypt.Conscrypt
import java.security.Security

class XtraApp : Application(), SingletonImageLoader.Factory {

    companion object {
        lateinit var INSTANCE: Application
    }

    lateinit var xtraModule: XtraModule

    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
        xtraModule = XtraModule(this)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val conscrypt = Conscrypt.newProvider()
            Security.insertProviderAt(conscrypt, 1)
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context).apply {
            if (BuildConfig.DEBUG) {
                logger(DebugLogger())
            }
            components {
                add(OkHttpNetworkFetcherFactory(
                    callFactory = { xtraModule.okHttpClient.value },
                    cacheStrategy = { CacheControlCacheStrategy() }
                ))
            }
        }.build()
    }
}
