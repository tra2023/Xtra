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

class ChatWriteWebSocket(
    private val userLogin: String?,
    private val userToken: String?,
    private val channelLogin: String,
    private val listener: ChatReadWebSocket.Listener,
) {
    private var webSocket: WebSocket? = null
    private var scope: CoroutineScope? = null
    private var pingTimer: Timer? = null
    private var pongTimer: Timer? = null

    fun connect(coroutineScope: CoroutineScope): Job {
        scope = coroutineScope
        webSocket = WebSocket("wss://irc-ws.chat.twitch.tv", WebSocketListener())
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

    suspend fun send(message: CharSequence, replyId: String?) = withContext(Dispatchers.IO) {
        webSocket?.write(IrcRouter.chatSendText(channelLogin, message, replyId))
    }

    private inner class WebSocketListener : WebSocket.Listener {
        override suspend fun onConnect(webSocket: WebSocket) {
            IrcRouter.writeConnectWrites(channelLogin, userLogin, userToken).forEach {
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
