package com.github.andreyasadchy.xtra.repository

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.onDownload
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpMethod

/**
 * Multiplatform [XtraHttpClient] on Ktor + CIO for Android and JVM desktop.
 * Mirrors `app`'s OkHttp-based `AppXtraHttpClient` (same methods, timeouts,
 * header handling); the Android app keeps its OkHttp implementation for now.
 *
 * Prefer injecting a shared [HttpClient]; the default creates its own CIO engine.
 */
class KtorXtraHttpClient(
    private val client: HttpClient = HttpClient(CIO),
) : XtraHttpClient {

    override suspend fun execute(request: XtraHttpRequest): XtraHttpResponse {
        val response = client.request(request.url) {
            method = HttpMethod.parse(request.method)
            headers {
                request.headers.forEach { (key, value) -> append(key, value) }
            }
            request.body?.let { setBody(it) }
            request.timeoutMs?.let { timeoutMs ->
                timeout { requestTimeoutMillis = timeoutMs }
            }
        }
        return XtraHttpResponse(response.status.value, response.bodyAsBytes())
    }

    override suspend fun download(
        url: String,
        headers: Map<String, String>,
        timeoutMs: Long?,
        onProgress: (bytesRead: Long, contentLength: Long?) -> Unit,
    ): ByteArray {
        return client.downloadWithProgress(url, headers, timeoutMs, onProgress)
    }
}

/**
 * Downloads [url] reporting progress. Common replacement for
 * `NetworkUtils.ProgressInterceptor` + `Call.executeAsync`, which are OkHttp-bound.
 */
suspend fun HttpClient.downloadWithProgress(
    url: String,
    headers: Map<String, String> = emptyMap(),
    timeoutMs: Long? = null,
    onProgress: (bytesRead: Long, contentLength: Long?) -> Unit,
): ByteArray {
    return get(url) {
        headers {
            headers.forEach { (key, value) -> append(key, value) }
        }
        timeoutMs?.let {
            timeout { requestTimeoutMillis = it }
        }
        onDownload { bytesSentTotal, contentLength ->
            onProgress(bytesSentTotal, contentLength)
        }
    }.bodyAsBytes()
}
