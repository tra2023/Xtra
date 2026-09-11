package com.github.andreyasadchy.xtra.socket

import com.github.andreyasadchy.xtra.util.chat.ChatParser
import kotlin.random.Random

sealed interface IrcLineEvent {
    data object Ping : IrcLineEvent
    data object Pong : IrcLineEvent
    data object Reconnect : IrcLineEvent
    data class Command(val command: String?, val message: ChatParser.IRCMessage) : IrcLineEvent
}

/**
 * Pure Twitch IRC line routing shared by the WebSocket and raw-socket transports.
 * Transport (writes, timers, TLS) stays platform-side; this only classifies lines
 * and builds the handshake/chat payloads.
 */
object IrcRouter {

    fun readNick(): String = "justinfan${Random.nextInt(1000, 10000)}"

    fun readConnectWrites(channelLogin: String, nick: String): List<String> = listOf(
        "CAP REQ :twitch.tv/tags twitch.tv/commands",
        "NICK $nick",
        "JOIN #$channelLogin",
    )

    fun writeConnectWrites(channelLogin: String, userLogin: String?, userToken: String?): List<String> = listOf(
        "CAP REQ :twitch.tv/tags twitch.tv/commands",
        "PASS oauth:$userToken",
        "NICK $userLogin",
        "JOIN #$channelLogin",
    )

    fun chatSendText(channelLogin: String, message: CharSequence, replyId: String?): String {
        val reply = replyId?.let { "@reply-parent-msg-id=${it} " } ?: ""
        return "${reply}PRIVMSG #$channelLogin :$message"
    }

    fun routeLine(line: String): IrcLineEvent {
        return when {
            line.startsWith("PING") -> IrcLineEvent.Ping
            line.startsWith("PONG") -> IrcLineEvent.Pong
            line.startsWith("RECONNECT") -> IrcLineEvent.Reconnect
            else -> {
                val ircMessage = ChatParser.parseIRCMessage(line)
                IrcLineEvent.Command(ircMessage.command, ircMessage)
            }
        }
    }
}
