package com.github.andreyasadchy.xtra.util.chat

import com.github.andreyasadchy.xtra.socket.IrcLineEvent
import com.github.andreyasadchy.xtra.socket.IrcRouter
import com.github.andreyasadchy.xtra.socket.WebSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatWriteWebSocket(
    private val userLogin: String?,
    private val userToken: String?,
    private val channelLogin: String,
    private val listener: ChatReadWebSocket.Listener,
) {
    private var webSocket: WebSocket? = null
    private var scope: CoroutineScope? = null
    private var pingJob: Job? = null
    private var pongJob: Job? = null

    fun connect(coroutineScope: CoroutineScope): Job {
        scope = coroutineScope
        webSocket = WebSocket("wss://irc-ws.chat.twitch.tv", WebSocketListener())
        return coroutineScope.launch(Dispatchers.IO) {
            webSocket?.start()
        }
    }

    suspend fun disconnect(job: Job?) = withContext(Dispatchers.IO) {
        pingJob?.cancel()
        pongJob?.cancel()
        pingJob = null
        pongJob = null
        job?.cancel()
        webSocket?.disconnect()
        webSocket?.close()
        webSocket = null
        scope = null
    }

    private fun startPingTimer() {
        pingJob?.cancel()
        pingJob = scope?.launch {
            delay(PING_INTERVAL_MS)
            webSocket?.write("PING")
            startPongTimer()
        }
    }

    private fun startPongTimer() {
        pongJob?.cancel()
        pongJob = scope?.launch {
            delay(PONG_TIMEOUT_MS)
            webSocket?.disconnect()
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
            pingJob?.cancel()
            pongJob?.cancel()
            startPingTimer()
        }

        override suspend fun onMessage(webSocket: WebSocket, message: String) {
            message.removeSuffix("\r\n").split("\r\n").forEach {
                when (val event = IrcRouter.routeLine(it)) {
                    IrcLineEvent.Ping -> {
                        webSocket.write("PONG")
                    }
                    IrcLineEvent.Pong -> {
                        pingJob?.cancel()
                        pongJob?.cancel()
                        startPingTimer()
                    }
                    IrcLineEvent.Reconnect -> {
                        pingJob?.cancel()
                        pongJob?.cancel()
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

    private companion object {
        const val PING_INTERVAL_MS = 270000L
        const val PONG_TIMEOUT_MS = 10000L
    }
}
