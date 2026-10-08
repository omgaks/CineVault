package com.sole.cinevault.collections

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sole.cinevault.metadata.loadMetadataFetchEnabled
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.AmberDeep
import com.sole.cinevault.ui.theme.AmberGlow
import com.sole.cinevault.ui.theme.GlassSurfaceStrong
import com.sole.cinevault.ui.theme.SpaceDeep
import com.sole.cinevault.ui.theme.SpaceMid
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextMuted
import com.sole.cinevault.ui.theme.glassPanel

/**
 * Bottom sheet for a film you don't own (or that isn't out yet).
 * Information only — trailer, where it streams, wantlist. Nothing is downloaded.
 * Runtime / rating / providers load lazily and only when "Fetch online metadata" is on.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun MissingFilmSheet(
    slot: CollectionSlot,
    totalReleased: Int?,
    isWanted: Boolean,
    onToggleWanted: () -> Unit,
    onSearchLibrary: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val fetchEnabled = remember { loadMetadataFetchEnabled(context) }

    var extras by remember(slot.part.tmdbId) { mutableStateOf<FilmExtras?>(null) }
    var extrasDone by remember(slot.part.tmdbId) { mutableStateOf(false) }
    LaunchedEffect(slot.part.tmdbId) {
        extras = CollectionRepository.filmExtras(context, slot.part.tmdbId)
        extrasDone = true
    }

    val upcoming = slot.status == SlotStatus.UPCOMING
    val chipText = when {
        upcoming -> "UPCOMING"
        slot.isGap -> "MISSING BETWEEN ${slot.gapAfter} AND ${slot.gapBefore}"
        else -> "NOT IN YOUR LIBRARY"
    }
    val meta = listOfNotNull(
        slot.year,
        slot.number?.let { n -> if (totalReleased != null) "Film $n of $totalReleased" else "Film $n" },
        extras?.runtimeMinutes?.let { "${it / 60}h ${it % 60}m" },
        extras?.rating?.let { "★ ${"%.1f".format(it)} TMDB" }
    ).joinToString(" · ")
    val overview = extras?.overview ?: slot.part.overview
    val poster = tmdbImageUrl(slot.part.posterPath)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SpaceMid,
        contentColor = TextBright,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.25f)) }
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier.size(width = 104.dp, height = 156.dp).clip(RoundedCornerShape(16.dp))
                        .background(SpaceDeep).dashedBorder(if (slot.isGap) AmberCore else AmberDeep, 16.dp)
                ) {
                    if (poster != null) {
                        AsyncImage(
                            model = poster, contentDescription = slot.part.title, contentScale = ContentScale.Crop,
                            alpha = 0.6f,
                            colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        chipText, color = AmberCore, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(AmberGlow.copy(alpha = 0.14f))
                            .border(1.dp, AmberCore.copy(alpha = 0.5f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                    Text(slot.part.title, color = TextBright, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 24.sp)
                    if (meta.isNotBlank()) Text(meta, color = TextMuted, fontSize = 13.sp)
                }
            }

            if (!overview.isNullOrBlank()) {
                Text(overview, color = TextMuted, fontSize = 13.sp, lineHeight = 20.sp)
            }

            // Where to watch — only when lookups are on.
            if (fetchEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("WHERE TO WATCH", color = AmberCore, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                    val providers = extras?.providers.orEmpty()
                    when {
                        !extrasDone -> Text("Looking up…", color = TextMuted, fontSize = 12.sp)
                        extras == null -> Text("Couldn't load details. Check your connection.", color = TextMuted, fontSize = 12.sp)
                        providers.isEmpty() -> Text("No streaming info for your region.", color = TextMuted, fontSize = 12.sp)
                        else -> {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                providers.forEach { name ->
                                    Text(
                                        name, color = TextBright, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.heightIn(min = 36.dp).glassPanel(50.dp, GlassSurfaceStrong)
                                            .padding(horizontal = 14.dp, vertical = 9.dp)
                                    )
                                }
                            }
                            Text("Provider data by JustWatch, via TMDB.", color = TextMuted, fontSize = 11.sp)
                        }
                    }
                }
            } else {
                Text("Online lookups are off, so trailer and streaming info aren't shown.", color = TextMuted, fontSize = 12.sp)
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(50))
                        .background(if (isWanted) Color.Transparent else AmberGlow)
                        .border(1.5.dp, AmberGlow, RoundedCornerShape(50))
                        .clickable(onClick = onToggleWanted),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    val tint = if (isWanted) AmberCore else Color.Black
                    Icon(if (isWanted) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                    Text(
                        if (isWanted) "On your wantlist" else "Add to wantlist",
                        color = tint, fontSize = 15.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val trailerKey = extras?.trailerKey
                    SheetAction(
                        text = "Watch trailer", icon = Icons.Rounded.PlayArrow, enabled = trailerKey != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        trailerKey?.let { key ->
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$key"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                    }
                    SheetAction(text = "Search my library", icon = Icons.Rounded.Search, enabled = true, modifier = Modifier.weight(1f), onClick = onSearchLibrary)
                }
            }
        }
    }
}

@Composable
private fun SheetAction(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier.heightIn(min = 48.dp).glassPanel(50.dp, GlassSurfaceStrong)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        val tint = if (enabled) TextBright else TextMuted.copy(alpha = 0.5f)
        Icon(icon, contentDescription = null, tint = if (enabled) AmberCore else tint, modifier = Modifier.size(16.dp))
        Text(text, color = tint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
    }
}
