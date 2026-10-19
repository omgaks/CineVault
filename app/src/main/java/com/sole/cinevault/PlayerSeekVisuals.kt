package com.sole.cinevault

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.segments.FilmEnvelope
import com.sole.cinevault.segments.FilmLoudness
import com.sole.cinevault.segments.SeekMarker
import com.sole.cinevault.segments.SeekMarkerKind
import com.sole.cinevault.ui.theme.*
import kotlinx.coroutines.delay

@Composable
internal fun CinematicSeekBar(position: Long, duration: Long, isDragging: Boolean, seed: Int, onPreviewPositionChanged: (Long) -> Unit, onSeekFinished: (Long) -> Unit, markers: List<SeekMarker> = emptyList(), bufferedMs: Long = 0L, onScrub: (Long, Float) -> Unit = { _, _ -> }) {
    var localPosition by remember { mutableLongStateOf(position) }
    LaunchedEffect(position, isDragging) { if (!isDragging) localPosition = position }
    val haptic = LocalHapticFeedback.current
    var waveformVisible by remember { mutableStateOf(false) }
    LaunchedEffect(isDragging) {
        if (isDragging) { waveformVisible = true }
        else if (waveformVisible) { delay(2000); waveformVisible = false }
    }
    // Same bar width/gap as the waveform drawn below — used here purely to
    // detect when a drag crosses into a new bar, so a haptic tick can fire
    // per bar instead of only 4 times across the whole seek bar. Gives a
    // scroll-wheel/ratchet feel instead of a few coarse clicks.
    val density = LocalDensity.current
    val barStepPx = with(density) { (3.dp + 2.2.dp).toPx() }
    var lastBarIndex by remember { mutableIntStateOf(-1) }
    // Finer control: slide the thumb up while dragging and the bar moves slower (like the iPhone player).
    var originMs by remember { mutableLongStateOf(0L) }
    var fine by remember { mutableFloatStateOf(1f) }
    var anchorX by remember { mutableFloatStateOf(0f) }
    var anchorMs by remember { mutableLongStateOf(0L) }
    var startY by remember { mutableFloatStateOf(0f) }
    // Scene-break ticks: real quiet gaps in the soundtrack, once the audio has been measured.
    val cutFractions = remember(FilmLoudness.db, seed, duration) {
        val db = FilmLoudness.db
        if (db == null || FilmLoudness.seed != seed || duration <= 0L) emptyList() else sceneBreakFractions(db)
    }
    fun barIndexOf(x: Float): Int = (x / barStepPx).toInt()
    val bloom by animateFloatAsState(targetValue = if (isDragging || waveformVisible) 1f else 0f, animationSpec = tween(if (isDragging || waveformVisible) 300 else 600, easing = FastOutSlowInEasing), label = "liquidBloom")
    val scenePulse by rememberInfiniteTransition(label = "sceneDot").animateFloat(
        initialValue = 0.40f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sceneDotPulse"
    )
    val glow by animateFloatAsState(targetValue = if (isDragging) 1f else 0.45f, animationSpec = tween(220), label = "seekGlow")
    fun positionFromX(x: Float, width: Float): Long { if (duration <= 0L || width <= 0f) return 0L; return (duration * (x / width).coerceIn(0f, 1f)).toLong().coerceIn(0L, duration) }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val trackWidth = maxWidth
        Box(modifier = Modifier.fillMaxWidth().height(38.dp)
            .pointerInput(duration) { detectTapGestures { o -> val p = positionFromX(o.x, size.width.toFloat()); localPosition = p; onPreviewPositionChanged(p); onSeekFinished(p) } }
            .pointerInput(duration) { detectDragGestures(
                onDragStart = { o ->
                    originMs = localPosition
                    localPosition = positionFromX(o.x, size.width.toFloat()); lastBarIndex = barIndexOf(o.x)
                    anchorX = o.x; anchorMs = localPosition; startY = o.y; fine = 1f
                    onScrub(originMs, fine)
                    onPreviewPositionChanged(localPosition) },
                onDrag = { c, _ ->
                    val up = (startY - c.position.y) / density.density
                    val nf = when { up > 150f -> 0.1f; up > 90f -> 0.25f; up > 40f -> 0.5f; else -> 1f }
                    if (nf != fine) { fine = nf; anchorX = c.position.x; anchorMs = localPosition; onScrub(originMs, fine); haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    localPosition = (anchorMs + ((c.position.x - anchorX) / w * duration * fine).toLong()).coerceIn(0L, duration)
                    val bar = barIndexOf(c.position.x)
                    if (bar != lastBarIndex) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); lastBarIndex = bar }
                    onPreviewPositionChanged(localPosition)
                },
                onDragEnd = { onSeekFinished(localPosition) },
                onDragCancel = { onSeekFinished(localPosition) }) }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cy = size.height / 2f
                val prog = (localPosition.toFloat() / duration.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                val tx = (size.width * prog).coerceIn(0f, size.width)

                val threadAlpha = 1f - bloom
                if (threadAlpha > 0.01f) {
                    val th = 2.2.dp.toPx(); val r = th / 2f
                    // Straight neon track: dim line ahead, glowing line behind the playhead.
                    val dash: androidx.compose.ui.graphics.PathEffect? = null
                    drawLine(Color.White.copy(alpha = 0.18f * threadAlpha), Offset(0f, cy), Offset(size.width, cy), th * 1.3f, pathEffect = dash)
                    drawLine(AmberGlow.copy(alpha = 0.25f * threadAlpha), Offset(0f, cy), Offset(tx, cy), th * 4f, pathEffect = dash)
                    drawLine(AmberGlow.copy(alpha = 0.98f * threadAlpha), Offset(0f, cy), Offset(tx, cy), th * 1.3f, pathEffect = dash)
                    // Buffered dots: how far ahead the film is already loaded.
                    if (bufferedMs > localPosition && duration > 0L) {
                        val bx1 = size.width * (bufferedMs.toFloat() / duration).coerceIn(0f, 1f)
                        var dx = tx + 9.dp.toPx()
                        while (dx < bx1) { drawCircle(Color.White.copy(alpha = 0.55f * threadAlpha), 1.4.dp.toPx(), Offset(dx, cy)); dx += 9.dp.toPx() }
                    }
                    // Scene-break ticks.
                    cutFractions.forEach { fr ->
                        val x = size.width * fr
                        drawLine(Color.White.copy(alpha = (if (x <= tx) 0.55f else 0.30f) * threadAlpha), Offset(x, cy - 5.dp.toPx()), Offset(x, cy + 5.dp.toPx()), 1.dp.toPx())
                    }
                }
                // Dashed bridge from where the drag began to the playhead.
                if (isDragging && duration > 0L) {
                    val ox = size.width * (originMs.toFloat() / duration).coerceIn(0f, 1f)
                    drawLine(Color(0xFFFFF3D6).copy(alpha = 0.8f), Offset(ox, cy), Offset(tx, cy), 2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx())))
                    drawCircle(Color(0xFFFFF3D6).copy(alpha = 0.9f), 3.dp.toPx(), Offset(ox, cy), style = Stroke(1.5.dp.toPx()))
                }

                if (bloom > 0.01f) {
                    val barW = 3.dp.toPx(); val gap = 2.2.dp.toPx(); val step = barW + gap
                    val n = (size.width / step).toInt().coerceAtLeast(1)
                    val maxH = size.height * 0.92f
                    // Real loudness once the film has been measured; the decorative waves until then.
                    val loud = FilmLoudness.db
                    val realLevels = if (loud != null && FilmLoudness.seed == seed) FilmEnvelope.barLevels(loud, n) else null
                    val cr = androidx.compose.ui.geometry.CornerRadius(barW / 2f, barW / 2f)
                    for (i in 0 until n) {
                        val bx = i * step + barW / 2f
                        val h1 = i * 374761393 + seed * 668265263
                        val h2 = (h1 xor (h1 shr 13)) * 1274126177
                        val noise = ((h2 ushr 16) and 0xFFFF) / 65535f
                        val wave = 0.5f + 0.5f * kotlin.math.sin(i * 0.31f + (seed % 360) / 57.3f)
                        val wave2 = 0.5f + 0.5f * kotlin.math.sin(i * 0.071f + (seed % 13).toFloat())
                        val real = realLevels?.getOrNull(i) ?: -1f
                        val amp = if (real >= 0f) (0.14f + 0.86f * real).coerceIn(0.12f, 1f)
                            else (0.18f + 0.82f * (0.40f * wave + 0.25f * wave2 + 0.35f * noise)).coerceIn(0.12f, 1f)
                        val hgt = amp * maxH * bloom
                        val played = bx <= tx
                        val prox = 1f - (kotlin.math.abs(bx - tx) / (size.width * 0.30f)).coerceIn(0f, 1f)
                        val alpha = if (played) bloom * (0.55f + 0.45f * prox) else bloom * (0.20f + 0.45f * prox)
                        val barColor = if (played) AmberGlow else Color.White
                        drawRoundRect(color = barColor.copy(alpha = alpha), topLeft = Offset(bx - barW / 2f, cy - hgt / 2f), size = Size(barW, hgt), cornerRadius = cr)
                    }
                }

                val tickY = cy - bloom * (size.height * 0.40f)
                // Real markers only (intro/recap/credits bands, extra-scene rings).
                // A film with no segment data draws nothing here.
                markers.forEach { m ->
                    when (m.kind) {
                        SeekMarkerKind.SKIPPABLE, SeekMarkerKind.CREDITS -> {
                            val x0 = size.width * m.startFraction
                            val w = (size.width * (m.endFraction - m.startFraction)).coerceAtLeast(3.dp.toPx())
                            val h = 3.dp.toPx()
                            val tint = if (m.kind == SeekMarkerKind.SKIPPABLE) AmberGlow else Color(0xFF6FC3FF)
                            // Striped band so intro and credits can be seen coming.
                            val bw = w.coerceAtMost(size.width - x0)
                            val bh = 7.dp.toPx()
                            val top = tickY - bh / 2f
                            drawRect(tint.copy(alpha = 0.18f), Offset(x0, top), Size(bw, bh))
                            clipRect(x0, top, x0 + bw, top + bh) {
                                var sx = x0 - bh
                                while (sx < x0 + bw) { drawLine(tint.copy(alpha = 0.85f), Offset(sx, top + bh), Offset(sx + bh, top), 1.6.dp.toPx()); sx += 5.dp.toPx() }
                            }
                            drawRect(tint.copy(alpha = 0.7f), Offset(x0, top), Size(bw, bh), style = Stroke(0.8.dp.toPx()))
                        }
                        SeekMarkerKind.SCENE -> {
                            // A steady glowing dot: a scene is known to start here.
                            val c = Offset(size.width * m.startFraction, tickY)
                            drawCircle(color = AmberGlow.copy(alpha = 0.16f * scenePulse), radius = (9f + 4f * scenePulse).dp.toPx(), center = c)
                            drawCircle(color = AmberGlow.copy(alpha = 0.40f * scenePulse), radius = 6.5.dp.toPx(), center = c)
                            drawCircle(color = AmberCore, radius = 3.6.dp.toPx(), center = c)
                            drawCircle(color = Color(0xFFFFF3D6).copy(alpha = 0.9f), radius = 1.5.dp.toPx(), center = c)
                        }
                    }
                }

                drawCircle(color = AmberGlow.copy(alpha = 0.22f * glow), radius = 16.dp.toPx(), center = Offset(tx, cy))
                drawCircle(color = AmberCore.copy(alpha = 0.40f * glow), radius = 10.dp.toPx(), center = Offset(tx, cy))
                if (bloom > 0.01f) {
                    drawLine(color = Color(0xFFFFF3D6).copy(alpha = 0.90f * bloom), start = Offset(tx, cy - size.height * 0.46f), end = Offset(tx, cy + size.height * 0.46f), strokeWidth = 2.dp.toPx())
                }
                drawLine(color = Color(0xFFFFF3D6), start = Offset(tx, cy - 10.dp.toPx()), end = Offset(tx, cy + 10.dp.toPx()), strokeWidth = 2.dp.toPx())
                val dm = if (isDragging) 6.dp.toPx() else 5.dp.toPx()
                val diamond = androidx.compose.ui.graphics.Path().apply {
                    moveTo(tx, cy - dm); lineTo(tx + dm, cy); lineTo(tx, cy + dm); lineTo(tx - dm, cy); close()
                }
                drawPath(diamond, Color(0xFFFFF3D6))
                drawPath(diamond, Color(0xFFFF6E8C).copy(alpha = 0.9f), style = Stroke(1.2.dp.toPx()))
            }
        }
    }
}

@Composable
internal fun SeekPreviewBubble(
    isVisible: Boolean,
    bitmap: Bitmap?,
    timeText: String,
    isLandscape: Boolean,
    isLarge: Boolean,
    progress: Float,
    bottomPadding: Dp,
    deltaMs: Long = 0L,
    fine: Float = 1f,
    label: String? = null,
) {
    // Landscape has much less vertical room. Keeping the portrait-sized
    // card there made its top half occupy the visual centre even when its
    // bottom edge was correctly anchored above the seek dock.
    val pw by animateDpAsState(
        if (isLarge) (if (isLandscape) 168.dp else 210.dp)
        else (if (isLandscape) 128.dp else 160.dp),
        tween(160),
        "pw",
    )
    val ph by animateDpAsState(
        if (isLarge) (if (isLandscape) 88.dp else 118.dp)
        else (if (isLandscape) 64.dp else 90.dp),
        tween(160),
        "ph",
    )
    AnimatedVisibility(visible = isVisible, enter = fadeIn(animationSpec = tween(80)), exit = fadeOut(animationSpec = tween(80)), modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val hp = 18.dp; val aw = maxWidth - (hp * 2)
            val raw = aw * progress.coerceIn(0f, 1f) - (pw / 2); val max = aw - pw
            val safe = when { max < 0.dp -> 0.dp; raw < 0.dp -> 0.dp; raw > max -> max; else -> raw }
            Column(modifier = Modifier.align(Alignment.BottomStart).offset(x = hp + safe).padding(bottom = bottomPadding)
                .graphicsLayer { scaleX = if (isLarge) 1.02f else 0.98f; scaleY = if (isLarge) 1.02f else 0.98f; shadowElevation = if (isLarge) 18f else 10f }
                .clip(HudShape)
                .background(GlassSurfaceStrong)
                .border(1.4.dp, HudEdge, HudShape)
                .padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val fwd = deltaMs >= 0L
                val deltaColor = if (fwd) Color(0xFF7CE0C3) else Color(0xFFFF6E8C)
                Text(
                    text = (if (fwd) "+" else "−") + formatTime(kotlin.math.abs(deltaMs)),
                    color = deltaColor, fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp,
                )
                Text(
                    text = if (fine < 1f) "FINE ×" + (if (fine < 0.2f) "0.1" else if (fine < 0.4f) "0.25" else "0.5") + " · SLIDE DOWN FOR NORMAL" else (label ?: "SLIDE UP FOR FINE CONTROL"),
                    color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
                )
                Spacer(modifier = Modifier.height(5.dp))
                if (bitmap != null) Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.width(pw).height(ph).clip(androidx.compose.foundation.shape.CutCornerShape(8.dp)), contentScale = ContentScale.Crop)
                else Box(modifier = Modifier.width(pw).height(ph).clip(RoundedCornerShape(14.dp)).background(GlassSurfaceFaint), contentAlignment = Alignment.Center) { Text(text = timeText, color = AmberCore, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = timeText, color = AmberCore, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}



/**
 * Likely scene breaks, read from the soundtrack: stretches of near-silence (the quietest ~12% of
 * the film, at least 2 s long). Returned as fractions of the film, spread out and capped, so the
 * bar shows a handful of ticks and not a comb. Empty until the audio has been measured.
 */
internal fun sceneBreakFractions(db: FloatArray): List<Float> {
    val n = db.size
    if (n < 60) return emptyList()
    val measured = db.filter { !it.isNaN() }.sorted()
    if (measured.size < 60) return emptyList()
    val threshold = measured[(measured.size * 0.12f).toInt()]
    val runs = ArrayList<Pair<Int, Int>>() // start, length
    var i = 0
    while (i < n) {
        if (!db[i].isNaN() && db[i] <= threshold) {
            var j = i
            while (j < n && !db[j].isNaN() && db[j] <= threshold) j++
            if (j - i >= 2) runs.add(i to (j - i))
            i = j
        } else i++
    }
    val minGap = (n * 0.015f).toInt().coerceAtLeast(5)
    val picked = ArrayList<Int>()
    runs.sortedByDescending { it.second }.forEach { (start, len) ->
        val centre = start + len / 2
        if (picked.size < 60 && centre in (n / 100)..(n - n / 100) && picked.none { kotlin.math.abs(it - centre) < minGap }) picked.add(centre)
    }
    return picked.sorted().map { it.toFloat() / n }
}


/** Cyberpunk HUD panel: opposite corners cut hard, the other two nearly square. */
internal val HudShape = androidx.compose.foundation.shape.CutCornerShape(topStart = 16.dp, topEnd = 3.dp, bottomEnd = 16.dp, bottomStart = 3.dp)

/** Neon edge that runs amber, through rose, to sky. */
internal val HudEdge: androidx.compose.ui.graphics.Brush
    get() = androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFFFFC24D), Color(0xFFFF6E8C), Color(0xFF6FC3FF)))

/** Faint horizontal scanlines over a panel. */
internal fun Modifier.scanlines(alpha: Float = 0.06f): Modifier = drawWithContent {
    drawContent()
    var y = 0f
    val step = 3.dp.toPx()
    while (y < size.height) { drawLine(Color.White.copy(alpha = alpha), Offset(0f, y), Offset(size.width, y), 1f); y += step }
}
