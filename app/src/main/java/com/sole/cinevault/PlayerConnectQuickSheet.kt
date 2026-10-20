package com.sole.cinevault

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
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.network.CineVaultConnectState
import com.sole.cinevault.network.CineVaultNearbyRuntime
import com.sole.cinevault.network.DiscoveredNetworkDevice
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextMuted

private val ConnectCyan = Color(0xFF39D9FF)
private val ConnectViolet = Color(0xFF9C7CFF)
private val ConnectGreen = Color(0xFF5BE39D)

/** Shared on/off for the Connect window: the pill toggles it, the close coordinator closes it. */
internal object PlayerConnectUi {
    var visible by mutableStateOf(false)
}

@Composable
internal fun BoxScope.PlayerConnectWindow(
    isLandscape: Boolean,
    railBottomInset: Dp,
    keepControlsVisible: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val runtime = remember(context) { CineVaultNearbyRuntime.get(context) }
    val state by runtime.connectState.collectAsState()
    val visible = PlayerConnectUi.visible
    LaunchedEffect(visible) {
        while (visible) {
            keepControlsVisible()
            kotlinx.coroutines.delay(1500)
        }
    }
    LumenWindow(
        visible = visible,
        accent = LumenSky,
        title = "Quick Connect",
        icon = Icons.Rounded.CastConnected,
        size = LumenSize.M,
        isLandscape = isLandscape,
        bottomInset = railBottomInset,
    ) {
        Text(state.statusLabel, color = statusAccent(state), fontSize = LumenType.caption, fontWeight = FontWeight.Bold)
        ConnectContent(runtime, state)
    }
}

@Composable
private fun ConnectContent(runtime: CineVaultNearbyRuntime, state: CineVaultConnectState) {
        Spacer(Modifier.height(16.dp))
        ProviderStrip(
            title = "CineVault",
            subtitle = "Nearby library • direct stream",
            icon = Icons.Rounded.Devices,
            accent = ConnectViolet,
            active = state.isConnected || state.isProviding,
        )
        ProviderStrip(
            title = "Cast",
            subtitle = "Chromecast / Google TV",
            icon = Icons.Rounded.Cast,
            accent = ConnectCyan,
            active = false,
            note = "Cast integration follows Connect 3",
        )
        ProviderStrip(
            title = "Media Servers",
            subtitle = "Jellyfin • Emby • DLNA",
            icon = Icons.Rounded.Dns,
            accent = AmberCore,
            active = false,
            note = "Available from Network",
        )

        Spacer(Modifier.height(12.dp))
        if (state.remoteConnection != null) {
            ConnectedPeerCard(
                state.remoteConnection.device,
                onDisconnect = runtime::disconnectPeer,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickAction(
                    label = if (state.isBusy) "Finding…" else "Find CineVaults",
                    icon = Icons.Rounded.Radar,
                    accent = ConnectCyan,
                    enabled = !state.isBusy,
                    onClick = runtime::findCineVaultPeers,
                )
            }

            if (state.discoveredPeers.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("NEARBY", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                state.discoveredPeers.forEach { peer ->
                    NearbyPeer(
                        peer = peer,
                        connecting = state.connectingPeer?.id == peer.id,
                        onClick = { runtime.connectToPeer(peer) },
                    )
                    Spacer(Modifier.height(7.dp))
                }
            } else {
                Spacer(Modifier.height(14.dp))
                Text(
                    "Tap Find CineVaults. Devices sharing on the same local network will appear here.",
                    color = TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }

        if (state.isProviding) {
            Spacer(Modifier.height(14.dp))
            Text(
                "Your library is also visible nearby — sharing stays active while you connect.",
                color = ConnectViolet,
                fontSize = 11.sp,
            )
        }
}

@Composable
private fun ProviderStrip(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    active: Boolean,
    note: String? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(accent.copy(alpha = if (active) 0.12f else 0.055f), LumenCardShape)
            .border(1.dp, accent.copy(alpha = if (active) 0.42f else 0.16f), LumenCardShape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextMuted, fontSize = 11.sp)
        }
        when {
            active -> Text("ACTIVE", color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            note != null -> Text(note, color = TextMuted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun NearbyPeer(
    peer: DiscoveredNetworkDevice,
    connecting: Boolean,
    onClick: () -> Unit,
) {
    val pulse by rememberInfiniteTransition(label = "peerPulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "peerAlpha",
    )
    Row(
        Modifier.fillMaxWidth()
            .background(ConnectViolet.copy(alpha = 0.07f), LumenCardShape)
            .border(
                1.dp,
                ConnectViolet.copy(alpha = if (connecting) pulse else 0.20f),
                LumenCardShape,
            )
            .clickable(enabled = !connecting, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.PhoneAndroid, null, tint = ConnectViolet)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(peer.displayName, color = TextBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (connecting) "Waiting for approval…" else "Tap to connect",
                color = if (connecting) ConnectCyan else TextMuted,
                fontSize = 11.sp,
            )
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = ConnectViolet)
    }
}

@Composable
private fun ConnectedPeerCard(
    peer: DiscoveredNetworkDevice,
    onDisconnect: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .background(ConnectGreen.copy(alpha = 0.09f), LumenCardShape)
            .border(1.dp, ConnectGreen.copy(alpha = 0.36f), LumenCardShape)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Link, null, tint = ConnectGreen)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(peer.displayName, color = TextBright, fontWeight = FontWeight.SemiBold)
                Text("CineVault connection active", color = ConnectGreen, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        QuickAction("Disconnect", Icons.Rounded.LinkOff, ConnectGreen, onClick = onDisconnect)
    }
}

@Composable
private fun QuickAction(
    label: String,
    icon: ImageVector,
    accent: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier.background(accent.copy(alpha = 0.10f), LumenCardShape)
            .border(1.dp, accent.copy(alpha = 0.40f), LumenCardShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, color = TextBright, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun statusAccent(state: CineVaultConnectState): Color = when {
    state.isConnected -> ConnectGreen
    state.isBusy -> ConnectCyan
    state.isProviding -> ConnectViolet
    else -> AmberCore
}
