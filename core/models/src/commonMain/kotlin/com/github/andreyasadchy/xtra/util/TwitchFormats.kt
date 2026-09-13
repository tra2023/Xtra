package com.github.andreyasadchy.xtra.util

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Pure-Kotlin date/number/duration formatting shared by Android and JVM desktop.
 * Moved out of the app's `TwitchApiHelper`; Android string resources are passed in
 * as plain labels so this stays platform-free.
 */
object TwitchFormats {

    fun formatDurationFromSeconds(
        totalSeconds: Int,
        daysLabel: String,
        hoursLabel: String,
        minutesLabel: String,
        secondsLabel: String,
    ): String {
        val days = (totalSeconds / 86400)
        val hours = ((totalSeconds % 86400) / 3600)
        val minutes = (((totalSeconds % 86400) % 3600) / 60)
        val seconds = (totalSeconds % 60)
        return buildString {
            if (days > 0) {
                append("$days$daysLabel")
            }
            if (hours > 0) {
                if (isNotBlank()) {
                    append(" ")
                }
                append("$hours$hoursLabel")
            }
            if (minutes > 0) {
                if (isNotBlank()) {
                    append(" ")
                }
                append("$minutes$minutesLabel")
            }
            if (seconds > 0) {
                if (isNotBlank()) {
                    append(" ")
                }
                append("$seconds$secondsLabel")
            }
        }
    }

    fun formatCount(count: Int, compact: Boolean): String {
        return if (compact) {
            formatCompactCount(count)
        } else {
            formatGroupedCount(count)
        }
    }

    /**
     * Short scale (`1.2K`, `3M`) with at most one fraction digit, rounded down.
     * Matches `android.icu.number` compactShort for `en`; other locales always
     * use `.` as the decimal separator.
     */
    fun formatCompactCount(count: Int): String {
        val value = count.toDouble()
        return when {
            value >= 1_000_000_000 -> "${trimDown(value / 1_000_000_000)}B"
            value >= 1_000_000 -> "${trimDown(value / 1_000_000)}M"
            value >= 1_000 -> "${trimDown(value / 1_000)}K"
            else -> count.toString()
        }
    }

    private fun trimDown(value: Double): String {
        if (value >= 100) return value.toInt().toString()
        val truncated = (value * 10).toInt() / 10.0
        return if (truncated == truncated.toInt().toDouble()) {
            truncated.toInt().toString()
        } else {
            truncated.toString()
        }
    }

    /**
     * Chat timestamp patterns, same `"0"`-`"7"` codes as the Android preferences:
     * `H:mm`, `HH:mm`, `H:mm:ss`, `HH:mm:ss`, `h:mm a`, `hh:mm a`, `h:mm:ss a`, else `hh:mm:ss a`.
     */
    fun formatTimestampMillis(epochMillis: Long, timestampFormat: String?): String? {
        return try {
            val dateTime = Instant.fromEpochMilliseconds(epochMillis)
                .toLocalDateTime(TimeZone.currentSystemDefault())
            val hour = dateTime.hour
            val minute = dateTime.minute
            val second = dateTime.second
            val hour12 = if (hour % 12 == 0) 12 else hour % 12
            val amPm = if (hour < 12) "AM" else "PM"
            fun pad(value: Int) = value.toString().padStart(2, '0')
            when (timestampFormat) {
                "0" -> "$hour:${pad(minute)}"
                "1" -> "${pad(hour)}:${pad(minute)}"
                "2" -> "$hour:${pad(minute)}:${pad(second)}"
                "3" -> "${pad(hour)}:${pad(minute)}:${pad(second)}"
                "4" -> "$hour12:${pad(minute)} $amPm"
                "5" -> "${pad(hour12)}:${pad(minute)} $amPm"
                "6" -> "$hour12:${pad(minute)}:${pad(second)} $amPm"
                else -> "${pad(hour12)}:${pad(minute)}:${pad(second)} $amPm"
            }
        } catch (_: Exception) {
            null
        }
    }

    @OptIn(ExperimentalTime::class)
    fun minutesLeft(hour: Int, minute: Int): Int {
        val timeZone = TimeZone.currentSystemDefault()
        val now = Clock.System.now().toLocalDateTime(timeZone)
        var target = LocalDateTime(now.date, LocalTime(hour, minute))
        if (target < now) {
            target = LocalDateTime(target.date.plus(DatePeriod(days = 1)), target.time)
        }
        val diffMinutes = (target.date.toEpochDays() - now.date.toEpochDays()) * 24 * 60 +
            (target.hour * 60 + target.minute) - (now.hour * 60 + now.minute)
        return diffMinutes.toInt()
    }
}

/** Locale-grouped integer (`1,234`) — `java.text` on both Android and JVM desktop. */
expect fun formatGroupedCount(count: Int): String

/** Human date without year when the year is current (`Dec 5` / `Dec 5, 2024`-style). */
expect fun formatChatDate(timeMillis: Long): String
