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

// Best-effort year extraction straight from the filename — same pattern
// already used elsewhere (cleanScannedTitle etc.), not a new field on the
// model. Returns null rather than guessing when nothing matches.
private fun extractYearFromFileName(fileName: String): String? =
    Regex("\\b(19|20)\\d{2}\\b").find(fileName)?.value

@Composable
fun SearchScreen(
    videos: List<VideoWithMetadata>,
    query: String,
    onQueryChange: (String) -> Unit,
    onVideoClick: (VideoWithMetadata) -> Unit
) {
    ForceCineVaultBrightness()
    val context = LocalContext.current

    // FIX: search received the exact same unfiltered library list as the
    // main Library screen, but — unlike Library, which explicitly filters
    // both individually-hidden paths and whole secret folders before
    // displaying anything — had no secret-folder awareness at all. A
    // video correctly hidden from browsing was still fully searchable and
    // clickable straight through to Detail/Player, defeating the point
    // of hiding it in the first place. Same filtering logic as
    // LocalVideoLibraryScreen, applied here too.
    val hiddenPaths = remember { loadSecretVideoPaths(context) }
    val hiddenFolders = remember { loadSecretFolderPaths(context) }
    val searchableVideos = remember(videos, hiddenPaths, hiddenFolders) {
        videos.filter { !hiddenPaths.contains(it.video.path) && !videoIsInsideSecretFolder(it, hiddenFolders) }
    }

    // Expanded beyond title/filename to also match genre, director, and
    // year — e.g. "horror" or "nolan" or "2010" now actually finds
    // something instead of only exact title/filename substrings.
    // Deliberately NOT fuzzy/typo-tolerant matching (that's real edit-
    // distance scoring, a separate feature on its own merits) — this is
    // still exact substring matching, just against more fields.
    val filteredVideos = remember(searchableVideos, query) {
        if (query.isBlank()) searchableVideos else searchableVideos.filter { v ->
            v.title.contains(query, ignoreCase = true) ||
                v.video.name.contains(query, ignoreCase = true) ||
                v.genres.any { it.contains(query, ignoreCase = true) } ||
                v.director?.contains(query, ignoreCase = true) == true ||
                extractYearFromFileName(v.video.name)?.contains(query) == true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SpaceBlack)
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50),
            singleLine = true,
            leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null, tint = TextMuted) },
            placeholder = { Text("Search title, genre, director, year...", color = TextFaint) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextBright,
                unfocusedTextColor = TextBright,
                focusedContainerColor = GlassSurfaceStrong,
                unfocusedContainerColor = GlassSurface,
                focusedBorderColor = AmberGlow.copy(alpha = 0.65f),
                unfocusedBorderColor = GlassBorderTop,
                cursorColor = AmberGlow
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (query.isNotBlank() && filteredVideos.isEmpty()) {
            EmptyStateBlock(
                icon = Icons.Filled.Search,
                title = "No results",
                subtitle = "Nothing matches \"$query\" in title, genre, director, or year."
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredVideos) { videoItem ->
                    SearchPosterCard(item = videoItem, onClick = { onVideoClick(videoItem) })
                }
            }
        }
    }
}

@Composable
private fun SearchPosterCard(item: VideoWithMetadata, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Box {
            PosterBox(
                posterUrl = item.posterUrl,
                modifier = Modifier.fillMaxWidth().height(210.dp),
                videoPath = item.video.path,
                episodeStill = item.episodeStill,
                backdropUrl = item.backdropUrl,
                type = item.type
            )
            RatingBadgeStack(item = item, modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = item.title, color = TextBright, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
        if (item.subtitle.isNotBlank()) {
            Text(text = item.subtitle, color = TextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
