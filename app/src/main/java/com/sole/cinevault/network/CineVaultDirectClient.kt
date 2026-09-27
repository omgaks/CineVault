package com.sole.cinevault.network

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import com.google.gson.Gson
import com.sole.cinevault.VideoWithMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class CineVaultPairRequestEnvelope(
    val remoteDeviceId: String,
    val remoteDeviceName: String,
    val inviteNonce: String,
)

data class CineVaultPairResponseEnvelope(
    val state: String,
    val sessionToken: String? = null,
    val expiresAtEpochMs: Long? = null,
    val message: String? = null,
)

class CineVaultDirectClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build(),
    private val gson: Gson = Gson(),
) {
    suspend fun requestPairing(
        endpoint: String,
        request: NearbyPairingRequest,
    ): CineVaultPairResponseEnvelope = withContext(Dispatchers.IO) {
        val body = gson.toJson(
            CineVaultPairRequestEnvelope(
                request.remoteDeviceId,
                request.remoteDeviceName,
                request.inviteNonce,
            ),
        ).toRequestBody(JSON)

        http.newCall(
            Request.Builder()
                .url(CineVaultLanProtocol.pairRequestUrl(endpoint))
                .post(body)
                .build(),
        ).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext CineVaultPairResponseEnvelope(
                    state = "error",
                    message = text.ifBlank { "Pairing request failed (${response.code})." },
                )
            }
            gson.fromJson(text, CineVaultPairResponseEnvelope::class.java)
        }
    }

    suspend fun pollApproval(
        endpoint: String,
        remoteDeviceId: String,
        inviteNonce: String,
    ): CineVaultPairResponseEnvelope = withContext(Dispatchers.IO) {
        val url = CineVaultLanProtocol.pairApproveUrl(endpoint)
            .newBuilder()
            .addQueryParameter("deviceId", remoteDeviceId)
            .addQueryParameter("nonce", inviteNonce)
            .build()

        http.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext CineVaultPairResponseEnvelope(
                    state = "error",
                    message = text.ifBlank { "Approval check failed (${response.code})." },
                )
            }
            gson.fromJson(text, CineVaultPairResponseEnvelope::class.java)
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

class CineVaultDirectNetworkSource(
    override val displayName: String,
    private val endpoint: String,
    private val session: NearbyPairingSession,
    private val http: OkHttpClient = OkHttpClient(),
    private val gson: Gson = Gson(),
) : NetworkSource {
    override val type: NetworkType = NetworkType.CINEVAULT_GATEWAY
    override val id: String = stableNetworkSourceId(type, "$endpoint|${session.remoteDeviceId}")

    override fun createDataSourceFactory(): DataSource.Factory =
        DefaultHttpDataSource.Factory().apply {
            setDefaultRequestProperties(
                mapOf("Authorization" to CineVaultLanProtocol.bearerHeader(session)),
            )
        }

    override suspend fun testConnection(): NetworkConnectionResult = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(CineVaultLanProtocol.libraryUrl(endpoint))
                .header("Authorization", CineVaultLanProtocol.bearerHeader(session))
                .get()
                .build()
            http.newCall(request).execute().use {
                if (it.isSuccessful) NetworkConnectionResult.Connected
                else NetworkConnectionResult.Failed("CineVault Nearby access is no longer authorized.")
            }
        }.getOrElse { NetworkConnectionResult.Failed("CineVault Nearby device is unavailable.") }
    }

    override suspend fun scan(): List<NetworkVideo> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(CineVaultLanProtocol.libraryUrl(endpoint))
            .header("Authorization", CineVaultLanProtocol.bearerHeader(session))
            .get()
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("CineVault Nearby returned ${response.code}")
            val catalogue = gson.fromJson(
                response.body?.charStream(),
                CineVaultDirectCatalogue::class.java,
            ) ?: return@withContext emptyList()

            catalogue.items.map { item ->
                NetworkVideo(
                    path = CineVaultLanProtocol.resolve(endpoint, item.streamPath),
                    name = item.title,
                    size = item.sizeBytes,
                    posterUrl = item.artworkPath?.let { CineVaultLanProtocol.resolve(endpoint, it) },
                )
            }
        }
    }

    override suspend fun getMetadata(video: NetworkVideo): VideoWithMetadata? = null
}
