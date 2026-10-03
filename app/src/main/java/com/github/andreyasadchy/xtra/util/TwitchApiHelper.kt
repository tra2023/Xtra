package com.github.andreyasadchy.xtra.util

import android.content.Context
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.repository.SharedAuthHeaders
import com.github.andreyasadchy.xtra.repository.TwitchAuthHeaders
import com.github.andreyasadchy.xtra.ui.common.xtraSettings
import com.github.andreyasadchy.xtra.util.chat.ChatUtils

object TwitchApiHelper {

    var checkedValidation = false
    var checkedUpdates = false

    // Shared with :core (the download dialog's fallback list and the player's VOD probing).
    val defaultQualityList: List<String> get() = TwitchApiDefaults.defaultQualityList
    val vodDomains: List<String> get() = TwitchApiDefaults.vodDomains

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

    fun formatCount(count: Int, compact: Boolean): String =
        TwitchFormats.formatCount(count, compact)

    fun addTokenPrefixGQL(token: String) = TwitchImageUrls.addTokenPrefixGQL(token)
    fun addTokenPrefixHelix(token: String) = TwitchImageUrls.addTokenPrefixHelix(token)

    /**
     * Android entry point for the shared header builders: `:core` owns the rules, this only reads
     * the two preference files. Every caller (fragments, services, activities) goes through here, so
     * there is a single implementation of "which keys, which defaults, token or not".
     */
    fun getGQLHeaders(context: Context, includeToken: Boolean = false): Map<String, String> =
        SharedAuthHeaders.gqlHeaders(SharedAuthHeaders.loadConfig(context.xtraSettings()), includeToken)

    fun getHelixHeaders(context: Context): Map<String, String> =
        SharedAuthHeaders.helixHeaders(SharedAuthHeaders.loadConfig(context.xtraSettings()))

    fun isIntegrityTokenExpired(context: Context): Boolean {
        return TwitchAuthHeaders.isIntegrityTokenExpired(
            System.currentTimeMillis(),
            context.xtraSettings().getLong(C.INTEGRITY_EXPIRATION, 0),
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
