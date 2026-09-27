package com.sole.cinevault.network

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

/**
 * App-facing Network Hub. It owns the cakewalk setup flow while the visual
 * NetworkHubScreen remains protocol-agnostic.
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
            // Jellyfin/Emby/DLNA retain their existing dedicated engines.
            // Discovery is preferred; we do not fake them through WebDAV.
        },
        onAddWebSource = {
            setupType = NetworkType.HTTP_DIRECTORY
            setupAddress = ""
            showSetup = true
        },
        onShareLibrary = onShareLibrary,
        onSourceClick = { id -> integration.resolve(id)?.let(onOpenSource) },
        onDiscoveredDeviceClick = { /* NetworkHubScreen runtime owns discovery;
                                       connection remains consent-driven. */ },
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
}
