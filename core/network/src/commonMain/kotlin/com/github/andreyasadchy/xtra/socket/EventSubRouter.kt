package com.github.andreyasadchy.xtra.socket

import com.github.andreyasadchy.xtra.util.chat.asObjectOrNullCompat
import com.github.andreyasadchy.xtra.util.chat.intOrNullCompat
import com.github.andreyasadchy.xtra.util.chat.stringOrNullCompat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

sealed interface EventSubEvent {
    data object Ignore : EventSubEvent
    data class Welcome(val sessionId: String?, val keepaliveTimeoutMs: Long?) : EventSubEvent
    data class ChatMessage(val eventJson: String, val timestamp: String?) : EventSubEvent
    data class UserNotice(val eventJson: String, val timestamp: String?) : EventSubEvent
    data class ClearChat(val eventJson: String, val timestamp: String?) : EventSubEvent
    data class RoomState(val eventJson: String, val timestamp: String?) : EventSubEvent
    data object Keepalive : EventSubEvent
    data object Reconnect : EventSubEvent
}

/**
 * Pure EventSub WebSocket envelope routing (message dedup + session lifecycle).
 * Timers and transport stay platform-side; event payloads are passed through as
 * raw JSON strings for the existing kotlinx parsers.
 */
class EventSubRouter {

    private val json = Json { ignoreUnknownKeys = true }
    private val handledMessageIds = mutableListOf<String>()

    fun route(raw: String): EventSubEvent {
        try {
            if (raw.isBlank()) return EventSubEvent.Ignore
            val json = json.parseToJsonElement(raw).asObjectOrNullCompat() ?: return EventSubEvent.Ignore
            val metadata = json["metadata"]?.asObjectOrNullCompat()
            val messageId = metadata?.stringOrNullCompat("message_id")
            val timestamp = metadata?.stringOrNullCompat("message_timestamp")
            if (!messageId.isNullOrBlank()) {
                if (handledMessageIds.contains(messageId)) {
                    return EventSubEvent.Ignore
                } else {
                    handledMessageIds.add(messageId)
                    if (handledMessageIds.size > 200) {
                        handledMessageIds.removeAt(0)
                    }
                }
            }
            return when (metadata?.stringOrNullCompat("message_type")) {
                "notification" -> {
                    val payload = json["payload"]?.asObjectOrNullCompat()
                    val event = payload?.get("event")?.asObjectOrNullCompat()
                    if (event != null) {
                        when (metadata.stringOrNullCompat("subscription_type")) {
                            "channel.chat.message" -> EventSubEvent.ChatMessage(event.toString(), timestamp)
                            "channel.chat.notification" -> EventSubEvent.UserNotice(event.toString(), timestamp)
                            "channel.chat.clear" -> EventSubEvent.ClearChat(event.toString(), timestamp)
                            "channel.chat_settings.update" -> EventSubEvent.RoomState(event.toString(), timestamp)
                            else -> EventSubEvent.Ignore
                        }
                    } else EventSubEvent.Ignore
                }
                "session_keepalive" -> EventSubEvent.Keepalive
                "session_reconnect" -> EventSubEvent.Reconnect
                "session_welcome" -> {
                    val payload = json["payload"]?.asObjectOrNullCompat()
                    val session = payload?.get("session")?.asObjectOrNullCompat()
                    val keepaliveTimeoutMs = session?.intOrNullCompat("keepalive_timeout_seconds")
                        ?.takeIf { it > 0 }?.let { it * 1000L }
                    EventSubEvent.Welcome(
                        sessionId = session?.stringOrNullCompat("id"),
                        keepaliveTimeoutMs = keepaliveTimeoutMs,
                    )
                }
                else -> EventSubEvent.Ignore
            }
        } catch (e: Exception) {
            return EventSubEvent.Ignore
        }
    }
}
