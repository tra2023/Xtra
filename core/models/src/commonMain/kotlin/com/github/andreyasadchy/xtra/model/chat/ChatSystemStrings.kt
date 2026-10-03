package com.github.andreyasadchy.xtra.model.chat

/**
 * Localized system notices the chat renderer injects into the message list (join/disconnect,
 * websocket debug lines, cached emote sets, cleared chat, stream live/offline, channel points).
 *
 * The renderer lives in `:core` and has no Context, so the Android host builds this from its
 * resources — same shape as [ChatMessageStrings].
 */
class ChatSystemStrings(
    val loadedCachedStvEmotes: String,
    val loadedCachedBttvEmotes: String,
    val loadedCachedFfzEmotes: String,
    val clearedMessage: (userName: String?, message: String?) -> String,
    val disconnected: String,
    val joinedChannel: (channelLogin: String?) -> String,
    val disconnectedFromChannel: (channelLogin: String?, message: String) -> String,
    val websocketConnected: (socketName: String) -> String,
    val websocketDisconnected: (socketName: String, message: String) -> String,
    val chatCleared: String,
    val streamLive: (channelLogin: String?) -> String,
    val streamOffline: (channelLogin: String?) -> String,
    val pointsEarned: (points: Int?) -> String,
    // Units used by the clear-chat timeout ("user was timed out for 10 minutes").
    val chatTimeout: String,
    val chatBan: String,
    val days: String,
    val hours: String,
    val minutes: String,
    val seconds: String,
)
