package com.sole.cinevault.network

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextMuted

@Composable
fun NetworkHubIntegratedScreen(
    onBack: () -> Unit,
    onShareLibrary: () -> Unit,
    onOpenSource: (NetworkSource) -> Unit,
    onCineVaultPeer: (DiscoveredNetworkDevice, CineVaultQrPayload?) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val integration = remember(context) { NetworkHubIntegration(context) }
    val qrScanner = remember(context) { CineVaultQrScanner(context) }
    val nearby = remember(context) { CineVaultNearbyRuntime.get(context) }
    val connectState by nearby.connectState.collectAsState()

    var refreshKey by remember { mutableIntStateOf(0) }
    val summaries = remember(refreshKey) { integration.summaries() }
    var showSetup by remember { mutableStateOf(false) }
    var setupType by remember { mutableStateOf(NetworkType.WEBDAV) }
    var setupAddress by remember { mutableStateOf("") }
    var showMediaServerSetup by remember { mutableStateOf(false) }
    var localMessage by remember { mutableStateOf<String?>(null) }

    fun handleDevice(device: DiscoveredNetworkDevice, qr: CineVaultQrPayload? = null) {
        val active = connectState.remoteConnection
        if (active != null && active.device.id == device.id) {
            onOpenSource(CineVaultDirectNetworkSource(active.device.displayName, active.endpoint, active.session))
            return
        }

        when (val action = discoveredDeviceAction(device)) {
            is DiscoveredDeviceAction.OpenDlna -> onOpenSource(action.source)
            is DiscoveredDeviceAction.PrefillManual -> when (action.type) {
                NetworkType.JELLYFIN, NetworkType.EMBY -> {
                    localMessage = "This server was discovered. Sign in once to continue."
                    showMediaServerSetup = true
                }
                else -> {
                    setupType = action.type
                    setupAddress = action.address
                    showSetup = true
                }
            }
            is DiscoveredDeviceAction.CineVaultPeer -> {
                onCineVaultPeer(action.device, qr)
                nearby.connectToPeer(action.device, qr, onOpenSource)
            }
            is DiscoveredDeviceAction.Unsupported -> localMessage = action.reason
        }
    }

    NetworkHubScreen(
        savedSources = summaries,
        discoveredDevices = connectState.discoveredPeers,
        connectState = if (connectState.remoteConnection != null) {
            connectState.copy(connectingPeer = null)
        } else connectState,
        onBack = onBack,
        onFindDevices = nearby::findCineVaultPeers,
        onDisconnectPeer = nearby::disconnectPeer,
        onScanQr = {
            qrScanner.scan(
                onResult = { raw ->
                    val payload = parseCineVaultQrPayload(raw)
                    when {
                        payload == null -> localMessage = "That QR code is not a valid CineVault Nearby invite."
                        payload.expiresAtEpochMs <= System.currentTimeMillis() ->
                            localMessage = "That CineVault invite has expired. Ask the other device to create a new one."
                        else -> handleDevice(payload.asDiscoveredDevice(), payload)
                    }
                },
                onError = { localMessage = it },
            )
        },
        onAddFileShare = { setupType = NetworkType.WEBDAV; setupAddress = ""; showSetup = true },
        onAddMediaServer = { showMediaServerSetup = true },
        onAddWebSource = { setupType = NetworkType.HTTP_DIRECTORY; setupAddress = ""; showSetup = true },
        onShareLibrary = onShareLibrary,
        onSourceClick = { id -> integration.resolve(id)?.let(onOpenSource) },
        onDiscoveredDeviceClick = { handleDevice(it) },
    )

    if (showSetup) EffortlessSourceSheet(
        initialType = setupType,
        initialAddress = setupAddress,
        onDismiss = { showSetup = false },
        onSave = { source, credential ->
            integration.save(source, credential)
            refreshKey++
            showSetup = false
            integration.resolve(source.id)?.let(onOpenSource)
        },
    )

    if (showMediaServerSetup) MediaServerSetupSheet(
        onDismiss = { showMediaServerSetup = false },
        onConnected = { source, session ->
            integration.saveMediaServer(source, session)
            refreshKey++
            showMediaServerSetup = false
            integration.resolve(source.id)?.let(onOpenSource)
        },
    )

    val message = localMessage ?: connectState.message
    message?.let { body ->
        AlertDialog(
            onDismissRequest = {
                localMessage = null
                nearby.clearConnectMessage()
            },
            confirmButton = {
                TextButton(onClick = {
                    localMessage = null
                    nearby.clearConnectMessage()
                }) { Text("OK", color = AmberCore) }
            },
            title = {
                Text(
                    if (connectState.isBusy) connectState.statusLabel else "Network",
                    color = TextBright,
                )
            },
            text = { Text(body, color = TextMuted) },
            containerColor = Color(0xFF15161A),
        )
    }
}
