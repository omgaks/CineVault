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

fun Context.findCineActivity(): Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

// Persists the Home screen's scroll position across navigation — same pattern
// as LibraryScrollState in LocalVideoLibraryScreen.kt. A plain object survives
// composable disposal since it isn't tied to the composition.
private object HomeScrollState {
    var index: Int = 0
    var offset: Int = 0
}

// Forces max screen brightness while any non-player screen is on-screen —
// a deliberate design choice (poster art and glass UI read better bright,
// especially on a small phone screen), NOT the same thing as the bug fixed
// in VideoPlayerScreen.kt/MainActivity.kt. The actual bug there was forcing
// brightness INCONSISTENTLY (only Home/Search had it, so leaving them for
// Library or Settings looked like "dimming"); the fix is applying it
// uniformly across every browsing screen, not removing it. The player is
// the one deliberate exception — video content should respect the real
// screen brightness (plus the manual swipe gesture), since forcing 100%
// there was actively harmful for night viewing and battery during long
// playback sessions. Short screens (browsing) vs. long screens (watching)
// genuinely warrant different defaults.
@Composable
// Not private — called from LocalVideoLibraryScreen.kt too, and Kotlin's
// `private` on a top-level function means file-private, not just
// package-private, so it had to be opened up to be callable across files.
fun ForceCineVaultBrightness() {
    val context = LocalContext.current
    val activity = context.findCineActivity()

    DisposableEffect(Unit) {
        activity?.window?.attributes = activity?.window?.attributes?.apply {
            screenBrightness = 1.0f
        }
        onDispose {
            activity?.window?.attributes = activity?.window?.attributes?.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }
}

@Composable
fun CineBottomBar(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    val tabs = listOf(
        Triple(Icons.Filled.Home, "Home", 0),
        Triple(Icons.Filled.List, "Library", 1),
        Triple(Icons.Filled.Search, "Search", 2),
        Triple(Icons.Filled.Settings, "Settings", 3)
    )

    // FIX: was a flat NavigationBar with a solid containerColor — no
    // glass or glow treatment at all. Kept NavigationBar itself rather
    // than replacing it with a fully custom layout, since it handles
    // system bottom-bar insets (gesture nav areas etc.) automatically —
    // that's real, easy-to-silently-break behavior not worth risking for
    // a purely visual change. Instead layered a glowing amber gradient
    // line along the top edge (matching the reference image) and made
    // the container itself semi-transparent so it reads as glass sitting
    // over the content behind it, rather than a flat opaque bar. Size
    // and placement both unchanged.
    Box {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.5.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, AmberGlow.copy(alpha = 0.9f), AmberCore, AmberGlow.copy(alpha = 0.9f), Color.Transparent)
                    )
                )
        )
        NavigationBar(
            containerColor = SpaceDeep.copy(alpha = 0.88f),
            tonalElevation = 0.dp
        ) {
            tabs.forEach { (icon, label, index) ->
                val selected = selectedTab == index
                NavigationBarItem(
                    selected = selected,
                    onClick = { onTabSelected(index) },
                    label = {
                        Text(
                            text = label,
                            color = if (selected) AmberGlow else TextFaint,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    icon = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (selected) AmberGlow else TextFaint,
                                modifier = Modifier.size(22.dp)
                            )
                            if (selected) {
                                Spacer(Modifier.height(3.dp))
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(AmberGlow)
                                )
                            }
                        }
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = Color.Transparent
                    )
                )
            }
        }
    }
}

@Composable
fun HomeScreen(
    videos: List<VideoWithMetadata>,
    onScanRequest: () -> Unit,
    onItemClick: (VideoWithMetadata) -> Unit,
    onPlayClick: (VideoWithMetadata) -> Unit = {},
    // Same callback MainActivity.kt already threads into
    // LocalVideoLibraryScreen — needed here too so the big "Scan Library"
    // button can start a REAL scan (via LibraryScanController) instead of
    // just navigating to Library and leaving the person to tap Scan again
    // once they get there.
    onVideosLoaded: (List<VideoWithMetadata>) -> Unit = {}
) {
    ForceCineVaultBrightness()

    val context = LocalContext.current
    var continueMode by remember { mutableStateOf("List") }
    var featuredMode by remember { mutableStateOf("Grid") }

    // Storage permission for the big Scan Library button below — starts the
    // real scan via LibraryScanController on grant, then navigates to
    // Library either way (denied permission still lands on Library, which
    // already has its own "Storage permission denied" messaging and its
    // own Scan button to retry from).
    val scanPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
    val scanPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) LibraryScanController.start(context, onVideosLoaded)
        onScanRequest()
    }

    val continueWatching =
        videos.filter {
            loadPlaybackPosition(context, it.video.path) > 15_000L
        }.take(12)

    // FEATURE: was a single fixed pick (continueWatching's top backdrop,
    // falling back to the library's first) — same image every time Home
    // opens, however large the library actually is. Now builds a shuffled
    // pool from every video with a backdrop/poster and rotates through it
    // on a timer, so the hero banner stays fresh across visits instead of
    // just showing whatever happens to be first. Shuffled once per
    // composition (remember(videos), not on every recomposition) so the
    // rotation order stays stable and doesn't re-shuffle mid-view.
    val heroCandidates = remember(videos) {
        videos.mapNotNull { it.backdropUrl ?: it.posterUrl }.distinct().shuffled()
    }
    var heroIndex by remember(heroCandidates) { mutableStateOf(0) }
    LaunchedEffect(heroCandidates) {
        if (heroCandidates.size <= 1) return@LaunchedEffect
        while (true) {
            delay(7000)
            heroIndex = (heroIndex + 1) % heroCandidates.size
        }
    }
    val heroImage = heroCandidates.getOrNull(heroIndex)

    // Restores scroll position from HomeScrollState so returning from Detail
    // (or switching tabs and back) lands where you left off, not the top.
    val homeListState = rememberLazyListState(
        initialFirstVisibleItemIndex = HomeScrollState.index,
        initialFirstVisibleItemScrollOffset = HomeScrollState.offset
    )
    LaunchedEffect(homeListState) {
        snapshotFlow { homeListState.firstVisibleItemIndex to homeListState.firstVisibleItemScrollOffset }
            .collect { (i, o) -> HomeScrollState.index = i; HomeScrollState.offset = o }
    }

    // FIX: wraps the LazyColumn specifically to capture the REAL,
    // reliable viewport height. A BoxWithConstraints placed inside a
    // LazyColumn item (which FreshInstallWelcomeContent used to rely on
    // for its own height-aware sizing) doesn't give a trustworthy
    // maxHeight — LazyColumn items are measured with effectively
    // unbounded height by design, since that's what lets the column
    // scroll. This outer one, wrapping the LazyColumn itself, sees the
    // actual on-screen space instead.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenMaxHeight = maxHeight
        androidx.compose.foundation.lazy.LazyColumn(
        state = homeListState,
        modifier = Modifier
            .fillMaxSize()
            .background(SpaceBlack)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 30.dp)
    ) {
        if (videos.isNotEmpty()) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(SpaceMid)
            ) {
                Crossfade(targetState = heroImage, animationSpec = tween(900), label = "heroImageCrossfade") { image ->
                    if (image != null) {
                        AsyncImage(
                            model = image,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            alpha = 0.98f,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0x66000000),
                                    SpaceBlack.copy(alpha = 0.85f)
                                )
                            )
                        )
                )

                // LOGO FIX: removed the 80.dp background square (Color.Black alpha 0.38f
                // + RoundedCornerShape container) that was sitting behind the logo. The
                // logo now renders directly with a soft drop-shadow for contrast against
                // varying hero backdrops, instead of a visible box.
                Image(
                    painter = painterResource(id = R.drawable.cinevault_circle_logo),
                    contentDescription = "CineVault Logo",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp)
                        .size(56.dp)
                        .shadow(elevation = 10.dp, shape = CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(24.dp)
                ) {
                    Text(
                        text = "Your Cinema Library",
                        color = TextBright,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(5.dp))

                    Text(
                        text = "Movies • TV Shows • Local Playback",
                        color = TextMuted,
                        fontSize = 14.sp
                    )

                    if (videos.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = onScanRequest,
                            shape = RoundedCornerShape(40.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AmberGlow.copy(alpha = 0.90f),
                                contentColor = Color.Black
                            )
                        ) {
                            Text("Open Library", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (continueWatching.isNotEmpty()) {
            item {
                ContinueWatchingSection(
                    items = continueWatching,
                    mode = continueMode,
                    onModeChange = { continueMode = it },
                    onItemClick = onItemClick,
                    // "See All" — sets Library's remembered category to
                    // Continue Watching (LocalVideoLibraryScreen.kt reads
                    // this on init) then reuses the same nav action the
                    // hero card's own "Open Library" button already calls,
                    // rather than threading a brand new navigation callback
                    // through MainActivity.kt for this alone.
                    onSeeAll = {
                        LibraryScrollState.category = "Continue Watching"
                        onScanRequest()
                    }
                )
            }
        }
        }

        if (videos.isNotEmpty()) {
            item {
                FeaturedLibrarySection(
                    items = videos.take(18),
                    mode = featuredMode,
                    onModeChange = { featuredMode = it },
                    onItemClick = onItemClick,
                    onPlayClick = onPlayClick
                )
            }
        } else {
            item {
                FreshInstallWelcomeContent(
                    availableHeight = screenMaxHeight,
                    onScanLibrary = { scanPermissionLauncher.launch(scanPermission) },
                    onChooseFolder = onScanRequest,
                    onConnectSmb = onScanRequest,
                    onOpenStream = onScanRequest
                )
            }
        }
    }
    }
}

// ── Continue Watching — screenshot style: clean card, timestamps at the
//    corners, thin progress line at the bottom edge, title BELOW the card ──
@Composable
fun FeaturedLibrarySection(
    items: List<VideoWithMetadata>,
    mode: String,
    onModeChange: (String) -> Unit,
    onItemClick: (VideoWithMetadata) -> Unit,
    onPlayClick: (VideoWithMetadata) -> Unit = {}
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Featured From Your Library",
                color = TextBright,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(GlassSurface)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                SmallToggleChip(text = "Grid", selected = mode == "Grid", onClick = { onModeChange("Grid") })
                SmallToggleChip(text = "List", selected = mode == "List", onClick = { onModeChange("List") })
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (mode == "Grid") {
            val gridItems = items.take(9)
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                gridItems.chunked(3).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowItems.forEach { item ->
                            Box(modifier = Modifier.weight(1f)) {
                                LibraryGridCard(item = item, onClick = { onItemClick(item) }, onPlayClick = onPlayClick)
                            }
                        }
                        repeat(3 - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items.take(10).forEach { item ->
                    LibraryCard(item = item, onClick = { onItemClick(item) })
                }
            }
        }
    }
}

@Composable
fun HomeRow(
    title: String,
    items: List<VideoWithMetadata>,
    onItemClick: (VideoWithMetadata) -> Unit
) {
    val context = LocalContext.current
    Column {
        Text(text = title, color = TextBright, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(14.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(items) { item ->
                val watchedPercent = getWatchedPercent(context, item)
                Column(
                    modifier = Modifier.width(145.dp).clickable { onItemClick(item) }
                ) {
                    Box {
                        PosterBox(
                            posterUrl = item.posterUrl,
                            modifier = Modifier.fillMaxWidth().height(180.dp),
                            progress = watchedPercent,
                            videoPath = item.video.path,
                            episodeStill = item.episodeStill,
                            backdropUrl = item.backdropUrl,
                            type = item.type
                        )
                        RatingBadgeStack(item = item, modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = item.title, color = TextBright, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    if (item.subtitle.isNotBlank()) {
                        Text(text = item.subtitle, color = TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

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

// ── PosterBox — CLEAN poster art. No gradients, no text overlays. Just the
//    poster and (optionally) a thin progress line hugging the bottom edge. ──
@Composable
fun PosterBox(
    posterUrl: String?,
    modifier: Modifier,
    progress: Float = 0f,
    videoPath: String? = null,
    episodeStill: String? = null,
    backdropUrl: String? = null,
    type: String = ""
) {
    val context = LocalContext.current

    // Only TV episodes use stills — movies always use poster
    val displayImage = when {
        type.equals("tv", ignoreCase = true) && !episodeStill.isNullOrBlank() -> episodeStill
        !posterUrl.isNullOrBlank() -> posterUrl
        else -> null
    }

    var localBitmap by remember(videoPath) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var thumbnailFailed by remember(videoPath) { mutableStateOf(false) }

    LaunchedEffect(displayImage, videoPath) {
        if (displayImage.isNullOrBlank() && !videoPath.isNullOrBlank() && !thumbnailFailed) {
            val bitmap = VideoThumbnailHelper.generateLocalThumbnail(context = context, videoPath = videoPath)
            if (bitmap != null) localBitmap = bitmap else thumbnailFailed = true
        }
    }

    val bitmapSnapshot = localBitmap
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SpaceMid)
    ) {
        when {
            !displayImage.isNullOrBlank() -> {
                AsyncImage(model = displayImage, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            bitmapSnapshot != null -> {
                Image(bitmap = bitmapSnapshot.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            else -> {
                Box(modifier = Modifier.fillMaxSize().background(SpaceDeep), contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null, tint = TextFaint, modifier = Modifier.size(42.dp))
                }
            }
        }

        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.18f))
            ) {
                Box(modifier = Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(AmberGlow))
            }
        }
    }
}

// ── LibraryGridCard — the new poster card. Clean art, corner chips,
//    gold QuickPlay (CV1), title + year BELOW. Long-press for actions. ──
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryGridCard(
    item: VideoWithMetadata,
    onClick: () -> Unit,
    onPlayClick: (VideoWithMetadata) -> Unit = {},
    onLongPress: (VideoWithMetadata) -> Unit = {}
) {
    val context = LocalContext.current
    val badges = mediaBadgesFromName(item.video.name)
    val watchedPercent = getWatchedPercent(context, item)
    val qualityChip = listOfNotNull(
        badges.firstOrNull { it == "4K" || it == "1080p" || it == "720p" },
        badges.firstOrNull { it == "HDR" }
    ).joinToString(" ")

    Column(
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = { onLongPress(item) }
        )
    ) {
        Box {
            PosterBox(
                posterUrl = item.posterUrl,
                modifier = Modifier.fillMaxWidth().height(160.dp),
                progress = watchedPercent,
                videoPath = item.video.path,
                episodeStill = item.episodeStill,
                backdropUrl = item.backdropUrl,
                type = item.type
            )

            RatingBadgeStack(item = item, modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
            if (qualityChip.isNotBlank()) {
                CornerChip(text = qualityChip, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
            }

            // Gold QuickPlay — kept from CV1
            Box(modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)) {
                QuickPlayButton { onPlayClick(item) }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = item.title,
            color = TextBright,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold
        )

        if (item.subtitle.isNotBlank()) {
            Text(
                text = item.subtitle,
                color = TextMuted,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryCard(
    item: VideoWithMetadata,
    onClick: () -> Unit,
    onLongPress: (VideoWithMetadata) -> Unit = {}
) {
    val context = LocalContext.current
    val watchedPercent = getWatchedPercent(context, item)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SpaceMid)
            .combinedClickable(onClick = onClick, onLongClick = { onLongPress(item) })
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PosterBox(
            posterUrl = item.posterUrl,
            modifier = Modifier.width(72.dp).height(106.dp),
            progress = watchedPercent,
            videoPath = item.video.path,
            episodeStill = item.episodeStill,
            backdropUrl = item.backdropUrl,
            type = item.type
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.title, color = TextBright, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = item.subtitle.ifBlank { item.video.name }, color = TextMuted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)

            if (watchedPercent > 0f) {
                Spacer(modifier = Modifier.height(8.dp))
                ProgressBar(progress = watchedPercent)
                Spacer(modifier = Modifier.height(5.dp))
                Text(text = "${(watchedPercent * 100).toInt().coerceIn(1, 99)}% watched", color = AmberGlow, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }

            if ((item.rating ?: 0.0) > 0.0) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "★ ${String.format("%.1f", item.rating)}", color = AmberGlow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

internal fun formatClock(ms: Long): String {
    val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

internal fun getWatchedPercent(context: Context, item: VideoWithMetadata): Float {
    val savedPosition = loadPlaybackPosition(context, item.video.path)
    if (savedPosition <= 15_000L) return 0f
    // Use real saved duration if available, fall back to 90min estimate
    val realDuration = loadDuration(context, item.video.path)
    val duration = if (realDuration > 60_000L) realDuration else 90L * 60L * 1000L
    return (savedPosition.toFloat() / duration.toFloat()).coerceIn(0.03f, 0.98f)
}

fun groupTvShows(videos: List<VideoWithMetadata>): List<TvGroup> {
    return videos
        .filter { it.type == "tv" }
        .groupBy { it.title }
        .map { (title, episodes) ->
            TvGroup(
                showName = title,
                posterUrl = episodes.firstOrNull()?.posterUrl,
                backdropUrl = episodes.firstOrNull()?.backdropUrl,
                episodes = episodes.sortedBy { it.subtitle }
            )
        }
        .sortedBy { it.showName }
}

fun mediaBadgesFromName(fileName: String): List<String> {
    val lower = fileName.lowercase()
    val badges = mutableListOf<String>()

    if (lower.contains("3d") || lower.contains("sbs") || lower.contains("hsbs") ||
        lower.contains("half sbs") || lower.contains("ou")) badges.add("3D")
    if (lower.contains("hevc") || lower.contains("x265") || lower.contains("h265")) badges.add("HEVC")
    if (lower.contains("2160p") || lower.contains("4k")) badges.add("4K")
    else if (lower.contains("1080p")) badges.add("1080p")
    else if (lower.contains("720p")) badges.add("720p")
    if (lower.contains("hdr")) badges.add("HDR")
    if (lower.contains("atmos")) badges.add("ATMOS")

    return badges.distinct()
}
