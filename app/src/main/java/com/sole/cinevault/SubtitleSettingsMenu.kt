package com.sole.cinevault

import com.sole.cinevault.ui.theme.*
import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// Dark, warm color for text sitting ON TOP of the solid amber fills (step
// pills, reset pill's pulse core) — plain black/white reads flat against
// amber; this matches the deep-brown-on-gold treatment used everywhere else
// a solid amber fill needs a legible label.
private val AmberFillText = Color(0xFF241A08)
private val DangerRed = Color(0xFFFF5252)

// Single source of truth for this menu's width, used both internally below
// AND by VideoPlayerScreen.kt to calculate where to anchor the popup next
// to the subtitle icon. Previously these were two separately hand-written
// formulas that had to be kept in sync manually — easy to silently drift
// apart the next time either file changes (which is exactly what would
// have happened here, since this redesign changed the width percentages).
// Not private, so it's callable from VideoPlayerScreen.kt in the same
// package.
fun subtitleMenuWidth(screenWidthDp: Float, isLandscape: Boolean): Dp =
    if (isLandscape) (screenWidthDp * 0.19f).dp.coerceIn(135.dp, 175.dp)
    else (screenWidthDp * 0.40f).dp.coerceIn(150.dp, 210.dp)

@Composable
fun SubtitleSettingsMenu(
    isVisible: Boolean,
    subtitlesEnabled: Boolean,
    hasInternalSubtitles: Boolean,
    onInternalClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onPickFileClick: () -> Unit = {},
    onToggleSubtitles: () -> Unit,
    onDismiss: () -> Unit,
    currentFontSize: Float,
    onFontSizeChange: (Float) -> Unit,
    currentVerticalPosition: Float,
    onVerticalPositionChange: (Float) -> Unit,
    currentSyncOffset: Float,
    onSyncOffsetChange: (Float) -> Unit,
    onReset: () -> Unit,
    onUserInteraction: () -> Unit
) {
    if (!isVisible) return
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp
    val popupWidth: Dp = subtitleMenuWidth(screenWidthDp.toFloat(), isLandscape)
    val panelMaxHeight: Dp = if (isLandscape) {
        (screenHeightDp * 0.82f).dp.coerceAtMost(260.dp)
    } else {
        (screenHeightDp * 0.60f).dp.coerceAtMost(440.dp)
    }

    val rowGap = if (isLandscape) 7.dp else 8.dp
    val titleSize = if (isLandscape) 11.sp else 12.sp
    val labelSize = if (isLandscape) 9.sp else 9.5.sp
    val valueSize = if (isLandscape) 8.5.sp else 9.sp

    Column(
        modifier = Modifier
            .width(popupWidth)
            .heightIn(max = panelMaxHeight)
            .glassPanel(cornerRadius = if (isLandscape) 18.dp else 20.dp, fill = SpaceMid.copy(alpha = 0.97f))
            .clickable { onUserInteraction() }
            .padding(start = if (isLandscape) 9.dp else 11.dp, end = if (isLandscape) 9.dp else 11.dp, top = if (isLandscape) 9.dp else 10.dp, bottom = if (isLandscape) 12.dp else 14.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(rowGap)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Subtitles", color = AmberCore, fontSize = titleSize, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = { onUserInteraction(); onDismiss() }, modifier = Modifier.size(if (isLandscape) 20.dp else 22.dp).clip(CircleShape).background(GlassSurface)) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextBright, modifier = Modifier.size(if (isLandscape) 11.dp else 12.dp))
            }
        }

        // Three separate pills, not a stacked list — Download always carries
        // a soft amber glow (it's the primary action here); Off/On glows RED
        // specifically when subtitles are actually off, so the pill's state
        // doubles as a status indicator, not just a button; Browse stays
        // neutral since it has no persistent on/off state of its own.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            ActionPill(
                icon = Icons.Default.Download,
                label = "Download",
                modifier = Modifier.weight(1f),
                glow = PillGlow.Amber,
                isLandscape = isLandscape
            ) { onUserInteraction(); onDownloadClick() }
            ActionPill(
                icon = if (subtitlesEnabled) Icons.Default.Subtitles else Icons.Default.SubtitlesOff,
                label = if (subtitlesEnabled) "On" else "Off",
                modifier = Modifier.weight(1f),
                glow = if (!subtitlesEnabled) PillGlow.Red else PillGlow.None,
                isLandscape = isLandscape
            ) { onUserInteraction(); onToggleSubtitles() }
            ActionPill(
                icon = Icons.Default.FolderOpen,
                label = "Browse",
                modifier = Modifier.weight(1f),
                glow = PillGlow.None,
                isLandscape = isLandscape
            ) { onUserInteraction(); onPickFileClick() }
        }

        HorizontalDivider(color = GlassBorderBottom)

        SteppedControlRow(
            label = "Text size",
            valueText = "${currentFontSize.toInt()}sp",
            value = currentFontSize,
            valueRange = 12f..32f,
            stepAmount = 1f,
            labelSize = labelSize,
            valueSize = valueSize,
            onChange = { onUserInteraction(); onFontSizeChange(it) },
            formatBubble = { "${it.toInt()}sp" }
        )
        SteppedControlRow(
            label = "Position",
            valueText = positionBandLabel(currentVerticalPosition),
            value = currentVerticalPosition,
            valueRange = 0.02f..0.30f,
            stepAmount = 0.01f,
            labelSize = labelSize,
            valueSize = valueSize,
            onChange = { onUserInteraction(); onVerticalPositionChange(it) },
            formatBubble = { positionBandLabel(it) }
        )
        SteppedControlRow(
            label = "Sync",
            valueText = formatSyncSeconds(currentSyncOffset),
            value = currentSyncOffset.coerceIn(-10f, 10f),
            valueRange = -10f..10f,
            stepAmount = 0.1f,
            labelSize = labelSize,
            valueSize = valueSize,
            onChange = { onUserInteraction(); onSyncOffsetChange(it) },
            formatBubble = { formatSyncSeconds(it) },
            accelHint = "hold either side to speed up"
        )

        HorizontalDivider(color = GlassBorderBottom)

        // Reset — same solid pill shape as the top action row, but pulsing
        // red so it reads unmistakably as the "undo everything" action
        // rather than blending in with the rest of the controls.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            PulsingResetPill(isLandscape = isLandscape) { onUserInteraction(); onReset() }
        }
    }
}

private fun positionBandLabel(value: Float): String {
    val fraction = ((value - 0.02f) / (0.30f - 0.02f)).coerceIn(0f, 1f)
    return when {
        fraction < 0.34f -> "Low"
        fraction < 0.67f -> "Mid"
        else -> "High"
    }
}

private fun formatSyncSeconds(value: Float): String =
    if (value >= 0f) "+${String.format("%.1f", value)}s" else "${String.format("%.1f", value)}s"

private enum class PillGlow { None, Amber, Red }

// One of the three top actions. Glow is entirely driven by the `glow`
// parameter so the same composable serves the always-amber Download pill,
// the state-reflecting Off/On pill, and the neutral Browse pill.
@Composable
private fun ActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    glow: PillGlow,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val borderColor = when (glow) { PillGlow.Amber -> AmberCore; PillGlow.Red -> DangerRed; PillGlow.None -> AmberCore.copy(alpha = 0.35f) }
    val contentColor = when (glow) { PillGlow.Amber -> AmberCore; PillGlow.Red -> Color(0xFFFF8080); PillGlow.None -> TextBright.copy(alpha = 0.85f) }
    val glowRadius = if (glow == PillGlow.None) 0.dp else 14.dp

    Column(
        modifier = modifier
            .let { if (glowRadius > 0.dp) it.amberGlow(radius = glowRadius, alpha = 0.5f) else it }
            .clip(RoundedCornerShape(14.dp))
            .background(SpaceDeep.copy(alpha = 0.7f))
            .border(1.2.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = if (isLandscape) 6.dp else 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(if (isLandscape) 14.dp else 16.dp))
        Spacer(modifier = Modifier.height(3.dp))
        Text(text = label, color = contentColor, fontSize = if (isLandscape) 7.5.sp else 8.5.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// Text Size / Position / Sync all share this exact structure now: an
// outlined (not flat, not glowing) label pill showing name + current value,
// then a [-] [seek bar] [+] row. The floating value bubble appears above the
// thumb whenever the value changes from EITHER the drag gesture or the step
// pills, and fades out ~900ms after the last change — the same debounce
// pattern already used for the buffering spinner in VideoPlayerScreen.kt.
@Composable
private fun SteppedControlRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    stepAmount: Float,
    labelSize: TextUnit,
    valueSize: TextUnit,
    onChange: (Float) -> Unit,
    formatBubble: (Float) -> String,
    accelHint: String? = null
) {
    var interactionTick by remember { mutableIntStateOf(0) }
    var showBubble by remember { mutableStateOf(false) }
    var bubbleValue by remember { mutableFloatStateOf(value) }

    fun notifyInteraction(newValue: Float) {
        bubbleValue = newValue
        showBubble = true
        interactionTick++
    }

    LaunchedEffect(interactionTick) {
        if (interactionTick == 0) return@LaunchedEffect
        delay(900)
        showBubble = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(SpaceDeep.copy(alpha = 0.7f))
                .border(1.2.dp, AmberCore.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                .padding(horizontal = 11.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, color = Color(0xFFC9A765), fontSize = labelSize, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = valueText, color = AmberCore, fontSize = valueSize, fontWeight = FontWeight.Black)
        }
        Spacer(modifier = Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            AccelStepPill(isPlus = false) {
                val next = (value - stepAmount).coerceIn(valueRange.start, valueRange.endInclusive)
                notifyInteraction(next); onChange(next)
            }
            Box(modifier = Modifier.weight(1f)) {
                if (showBubble) {
                    val fraction = ((bubbleValue - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
                    ValueBubble(text = formatBubble(bubbleValue), fraction = fraction)
                }
                ThinSliderBar(
                    value = value,
                    onValueChange = { notifyInteraction(it); onChange(it) },
                    valueRange = valueRange
                )
            }
            AccelStepPill(isPlus = true) {
                val next = (value + stepAmount).coerceIn(valueRange.start, valueRange.endInclusive)
                notifyInteraction(next); onChange(next)
            }
        }
        if (accelHint != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = accelHint, color = TextFaint.copy(alpha = 0.7f), fontSize = 7.5.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
        }
    }
}

// Floating readout above the slider thumb — positioned by fraction across
// the same width the ThinSliderBar occupies, so it tracks the thumb exactly.
@Composable
private fun ValueBubble(text: String, fraction: Float) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val bubbleWidthEstimate = 44.dp
        val xOffset = (maxWidth * fraction - bubbleWidthEstimate / 2f).coerceIn(0.dp, (maxWidth - bubbleWidthEstimate).coerceAtLeast(0.dp))
        Box(
            modifier = Modifier
                .offset(x = xOffset, y = (-22).dp)
                .clip(RoundedCornerShape(50))
                .background(SpaceDeep)
                .border(1.dp, AmberCore.copy(alpha = 0.5f), RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(text = text, color = AmberCore, fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

// Filled amber capsule — replaces the old outline circles. Tap = one
// immediate step. Holding past ~350ms starts auto-repeating, with the
// interval between repeats shrinking (220ms -> 40ms) the longer it's held,
// which is what actually produces the "hold to accelerate" feel rather than
// a fixed repeat rate.
@Composable
private fun AccelStepPill(isPlus: Boolean, onStep: () -> Unit) {
    val scope = rememberCoroutineScope()
    // pointerInput(Unit) only ever installs its gesture-detection block ONCE,
    // on first composition — it does NOT get a fresh copy of onStep on every
    // recomposition. Without this wrapper, every press (and every tick of a
    // held repeat) was silently using the very first onStep lambda from when
    // this pill was first drawn, computing from a permanently stale value —
    // which is exactly why Sync would jump straight to +10.0s and stick, and
    // why holding appeared to do nothing (it kept recomputing the same
    // frozen result). rememberUpdatedState always points at the latest
    // lambda, so each tap/tick now reads the real current value.
    val currentOnStep = rememberUpdatedState(onStep)
    Box(
        modifier = Modifier
            .size(width = 32.dp, height = 28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(AmberGlow, AmberCore)))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        currentOnStep.value()
                        val job = scope.launch {
                            delay(350)
                            var interval = 220L
                            while (isActive) {
                                currentOnStep.value()
                                delay(interval)
                                interval = (interval * 0.86f).toLong().coerceAtLeast(40L)
                            }
                        }
                        tryAwaitRelease()
                        job.cancel()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(text = if (isPlus) "+" else "\u2212", color = AmberFillText, fontSize = 16.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun PulsingResetPill(isLandscape: Boolean, onClick: () -> Unit) {
    val infinite = rememberInfiniteTransition(label = "resetPulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1000, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "resetPulseAlpha"
    )
    Row(
        modifier = Modifier
            .amberGlow(radius = 16.dp, alpha = pulse * 0.6f)
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF2A0A0A))
            .border(1.2.dp, DangerRed.copy(alpha = 0.4f + pulse * 0.5f), RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = if (isLandscape) 12.dp else 14.dp, vertical = if (isLandscape) 6.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = Icons.Default.Restore, contentDescription = null, tint = Color(0xFFFF8080), modifier = Modifier.size(if (isLandscape) 12.dp else 13.dp))
        Spacer(modifier = Modifier.width(5.dp))
        Text(text = "Reset", color = Color(0xFFFF8080), fontSize = if (isLandscape) 9.sp else 10.sp, fontWeight = FontWeight.Black)
    }
}

// A genuinely thin slider — Material3's Slider always reserves a large touch
// target no matter how short its Modifier.height is set, which is what was
// eating vertical space. This draws just a thin rounded track + small thumb
// directly, at whatever height is asked for (12-16dp instead of 26-34dp).
@Composable
private fun ThinSliderBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    height: Dp = 22.dp
) {
    fun valueFromFraction(fraction: Float): Float =
        valueRange.start + fraction.coerceIn(0f, 1f) * (valueRange.endInclusive - valueRange.start)

    // Only the thumb (the small dot) responds to touch/drag now — anywhere
    // else on the bar lets the gesture pass straight through to the popup's
    // verticalScroll. Previously ANY touch on the bar (including the start of
    // a vertical scroll swipe) was captured as a horizontal drag, which is
    // what made scrolling this menu feel broken, especially in landscape
    // where the popup is short and packed tight with sliders.
    val currentValue = rememberUpdatedState(value)
    val currentOnChange = rememberUpdatedState(onValueChange)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(valueRange) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val fraction = ((currentValue.value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
                    val thumbX = size.width * fraction
                    val hitRadiusPx = 22.dp.toPx()
                    if (kotlin.math.abs(down.position.x - thumbX) > hitRadiusPx) {
                        // Missed the thumb — don't consume, let it fall through to scroll.
                        return@awaitEachGesture
                    }
                    down.consume()
                    currentOnChange.value(valueFromFraction(down.position.x / size.width.toFloat()))
                    val pointerId = down.id
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (!change.pressed) break
                        change.consume()
                        currentOnChange.value(valueFromFraction(change.position.x / size.width.toFloat()))
                    }
                }
            }
    ) {
        val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackH = (size.height * 0.20f).coerceAtLeast(2f)
            val cy = size.height / 2f
            val r = CornerRadius(trackH / 2f, trackH / 2f)
            drawRoundRect(color = Color.White.copy(alpha = 0.14f), topLeft = Offset(0f, cy - trackH / 2f), size = Size(size.width, trackH), cornerRadius = r)
            drawRoundRect(color = AmberGlow, topLeft = Offset(0f, cy - trackH / 2f), size = Size(size.width * fraction, trackH), cornerRadius = r)
            drawCircle(color = AmberCore, radius = size.height * 0.30f, center = Offset(size.width * fraction, cy))
            drawCircle(color = Color.White.copy(alpha = 0.9f), radius = size.height * 0.11f, center = Offset(size.width * fraction, cy))
        }
    }
}
