package com.github.andreyasadchy.xtra.repository

class XtraHttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
    /** Engine hint from user prefs: C.HTTP_ENGINE / C.CRONET / C.OKHTTP, null = default. */
    val engine: String? = null,
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

fun interface XtraHttpClient {
    suspend fun execute(request: XtraHttpRequest): XtraHttpResponse
}
