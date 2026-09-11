package com.github.andreyasadchy.xtra.util.chat

import android.content.Context
import androidx.core.content.ContextCompat
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.util.TwitchApiHelper

object ChatUtils {

    const val ACTION = ChatParser.ACTION

    typealias IRCMessage = ChatParser.IRCMessage

    fun parseIRCMessage(message: String): IRCMessage = ChatParser.parseIRCMessage(message)

    fun parseChatMessage(message: IRCMessage): ChatMessage = ChatParser.parseChatMessage(message)

    fun parseClearMessage(message: IRCMessage): ChatMessage = ChatParser.parseClearMessage(message)

    fun parseClearChat(context: Context, message: IRCMessage): ChatMessage {
        val duration = message.tags["ban-duration"]
        val login = if (message.params.size >= 2) {
            message.params.lastOrNull()
        } else null
        val text = if (login != null) {
            if (duration != null) {
                ContextCompat.getString(context, R.string.chat_timeout).format(login, TwitchApiHelper.getDurationFromSeconds(context, duration))
            } else {
                ContextCompat.getString(context, R.string.chat_ban).format(login)
            }
        } else {
            ContextCompat.getString(context, R.string.chat_clear)
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

    fun parseNotice(message: IRCMessage): ChatMessage = ChatParser.parseNotice(message)
}
