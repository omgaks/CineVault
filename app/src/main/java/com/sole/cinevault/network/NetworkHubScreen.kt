package com.sole.cinevault.network

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.SpaceBlack
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextFaint
import com.sole.cinevault.ui.theme.TextMuted

@Composable
fun NetworkHubScreen(
    savedSources: List<NetworkHubSourceSummary> = emptyList(),
    discoveredDevices: List<DiscoveredNetworkDevice> = emptyList(),
    onBack: () -> Unit,
    onFindDevices: () -> Unit,
    onScanQr: () -> Unit,
    onAddFileShare: () -> Unit,
    onAddMediaServer: () -> Unit,
    onAddWebSource: () -> Unit,
    onShareLibrary: () -> Unit,
    onSourceClick: (String) -> Unit = {},
    onDiscoveredDeviceClick: (DiscoveredNetworkDevice) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var runtimeState by remember { mutableStateOf(NetworkHubState()) }
    val runtime = remember(context, scope) {
        NetworkHubRuntime(context, scope) { runtimeState = it }
    }
    DisposableEffect(runtime) { onDispose { runtime.stop() } }

    val visibleDevices = remember(discoveredDevices, runtimeState.discoveredDevices) {
        sanitizeDiscoveredDevices(discoveredDevices + runtimeState.discoveredDevices)
    }
    var addKind by remember { mutableStateOf<AddSourceKind?>(null) }

    BackHandler(onBack = onBack)

    BoxWithConstraints(Modifier.fillMaxSize().background(SpaceBlack)) {
        val wide = maxWidth >= 720.dp
        val horizontalPadding = when {
            maxWidth >= 1200.dp -> 64.dp
            wide -> 40.dp
            else -> 20.dp
        }

        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding, vertical = 20.dp),
        ) {
            NetworkHubHeader(onBack)
            Spacer(Modifier.height(22.dp))

            if (wide) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(Modifier.weight(1f)) {
                        QuickActions(
                            runtimeState.isDiscovering,
                            onFindDevices = { runtime.findDevices(); onFindDevices() },
                            onScanQr = onScanQr,
                            onShareLibrary = onShareLibrary,
                        )
                        Spacer(Modifier.height(18.dp))
                        SavedSources(savedSources, onSourceClick)
                    }
                    Column(Modifier.weight(1f)) {
                        NearbyDevices(visibleDevices, onDiscoveredDeviceClick)
                        Spacer(Modifier.height(18.dp))
                        AddManually(
                            onFileShare = { addKind = AddSourceKind.FILE_SHARE; onAddFileShare() },
                            onMediaServer = { addKind = AddSourceKind.MEDIA_SERVER; onAddMediaServer() },
                            onWebSource = { addKind = AddSourceKind.WEB; onAddWebSource() },
                        )
                    }
                }
            } else {
                QuickActions(
                    runtimeState.isDiscovering,
                    { runtime.findDevices(); onFindDevices() },
                    onScanQr,
                    onShareLibrary,
                )
                Spacer(Modifier.height(18.dp))
                SavedSources(savedSources, onSourceClick)
                Spacer(Modifier.height(18.dp))
                NearbyDevices(visibleDevices, onDiscoveredDeviceClick)
                Spacer(Modifier.height(18.dp))
                AddManually(
                    { addKind = AddSourceKind.FILE_SHARE; onAddFileShare() },
                    { addKind = AddSourceKind.MEDIA_SERVER; onAddMediaServer() },
                    { addKind = AddSourceKind.WEB; onAddWebSource() },
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    addKind?.let { kind ->
        AddSourceInfoDialog(kind = kind, onDismiss = { addKind = null })
    }
}

data class NetworkHubSourceSummary(
    val id: String,
    val name: String,
    val typeLabel: String,
    val statusLabel: String = "Saved",
)

private enum class AddSourceKind { FILE_SHARE, MEDIA_SERVER, WEB }

@Composable
private fun AddSourceInfoDialog(kind: AddSourceKind, onDismiss: () -> Unit) {
    val title: String
    val body: String
    when (kind) {
        AddSourceKind.FILE_SHARE -> {
            title = "File Share"
            body = "SMB, WebDAV and SFTP are available. CineVault keeps credentials separate from saved source details."
        }
        AddSourceKind.MEDIA_SERVER -> {
            title = "Media Server"
            body = "Jellyfin and Emby can be signed in directly. DLNA devices are discovered automatically with Find Devices."
        }
        AddSourceKind.WEB -> {
            title = "Web / Playlist"
            body = "HTTP Directory and M3U sources are available. CineVault never auto-connects to an unknown address."
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK", color = AmberCore) } },
        title = { Text(title, color = TextBright) },
        text = { Text(body, color = TextMuted) },
        containerColor = Color(0xFF15161A),
    )
}

@Composable
private fun NetworkHubHeader(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HubIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
        Spacer(Modifier.width(14.dp))
        Column {
            Text("Network", color = TextBright, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("Your sources, nearby devices and private sharing.", color = TextMuted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun QuickActions(
    discovering: Boolean,
    onFindDevices: () -> Unit,
    onScanQr: () -> Unit,
    onShareLibrary: () -> Unit,
) = HubSection("Connect") {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HubAction(
            if (discovering) "Finding…" else "Find Devices",
            Icons.Rounded.Search,
            onFindDevices,
            !discovering,
        )
        HubAction("Scan QR", Icons.Rounded.QrCodeScanner, onScanQr)
        HubAction("Share My Library", Icons.Rounded.FolderShared, onShareLibrary)
    }
}

@Composable
private fun SavedSources(
    sources: List<NetworkHubSourceSummary>,
    onClick: (String) -> Unit,
) = HubSection("Your Sources") {
    if (sources.isEmpty()) {
        EmptyHint("No network sources added yet.")
    } else {
        sources.forEach { source ->
            HubRow(
                Icons.Rounded.Storage,
                source.name,
                "${source.typeLabel} • ${source.statusLabel}",
            ) { onClick(source.id) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun NearbyDevices(
    devices: List<DiscoveredNetworkDevice>,
    onClick: (DiscoveredNetworkDevice) -> Unit,
) = HubSection("Nearby") {
    if (devices.isEmpty()) {
        EmptyHint("Nothing discovered yet. Find Devices only looks; it never connects automatically.")
    } else {
        sanitizeDiscoveredDevices(devices).forEach { device ->
            HubRow(
                icon = when (device.kind) {
                    NetworkDiscoveryKind.CINEVAULT -> Icons.Rounded.Devices
                    NetworkDiscoveryKind.DLNA -> Icons.Rounded.LiveTv
                    else -> Icons.Rounded.Wifi
                },
                title = device.displayName,
                subtitle = discoveryLabel(device.kind),
            ) { onClick(device) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AddManually(
    onFileShare: () -> Unit,
    onMediaServer: () -> Unit,
    onWebSource: () -> Unit,
) = HubSection("Add Manually") {
    HubRow(Icons.Rounded.Storage, "File Shares", "SMB • WebDAV • SFTP", onFileShare)
    Spacer(Modifier.height(8.dp))
    HubRow(Icons.Rounded.Devices, "Media Servers", "Jellyfin • Emby • DLNA", onMediaServer)
    Spacer(Modifier.height(8.dp))
    HubRow(Icons.Rounded.Add, "Web / Playlist", "HTTP directory • M3U", onWebSource)
}

@Composable
private fun HubSection(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(Color(0xFF121317), RoundedCornerShape(22.dp))
            .border(1.dp, AmberCore.copy(alpha = 0.18f), RoundedCornerShape(22.dp))
            .padding(16.dp),
    ) {
        Text(title, color = TextBright, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun HubAction(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        Modifier.background(AmberCore.copy(alpha = 0.10f), RoundedCornerShape(50))
            .border(1.dp, AmberCore.copy(alpha = 0.45f), RoundedCornerShape(50))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = AmberCore)
        Spacer(Modifier.width(8.dp))
        Text(text, color = TextBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HubRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .background(Color.White.copy(alpha = 0.035f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = AmberCore)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextBright, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextMuted, fontSize = 12.sp)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = AmberCore.copy(alpha = 0.72f))
    }
}

@Composable
private fun HubIcon(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier.background(AmberCore.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Icon(icon, description, tint = AmberCore)
    }
}

@Composable
private fun EmptyHint(text: String) =
    Text(text, color = TextFaint, fontSize = 12.sp, lineHeight = 17.sp)

private fun discoveryLabel(kind: NetworkDiscoveryKind): String = when (kind) {
    NetworkDiscoveryKind.CINEVAULT -> "CineVault • Nearby"
    NetworkDiscoveryKind.JELLYFIN -> "Jellyfin"
    NetworkDiscoveryKind.EMBY -> "Emby"
    NetworkDiscoveryKind.DLNA -> "DLNA / UPnP • Tap to browse"
    NetworkDiscoveryKind.WEBDAV -> "WebDAV"
    NetworkDiscoveryKind.SMB -> "SMB"
    NetworkDiscoveryKind.UNKNOWN -> "Network device"
}
