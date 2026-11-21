package com.sole.cinevault.network

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.drawscope.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.rememberMotionAllowed
import com.sole.cinevault.ui.theme.SpaceBlack
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val RadarCyan = Color(0xFF39D9FF)
private val RadarViolet = Color(0xFF9C7CFF)
private val RadarGreen = Color(0xFF5BE39D)
private val RadarAmber = Color(0xFFFFC24D)

/**
 * The top of the Network hub: a live radar. It sweeps while looking, ripples while
 * sharing, and draws a flowing link with travelling dots once a device is connected.
 * Devices that were found appear as blips that light up when the sweep passes them.
 */
@Composable
internal fun NetworkRadarHero(
    state: CineVaultConnectState,
    devices: List<DiscoveredNetworkDevice>,
    modifier: Modifier = Modifier
) {
    val motion = rememberMotionAllowed()
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    val labelStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
    val transition = rememberInfiniteTransition(label = "radar")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4500, easing = LinearEasing)),
        label = "t"
    )
    val time = if (motion) t else 0.25f
    val accent = when {
        state.isConnected -> RadarGreen
        state.isBusy -> RadarCyan
        state.isProviding -> RadarViolet
        else -> RadarAmber
    }
    val peerId = state.remoteConnection?.device?.id
    val peer = state.remoteConnection?.device
    val shown = remember(devices, peer) {
        (listOfNotNull(peer) + devices).distinctBy { it.id }.take(8)
    }
    val description = when {
        state.isConnected -> "Connected. Streaming link active."
        state.isBusy -> "Looking for devices on your network."
        state.isProviding -> "Sharing your library nearby."
        else -> "Ready. Find devices to start."
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(320.dp)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxWidth().height(320.dp)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val maxR = min(size.width, size.height) / 2f - 6.dp.toPx()
            val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 8.dp.toPx()))

            // A faint star field so the space around the radar never looks empty.
            for (i in 0 until 46) {
                val sx = ((i * 97) % 1000) / 1000f * size.width
                val sy = ((i * 53) % 1000) / 1000f * size.height
                val tw = 0.5f + 0.5f * sin((time + i / 46f) * 2f * PI.toFloat())
                drawCircle(Color.White.copy(alpha = 0.05f + 0.10f * tw), radius = (0.8f + (i % 3) * 0.5f).dp.toPx(), center = Offset(sx, sy))
            }
            // Quiet guide rings.
            for (k in 1..3) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = maxR * k / 3f,
                    center = c,
                    style = Stroke(1.dp.toPx(), pathEffect = dash)
                )
            }
            // Expanding ripples while something is happening.
            if (state.isBusy || state.isProviding || state.isConnected) {
                for (k in 0 until 3) {
                    val phase = (time + k / 3f) % 1f
                    drawCircle(
                        color = accent.copy(alpha = (1f - phase) * 0.45f),
                        radius = maxR * phase,
                        center = c,
                        style = Stroke(1.5.dp.toPx())
                    )
                }
            } else {
                val breathe = 0.5f + 0.5f * sin(time * 2f * PI.toFloat())
                drawCircle(
                    color = accent.copy(alpha = 0.10f + 0.14f * breathe),
                    radius = maxR * (0.28f + 0.06f * breathe),
                    center = c,
                    style = Stroke(1.5.dp.toPx())
                )
            }
            // The sweep.
            val sweepAngle = time * 360f
            if (state.isBusy) {
                rotate(degrees = sweepAngle, pivot = c) {
                    drawCircle(
                        brush = Brush.sweepGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.78f to Color.Transparent,
                                1f to accent.copy(alpha = 0.42f)
                            ),
                            center = c
                        ),
                        radius = maxR,
                        center = c
                    )
                }
            }
            // Device blips.
            shown.forEach { device ->
                val (angle, distance) = radarPlacement(device.id)
                val isPeer = device.id == peerId
                val rad = angle * PI.toFloat() / 180f
                val r = if (isPeer) maxR * 0.78f else maxR * distance
                val p = Offset(c.x + r * cos(rad), c.y + r * sin(rad))
                val color = when {
                    isPeer -> RadarGreen
                    device.kind == NetworkDiscoveryKind.CINEVAULT -> RadarViolet
                    device.kind == NetworkDiscoveryKind.DLNA -> RadarCyan
                    else -> RadarAmber
                }
                val glow = if (state.isBusy) sweepGlow(sweepAngle, angle)
                else 0.35f + 0.25f * sin((time + angle / 360f) * 2f * PI.toFloat())
                val dim = if (peerId != null && !isPeer) 0.35f else 1f
                drawCircle(color.copy(alpha = 0.28f * glow * dim), radius = (7f + 14f * glow).dp.toPx(), center = p)
                drawCircle(color.copy(alpha = dim), radius = (if (isPeer) 6f else 4.5f).dp.toPx(), center = p)
                drawCircle(SpaceBlack, radius = 1.8.dp.toPx(), center = p)
                val label = textMeasurer.measure(device.displayName.take(18), labelStyle)
                drawText(label, color = Color.White.copy(alpha = 0.8f * dim), topLeft = Offset(p.x - label.size.width / 2f, p.y + 12.dp.toPx()))

                // A flowing link from this device to the connected peer.
                if (isPeer) {
                    val mid = Offset((c.x + p.x) / 2f, (c.y + p.y) / 2f - 34.dp.toPx())
                    val path = Path().apply {
                        moveTo(c.x, c.y)
                        quadraticTo(mid.x, mid.y, p.x, p.y)
                    }
                    drawPath(
                        path,
                        color = RadarGreen.copy(alpha = 0.75f),
                        style = Stroke(
                            2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(5.dp.toPx(), 9.dp.toPx()),
                                phase = -time * 14.dp.toPx() * 8f
                            )
                        )
                    )
                    for (k in 0 until 3) {
                        val u = (time * 2f + k / 3f) % 1f
                        val inv = 1f - u
                        val x = inv * inv * c.x + 2f * inv * u * mid.x + u * u * p.x
                        val y = inv * inv * c.y + 2f * inv * u * mid.y + u * u * p.y
                        drawCircle(RadarGreen.copy(alpha = 0.9f * min(1f, abs(sin(u * PI.toFloat())) + 0.2f)), 3.dp.toPx(), Offset(x, y))
                    }
                }
            }
        }
        // This device, at the centre.
        Box(
            Modifier
                .size(56.dp)
                .background(SpaceBlack, CircleShape)
                .border(1.5.dp, accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                when {
                    state.isConnected -> Icons.Rounded.Link
                    state.isBusy -> Icons.Rounded.Radar
                    state.isProviding -> Icons.Rounded.WifiTethering
                    else -> Icons.Rounded.Hub
                },
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
