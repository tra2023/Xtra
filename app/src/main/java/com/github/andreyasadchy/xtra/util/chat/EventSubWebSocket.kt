package com.github.andreyasadchy.xtra.util.chat

import com.github.andreyasadchy.xtra.socket.EventSubEvent
import com.github.andreyasadchy.xtra.socket.EventSubRouter
import com.github.andreyasadchy.xtra.util.WebSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Timer
import javax.net.ssl.X509TrustManager
import kotlin.concurrent.schedule

class EventSubWebSocket(
    private val trustManager: Lazy<X509TrustManager>,
    private val listener: Listener,
) {
    private var webSocket: WebSocket? = null
    private var pongTimer: Timer? = null
    private var timeout = 10000L
    private val router = EventSubRouter()

    fun connect(coroutineScope: CoroutineScope): Job {
        webSocket = WebSocket("wss://eventsub.wss.twitch.tv/ws", trustManager, WebSocketListener())
        webSocket?.coroutineScope = coroutineScope
        return coroutineScope.launch(Dispatchers.IO) {
            webSocket?.start()
        }
    }

    suspend fun disconnect(job: Job?) = withContext(Dispatchers.IO) {
        pongTimer?.cancel()
        job?.cancel()
        webSocket?.disconnect()
    }

    private suspend fun startPongTimer() = withContext(Dispatchers.IO) {
        pongTimer = Timer().apply {
            schedule(timeout) {
                webSocket?.coroutineScope?.launch {
                    webSocket?.disconnect()
                }
            }
        }
    }

    interface Listener {
        suspend fun onConnect() {}
        suspend fun onWelcomeMessage(sessionId: String) {}
        suspend fun onChatMessage(event: String, timestamp: String?) {}
        suspend fun onUserNotice(event: String, timestamp: String?) {}
        suspend fun onClearChat(event: String, timestamp: String?) {}
        suspend fun onRoomState(event: String, timestamp: String?) {}
        suspend fun onDisconnect(message: String, fullMsg: String?) {}
    }

    private inner class WebSocketListener : WebSocket.Listener {
        override suspend fun onConnect(webSocket: WebSocket) {
            listener.onConnect()
        }

        override suspend fun onMessage(webSocket: WebSocket, message: String) {
            try {
                when (val event = router.route(message)) {
                    is EventSubEvent.ChatMessage -> listener.onChatMessage(event.eventJson, event.timestamp)
                    is EventSubEvent.UserNotice -> listener.onUserNotice(event.eventJson, event.timestamp)
                    is EventSubEvent.ClearChat -> listener.onClearChat(event.eventJson, event.timestamp)
                    is EventSubEvent.RoomState -> listener.onRoomState(event.eventJson, event.timestamp)
                    EventSubEvent.Keepalive -> {
                        pongTimer?.cancel()
                        startPongTimer()
                    }
                    EventSubEvent.Reconnect -> {
                        pongTimer?.cancel()
                        webSocket.disconnect()
                    }
                    is EventSubEvent.Welcome -> {
                        event.keepaliveTimeoutMs?.let { timeout = it }
                        pongTimer?.cancel()
                        startPongTimer()
                        event.sessionId?.takeIf { it.isNotBlank() }?.let {
                            listener.onWelcomeMessage(it)
                        }
                    }
                    EventSubEvent.Ignore -> {
                    }
                }
            } catch (e: Exception) {

            }
        }

        override suspend fun onDisconnect(webSocket: WebSocket, message: String, fullMsg: String?) {
            listener.onDisconnect(message, fullMsg)
        }
    }
}
