package com.github.andreyasadchy.xtra.socket

import com.github.andreyasadchy.xtra.util.chat.asObjectOrNullCompat
import com.github.andreyasadchy.xtra.util.chat.intOrNullCompat
import com.github.andreyasadchy.xtra.util.chat.stringOrNullCompat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Clock
import kotlin.uuid.Uuid

sealed interface HermesEvent {
    data object Ignore : HermesEvent
    data class Welcome(val keepaliveSec: Int?) : HermesEvent
    data class Playback(val messageJson: String) : HermesEvent
    data class StreamInfo(val messageJson: String) : HermesEvent
    data class Reward(val messageJson: String) : HermesEvent
    data class PointsEarned(val messageJson: String) : HermesEvent
    data object ClaimAvailable : HermesEvent
    data class Raid(val messageJson: String, val openStream: Boolean) : HermesEvent
    data class Poll(val messageJson: String) : HermesEvent
    data class Prediction(val messageJson: String) : HermesEvent
    data object Keepalive : HermesEvent
    data object Reconnect : HermesEvent
}

/**
 * Pure Hermes (PubSub-over-WebSocket) session routing + subscribe-message building.
 * Timers, transport and the minute-watched ticker stay platform-side; PubSub
 * payloads are passed through as raw JSON strings for the existing parsers.
 */
class HermesRouter {

    private val json = Json { ignoreUnknownKeys = true }
    private val handledMessageIds = mutableListOf<String>()
    private var topics = emptyMap<String, String>()

    class Subscriptions(
        val topics: Map<String, String>,
        val messages: List<String>,
    )

    fun buildAuthenticate(userId: String?, gqlToken: String?, collectPoints: Boolean): String? {
        if (userId.isNullOrBlank() || gqlToken.isNullOrBlank() || !collectPoints) return null
        return buildJsonObject {
            put("id", Uuid.random().toHexString().substring(0, 21))
            put("type", "authenticate")
            putJsonObject("authenticate") {
                put("token", gqlToken)
            }
            put("timestamp", Clock.System.now().toString())
        }.toString()
    }

    fun buildSubscriptions(
        channelId: String,
        userId: String?,
        gqlToken: String?,
        collectPoints: Boolean,
        showRaids: Boolean,
        showPolls: Boolean,
        showPredictions: Boolean,
    ): Subscriptions {
        val newTopics = buildMap {
            put(Uuid.random().toHexString().substring(0, 21), "video-playback-by-id.$channelId")
            put(Uuid.random().toHexString().substring(0, 21), "broadcast-settings-update.$channelId")
            put(Uuid.random().toHexString().substring(0, 21), "community-points-channel-v1.$channelId")
            if (showRaids) {
                put(Uuid.random().toHexString().substring(0, 21), "raid.$channelId")
            }
            if (showPolls) {
                put(Uuid.random().toHexString().substring(0, 21), "polls.$channelId")
            }
            if (showPredictions) {
                put(Uuid.random().toHexString().substring(0, 21), "predictions-channel-v1.$channelId")
            }
            if (!userId.isNullOrBlank() && !gqlToken.isNullOrBlank()) {
                if (collectPoints) {
                    put(Uuid.random().toHexString().substring(0, 21), "community-points-user-v1.$userId")
                }
            }
        }
        topics = newTopics
        val messages = newTopics.map { (key, topic) ->
            buildJsonObject {
                put("type", "subscribe")
                put("id", Uuid.random().toHexString().substring(0, 21))
                putJsonObject("subscribe") {
                    put("id", key)
                    put("type", "pubsub")
                    putJsonObject("pubsub") {
                        put("topic", topic)
                    }
                }
                put("timestamp", Clock.System.now().toString())
            }.toString()
        }
        return Subscriptions(newTopics, messages)
    }

    fun route(raw: String): HermesEvent {
        try {
            if (raw.isBlank()) return HermesEvent.Ignore
            val json = json.parseToJsonElement(raw).asObjectOrNullCompat() ?: return HermesEvent.Ignore
            val messageId = json.stringOrNullCompat("id")
            if (!messageId.isNullOrBlank()) {
                if (handledMessageIds.contains(messageId)) {
                    return HermesEvent.Ignore
                } else {
                    handledMessageIds.add(messageId)
                    if (handledMessageIds.size > 200) {
                        handledMessageIds.removeAt(0)
                    }
                }
            }
            return when (json.stringOrNullCompat("type")) {
                "notification" -> {
                    val notification = json["notification"]?.asObjectOrNullCompat()
                    val subscription = notification?.get("subscription")?.asObjectOrNullCompat()
                    val subscriptionId = subscription?.stringOrNullCompat("id")
                    val topic = topics[subscriptionId]
                    val message = notification?.stringOrNullCompat("pubsub")
                    val messageType = message?.let {
                        try {
                            this.json.parseToJsonElement(it).asObjectOrNullCompat()?.stringOrNullCompat("type")
                        } catch (e: Exception) {
                            null
                        }
                    }
                    if (topic != null && message != null && messageType != null) {
                        when {
                            topic.startsWith("video-playback-by-id") -> HermesEvent.Playback(message)
                            topic.startsWith("broadcast-settings-update") -> {
                                when {
                                    messageType.startsWith("broadcast_settings_update") -> HermesEvent.StreamInfo(message)
                                    else -> HermesEvent.Ignore
                                }
                            }
                            topic.startsWith("community-points-channel") -> {
                                when {
                                    messageType.startsWith("reward-redeemed") -> HermesEvent.Reward(message)
                                    else -> HermesEvent.Ignore
                                }
                            }
                            topic.startsWith("community-points-user") -> {
                                when {
                                    messageType.startsWith("points-earned") -> HermesEvent.PointsEarned(message)
                                    messageType.startsWith("claim-available") -> HermesEvent.ClaimAvailable
                                    else -> HermesEvent.Ignore
                                }
                            }
                            topic.startsWith("raid") -> {
                                when {
                                    messageType.startsWith("raid_update") -> HermesEvent.Raid(message, false)
                                    messageType.startsWith("raid_go") -> HermesEvent.Raid(message, true)
                                    else -> HermesEvent.Ignore
                                }
                            }
                            topic.startsWith("polls") -> HermesEvent.Poll(message)
                            topic.startsWith("predictions-channel") -> HermesEvent.Prediction(message)
                            else -> HermesEvent.Ignore
                        }
                    } else HermesEvent.Ignore
                }
                "keepalive" -> HermesEvent.Keepalive
                "reconnect" -> HermesEvent.Reconnect
                "welcome" -> {
                    val welcome = json["welcome"]?.asObjectOrNullCompat()
                    HermesEvent.Welcome(welcome?.intOrNullCompat("keepaliveSec"))
                }
                else -> HermesEvent.Ignore
            }
        } catch (e: Exception) {
            return HermesEvent.Ignore
        }
    }
}
