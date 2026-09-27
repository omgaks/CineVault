package com.sole.cinevault.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID

class MediaServerApi(
    serverUrl: String,
    private val kind: MediaServerKind,
    private val client: OkHttpClient = createNetworkHttpClient(),
    private val deviceId: String = UUID.randomUUID().toString(),
) {
    val baseUrl: String = normalizeServerUrl(serverUrl)

    init {
        require(isSafeHttpUrl(baseUrl)) { "Media server URL must be a safe HTTP(S) URL" }
    }

    suspend fun authenticate(username: String, password: String): Result<MediaServerSession> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(username.isNotBlank()) { "Username is required" }
                val body = JSONObject()
                    .put("Username", username)
                    .put("Pw", password)
                    .toString()
                    .toRequestBody(JSON)

                val request = Request.Builder()
                    .url("$baseUrl/Users/AuthenticateByName")
                    .header("X-Emby-Authorization", authorizationHeader())
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("Authentication failed")
                    parseMediaServerAuthentication(response.body?.string().orEmpty(), baseUrl)
                        ?: error("Invalid authentication response")
                }
            }
        }

    suspend fun test(session: MediaServerSession): NetworkConnectionResult =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/System/Info")
                    .header("X-Emby-Authorization", authorizationHeader(session))
                    .header("X-Emby-Token", session.accessToken)
                    .get()
                    .build()
                client.newCall(request).execute().use {
                    if (it.isSuccessful) NetworkConnectionResult.Connected
                    else safeNetworkFailure(kind.name.lowercase().replaceFirstChar(Char::uppercase))
                }
            } catch (_: Exception) {
                safeNetworkFailure(kind.name.lowercase().replaceFirstChar(Char::uppercase))
            }
        }

    suspend fun getItems(session: MediaServerSession): List<MediaServerItem> =
        withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl/Users/${urlSegment(session.userId)}/Items".toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("Recursive", "true")
                    .addQueryParameter("IncludeItemTypes", "Movie,Episode,Video")
                    .addQueryParameter("Fields", "Overview,CommunityRating,PrimaryImageAspectRatio,MediaSources")
                    .addQueryParameter("SortBy", "SortName")
                    .addQueryParameter("SortOrder", "Ascending")
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .header("X-Emby-Authorization", authorizationHeader(session))
                    .header("X-Emby-Token", session.accessToken)
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) emptyList()
                    else parseMediaServerItems(response.body?.string().orEmpty())
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

    fun directStreamUrl(item: MediaServerItem, session: MediaServerSession): String {
        val extension = item.container
            ?.substringBefore(',')
            ?.lowercase()
            ?.takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
            ?.let { ".$it" }
            .orEmpty()

        return "$baseUrl/Videos/${urlSegment(item.id)}/stream$extension".toHttpUrl()
            .newBuilder()
            .addQueryParameter("static", "true")
            .addQueryParameter("api_key", session.accessToken)
            .build()
            .toString()
    }

    fun imageUrl(item: MediaServerItem, session: MediaServerSession): String? {
        if (item.primaryImageTag.isNullOrBlank()) return null
        return "$baseUrl/Items/${urlSegment(item.id)}/Images/Primary".toHttpUrl()
            .newBuilder()
            .addQueryParameter("tag", item.primaryImageTag)
            .addQueryParameter("maxWidth", "600")
            .addQueryParameter("quality", "90")
            .addQueryParameter("api_key", session.accessToken)
            .build()
            .toString()
    }

    fun authorizationHeader(session: MediaServerSession? = null): String =
        buildString {
            append("MediaBrowser ")
            append("""Client="CineVault", """)
            append("""Device="Android", """)
            append("""DeviceId="${headerValue(deviceId)}", """)
            append("""Version="1"""")
            if (session != null) {
                append(""", UserId="${headerValue(session.userId)}"""")
                append(""", Token="${headerValue(session.accessToken)}"""")
            }
        }

    private fun headerValue(value: String): String =
        value.replace("\\", "").replace("\"", "").replace("\r", "").replace("\n", "").take(256)

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
