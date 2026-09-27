package com.sole.cinevault.network

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextMuted

/**
 * App-facing Network Hub.
 *
 * S10B makes discovery endpoint-aware:
 * - DLNA discovery opens a real DlnaNetworkSource.
 * - QR invokes the platform Google Code Scanner and accepts only CineVault
 *   Nearby payloads.
 * - CineVault peers are carried forward with their real LAN endpoint rather
 *   than losing everything except the opaque id.
 *
 * Pair/request transport is deliberately delegated through onCineVaultPeer;
 * S10C wires that callback to the approval-gated Direct transport.
 */
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

    var refreshKey by remember { mutableIntStateOf(0) }
    val summaries = remember(refreshKey) { integration.summaries() }

    var showSetup by remember { mutableStateOf(false) }
    var setupType by remember { mutableStateOf(NetworkType.WEBDAV) }
    var setupAddress by remember { mutableStateOf("") }
    var showMediaServerSetup by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun handleDevice(device: DiscoveredNetworkDevice, qr: CineVaultQrPayload? = null) {
        when (val action = discoveredDeviceAction(device)) {
            is DiscoveredDeviceAction.OpenDlna -> onOpenSource(action.source)

            is DiscoveredDeviceAction.PrefillManual -> {
                when (action.type) {
                    NetworkType.JELLYFIN, NetworkType.EMBY -> {
                        message = "This server was discovered. Open Media Servers to sign in securely."
                        showMediaServerSetup = true
                    }
                    else -> {
                        setupType = action.type
                        setupAddress = action.address
                        showSetup = true
                    }
                }
            }

            is DiscoveredDeviceAction.CineVaultPeer ->
                onCineVaultPeer(action.device, qr)

            is DiscoveredDeviceAction.Unsupported ->
                message = action.reason
        }
    }

    NetworkHubScreen(
        savedSources = summaries,
        onBack = onBack,
        onFindDevices = {},
        onScanQr = {
            qrScanner.scan(
                onResult = { raw ->
                    val payload = parseCineVaultQrPayload(raw)
                    if (payload == null) {
                        message = "That QR code is not a valid CineVault Nearby invite."
                    } else if (payload.expiresAtEpochMs <= System.currentTimeMillis()) {
                        message = "That CineVault invite has expired. Ask the other device to create a new one."
                    } else {
                        handleDevice(payload.asDiscoveredDevice(), payload)
                    }
                },
                onError = { message = it },
            )
        },
        onAddFileShare = {
            setupType = NetworkType.WEBDAV
            setupAddress = ""
            showSetup = true
        },
        onAddMediaServer = {
            showMediaServerSetup = true
        },
        onAddWebSource = {
            setupType = NetworkType.HTTP_DIRECTORY
            setupAddress = ""
            showSetup = true
        },
        onShareLibrary = onShareLibrary,
        onSourceClick = { id -> integration.resolve(id)?.let(onOpenSource) },
        onDiscoveredDeviceClick = { handleDevice(it) },
    )

    if (showSetup) {
        EffortlessSourceSheet(
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
    }

    if (showMediaServerSetup) {
        MediaServerSetupSheet(
            onDismiss = { showMediaServerSetup = false },
            onConnected = { source, session ->
                integration.saveMediaServer(source, session)
                refreshKey++
                showMediaServerSetup = false
                integration.resolve(source.id)?.let(onOpenSource)
            },
        )
    }

    message?.let { body ->
        AlertDialog(
            onDismissRequest = { message = null },
            confirmButton = {
                TextButton(onClick = { message = null }) {
                    Text("OK", color = AmberCore)
                }
            },
            title = { Text("Network", color = TextBright) },
            text = { Text(body, color = TextMuted) },
            containerColor = Color(0xFF15161A),
        )
    }
}
