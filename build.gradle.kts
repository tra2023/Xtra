// Declare versions once in the root classloader so sibling KMP modules
// (:core:models, :core:database, ...) share the same plugin classes and
// build services (e.g. SwiftImportFingerprintedCoordinationService).
// Without this, `clean` fails with a `coordinationService` classloader mismatch.
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.jetbrains.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.androidx.room) apply false
    alias(libs.plugins.apollo) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.navigation.safeargs) apply false
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}