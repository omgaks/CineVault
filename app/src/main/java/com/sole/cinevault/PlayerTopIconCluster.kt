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
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.PictureInPictureAlt
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
import com.sole.cinevault.picture.PictureEnhanceRegistry
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

    val speedActive = playbackSpeed != 1f || showSpeedMenu
    val content: @Composable () -> Unit = {
        // Right-hand pill column, top to bottom. All pills share the Connect
        // pill's capsule; each has its own neon colour (the colour code).
        NeonPill(
            icon = Icons.Rounded.Timer,
            label = "SLEEP",
            accent = NeonViolet,
            contentDescription = "Sleep timer",
            activeDot = sleepTimerActive || showSleepMenu,
            onClick = onSleepClick,
        )
        NeonPill(
            icon = Icons.Rounded.Speed,
            label = if (playbackSpeed != 1f) "${formatSpeedLabel(playbackSpeed)}×" else "SPEED",
            accent = NeonGold,
            contentDescription = "Playback speed",
            highlighted = speedActive,
            onClick = onSpeedClick,
        )
        ConnectPill(
            connected = connectState.isConnected,
            busy = connectState.isBusy,
            sharing = connectState.isProviding,
            onClick = { showConnect = true },
        )
        // Picture enhancement entry (GPU filters). Shown once the player has registered it.
        PictureEnhanceRegistry.current?.let { picture ->
            val pictureActive = picture.isActive || picture.panelOpen
            NeonPill(
                icon = Icons.Rounded.Tune,
                label = "PICTURE",
                accent = NeonRose,
                contentDescription = "Picture settings",
                highlighted = pictureActive,
                onClick = { picture.togglePanel() },
            )
        }
        NeonPill(
            icon = Icons.Rounded.PictureInPictureAlt,
            label = "PIP",
            accent = NeonMint,
            contentDescription = "Picture in picture",
            onClick = onPipClick,
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

private val NeonViolet = Color(0xFFC792FF)
private val NeonGold = Color(0xFFFFC94D)
private val NeonRose = Color(0xFFFF6E8C)
private val NeonMint = Color(0xFF7CE0C3)
private val NeonSky = Color(0xFF6FC3FF)

private fun formatSpeedLabel(speed: Float): String =
    if (speed % 1f == 0f) speed.toInt().toString()
    else speed.toString().trimEnd('0').trimEnd('.')

/**
 * Connect-pill-shaped capsule: glass + soft accent wash + accent outline, with a
 * glowing accent icon followed by a short label. Sized by its content.
 */
@Composable
internal fun NeonPill(
    icon: ImageVector,
    label: String,
    accent: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
    activeDot: Boolean = false,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .height(40.dp)
            .clip(shape)
            .background(GlassSurface)
            .background(
                Brush.horizontalGradient(
                    listOf(accent.copy(alpha = if (highlighted) 0.34f else 0.22f), Color.Transparent)
                )
            )
            .border(1.2.dp, accent.copy(alpha = if (highlighted) 0.95f else 0.70f), shape)
            .clickable(onClick = onClick)
            .padding(start = 7.dp, end = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(28.dp)) {
            // Soft neon halo behind the icon.
            Box(
                Modifier
                    .size(28.dp)
                    .background(
                        Brush.radialGradient(listOf(accent.copy(alpha = 0.45f), Color.Transparent)),
                        RoundedCornerShape(50),
                    )
            )
            Icon(icon, contentDescription, tint = accent, modifier = Modifier.size(24.dp))
            if (activeDot) {
                Box(
                    Modifier.align(Alignment.TopEnd)
                        .size(7.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFFE53935))
                )
            }
        }
        Spacer(Modifier.width(7.dp))
        Text(
            label,
            color = TextBright,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            maxLines = 1,
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
        else -> NeonSky
    }
    val label = when {
        connected -> "CONNECTED"
        busy -> "CONNECTING"
        sharing -> "SHARING"
        else -> "CONNECT"
    }
    NeonPill(
        icon = Icons.Rounded.CastConnected,
        label = label,
        accent = accent,
        contentDescription = "Connect",
        highlighted = connected || busy || sharing,
        onClick = onClick,
    )
}
