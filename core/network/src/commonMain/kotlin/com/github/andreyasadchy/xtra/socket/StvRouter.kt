package com.github.andreyasadchy.xtra.socket

import com.github.andreyasadchy.xtra.util.chat.asObjectOrNullCompat
import com.github.andreyasadchy.xtra.util.chat.intOrNullCompat
import com.github.andreyasadchy.xtra.util.chat.stringOrNullCompat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

sealed interface StvEvent {
    data object Ignore : StvEvent
    data class EmoteSetUpdate(val bodyJson: String) : StvEvent
    data class Cosmetic(val bodyJson: String) : StvEvent
    data class Entitlement(val bodyJson: String) : StvEvent
    data class Hello(val sessionId: String?) : StvEvent
    data object Reconnect : StvEvent
}

/**
 * Pure 7TV EventApi routing + subscribe-message building.
 * Transport and headers stay platform-side; bodies are passed through as raw
 * JSON strings for the existing parsers.
 */
object StvRouter {

    const val OPCODE_DISPATCH = 0
    const val OPCODE_HELLO = 1
    const val OPCODE_RECONNECT = 4
    const val OPCODE_SUBSCRIBE = 35

    private val json = Json { ignoreUnknownKeys = true }

    fun buildSubscribes(channelId: String): List<String> {
        return listOf(
            "emote_set.*",
            "cosmetic.*",
            "entitlement.*",
        ).map { type ->
            buildJsonObject {
                put("op", OPCODE_SUBSCRIBE)
                putJsonObject("d") {
                    put("type", type)
                    putJsonObject("condition") {
                        put("ctx", "channel")
                        put("platform", "TWITCH")
                        put("id", channelId)
                    }
                }
            }.toString()
        }
    }

    fun route(raw: String): StvEvent {
        try {
            if (raw.isBlank()) return StvEvent.Ignore
            val json = json.parseToJsonElement(raw).asObjectOrNullCompat() ?: return StvEvent.Ignore
            return when (json.intOrNullCompat("op")) {
                OPCODE_DISPATCH -> {
                    val data = json["d"]?.asObjectOrNullCompat()
                    val type = data?.stringOrNullCompat("type")
                    val body = data?.get("body")?.asObjectOrNullCompat()
                    if (type != null && body != null) {
                        when (type) {
                            "emote_set.update" -> StvEvent.EmoteSetUpdate(body.toString())
                            "cosmetic.create" -> StvEvent.Cosmetic(body.toString())
                            "entitlement.create" -> StvEvent.Entitlement(body.toString())
                            else -> StvEvent.Ignore
                        }
                    } else StvEvent.Ignore
                }
                OPCODE_HELLO -> {
                    val data = json["d"]?.asObjectOrNullCompat()
                    StvEvent.Hello(data?.stringOrNullCompat("session_id"))
                }
                OPCODE_RECONNECT -> StvEvent.Reconnect
                else -> StvEvent.Ignore
            }
        } catch (e: Exception) {
            return StvEvent.Ignore
        }
    }
}
