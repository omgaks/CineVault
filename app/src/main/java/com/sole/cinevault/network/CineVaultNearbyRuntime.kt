package com.sole.cinevault.network

import android.content.Context
import android.os.Build
import com.sole.cinevault.library.isRestrictedFolderItem
import com.sole.cinevault.library.loadLibraryCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.UUID

data class CineVaultSharingState(
    val running: Boolean = false,
    val endpoint: String? = null,
    val invite: NearbyPairingInvite? = null,
    val pending: NearbyPairingRequest? = null,
    val approved: NearbyPairingSession? = null,
)

class CineVaultNearbyRuntime(private val context: Context) {
    private val app = context.applicationContext
    private val authorization = CineVaultDirectAuthorization()
    private val policy = NearbyPairingPolicy()
    private val advertiser = CineVaultLanAdvertiser(app)
    private var selection = ShareLibrarySelection(SharedLibraryScope.ENTIRE_LIBRARY)
    private val server = CineVaultDirectServer(
        authorization = authorization,
        mediaProvider = { mediaSnapshot },
        selectionProvider = { selection },
    )
    private var mediaSnapshot: List<CineVaultDirectMedia> = emptyList()

    private val _connectState = MutableStateFlow(CineVaultConnectState())
    val connectState: StateFlow<CineVaultConnectState> = _connectState.asStateFlow()

    private fun publish(sharing: CineVaultSharingState): CineVaultSharingState {
        _connectState.value = _connectState.value.withSharing(sharing)
        return sharing
    }

    val deviceId: String by lazy {
        val prefs = app.getSharedPreferences("cinevault_nearby", Context.MODE_PRIVATE)
        prefs.getString("device_id", null)?.takeIf(::isSafeLanDeviceId)
            ?: ("cv_" + UUID.randomUUID().toString().replace("-", "")).also {
                prefs.edit().putString("device_id", it).apply()
            }
    }

    val deviceName: String
        get() = "CineVault • " + (Build.MODEL?.trim()?.takeIf(String::isNotBlank) ?: "Android")

    suspend fun startSharing(newSelection: ShareLibrarySelection): CineVaultSharingState =
        withContext(Dispatchers.IO) {
            selection = newSelection
            mediaSnapshot = loadShareableMedia()
            val port = server.start()
            val host = localIpv4Address() ?: run {
                server.stop()
                error("CineVault could not determine this device's Wi-Fi address.")
            }
            advertiser.start(deviceId, deviceName, port)
            val invite = policy.createInvite(deviceId, deviceName)
            publish(
                CineVaultSharingState(
                    running = true,
                    endpoint = "http://$host:$port",
                    invite = invite,
                )
            )
        }

    fun refresh(state: CineVaultSharingState = _connectState.value.sharing): CineVaultSharingState {
        if (!state.running) return publish(state)
        val requests = server.pendingRequests()
        val request = requests.firstOrNull { it.inviteNonce == state.invite?.nonce }
            ?: requests.firstOrNull()
        return publish(state.copy(pending = request))
    }

    fun approve(state: CineVaultSharingState): CineVaultSharingState {
        val invite = state.invite ?: return state
        val request = state.pending ?: return state
        val session = if (request.inviteNonce == invite.nonce) {
            server.approve(invite, request, policy)
        } else {
            server.approveDiscovered(request)
        } ?: return state
        return publish(state.copy(pending = null, approved = session))
    }

    fun deny(state: CineVaultSharingState): CineVaultSharingState {
        state.pending?.let(server::deny)
        return publish(state.copy(pending = null))
    }

    fun stopSharing(): CineVaultSharingState {
        advertiser.stop()
        server.stop()
        mediaSnapshot = emptyList()
        return publish(CineVaultSharingState())
    }

    fun qrPayload(state: CineVaultSharingState): String? {
        val invite = state.invite ?: return null
        val endpoint = state.endpoint ?: return null
        fun enc(v: String) = URLEncoder.encode(v, Charsets.UTF_8.name()).replace("+", "%20")
        return "cinevault://nearby?id=${enc(deviceId)}&name=${enc(deviceName)}&endpoint=${enc(endpoint)}" +
            "&nonce=${enc(invite.nonce)}&expires=${invite.expiresAtEpochMs}"
    }

    fun selectionForFolders(folderIds: Set<String>): ShareLibrarySelection =
        shareSelectionForFolders(folderIds)

    suspend fun availableFolders(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        loadLibraryCache(app)?.videos.orEmpty()
            .filterNot(::isRestrictedFolderItem)
            .mapNotNull { item ->
                item.video.folderPath.takeIf(String::isNotBlank)?.let { it to File(it).name.ifBlank { it } }
            }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
    }

    private suspend fun loadShareableMedia(): List<CineVaultDirectMedia> {
        return loadLibraryCache(app)?.videos.orEmpty().mapNotNull { item ->
            val file = File(item.video.path)
            if (!file.isFile) return@mapNotNull null
            CineVaultDirectMedia(
                id = stableNetworkSourceId(NetworkType.CINEVAULT_GATEWAY, file.absolutePath),
                title = item.title.ifBlank { item.video.name },
                file = file,
                folderId = item.video.folderPath,
                isVaultOrSecret = isRestrictedFolderItem(item),
            )
        }
    }

    private fun localIpv4Address(): String? =
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .asSequence()
            .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
            .flatMap { it.inetAddresses.toList().asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
            ?.hostAddress

    companion object {
        @Volatile private var instance: CineVaultNearbyRuntime? = null
        fun get(context: Context): CineVaultNearbyRuntime =
            instance ?: synchronized(this) {
                instance ?: CineVaultNearbyRuntime(context).also { instance = it }
            }
    }
}

internal fun shareSelectionForFolders(folderIds: Set<String>): ShareLibrarySelection =
    ShareLibrarySelection(SharedLibraryScope.SELECTED_FOLDERS, folderIds)

fun createNearbyPairRequest(
    localDeviceId: String,
    localDeviceName: String,
    qr: CineVaultQrPayload?,
): NearbyPairingRequest? {
    val nonce = qr?.nonce ?: ByteArray(18).also(SecureRandom()::nextBytes)
        .joinToString("") { "%02x".format(it) }
    return NearbyPairingRequest(localDeviceId, localDeviceName, nonce)
}
