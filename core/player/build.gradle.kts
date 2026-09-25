plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    android {
        namespace = "com.github.andreyasadchy.xtra.player"
        compileSdk = 37
        minSdk = 36
    }
    jvm()

    sourceSets {
        commonMain.dependencies {
            // PlaybackEngine / PlayerController / PlaybackSession live in :core:database.
            api(project(":core:database"))
            api(project(":core:models"))
            // Exposed so Android/JVM hosts can render the surface and observe player state.
            api(libs.mediamp.all)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(libs.coroutines.core)
        }
        androidMain.dependencies {
            // The Android mediamp backend is ExoPlayer; used for the platform-only
            // escape hatches (volume, disabling the video track, HLS rendition
            // selection) that are not part of mediamp's common API.
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.exoplayer.hls)
        }
        // Desktop playback additionally needs a bundled MPV runtime at runtime, e.g.:
        //   jvmRuntimeOnly("org.openani.mediamp:mediamp-mpv-runtime-windows-x64:0.5.0")
        // It is intentionally not declared here because there is no desktop distribution
        // yet and the Linux artifact is GPLv3.
    }
}
