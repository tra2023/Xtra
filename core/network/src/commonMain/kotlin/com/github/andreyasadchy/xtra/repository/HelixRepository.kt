package com.github.andreyasadchy.xtra.repository

import com.github.andreyasadchy.xtra.model.helix.channel.ChannelSearchResponse
import com.github.andreyasadchy.xtra.model.helix.chat.BadgesResponse
import com.github.andreyasadchy.xtra.model.helix.chat.ChatUsersResponse
import com.github.andreyasadchy.xtra.model.helix.chat.CheerEmotesResponse
import com.github.andreyasadchy.xtra.model.helix.chat.EmoteSetsResponse
import com.github.andreyasadchy.xtra.model.helix.chat.UserEmotesResponse
import com.github.andreyasadchy.xtra.model.helix.clip.ClipsResponse
import com.github.andreyasadchy.xtra.model.helix.follows.FollowsResponse
import com.github.andreyasadchy.xtra.model.helix.game.GamesResponse
import com.github.andreyasadchy.xtra.model.helix.stream.StreamsResponse
import com.github.andreyasadchy.xtra.model.helix.user.UsersResponse
import com.github.andreyasadchy.xtra.model.helix.video.VideosResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class HelixRepository(
    private val client: XtraHttpClient,
    private val json: Json,
) {

    private suspend fun get(headers: Map<String, String>, url: String): XtraHttpResponse =
        client.execute(XtraHttpRequest(XtraHttpRequest.GET, url, headers,))

    private suspend fun postJson(headers: Map<String, String>, url: String, body: String): XtraHttpResponse =
        client.execute(XtraHttpRequest(XtraHttpRequest.POST, url, headers + ("Content-Type" to "application/json"), body.toByteArray(),))

    private suspend fun delete(headers: Map<String, String>, url: String): XtraHttpResponse =
        client.execute(XtraHttpRequest(XtraHttpRequest.DELETE, url, headers,))

    private suspend fun put(headers: Map<String, String>, url: String): XtraHttpResponse =
        client.execute(XtraHttpRequest(XtraHttpRequest.PUT, url, headers,))

    private suspend fun patchJson(headers: Map<String, String>, url: String, body: String): XtraHttpResponse =
        client.execute(XtraHttpRequest(XtraHttpRequest.PATCH, url, headers + ("Content-Type" to "application/json"), body.toByteArray(),))

    private fun XtraHttpResponse.nullOrError(): String? =
        if (code in 200..299) null else bodyAsString()

    suspend fun getGames(headers: Map<String, String>, ids: List<String>? = null, names: List<String>? = null): GamesResponse = withContext(Dispatchers.IO) {
        val url = HelixUrls.build("https://api.twitch.tv/helix/games") {
            params("id", ids)
            params("name", names)
        }
        json.decodeFromString<GamesResponse>(get(headers, url).bodyAsString())
    }

    suspend fun getTopGames(headers: Map<String, String>, limit: Int?, offset: String?): GamesResponse = withContext(Dispatchers.IO) {
        val url = HelixUrls.build("https://api.twitch.tv/helix/games/top") {
            param("first", limit)
            param("after", offset)
        }
        json.decodeFromString<GamesResponse>(get(headers, url).bodyAsString())
    }

    suspend fun getStreams(headers: Map<String, String>, ids: List<String>? = null, logins: List<String>? = null, gameId: String? = null, languages: List<String>? = null, limit: Int? = null, offset: String? = null): StreamsResponse = withContext(Dispatchers.IO) {
        val url = HelixUrls.build("https://api.twitch.tv/helix/streams") {
            params("user_id", ids)
            params("user_login", logins)
            param("game_id", gameId)
            params("language", languages)
            param("first", limit)
            param("after", offset)
        }
        json.decodeFromString<StreamsResponse>(get(headers, url).bodyAsString())
    }

    suspend fun getFollowedStreams(headers: Map<String, String>, userId: String?, limit: Int?, offset: String?): StreamsResponse = withContext(Dispatchers.IO) {
        val url = HelixUrls.build("https://api.twitch.tv/helix/streams/followed") {
            param("user_id", userId)
            param("first", limit)
            param("after", offset)
        }
        json.decodeFromString<StreamsResponse>(get(headers, url).bodyAsString())
    }

    suspend fun getClips(headers: Map<String, String>, ids: List<String>? = null, channelId: String? = null, gameId: String? = null, startedAt: String? = null, endedAt: String? = null, limit: Int? = null, offset: String? = null): ClipsResponse = withContext(Dispatchers.IO) {
        val url = HelixUrls.build("https://api.twitch.tv/helix/clips") {
            params("id", ids)
            param("broadcaster_id", channelId)
            param("game_id", gameId)
            param("started_at", startedAt)
            param("ended_at", endedAt)
            param("first", limit)
            param("after", offset)
        }
        json.decodeFromString<ClipsResponse>(get(headers, url).bodyAsString())
    }

    suspend fun getVideos(headers: Map<String, String>, ids: List<String>? = null, gameId: String? = null, channelId: String? = null, period: String? = null, broadcastType: String? = null, sort: String? = null, language: String? = null, limit: Int? = null, offset: String? = null): VideosResponse = withContext(Dispatchers.IO) {
        val url = HelixUrls.build("https://api.twitch.tv/helix/videos") {
            params("id", ids)
            param("game_id", gameId)
            param("user_id", channelId)
            param("period", period)
            param("type", broadcastType)
            param("sort", sort)
            param("language", language)
            param("first", limit)
            param("after", offset)
        }
        json.decodeFromString<VideosResponse>(get(headers, url).bodyAsString())
    }

    suspend fun getUsers(headers: Map<String, String>, ids: List<String>? = null, logins: List<String>? = null): UsersResponse = withContext(Dispatchers.IO) {
        val url = HelixUrls.build("https://api.twitch.tv/helix/users") {
            params("id", ids)
            params("login", logins)
        }
        json.decodeFromString<UsersResponse>(get(headers, url).bodyAsString())
    }

    suspend fun getSearchGames(headers: Map<String, String>, query: String?, limit: Int?, offset: String?): GamesResponse = withContext(Dispatchers.IO) {
        val url = HelixUrls.build("https://api.twitch.tv/helix/search/categories") {
            param("query", query)
            param("first", limit)
            param("after", offset)
        }
        json.decodeFromString<GamesResponse>(get(headers, url).bodyAsString())
    }

    suspend fun getSearchChannels(headers: Map<String, String>, query: String?, limit: Int?, offset: String?, live: Boolean? = null): ChannelSearchResponse = withContext(Dispatchers.IO) {
        val url = HelixUrls.build("https://api.twitch.tv/helix/search/channels") {
            param("query", query)
            param("first", limit)
            param("after", offset)
            param("live_only", live)
        }
        json.decodeFromString<ChannelSearchResponse>(get(headers, url).bodyAsString())
    }

    suspend fun getUserFollows(headers: Map<String, String>, userId: String?, targetId: String? = null, limit: Int? = null, offset: String? = null): FollowsResponse = withContext(Dispatchers.IO) {
        val url = HelixUrls.build("https://api.twitch.tv/helix/channels/followed") {
            param("user_id", userId)
            param("broadcaster_id", targetId)
            param("first", limit)
            param("after", offset)
        }
        json.decodeFromString<FollowsResponse>(get(headers, url).bodyAsString())
    }

    suspend fun getUserFollowers(headers: Map<String, String>, userId: String?, targetId: String? = null, limit: Int? = null, offset: String? = null): FollowsResponse = withContext(Dispatchers.IO) {
        val url = HelixUrls.build("https://api.twitch.tv/helix/channels/followers") {
            param("user_id", targetId)
            param("broadcaster_id", userId)
            param("first", limit)
            param("after", offset)
        }
        json.decodeFromString<FollowsResponse>(get(headers, url).bodyAsString())
    }

    suspend fun getUserEmotes(headers: Map<String, String>, userId: String?, channelId: String?, offset: String?): UserEmotesResponse = withContext(Dispatchers.IO) {
        val url = HelixUrls.build("https://api.twitch.tv/helix/chat/emotes/user") {
            param("user_id", userId)
            param("broadcaster_id", channelId)
            param("after", offset)
        }
        json.decodeFromString<UserEmotesResponse>(get(headers, url).bodyAsString())
    }

    suspend fun getEmotesFromSet(headers: Map<String, String>, setIds: List<String>): EmoteSetsResponse = withContext(Dispatchers.IO) {
        val url = HelixUrls.build("https://api.twitch.tv/helix/chat/emotes/set") {
            params("emote_set_id", setIds)
        }
        json.decodeFromString<EmoteSetsResponse>(get(headers, url).bodyAsString())
    }

    suspend fun getGlobalBadges(headers: Map<String, String>): BadgesResponse = withContext(Dispatchers.IO) {
        val url = "https://api.twitch.tv/helix/chat/badges/global"
        json.decodeFromString<BadgesResponse>(get(headers, url).bodyAsString())
    }

    suspend fun getChannelBadges(headers: Map<String, String>, userId: String?): BadgesResponse = withContext(Dispatchers.IO) {
        val url = HelixUrls.build("https://api.twitch.tv/helix/chat/badges") {
            param("broadcaster_id", userId)
        }
        json.decodeFromString<BadgesResponse>(get(headers, url).bodyAsString())
    }

    suspend fun getCheerEmotes(headers: Map<String, String>, userId: String?): CheerEmotesResponse = withContext(Dispatchers.IO) {
        val url = HelixUrls.build("https://api.twitch.tv/helix/bits/cheermotes") {
            param("broadcaster_id", userId)
        }
        json.decodeFromString<CheerEmotesResponse>(get(headers, url).bodyAsString())
    }

    suspend fun getChatters(headers: Map<String, String>, channelId: String?, userId: String?, limit: Int? = null, offset: String? = null): ChatUsersResponse = withContext(Dispatchers.IO) {
        val url = HelixUrls.build("https://api.twitch.tv/helix/chat/chatters") {
            param("broadcaster_id", channelId)
            param("moderator_id", userId)
            param("first", limit)
            param("after", offset)
        }
        json.decodeFromString<ChatUsersResponse>(get(headers, url).bodyAsString())
    }

    suspend fun createEventSubSubscription(headers: Map<String, String>, userId: String?, channelId: String?, type: String?, sessionId: String?): String? = withContext(Dispatchers.IO) {
        val url = "https://api.twitch.tv/helix/eventsub/subscriptions"
        val body = buildJsonObject {
            put("type", type)
            put("version", "1")
            putJsonObject("condition") {
                put("broadcaster_user_id", channelId)
                put("user_id", userId)
            }
            putJsonObject("transport") {
                put("method", "websocket")
                put("session_id", sessionId)
            }
        }.toString()
        postJson(headers, url, body).nullOrError()
    }

    suspend fun sendMessage(headers: Map<String, String>, userId: String?, channelId: String?, message: String?, replyId: String?): String? = withContext(Dispatchers.IO) {
        val url = "https://api.twitch.tv/helix/chat/messages"
        val body = buildJsonObject {
            put("broadcaster_id", channelId)
            put("sender_id", userId)
            put("message", message)
            replyId?.let { put("reply_parent_message_id", it) }
        }.toString()
        postJson(headers, url, body).nullOrError()
    }

    suspend fun sendAnnouncement(headers: Map<String, String>, channelId: String?, userId: String?, message: String?, color: String?): String? = withContext(Dispatchers.IO) {
        val url = HelixUrls.build("https://api.twitch.tv/helix/chat/announcements") {
            param("broadcaster_id", channelId)
            param("moderator_id", userId)
        }
        val body = buildJsonObject {
            put("message", message)
            color?.let { put("color", it) }
        }.toString()
        postJson(headers, url, body).nullOrError()
    }

    suspend fun banUser(headers: Map<String, String>, channelId: String?, userId: String?, targetId: String?, duration: String? = null, reason: String?): String? = withContext(Dispatchers.IO) {
        val url = HelixUrls.build("https://api.twitch.tv/helix/moderation/bans") {
            param("broadcaster_id", channelId)
            param("moderator_id", userId)
        }
        val body = buildJsonObject {
            putJsonObject("data") {
                duration?.toIntOrNull()?.let { put("duration", it) }
                put("reason", reason)
                put("user_id", targetId)
            }
        }.toString()
        postJson(headers, url, body).nullOrError()
    }

    suspend fun unbanUser(headers: Map<String, String>, channelId: String?, userId: String?, targetId: String?): String? = withContext(Dispatchers.IO) {
        val url = HelixUrls.build("https://api.twitch.tv/helix/moderation/bans") {
            param("broadcaster_id", channelId)
            param("moderator_id", userId)
            param("user_id", targetId)
        }
        delete(headers, url).nullOrError()
    }

    suspend fun deleteMessages(headers: Map<String, String>, channelId: String?, userId: String?, messageId: String? = null): String? = withContext(Dispatchers.IO) {
        val url = HelixUrls.build("https://api.twitch.tv/helix/moderation/chat") {
            param("broadcaster_id", channelId)
            param("moderator_id", userId)
            param("message_id", messageId)
        }
        delete(headers, url).nullOrError()
    }

    suspend fun getChatColor(headers: Map<String, String>, userId: String?): String? = withContext(Dispatchers.IO) {
        val url = HelixUrls.build("https://api.twitch.tv/helix/chat/color") {
            param("user_id", userId)
        }
        val response = get(headers, url)
        if (response.code in 200..299) {
            json.decodeFromString<JsonElement>(response.bodyAsString()).jsonObject["data"]?.jsonArray?.firstOrNull()?.jsonObject?.get("color")?.jsonPrimitive?.contentOrNull
        } else {
            response.bodyAsString()
        }
    }

    suspend fun updateChatColor(headers: Map<String, String>, userId: String?, color: String?): String? = withContext(Dispatchers.IO) {
        val url = HelixUrls.build("https://api.twitch.tv/helix/chat/color") {
            param("user_id", userId)
            param("color", color)
        }
        put(headers, url).nullOrError()
    }

    suspend fun startCommercial(headers: Map<String, String>, channelId: String?, length: String?): String? = withContext(Dispatchers.IO) {
        val url = "https://api.twitch.tv/helix/channels/commercial"
        val body = buildJsonObject {
            put("broadcaster_id", channelId)
            put("length", length?.toIntOrNull())
        }.toString()
        val response = postJson(headers, url, body)
        if (response.code in 200..299) {
            json.decodeFromString<JsonElement>(response.bodyAsString()).jsonObject["data"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
        } else {
            response.bodyAsString()
        }
    }

    suspend fun updateChatSettings(headers: Map<String, String>, channelId: String?, userId: String?, emote: Boolean? = null, followers: Boolean? = null, followersDuration: Int? = null, slow: Boolean? = null, slowDuration: Int? = null, subs: Boolean? = null, unique: Boolean? = null): String? = withContext(Dispatchers.IO) {
        val url = HelixUrls.build("https://api.twitch.tv/helix/chat/settings") {
            param("broadcaster_id", channelId)
            param("moderator_id", userId)
        }
        val body = buildJsonObject {
            emote?.let { put("emote_mode", it) }
            followers?.let { put("follower_mode", it) }
            followersDuration?.let { put("follower_mode_duration", it) }
            slow?.let { put("slow_mode", it) }
            slowDuration?.let { put("slow_mode_wait_time", it) }
            subs?.let { put("subscriber_mode", it) }
            unique?.let { put("unique_chat_mode", it) }
        }.toString()
        patchJson(headers, url, body).nullOrError()
    }

    suspend fun createStreamMarker(headers: Map<String, String>, channelId: String?, description: String?): String? = withContext(Dispatchers.IO) {
        val url = "https://api.twitch.tv/helix/streams/markers"
        val body = buildJsonObject {
            put("user_id", channelId)
            description?.let { put("description", it) }
        }.toString()
        postJson(headers, url, body).nullOrError()
    }

    suspend fun addModerator(headers: Map<String, String>, channelId: String?, targetId: String?): String? = withContext(Dispatchers.IO) {
        val url = HelixUrls.build("https://api.twitch.tv/helix/moderation/moderators") {
            param("broadcaster_id", channelId)
            param("user_id", targetId)
        }
        get(headers, url).nullOrError()
    }

    suspend fun removeModerator(headers: Map<String, String>, channelId: String?, targetId: String?): String? = withContext(Dispatchers.IO) {
        val url = HelixUrls.build("https://api.twitch.tv/helix/moderation/moderators") {
            param("broadcaster_id", channelId)
            param("user_id", targetId)
        }
        delete(headers, url).nullOrError()
    }

    suspend fun startRaid(headers: Map<String, String>, channelId: String?, targetId: String?): String? = withContext(Dispatchers.IO) {
        val url = HelixUrls.build("https://api.twitch.tv/helix/raids") {
            param("from_broadcaster_id", channelId)
            param("to_broadcaster_id", targetId)
        }
        get(headers, url).nullOrError()
    }

    suspend fun cancelRaid(headers: Map<String, String>, channelId: String?): String? = withContext(Dispatchers.IO) {
        val url = HelixUrls.build("https://api.twitch.tv/helix/raids") {
            param("broadcaster_id", channelId)
        }
        delete(headers, url).nullOrError()
    }

    suspend fun addVip(headers: Map<String, String>, channelId: String?, targetId: String?): String? = withContext(Dispatchers.IO) {
        val url = HelixUrls.build("https://api.twitch.tv/helix/channels/vips") {
            param("broadcaster_id", channelId)
            param("user_id", targetId)
        }
        get(headers, url).nullOrError()
    }

    suspend fun removeVip(headers: Map<String, String>, channelId: String?, targetId: String?): String? = withContext(Dispatchers.IO) {
        val url = HelixUrls.build("https://api.twitch.tv/helix/channels/vips") {
            param("broadcaster_id", channelId)
            param("user_id", targetId)
        }
        delete(headers, url).nullOrError()
    }

    suspend fun sendWhisper(headers: Map<String, String>, userId: String?, targetId: String?, message: String?): String? = withContext(Dispatchers.IO) {
        val url = HelixUrls.build("https://api.twitch.tv/helix/whispers") {
            param("from_user_id", userId)
            param("to_user_id", targetId)
        }
        val body = buildJsonObject {
            put("message", message)
        }.toString()
        postJson(headers, url, body).nullOrError()
    }
}
