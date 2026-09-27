package com.sole.cinevault.network

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class WebDavNetworkSource(
    private val baseUrl: String,
    override val displayName: String,
    private val credentialProvider: () -> NetworkCredential? = { null },
    private val client: OkHttpClient = createNetworkHttpClient(),
) : NetworkSource {
    init { require(isSafeHttpUrl(baseUrl)) { "WebDAV URL must be safe" } }

    override val type: NetworkType = NetworkType.WEBDAV
    override val id: String = stableNetworkSourceId(type, baseUrl)

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
        try {
            val request = Request.Builder()
                .url(baseUrl)
                .applyBasicCredential(credentialProvider())
                .header("Depth", "infinity")
                .method("PROPFIND", PROPFIND_BODY.toRequestBody(XML_MEDIA_TYPE))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) emptyList()
                else extractWebDavVideoHrefs(response.body?.string().orEmpty(), baseUrl)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun testConnection(): NetworkConnectionResult =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(baseUrl)
                    .applyBasicCredential(credentialProvider())
                    .header("Depth", "0")
                    .method("PROPFIND", PROPFIND_BODY.toRequestBody(XML_MEDIA_TYPE))
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful || response.code == 207)
                        NetworkConnectionResult.Connected
                    else safeNetworkFailure(displayName)
                }
            } catch (_: Exception) {
                safeNetworkFailure(displayName)
            }
        }

    private companion object {
        val XML_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaTypeCompat()
        const val PROPFIND_BODY =
            """<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getcontentlength/><d:getlastmodified/></d:prop></d:propfind>"""
    }
}

private fun String.toMediaTypeCompat() = okhttp3.MediaType.Companion.toMediaType(this)
