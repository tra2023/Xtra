package com.github.andreyasadchy.xtra.repository

import com.github.andreyasadchy.xtra.db.PlaybackStatesDao
import com.github.andreyasadchy.xtra.db.RecentEmotesDao
import com.github.andreyasadchy.xtra.db.VideoPositionsDao
import com.github.andreyasadchy.xtra.db.VideoSwapDao
import com.github.andreyasadchy.xtra.graphql.type.BadgeImageSize
import com.github.andreyasadchy.xtra.graphql.type.EmoteType
import com.github.andreyasadchy.xtra.model.PlaybackState
import com.github.andreyasadchy.xtra.model.VideoPosition
import com.github.andreyasadchy.xtra.model.VideoQuality
import com.github.andreyasadchy.xtra.model.chat.CheerEmote
import com.github.andreyasadchy.xtra.model.chat.Emote
import com.github.andreyasadchy.xtra.model.chat.RecentEmote
import com.github.andreyasadchy.xtra.model.chat.TwitchBadge
import com.github.andreyasadchy.xtra.model.chat.TwitchEmote
import com.github.andreyasadchy.xtra.model.misc.BTTVResponse
import com.github.andreyasadchy.xtra.model.misc.FFZChannelResponse
import com.github.andreyasadchy.xtra.model.misc.FFZGlobalResponse
import com.github.andreyasadchy.xtra.model.misc.FFZResponse
import com.github.andreyasadchy.xtra.model.misc.RecentMessagesResponse
import com.github.andreyasadchy.xtra.model.misc.STVChannelResponse
import com.github.andreyasadchy.xtra.model.misc.STVEmoteSetResponse
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.model.ui.VideoSwap
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchImageUrls
import com.github.andreyasadchy.xtra.util.m3u8.AdDetector
import com.github.andreyasadchy.xtra.util.m3u8.PlaylistUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.Base64
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.time.Instant
import kotlin.uuid.Uuid

class PlayerRepository(
    private val httpClient: XtraHttpClient,
    private val json: Json,
    private val userAgent: String,
    private val recentEmotes: RecentEmotesDao,
    private val videoSwapDao: VideoSwapDao,
    private val videoPositions: VideoPositionsDao,
    private val playbackStatesDao: PlaybackStatesDao,
    private val graphQLRepository: GraphQLRepository,
    private val helixRepository: HelixRepository,
) {

    private suspend fun httpGet(url: String, headers: Map<String, String> = emptyMap()): String {
        return httpClient.execute(
            XtraHttpRequest(
                method = XtraHttpRequest.GET,
                url = url,
                headers = headers,
            )
        ).bodyAsString()
    }

    private fun userAgentHeaders(extra: Map<String, String> = emptyMap()): Map<String, String> {
        return extra + ("User-Agent" to userAgent)
    }

    private fun buildUrl(base: String, params: List<Pair<String, String?>>): String {
        val query = params
            .filter { !it.second.isNullOrBlank() }
            .joinToString("&") { (key, value) -> "${encodeQueryComponent(key)}=${encodeQueryComponent(value!!)}" }
        return if (query.isEmpty()) base else "$base?$query"
    }

    private fun encodeQueryComponent(value: String): String = buildString {
        for (char in value) {
            if (char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char == '-' || char == '_' || char == '.' || char == '~') {
                append(char)
            } else {
                for (byte in char.toString().toByteArray()) {
                    append('%')
                    append(HEX_DIGITS[(byte.toInt() shr 4) and 0xF])
                    append(HEX_DIGITS[byte.toInt() and 0xF])
                }
            }
        }
    }

    companion object {
        private const val HEX_DIGITS = "0123456789ABCDEF"
    }

    suspend fun loadStreamPlaylistUrl(gqlHeaders: Map<String, String>, channelLogin: String, platform: String?, playerType: String?, supportedCodecs: String?, enableIntegrity: Boolean): String = withContext(Dispatchers.IO) {
        val platform = platform?.takeIf { it.isNotBlank() } ?: "web"
        val playerType = playerType?.takeIf { it.isNotBlank() } ?: "site"
        val accessToken = loadStreamPlaybackAccessToken(gqlHeaders, channelLogin, platform, playerType, enableIntegrity).let { token ->
            if (token.second?.contains("\"forbidden\":true") == true && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                loadStreamPlaybackAccessToken(gqlHeaders.filterNot { it.key == C.HEADER_TOKEN }, channelLogin, platform, playerType, enableIntegrity)
            } else token
        }
        val signature = accessToken.first
        val token = accessToken.second
        buildUrl(
            base = "https://usher.ttvnw.net/api/v2/channel/hls/${channelLogin}.m3u8",
            params = listOf(
                "allow_source" to "true",
                "allow_audio_only" to "true",
                "fast_bread" to "true", // low latency
                "include_unavailable" to "true",
                "p" to Random.nextInt(9999999).toString(),
                "platform" to platform,
                "sig" to signature,
                "supported_codecs" to supportedCodecs?.takeIf { it.isNotBlank() },
                "token" to token,
            )
        )
    }

    private suspend fun loadStreamPlaybackAccessToken(gqlHeaders: Map<String, String>, channelLogin: String, platform: String, playerType: String, enableIntegrity: Boolean): Pair<String?, String?> = withContext(Dispatchers.IO) {
        val accessTokenHeaders = if (enableIntegrity) {
            gqlHeaders
        } else {
            gqlHeaders.toMutableMap().apply {
                put("X-Device-Id", Uuid.random().toHexString())
            }
        }
        try {
            val response = graphQLRepository.loadPlaybackAccessToken(
                headers = accessTokenHeaders,
                login = channelLogin,
                platform = platform,
                playerType = playerType,
            )
            if (enableIntegrity) {
                response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let { throw Exception(it.message) }
            }
            response.data!!.streamPlaybackAccessToken!!.let {
                it.signature to it.value
            }
        } catch (e: Exception) {
            if (e.message == C.FAILED_INTEGRITY_CHECK) throw e
            val response = graphQLRepository.loadQueryStreamPlaybackAccessToken(
                headers = accessTokenHeaders,
                login = channelLogin,
                platform = platform,
                playerType = playerType,
            )
            if (enableIntegrity) {
                response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let { throw Exception(it.message) }
            }
            response.data!!.streamPlaybackAccessToken!!.let {
                it.signature to it.value
            }
        }
    }

    suspend fun loadVideoPlaylistUrl(gqlHeaders: Map<String, String>, videoId: String?, supportedCodecs: String?, enableIntegrity: Boolean): Pair<String, List<String>> = withContext(Dispatchers.IO) {
        val accessTokenHeaders = if (enableIntegrity) {
            gqlHeaders
        } else {
            gqlHeaders.toMutableMap().apply {
                put("X-Device-Id", Uuid.random().toHexString())
            }
        }
        val accessToken = try {
            val response = graphQLRepository.loadPlaybackAccessToken(
                headers = accessTokenHeaders,
                vodId = videoId,
                platform = "web",
                playerType = "site",
            )
            if (enableIntegrity) {
                response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let { throw Exception(it.message) }
            }
            response.data!!.videoPlaybackAccessToken!!.let {
                it.signature to it.value
            }
        } catch (e: Exception) {
            if (e.message == C.FAILED_INTEGRITY_CHECK) throw e
            val response = graphQLRepository.loadQueryVideoPlaybackAccessToken(
                headers = accessTokenHeaders,
                videoId = videoId!!,
                platform = "web",
                playerType = "site",
            )
            if (enableIntegrity) {
                response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let { throw Exception(it.message) }
            }
            response.data!!.videoPlaybackAccessToken!!.let {
                it.signature to it.value
            }
        }
        val signature = accessToken.first
        val token = accessToken.second
        val backupQualities = mutableListOf<String>()
        token?.let { value ->
            val array = try {
                json.parseToJsonElement(value).jsonObject["chansub"]?.jsonObject?.get("restricted_bitrates")?.jsonArray
            } catch (_: Exception) {
                null
            }
            if (array != null) {
                for (element in array) {
                    val quality = if (element is JsonPrimitive && element !is JsonNull) element.content else null
                    if (!quality.isNullOrBlank()) {
                        backupQualities.add(quality)
                    }
                }
            }
        }
        val url = buildUrl(
            base = "https://usher.ttvnw.net/vod/v2/${videoId}.m3u8",
            params = listOf(
                "allow_source" to "true",
                "allow_audio_only" to "true",
                "include_unavailable" to "true",
                "p" to Random.nextInt(9999999).toString(),
                "platform" to "web",
                "sig" to signature,
                "supported_codecs" to supportedCodecs?.takeIf { it.isNotBlank() },
                "token" to token,
            )
        )
        url to backupQualities
    }

    suspend fun loadClipQualities(gqlHeaders: Map<String, String>, clipId: String?, enableIntegrity: Boolean): List<VideoQuality>? = withContext(Dispatchers.IO) {
        try {
            val response = graphQLRepository.loadClipUrls(gqlHeaders, clipId)
            if (enableIntegrity) {
                response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let { throw Exception(it.message) }
            }
            val accessToken = response.data?.clip?.playbackAccessToken
            response.data!!.clip.assets.let { assets ->
                (assets.find { it.portraitMetadata?.portraitClipLayout.isNullOrBlank() } ?: assets.firstOrNull())?.videoQualities?.mapIndexedNotNull { index, quality ->
                    if (quality.sourceURL.isNotBlank()) {
                        val name = if (!quality.quality.isNullOrBlank()) {
                            val frameRate = quality.frameRate?.roundToInt() ?: ""
                            "${quality.quality}p${frameRate}"
                        } else {
                            index.toString()
                        }
                        val url = buildUrl(
                            base = quality.sourceURL,
                            params = listOf(
                                "sig" to accessToken?.signature,
                                "token" to accessToken?.value,
                            )
                        )
                        VideoQuality(name, quality.quality?.toIntOrNull(), quality.frameRate, quality.bitrate, quality.codecs, url)
                    } else null
                }
            }
        } catch (e: Exception) {
            if (e.message == C.FAILED_INTEGRITY_CHECK) throw e
            val response = graphQLRepository.loadQueryClipUrls(gqlHeaders, clipId!!)
            if (enableIntegrity) {
                response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let { throw Exception(it.message) }
            }
            val accessToken = response.data?.clip?.playbackAccessToken
            response.data?.clip?.assets?.let { assets ->
                (assets.find { it?.portraitMetadata?.portraitClipLayout.isNullOrBlank() } ?: assets.firstOrNull())?.videoQualities?.mapIndexedNotNull { index, quality ->
                    val sourceURL = quality?.sourceURL
                    if (!sourceURL.isNullOrBlank()) {
                        val qualityValue = quality.quality
                        val name = if (!qualityValue.isNullOrBlank()) {
                            val frameRate = quality.frameRate?.roundToInt() ?: ""
                            "${qualityValue}p${frameRate}"
                        } else {
                            index.toString()
                        }
                        val url = buildUrl(
                            base = sourceURL,
                            params = listOf(
                                "sig" to accessToken?.signature,
                                "token" to accessToken?.value,
                            )
                        )
                        VideoQuality(
                            name, qualityValue?.toIntOrNull(), quality.frameRate?.toFloat(), quality.bitrate, quality.codecs, url
                        )
                    } else null
                }
            }
        }
    }

    suspend fun loadVideoInfo(gqlHeaders: Map<String, String>, helixHeaders: Map<String, String>, videoId: String?, enableIntegrity: Boolean): Video? = withContext(Dispatchers.IO) {
        val video = try {
            val response = graphQLRepository.loadQueryVideo(
                headers = gqlHeaders,
                id = videoId
            )
            if (enableIntegrity) {
                response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let {
                    throw Exception(it.message)
                }
            }
            response.data!!.let { item ->
                item.video?.let {
                    Video(
                        id = videoId,
                        channelId = it.owner?.id,
                        channelLogin = it.owner?.login,
                        channelName = it.owner?.displayName,
                        channelImageURL = it.owner?.profileImageURL,
                        gameId = it.game?.id,
                        gameSlug = it.game?.slug,
                        gameName = it.game?.displayName,
                        title = it.title,
                        thumbnailURL = it.previewThumbnailURL,
                        createdAt = it.createdAt?.toString(),
                        durationSeconds = it.lengthSeconds,
                        type = it.broadcastType?.toString(),
                        animatedPreviewURL = it.animatedPreviewURL,
                    )
                }
            }
        } catch (e: Exception) {
            if (e.message == C.FAILED_INTEGRITY_CHECK) {
                throw e
            }
            if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                try {
                    helixRepository.getVideos(
                        headers = helixHeaders,
                        ids = videoId?.let { listOf(it) }
                    ).data.firstOrNull()?.let {
                        Video(
                            id = it.id,
                            channelId = it.channelId,
                            channelLogin = it.channelLogin,
                            channelName = it.channelName,
                            title = it.title,
                            thumbnailURL = it.thumbnailURL,
                            createdAt = it.createdAt,
                            viewCount = it.viewCount,
                            durationSeconds = it.duration?.let { duration -> TwitchImageUrls.getDuration(duration) },
                        )
                    }
                } catch (e: Exception) {
                    null
                }
            } else null
        }
        video
    }

    suspend fun checkForAds(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val playlist = PlaylistUtils.parseMediaPlaylist(httpGet(url))
            val segment = playlist.segments.lastOrNull() ?: return@withContext false
            AdDetector.isAd(
                segment = AdDetector.AdSegment(
                    title = segment.title,
                    startTimeMs = segment.programDateTime?.let { Instant.parseOrNull(it)?.toEpochMilliseconds()?.takeIf { ms -> ms > 0 } },
                ),
                ranges = playlist.dateRanges.map { dateRange ->
                    val startTime = Instant.parseOrNull(dateRange.startDate)?.toEpochMilliseconds()?.takeIf { ms -> ms > 0 }
                    AdDetector.AdRange(
                        id = dateRange.id,
                        rangeClass = dateRange.rangeClass,
                        ad = dateRange.ad,
                        startTimeMs = startTime,
                        endTimeMs = startTime?.let { start ->
                            dateRange.endDate?.let { Instant.parseOrNull(it)?.toEpochMilliseconds()?.takeIf { ms -> ms > 0 } }
                                ?: dateRange.duration?.let { start + (it * 1000f).toLong() }
                                ?: dateRange.plannedDuration?.let { start + (it * 1000f).toLong() }
                        },
                    )
                },
            )
        } catch (e: Exception) {
            false
        }
    }

    suspend fun sendMinuteWatched(userId: String?, streamId: String?, channelId: String?, channelLogin: String?) = withContext(Dispatchers.IO) {
        val pageResponse = channelLogin?.let {
            val pageUrl = "https://www.twitch.tv/${channelLogin}"
            httpGet(pageUrl)
        }
        if (!pageResponse.isNullOrBlank()) {
            val settingsRegex = Regex("https://[\\w.]+/config/settings\\.\\w+?\\.js")
            val settingsUrl = settingsRegex.find(pageResponse)?.value
            val settingsResponse = settingsUrl?.let {
                httpGet(it)
            }
            if (!settingsResponse.isNullOrBlank()) {
                val spadeRegex = Regex("\"(?:beacon_url|spade_url)\":\"(.*?)\"")
                val spadeUrl = spadeRegex.find(settingsResponse)?.groups?.get(1)?.value
                if (!spadeUrl.isNullOrBlank()) {
                    val body = buildJsonObject {
                        put("event", "minute-watched")
                        putJsonObject("properties") {
                            put("channel_id", channelId)
                            put("broadcast_id", streamId)
                            put("player", "site")
                            put("user_id", userId?.toLong())
                        }
                    }.toString()
                    val spadeRequest = "data=" + Base64.Default.encode(body.toByteArray())
                    httpClient.execute(
                        XtraHttpRequest(
                            method = XtraHttpRequest.POST,
                            url = spadeUrl,
                            headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
                            body = spadeRequest.toByteArray(),
                        )
                    )
                }
            }
        }
    }

    suspend fun loadRecentMessages(recentMessagesUrl: String, channelLogin: String, limit: String): RecentMessagesResponse = withContext(Dispatchers.IO) {
        val url = recentMessagesUrl.replace("\$channel", channelLogin) + "?limit=${limit}"
        json.decodeFromString<RecentMessagesResponse>(httpGet(url, userAgentHeaders()))
    }

    suspend fun loadGlobalSTVEmoteSetResponse(): String = withContext(Dispatchers.IO) {
        val url = "https://7tv.io/v3/emote-sets/global"
        httpGet(url, userAgentHeaders())
    }

    suspend fun loadSTVEmoteSetResponse(setId: String): String = withContext(Dispatchers.IO) {
        val url = "https://7tv.io/v3/emote-sets/${setId}"
        httpGet(url, userAgentHeaders())
    }

    suspend fun loadSTVEmoteSet(response: String, useWebp: Boolean, global: Boolean): Pair<String?, List<Emote>> = withContext(Dispatchers.IO) {
        val response = json.decodeFromString<STVEmoteSetResponse>(response)
        Pair(response.id, parseSTVEmotes(response.emotes, useWebp, if (global) Emote.GLOBAL_STV else Emote.CHANNEL_STV))
    }

    suspend fun loadSTVUserResponse(channelId: String): String = withContext(Dispatchers.IO) {
        val url = "https://7tv.io/v3/users/twitch/${channelId}"
        httpGet(url, userAgentHeaders())
    }

    suspend fun loadSTVUser(response: String, useWebp: Boolean): Pair<String?, List<Emote>?> = withContext(Dispatchers.IO) {
        val response = json.decodeFromString<STVChannelResponse>(response)
        Pair(response.emoteSetId, response.emoteSet?.emotes?.let { parseSTVEmotes(it, useWebp, Emote.CHANNEL_STV) })
    }

    private fun parseSTVEmotes(response: List<STVEmoteSetResponse.Emote>, useWebp: Boolean, source: Int): List<Emote> {
        return response.mapNotNull { emote ->
            emote.name?.takeIf { it.isNotBlank() }?.let { name ->
                emote.data?.let { data ->
                    data.host?.let { host ->
                        host.url?.takeIf { it.isNotBlank() }?.let { template ->
                            val urls = host.files?.mapNotNull { file ->
                                file.name?.takeIf { it.isNotBlank() &&
                                        if (useWebp) {
                                            file.format == "WEBP"
                                        } else {
                                            file.format == "GIF" || file.format == "PNG"
                                        }
                                }?.let { name ->
                                    "https:${template}/${name}"
                                }
                            }
                            Emote(
                                name = name,
                                url1x = urls?.getOrNull(0) ?: "https:${template}/1x.webp",
                                url2x = urls?.getOrNull(1) ?: if (urls.isNullOrEmpty()) "https:${template}/2x.webp" else null,
                                url3x = urls?.getOrNull(2) ?: if (urls.isNullOrEmpty()) "https:${template}/3x.webp" else null,
                                url4x = urls?.getOrNull(3) ?: if (urls.isNullOrEmpty()) "https:${template}/4x.webp" else null,
                                format = urls?.getOrNull(0)?.substringAfterLast(".") ?: "webp",
                                isAnimated = data.animated != false,
                                isOverlayEmote = emote.flags == 1,
                                source = source,
                            )
                        }
                    }
                }
            }
        }
    }

    suspend fun getSTVUser(userId: String): String? = withContext(Dispatchers.IO) {
        val url = "https://7tv.io/v3/users/twitch/${userId}"
        val response = httpGet(url, userAgentHeaders())
        try {
            json.parseToJsonElement(response).jsonObject["user"]?.jsonObject?.let { user ->
                val id = user["id"]
                if (id is JsonPrimitive && id !is JsonNull) id.content else ""
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun sendSTVPresence(stvUserId: String, channelId: String, sessionId: String?, self: Boolean) = withContext(Dispatchers.IO) {
        val url = "https://7tv.io/v3/users/${stvUserId}/presences"
        val body = buildJsonObject {
            put("kind", 1)
            put("passive", self)
            put("session_id", if (self) sessionId else "undefined")
            putJsonObject("data") {
                put("platform", "TWITCH")
                put("id", channelId)
            }
        }.toString()
        httpClient.execute(
            XtraHttpRequest(
                method = XtraHttpRequest.POST,
                url = url,
                headers = mapOf(
                    "Content-Type" to "application/json",
                    "User-Agent" to userAgent,
                ),
                body = body.toByteArray(),
            )
        )
    }

    suspend fun loadGlobalBTTVEmotesResponse(): String = withContext(Dispatchers.IO) {
        val url = "https://api.betterttv.net/3/cached/emotes/global"
        httpGet(url, userAgentHeaders())
    }

    suspend fun loadGlobalBTTVEmotes(response: String, useWebp: Boolean): List<Emote> = withContext(Dispatchers.IO) {
        val response = json.decodeFromString<List<BTTVResponse>>(response)
        parseBTTVEmotes(response, useWebp, Emote.GLOBAL_BTTV)
    }

    suspend fun loadBTTVEmotesResponse(channelId: String): String = withContext(Dispatchers.IO) {
        val url = "https://api.betterttv.net/3/cached/users/twitch/${channelId}"
        httpGet(url, userAgentHeaders())
    }

    suspend fun loadBTTVEmotes(response: String, useWebp: Boolean): List<Emote> = withContext(Dispatchers.IO) {
        val response = json.decodeFromString<Map<String, JsonElement>>(response)
        parseBTTVEmotes(
            response.entries.filter { it.key != "bots" && it.value is JsonArray }.map { entry ->
                (entry.value as JsonArray).map { json.decodeFromJsonElement<BTTVResponse>(it) }
            }.flatten(),
            useWebp,
            Emote.CHANNEL_BTTV
        )
    }

    private fun parseBTTVEmotes(response: List<BTTVResponse>, useWebp: Boolean, source: Int): List<Emote> {
        val list = listOf("IceCold", "SoSnowy", "SantaHat", "TopHat", "CandyCane", "ReinDeer", "cvHazmat", "cvMask")
        return response.mapNotNull { emote ->
            emote.code?.takeIf { it.isNotBlank() }?.let { name ->
                emote.id?.takeIf { it.isNotBlank() }?.let { id ->
                    Emote(
                        name = name,
                        url1x = if (useWebp) "https://cdn.betterttv.net/emote/$id/1x.webp" else "https://cdn.betterttv.net/emote/$id/1x",
                        url2x = if (useWebp) "https://cdn.betterttv.net/emote/$id/2x.webp" else "https://cdn.betterttv.net/emote/$id/2x",
                        url3x = if (useWebp) "https://cdn.betterttv.net/emote/$id/2x.webp" else "https://cdn.betterttv.net/emote/$id/2x",
                        url4x = if (useWebp) "https://cdn.betterttv.net/emote/$id/3x.webp" else "https://cdn.betterttv.net/emote/$id/3x",
                        format = if (useWebp) "webp" else null,
                        isAnimated = emote.animated != false,
                        isOverlayEmote = list.contains(name),
                        source = source,
                    )
                }
            }
        }
    }

    suspend fun loadGlobalFFZEmotesResponse(): String = withContext(Dispatchers.IO) {
        val url = "https://api.frankerfacez.com/v1/set/global"
        httpGet(url, userAgentHeaders())
    }

    suspend fun loadGlobalFFZEmotes(response: String, useWebp: Boolean): List<Emote> = withContext(Dispatchers.IO) {
        val response = json.decodeFromString<FFZGlobalResponse>(response)
        response.sets.entries.filter { it.key.toIntOrNull()?.let { set -> response.globalSets.contains(set) } == true }.flatMap {
            it.value.emoticons?.let { emotes -> parseFFZEmotes(emotes, useWebp, Emote.GLOBAL_FFZ) } ?: emptyList()
        }
    }

    suspend fun loadFFZEmotesResponse(channelId: String): String = withContext(Dispatchers.IO) {
        val url = "https://api.frankerfacez.com/v1/room/id/${channelId}"
        httpGet(url, userAgentHeaders())
    }

    suspend fun loadFFZEmotes(response: String, useWebp: Boolean): List<Emote> = withContext(Dispatchers.IO) {
        val response = json.decodeFromString<FFZChannelResponse>(response)
        response.sets.entries.flatMap {
            it.value.emoticons?.let { emotes -> parseFFZEmotes(emotes, useWebp, Emote.CHANNEL_FFZ) } ?: emptyList()
        }
    }

    private fun parseFFZEmotes(response: List<FFZResponse.Emote>, useWebp: Boolean, source: Int): List<Emote> {
        return response.mapNotNull { emote ->
            emote.name?.takeIf { it.isNotBlank() }?.let { name ->
                val animated = emote.animated
                val isAnimated = animated != null
                if (animated != null) {
                    if (useWebp) {
                        animated
                    } else {
                        FFZResponse.Urls(
                            url1x = animated.url1x + ".gif",
                            url2x = animated.url2x + ".gif",
                            url4x = animated.url4x + ".gif",
                        )
                    }
                } else {
                    emote.urls
                }?.let { urls ->
                    Emote(
                        name = name,
                        url1x = urls.url1x,
                        url2x = urls.url2x,
                        url3x = urls.url2x,
                        url4x = urls.url4x,
                        format = if (isAnimated && useWebp) "webp" else null,
                        isAnimated = isAnimated,
                        source = source,
                    )
                }
            }
        }
    }

    suspend fun loadGlobalBadges(helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, emoteQuality: String, enableIntegrity: Boolean): List<TwitchBadge> = withContext(Dispatchers.IO) {
        try {
            val response = graphQLRepository.loadQueryBadges(gqlHeaders,
                when (emoteQuality) {
                    "4" -> BadgeImageSize.QUADRUPLE
                    "3" -> BadgeImageSize.QUADRUPLE
                    "2" -> BadgeImageSize.DOUBLE
                    else -> BadgeImageSize.NORMAL
                }
            )
            if (enableIntegrity) {
                response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let { throw Exception(it.message) }
            }
            response.data!!.badges?.mapNotNull {
                it?.setID?.let { setId ->
                    it.version?.let { version ->
                        it.imageURL?.let { url ->
                            TwitchBadge(
                                setId = setId,
                                version = version,
                                url1x = url,
                                url2x = url,
                                url3x = url,
                                url4x = url,
                                title = it.title
                            )
                        }
                    }
                }
            } ?: emptyList()
        } catch (e: Exception) {
            if (e.message == C.FAILED_INTEGRITY_CHECK) throw e
            try {
                val response = graphQLRepository.loadChatBadges(gqlHeaders, "")
                if (enableIntegrity) {
                    response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let { throw Exception(it.message) }
                }
                response.data!!.badges?.mapNotNull {
                    it.setID?.let { setId ->
                        it.version?.let { version ->
                            TwitchBadge(
                                setId = setId,
                                version = version,
                                url1x = it.image1x,
                                url2x = it.image2x,
                                url3x = it.image4x,
                                url4x = it.image4x,
                                title = it.title,
                            )
                        }
                    }
                } ?: emptyList()
            } catch (e: Exception) {
                if (e.message == C.FAILED_INTEGRITY_CHECK) throw e
                if (helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) throw Exception()
                helixRepository.getGlobalBadges(helixHeaders).data.mapNotNull { set ->
                    set.setId?.let { setId ->
                        set.versions?.mapNotNull {
                            it.id?.let { version ->
                                TwitchBadge(
                                    setId = setId,
                                    version = version,
                                    url1x = it.url1x,
                                    url2x = it.url2x,
                                    url3x = it.url4x,
                                    url4x = it.url4x
                                )
                            }
                        }
                    }
                }.flatten()
            }
        }
    }

    suspend fun loadChannelBadges(helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, channelId: String?, channelLogin: String?, emoteQuality: String, enableIntegrity: Boolean): List<TwitchBadge> = withContext(Dispatchers.IO) {
        try {
            val response = graphQLRepository.loadQueryUserBadges(gqlHeaders, channelId, channelLogin.takeIf { channelId.isNullOrBlank() },
                when (emoteQuality) {
                    "4" -> BadgeImageSize.QUADRUPLE
                    "3" -> BadgeImageSize.QUADRUPLE
                    "2" -> BadgeImageSize.DOUBLE
                    else -> BadgeImageSize.NORMAL
                }
            )
            if (enableIntegrity) {
                response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let { throw Exception(it.message) }
            }
            response.data!!.user?.broadcastBadges?.mapNotNull {
                it?.setID?.let { setId ->
                    it.version?.let { version ->
                        it.imageURL?.let { url ->
                            TwitchBadge(
                                setId = setId,
                                version = version,
                                url1x = url,
                                url2x = url,
                                url3x = url,
                                url4x = url,
                                title = it.title
                            )
                        }
                    }
                }
            } ?: emptyList()
        } catch (e: Exception) {
            if (e.message == C.FAILED_INTEGRITY_CHECK) throw e
            try {
                val response = graphQLRepository.loadChatBadges(gqlHeaders, channelLogin)
                if (enableIntegrity) {
                    response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let { throw Exception(it.message) }
                }
                response.data!!.badges?.mapNotNull {
                    it.setID?.let { setId ->
                        it.version?.let { version ->
                            TwitchBadge(
                                setId = setId,
                                version = version,
                                url1x = it.image1x,
                                url2x = it.image2x,
                                url3x = it.image4x,
                                url4x = it.image4x,
                                title = it.title,
                            )
                        }
                    }
                } ?: emptyList()
            } catch (e: Exception) {
                if (e.message == C.FAILED_INTEGRITY_CHECK) throw e
                if (helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) throw Exception()
                helixRepository.getChannelBadges(helixHeaders, channelId).data.mapNotNull { set ->
                    set.setId?.let { setId ->
                        set.versions?.mapNotNull {
                            it.id?.let { version ->
                                TwitchBadge(
                                    setId = setId,
                                    version = version,
                                    url1x = it.url1x,
                                    url2x = it.url2x,
                                    url3x = it.url4x,
                                    url4x = it.url4x
                                )
                            }
                        }
                    }
                }.flatten()
            }
        }
    }

    suspend fun loadCheerEmotes(helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, channelId: String?, channelLogin: String?, animateGifs: Boolean, enableIntegrity: Boolean): List<CheerEmote> = withContext(Dispatchers.IO) {
        try {
            val emotes = mutableListOf<CheerEmote>()
            val response = graphQLRepository.loadQueryUserCheerEmotes(gqlHeaders, channelId, channelLogin.takeIf { channelId.isNullOrBlank() })
            if (enableIntegrity) {
                response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let { throw Exception(it.message) }
            }
            response.data!!.cheerConfig?.displayConfig?.let { config ->
                val background = config.backgrounds?.find { it == "dark" } ?: config.backgrounds?.lastOrNull() ?: ""
                val format = if (animateGifs) {
                    config.types?.find { it.animation == "animated" } ?: config.types?.find { it.animation == "static" }
                } else {
                    config.types?.find { it.animation == "static" }
                } ?: config.types?.lastOrNull()
                val scale1x = config.scales?.find { it.startsWith("1") } ?: config.scales?.lastOrNull() ?: ""
                val scale2x = config.scales?.find { it.startsWith("2") } ?: scale1x
                val scale3x = config.scales?.find { it.startsWith("3") } ?: scale2x
                val scale4x = config.scales?.find { it.startsWith("4") } ?: scale3x
                response.data!!.cheerConfig?.groups?.mapNotNull { group ->
                    group.nodes?.mapNotNull { emote ->
                        emote.tiers?.mapNotNull { tier ->
                            config.colors?.find { it.bits == tier?.bits }?.let { item ->
                                val prefix = emote.prefix!!
                                val bits = item.bits!!
                                val url = group.templateURL!!
                                    .replaceFirst("PREFIX", prefix.lowercase())
                                    .replaceFirst("TIER", bits.toString())
                                    .replaceFirst("BACKGROUND", background)
                                    .replaceFirst("ANIMATION", format?.animation ?: "")
                                    .replaceFirst("EXTENSION", format?.extension ?: "")
                                CheerEmote(
                                    name = prefix,
                                    url1x = url.replaceFirst("SCALE", scale1x),
                                    url2x = url.replaceFirst("SCALE", scale2x),
                                    url3x = url.replaceFirst("SCALE", scale3x),
                                    url4x = url.replaceFirst("SCALE", scale4x),
                                    format = if (format?.animation == "animated") "gif" else null,
                                    isAnimated = format?.animation == "animated",
                                    minBits = bits,
                                    color = item.color
                                )
                            }
                        }
                    }?.flatten()
                }?.flatten()?.let { emotes.addAll(it) }
                response.data!!.user?.cheer?.cheerGroups?.mapNotNull { group ->
                    group.nodes?.mapNotNull { emote ->
                        emote.tiers?.mapNotNull { tier ->
                            config.colors?.find { it.bits == tier?.bits }?.let { item ->
                                val prefix = emote.prefix!!
                                val bits = item.bits!!
                                val url = group.templateURL!!
                                    .replaceFirst("PREFIX", prefix.lowercase())
                                    .replaceFirst("TIER", bits.toString())
                                    .replaceFirst("BACKGROUND", background)
                                    .replaceFirst("ANIMATION", format?.animation ?: "")
                                    .replaceFirst("EXTENSION", format?.extension ?: "")
                                CheerEmote(
                                    name = prefix,
                                    url1x = url.replaceFirst("SCALE", scale1x),
                                    url2x = url.replaceFirst("SCALE", scale2x),
                                    url3x = url.replaceFirst("SCALE", scale3x),
                                    url4x = url.replaceFirst("SCALE", scale4x),
                                    format = if (format?.animation == "animated") "gif" else null,
                                    isAnimated = format?.animation == "animated",
                                    minBits = bits,
                                    color = item.color
                                )
                            }
                        }
                    }?.flatten()
                }?.flatten()?.let { emotes.addAll(it) }
            }
            emotes
        } catch (e: Exception) {
            if (e.message == C.FAILED_INTEGRITY_CHECK) throw e
            try {
                val emotes = mutableListOf<CheerEmote>()
                val response = graphQLRepository.loadGlobalCheerEmotes(gqlHeaders)
                if (enableIntegrity) {
                    response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let { throw Exception(it.message) }
                }
                response.data!!.cheerConfig.displayConfig.let { config ->
                    val background = config.backgrounds?.find { it == "dark" } ?: config.backgrounds?.lastOrNull() ?: ""
                    val format = if (animateGifs) {
                        config.types?.find { it.animation == "animated" } ?: config.types?.find { it.animation == "static" }
                    } else {
                        config.types?.find { it.animation == "static" }
                    } ?: config.types?.lastOrNull()
                    val scale1x = config.scales?.find { it.startsWith("1") } ?: config.scales?.lastOrNull() ?: ""
                    val scale2x = config.scales?.find { it.startsWith("2") } ?: scale1x
                    val scale3x = config.scales?.find { it.startsWith("3") } ?: scale2x
                    val scale4x = config.scales?.find { it.startsWith("4") } ?: scale3x
                    val cheerConfig = response.data!!.cheerConfig
                    cheerConfig.groups.map { group ->
                        group.nodes.map { emote ->
                            emote.tiers.mapNotNull { tier ->
                                config.colors.find { it.bits == tier.bits }?.let { item ->
                                    val url = group.templateURL
                                        .replaceFirst("PREFIX", emote.prefix.lowercase())
                                        .replaceFirst("TIER", item.bits.toString())
                                        .replaceFirst("BACKGROUND", background)
                                        .replaceFirst("ANIMATION", format?.animation ?: "")
                                        .replaceFirst("EXTENSION", format?.extension ?: "")
                                    CheerEmote(
                                        name = emote.prefix,
                                        url1x = url.replaceFirst("SCALE", scale1x),
                                        url2x = url.replaceFirst("SCALE", scale2x),
                                        url3x = url.replaceFirst("SCALE", scale3x),
                                        url4x = url.replaceFirst("SCALE", scale4x),
                                        format = if (format?.animation == "animated") "gif" else null,
                                        isAnimated = format?.animation == "animated",
                                        minBits = item.bits,
                                        color = item.color,
                                    )
                                }
                            }
                        }.flatten()
                    }.flatten().let { emotes.addAll(it) }
                    graphQLRepository.loadChannelCheerEmotes(gqlHeaders, channelLogin).data?.channel?.cheer?.cheerGroups?.map { group ->
                        group.nodes.map { emote ->
                            emote.tiers.mapNotNull { tier ->
                                config.colors.find { it.bits == tier.bits }?.let { item ->
                                    val url = group.templateURL
                                        .replaceFirst("PREFIX", emote.prefix.lowercase())
                                        .replaceFirst("TIER", item.bits.toString())
                                        .replaceFirst("BACKGROUND", background)
                                        .replaceFirst("ANIMATION", format?.animation ?: "")
                                        .replaceFirst("EXTENSION", format?.extension ?: "")
                                    CheerEmote(
                                        name = emote.prefix,
                                        url1x = url.replaceFirst("SCALE", scale1x),
                                        url2x = url.replaceFirst("SCALE", scale2x),
                                        url3x = url.replaceFirst("SCALE", scale3x),
                                        url4x = url.replaceFirst("SCALE", scale4x),
                                        format = if (format?.animation == "animated") "gif" else null,
                                        isAnimated = format?.animation == "animated",
                                        minBits = item.bits,
                                        color = item.color,
                                    )
                                }
                            }
                        }.flatten()
                    }?.flatten()?.let { emotes.addAll(it) }
                }
                emotes
            } catch (e: Exception) {
                if (e.message == C.FAILED_INTEGRITY_CHECK) throw e
                if (helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) throw Exception()
                helixRepository.getCheerEmotes(helixHeaders, channelId).data.map { set ->
                    set.tiers.mapNotNull { tier ->
                        tier.images.let { it.dark ?: it.light }?.let { formats ->
                            if (animateGifs) {
                                formats.animated ?: formats.static
                            } else {
                                formats.static
                            }?.let { urls ->
                                CheerEmote(
                                    name = set.prefix,
                                    url1x = urls.url1x,
                                    url2x = urls.url2x,
                                    url3x = urls.url3x,
                                    url4x = urls.url4x,
                                    format = if (urls == formats.animated) "gif" else null,
                                    isAnimated = urls == formats.animated,
                                    minBits = tier.minBits,
                                    color = tier.color
                                )
                            }
                        }
                    }
                }.flatten()
            }
        }
    }

    suspend fun loadUserEmotes(helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, channelId: String?, userId: String?, animateGifs: Boolean, enableIntegrity: Boolean): List<TwitchEmote> = withContext(Dispatchers.IO) {
        try {
            if (gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) throw Exception()
            val emotes = mutableListOf<TwitchEmote>()
            var offset: String? = null
            do {
                val response = graphQLRepository.loadUserEmotes(gqlHeaders, channelId, offset)
                if (enableIntegrity) {
                    response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let { throw Exception(it.message) }
                }
                val sets = response.data!!.channel.self.availableEmoteSetsPaginated
                val items = sets.edges
                items.map { item ->
                    item.node.let { set ->
                        set.emotes.mapNotNull { emote ->
                            emote.token?.let { token ->
                                TwitchEmote(
                                    id = emote.id,
                                    name = if (emote.type == "SMILIES") {
                                        token.replace("\\", "").replace("?", "")
                                            .replace("&lt;", "<").replace("&gt;", ">")
                                            .replace(Regex("\\((.)\\|.\\)")) { it.groups[1]?.value ?: "" }
                                            .replace(Regex("\\[(.).*?]")) { it.groups[1]?.value ?: "" }
                                    } else token,
                                    setId = emote.setID,
                                    ownerId = set.owner?.id
                                )
                            }
                        }
                    }
                }.flatten().let { emotes.addAll(it) }
                offset = items.lastOrNull()?.cursor
            } while (!items.lastOrNull()?.cursor.isNullOrBlank() && sets.pageInfo?.hasNextPage == true)
            emotes
        } catch (e: Exception) {
            if (e.message == C.FAILED_INTEGRITY_CHECK) throw e
            try {
                if (gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) throw Exception()
                val response = graphQLRepository.loadQueryUserEmotes(gqlHeaders)
                if (enableIntegrity) {
                    response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let { throw Exception(it.message) }
                }
                response.data!!.user?.emoteSets?.mapNotNull { set ->
                    set.emotes?.mapNotNull { emote ->
                        val token = emote?.token
                        val owner = emote?.owner
                        if (token != null && (!emote.type?.toString().equals("follower", true) || (owner?.id == null || owner.id == channelId))) {
                            TwitchEmote(
                                id = emote.id,
                                name = if (emote.type == EmoteType.SMILIES) {
                                    token.replace("\\", "").replace("?", "")
                                        .replace("&lt;", "<").replace("&gt;", ">")
                                        .replace(Regex("\\((.)\\|.\\)")) { it.groups[1]?.value ?: "" }
                                        .replace(Regex("\\[(.).*?]")) { it.groups[1]?.value ?: "" }
                                } else token,
                                setId = emote.setID,
                                ownerId = owner?.id
                            )
                        } else null
                    }
                }?.flatten() ?: emptyList()
            } catch (e: Exception) {
                if (e.message == C.FAILED_INTEGRITY_CHECK) throw e
                if (helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) throw Exception()
                val emotes = mutableListOf<TwitchEmote>()
                var offset: String? = null
                do {
                    val response = helixRepository.getUserEmotes(helixHeaders, userId, channelId, offset)
                    response.data.mapNotNull { emote ->
                        emote.name?.let { name ->
                            emote.id?.let { id ->
                                val format = if (animateGifs) {
                                    emote.format?.find { it == "animated" } ?: emote.format?.find { it == "static" }
                                } else {
                                    emote.format?.find { it == "static" }
                                } ?: emote.format?.firstOrNull() ?: ""
                                val theme = emote.theme?.find { it == "dark" } ?: emote.theme?.lastOrNull() ?: ""
                                val scale1x = emote.scale?.find { it.startsWith("1") } ?: emote.scale?.lastOrNull() ?: ""
                                val scale2x = emote.scale?.find { it.startsWith("2") } ?: scale1x
                                val scale3x = emote.scale?.find { it.startsWith("3") } ?: scale2x
                                val url = response.template
                                    .replaceFirst("{{id}}", id)
                                    .replaceFirst("{{format}}", format)
                                    .replaceFirst("{{theme_mode}}", theme)
                                TwitchEmote(
                                    name = if (emote.type == "smilies") {
                                        name.replace("\\", "").replace("?", "")
                                            .replace("&lt;", "<").replace("&gt;", ">")
                                            .replace(Regex("\\((.)\\|.\\)")) { it.groups[1]?.value ?: "" }
                                            .replace(Regex("\\[(.).*?]")) { it.groups[1]?.value ?: "" }
                                    } else name,
                                    url1x = url.replaceFirst("{{scale}}", scale1x),
                                    url2x = url.replaceFirst("{{scale}}", scale2x),
                                    url3x = url.replaceFirst("{{scale}}", scale3x),
                                    url4x = url.replaceFirst("{{scale}}", scale3x),
                                    format = if (format == "animated") "gif" else null,
                                    setId = emote.setId,
                                    ownerId = emote.ownerId
                                )
                            }
                        }
                    }.let { emotes.addAll(it) }
                    offset = response.pagination?.cursor
                } while (!response.pagination?.cursor.isNullOrBlank())
                emotes
            }
        }
    }

    suspend fun loadEmotesFromSet(helixHeaders: Map<String, String>, setIds: List<String>, animateGifs: Boolean): List<TwitchEmote> = withContext(Dispatchers.IO) {
        val response = helixRepository.getEmotesFromSet(helixHeaders, setIds)
        response.data.mapNotNull { emote ->
            emote.name?.let { name ->
                emote.id?.let { id ->
                    val format = if (animateGifs) {
                        emote.format?.find { it == "animated" } ?: emote.format?.find { it == "static" }
                    } else {
                        emote.format?.find { it == "static" }
                    } ?: emote.format?.firstOrNull() ?: ""
                    val theme = emote.theme?.find { it == "dark" } ?: emote.theme?.lastOrNull() ?: ""
                    val scale1x = emote.scale?.find { it.startsWith("1") } ?: emote.scale?.lastOrNull() ?: ""
                    val scale2x = emote.scale?.find { it.startsWith("2") } ?: scale1x
                    val scale3x = emote.scale?.find { it.startsWith("3") } ?: scale2x
                    val url = response.template
                        .replaceFirst("{{id}}", id)
                        .replaceFirst("{{format}}", format)
                        .replaceFirst("{{theme_mode}}", theme)
                    TwitchEmote(
                        name = if (emote.type == "smilies") {
                            name.replace("\\", "").replace("?", "")
                                .replace("&lt;", "<").replace("&gt;", ">")
                                .replace(Regex("\\((.)\\|.\\)")) { it.groups[1]?.value ?: "" }
                                .replace(Regex("\\[(.).*?]")) { it.groups[1]?.value ?: "" }
                        } else name,
                        url1x = url.replaceFirst("{{scale}}", scale1x),
                        url2x = url.replaceFirst("{{scale}}", scale2x),
                        url3x = url.replaceFirst("{{scale}}", scale3x),
                        url4x = url.replaceFirst("{{scale}}", scale3x),
                        format = if (format == "animated") "gif" else null,
                        setId = emote.setId,
                        ownerId = emote.ownerId
                    )
                }
            }
        }
    }

    fun loadRecentEmotesFlow() = recentEmotes.getAllFlow()

    suspend fun loadRecentEmotes(): List<RecentEmote> = withContext(Dispatchers.IO) {
        recentEmotes.getAll()
    }

    suspend fun insertRecentEmotes(emotes: Collection<RecentEmote>) = withContext(Dispatchers.IO) {
        val listSize = emotes.size
        val list = if (listSize <= RecentEmote.MAX_SIZE) {
            emotes
        } else {
            emotes.toList().subList(listSize - RecentEmote.MAX_SIZE, listSize)
        }
        recentEmotes.ensureMaxSizeAndInsert(list)
    }

    suspend fun getVideoSwapItems() = withContext(Dispatchers.IO) {
        videoSwapDao.getAll()
    }

    suspend fun saveVideoSwapItems(items: List<VideoSwap>) = withContext(Dispatchers.IO) {
        videoSwapDao.insertList(items)
    }

    suspend fun updateVideoSwapItems(items: List<VideoSwap>) = withContext(Dispatchers.IO) {
        videoSwapDao.updateList(items)
    }

    suspend fun saveVideoSwap(item: VideoSwap): Long = withContext(Dispatchers.IO) {
        videoSwapDao.insert(item)
    }

    suspend fun deleteVideoSwap(item: VideoSwap) = withContext(Dispatchers.IO) {
        videoSwapDao.delete(item)
    }

    suspend fun updateVideoSwap(item: VideoSwap) = withContext(Dispatchers.IO) {
        videoSwapDao.update(item)
    }

    fun loadVideoPositions() = videoPositions.getAll()

    suspend fun getVideoPosition(id: Long) = withContext(Dispatchers.IO) {
        videoPositions.getById(id)
    }

    suspend fun saveVideoPosition(position: VideoPosition) = withContext(Dispatchers.IO) {
        videoPositions.insert(position)
    }

    suspend fun deleteVideoPositions() = withContext(Dispatchers.IO) {
        videoPositions.deleteAll()
    }

    suspend fun getPlaybackStates() = withContext(Dispatchers.IO) {
        playbackStatesDao.getAll()
    }

    suspend fun savePlaybackStates(items: List<PlaybackState>) = withContext(Dispatchers.IO) {
        playbackStatesDao.replaceItems(items)
    }

    suspend fun deletePlaybackStates() = withContext(Dispatchers.IO) {
        playbackStatesDao.deleteAll()
    }
}