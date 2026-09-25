package com.github.andreyasadchy.xtra.settings

import kotlinx.coroutines.flow.Flow

/**
 * Platform-agnostic settings reader shared by Android and JVM desktop.
 *
 * Android impl reads from SharedPreferences (`prefs()` + `tokenPrefs()`),
 * JVM desktop impl reads from java.util.prefs / properties file.
 * Keys are the same [C] constants used by `:app` today.
 *
 * The write + observe API backs the Compose settings screens in `:core:ui`.
 * Reads stay synchronous (SharedPreferences / java.util.prefs semantics);
 * writes are synchronous too and [changes] emits the changed key so
 * composables can stay reactive without a Context.
 */
interface XtraSettings {
    fun getString(key: String, default: String?): String?
    fun getBoolean(key: String, default: Boolean): Boolean
    fun getInt(key: String, default: Int): Int
    fun putString(key: String, value: String?)
    fun putBoolean(key: String, value: Boolean)
    fun putInt(key: String, value: Int)
    fun remove(key: String)
    fun observeChanges(): Flow<String>
}

/**
 * Snapshot of everything browse screens need: client ids, tokens,
 * integrity flag + compact-streams display mode. Resolved once per
 * Pager creation so commonMain never touches Android Context.
 */
data class AuthConfig(
    val gqlClientId: String?,
    val gqlToken: String?,
    val gqlHeadersJson: String?,
    val helixClientId: String?,
    val helixToken: String?,
    val enableIntegrity: Boolean,
    val compactStreams: String?,
)
