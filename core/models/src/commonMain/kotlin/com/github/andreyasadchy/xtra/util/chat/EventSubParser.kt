package com.github.andreyasadchy.xtra.util.chat

import com.github.andreyasadchy.xtra.model.chat.Badge
import com.github.andreyasadchy.xtra.model.chat.ChannelPointReward
import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.model.chat.RoomState
import com.github.andreyasadchy.xtra.model.chat.TwitchEmote
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlin.time.Instant

object EventSubParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parseChatMessage(jsonString: String, timestamp: String?): ChatMessage =
        parseChatMessage(json.parseToJsonElement(jsonString).asObjectOrNullCompat() ?: JsonObject(emptyMap()), timestamp)

    fun parseChatMessage(json: JsonObject, timestamp: String?): ChatMessage {
        val message = StringBuilder()
        val messageObj = json["message"]?.asObjectOrNullCompat()
        val messageText = messageObj?.stringOrNullCompat("text")
        val emotesList = mutableListOf<TwitchEmote>()
        val fragments = messageObj?.get("fragments")?.asArrayOrNullCompat()
        if (fragments != null) {
            for (i in 0 until fragments.size) {
                val fragment = fragments[i].asObjectOrNullCompat()
                val type = fragment?.stringOrNullCompat("type")
                val fragmentText = fragment?.stringOrNullCompat("text")
                if (!fragmentText.isNullOrBlank()) {
                    if (type.equals("emote", true)) {
                        val emote = fragment.get("emote")?.asObjectOrNullCompat()
                        val id = emote?.stringOrNullCompat("id")
                        if (!id.isNullOrBlank()) {
                            emotesList.add(
                                TwitchEmote(
                                    id = id,
                                    begin = message.codePointCountCompat(),
                                    end = message.codePointCountCompat() + fragmentText.lastIndex,
                                    setId = emote.stringOrNullCompat("emote_set_id"),
                                    ownerId = emote.stringOrNullCompat("owner_id")
                                )
                            )
                        }
                    }
                    message.append(fragmentText)
                }
            }
        }
        val badgesList = mutableListOf<Badge>()
        val badges = json["badges"]?.asArrayOrNullCompat()
        if (badges != null) {
            for (i in 0 until badges.size) {
                val badge = badges[i].asObjectOrNullCompat()
                val set = badge?.stringOrNullCompat("set_id")
                val id = badge?.stringOrNullCompat("id")
                if (!set.isNullOrBlank() && !id.isNullOrBlank()) {
                    badgesList.add(Badge(set, id))
                }
            }
        }
        return ChatMessage(
            type = ChatMessage.USER_MESSAGE,
            id = json.stringOrNullCompat("message_id"),
            userId = json.stringOrNullCompat("chatter_user_id"),
            userLogin = json.stringOrNullCompat("chatter_user_login"),
            userName = json.stringOrNullCompat("chatter_user_name"),
            message = message.toString(),
            color = json.stringOrNullCompat("color"),
            emotes = emotesList,
            badges = badgesList,
            isAction = messageText?.startsWith(ChatParser.ACTION) == true,
            bits = json["cheer"]?.asObjectOrNullCompat()?.intOrNullCompat("bits")?.takeIf { it > 0 },
            msgId = json.stringOrNullCompat("message_type")?.takeIf { !it.equals("text", true) },
            reward = json.stringOrNullCompat("channel_points_custom_reward_id")?.let { ChannelPointReward(id = it) },
            timestamp = timestamp?.let { parseTimestamp(it) },
            fullMsg = json.toString()
        )
    }

    fun parseUserNotice(jsonString: String, timestamp: String?): ChatMessage =
        parseUserNotice(json.parseToJsonElement(jsonString).asObjectOrNullCompat() ?: JsonObject(emptyMap()), timestamp)

    fun parseUserNotice(json: JsonObject, timestamp: String?): ChatMessage {
        val message = StringBuilder()
        val messageObj = json["message"]?.asObjectOrNullCompat()
        val messageText = messageObj?.stringOrNullCompat("text")
        val systemMsg = json.stringOrNullCompat("system_message")
        return if (messageText != null) {
            val emotesList = mutableListOf<TwitchEmote>()
            val fragments = messageObj.get("fragments")?.asArrayOrNullCompat()
            if (fragments != null) {
                for (i in 0 until fragments.size) {
                    val fragment = fragments[i].asObjectOrNullCompat()
                    val type = fragment?.stringOrNullCompat("type")
                    val fragmentText = fragment?.stringOrNullCompat("text")
                    if (!fragmentText.isNullOrBlank()) {
                        if (type.equals("emote", true)) {
                            val emote = fragment.get("emote")?.asObjectOrNullCompat()
                            val id = emote?.stringOrNullCompat("id")
                            if (!id.isNullOrBlank()) {
                                emotesList.add(
                                    TwitchEmote(
                                        id = id,
                                        begin = message.codePointCountCompat(),
                                        end = message.codePointCountCompat() + fragmentText.lastIndex,
                                        setId = emote.stringOrNullCompat("emote_set_id"),
                                        ownerId = emote.stringOrNullCompat("owner_id")
                                    )
                                )
                            }
                        }
                        message.append(fragmentText)
                    }
                }
            }
            val badgesList = mutableListOf<Badge>()
            val badges = json["badges"]?.asArrayOrNullCompat()
            if (badges != null) {
                for (i in 0 until badges.size) {
                    val badge = badges[i].asObjectOrNullCompat()
                    val set = badge?.stringOrNullCompat("set_id")
                    val id = badge?.stringOrNullCompat("id")
                    if (!set.isNullOrBlank() && !id.isNullOrBlank()) {
                        badgesList.add(Badge(set, id))
                    }
                }
            }
            ChatMessage(
                type = ChatMessage.USER_MESSAGE,
                id = json.stringOrNullCompat("message_id"),
                userId = json.stringOrNullCompat("chatter_user_id"),
                userLogin = json.stringOrNullCompat("chatter_user_login"),
                userName = json.stringOrNullCompat("chatter_user_name"),
                message = message.toString(),
                color = json.stringOrNullCompat("color"),
                emotes = emotesList,
                badges = badgesList,
                isAction = messageText.startsWith(ChatParser.ACTION),
                systemMsg = systemMsg,
                msgId = json.stringOrNullCompat("notice_type"),
                timestamp = timestamp?.let { parseTimestamp(it) },
                fullMsg = json.toString()
            )
        } else {
            ChatMessage(
                type = ChatMessage.USER_MESSAGE,
                userId = json.stringOrNullCompat("chatter_user_id"),
                userLogin = json.stringOrNullCompat("chatter_user_login"),
                userName = json.stringOrNullCompat("chatter_user_name"),
                systemMsg = systemMsg,
                timestamp = timestamp?.let { parseTimestamp(it) },
                fullMsg = json.toString()
            )
        }
    }

    fun parseRoomState(jsonString: String): RoomState =
        parseRoomState(json.parseToJsonElement(jsonString).asObjectOrNullCompat() ?: JsonObject(emptyMap()))

    fun parseRoomState(json: JsonObject): RoomState {
        val emote = json.booleanOrNullCompat("emote_mode")
        val followers = json.booleanOrNullCompat("follower_mode")
        val followersDuration = json.intOrNullCompat("follower_mode_duration_minutes")
        val slow = json.booleanOrNullCompat("slow_mode")
        val slowDuration = json.intOrNullCompat("slow_mode_wait_time_seconds")?.takeIf { it > 0 }
        val subs = json.booleanOrNullCompat("subscriber_mode")
        val unique = json.booleanOrNullCompat("unique_chat_mode")
        return RoomState(
            emote = if (emote == true) "1" else "0",
            followers = if (followers == true) (followersDuration ?: 0).toString() else "-1",
            unique = if (unique == true) "1" else "0",
            slow = if (slow == true && slowDuration != null) slowDuration.toString() else "0",
            subs = if (subs == true) "1" else "0"
        )
    }

    fun parseTimestamp(value: String): Long? = try {
        Instant.parse(value).toEpochMilliseconds().takeIf { it > 0 }
    } catch (e: Exception) {
        null
    }

    internal fun StringBuilder.codePointCountCompat(): Int {
        var count = 0
        var i = 0
        while (i < length) {
            val c = this[i]
            i += if (c.isHighSurrogate() && i + 1 < length && this[i + 1].isLowSurrogate()) 2 else 1
            count++
        }
        return count
    }
}
