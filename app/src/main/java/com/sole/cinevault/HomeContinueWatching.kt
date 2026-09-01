package com.sole.cinevault

import com.sole.cinevault.library.*

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sole.cinevault.ui.theme.*

@Composable
fun ContinueWatchingSection(
    items: List<VideoWithMetadata>,
    mode: String,
    onModeChange: (String) -> Unit,
    onItemClick: (VideoWithMetadata) -> Unit,
    onSeeAll: () -> Unit = {}
) {
    val context = LocalContext.current

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Continue Watching",
                color = TextBright,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            // Only worth showing when there's actually more to see than the
            // 12-item cap this row already renders — a "See All" that takes
            // you somewhere with nothing new is worse than no button.
            if (items.size >= 12) {
                Text(
                    text = "See All",
                    color = AmberCore,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onSeeAll() }.padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(GlassSurface)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                SmallToggleChip(text = "List", selected = mode == "List", onClick = { onModeChange("List") })
                SmallToggleChip(text = "Grid", selected = mode == "Grid", onClick = { onModeChange("Grid") })
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (mode == "List") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(items) { item ->
                    val positionMs = loadPlaybackPosition(context, item.video.path)
                    val durationMs = loadDuration(context, item.video.path)
                    val watchedPercent = getWatchedPercent(context, item)

                    Column(modifier = Modifier.width(250.dp).clickable { onItemClick(item) }) {
                        // ── FIX: composite card instead of bare force-crop ──
                        // Previously: val image = item.backdropUrl ?: item.episodeStill ?: item.posterUrl
                        // then ONE AsyncImage with ContentScale.Crop over the
                        // whole 250x140 landscape box. When there was no
                        // backdrop/still, a portrait poster got crushed into
                        // that landscape shape — title text and faces sliced
                        // off. Now: real landscape art (backdrop/still) still
                        // crops exactly as before (unchanged, no regression).
                        // Only when falling back to a PORTRAIT poster do we
                        // switch to a composite layout — poster shown in full
                        // on the right, same poster blurred+scaled as ambient
                        // fill on the left. No second network fetch; same
                        // posterUrl used twice.
                        val landscapeImage = item.backdropUrl ?: item.episodeStill
                        val fallbackPoster = item.posterUrl

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(SpaceMid)
                        ) {
                            if (!landscapeImage.isNullOrBlank()) {
                                AsyncImage(
                                    model = landscapeImage,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (!fallbackPoster.isNullOrBlank()) {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    // Left: ambient fill — same poster image,
                                    // scaled + blurred (API 31+) or just
                                    // scaled + darkened (below API 31).
                                    Box(modifier = Modifier.weight(1.35f).fillMaxHeight()) {
                                        AsyncImage(
                                            model = fallbackPoster,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .graphicsLayer {
                                                    scaleX = 1.4f; scaleY = 1.4f
                                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                        renderEffect = android.graphics.RenderEffect
                                                            .createBlurEffect(
                                                                40f, 40f,
                                                                android.graphics.Shader.TileMode.CLAMP
                                                            )
                                                            .asComposeRenderEffect()
                                                    }
                                                }
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.35f))
                                        )
                                    }
                                    // Right: the real poster, uncropped —
                                    // title logo and face stay intact.
                                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                        AsyncImage(
                                            model = fallbackPoster,
                                            contentDescription = null,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }

                            // Scrim just behind the timestamps — unchanged
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.60f))))
                            )

                            Text(
                                text = formatClock(positionMs),
                                color = TextBright, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 8.dp)
                            )
                            if (durationMs > 0L) {
                                Text(
                                    text = formatClock(durationMs),
                                    color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 8.dp)
                                )
                            }

                            // Thin progress line hugging the bottom edge — unchanged
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .background(Color.White.copy(alpha = 0.18f))
                            ) {
                                Box(modifier = Modifier.fillMaxWidth(watchedPercent.coerceIn(0f, 1f)).fillMaxHeight().background(AmberGlow))
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = item.title,
                            color = TextBright, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        } else {
            val gridItems = items.take(6)
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                gridItems.chunked(3).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowItems.forEach { item ->
                            val watchedPercent = getWatchedPercent(context, item)
                            ResumePosterBox(
                                item = item,
                                modifier = Modifier.weight(1f),
                                progress = watchedPercent,
                                onClick = { onItemClick(item) }
                            )
                        }
                        repeat(3 - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ResumePosterBox(
    item: VideoWithMetadata,
    modifier: Modifier,
    progress: Float,
    onClick: () -> Unit
) {
    Column(modifier = modifier.clickable { onClick() }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SpaceMid)
        ) {
            val imageModel = item.posterUrl ?: item.video.path
            if (imageModel.isNotBlank()) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null, tint = TextFaint, modifier = Modifier.size(30.dp))
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.18f))
            ) {
                Box(modifier = Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(AmberGlow))
            }

            RatingBadgeStack(item = item, modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text(text = item.title, color = TextBright, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun ProgressBar(progress: Float, compact: Boolean = false) {
    val barHeight = if (compact) 3.dp else 4.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(barHeight)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.18f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(AmberGlow)
        )
    }
}

@Composable
internal fun SmallToggleChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) AmberGlow.copy(alpha = 0.85f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = if (selected) Color.Black else TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

// Quick play button overlay — gold circle, kept from CV1
@Composable
internal fun QuickPlayButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(AmberGlow.copy(alpha = 0.92f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = "Play",
            tint = Color.Black,
            modifier = Modifier.size(22.dp)
        )
    }
}

// ── Poster corner chips — small glass badges, screenshot style ───────────────

@Composable
internal fun ImdbCornerChip(value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.62f))
            .padding(horizontal = 5.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(Color(0xFFF5C518)).padding(horizontal = 3.dp, vertical = 1.dp)
        ) {
            Text(text = "IMDb", color = Color.Black, fontSize = 6.sp, fontWeight = FontWeight.Black)
        }
        Spacer(modifier = Modifier.width(3.dp))
        Text(text = value, color = TextBright, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun TmdbCornerChip(value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.62f))
            .padding(horizontal = 5.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painter = painterResource(R.drawable.ic_tmdb), contentDescription = "TMDB", modifier = Modifier.height(8.dp), contentScale = ContentScale.Fit)
        Spacer(modifier = Modifier.width(3.dp))
        Text(text = value, color = TextBright, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun RottenTomatoesCornerChip(value: String, modifier: Modifier = Modifier) {
    val percent = value.replace("%", "").trim().toIntOrNull() ?: 0
    val isFresh = percent >= 60
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.62f))
            .padding(horizontal = 5.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ic_rotten_tomatoes),
            contentDescription = "Rotten Tomatoes",
            modifier = Modifier.height(9.dp),
            contentScale = ContentScale.Fit,
            colorFilter = if (!isFresh) androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFF8BC34A)) else null
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(text = value, color = TextBright, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

// Shared vertical stack of whichever rating badges the item actually has —
// IMDb, Rotten Tomatoes, TMDB. Used by every poster-forward card (Library
// grid, Home Featured, Continue Watching grid mode, Search results) so
// ratings show up consistently everywhere a poster is the primary visual,
// not just on the Detail screen. Renders nothing if the item has no ratings
// at all (e.g. Select-Folder items, which never go through TMDB enrichment).
@Composable
internal fun RatingBadgeStack(item: VideoWithMetadata, modifier: Modifier = Modifier) {
    val imdb = item.imdbRating?.takeIf { it.isNotBlank() && it != "N/A" }
    val rt = item.rottenTomatoesRating?.takeIf { it.isNotBlank() && it != "N/A" }
    val tmdb = item.rating?.takeIf { it > 0.0 }
    if (imdb == null && rt == null && tmdb == null) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (imdb != null) ImdbCornerChip(value = imdb)
        if (rt != null) RottenTomatoesCornerChip(value = rt)
        if (tmdb != null) TmdbCornerChip(value = String.format("%.1f", tmdb))
    }
}

@Composable
internal fun CornerChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = TextBright,
        fontSize = 8.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.62f))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    )
}
