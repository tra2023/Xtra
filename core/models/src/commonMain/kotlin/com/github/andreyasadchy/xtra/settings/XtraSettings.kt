package com.github.andreyasadchy.xtra.settings

/**
 * Platform-agnostic settings reader shared by Android and JVM desktop.
 *
 * Android impl reads from SharedPreferences (`prefs()` + `tokenPrefs()`),
 * JVM desktop impl reads from java.util.prefs / properties file.
 * Keys are the same [C] constants used by `:app` today.
 */
interface XtraSettings {
    fun getString(key: String, default: String?): String?
    fun getBoolean(key: String, default: Boolean): Boolean
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
