package com.sole.cinevault.network

import android.content.Context
import android.os.Build
import com.sole.cinevault.library.isRestrictedFolderItem
import com.sole.cinevault.library.loadLibraryCache
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
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
    private val discovery = CineVaultLanDiscoveryProvider(app)
    private val directClient = CineVaultDirectClient()
    private var selection = ShareLibrarySelection(SharedLibraryScope.ENTIRE_LIBRARY)
    private val server = CineVaultDirectServer(
        authorization = authorization,
        mediaProvider = { mediaSnapshot },
        selectionProvider = { selection },
    )
    private var mediaSnapshot: List<CineVaultDirectMedia> = emptyList()
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var discoveryJob: Job? = null
    private var pairingJob: Job? = null

    private val _connectState = MutableStateFlow(CineVaultConnectState())
    val connectState: StateFlow<CineVaultConnectState> = _connectState.asStateFlow()

    val deviceId: String by lazy {
        val prefs = app.getSharedPreferences("cinevault_nearby", Context.MODE_PRIVATE)
        prefs.getString("device_id", null)?.takeIf(::isSafeLanDeviceId)
            ?: ("cv_" + UUID.randomUUID().toString().replace("-", "")).also {
                prefs.edit().putString("device_id", it).apply()
            }
    }

    val deviceName: String
        get() = "CineVault • " + (Build.MODEL?.trim()?.takeIf(String::isNotBlank) ?: "Android")

    private fun update(block: (CineVaultConnectState) -> CineVaultConnectState) {
        _connectState.value = block(_connectState.value)
    }

    private fun publish(sharing: CineVaultSharingState): CineVaultSharingState {
        update { it.withSharing(sharing) }
        return sharing
    }

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
            publish(CineVaultSharingState(true, "http://$host:$port", invite))
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
        val session = if (request.inviteNonce == invite.nonce) server.approve(invite, request, policy)
        else server.approveDiscovered(request)
        return session?.let { publish(state.copy(pending = null, approved = it)) } ?: state
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

    fun findCineVaultPeers() {
        discoveryJob?.cancel()
        discoveryJob = engineScope.launch {
            update { it.copy(phase = CineVaultConnectPhase.DISCOVERING, message = null) }
            val peers = runCatching { discovery.discover() }
                .getOrElse {
                    update { state -> state.withError("Nearby discovery failed. ${it.message.orEmpty()}".trim()) }
                    return@launch
                }
                .filter { it.kind == NetworkDiscoveryKind.CINEVAULT && it.id != deviceId }
            update {
                it.copy(
                    phase = if (it.sharing.running) CineVaultConnectPhase.AVAILABLE else CineVaultConnectPhase.OFF,
                    discoveredPeers = sanitizeDiscoveredDevices(peers),
                    message = null,
                )
            }
        }
    }

    fun connectToPeer(
        device: DiscoveredNetworkDevice,
        qr: CineVaultQrPayload? = null,
        onConnected: (NetworkSource) -> Unit = {},
    ) {
        if (pairingJob?.isActive == true) return
        pairingJob = engineScope.launch {
            val endpoint = device.addressHint
            if (endpoint.isNullOrBlank()) {
                update { it.withError("This CineVault device did not provide a usable Nearby address.") }
                return@launch
            }
            update {
                it.copy(
                    enabled = true,
                    phase = CineVaultConnectPhase.REQUESTING_APPROVAL,
                    connectingPeer = device,
                    message = "Waiting for approval on ${device.displayName}…",
                )
            }
            try {
                val request = createNearbyPairRequest(deviceId, deviceName, qr)
                    ?: error("Could not create pairing request.")
                val initial = directClient.requestPairing(endpoint, request)
                if (initial.state == "error" || initial.state == "denied") {
                    error(initial.message ?: "Pairing request failed.")
                }
                var approved = initial.takeIf { it.state == "approved" }
                repeat(120) {
                    if (approved != null) return@repeat
                    delay(750)
                    val result = directClient.pollApproval(endpoint, request.remoteDeviceId, request.inviteNonce)
                    when (result.state) {
                        "approved" -> approved = result
                        "denied", "error" -> error(result.message ?: "The sharing device declined the request.")
                    }
                }
                val result = approved ?: error("Approval timed out. Try again when the other device is ready.")
                val token = result.sessionToken ?: error("The approved session did not contain an access token.")
                val expiry = result.expiresAtEpochMs ?: error("The approved session did not contain an expiry.")
                val session = NearbyPairingSession(device.id, token, expiry)
                val source = CineVaultDirectNetworkSource(device.displayName, endpoint, session)
                when (val test = source.testConnection()) {
                    NetworkConnectionResult.Connected -> Unit
                    is NetworkConnectionResult.Failed -> error(test.message)
                    else -> error("CineVault Nearby connection could not be verified.")
                }
                update {
                    it.copy(
                        enabled = true,
                        phase = CineVaultConnectPhase.CONNECTED,
                        connectingPeer = null,
                        remoteConnection = CineVaultRemoteConnection(device, endpoint, session),
                        message = null,
                    )
                }
                withContext(Dispatchers.Main.immediate) { onConnected(source) }
            } catch (t: Throwable) {
                update { it.withError(t.message ?: "Could not connect to this CineVault device.") }
            }
        }
    }

    fun clearConnectMessage() = update {
        val phase = when {
            it.remoteConnection != null -> CineVaultConnectPhase.CONNECTED
            it.sharing.pending != null -> CineVaultConnectPhase.APPROVAL_REQUIRED
            it.sharing.approved != null -> CineVaultConnectPhase.APPROVED
            it.sharing.running -> CineVaultConnectPhase.AVAILABLE
            else -> CineVaultConnectPhase.OFF
        }
        it.copy(phase = phase, message = null)
    }

    fun qrPayload(state: CineVaultSharingState): String? {
        val invite = state.invite ?: return null
        val endpoint = state.endpoint ?: return null
        fun enc(v: String) = URLEncoder.encode(v, Charsets.UTF_8.name()).replace("+", "%20")
        return "cinevault://nearby?id=${enc(deviceId)}&name=${enc(deviceName)}&endpoint=${enc(endpoint)}" +
            "&nonce=${enc(invite.nonce)}&expires=${invite.expiresAtEpochMs}"
    }

    fun selectionForFolders(folderIds: Set<String>) = shareSelectionForFolders(folderIds)

    suspend fun availableFolders(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        loadLibraryCache(app)?.videos.orEmpty().filterNot(::isRestrictedFolderItem).mapNotNull { item ->
            item.video.folderPath.takeIf(String::isNotBlank)?.let { it to File(it).name.ifBlank { it } }
        }.distinctBy { it.first }.sortedBy { it.second.lowercase() }
    }

    private suspend fun loadShareableMedia(): List<CineVaultDirectMedia> =
        loadLibraryCache(app)?.videos.orEmpty().mapNotNull { item ->
            val file = File(item.video.path)
            if (!file.isFile) null else CineVaultDirectMedia(
                stableNetworkSourceId(NetworkType.CINEVAULT_GATEWAY, file.absolutePath),
                item.title.ifBlank { item.video.name }, file, item.video.folderPath, isRestrictedFolderItem(item),
            )
        }

    private fun localIpv4Address(): String? =
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().asSequence()
            .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
            .flatMap { it.inetAddresses.toList().asSequence() }.filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }?.hostAddress

    companion object {
        @Volatile private var instance: CineVaultNearbyRuntime? = null
        fun get(context: Context): CineVaultNearbyRuntime =
            instance ?: synchronized(this) {
                instance ?: CineVaultNearbyRuntime(context).also { instance = it }
            }
    }
}

internal fun shareSelectionForFolders(folderIds: Set<String>) =
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
