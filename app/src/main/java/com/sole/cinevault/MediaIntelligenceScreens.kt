package com.sole.cinevault

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
// FIX: onPlayClick now actually plays — previously every one of these four
// screens routed the quick-play button on each poster to onItemClick (the
// SAME thing tapping the card itself does), which opens Detail instead of
// starting playback. A second, related fix lives in MainActivity.kt: the
// episodeList passed to the player when playing FROM one of these screens
// is now the actual filtered `items` list for that screen (this genre /
// this collection / this folder), not the entire library — so swiping to
// the next/previous video during playback now correctly stays within this
// same group instead of jumping to an unrelated video from the whole
// library.
//
// SCROLL PERSISTENCE (new): each of these screens is a fresh composable
// instance every time it's pushed (real back-stack navigation, see
// MainActivity.kt), so a plain LazyVerticalGrid always reset to the top —
// backing out of a deep scroll and returning meant re-scrolling through
// everything again. MediaGridScrollState below persists the last scroll
// position per screen (keyed by title, which is unique enough per genre/
// director/actor/collection/folder for personal-library use) so returning
// lands back where you left off, matching the same pattern already used
// for the Library grid (LibraryScrollState) and Detail screen.
//
// JUMP-TO-LAST-PLAYED (new): CollectionScreen accepts an optional
// initialScrollTargetVideoPath — when set (and there's no already-persisted
// scroll position for this screen yet), the grid opens scrolled directly to
// that video instead of the top of the list. Used for Select-Folder pages
// so tapping a folder lands you on the same video shown in its thumbnail
// (the last one played) rather than forcing a rescroll through everything.

private object MediaGridScrollState {
    private val positions = mutableMapOf<String, Pair<Int, Int>>()
    fun get(key: String): Pair<Int, Int> = positions[key] ?: (0 to 0)
    fun set(key: String, index: Int, offset: Int) { positions[key] = index to offset }
    fun hasSaved(key: String): Boolean = positions.containsKey(key)
}

@Composable
fun GenreScreen(
    genreName: String,
    videos: List<VideoWithMetadata>,
    onBack: () -> Unit,
    onItemClick: (VideoWithMetadata) -> Unit,
    onPlayClick: (VideoWithMetadata) -> Unit = {}
) {
    val items = remember(videos, genreName) {
        videos.filter { v -> v.genres.any { it.equals(genreName, ignoreCase = true) } }
    }
    MediaIntelligenceGridScreen(
        title = genreName,
        subtitle = titleCountLabel(items.size),
        items = items,
        onBack = onBack,
        onItemClick = onItemClick,
        onPlayClick = onPlayClick,
        scrollKey = "genre:$genreName"
    )
}

@Composable
fun DirectorScreen(
    directorName: String,
    videos: List<VideoWithMetadata>,
    onBack: () -> Unit,
    onItemClick: (VideoWithMetadata) -> Unit,
    onPlayClick: (VideoWithMetadata) -> Unit = {}
) {
    val items = remember(videos, directorName) {
        videos.filter { it.director?.equals(directorName, ignoreCase = true) == true }
    }
    MediaIntelligenceGridScreen(
        title = directorName,
        subtitle = "Director • ${titleCountLabel(items.size)}",
        items = items,
        onBack = onBack,
        onItemClick = onItemClick,
        onPlayClick = onPlayClick,
        scrollKey = "director:$directorName"
    )
}

@Composable
fun ActorScreen(
    actorId: Int,
    actorName: String,
    profilePath: String?,
    videos: List<VideoWithMetadata>,
    onBack: () -> Unit,
    onItemClick: (VideoWithMetadata) -> Unit,
    onPlayClick: (VideoWithMetadata) -> Unit = {}
) {
    val context = LocalContext.current
    val items = remember(videos, actorId) {
        videos.filter { v -> v.cast.any { it.id == actorId } }
    }
    MediaIntelligenceGridScreen(
        title = actorName,
        subtitle = titleCountLabel(items.size) + " in your library",
        items = items,
        onBack = onBack,
        onItemClick = onItemClick,
        onPlayClick = onPlayClick,
        circularProfileUrl = profilePath?.let { "https://image.tmdb.org/t/p/w500$it" },
        scrollKey = "actor:$actorId",
        // Browsing your own library and "who is this person" are two
        // different intents — this keeps both available instead of forcing
        // a choice between them.
        onSearchWebClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(actorName)}")))
        }
    )
}

@Composable
fun CollectionScreen(
    title: String,
    items: List<VideoWithMetadata>,
    onBack: () -> Unit,
    onItemClick: (VideoWithMetadata) -> Unit,
    onPlayClick: (VideoWithMetadata) -> Unit = {},
    // When set, and this screen has no saved scroll position yet, the grid
    // opens scrolled to the video at this path instead of the top. Pass the
    // folder's last-played video path here for Select-Folder pages.
    initialScrollTargetVideoPath: String? = null
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
        onPlayClick = onPlayClick,
        heroBackdropUrl = heroBackdrop,
        scrollKey = "collection:$title",
        initialScrollTargetVideoPath = initialScrollTargetVideoPath
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
    onPlayClick: (VideoWithMetadata) -> Unit = {},
    heroBackdropUrl: String? = null,
    circularProfileUrl: String? = null,
    onSearchWebClick: (() -> Unit)? = null,
    scrollKey: String,
    initialScrollTargetVideoPath: String? = null
) {
    // Header occupies grid index 0 (span=maxLineSpan); the poster items
    // start at index 1. Used only for the one-time jump-to-last-played
    // target below — after that, normal scroll-position persistence takes
    // over via MediaGridScrollState.
    val hadSavedPosition = remember(scrollKey) { MediaGridScrollState.hasSaved(scrollKey) }
    val initialIndex = remember(scrollKey, items) {
        if (hadSavedPosition) {
            MediaGridScrollState.get(scrollKey).first
        } else {
            val targetIdx = initialScrollTargetVideoPath?.let { path -> items.indexOfFirst { it.video.path == path } } ?: -1
            if (targetIdx >= 0) 1 + targetIdx else 0
        }
    }
    val initialOffset = remember(scrollKey) {
        if (hadSavedPosition) MediaGridScrollState.get(scrollKey).second else 0
    }

    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = initialIndex,
        initialFirstVisibleItemScrollOffset = initialOffset
    )
    LaunchedEffect(gridState, scrollKey) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .collect { (i, o) -> MediaGridScrollState.set(scrollKey, i, o) }
    }

    Box(modifier = Modifier.fillMaxSize().background(SpaceBlack)) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 28.dp)
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
                    LibraryGridCard(item = item, onClick = { onItemClick(item) }, onPlayClick = { onPlayClick(item) })
                }
            }

            // Tap the face again, below everything you already own, to look
            // the person up on the web instead — the local library grid is
            // the primary answer to "what do I have with this person", this
            // is the secondary path once that's been seen.
            if (onSearchWebClick != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier.size(72.dp).clip(CircleShape).background(SpaceMid)
                                .border(1.5.dp, Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderBottom)), CircleShape)
                                .clickable { onSearchWebClick() }
                        ) {
                            if (circularProfileUrl != null) {
                                AsyncImage(model = circularProfileUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(imageVector = Icons.Rounded.Public, contentDescription = null, tint = AmberCore, modifier = Modifier.size(28.dp))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Tap to search the web", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
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
