package com.github.andreyasadchy.xtra.util

/**
 * Pure-Kotlin subset of TwitchApiHelper safe for commonMain.
 * Android-specific formatting (Context, DateUtils, ICU) stays in app's TwitchApiHelper.
 */
object TwitchImageUrls {
    private val imageSizeRegex = Regex("-\\d+x\\d+\\.")

    fun getStreamThumbnail(url: String?): String? {
        return when {
            url.isNullOrBlank() -> "https://static-cdn.jtvnw.net/ttv-static/404_preview-440x248.jpg"
            url.contains("{width}x{height}") -> url.replace("{width}", "1280").replace("{height}", "720")
            else -> url.replace(imageSizeRegex, "-1280x720.")
        }
    }

    fun getVideoThumbnail(url: String?): String? {
        return when {
            url.isNullOrBlank() || url.startsWith("https://vod-secure.twitch.tv/_404/404_processing") -> {
                "https://vod-secure.twitch.tv/_404/404_processing_320x180.png"
            }
            url.contains("{width}x{height}") -> url.replace("{width}", "1280").replace("{height}", "720")
            url.contains("%{width}x%{height}") -> url.replace("%{width}", "1280").replace("%{height}", "720")
            else -> url.replace(imageSizeRegex, "-1280x720.")
        }
    }

    fun getClipThumbnail(url: String?): String? {
        return url?.replace(imageSizeRegex, "-1280x720.")
    }

    fun getGameBoxArt(url: String?): String? {
        return when {
            url.isNullOrBlank() -> "https://static-cdn.jtvnw.net/ttv-static/404_boxart.jpg"
            url.contains("{width}x{height}") -> url.replace("{width}", "285").replace("{height}", "380")
            else -> url.replace(imageSizeRegex, "-285x380.")
        }
    }

    fun getProfileImage(url: String?): String? {
        return url?.replace(imageSizeRegex, "-300x300.")
    }

    fun getDuration(duration: String): Int {
        val h = duration.substringBefore("h", "0").takeLastWhile { it.isDigit() }.toIntOrNull() ?: 0
        val m = duration.substringBefore("m", "0").takeLastWhile { it.isDigit() }.toIntOrNull() ?: 0
        val s = duration.substringBefore("s", "0").takeLastWhile { it.isDigit() }.toIntOrNull() ?: 0
        return (h * 3600) + (m * 60) + s
    }

    fun addTokenPrefixGQL(token: String) = "OAuth $token"
    fun addTokenPrefixHelix(token: String) = "Bearer $token"
}
