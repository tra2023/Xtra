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
        minSdk = 36
    }
    jvm()

    sourceSets {
        // JVM-only APIs (java.net sockets) shared by Android and JVM desktop.
        // Truly multiplatform code goes in commonMain; Compose UI in :core:ui.
        val jvmAndroidMain by creating {
            dependsOn(commonMain.get())
        }
        val androidMain by getting {
            dependsOn(jvmAndroidMain)
        }
        val jvmMain by getting {
            dependsOn(jvmAndroidMain)
        }
        commonMain.dependencies {
            implementation(project(":core:models"))
            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)
            implementation(libs.apollo.api)
            implementation(libs.okio)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.websockets)
        }
    }
}

apollo {
    @Suppress("ApolloEndpointNotConfigured")
    service("service") {
        packageName.set("com.github.andreyasadchy.xtra.graphql")
    }
}
