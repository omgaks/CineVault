package com.sole.cinevault

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun TvShowDetailScreen(
    group: TvGroup,
    onBack: () -> Unit,
    onEpisodeClick: (VideoWithMetadata) -> Unit
) {
    val context = LocalContext.current
    val heroImage = group.backdropUrl ?: group.posterUrl

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF080808))) {

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // ── Hero backdrop ─────────────────────────────────────────────
            item {
                Box(modifier = Modifier.fillMaxWidth().height(340.dp)) {
                    if (!heroImage.isNullOrBlank()) {
                        AsyncImage(
                            model = heroImage,
                            contentDescription = group.showName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF161616)))
                    }

                    // Gradient overlay
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.60f),
                                    Color(0xFF080808)
                                )
                            )
                        )
                    )

                    // Show title + episode count at bottom
                    Column(
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = 22.dp, end = 22.dp, bottom = 20.dp)
                    ) {
                        Text(text = group.showName, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "${group.episodes.size} Episodes", color = Color(0xFFAAAAAA), fontSize = 14.sp)
                    }
                }
            }

            // ── Episode stills horizontal row ─────────────────────────────
            item {
                Column(modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)) {
                    Text(
                        text = "Episodes",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 22.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 22.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(group.episodes) { index, episode ->
                            EpisodeStillCard(
                                episode = episode,
                                episodeNumber = index + 1,
                                context = context,
                                onClick = { onEpisodeClick(episode) }
                            )
                        }
                    }
                }
            }

            // ── Full episode list ─────────────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "All Episodes",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 22.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            itemsIndexed(group.episodes) { index, episode ->
                EpisodeListRow(
                    episode = episode,
                    episodeNumber = index + 1,
                    context = context,
                    onClick = { onEpisodeClick(episode) }
                )
                Spacer(modifier = Modifier.height(2.dp))
            }

            item { Spacer(modifier = Modifier.height(100.dp)) }
        }

        // ── Back button ───────────────────────────────────────────────────
        Box(
            modifier = Modifier.align(Alignment.TopStart).padding(14.dp)
                .size(42.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

// ── Horizontal episode still card (in the top row) ───────────────────────────
@Composable
private fun EpisodeStillCard(
    episode: VideoWithMetadata,
    episodeNumber: Int,
    context: android.content.Context,
    onClick: () -> Unit
) {
    val imageUrl = episode.episodeStill ?: episode.backdropUrl ?: episode.posterUrl
    val savedPosition = remember { loadPlaybackPosition(context, episode.video.path) }
    val hasProgress = savedPosition > 5_000L
    val episodeDurationEstimate = 45L * 60L * 1000L
    val progress = if (hasProgress) (savedPosition.toFloat() / episodeDurationEstimate).coerceIn(0f, 1f) else 0f

    Column(
        modifier = Modifier.width(220.dp).clickable { onClick() }
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(124.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1A1A1A))
        ) {
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }

            // Episode number badge
            Box(
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                    .clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.70f))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(text = "E$episodeNumber", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            // Play icon overlay
            Box(
                modifier = Modifier.align(Alignment.Center).size(38.dp).clip(CircleShape)
                    .background(Color(0xFFE8A020).copy(alpha = 0.88f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(22.dp))
            }

            // Progress bar at bottom
            if (hasProgress) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                    color = Color(0xFFE8A020),
                    trackColor = Color.White.copy(alpha = 0.20f)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = cleanEpisodeSubtitle(episode, episodeNumber),
            color = Color(0xFFAAAAAA),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = cleanEpisodeTitle(episode, episodeNumber),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ── List row for each episode below the stills ───────────────────────────────
@Composable
private fun EpisodeListRow(
    episode: VideoWithMetadata,
    episodeNumber: Int,
    context: android.content.Context,
    onClick: () -> Unit
) {
    val imageUrl = episode.episodeStill ?: episode.backdropUrl ?: episode.posterUrl
    val savedPosition = remember { loadPlaybackPosition(context, episode.video.path) }
    val hasProgress = savedPosition > 5_000L
    val episodeDurationEstimate = 45L * 60L * 1000L
    val progress = if (hasProgress) (savedPosition.toFloat() / episodeDurationEstimate).coerceIn(0f, 1f) else 0f

    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
            .padding(horizontal = 22.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Still thumbnail
        Box(
            modifier = Modifier.width(110.dp).height(62.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1A1A1A))
        ) {
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            if (hasProgress) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                    color = Color(0xFFE8A020),
                    trackColor = Color.White.copy(alpha = 0.20f)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = cleanEpisodeSubtitle(episode, episodeNumber),
                color = Color(0xFFAAAAAA),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = cleanEpisodeTitle(episode, episodeNumber),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!episode.overview.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = episode.overview!!,
                    color = Color(0xFF888888),
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = Color(0xFFE8A020),
            modifier = Modifier.size(24.dp)
        )
    }
}

// ── Title cleaning helpers ────────────────────────────────────────────────────

private fun cleanEpisodeSubtitle(episode: VideoWithMetadata, episodeNumber: Int): String {
    val fileName = episode.video.name
    val match = Regex("s(\\d{1,2})e(\\d{1,2})", RegexOption.IGNORE_CASE).find(fileName)
    return if (match != null) {
        "S${match.groupValues[1].padStart(2,'0')}E${match.groupValues[2].padStart(2,'0')}"
    } else {
        episode.subtitle.substringBefore("•").trim().ifBlank { "Episode $episodeNumber" }
    }
}

private fun cleanEpisodeTitle(episode: VideoWithMetadata, episodeNumber: Int): String {
    val fromSubtitle = episode.subtitle.substringAfter("•", "").trim()
    if (fromSubtitle.isNotBlank()) return fromSubtitle
    val fileNameTitle = cleanTvTitle(episode.video.name)
    val showTitle = cleanTvTitle(episode.title)
    return when {
        fileNameTitle.isNotBlank() && !fileNameTitle.equals(showTitle, ignoreCase = true) -> fileNameTitle
        showTitle.isNotBlank() -> showTitle
        else -> "Episode $episodeNumber"
    }
}

private fun cleanTvTitle(title: String): String {
    var cleaned = title.substringAfterLast("/").substringAfterLast("\\").substringBeforeLast(".")
        .replace(Regex("\\[.*?]"), " ").replace(Regex("\\(.*?\\)"), " ")
        .replace(".", " ").replace("_", " ").replace("-", " ")
    cleaned = cleaned.replace(Regex("s\\d{1,2}e\\d{1,2}", RegexOption.IGNORE_CASE), " ")
    cleaned = cleaned.replace(Regex("\\bseason\\s*\\d+\\b", RegexOption.IGNORE_CASE), " ")
    cleaned = cleaned.replace(Regex("\\bepisode\\s*\\d+\\b", RegexOption.IGNORE_CASE), " ")
    cleaned = cleaned.replace(Regex("\\b(2160p|1080p|720p|480p|4k|uhd|hdr10\\+?|hdr|dv|dolby|vision|x264|x265|h264|h265|hevc|web|webdl|webrip|bluray|brrip|hdrip|dvdrip|aac|ddp|dts|atmos|10bit|nf|amzn|yts|rarbg|eztv|tgx|mkv|mp4|avi)\\b", RegexOption.IGNORE_CASE), " ")
    return cleaned.replace(Regex("\\b(19|20)\\d{2}\\b"), " ").replace(Regex("\\s+"), " ").trim()
}
