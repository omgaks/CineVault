package com.sole.cinevault.network

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.FolderShared
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.SpaceBlack
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextFaint
import com.sole.cinevault.ui.theme.TextMuted

/**
 * D16 Network Hub shell. It is intentionally callback-driven: discovery,
 * QR, source persistence and Nearby permissions stay outside the UI.
 */
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
    onDiscoveredDeviceClick: (String) -> Unit = {},
) {
    BackHandler(onBack = onBack)

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(SpaceBlack)
    ) {
        val wide = maxWidth >= 720.dp
        val horizontalPadding = if (wide) 40.dp else 20.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding, vertical = 20.dp)
        ) {
            NetworkHubHeader(onBack)
            Spacer(Modifier.height(22.dp))

            if (wide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(Modifier.weight(1f)) {
                        QuickActions(onFindDevices, onScanQr, onShareLibrary)
                        Spacer(Modifier.height(18.dp))
                        SavedSources(savedSources, onSourceClick)
                    }
                    Column(Modifier.weight(1f)) {
                        NearbyDevices(discoveredDevices, onDiscoveredDeviceClick)
                        Spacer(Modifier.height(18.dp))
                        AddManually(onAddFileShare, onAddMediaServer, onAddWebSource)
                    }
                }
            } else {
                QuickActions(onFindDevices, onScanQr, onShareLibrary)
                Spacer(Modifier.height(18.dp))
                SavedSources(savedSources, onSourceClick)
                Spacer(Modifier.height(18.dp))
                NearbyDevices(discoveredDevices, onDiscoveredDeviceClick)
                Spacer(Modifier.height(18.dp))
                AddManually(onAddFileShare, onAddMediaServer, onAddWebSource)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

data class NetworkHubSourceSummary(
    val id: String,
    val name: String,
    val typeLabel: String,
    val statusLabel: String = "Saved",
)

@Composable
private fun NetworkHubHeader(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HubIcon(Icons.Rounded.ArrowBack, "Back", onBack)
        Spacer(Modifier.width(14.dp))
        Column {
            Text("Network", color = TextBright, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(
                "Your sources, nearby devices and private sharing.",
                color = TextMuted,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun QuickActions(
    onFindDevices: () -> Unit,
    onScanQr: () -> Unit,
    onShareLibrary: () -> Unit,
) = HubSection("Connect") {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HubAction("Find Devices", Icons.Rounded.Search, onFindDevices)
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
                icon = Icons.Rounded.Storage,
                title = source.name,
                subtitle = "${source.typeLabel} • ${source.statusLabel}",
                onClick = { onClick(source.id) },
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun NearbyDevices(
    devices: List<DiscoveredNetworkDevice>,
    onClick: (String) -> Unit,
) = HubSection("Nearby") {
    if (devices.isEmpty()) {
        EmptyHint("Nothing discovered yet. Find Devices only looks; it never connects automatically.")
    } else {
        sanitizeDiscoveredDevices(devices).forEach { device ->
            HubRow(
                icon = if (device.kind == NetworkDiscoveryKind.CINEVAULT)
                    Icons.Rounded.Devices else Icons.Rounded.Wifi,
                title = device.displayName,
                subtitle = discoveryLabel(device.kind),
                onClick = { onClick(device.id) },
            )
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
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF121317), RoundedCornerShape(22.dp))
            .border(1.dp, AmberCore.copy(alpha = 0.18f), RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Text(title, color = TextBright, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun HubAction(text: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .background(AmberCore.copy(alpha = 0.10f), RoundedCornerShape(50))
            .border(1.dp, AmberCore.copy(alpha = 0.45f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
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
        Modifier
            .fillMaxWidth()
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
    }
}

@Composable
private fun HubIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .background(AmberCore.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Icon(icon, description, tint = AmberCore)
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(text, color = TextFaint, fontSize = 12.sp, lineHeight = 17.sp)
}

private fun discoveryLabel(kind: NetworkDiscoveryKind): String = when (kind) {
    NetworkDiscoveryKind.CINEVAULT -> "CineVault • Nearby"
    NetworkDiscoveryKind.JELLYFIN -> "Jellyfin"
    NetworkDiscoveryKind.EMBY -> "Emby"
    NetworkDiscoveryKind.DLNA -> "DLNA / UPnP"
    NetworkDiscoveryKind.WEBDAV -> "WebDAV"
    NetworkDiscoveryKind.SMB -> "SMB"
    NetworkDiscoveryKind.UNKNOWN -> "Network device"
}
