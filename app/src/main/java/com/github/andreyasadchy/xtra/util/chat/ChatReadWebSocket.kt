package com.github.andreyasadchy.xtra.util.chat

import com.github.andreyasadchy.xtra.socket.IrcLineEvent
import com.github.andreyasadchy.xtra.socket.IrcRouter
import com.github.andreyasadchy.xtra.socket.WebSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Timer
import kotlin.concurrent.schedule

class ChatReadWebSocket(
    private val channelLogin: String,
    private val showGifMessages: Boolean,
    private val listener: Listener,
) {
    private var webSocket: WebSocket? = null
    private var scope: CoroutineScope? = null
    private var pingTimer: Timer? = null
    private var pongTimer: Timer? = null

    fun connect(coroutineScope: CoroutineScope): Job {
        scope = coroutineScope
        webSocket = WebSocket(
            url = "wss://irc-ws.chat.twitch.tv",
            listener = WebSocketListener(),
            headers = if (showGifMessages) {
                mapOf("Cookie" to "experiment_overrides={%22experiments%22:{}%2C%22disabled%22:[]}")
            } else null,
        )
        return coroutineScope.launch(Dispatchers.IO) {
            webSocket?.start()
        }
    }

    suspend fun disconnect(job: Job?) = withContext(Dispatchers.IO) {
        pingTimer?.cancel()
        pongTimer?.cancel()
        job?.cancel()
        webSocket?.disconnect()
        webSocket?.close()
        webSocket = null
        scope = null
    }

    private suspend fun startPingTimer() = withContext(Dispatchers.IO) {
        pingTimer = Timer().apply {
            schedule(270000) {
                scope?.launch {
                    webSocket?.write("PING")
                    startPongTimer()
                }
            }
        }
    }

    private suspend fun startPongTimer() = withContext(Dispatchers.IO) {
        pongTimer = Timer().apply {
            schedule(10000) {
                scope?.launch {
                    webSocket?.disconnect()
                }
            }
        }
    }

    interface Listener {
        suspend fun onConnect() {}
        suspend fun onChatMessage(message: ChatUtils.IRCMessage, userNotice: Boolean) {}
        suspend fun onClearMessage(message: ChatUtils.IRCMessage) {}
        suspend fun onClearChat(message: ChatUtils.IRCMessage) {}
        suspend fun onNotice(message: ChatUtils.IRCMessage) {}
        suspend fun onRoomState(message: ChatUtils.IRCMessage) {}
        suspend fun onUserState(message: ChatUtils.IRCMessage) {}
        suspend fun onDisconnect(message: String, fullMsg: String?) {}
    }

    private inner class WebSocketListener : WebSocket.Listener {
        override suspend fun onConnect(webSocket: WebSocket) {
            IrcRouter.readConnectWrites(channelLogin, IrcRouter.readNick()).forEach {
                webSocket.write(it)
            }
            listener.onConnect()
            pingTimer?.cancel()
            pongTimer?.cancel()
            startPingTimer()
        }

        override suspend fun onMessage(webSocket: WebSocket, message: String) {
            message.removeSuffix("\r\n").split("\r\n").forEach {
                when (val event = IrcRouter.routeLine(it)) {
                    IrcLineEvent.Ping -> {
                        webSocket.write("PONG")
                    }
                    IrcLineEvent.Pong -> {
                        pingTimer?.cancel()
                        pongTimer?.cancel()
                        startPingTimer()
                    }
                    IrcLineEvent.Reconnect -> {
                        pingTimer?.cancel()
                        pongTimer?.cancel()
                        webSocket.disconnect()
                    }
                    is IrcLineEvent.Command -> {
                        when (event.command) {
                            "PRIVMSG" -> listener.onChatMessage(event.message, false)
                            "USERNOTICE" -> listener.onChatMessage(event.message, true)
                            "CLEARMSG" -> listener.onClearMessage(event.message)
                            "CLEARCHAT" -> listener.onClearChat(event.message)
                            "NOTICE" -> listener.onNotice(event.message)
                            "ROOMSTATE" -> listener.onRoomState(event.message)
                            "USERSTATE" -> listener.onUserState(event.message)
                        }
                    }
                }
            }
        }

        override suspend fun onDisconnect(webSocket: WebSocket, message: String, fullMsg: String?) {
            listener.onDisconnect(message, fullMsg)
        }
    }
}
