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


// ── PosterBox — CLEAN poster art. No gradients, no text overlays. Just the
//    poster and (optionally) a thin progress line hugging the bottom edge. ──
