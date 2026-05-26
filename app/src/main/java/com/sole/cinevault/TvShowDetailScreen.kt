package com.sole.cinevault

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter

@Composable
fun TvShowDetailScreen(
    group: TvGroup,
    onBack: () -> Unit,
    onEpisodeClick: (VideoWithMetadata) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item {
            Row {
                Text(
                    text = "← Back",
                    color = Color.White,
                    fontSize = 18.sp,
                    modifier = Modifier.clickable { onBack() }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = group.showName,
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${group.episodes.size} Episodes",
                color = Color.Gray,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(18.dp))
        }

        itemsIndexed(group.episodes) { index, episode ->
            EpisodePosterCard(
                episode = episode,
                episodeNumber = index + 1,
                onClick = { onEpisodeClick(episode) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(90.dp))
        }
    }
}

@Composable
private fun EpisodePosterCard(
    episode: VideoWithMetadata,
    episodeNumber: Int,
    onClick: () -> Unit
) {
    val imageUrl = episode.episodeStill ?: episode.backdropUrl ?: episode.posterUrl

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF151515))
            .clickable { onClick() }
    ) {
        if (!imageUrl.isNullOrBlank()) {
            Image(
                painter = rememberAsyncImagePainter(imageUrl),
                contentDescription = episode.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp),
                contentScale = ContentScale.Crop
            )
        }

        Column(
            modifier = Modifier.padding(22.dp)
        ) {
            Text(
                text = cleanEpisodeSubtitle(episode, episodeNumber),
                color = Color.Gray,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = cleanEpisodeTitle(episode, episodeNumber),
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = episode.overview ?: "Tap play to watch this episode.",
                color = Color.LightGray,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                maxLines = 4
            )

            Spacer(modifier = Modifier.height(18.dp))

            LinearProgressIndicator(
                progress = { 0.18f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(20.dp)),
                color = Color(0xFF8E5CFF),
                trackColor = Color.DarkGray
            )

            Spacer(modifier = Modifier.height(22.dp))

            Button(
                onClick = onClick,
                shape = RoundedCornerShape(40.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF333333),
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp)
            ) {
                Text("▶  Play Episode", fontSize = 16.sp)
            }
        }
    }
}

private fun cleanEpisodeSubtitle(
    episode: VideoWithMetadata,
    episodeNumber: Int
): String {
    val fileName = episode.video.name
    val match = Regex("s(\\d{1,2})e(\\d{1,2})", RegexOption.IGNORE_CASE).find(fileName)

    return if (match != null) {
        val season = match.groupValues[1].padStart(2, '0')
        val ep = match.groupValues[2].padStart(2, '0')
        "S${season}E${ep}"
    } else {
        episode.subtitle
            .substringBefore("•")
            .trim()
            .ifBlank { "Episode $episodeNumber" }
    }
}

private fun cleanEpisodeTitle(
    episode: VideoWithMetadata,
    episodeNumber: Int
): String {
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
    var cleaned = title
        .substringAfterLast("/")
        .substringAfterLast("\\")
        .substringBeforeLast(".")
        .replace(Regex("\\[.*?]"), " ")
        .replace(Regex("\\(.*?\\)"), " ")
        .replace(".", " ")
        .replace("_", " ")
        .replace("-", " ")

    cleaned = cleaned.replace(Regex("s\\d{1,2}e\\d{1,2}", RegexOption.IGNORE_CASE), " ")
    cleaned = cleaned.replace(Regex("\\bseason\\s*\\d+\\b", RegexOption.IGNORE_CASE), " ")
    cleaned = cleaned.replace(Regex("\\bepisode\\s*\\d+\\b", RegexOption.IGNORE_CASE), " ")

    cleaned = cleaned.replace(
        Regex(
            "\\b(2160p|1080p|720p|480p|4k|uhd|hdr10\\+?|hdr|dv|dolby|vision|x264|x265|h264|h265|hevc|web|webdl|webrip|bluray|brrip|hdrip|dvdrip|aac|ddp|dts|atmos|10bit|nf|amzn|yts|rarbg|eztv|tgx|mkv|mp4|avi)\\b",
            RegexOption.IGNORE_CASE
        ),
        " "
    )

    return cleaned
        .replace(Regex("\\b(19|20)\\d{2}\\b"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
