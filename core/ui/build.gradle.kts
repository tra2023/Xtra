plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    android {
        namespace = "com.github.andreyasadchy.xtra.ui"
        compileSdk = 37
        minSdk = 36
    }
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:models"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.paging.compose)
            implementation(libs.coil)
            implementation(libs.coil.compose)
            implementation(libs.coil.gif)
            implementation(libs.coil.network.cache.control)
            implementation(libs.coil.network.ktor3)
            implementation(libs.ktor.client.cio)
            implementation(libs.coroutines.core)
        }
    }
}
