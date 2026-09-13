package com.github.andreyasadchy.xtra.util

import android.content.Context
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.repository.TwitchAuthHeaders
import com.github.andreyasadchy.xtra.repository.TwitchHeaders
import com.github.andreyasadchy.xtra.util.chat.ChatUtils

object TwitchApiHelper {

    var checkedValidation = false
    var checkedUpdates = false
    val defaultQualityList = listOf("chunked", "1080p60", "1080p30", "720p60", "720p30", "480p30", "360p30", "160p30", "144p30", "high", "medium", "low", "mobile", "audio_only")
    val vodDomains = listOf(
        "https://vod-secure.twitch.tv",
        "https://vod-metro.twitch.tv",
        "https://vod-pop-secure.twitch.tv",
        "https://d2e2de1etea730.cloudfront.net",
        "https://dqrpb9wgowsf5.cloudfront.net",
        "https://ds0h3roq6wcgc.cloudfront.net",
        "https://d2nvs31859zcd8.cloudfront.net",
        "https://d2aba1wr3818hz.cloudfront.net",
        "https://d3c27h4odz752x.cloudfront.net",
        "https://dgeft87wbj63p.cloudfront.net",
        "https://d1m7jfoe9zdc1j.cloudfront.net",
        "https://d3vd9lfkzbru3h.cloudfront.net",
        "https://d2vjef5jvl6bfs.cloudfront.net",
        "https://d1ymi26ma8va5x.cloudfront.net",
        "https://d1mhjrowxxagfy.cloudfront.net",
        "https://ddacn6pr5v0tl.cloudfront.net",
        "https://d3aqoihi2n8ty8.cloudfront.net",
        "https://d3fi1amfgojobc.cloudfront.net",
        "https://d3stzm2eumvgb4.cloudfront.net",
        "https://d2vi6trrdongqn.cloudfront.net",
        "https://d1ndex63qxojbr.cloudfront.net",
    )

    fun getGameBoxArt(url: String?): String? = TwitchImageUrls.getGameBoxArt(url)

    fun getType(context: Context, type: String?): String? {
        return when (type?.lowercase()) {
            "archive" -> context.getString(R.string.video_type_archive)
            "highlight" -> context.getString(R.string.video_type_highlight)
            "upload" -> context.getString(R.string.video_type_upload)
            else -> null
        }
    }

    fun getDuration(duration: String): Int = TwitchImageUrls.getDuration(duration)

    fun getDurationFromSeconds(context: Context, input: String?): String? {
        return input?.toIntOrNull()?.let { duration ->
            TwitchFormats.formatDurationFromSeconds(
                duration,
                context.getString(R.string.days),
                context.getString(R.string.hours),
                context.getString(R.string.minutes),
                context.getString(R.string.seconds),
            )
        }
    }

    fun getClearChatStrings(context: Context): ChatUtils.ClearChatStrings {
        return ChatUtils.ClearChatStrings(
            timeoutFormat = context.getString(R.string.chat_timeout),
            banFormat = context.getString(R.string.chat_ban),
            clearText = context.getString(R.string.chat_clear),
        )
    }

    fun getMinutesLeft(hour: Int, minute: Int): Int =
        TwitchFormats.minutesLeft(hour, minute)

    fun getTimestamp(input: Long, timestampFormat: String?): String? =
        TwitchFormats.formatTimestampMillis(input, timestampFormat)

    fun formatCount(count: Int, compact: Boolean): String =
        TwitchFormats.formatCount(count, compact)

    fun addTokenPrefixGQL(token: String) = TwitchImageUrls.addTokenPrefixGQL(token)
    fun addTokenPrefixHelix(token: String) = TwitchImageUrls.addTokenPrefixHelix(token)

    fun getGQLHeaders(context: Context, includeToken: Boolean = false): Map<String, String> {
        return TwitchAuthHeaders.getGqlHeaders(
            enableIntegrity = context.prefs().getBoolean(C.ENABLE_INTEGRITY, false),
            integrityHeadersJson = context.tokenPrefs().getString(C.GQL_HEADERS, null),
            gqlClientId = context.prefs().getString(C.GQL_CLIENT_ID2, TwitchHeaders.DEFAULT_GQL_CLIENT_ID),
            gqlToken = context.tokenPrefs().getString(C.GQL_TOKEN2, null),
            includeToken = includeToken,
        )
    }

    fun getHelixHeaders(context: Context): Map<String, String> {
        return TwitchAuthHeaders.getHelixHeaders(
            helixClientId = context.prefs().getString(C.HELIX_CLIENT_ID, TwitchHeaders.DEFAULT_HELIX_CLIENT_ID),
            token = context.tokenPrefs().getString(C.TOKEN, null),
        )
    }

    fun isIntegrityTokenExpired(context: Context): Boolean {
        return TwitchAuthHeaders.isIntegrityTokenExpired(
            System.currentTimeMillis(),
            context.tokenPrefs().getLong(C.INTEGRITY_EXPIRATION, 0),
        )
    }

    fun getMessageIdString(context: Context, msgId: String?): String? {
        return when (msgId) {
            "highlighted-message" -> context.getString(R.string.irc_msgid_highlighted_message)
            "announcement" -> context.getString(R.string.irc_msgid_announcement)
            else -> null
        }
    }
}
