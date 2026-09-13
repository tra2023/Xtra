package com.github.andreyasadchy.xtra.socket

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Ktor-based WebSocket with the same reconnect contract as the old
 * hand-rolled `app` implementation.
 *
 * - [start] runs the connect/receive loop until cancelled or the retry
 *   budget (20 attempts) is exhausted.
 * - [disconnect] closes the current session. If [start] is still running it
 *   will reconnect; call [close] for final teardown to release the client.
 * - Ktor's WebSockets plugin answers pings and handles permessage-deflate,
 *   so manual frame parsing, ping timers and TLS trust-manager plumbing are
 *   no longer needed.
 */
class WebSocket(
    private val url: String,
    private val listener: Listener,
    private val headers: Map<String, String>? = null,
    private val pingIntervalMillis: Long = 20_000,
    client: HttpClient? = null,
) {
    private val ownedClient = client == null
    private val client: HttpClient = client ?: HttpClient(CIO) {
        install(WebSockets) {
            pingIntervalMillis = this@WebSocket.pingIntervalMillis
        }
    }

    private var session: io.ktor.client.plugins.websocket.ClientWebSocketSession? = null
    private val writeMutex = Mutex()
    private var connectionAttempt = 0
    private var delayReconnect = false

    suspend fun start() {
        while (currentCoroutineContext().isActive) {
            try {
                connectionAttempt += 1
                client.webSocketSession {
                    url(this@WebSocket.url)
                    this@WebSocket.headers?.forEach { entry -> header(entry.key, entry.value) }
                }.also { newSession ->
                    session = newSession
                }
                connectionAttempt = 0
                delayReconnect = false
                listener.onConnect(this@WebSocket)
                for (frame in session!!.incoming) {
                    currentCoroutineContext().ensureActive()
                    when (frame) {
                        is Frame.Text -> listener.onMessage(this@WebSocket, frame.readText())
                        is Frame.Binary -> listener.onMessage(this@WebSocket, frame.data.decodeToString())
                        is Frame.Close -> break
                        else -> Unit
                    }
                }
            } catch (e: CancellationException) {
                currentCoroutineContext().ensureActive()
                throw e
            } catch (e: Exception) {
                val message = e.toString()
                if (message.contains("429") || message.contains("Too Many Requests", ignoreCase = true)) {
                    delayReconnect = true
                }
                try {
                    listener.onDisconnect(this@WebSocket, message, e.stackTraceToString())
                } catch (_: Exception) {
                }
            }
            try {
                session?.close()
            } catch (_: Exception) {
            }
            session = null
            if (!currentCoroutineContext().isActive) return
            if (connectionAttempt >= MAX_RECONNECT_ATTEMPTS) return
            if (delayReconnect) {
                delayReconnect = false
                delay(1.minutes)
            } else {
                delay(1.seconds)
            }
        }
    }

    suspend fun write(message: String) {
        val current = session ?: return
        if (!current.isActive) return
        writeMutex.withLock {
            try {
                current.send(Frame.Text(message))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    suspend fun disconnect() {
        try {
            session?.close(CloseReason(CloseReason.Codes.NORMAL, ""))
        } catch (_: Exception) {
        } finally {
            session = null
        }
    }

    suspend fun close() {
        try {
            session?.close()
        } catch (_: Exception) {
        } finally {
            session = null
        }
        if (ownedClient) {
            try {
                client.close()
            } catch (_: Exception) {
            }
        }
    }

    interface Listener {
        suspend fun onConnect(webSocket: WebSocket) {}
        suspend fun onMessage(webSocket: WebSocket, message: String) {}
        suspend fun onDisconnect(webSocket: WebSocket, message: String, fullMsg: String? = null) {}
    }

    companion object {
        private const val MAX_RECONNECT_ATTEMPTS = 20
    }
}
