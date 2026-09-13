plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.github.andreyasadchy.xtra.shared"
        compileSdk = 37
        minSdk = 36
    }
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(project(":core:models"))
            api(project(":core:network"))
            api(project(":core:database"))
            api(project(":core:ui"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.coroutines.core)
            implementation(libs.paging.common)
            implementation(libs.serialization.json)
        }
    }
}
