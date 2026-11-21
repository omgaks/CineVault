package com.sole.cinevault.network

import androidx.activity.compose.BackHandler
import com.sole.cinevault.glasses.display.HostWindowOnly
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.CineType
import com.sole.cinevault.ui.theme.NewsreaderFamily
import com.sole.cinevault.ui.theme.SpaceBlack
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextFaint
import com.sole.cinevault.ui.theme.TextMuted

private val ConnectCyan = Color(0xFF39D9FF)
private val ConnectViolet = Color(0xFF9C7CFF)
private val ConnectGreen = Color(0xFF5BE39D)

@Composable
fun NetworkHubScreen(
    savedSources: List<NetworkHubSourceSummary> = emptyList(),
    discoveredDevices: List<DiscoveredNetworkDevice> = emptyList(),
    connectState: CineVaultConnectState = CineVaultConnectState(),
    onBack: () -> Unit,
    onFindDevices: () -> Unit,
    onDisconnectPeer: () -> Unit = {},
    onReconnect: () -> Unit = {},
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
    val runtime = remember(context, scope) { NetworkHubRuntime(context, scope) { runtimeState = it } }
    DisposableEffect(runtime) { onDispose { runtime.stop() } }
    val visibleDevices = remember(discoveredDevices, runtimeState.discoveredDevices) {
        sanitizeDiscoveredDevices(discoveredDevices + runtimeState.discoveredDevices)
    }
    var addKind by remember { mutableStateOf<AddSourceKind?>(null) }
    val remembered = remember(connectState.isConnected) { CineVaultReconnectStore(context).load() }
    BackHandler(onBack = onBack)

    BoxWithConstraints(Modifier.fillMaxSize().background(SpaceBlack)) {
        val wide = maxWidth >= 720.dp
        val horizontalPadding = when { maxWidth >= 1200.dp -> 64.dp; wide -> 40.dp; else -> 20.dp }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = horizontalPadding, vertical = 20.dp)) {
            NetworkHubHeader(onBack)
            Spacer(Modifier.height(8.dp))
            NetworkRadarHero(connectState, visibleDevices)
            Spacer(Modifier.height(8.dp))
            CineVaultConnectCard(connectState, remembered, onFindDevices, onScanQr, onShareLibrary, onDisconnectPeer, onReconnect, onOpenConnected = { connectState.remoteConnection?.device?.let(onDiscoveredDeviceClick) })
            Spacer(Modifier.height(18.dp))
            if (wide) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) { SavedSources(savedSources, onSourceClick) }
                    Column(Modifier.weight(1f)) {
                        NearbyDevices(visibleDevices, connectState, remembered, onDiscoveredDeviceClick)
                        Spacer(Modifier.height(18.dp))
                        AddManually({ addKind = AddSourceKind.FILE_SHARE; onAddFileShare() }, { addKind = AddSourceKind.MEDIA_SERVER; onAddMediaServer() }, { addKind = AddSourceKind.WEB; onAddWebSource() })
                    }
                }
            } else {
                SavedSources(savedSources, onSourceClick)
                Spacer(Modifier.height(18.dp))
                NearbyDevices(visibleDevices, connectState, remembered, onDiscoveredDeviceClick)
                Spacer(Modifier.height(18.dp))
                AddManually({ addKind = AddSourceKind.FILE_SHARE; onAddFileShare() }, { addKind = AddSourceKind.MEDIA_SERVER; onAddMediaServer() }, { addKind = AddSourceKind.WEB; onAddWebSource() })
            }
            Spacer(Modifier.height(32.dp))
        }
    }
    addKind?.let { kind -> AddSourceInfoDialog(kind) { addKind = null } }
}

data class NetworkHubSourceSummary(val id: String, val name: String, val typeLabel: String, val statusLabel: String = "Saved")
private enum class AddSourceKind { FILE_SHARE, MEDIA_SERVER, WEB }

@Composable
private fun CineVaultConnectCard(state: CineVaultConnectState, remembered: RememberedCineVaultPeer?, onFindDevices: () -> Unit, onScanQr: () -> Unit, onShareLibrary: () -> Unit, onDisconnect: () -> Unit, onReconnect: () -> Unit, onOpenConnected: () -> Unit) {
    val infinite = rememberInfiniteTransition(label = "connectPulse")
    val pulse by infinite.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
    val accent = when { state.isConnected -> ConnectGreen; state.requiresLocalApproval -> AmberCore; state.isBusy -> ConnectCyan; state.isProviding -> ConnectViolet; else -> AmberCore }
    Column(Modifier.fillMaxWidth().background(Color(0xFF12151B), RoundedCornerShape(26.dp)).border(1.5.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(26.dp)).padding(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(accent.copy(alpha = 0.13f), CircleShape).border(1.dp, accent.copy(alpha = if (state.isBusy) pulse else 0.55f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(when { state.isConnected -> Icons.Rounded.Link; state.isBusy -> Icons.Rounded.Radar; state.isProviding -> Icons.Rounded.WifiTethering; else -> Icons.Rounded.Hub }, null, tint = accent, modifier = Modifier.size(25.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("CineVault Connect", color = TextBright, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(state.statusLabel, color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            RoleBadge(state.role, accent)
        }
        val waitingFor = state.connectingPeer?.takeIf { !state.isConnected && state.phase == CineVaultConnectPhase.REQUESTING_APPROVAL }
        if (waitingFor != null) {
            ApprovalPanel(waitingFor.displayName, state.connectingCode, onCancel = onDisconnect)
            return@Column
        }
        Spacer(Modifier.height(16.dp))
        Text(when {
            state.remoteConnection != null -> "Streaming access to ${state.remoteConnection.device.displayName} is active." + (state.remoteVideoCount?.let { " $it ${if (it == 1) "video is" else "videos are"} ready to play." } ?: "")
            state.requiresLocalApproval -> "${state.sharing.pending?.remoteDeviceName ?: "A CineVault"} wants access to your shared library."
            state.isProviding -> "Your CineVault is visible nearby. You can keep sharing while connecting to another CineVault."
            state.isBusy -> "Looking for CineVault devices on your local network."
            else -> "Share your library or discover another CineVault from one place."
        }, color = TextMuted, fontSize = 13.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            ConnectAction(if (state.phase == CineVaultConnectPhase.DISCOVERING) "Finding…" else "Find CineVaults", Icons.Rounded.Radar, ConnectCyan, onFindDevices, state.phase != CineVaultConnectPhase.DISCOVERING)
            ConnectAction("Scan QR", Icons.Rounded.QrCodeScanner, ConnectViolet, onScanQr)
            ConnectAction(if (state.isProviding) "Sharing On" else "Share Library", Icons.Rounded.FolderShared, AmberCore, onShareLibrary)
            if (state.isConnected) ConnectAction("Open library", Icons.Rounded.VideoLibrary, ConnectGreen, onOpenConnected)
            if (state.isConnected) ConnectAction("Disconnect", Icons.Rounded.LinkOff, ConnectGreen, onDisconnect)
            if (!state.isConnected && !state.isBusy && remembered != null) ConnectAction("Reconnect to ${remembered.displayName}", Icons.Rounded.Replay, ConnectCyan, onReconnect)
        }
    }
}

private const val APPROVAL_TIMEOUT_SECONDS = 90

/** Shown inside the Connect card while the other device decides: code to compare, countdown, Cancel. */
@Composable
private fun ApprovalPanel(deviceName: String, code: String?, onCancel: () -> Unit) {
    var elapsed by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); elapsed++ } }
    val left = (APPROVAL_TIMEOUT_SECONDS - elapsed).coerceAtLeast(0)
    Spacer(Modifier.height(16.dp))
    Text("Waiting for $deviceName to approve", color = TextBright, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Text("On $deviceName, tap Approve. Check that this code matches the one it shows.", color = TextMuted, fontSize = 13.sp, lineHeight = 18.sp)
    if (code != null) {
        Spacer(Modifier.height(14.dp))
        Text(formatMatchCode(code), color = AmberCore, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp, modifier = Modifier.background(AmberCore.copy(alpha = 0.10f), RoundedCornerShape(16.dp)).border(1.dp, AmberCore.copy(alpha = 0.4f), RoundedCornerShape(16.dp)).padding(horizontal = 18.dp, vertical = 8.dp))
    }
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ConnectAction("Cancel", Icons.Rounded.Close, ConnectCyan, onCancel)
        Text("Times out in ${left / 60}:${(left % 60).toString().padStart(2, '0')}", color = TextFaint, fontSize = 12.sp)
    }
}

@Composable private fun RoleBadge(role: CineVaultConnectRole, accent: Color) {
    val text = when (role) { CineVaultConnectRole.PROVIDER -> "SHARE"; CineVaultConnectRole.RECEIVER -> "RECEIVE"; CineVaultConnectRole.BOTH -> "BOTH"; CineVaultConnectRole.IDLE -> "READY" }
    Text(text, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.background(accent.copy(alpha = 0.10f), RoundedCornerShape(50)).border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 5.dp))
}

@Composable private fun ConnectAction(text: String, icon: ImageVector, accent: Color, onClick: () -> Unit, enabled: Boolean = true) {
    Row(Modifier.alpha(if (enabled) 1f else 0.55f).background(accent.copy(alpha = 0.09f), RoundedCornerShape(50)).border(1.dp, accent.copy(alpha = 0.38f), RoundedCornerShape(50)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(7.dp)); Text(text, color = TextBright, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable private fun NearbyDevices(devices: List<DiscoveredNetworkDevice>, connectState: CineVaultConnectState, remembered: RememberedCineVaultPeer?, onClick: (DiscoveredNetworkDevice) -> Unit) = HubSection("Nearby") {
    if (devices.isEmpty()) EmptyNearby(remembered)
    else sanitizeDiscoveredDevices(devices).forEach { device ->
        val isConnecting = connectState.connectingPeer?.id == device.id
        val isConnected = connectState.remoteConnection?.device?.id == device.id
        val accent = when { isConnected -> ConnectGreen; isConnecting -> ConnectCyan; device.kind == NetworkDiscoveryKind.CINEVAULT -> ConnectViolet; else -> AmberCore }
        HubRow(
            icon = when (device.kind) { NetworkDiscoveryKind.CINEVAULT -> Icons.Rounded.Devices; NetworkDiscoveryKind.DLNA -> Icons.Rounded.LiveTv; else -> Icons.Rounded.Wifi },
            title = device.displayName,
            subtitle = when { isConnected -> "Connected • Tap to open library"; isConnecting -> "Waiting for approval…"; else -> discoveryLabel(device.kind) },
            accent = accent,
            enabled = !isConnecting,
        ) { onClick(device) }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable private fun AddSourceInfoDialog(kind: AddSourceKind, onDismiss: () -> Unit) {
    val (title, body) = when (kind) {
        AddSourceKind.FILE_SHARE -> "File Share" to "SMB, WebDAV and SFTP are available. CineVault keeps credentials separate from saved source details."
        AddSourceKind.MEDIA_SERVER -> "Media Server" to "Jellyfin and Emby can be signed in directly. DLNA devices are discovered automatically."
        AddSourceKind.WEB -> "Web / Playlist" to "HTTP Directory and M3U sources are available. CineVault never auto-connects to an unknown address."
    }
    HostWindowOnly { AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = onDismiss) { Text("OK", color = AmberCore) } }, title = { Text(title, color = TextBright) }, text = { Text(body, color = TextMuted) }, containerColor = Color(0xFF15161A)) }
}

@Composable private fun NetworkHubHeader(onBack: () -> Unit) { Row(verticalAlignment = Alignment.CenterVertically) { HubIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack); Spacer(Modifier.width(14.dp)); Column { Text("Network", color = TextBright, fontFamily = NewsreaderFamily, fontSize = CineType.Display); Text("Connect • stream • share", color = TextMuted, fontSize = 13.sp) } } }
@Composable private fun SavedSources(sources: List<NetworkHubSourceSummary>, onClick: (String) -> Unit) = HubSection("Your Sources") { if (sources.isEmpty()) EmptyHint("No saved sources yet. Add a file share, media server or web link under Add Manually and it will wait here.") else sources.forEach { source -> HubRow(Icons.Rounded.Storage, source.name, "${source.typeLabel} • ${source.statusLabel}") { onClick(source.id) }; Spacer(Modifier.height(8.dp)) } }
@Composable private fun AddManually(onFileShare: () -> Unit, onMediaServer: () -> Unit, onWebSource: () -> Unit) = HubSection("Add Manually") { HubRow(Icons.Rounded.Storage, "File Shares", "SMB • WebDAV • SFTP", onClick = onFileShare); Spacer(Modifier.height(8.dp)); HubRow(Icons.Rounded.Devices, "Media Servers", "Jellyfin • Emby • DLNA", accent = ConnectViolet, onClick = onMediaServer); Spacer(Modifier.height(8.dp)); HubRow(Icons.Rounded.AddLink, "Web / Playlist", "HTTP directory • M3U", accent = ConnectCyan, onClick = onWebSource) }
@Composable private fun HubSection(title: String, content: @Composable () -> Unit) { Column(Modifier.fillMaxWidth().background(Color(0xFF121317), RoundedCornerShape(22.dp)).border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(22.dp)).padding(16.dp)) { Text(title, color = TextBright, fontSize = 16.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(12.dp)); content() } }
@Composable private fun HubRow(icon: ImageVector, title: String, subtitle: String, accent: Color = AmberCore, enabled: Boolean = true, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().hubEntrance().alpha(if (enabled) 1f else 0.72f).background(accent.copy(alpha = 0.055f), RoundedCornerShape(16.dp)).border(1.dp, accent.copy(alpha = 0.14f), RoundedCornerShape(16.dp)).clickable(enabled = enabled, onClick = onClick).padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(36.dp).background(accent.copy(alpha = 0.12f), RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp)) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(title, color = TextBright, fontSize = 14.sp, fontWeight = FontWeight.SemiBold); Text(subtitle, color = TextMuted, fontSize = 12.sp) }; Icon(Icons.Rounded.ChevronRight, null, tint = accent.copy(alpha = 0.72f)) } }
@Composable private fun HubIcon(icon: ImageVector, description: String, onClick: () -> Unit) { Box(Modifier.background(AmberCore.copy(alpha = 0.10f), RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(10.dp)) { Icon(icon, description, tint = AmberCore) } }
@Composable private fun EmptyNearby(remembered: RememberedCineVaultPeer?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.Radar, null, tint = ConnectCyan.copy(alpha = 0.7f), modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(8.dp))
        Text("No devices in range yet", color = TextBright, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text("Tap Find CineVaults, and on the other device open Network → Share Library. Both need the same Wi-Fi. No Wi-Fi in common? Use Scan QR.", color = TextMuted, fontSize = 12.sp, lineHeight = 17.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (remembered != null) {
            Spacer(Modifier.height(8.dp))
            Text("Last connected: ${remembered.displayName}", color = TextFaint, fontSize = 12.sp)
        }
    }
}
@Composable private fun EmptyHint(text: String) = Text(text, color = TextFaint, fontSize = 12.sp, lineHeight = 17.sp)
private fun discoveryLabel(kind: NetworkDiscoveryKind): String = when (kind) { NetworkDiscoveryKind.CINEVAULT -> "CineVault • Nearby"; NetworkDiscoveryKind.JELLYFIN -> "Jellyfin"; NetworkDiscoveryKind.EMBY -> "Emby"; NetworkDiscoveryKind.DLNA -> "DLNA / UPnP • Tap to browse"; NetworkDiscoveryKind.WEBDAV -> "WebDAV"; NetworkDiscoveryKind.SMB -> "SMB"; NetworkDiscoveryKind.UNKNOWN -> "Network device" }

/** Rows glide in and fade up the first time they appear, so the hub feels alive. */
@Composable
private fun Modifier.hubEntrance(): Modifier {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(if (shown) 1f else 0f, tween(520, easing = FastOutSlowInEasing), label = "hubEntrance")
    return this.graphicsLayer { alpha = progress; translationY = (1f - progress) * 28f }
}
