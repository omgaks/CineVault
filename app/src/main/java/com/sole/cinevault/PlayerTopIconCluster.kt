package com.sole.cinevault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.network.CineVaultNearbyRuntime
import com.sole.cinevault.ui.theme.*

@Composable
internal fun TopIconCluster(
    isLandscape: Boolean,
    iconSize: Dp,
    playbackSpeed: Float,
    sleepTimerActive: Boolean,
    showSpeedMenu: Boolean,
    showSleepMenu: Boolean,
    onSpeedClick: () -> Unit,
    onSleepClick: () -> Unit,
    onPipClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val nearby = remember(context) { CineVaultNearbyRuntime.get(context) }
    val connectState by nearby.connectState.collectAsState()
    var showConnect by remember { mutableStateOf(false) }

    val content: @Composable () -> Unit = {
        ConnectPill(
            connected = connectState.isConnected,
            busy = connectState.isBusy,
            sharing = connectState.isProviding,
            onClick = { showConnect = true },
        )
        AmberPillIcon(
            icon = Icons.Rounded.Tv,
            contentDescription = "Picture in picture",
            onClick = onPipClick,
        )
        AmberPillIcon(
            icon = Icons.Rounded.Timer,
            contentDescription = "Sleep timer",
            activeDot = sleepTimerActive || showSleepMenu,
            onClick = onSleepClick,
        )
        LabeledGlowIcon(
            icon = Icons.Rounded.Speed,
            label = "Speed",
            size = iconSize,
            tint = if (playbackSpeed != 1f || showSpeedMenu) AmberCore else TextBright,
            active = playbackSpeed != 1f || showSpeedMenu,
            onClick = onSpeedClick,
        )
    }

    if (isLandscape) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
    } else {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End,
        ) { content() }
    }

    if (showConnect) {
        PlayerConnectQuickSheet(
            runtime = nearby,
            state = connectState,
            onDismiss = { showConnect = false },
        )
    }
}

@Composable
private fun ConnectPill(
    connected: Boolean,
    busy: Boolean,
    sharing: Boolean,
    onClick: () -> Unit,
) {
    val accent = when {
        connected -> Color(0xFF5BE39D)
        busy -> Color(0xFF39D9FF)
        sharing -> Color(0xFF9C7CFF)
        else -> AmberCore
    }
    val label = when {
        connected -> "CONNECTED"
        busy -> "CONNECTING"
        sharing -> "SHARING"
        else -> "CONNECT"
    }
    Row(
        Modifier
            .height(46.dp)
            .clip(RoundedCornerShape(50))
            .background(GlassSurface)
            .background(
                Brush.horizontalGradient(
                    listOf(accent.copy(alpha = 0.22f), Color.Transparent)
                )
            )
            .border(1.2.dp, accent.copy(alpha = 0.70f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.CastConnected,
            contentDescription = "Connect",
            tint = accent,
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            label,
            color = TextBright,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun AmberPillIcon(
    icon: ImageVector,
    contentDescription: String,
    activeDot: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(46.dp)
            .clip(RoundedCornerShape(50))
            .background(AmberCore)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = Color.Black, modifier = Modifier.size(22.dp))
        if (activeDot) {
            Box(
                Modifier.align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp)
                    .size(7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFE53935))
            )
        }
    }
}

@Composable
private fun LabeledGlowIcon(
    icon: ImageVector,
    label: String,
    size: Dp,
    tint: Color = TextBright,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(size)
                .clip(RoundedCornerShape(20.dp))
                .background(GlassSurface)
                .background(
                    Brush.radialGradient(
                        listOf(
                            AmberGlow.copy(alpha = if (active) 0.38f else 0.20f),
                            Color.Transparent,
                        )
                    )
                )
                .border(
                    1.2.dp,
                    Brush.verticalGradient(
                        listOf(
                            AmberGlow.copy(alpha = if (active) 0.90f else 0.55f),
                            AmberDeep.copy(alpha = if (active) 0.55f else 0.25f),
                        )
                    ),
                    RoundedCornerShape(20.dp),
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(size * 0.44f))
        }
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            color = if (active) AmberCore else TextMuted,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
