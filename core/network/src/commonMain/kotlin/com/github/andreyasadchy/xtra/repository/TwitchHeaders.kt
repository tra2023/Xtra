package com.github.andreyasadchy.xtra.repository

import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchImageUrls
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Pure-Kotlin builders for the Twitch API header maps.
 * Previously part of the app's TwitchApiHelper; the Android layer
 * (SharedPreferences lookups) stays in the app, which delegates here.
 */
object TwitchHeaders {

    const val DEFAULT_GQL_CLIENT_ID = "kd1unb4b3q4t58fwlpcbzcbnm76a8fp"
    const val DEFAULT_HELIX_CLIENT_ID = "ilfexgv3nnljz3isbm257gzwrzr7bi"

    fun getGqlHeaders(clientId: String?, token: String?, includeToken: Boolean = false): Map<String, String> {
        return mutableMapOf<String, String>().apply {
            clientId?.takeIf { it.isNotBlank() }?.let {
                put(C.HEADER_CLIENT_ID, it)
            }
            if (includeToken) {
                token?.takeIf { it.isNotBlank() }?.let {
                    put(C.HEADER_TOKEN, TwitchImageUrls.addTokenPrefixGQL(it))
                }
            }
        }
    }

    fun parseIntegrityHeaders(jsonString: String?): Map<String, String> {
        if (jsonString.isNullOrBlank()) return emptyMap()
        return try {
            Json.parseToJsonElement(jsonString).jsonObject.entries.associate { (key, value) ->
                key to ((value as? JsonPrimitive)?.content ?: "")
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun getHelixHeaders(clientId: String?, token: String?): Map<String, String> {
        return mutableMapOf<String, String>().apply {
            clientId?.takeIf { it.isNotBlank() }?.let {
                put(C.HEADER_CLIENT_ID, it)
            }
            token?.takeIf { it.isNotBlank() }?.let {
                put(C.HEADER_TOKEN, TwitchImageUrls.addTokenPrefixHelix(it))
            }
        }
    }
}
