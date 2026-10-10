package com.sole.cinevault

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.library.formatFileSize
import com.sole.cinevault.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** File size in bytes for a plain local path; 0 for network and content:// entries. */
private suspend fun localFileSize(path: String): Long = withContext(Dispatchers.IO) {
    if (path.startsWith("smb://", ignoreCase = true) || path.startsWith("content://", ignoreCase = true)) 0L
    else try { File(path).length() } catch (_: Exception) { 0L }
}

private fun qualityBadges(fileName: String): List<String> =
    mediaBadgesFromName(fileName).filter { it == "4K" || it == "1080p" || it == "720p" || it == "HDR" || it == "HEVC" || it == "ATMOS" }

/**
 * Details view: a bigger poster with year, genres, rating logos, a short
 * overview, quality chips, size and watch progress. Posters keep 2:3.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LibraryDetailsRow(
    item: VideoWithMetadata,
    onClick: () -> Unit,
    onLongPress: (VideoWithMetadata) -> Unit = {}
) {
    val context = LocalContext.current
    val watchedPercent = getWatchedPercent(context, item)
    var sizeBytes by remember(item.video.path) { mutableStateOf(0L) }
    LaunchedEffect(item.video.path) { sizeBytes = localFileSize(item.video.path) }

    val imdb = item.imdbRating?.takeIf { it.isNotBlank() && it != "N/A" }
    val rt = item.rottenTomatoesRating?.takeIf { it.isNotBlank() && it != "N/A" }
    val tmdb = item.rating?.takeIf { it > 0.0 }
    val meta = joinMeta(extractYearFromName(item.video.name), item.genres.take(2).joinToString(", ").ifBlank { null })

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .cineCard(radius = 22.dp)
            .combinedClickable(onClick = onClick, onLongClick = { onLongPress(item) })
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .width(96.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(14.dp))
        ) {
            PosterBox(
                posterUrl = item.posterUrl,
                modifier = Modifier.fillMaxSize(),
                progress = watchedPercent,
                videoPath = item.video.path,
                episodeStill = item.episodeStill,
                backdropUrl = item.backdropUrl,
                type = item.type
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = item.title,
                color = TextBright,
                fontFamily = NewsreaderFamily,
                fontSize = CineType.Title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (meta.isNotEmpty()) {
                Text(text = meta, color = TextMuted, fontFamily = PlexMonoFamily, fontSize = CineType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (imdb != null || rt != null || tmdb != null) {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (imdb != null) ImdbCornerChip(value = imdb)
                    if (rt != null) RottenTomatoesCornerChip(value = rt)
                    if (tmdb != null) TmdbCornerChip(value = String.format("%.1f", tmdb))
                }
            }
            if (!item.overview.isNullOrBlank()) {
                Text(
                    text = item.overview,
                    color = TextMuted,
                    fontSize = CineType.Label,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val badges = qualityBadges(item.video.name)
            if (badges.isNotEmpty() || sizeBytes > 0L) {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    badges.forEach { CineBadge(text = it) }
                    if (sizeBytes > 0L) CineBadge(text = formatFileSize(sizeBytes))
                }
            }
            if (watchedPercent > 0f) {
                ProgressBar(progress = watchedPercent, compact = true)
            }
        }
    }
}

/** Compact view: small poster, title and one line of facts. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LibraryCompactRow(
    item: VideoWithMetadata,
    onClick: () -> Unit,
    onLongPress: (VideoWithMetadata) -> Unit = {}
) {
    val context = LocalContext.current
    val watchedPercent = getWatchedPercent(context, item)
    var sizeBytes by remember(item.video.path) { mutableStateOf(0L) }
    LaunchedEffect(item.video.path) { sizeBytes = localFileSize(item.video.path) }
    val facts = joinMeta(
        extractYearFromName(item.video.name),
        qualityBadges(item.video.name).firstOrNull { it == "4K" || it == "1080p" || it == "720p" },
        if (sizeBytes > 0L) formatFileSize(sizeBytes) else null
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = { onLongPress(item) })
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(44.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
        ) {
            PosterBox(
                posterUrl = item.posterUrl,
                modifier = Modifier.fillMaxSize(),
                progress = 0f,
                videoPath = item.video.path,
                episodeStill = item.episodeStill,
                backdropUrl = item.backdropUrl,
                type = item.type
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = item.title,
                color = TextBright,
                fontSize = CineType.Label,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (facts.isNotEmpty()) {
                Text(text = facts, color = TextMuted, fontFamily = PlexMonoFamily, fontSize = CineType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (watchedPercent > 0f) {
                ProgressBar(progress = watchedPercent, compact = true)
            }
        }
    }
}
