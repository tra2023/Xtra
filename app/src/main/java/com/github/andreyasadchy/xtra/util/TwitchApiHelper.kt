package com.github.andreyasadchy.xtra.util

import android.content.Context
import android.icu.number.Notation
import android.icu.number.NumberFormatter
import android.icu.number.Precision
import android.text.format.DateUtils
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.repository.TwitchHeaders
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.Year
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

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

    fun getStreamThumbnail(url: String?): String? = TwitchImageUrls.getStreamThumbnail(url)

    fun getVideoThumbnail(url: String?): String? = TwitchImageUrls.getVideoThumbnail(url)

    fun getClipThumbnail(url: String?): String? = TwitchImageUrls.getClipThumbnail(url)

    fun getGameBoxArt(url: String?): String? = TwitchImageUrls.getGameBoxArt(url)

    fun getProfileImage(url: String?): String? = TwitchImageUrls.getProfileImage(url)

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
            val days = (duration / 86400)
            val hours = ((duration % 86400) / 3600)
            val minutes = (((duration % 86400) % 3600) / 60)
            val seconds = (duration % 60)
            buildString {
                if (days > 0) {
                    append("$days${context.getString(R.string.days)}")
                }
                if (hours > 0) {
                    if (isNotBlank()) {
                        append(" ")
                    }
                    append("$hours${context.getString(R.string.hours)}")
                }
                if (minutes > 0) {
                    if (isNotBlank()) {
                        append(" ")
                    }
                    append("$minutes${context.getString(R.string.minutes)}")
                }
                if (seconds > 0) {
                    if (isNotBlank()) {
                        append(" ")
                    }
                    append("$seconds${context.getString(R.string.seconds)}")
                }
            }
        }
    }

    fun getMinutesLeft(hour: Int, minute: Int): Int {
        val currentDate = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.systemDefault())
        val date = currentDate.withHour(hour).withMinute(minute).let {
            if (it < currentDate) it.plusDays(1) else it
        }
        return ChronoUnit.MINUTES.between(currentDate, date).toInt()
    }

    fun getTimestamp(input: Long, timestampFormat: String?): String? {
        val pattern = when (timestampFormat) {
            "0" -> "H:mm"
            "1" -> "HH:mm"
            "2" -> "H:mm:ss"
            "3" -> "HH:mm:ss"
            "4" -> "h:mm a"
            "5" -> "hh:mm a"
            "6" -> "h:mm:ss a"
            else -> "hh:mm:ss a"
        }
        return try {
            val date = LocalDateTime.ofInstant(Instant.ofEpochMilli(input), ZoneOffset.systemDefault())
            DateTimeFormatter.ofPattern(pattern).format(date)
        } catch (e: Exception) {
            null
        }
    }

    fun formatDate(context: Context, time: Long): String {
        val currentYear = Year.now().value
        val year = LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneOffset.UTC).year
        val format = if (year == currentYear) {
            DateUtils.FORMAT_NO_YEAR
        } else {
            DateUtils.FORMAT_SHOW_DATE
        }
        return DateUtils.formatDateTime(context, time, format)
    }

    fun formatCount(count: Int, compact: Boolean): String {
        return if (compact) {
            NumberFormatter.withLocale(Locale.getDefault())
                .notation(Notation.compactShort())
                .precision(Precision.maxFraction(1))
                .roundingMode(RoundingMode.DOWN)
                .format(count)
                .toString()
        } else {
            NumberFormat.getInstance().format(count)
        }
    }

    fun addTokenPrefixGQL(token: String) = TwitchImageUrls.addTokenPrefixGQL(token)
    fun addTokenPrefixHelix(token: String) = TwitchImageUrls.addTokenPrefixHelix(token)

    fun getGQLHeaders(context: Context, includeToken: Boolean = false): Map<String, String> {
        return if (context.prefs().getBoolean(C.ENABLE_INTEGRITY, false)) {
            TwitchHeaders.parseIntegrityHeaders(context.tokenPrefs().getString(C.GQL_HEADERS, null))
        } else {
            TwitchHeaders.getGqlHeaders(
                clientId = context.prefs().getString(C.GQL_CLIENT_ID2, TwitchHeaders.DEFAULT_GQL_CLIENT_ID),
                token = context.tokenPrefs().getString(C.GQL_TOKEN2, null),
                includeToken = includeToken,
            )
        }
    }

    fun getHelixHeaders(context: Context): Map<String, String> {
        return TwitchHeaders.getHelixHeaders(
            clientId = context.prefs().getString(C.HELIX_CLIENT_ID, TwitchHeaders.DEFAULT_HELIX_CLIENT_ID),
            token = context.tokenPrefs().getString(C.TOKEN, null),
        )
    }

    fun isIntegrityTokenExpired(context: Context): Boolean {
        return System.currentTimeMillis() >= context.tokenPrefs().getLong(C.INTEGRITY_EXPIRATION, 0)
    }

    fun getMessageIdString(context: Context, msgId: String?): String? {
        return when (msgId) {
            "highlighted-message" -> context.getString(R.string.irc_msgid_highlighted_message)
            "announcement" -> context.getString(R.string.irc_msgid_announcement)
            else -> null
        }
    }
}
