package com.sole.cinevault

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.network.CineVaultNearbyRuntime
import com.sole.cinevault.picture.PictureEnhanceRegistry
import com.sole.cinevault.ui.theme.GlassSurface
import com.sole.cinevault.ui.theme.TextBright

private val NeonViolet = Color(0xFFC792FF)
private val NeonGold = Color(0xFFFFC94D)
private val NeonRose = Color(0xFFFF6E8C)
private val NeonMint = Color(0xFF7CE0C3)
private val NeonSky = Color(0xFF6FC3FF)

/** How a pill's border animates: a light running round it, or a sweep showing progress. */
internal data class PillRunner(val periodMs: Int)

/** A ring that grows out of the icon and fades, over [periodMs]. */
internal data class PillPulse(val periodMs: Int)

/**
 * Right-hand pill column, top to bottom: lock, sleep, speed, connect, picture, PiP, voice.
 * Pills in the column share one width. The lock pill is drawn by the lock layer at the very
 * same spot; a blank slot keeps its place here.
 */
@Composable
internal fun TopIconCluster(
    isLandscape: Boolean,
    playbackSpeed: Float,
    sleepTimerActive: Boolean,
    sleepTimerRemainingMs: Long,
    sleepTimerMinutes: Int,
    showSpeedMenu: Boolean,
    showSleepMenu: Boolean,
    onSpeedClick: () -> Unit,
    onSleepClick: () -> Unit,
    onPipClick: () -> Unit,
    onConnectClick: () -> Unit = { PlayerConnectUi.visible = !PlayerConnectUi.visible },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val motion = rememberMotionEnabled()
    val nearby = remember(context) { CineVaultNearbyRuntime.get(context) }
    val connectState by nearby.connectState.collectAsState()

    val gap = PlayerPillMetrics.gap(isLandscape)
    val pillHeight = PlayerPillMetrics.height(isLandscape)
    val wide = Modifier.fillMaxWidth()

    Column(
        modifier = modifier
            .width(IntrinsicSize.Max)
            .onGloballyPositioned {
                PlayerPillMetrics.rightColumnWidth = with(density) { it.size.width.toDp() }
                PlayerPillMetrics.rightColumnHeight = with(density) { it.size.height.toDp() }
            },
        verticalArrangement = Arrangement.spacedBy(gap),
        horizontalAlignment = Alignment.End,
    ) {
        Spacer(Modifier.height(pillHeight))

        // SLEEP: counts down in the label; a ring sweeps round the pill showing time left.
        val swing by rememberLoop(motion && sleepTimerActive, -9f, 9f, 1400)
        val sleepMinutesLeft = ((sleepTimerRemainingMs + 59_999L) / 60_000L).toInt()
        NeonPill(
            icon = Icons.Rounded.Timer,
            label = if (sleepTimerActive && sleepTimerRemainingMs > 0) "SLEEP ${sleepMinutesLeft}m" else "SLEEP",
            accent = NeonViolet,
            contentDescription = "Sleep timer",
            compact = isLandscape,
            modifier = wide,
            highlighted = sleepTimerActive || showSleepMenu,
            iconModifier = Modifier.graphicsLayer { rotationZ = swing },
            progress = if (sleepTimerActive && sleepTimerMinutes > 0 && sleepTimerRemainingMs > 0) {
                { sleepTimerRemainingMs.toFloat() / (sleepTimerMinutes * 60_000f) }
            } else null,
            bobPhaseMs = 0,
            onClick = onSleepClick,
        )

        // SPEED: the dial needle leans to the chosen speed.
        val needle by animateFloatAsState(
            targetValue = (playbackSpeed.coerceIn(0.25f, 3f) - 1f) * 20f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            label = "speedNeedle",
        )
        val flutter by rememberLoop(motion && playbackSpeed != 1f, -2.5f, 2.5f, 260)
        NeonPill(
            icon = Icons.Rounded.Speed,
            label = if (playbackSpeed != 1f) "${formatSpeedLabel(playbackSpeed)}×" else "SPEED",
            accent = NeonGold,
            contentDescription = "Playback speed",
            compact = isLandscape,
            modifier = wide,
            highlighted = playbackSpeed != 1f || showSpeedMenu,
            iconModifier = Modifier.graphicsLayer { rotationZ = if (motion) needle + flutter else 0f },
            bobPhaseMs = 450,
            onClick = onSpeedClick,
        )

        // CONNECT: idle waves, a light running round the pill while connecting, a pop when connected.
        val connected = connectState.isConnected
        val busy = connectState.isBusy
        val sharing = connectState.isProviding
        val connectAccent = when {
            connected -> Color(0xFF5BE39D)
            busy -> Color(0xFF39D9FF)
            sharing -> Color(0xFF9C7CFF)
            else -> NeonSky
        }
        val pop by rememberKick(trigger = connected, enabled = motion, durationMs = 520)
        NeonPill(
            icon = Icons.Rounded.CastConnected,
            label = when {
                connected -> "CONNECTED"
                busy -> "CONNECTING"
                sharing -> "SHARING"
                else -> "CONNECT"
            },
            accent = connectAccent,
            contentDescription = "Connect",
            compact = isLandscape,
            modifier = wide,
            highlighted = connected || busy || sharing,
            iconModifier = Modifier.graphicsLayer {
                val s = 1f + 0.4f * pop
                scaleX = s
                scaleY = s
            },
            runner = if (busy) PillRunner(950) else null,
            ringPulse = when {
                busy -> PillPulse(1100)
                sharing -> PillPulse(1500)
                connected -> null
                else -> PillPulse(3200)
            },
            bobPhaseMs = 900,
            onClick = onConnectClick,
        )

        // PICTURE: the sliders drift while the picture panel is open or a filter is on.
        PictureEnhanceRegistry.current?.let { picture ->
            val pictureActive = picture.isActive || picture.panelOpen
            val drift by rememberLoop(motion && pictureActive, -2.5f, 2.5f, 1100)
            NeonPill(
                icon = Icons.Rounded.Tune,
                label = "PICTURE",
                accent = NeonRose,
                contentDescription = "Picture settings",
                compact = isLandscape,
                modifier = wide,
                highlighted = pictureActive,
                iconModifier = Modifier.graphicsLayer { translationX = drift.dp.toPx() },
                bobPhaseMs = 1350,
                onClick = { picture.togglePanel() },
            )
        }

        // PIP: the inner window shrinks to the corner when tapped.
        var pipTaps by remember { mutableIntStateOf(0) }
        val pipKick by rememberKick(trigger = pipTaps, enabled = motion, durationMs = 520)
        NeonPill(
            icon = Icons.Rounded.PictureInPictureAlt,
            label = "PIP",
            accent = NeonMint,
            contentDescription = "Picture in picture",
            compact = isLandscape,
            modifier = wide,
            iconModifier = Modifier.graphicsLayer {
                val s = 1f - 0.4f * pipKick
                scaleX = s
                scaleY = s
                translationX = 5.dp.toPx() * pipKick
                translationY = 5.dp.toPx() * pipKick
            },
            bobPhaseMs = 1800,
            onClick = {
                pipTaps++
                onPipClick()
            },
        )

        VoiceRoundButton(compact = isLandscape, motion = motion)
    }

}

private fun formatSpeedLabel(speed: Float): String =
    if (speed % 1f == 0f) speed.toInt().toString()
    else speed.toString().trimEnd('0').trimEnd('.')

/**
 * Connect-pill-shaped capsule: glass + soft accent wash + accent outline, with a glowing accent
 * icon followed by a short label. It floats: a soft shadow in its own colour, a light top edge,
 * a very slow bob, and a quick squish when pressed. Motion stops when the person reduces motion.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NeonPill(
    icon: ImageVector,
    label: String,
    accent: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    activeDot: Boolean = false,
    highlighted: Boolean = false,
    haloBoost: Float = 0f,
    haloBreath: Boolean = false,
    blink: Boolean = false,
    iconModifier: Modifier = Modifier,
    runner: PillRunner? = null,
    progress: (() -> Float)? = null,
    ringPulse: PillPulse? = null,
    bobPhaseMs: Int = 0,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val motion = rememberMotionEnabled()
    val shape = RoundedCornerShape(50)
    val box = PlayerPillMetrics.iconBox(compact)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val squish by animateFloatAsState(
        targetValue = if (pressed && motion) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pillSquish",
    )
    val bob by rememberLoop(motion, -1.5f, 1.5f, 3600, rest = 0f, phaseMs = bobPhaseMs)
    val runnerPhase = rememberCycle(motion && runner != null, runner?.periodMs ?: 1000)
    val pulsePhase = rememberCycle(motion && ringPulse != null, ringPulse?.periodMs ?: 1000)
    val breath by rememberLoop(motion && haloBreath, 0f, 0.35f, 1500)
    val blinkAlpha by rememberLoop(motion && blink, 0.35f, 1f, 800, rest = 1f)
    val strokePx = with(LocalDensity.current) { 2.dp.toPx() }

    Row(
        modifier
            .graphicsLayer {
                translationY = bob.dp.toPx()
                scaleX = squish
                scaleY = squish
            }
            .height(PlayerPillMetrics.height(compact))
            .shadow(
                elevation = if (pressed) 3.dp else 9.dp,
                shape = shape,
                clip = false,
                ambientColor = accent,
                spotColor = accent,
            )
            .clip(shape)
            .background(GlassSurface)
            .background(
                Brush.horizontalGradient(
                    listOf(accent.copy(alpha = if (highlighted) 0.34f else 0.22f), Color.Transparent)
                )
            )
            .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.10f), 0.5f to Color.Transparent))
            .border(
                1.2.dp,
                accent.copy(alpha = (if (highlighted) 0.95f else 0.70f) * blinkAlpha),
                shape,
            )
            .pillBorderFx(
                color = accent,
                runner = if (runner != null) ({ runnerPhase.value }) else null,
                progress = progress,
                strokeWidthPx = strokePx,
            )
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(start = 7.dp, end = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(box)
                .drawBehind {
                    if (ringPulse != null) {
                        val p = pulsePhase.value
                        drawCircle(
                            color = accent.copy(alpha = (1f - p) * 0.55f),
                            radius = size.minDimension / 2f * (0.7f + 0.9f * p),
                            style = Stroke(width = 1.5.dp.toPx()),
                        )
                    }
                },
        ) {
            // Soft neon halo behind the icon; flares while pressed.
            Box(
                Modifier
                    .size(box)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                accent.copy(
                                    alpha = (0.45f + haloBoost + breath + if (pressed) 0.3f else 0f)
                                        .coerceAtMost(0.95f)
                                ),
                                Color.Transparent,
                            )
                        ),
                        CircleShape,
                    )
            )
            Icon(
                icon,
                contentDescription,
                tint = accent,
                modifier = iconModifier.size(PlayerPillMetrics.icon(compact)),
            )
            if (activeDot) {
                Box(
                    Modifier.align(Alignment.TopEnd)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE53935))
                )
            }
        }
        Spacer(Modifier.width(7.dp))
        Text(
            label,
            color = TextBright,
            fontSize = if (compact) 9.sp else 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            maxLines = 1,
        )
    }
}

/**
 * The talk (microphone) button, last in the right-hand column. Round and filled in the pill colours:
 * violet when idle, mint while listening. It breathes when idle; while listening, rings grow out of
 * it with your voice level. The talk host in MainActivity owns the listening; this only draws and taps.
 */
@Composable
private fun VoiceRoundButton(compact: Boolean, motion: Boolean) {
    val tap = VoiceRuntime.micTap
    val inPlayer = VoicePlayerRegistry.current != null
    val micShown = VoiceRuntime.showMic || !VoiceRuntime.wakeListening
    if (!(VoiceRuntime.enabled && micShown && inPlayer && tap != null)) return

    val listening = VoiceRuntime.micListening
    val level = if (listening) VoiceRuntime.micTalkLevel else VoiceRuntime.wakeLevel
    val ringOn = listening || (VoiceRuntime.wakeListening && VoiceRuntime.isListening)
    val accent = if (listening || VoiceRuntime.wakeSpeech) NeonMint else NeonViolet
    val size = PlayerPillMetrics.voiceSize(compact)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val squish by animateFloatAsState(
        targetValue = if (pressed && motion) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "voiceSquish",
    )
    val breathe by rememberLoop(motion && !listening, 1f, 1.07f, 2400, rest = 1f)
    val bob by rememberLoop(motion, -1.5f, 1.5f, 3600, rest = 0f, phaseMs = 2250)
    val ringPhase = rememberCycle(motion && ringOn, 1200)
    val boost = if (ringOn) kotlin.math.sqrt(level.coerceIn(0f, 1f)) else 0f

    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                translationY = bob.dp.toPx()
                val s = squish * breathe
                scaleX = s
                scaleY = s
            }
            .drawBehind {
                if (ringOn) {
                    val p = ringPhase.value
                    drawCircle(
                        color = accent.copy(alpha = (1f - p) * 0.55f),
                        radius = this.size.minDimension / 2f * (1.05f + (0.35f + 0.6f * boost) * p),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                    drawCircle(
                        color = accent.copy(alpha = 0.22f + 0.25f * boost),
                        radius = this.size.minDimension / 2f * (1.12f + 0.3f * boost),
                    )
                }
            }
            .shadow(12.dp, CircleShape, clip = false, ambientColor = accent, spotColor = accent)
            .clip(CircleShape)
            .background(accent)
            .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.28f), 0.55f to Color.Transparent))
            .border(1.2.dp, Color.White.copy(alpha = 0.55f), CircleShape)
            .combinedClickable(interactionSource = interaction, indication = null, onClick = { tap() }),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Mic,
            contentDescription = if (listening) "Stop listening" else "Talk to CineVault",
            tint = Color(0xFF05060A),
            modifier = Modifier.size(size * 0.52f),
        )
    }
}
