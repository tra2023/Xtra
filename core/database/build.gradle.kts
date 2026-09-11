plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

kotlin {
    android {
        namespace = "com.github.andreyasadchy.xtra.database"
        compileSdk = 37
        minSdk = 23
    }
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.room.runtime)
            implementation(libs.room.paging)
            implementation(libs.sqlite.bundled)
            implementation(libs.coroutines.core)
            implementation(libs.paging.common)
            implementation(libs.serialization.json)
        }
    }
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspJvm", libs.room.compiler)
}

room {
    schemaDirectory("$projectDir/schemas")
}
