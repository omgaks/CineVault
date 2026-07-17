package com.sole.cinevault

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sole.cinevault.ui.theme.*

// ── Genre / Director / Actor / Collection pages ────────────────────────────
// All four are "filter the library by some attribute and show a poster grid"
// — built on one shared internal layout (MediaIntelligenceGridScreen) so
// they stay visually consistent with each other and with the rest of the
// app (same LibraryGridCard used in Library/Home/Search), rather than four
// independently-drifting screens.
//
// Note: the quick-play button on each card routes to the Detail screen here
// (same as tapping the card itself) rather than jumping straight into the
// player — keeps the callback surface these screens need from MainActivity
// to just onItemClick, instead of also threading a separate play callback
// through four more screens.

@Composable
fun GenreScreen(
    genreName: String,
    videos: List<VideoWithMetadata>,
    onBack: () -> Unit,
    onItemClick: (VideoWithMetadata) -> Unit
) {
    val items = remember(videos, genreName) {
        videos.filter { v -> v.genres.any { it.equals(genreName, ignoreCase = true) } }
    }
    MediaIntelligenceGridScreen(
        title = genreName,
        subtitle = titleCountLabel(items.size),
        items = items,
        onBack = onBack,
        onItemClick = onItemClick
    )
}

@Composable
fun DirectorScreen(
    directorName: String,
    videos: List<VideoWithMetadata>,
    onBack: () -> Unit,
    onItemClick: (VideoWithMetadata) -> Unit
) {
    val items = remember(videos, directorName) {
        videos.filter { it.director?.equals(directorName, ignoreCase = true) == true }
    }
    MediaIntelligenceGridScreen(
        title = directorName,
        subtitle = "Director • ${titleCountLabel(items.size)}",
        items = items,
        onBack = onBack,
        onItemClick = onItemClick
    )
}

@Composable
fun ActorScreen(
    actorId: Int,
    actorName: String,
    profilePath: String?,
    videos: List<VideoWithMetadata>,
    onBack: () -> Unit,
    onItemClick: (VideoWithMetadata) -> Unit
) {
    val items = remember(videos, actorId) {
        videos.filter { v -> v.cast.any { it.id == actorId } }
    }
    MediaIntelligenceGridScreen(
        title = actorName,
        subtitle = titleCountLabel(items.size) + " in your library",
        items = items,
        onBack = onBack,
        onItemClick = onItemClick,
        circularProfileUrl = profilePath?.let { "https://image.tmdb.org/t/p/w500$it" }
    )
}

@Composable
fun CollectionScreen(
    title: String,
    items: List<VideoWithMetadata>,
    onBack: () -> Unit,
    onItemClick: (VideoWithMetadata) -> Unit
) {
    val heroBackdrop = remember(items) {
        items.firstOrNull { !it.backdropUrl.isNullOrBlank() }?.backdropUrl
    }
    MediaIntelligenceGridScreen(
        title = title,
        subtitle = titleCountLabel(items.size) + " in your library",
        items = items,
        onBack = onBack,
        onItemClick = onItemClick,
        heroBackdropUrl = heroBackdrop
    )
}

private fun titleCountLabel(count: Int): String = "$count title${if (count != 1) "s" else ""}"

@Composable
private fun MediaIntelligenceGridScreen(
    title: String,
    subtitle: String,
    items: List<VideoWithMetadata>,
    onBack: () -> Unit,
    onItemClick: (VideoWithMetadata) -> Unit,
    heroBackdropUrl: String? = null,
    circularProfileUrl: String? = null
) {
    Box(modifier = Modifier.fillMaxSize().background(SpaceBlack)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, top = 16.dp, bottom = 28.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    if (heroBackdropUrl != null) {
                        Box(modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(22.dp))) {
                            AsyncImage(model = heroBackdropUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            Box(
                                modifier = Modifier.fillMaxSize().background(
                                    Brush.verticalGradient(colors = listOf(Color.Transparent, SpaceBlack.copy(alpha = 0.90f)))
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(50.dp))
                    } else {
                        Spacer(modifier = Modifier.height(54.dp))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (circularProfileUrl != null) {
                            Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(SpaceMid)) {
                                AsyncImage(model = circularProfileUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                        }
                        Column {
                            Text(text = title, color = TextBright, fontSize = 24.sp, fontWeight = FontWeight.Black, maxLines = 2)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = subtitle, color = TextMuted, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.width(46.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(AmberGlow))
                    Spacer(modifier = Modifier.height(18.dp))
                }
            }

            if (items.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Text(text = "No titles found in your library.", color = TextMuted, fontSize = 15.sp)
                    }
                }
            } else {
                items(items = items, key = { it.video.path }) { item ->
                    LibraryGridCard(item = item, onClick = { onItemClick(item) }, onPlayClick = { onItemClick(item) })
                }
            }
        }

        // Back button — same glass-circle treatment as Detail/TvShow screens
        Box(
            modifier = Modifier.align(Alignment.TopStart).padding(14.dp).size(42.dp).clip(CircleShape)
                .background(GlassSurfaceStrong).clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = Icons.Rounded.ArrowBack, contentDescription = "Back", tint = TextBright, modifier = Modifier.size(22.dp))
        }
    }
}
