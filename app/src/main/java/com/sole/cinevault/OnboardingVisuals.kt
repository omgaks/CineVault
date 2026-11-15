package com.sole.cinevault

import android.provider.Settings
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.HdrOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tv
import coil.compose.AsyncImage
import com.sole.cinevault.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One icon per tutorial guide, so every pill and card looks the part. */
internal fun guideIcon(id: String): ImageVector = when (id) {
    "gestures" -> Icons.Rounded.TouchApp
    "metadata" -> Icons.Rounded.Movie
    "subtitles" -> Icons.Rounded.Subtitles
    "picture" -> Icons.Rounded.HdrOn
    "glasses" -> Icons.Rounded.Devices
    "audio" -> Icons.Rounded.SurroundSound
    "network" -> Icons.Rounded.Radar
    "tv" -> Icons.Rounded.Tv
    "scene" -> Icons.Rounded.AutoAwesome
    "secret" -> Icons.Rounded.Lock
    "voice" -> Icons.Rounded.Mic
    else -> Icons.Rounded.AutoAwesome
}

/** False when the person has switched animations off in Android's developer settings. */
@Composable
internal fun rememberMotionAllowed(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
        }.getOrDefault(true)
    }
}

private const val WALL_COLUMNS = 4
private const val WALL_ROWS = 2

/**
 * The scan screen's poster wall: shimmering gaps that turn into the person's real posters
 * as they are found. Films that have no poster yet appear as titled tiles in a gel colour.
 */
@Composable
internal fun PosterWall(videos: List<VideoWithMetadata>, modifier: Modifier = Modifier) {
    val motion = rememberMotionAllowed()
    val slots = remember(videos) {
        posterWallSlots(videos.map { it.title to it.posterUrl }, WALL_COLUMNS * WALL_ROWS)
    }
    val transition = rememberInfiniteTransition(label = "wallShimmer")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "sweep"
    )
    val sweepNow = if (motion) sweep else 0.5f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(0.7f to Color.Black, 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn
                )
            }
            .semantics { contentDescription = "Your films appearing as they are found" },
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        for (row in 0 until WALL_ROWS) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until WALL_COLUMNS) {
                    val index = row * WALL_COLUMNS + col
                    Box(modifier = Modifier.weight(1f)) {
                        WallTile(slots.getOrNull(index), sweepNow, index)
                    }
                }
            }
        }
    }
}

@Composable
private fun WallTile(slot: WallSlot?, sweep: Float, index: Int) {
    val shape = RoundedCornerShape(14.dp)
    val pop by animateFloatAsState(
        targetValue = if (slot == null) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "pop"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .graphicsLayer { scaleX = pop; scaleY = pop }
            .clip(shape)
            .background(CineGlassFill)
            .border(1.dp, CineGlassEdge, shape)
    ) {
        Crossfade(targetState = slot, animationSpec = tween(520), label = "tile") { s ->
            when {
                s == null -> {
                    val phase = (sweep + index * 0.09f) % 1f
                    Box(
                        Modifier
                            .fillMaxSize()
                            .drawBehind {
                                val w = size.width
                                val x = -w + phase * 3f * w
                                drawRect(
                                    Brush.linearGradient(
                                        colors = listOf(Color.Transparent, GelGold.copy(alpha = 0.14f), Color.Transparent),
                                        start = Offset(x, 0f),
                                        end = Offset(x + w, size.height * 0.4f)
                                    )
                                )
                            }
                    )
                }
                !s.posterUrl.isNullOrBlank() -> AsyncImage(
                    model = s.posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                else -> {
                    val gel = TutorialGels[(s.title.hashCode() and 0x7fffffff) % TutorialGels.size]
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(listOf(gel.copy(alpha = 0.38f), SpaceBlack))),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(6.dp)) {
                            Icon(Icons.Rounded.Movie, contentDescription = null, tint = gel, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.height(6.dp))
                            Text(
                                s.title,
                                color = TextBright,
                                fontFamily = NewsreaderFamily,
                                fontSize = CineType.Caption,
                                textAlign = TextAlign.Center,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "128 films found" that counts up as the scan discovers more. */
@Composable
internal fun FoundCounter(count: Int, scanning: Boolean, modifier: Modifier = Modifier) {
    val shown by animateIntAsState(count, animationSpec = tween(600), label = "found")
    val text = when {
        count == 0 && scanning -> "Looking..."
        count == 0 -> ""
        count == 1 -> "$shown film found"
        else -> "$shown films found"
    }
    if (text.isNotEmpty()) {
        Text(
            text,
            color = GelMint,
            fontSize = CineType.Label,
            fontWeight = FontWeight.SemiBold,
            modifier = modifier
        )
    }
}

/**
 * The feature tour: guide pills orbit a glowing core. The pill at the front is the one
 * being described. Tap a pill to bring it forward and hold it; tap the held pill to
 * let the orbit carry on. With animations switched off the orbit simply stays still.
 */
@Composable
internal fun FeatureOrbit(guides: List<TutorialGuide>, modifier: Modifier = Modifier) {
    if (guides.isEmpty()) return
    val motion = rememberMotionAllowed()
    val n = guides.size
    val step = 360f / n
    var angle by remember { mutableFloatStateOf(90f) }
    var held by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(motion) {
        if (!motion) return@LaunchedEffect
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (last != 0L && !held) {
                    angle = (angle + (now - last) / 1_000_000_000f * 9f) % 360f
                }
                last = now
            }
        }
    }

    val front = orbitFrontIndex(angle, n)
    val glow = rememberInfiniteTransition(label = "coreGlow")
    val glowScale by glow.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
        label = "glowScale"
    )

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val density = LocalDensity.current
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().height(290.dp),
            contentAlignment = Alignment.Center
        ) {
            val rx = with(density) { (maxWidth / 2 - 64.dp).coerceAtLeast(60.dp).toPx() }
            val ry = with(density) { 92.dp.toPx() }
            val frontGel = TutorialGels[guides[front].gelIndex % TutorialGels.size]

            // Glow behind the core, tinted by whichever pill is at the front.
            Box(
                Modifier
                    .size(220.dp)
                    .graphicsLayer { scaleX = glowScale; scaleY = glowScale }
                    .background(
                        Brush.radialGradient(listOf(frontGel.copy(alpha = 0.34f), Color.Transparent)),
                        CircleShape
                    )
            )
            // The orbit path.
            Canvas(Modifier.fillMaxSize()) {
                drawOval(
                    color = Color.White.copy(alpha = 0.10f),
                    topLeft = Offset(center.x - rx, center.y - ry),
                    size = Size(rx * 2, ry * 2),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 12f)))
                )
            }
            // The core.
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(SpaceBlack)
                    .border(1.dp, GelGold.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Movie, contentDescription = null, tint = GelGold, modifier = Modifier.size(32.dp))
            }
            // Pills, back to front so nearer ones overlap farther ones.
            val order = guides.indices.sortedBy { sin(Math.toRadians((angle + it * step).toDouble())) }
            order.forEach { i ->
                val theta = Math.toRadians((angle + i * step).toDouble())
                val depth = ((sin(theta) + 1.0) / 2.0).toFloat()
                val scale = 0.78f + 0.30f * depth
                val fade = 0.45f + 0.55f * depth
                OrbitPill(
                    guide = guides[i],
                    selected = i == front,
                    modifier = Modifier
                        .offset { IntOffset((rx * cos(theta)).roundToInt(), (ry * sin(theta)).roundToInt()) }
                        .graphicsLayer { scaleX = scale; scaleY = scale; alpha = fade },
                    onClick = {
                        if (held && i == front) {
                            held = false
                        } else {
                            held = true
                            val turn = orbitTurnToFront(angle, i, n)
                            val from = angle
                            scope.launch {
                                androidx.compose.animation.core.animate(
                                    initialValue = 0f,
                                    targetValue = turn,
                                    animationSpec = spring(stiffness = Spring.StiffnessLow)
                                ) { value, _ -> angle = (from + value + 360f) % 360f }
                            }
                        }
                    }
                )
            }
        }

        val guide = guides[front]
        val gel = TutorialGels[guide.gelIndex % TutorialGels.size]
        Crossfade(targetState = guide, animationSpec = tween(380), label = "orbitCard") { g ->
            val c = TutorialGels[g.gelIndex % TutorialGels.size]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .cineCard(radius = 26.dp, gel = c)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(c.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(guideIcon(g.id), contentDescription = null, tint = c, modifier = Modifier.size(26.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(g.title, color = c, fontFamily = NewsreaderFamily, fontSize = CineType.Title)
                    Text(g.tagline, color = TextBright, fontSize = CineType.Label)
                }
            }
        }
        Text(
            text = if (held) "Held. Tap it again to let the pills drift." else "Tap a pill to hold it.",
            color = gel.copy(alpha = 0.8f),
            fontSize = CineType.Caption,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun OrbitPill(
    guide: TutorialGuide,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val gel = TutorialGels[guide.gelIndex % TutorialGels.size]
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(CircleShape)
            .background(if (selected) gel.copy(alpha = 0.20f) else CineGlassFill)
            .border(1.dp, if (selected) gel else gel.copy(alpha = 0.45f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp)
            .semantics { contentDescription = guide.title },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(guideIcon(guide.id), contentDescription = null, tint = gel, modifier = Modifier.size(18.dp))
        Text(guide.title, color = if (selected) TextBright else gel, fontSize = CineType.Label, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
