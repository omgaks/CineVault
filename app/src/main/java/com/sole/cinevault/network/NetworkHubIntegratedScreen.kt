package com.sole.cinevault.network

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

/**
 * App-facing Network Hub.
 *
 * S10A closes the manual media-server setup gap while keeping discovery
 * consent-driven. CineVault/DLNA discovered-device handoff and QR are closed
 * in the following transport/discovery slice because they require the
 * discovered endpoint itself, not only a source id.
 */
@Composable
fun NetworkHubIntegratedScreen(
    onBack: () -> Unit,
    onShareLibrary: () -> Unit,
    onOpenSource: (NetworkSource) -> Unit,
) {
    val context = LocalContext.current
    val integration = remember(context) { NetworkHubIntegration(context) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val summaries = remember(refreshKey) { integration.summaries() }

    var showSetup by remember { mutableStateOf(false) }
    var setupType by remember { mutableStateOf(NetworkType.WEBDAV) }
    var setupAddress by remember { mutableStateOf("") }
    var showMediaServerSetup by remember { mutableStateOf(false) }

    NetworkHubScreen(
        savedSources = summaries,
        onBack = onBack,
        onFindDevices = {},
        onScanQr = {},
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
        onDiscoveredDeviceClick = { /* S10B receives the endpoint-aware handoff. */ },
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
}
