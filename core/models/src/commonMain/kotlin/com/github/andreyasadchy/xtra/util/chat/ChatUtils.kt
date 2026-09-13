package com.github.andreyasadchy.xtra.util.chat

import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.util.TwitchFormats

object ChatUtils {

    const val ACTION = ChatParser.ACTION

    typealias IRCMessage = ChatParser.IRCMessage

    fun parseIRCMessage(message: String): IRCMessage = ChatParser.parseIRCMessage(message)

    fun parseChatMessage(message: IRCMessage): ChatMessage = ChatParser.parseChatMessage(message)

    fun parseClearMessage(message: IRCMessage): ChatMessage = ChatParser.parseClearMessage(message)

    /**
     * Localized strings for [parseClearChat]. Android resolves these from resources
     * (`R.string.chat_timeout` etc.); other platforms provide their own.
     */
    class ClearChatStrings(
        /** `"...".format(login, duration)` for timeouts. */
        val timeoutFormat: String,
        /** `"...".format(login)` for bans. */
        val banFormat: String,
        /** Plain text for a full chat clear. */
        val clearText: String,
    )

    fun parseClearChat(
        message: IRCMessage,
        strings: ClearChatStrings,
        durationText: (seconds: Int) -> String,
    ): ChatMessage {
        val duration = message.tags["ban-duration"]
        val login = if (message.params.size >= 2) {
            message.params.lastOrNull()
        } else null
        val text = if (login != null) {
            if (duration != null) {
                strings.timeoutFormat.format(login, durationText(duration.toIntOrNull() ?: 0))
            } else {
                strings.banFormat.format(login)
            }
        } else {
            strings.clearText
        }
        return ChatMessage(
            type = if (login != null) {
                ChatMessage.USER_MESSAGE
            } else {
                ChatMessage.NOTICE_MESSAGE
            },
            userId = message.tags["target-user-id"],
            userLogin = login,
            systemMsg = text,
            timestamp = message.tags["tmi-sent-ts"]?.toLongOrNull(),
            fullMsg = message.fullMessage
        )
    }

    fun formatDurationFromSeconds(
        totalSeconds: Int,
        daysLabel: String,
        hoursLabel: String,
        minutesLabel: String,
        secondsLabel: String,
    ): String = TwitchFormats.formatDurationFromSeconds(
        totalSeconds, daysLabel, hoursLabel, minutesLabel, secondsLabel
    )

    fun parseNotice(message: IRCMessage): ChatMessage = ChatParser.parseNotice(message)
}
