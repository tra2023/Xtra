package com.github.andreyasadchy.xtra

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.github.andreyasadchy.xtra.ui.createXtraImageLoader

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

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return createXtraImageLoader(context, debug = BuildConfig.DEBUG)
    }
}
