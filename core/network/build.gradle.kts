plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.github.andreyasadchy.xtra.network"
        compileSdk = 37
        minSdk = 23
    }
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:models"))
            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)
        }
    }
}
