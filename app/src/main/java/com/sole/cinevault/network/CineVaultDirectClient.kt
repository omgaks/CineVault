package com.sole.cinevault.network

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import com.google.gson.Gson
import com.google.gson.JsonParser
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

internal fun parsePairResponse(raw: String): CineVaultPairResponseEnvelope {
    val json = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()
        ?: return CineVaultPairResponseEnvelope(
            state = "error",
            message = "The sharing device returned an invalid pairing response.",
        )

    val state = json.get("state")
        ?.takeUnless { it.isJsonNull }
        ?.asString
        ?.trim()
        ?.lowercase()
        ?.takeIf { it in setOf("pending", "approved", "denied", "error") }
        ?: return CineVaultPairResponseEnvelope(
            state = "error",
            message = "The sharing device returned an incomplete pairing response.",
        )

    val token = json.get("sessionToken")
        ?.takeUnless { it.isJsonNull }
        ?.asString
        ?.trim()
        ?.takeIf { it.length >= 32 }
    val expiry = json.get("expiresAtEpochMs")
        ?.takeUnless { it.isJsonNull }
        ?.runCatching { asLong }
        ?.getOrNull()
        ?.takeIf { it > 0L }
    val message = json.get("message")
        ?.takeUnless { it.isJsonNull }
        ?.asString
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    if (state == "approved" && (token == null || expiry == null)) {
        return CineVaultPairResponseEnvelope(
            state = "error",
            message = "The approved session was incomplete. Please approve the connection again.",
        )
    }

    return CineVaultPairResponseEnvelope(
        state = state,
        sessionToken = token,
        expiresAtEpochMs = expiry,
        message = message,
    )
}

/**
 * Approval polling tolerates an empty/truncated LAN response instead of
 * tearing down pairing and forcing the owner to approve the same peer again.
 */
internal fun parseApprovalPollResponse(raw: String): CineVaultPairResponseEnvelope {
    if (raw.isBlank()) return CineVaultPairResponseEnvelope(state = "pending")

    val parsed = parsePairResponse(raw)
    if (parsed.state != "error") return parsed

    val hasRecognisedState = runCatching {
        JsonParser.parseString(raw)
            .asJsonObject
            .get("state")
            ?.takeUnless { it.isJsonNull }
            ?.asString
            ?.trim()
            ?.lowercase() in setOf("pending", "approved", "denied", "error")
    }.getOrDefault(false)

    return if (hasRecognisedState) parsed
    else CineVaultPairResponseEnvelope(state = "pending")
}


internal fun parseDirectCatalogue(raw: String): CineVaultDirectCatalogue {
    if (raw.isBlank()) return CineVaultDirectCatalogue(items = emptyList())

    val root = runCatching { JsonParser.parseString(raw) }.getOrNull()
        ?: error("CineVault Nearby returned an invalid library response.")
    if (!root.isJsonObject) error("CineVault Nearby returned an incompatible library response.")

    val obj = root.asJsonObject
    val version = obj.get("protocolVersion")
        ?.takeUnless { it.isJsonNull }
        ?.runCatching { asInt }
        ?.getOrNull()
        ?: CineVaultLanProtocol.VERSION

    val itemsElement = obj.get("items") ?: return CineVaultDirectCatalogue(
        protocolVersion = version,
        items = emptyList(),
    )
    if (!itemsElement.isJsonArray) {
        error("CineVault Nearby returned an incompatible library response.")
    }

    val items = itemsElement.asJsonArray.mapNotNull { element ->
        val item = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
        val id = item.stringValue("id") ?: return@mapNotNull null
        val title = item.stringValue("title") ?: return@mapNotNull null
        val streamPath = item.stringValue("streamPath") ?: return@mapNotNull null
        val sizeBytes = item.get("sizeBytes")
            ?.takeUnless { it.isJsonNull }
            ?.runCatching { asLong }
            ?.getOrNull()
            ?: 0L
        val mimeType = item.stringValue("mimeType") ?: "video/*"
        val subtitlePaths = item.get("subtitlePaths")
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
            ?.mapNotNull { value ->
                value.takeUnless { it.isJsonNull }
                    ?.runCatching { asString.trim() }
                    ?.getOrNull()
                    ?.takeIf { it.isNotBlank() }
            }
            ?: emptyList()
        val artworkPath = item.stringValue("artworkPath")

        CineVaultDirectCatalogueItem(
            id = id,
            title = title,
            sizeBytes = sizeBytes,
            mimeType = mimeType,
            streamPath = streamPath,
            subtitlePaths = subtitlePaths,
            artworkPath = artworkPath,
        )
    }

    return CineVaultDirectCatalogue(protocolVersion = version, items = items)
}

private fun com.google.gson.JsonObject.stringValue(name: String): String? =
    get(name)
        ?.takeUnless { it.isJsonNull }
        ?.runCatching { asString.trim() }
        ?.getOrNull()
        ?.takeIf { it.isNotBlank() }

class CineVaultDirectClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(100, TimeUnit.SECONDS)
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
            parsePairResponse(text)
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
            parseApprovalPollResponse(text)
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
            val raw = response.body?.string().orEmpty()
            val catalogue = parseDirectCatalogue(raw)

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
