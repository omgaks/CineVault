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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sole.cinevault.ui.theme.*

// FEATURE: fresh-install empty-state redesign, phase 1 (structure +
// responsiveness). Replaces the old single circular "Scan\nLibrary"
// button with a fuller welcome screen — hero, heading/tagline/
// description, a prominent scan action, three secondary source options,
// and a privacy assurance strip. Built from a real design spec, adapted
// to this codebase's actual proven patterns rather than followed
// literally:
// - Hero uses the app's own existing logo (cinevault_circle_logo) with a
//   simple animated glow ring, not a custom illustrated icon — a
//   photorealistic hand-built Compose icon wouldn't have matched the
//   reference mockup's rendered-metal look anyway, and this reuses
//   something already proven to look right in the app.
// - Responsive breakpoint uses the same minOf(maxWidth, maxHeight) >=
//   600.dp technique already proven for Subtitle Studio's sizing, rather
//   than the spec's more granular 4-6 tier breakdown, which isn't
//   realistically testable against just two real devices.
// - Choose Folder / Connect SMB / Open Stream all navigate to the
//   Library tab for now (same proven fallback the existing Scan button
//   already uses) rather than deep-linking to a specific dialog — those
//   entry points live inside LocalVideoLibraryScreen's own internal
//   state currently, not exposed as callbacks HomeScreen can reach
//   directly. Worth a follow-up pass if direct deep-linking is wanted.
// - Landscape-tablet 3-in-a-row tile layout deliberately deferred —
//   tiles stack vertically regardless of orientation for this first
//   pass, to verify the core structure on-device before adding a second
//   layout variant on top of it.
@Immutable
private data class WelcomeDimensions(
    val horizontalPadding: Dp,
    val heroSize: Dp,
    val headingSize: androidx.compose.ui.unit.TextUnit,
    val taglineSize: androidx.compose.ui.unit.TextUnit,
    val bodySize: androidx.compose.ui.unit.TextUnit,
    val primaryButtonHeight: Dp,
    val primaryButtonMaxWidth: Dp,
    val sourceTileHeight: Dp,
    val contentMaxWidth: Dp,
    val sectionGap: Dp
)

@Composable
private fun rememberWelcomeDimensions(isTablet: Boolean): WelcomeDimensions =
    remember(isTablet) {
        if (isTablet) {
            WelcomeDimensions(
                horizontalPadding = 40.dp, heroSize = 170.dp, headingSize = 32.sp, taglineSize = 17.sp, bodySize = 14.sp,
                primaryButtonHeight = 64.dp, primaryButtonMaxWidth = 560.dp, sourceTileHeight = 84.dp,
                contentMaxWidth = 620.dp, sectionGap = 22.dp
            )
        } else {
            WelcomeDimensions(
                horizontalPadding = 20.dp, heroSize = 130.dp, headingSize = 26.sp, taglineSize = 15.sp, bodySize = 13.sp,
                primaryButtonHeight = 58.dp, primaryButtonMaxWidth = 480.dp, sourceTileHeight = 74.dp,
                contentMaxWidth = 460.dp, sectionGap = 16.dp
            )
        }
    }

@Composable
private fun SourceTile(icon: ImageVector, title: String, subtitle: String, height: Dp, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = height)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(GlassHighlight.copy(alpha = 0.10f), GlassSurface)))
            .border(1.dp, Brush.verticalGradient(listOf(AmberGlow.copy(alpha = 0.45f), AmberGlow.copy(alpha = 0.12f))), RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(AmberGlow.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = AmberCore, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(text = title, color = TextBright, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, color = TextMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun FreshInstallWelcomeContent(
    onScanLibrary: () -> Unit,
    onChooseFolder: () -> Unit,
    onConnectSmb: () -> Unit,
    onOpenStream: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val isTablet = minOf(maxWidth, maxHeight) >= 600.dp
        val dims = rememberWelcomeDimensions(isTablet)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = dims.contentMaxWidth)
                .align(Alignment.Center)
                .padding(horizontal = dims.horizontalPadding, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(13.dp))
                    .background(GlassSurface)
                    .border(1.dp, AmberCore.copy(alpha = 0.32f), RoundedCornerShape(13.dp))
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Icon(imageVector = Icons.Filled.Shield, contentDescription = null, tint = AmberCore, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Private · Local-first", color = TextBright, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(dims.sectionGap))

            val heroGlow = rememberInfiniteTransition(label = "heroGlow")
            val heroGlowAlpha by heroGlow.animateFloat(
                initialValue = 0.30f, targetValue = 0.55f,
                animationSpec = infiniteRepeatable(animation = tween(1800, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
                label = "heroGlowAlpha"
            )
            Box(modifier = Modifier.size(dims.heroSize), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.radialGradient(listOf(AmberGlow.copy(alpha = heroGlowAlpha), Color.Transparent)))
                )
                Image(
                    painter = painterResource(id = R.drawable.cinevault_circle_logo),
                    contentDescription = "CineVault",
                    modifier = Modifier.size(dims.heroSize * 0.62f)
                )
            }

            Spacer(modifier = Modifier.height(dims.sectionGap))

            Text(
                text = "Welcome to CineVault",
                color = TextBright, fontSize = dims.headingSize, fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Turn your videos into a personal cinema.",
                color = AmberCore.copy(alpha = 0.85f), fontSize = dims.taglineSize, fontWeight = FontWeight.Medium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "CineVault finds movies and shows on this device, then adds artwork, ratings and details.",
                color = TextBright.copy(alpha = 0.70f), fontSize = dims.bodySize,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.widthIn(max = 380.dp)
            )

            Spacer(modifier = Modifier.height(dims.sectionGap + 6.dp))

            // Same breathing-glow technique as FrostedPlayButton in the
            // player screen — alpha pulse only, reshaped from circle to
            // pill so the app's established amber-glow motion language
            // stays consistent rather than introducing a second, visually
            // different effect for what's conceptually the same "tap me"
            // affordance.
            val glow = rememberInfiniteTransition(label = "scanPillGlow")
            val glowAlpha by glow.animateFloat(
                initialValue = 0.45f, targetValue = 0.95f,
                animationSpec = infiniteRepeatable(animation = tween(1400, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
                label = "scanPillGlowAlpha"
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = dims.primaryButtonMaxWidth)
                    .height(dims.primaryButtonHeight)
                    .shadow(elevation = 14.dp, shape = RoundedCornerShape(50), ambientColor = AmberCore.copy(alpha = 0.35f), spotColor = AmberCore.copy(alpha = 0.45f))
                    .clip(RoundedCornerShape(50))
                    .background(Brush.verticalGradient(listOf(AmberCore.copy(alpha = 0.75f + 0.2f * glowAlpha), AmberDeep)))
                    .clickable { onScanLibrary() }
                    .padding(horizontal = 22.dp)
            ) {
                Icon(imageVector = Icons.Filled.Search, contentDescription = null, tint = Color.Black, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = "Scan My Library", color = Color.Black, fontSize = if (isTablet) 19.sp else 17.sp, fontWeight = FontWeight.Black)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Usually takes less than a minute", color = TextMuted, fontSize = 11.5.sp)

            Spacer(modifier = Modifier.height(dims.sectionGap + 4.dp))

            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SourceTile(icon = Icons.Filled.Folder, title = "Choose Folder", subtitle = "Scan only what you select", height = dims.sourceTileHeight, onClick = onChooseFolder)
                SourceTile(icon = Icons.Filled.Dns, title = "Connect SMB", subtitle = "Add a computer or NAS", height = dims.sourceTileHeight, onClick = onConnectSmb)
                SourceTile(icon = Icons.Filled.Link, title = "Open Stream", subtitle = "Play a direct video link", height = dims.sourceTileHeight, onClick = onOpenStream)
            }

            Spacer(modifier = Modifier.height(dims.sectionGap))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(GlassSurface)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Icon(imageVector = Icons.Filled.Shield, contentDescription = null, tint = AmberCore, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "No account · No ads · Your videos stay on your device",
                    color = TextMuted, fontSize = 12.5.sp, maxLines = 2
                )
            }
        }
    }
}


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

    NavigationBar(
        containerColor = SpaceDeep,
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

    androidx.compose.foundation.lazy.LazyColumn(
        state = homeListState,
        modifier = Modifier
            .fillMaxSize()
            .background(SpaceBlack)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 30.dp)
    ) {
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
                    onScanLibrary = { scanPermissionLauncher.launch(scanPermission) },
                    onChooseFolder = onScanRequest,
                    onConnectSmb = onScanRequest,
                    onOpenStream = onScanRequest
                )
            }
        }
    }
}

// ── Continue Watching — screenshot style: clean card, timestamps at the
//    corners, thin progress line at the bottom edge, title BELOW the card ──
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
private fun ResumePosterBox(
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
private fun ProgressBar(progress: Float, compact: Boolean = false) {
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
private fun SmallToggleChip(text: String, selected: Boolean, onClick: () -> Unit) {
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
private fun QuickPlayButton(onClick: () -> Unit) {
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
private fun ImdbCornerChip(value: String, modifier: Modifier = Modifier) {
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
private fun TmdbCornerChip(value: String, modifier: Modifier = Modifier) {
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
private fun RottenTomatoesCornerChip(value: String, modifier: Modifier = Modifier) {
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
private fun RatingBadgeStack(item: VideoWithMetadata, modifier: Modifier = Modifier) {
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
private fun CornerChip(text: String, modifier: Modifier = Modifier) {
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

    // Expanded beyond title/filename to also match genre, director, and
    // year — e.g. "horror" or "nolan" or "2010" now actually finds
    // something instead of only exact title/filename substrings.
    // Deliberately NOT fuzzy/typo-tolerant matching (that's real edit-
    // distance scoring, a separate feature on its own merits) — this is
    // still exact substring matching, just against more fields.
    val filteredVideos = remember(videos, query) {
        if (query.isBlank()) videos else videos.filter { v ->
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

private fun formatClock(ms: Long): String {
    val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

private fun getWatchedPercent(context: Context, item: VideoWithMetadata): Float {
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
