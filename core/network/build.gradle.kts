plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.apollo)
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
            implementation(libs.apollo.api)
            implementation(libs.okio)
        }
    }
}

apollo {
    @Suppress("ApolloEndpointNotConfigured")
    service("service") {
        packageName.set("com.github.andreyasadchy.xtra.graphql")
    }
}
