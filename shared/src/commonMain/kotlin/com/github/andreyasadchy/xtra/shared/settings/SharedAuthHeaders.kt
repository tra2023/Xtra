package com.github.andreyasadchy.xtra.shared.settings

import com.github.andreyasadchy.xtra.repository.TwitchAuthHeaders
import com.github.andreyasadchy.xtra.repository.TwitchHeaders
import com.github.andreyasadchy.xtra.util.C

/**
 * Shared header builder. Mirrors `TwitchApiHelper.getGQLHeaders/getHelixHeaders`
 * from `:app` but takes an [AuthConfig] instead of Android Context, so it runs
 * unchanged on Android and JVM desktop.
 */
object SharedAuthHeaders {

    fun loadConfig(settings: XtraSettings): AuthConfig {
        // Token prefs keys live in the "prefs2" file on Android; the
        // AndroidXtraSettings impl merges both files, desktop reads one store.
        return AuthConfig(
            gqlClientId = settings.getString(C.GQL_CLIENT_ID2, TwitchHeaders.DEFAULT_GQL_CLIENT_ID),
            gqlToken = settings.getString(C.GQL_TOKEN2, null),
            gqlHeadersJson = settings.getString(C.GQL_HEADERS, null),
            helixClientId = settings.getString(C.HELIX_CLIENT_ID, TwitchHeaders.DEFAULT_HELIX_CLIENT_ID),
            helixToken = settings.getString(C.TOKEN, null),
            enableIntegrity = settings.getBoolean(C.ENABLE_INTEGRITY, false),
            compactStreams = settings.getString(C.COMPACT_STREAMS, "disabled"),
        )
    }

    fun gqlHeaders(config: AuthConfig): Map<String, String> =
        TwitchAuthHeaders.getGqlHeaders(
            enableIntegrity = config.enableIntegrity,
            integrityHeadersJson = config.gqlHeadersJson,
            gqlClientId = config.gqlClientId,
            gqlToken = config.gqlToken,
        )

    fun helixHeaders(config: AuthConfig): Map<String, String> =
        TwitchAuthHeaders.getHelixHeaders(
            helixClientId = config.helixClientId,
            token = config.helixToken,
        )
}
