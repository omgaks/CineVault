package com.sole.cinevault.network

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class HttpNetworkSource(
    private val url: String,
    override val displayName: String,
    private val playlist: Boolean = false,
    private val credentialProvider: () -> NetworkCredential? = { null },
    private val client: OkHttpClient = createNetworkHttpClient(),
) : NetworkSource {
    init { require(isSafeHttpUrl(url)) { "HTTP source URL must be safe" } }

    override val type: NetworkType =
        if (playlist) NetworkType.M3U else NetworkType.HTTP_DIRECTORY

    override val id: String = stableNetworkSourceId(type, url)

    override fun createDataSourceFactory(): DataSource.Factory {
        val credential = credentialProvider()
        return DefaultHttpDataSource.Factory().apply {
            if (credential != null && credential.username.isNotBlank()) {
                setDefaultRequestProperties(
                    mapOf(
                        "Authorization" to okhttp3.Credentials.basic(
                            credential.username,
                            credential.secret,
                        )
                    )
                )
            }
        }
    }

    override suspend fun scan(): List<NetworkVideo> = withContext(Dispatchers.IO) {
        val body = executeTextRequest() ?: return@withContext emptyList()
        if (playlist) {
            parseM3u(body).map { NetworkVideo(path = it.url, name = it.name) }
        } else {
            extractHttpDirectoryVideos(body, url)
        }
    }

    override suspend fun testConnection(): NetworkConnectionResult =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url)
                    .applyBasicCredential(credentialProvider())
                    .head()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) NetworkConnectionResult.Connected
                    else safeNetworkFailure(displayName)
                }
            } catch (_: Exception) {
                safeNetworkFailure(displayName)
            }
        }

    private fun executeTextRequest(): String? =
        try {
            val request = Request.Builder().url(url)
                .applyBasicCredential(credentialProvider())
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        } catch (_: Exception) {
            null
        }
}
