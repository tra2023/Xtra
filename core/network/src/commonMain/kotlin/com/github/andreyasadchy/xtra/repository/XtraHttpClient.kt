package com.github.andreyasadchy.xtra.repository

class XtraHttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
    /** Per-request timeout override in ms (e.g. large VOD chat downloads), null = default. */
    val timeoutMs: Long? = null,
) {
    companion object {
        const val GET = "GET"
        const val POST = "POST"
        const val DELETE = "DELETE"
        const val PUT = "PUT"
        const val PATCH = "PATCH"
    }
}

class XtraHttpResponse(
    val code: Int,
    val body: ByteArray,
) {
    fun bodyAsString(): String = body.decodeToString()
}

interface XtraHttpClient {
    suspend fun execute(request: XtraHttpRequest): XtraHttpResponse

    /**
     * Downloads [url], reporting progress. Default implementation buffers via
     * [execute] and reports once; engines override with true streaming progress.
     */
    suspend fun download(
        url: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Long? = null,
        onProgress: (bytesRead: Long, contentLength: Long?) -> Unit = { _, _ -> },
    ): ByteArray {
        val response = execute(XtraHttpRequest(XtraHttpRequest.GET, url, headers, timeoutMs = timeoutMs))
        if (response.code !in 200..299) {
            throw IllegalStateException("HTTP ${response.code} for $url")
        }
        onProgress(response.body.size.toLong(), response.body.size.toLong())
        return response.body
    }
}

/** GET body as string, throwing on transport errors (like OkHttp), without status check. */
suspend fun XtraHttpClient.getString(
    url: String,
    headers: Map<String, String> = emptyMap(),
    timeoutMs: Long? = null,
): String {
    return execute(XtraHttpRequest(XtraHttpRequest.GET, url, headers, timeoutMs = timeoutMs)).bodyAsString()
}

/** GET body as string if HTTP 2xx, else null. Transport errors still throw. */
suspend fun XtraHttpClient.getStringOrNull(
    url: String,
    headers: Map<String, String> = emptyMap(),
    timeoutMs: Long? = null,
): String? {
    val response = execute(XtraHttpRequest(XtraHttpRequest.GET, url, headers, timeoutMs = timeoutMs))
    return if (response.code in 200..299) response.bodyAsString() else null
}

/** GET body bytes, throwing on transport errors, without status check. */
suspend fun XtraHttpClient.getBytes(
    url: String,
    headers: Map<String, String> = emptyMap(),
    timeoutMs: Long? = null,
): ByteArray {
    return execute(XtraHttpRequest(XtraHttpRequest.GET, url, headers, timeoutMs = timeoutMs)).body
}

/** GET body bytes if HTTP 2xx, else null. Transport errors still throw. */
suspend fun XtraHttpClient.getBytesOrNull(
    url: String,
    headers: Map<String, String> = emptyMap(),
    timeoutMs: Long? = null,
): ByteArray? {
    val response = execute(XtraHttpRequest(XtraHttpRequest.GET, url, headers, timeoutMs = timeoutMs))
    return if (response.code in 200..299) response.body else null
}
