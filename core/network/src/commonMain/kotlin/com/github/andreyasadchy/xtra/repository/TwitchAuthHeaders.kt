package com.github.andreyasadchy.xtra.repository

/**
 * Storage-free Twitch auth header builders shared by Android and JVM desktop.
 * Callers pass already-loaded values (SharedPreferences on Android, any
 * settings store on desktop); parsing/construction lives here next to [TwitchHeaders].
 */
object TwitchAuthHeaders {

    fun getGqlHeaders(
        enableIntegrity: Boolean,
        integrityHeadersJson: String?,
        gqlClientId: String?,
        gqlToken: String?,
        includeToken: Boolean = false,
    ): Map<String, String> {
        return if (enableIntegrity) {
            TwitchHeaders.parseIntegrityHeaders(integrityHeadersJson)
        } else {
            TwitchHeaders.getGqlHeaders(
                clientId = gqlClientId,
                token = gqlToken,
                includeToken = includeToken,
            )
        }
    }

    fun getHelixHeaders(helixClientId: String?, token: String?): Map<String, String> {
        return TwitchHeaders.getHelixHeaders(
            clientId = helixClientId,
            token = token,
        )
    }

    fun isIntegrityTokenExpired(nowMillis: Long, expirationMillis: Long): Boolean {
        return nowMillis >= expirationMillis
    }
}
