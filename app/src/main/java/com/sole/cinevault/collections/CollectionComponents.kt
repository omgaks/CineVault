package com.sole.cinevault.collections

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sole.cinevault.VideoWithMetadata
import com.sole.cinevault.tvmode.TvFocusableSlot
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.AmberDeep
import com.sole.cinevault.ui.theme.AmberEmber
import com.sole.cinevault.ui.theme.AmberGlow
import com.sole.cinevault.ui.theme.GlassSurfaceStrong
import com.sole.cinevault.ui.theme.SpaceDeep
import com.sole.cinevault.ui.theme.SpaceMid
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextFaint
import com.sole.cinevault.ui.theme.TextMuted
import com.sole.cinevault.ui.theme.glassPanel

internal fun tmdbImageUrl(path: String?, size: String = "w342"): String? =
    path?.takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/$size$it" }

/** A dashed rounded outline — Compose has no built-in dashed border. */
internal fun Modifier.dashedBorder(color: Color, radius: Dp, strokeWidth: Dp = 2.dp): Modifier =
    this.drawBehind {
        val sw = strokeWidth.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(sw / 2f, sw / 2f),
            size = Size(this.size.width - sw, this.size.height - sw),
            cornerRadius = CornerRadius(radius.toPx()),
            style = Stroke(width = sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
        )
    }

@Composable
internal fun ProgressRing(progress: Float, owned: Int, total: Int, diameter: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 6.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(
                color = Color.White.copy(alpha = 0.12f), startAngle = -90f, sweepAngle = 360f,
                useCenter = false, topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            if (progress > 0f) {
                drawArc(
                    color = AmberGlow, startAngle = -90f, sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false, topLeft = Offset(inset, inset), size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$owned", color = TextBright, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("of $total", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun AmberPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(AmberCore, AmberGlow)))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
internal fun GlassPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .glassPanel(50.dp, GlassSurfaceStrong)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = AmberCore, modifier = Modifier.size(18.dp))
        Text(text, color = TextBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun LegendRow(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(12.dp).border(2.dp, AmberGlow, RoundedCornerShape(4.dp)))
            Text("In library", color = TextMuted, fontSize = 12.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(12.dp).dashedBorder(AmberDeep, 4.dp))
            Text("To find", color = TextMuted, fontSize = 12.sp)
        }
    }
}

/** One poster in the ordered strip: owned (amber, playable), missing (ghost), or upcoming. */
@Composable
internal fun SlotTile(
    slot: CollectionSlot,
    watchedFraction: Float,
    wanted: Boolean,
    isTelevision: Boolean,
    onOpenOwned: (VideoWithMetadata) -> Unit,
    onPlayOwned: (VideoWithMetadata) -> Unit,
    onOpenMissing: (CollectionSlot) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    val owned = slot.owned
    val onActivate: () -> Unit = { if (owned != null) onOpenOwned(owned) else onOpenMissing(slot) }
    val posterUrl = owned?.posterUrl?.takeIf { it.isNotBlank() } ?: tmdbImageUrl(slot.part.posterPath)

    Column(modifier) {
        TvFocusableSlot(isTelevision = isTelevision, shape = shape, onActivate = onActivate) {
            when (slot.status) {
                SlotStatus.OWNED -> Box(
                    Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                        .shadow(10.dp, shape, ambientColor = AmberGlow, spotColor = AmberGlow)
                        .clip(shape).background(SpaceMid)
                        .border(2.dp, AmberGlow, shape)
                        .clickable(onClick = onActivate)
                ) {
                    if (posterUrl != null) {
                        AsyncImage(model = posterUrl, contentDescription = slot.part.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        TileTitle(slot.part.title, AmberCore)
                    }
                    if (owned != null) {
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(6.dp).size(34.dp).clip(CircleShape)
                                .background(GlassSurfaceStrong).clickable { onPlayOwned(owned) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play ${slot.part.title}", tint = TextBright, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (watchedFraction > 0f) {
                        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.15f)))
                        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(watchedFraction.coerceIn(0f, 1f)).height(4.dp).background(AmberCore))
                    }
                }

                SlotStatus.MISSING, SlotStatus.UPCOMING -> {
                    val upcoming = slot.status == SlotStatus.UPCOMING
                    val edge = when {
                        upcoming -> TextFaint
                        slot.isGap -> AmberCore
                        else -> AmberDeep
                    }
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                            .clip(shape).background(SpaceDeep)
                            .dashedBorder(edge, 16.dp)
                            .clickable(onClick = onActivate)
                    ) {
                        if (posterUrl != null) {
                            AsyncImage(
                                model = posterUrl, contentDescription = null, contentScale = ContentScale.Crop,
                                alpha = 0.42f,
                                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            TileTitle(slot.part.title, TextMuted)
                        }
                        Box(
                            Modifier.align(Alignment.Center).size(44.dp).clip(CircleShape)
                                .background(GlassSurfaceStrong)
                                .border(1.dp, AmberCore.copy(alpha = 0.45f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (upcoming) {
                                Text(slot.year ?: "TBA", color = AmberCore, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            } else {
                                Icon(Icons.Rounded.Add, contentDescription = "Details for ${slot.part.title}", tint = AmberCore, modifier = Modifier.size(22.dp))
                            }
                        }
                        if (wanted) {
                            Icon(
                                Icons.Rounded.Bookmark, contentDescription = "On your wantlist", tint = AmberCore,
                                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        Column(Modifier.padding(top = 8.dp)) {
            val heading = if (slot.number != null) "${slot.number} · ${slot.year ?: "TBA"}" else "Upcoming"
            Text(heading, color = TextBright, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(slotStateText(slot, watchedFraction), color = TextMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

internal fun slotStateText(slot: CollectionSlot, watchedFraction: Float): String = when (slot.status) {
    SlotStatus.OWNED -> when {
        watchedFraction >= 0.9f -> "Watched"
        watchedFraction > 0f -> "In library · ${(watchedFraction * 100).toInt()}%"
        else -> "In library"
    }
    SlotStatus.MISSING ->
        if (slot.isGap) "Missing between ${slot.gapAfter} and ${slot.gapBefore}" else "Not in library"
    SlotStatus.UPCOMING ->
        slot.year?.let { "Coming $it" } ?: "Release date TBA"
}

@Composable
private fun TileTitle(title: String, color: Color) {
    Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.BottomStart) {
        Text(title.uppercase(), color = color, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 13.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
}

/** Hero backdrop that fades into the page background. */
@Composable
internal fun CollectionHero(backdropUrl: String?, modifier: Modifier = Modifier) {
    Box(modifier) {
        if (!backdropUrl.isNullOrBlank()) {
            AsyncImage(model = backdropUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(AmberEmber, SpaceDeep))))
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.30f to Color.Transparent, 1f to com.sole.cinevault.ui.theme.SpaceBlack)
            )
        )
    }
}
