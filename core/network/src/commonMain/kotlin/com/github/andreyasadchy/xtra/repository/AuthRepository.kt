package com.github.andreyasadchy.xtra.repository

import com.github.andreyasadchy.xtra.model.id.DeviceCodeResponse
import com.github.andreyasadchy.xtra.model.id.TokenResponse
import com.github.andreyasadchy.xtra.model.id.ValidationResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class AuthRepository(
    private val client: XtraHttpClient,
    private val json: Json,
) {

    suspend fun validate(networkLibrary: String?, token: String): ValidationResponse = withContext(Dispatchers.IO) {
        val response = client.execute(
            XtraHttpRequest(
                method = XtraHttpRequest.GET,
                url = "https://id.twitch.tv/oauth2/validate",
                headers = mapOf("Authorization" to token),
                engine = networkLibrary,
            )
        )
        if (response.code != 401) {
            json.decodeFromString<ValidationResponse>(response.bodyAsString())
        } else {
            throw IllegalStateException("401")
        }
    }

    suspend fun revoke(networkLibrary: String?, body: String) = withContext(Dispatchers.IO) {
        client.execute(
            XtraHttpRequest(
                method = XtraHttpRequest.POST,
                url = "https://id.twitch.tv/oauth2/revoke",
                headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
                body = body.toByteArray(),
                engine = networkLibrary,
            )
        )
    }

    suspend fun getDeviceCode(networkLibrary: String?, body: String): DeviceCodeResponse = withContext(Dispatchers.IO) {
        val response = client.execute(
            XtraHttpRequest(
                method = XtraHttpRequest.POST,
                url = "https://id.twitch.tv/oauth2/device",
                headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
                body = body.toByteArray(),
                engine = networkLibrary,
            )
        )
        json.decodeFromString<DeviceCodeResponse>(response.bodyAsString())
    }

    suspend fun getToken(networkLibrary: String?, body: String): TokenResponse = withContext(Dispatchers.IO) {
        val response = client.execute(
            XtraHttpRequest(
                method = XtraHttpRequest.POST,
                url = "https://id.twitch.tv/oauth2/token",
                headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
                body = body.toByteArray(),
                engine = networkLibrary,
            )
        )
        json.decodeFromString<TokenResponse>(response.bodyAsString())
    }
}
