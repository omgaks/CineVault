package com.sole.cinevault

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sole.cinevault.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

private val TonightGels = listOf(GelMint, GelSky, GelViolet, GelCoral)

/** One film CineVault already knows the length of, in whole minutes. */
private data class MeasuredFilm(val item: VideoWithMetadata, val minutes: Int)

/**
 * "How long tonight?": pick how much time you have and see which films fit.
 * Only films whose length CineVault already knows (it learns a length when a
 * film has been opened in the player) can be counted, and the card says so.
 */
@Composable
internal fun HomeTonightSection(
    videos: List<VideoWithMetadata>,
    dayPart: String,
    isWide: Boolean,
    onItemClick: (VideoWithMetadata) -> Unit,
    onPlayClick: (VideoWithMetadata) -> Unit
) {
    val context = LocalContext.current
    var budget by rememberSaveable { mutableStateOf(TONIGHT_DEFAULT_MINUTES) }

    val measured by produceState(initialValue = emptyList<MeasuredFilm>(), videos) {
        value = withContext(Dispatchers.IO) {
            videos.filter { it.type != "tv" }.mapNotNull { video ->
                val ms = loadDuration(context, video.video.path)
                if (ms > 60_000L) MeasuredFilm(video, (ms / 60_000L).toInt()) else null
            }
        }
    }
    val fits = fitsWithin(measured, budget) { it.minutes }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                text = "How long tonight?",
                color = TextBright,
                fontFamily = NewsreaderFamily,
                fontSize = CineType.Section,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "FITS YOUR EVENING",
                color = TextFaint,
                fontFamily = PlexMonoFamily,
                fontSize = CineType.Caption,
                letterSpacing = 1.4.sp
            )
        }

        val gauge: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TonightGauge(
                    budgetMinutes = budget,
                    dayPart = dayPart,
                    filmsFit = fits.size,
                    bestRuntime = fits.firstOrNull()?.first?.minutes
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TONIGHT_OPTIONS_MINUTES.forEach { option ->
                        CineChip(
                            text = tonightOptionLabel(option),
                            selected = option == budget,
                            onClick = { budget = option },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        val fitList: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (measured.isEmpty()) {
                    Text(
                        text = "CineVault learns how long a film is the first time you open it. Play a few films and this gauge will start suggesting what fits your evening.",
                        color = TextMuted,
                        fontSize = CineType.Label,
                        lineHeight = 19.sp
                    )
                } else if (fits.isEmpty()) {
                    Text(
                        text = "Nothing CineVault has measured fits ${formatBudgetMinutes(budget)}. Try a longer evening.",
                        color = TextMuted,
                        fontSize = CineType.Label,
                        lineHeight = 19.sp
                    )
                } else {
                    fits.take(3).forEachIndexed { index, (film, spare) ->
                        TonightFitRow(
                            film = film,
                            spareMinutes = spare,
                            gel = TonightGels[index % TonightGels.size],
                            onOpen = { onItemClick(film.item) },
                            onPlay = { onPlayClick(film.item) }
                        )
                    }
                    if (measured.size < videos.count { it.type != "tv" }) {
                        Text(
                            text = "Counting ${measured.size} films whose length is known.",
                            color = TextFaint,
                            fontSize = CineType.Caption
                        )
                    }
                }
            }
        }

        if (isWide) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Top) {
                gauge(Modifier.weight(1f))
                fitList(Modifier.weight(1f))
            }
        } else {
            gauge(Modifier.fillMaxWidth())
            fitList(Modifier.fillMaxWidth())
        }
    }
}

private fun tonightOptionLabel(minutes: Int): String = when (minutes) {
    60 -> "1h"
    90 -> "1.5h"
    120 -> "2h"
    180 -> "3h"
    240 -> "4h"
    else -> formatBudgetMinutes(minutes).lowercase()
}

@Composable
private fun TonightFitRow(
    film: MeasuredFilm,
    spareMinutes: Int,
    gel: Color,
    onOpen: () -> Unit,
    onPlay: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .cineCard(radius = 22.dp)
            .clickable(onClick = onOpen)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 58.dp, height = 87.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SpaceMid)
        ) {
            val poster = film.item.posterUrl
            if (!poster.isNullOrBlank()) {
                AsyncImage(
                    model = poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(87.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = film.item.title,
                color = TextBright,
                fontFamily = NewsreaderFamily,
                fontSize = CineType.Title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatBudgetMinutes(film.minutes),
                color = TextMuted,
                fontFamily = PlexMonoFamily,
                fontSize = CineType.Caption
            )
            Text(
                text = spareText(spareMinutes),
                color = gel,
                fontSize = CineType.Caption,
                fontWeight = FontWeight.Bold
            )
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(AmberCore.copy(alpha = 0.12f))
                .border(1.dp, AmberCore.copy(alpha = 0.5f), CircleShape)
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Play ${film.item.title}",
                tint = AmberCore,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * The HUD-style gauge: a ring of ticks that lights up as the evening gets
 * longer, with the time in the middle. Drawn in the app, no images.
 */
@Composable
private fun TonightGauge(
    budgetMinutes: Int,
    dayPart: String,
    filmsFit: Int,
    bestRuntime: Int?
) {
    val fraction = tonightGaugeFraction(budgetMinutes)
    val frame = Brush.linearGradient(listOf(GelGold, GelRose, GelViolet))
    val shape = CutCornerShape(topStart = 22.dp, bottomEnd = 22.dp)
    val label = formatBudgetMinutes(budgetMinutes)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.36f)
            .clip(shape)
            .background(Color(0xFF070912))
            .border(1.dp, frame, shape)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val cx = size.width / 2f
            val cy = size.height * 0.54f
            val radius = minOf(size.width * 0.30f, size.height * 0.40f)
            drawCircle(
                color = GelSky.copy(alpha = 0.40f),
                radius = radius * 0.92f,
                center = Offset(cx, cy),
                style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 9.dp.toPx())))
            )
            drawCircle(
                color = GelRose.copy(alpha = 0.25f),
                radius = radius * 0.78f,
                center = Offset(cx, cy),
                style = Stroke(width = 1.dp.toPx())
            )
            val ticks = 54
            val lit = (fraction * (ticks - 1)).toInt()
            for (i in 0 until ticks) {
                val t = i / (ticks - 1).toFloat()
                val angle = Math.toRadians((135.0 + 270.0 * t))
                val major = i % 9 == 0
                val inner = radius * 1.04f
                val outer = radius * (if (major) 1.26f else 1.16f)
                val dx = cos(angle).toFloat()
                val dy = sin(angle).toFloat()
                val color = when {
                    i > lit -> Color.White.copy(alpha = 0.14f)
                    t < 0.5f -> GelGold
                    else -> GelRose
                }
                drawLine(
                    color = color,
                    start = Offset(cx + dx * inner, cy + dy * inner),
                    end = Offset(cx + dx * outer, cy + dy * outer),
                    strokeWidth = (if (major) 3.dp else 2.dp).toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(8.dp).background(GelGold))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "TIME BUDGET",
                color = GelGold,
                fontFamily = PlexMonoFamily,
                fontSize = CineType.Caption,
                letterSpacing = 1.6.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "// $dayPart MODE",
                color = GelSky,
                fontFamily = PlexMonoFamily,
                fontSize = CineType.Caption,
                letterSpacing = 1.2.sp
            )
        }

        Column(
            modifier = Modifier.align(Alignment.Center).offset(y = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Two offset copies behind the main text give the glitch fringe.
                GaugeValueText(text = label, color = GelSky.copy(alpha = 0.75f), modifier = Modifier.offset(x = (-2).dp))
                GaugeValueText(text = label, color = GelRose.copy(alpha = 0.75f), modifier = Modifier.offset(x = 2.dp))
                GaugeValueText(text = label, color = TextBright)
            }
            Text(
                text = "FREE TONIGHT",
                color = TextMuted,
                fontFamily = PlexMonoFamily,
                fontSize = CineType.Caption,
                letterSpacing = 2.sp
            )
        }

        Column(modifier = Modifier.align(Alignment.BottomStart).padding(start = 18.dp, bottom = 12.dp)) {
            Text(text = "FILMS FIT", color = TextFaint, fontFamily = PlexMonoFamily, fontSize = CineType.Caption, letterSpacing = 1.2.sp)
            Text(text = "$filmsFit", color = GelMint, fontFamily = PlexMonoFamily, fontWeight = FontWeight.Medium, fontSize = CineType.Title)
        }
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(text = "BEST SYNC", color = TextFaint, fontFamily = PlexMonoFamily, fontSize = CineType.Caption, letterSpacing = 1.2.sp)
            Text(
                text = bestRuntime?.let { formatBudgetMinutes(it) } ?: "--",
                color = GelRose,
                fontFamily = PlexMonoFamily,
                fontWeight = FontWeight.Medium,
                fontSize = CineType.Title
            )
        }
    }
}

@Composable
private fun GaugeValueText(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = color,
        fontFamily = PlexMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 40.sp,
        modifier = modifier
    )
}
