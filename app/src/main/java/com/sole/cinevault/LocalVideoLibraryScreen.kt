package com.sole.cinevault

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.app.KeyguardManager
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sole.cinevault.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File

enum class LibrarySortOption(val label: String) {
    TITLE_AZ("A-Z"),
    TITLE_ZA("Z-A"),
    NEWEST("Newest"),
    OLDEST("Oldest"),
    SIZE_BIG("Size ↓"),
    SIZE_SMALL("Size ↑")
}

private object LibraryScrollState {
    var index: Int = 0
    var offset: Int = 0
    var category: String = "All"
    var sort: LibrarySortOption = LibrarySortOption.TITLE_AZ
    var gridMode: Boolean = true
}

private data class VideoFolder(
    val folderName: String,
    val folderPath: String,
    val videos: List<VideoWithMetadata>
)

private fun groupVideosByFolder(videos: List<VideoWithMetadata>): List<VideoFolder> {
    return videos
        .groupBy { File(it.video.path).parent ?: "/" }
        .map { (path, items) ->
            VideoFolder(
                folderName = File(path).name.ifBlank { path },
                folderPath = path,
                videos = items.sortedBy { it.title.lowercase() }
            )
        }
        .sortedBy { it.folderName.lowercase() }
}

// ── Folder type icon heuristic ────────────────────────────────────────────
// Generic Material icons only — deliberately NOT actual TikTok/Instagram
// brand marks (those are trademarked assets, not something to reproduce).
// Matches on the folder's display name, which for restricted folders is
// usually whatever the source app named its export/download folder.
private fun folderIconFor(displayName: String): ImageVector {
    val lower = displayName.lowercase()
    return when {
        lower.contains("tiktok") -> Icons.Filled.MusicNote
        lower.contains("instagram") || lower.contains("insta") -> Icons.Filled.PhotoCamera
        lower.contains("whatsapp") -> Icons.Filled.Chat
        lower.contains("camera") || lower.contains("dcim") -> Icons.Filled.CameraAlt
        else -> Icons.Filled.Folder
    }
}

// ── Genre normalization ─────────────────────────────────────────────────
// TMDB uses slightly different genre names/groupings for movies vs TV shows
// (e.g. movies get "Science Fiction", TV shows get "Sci-Fi & Fantasy" for
// essentially the same thing), which previously showed up as two separate,
// near-duplicate chips. This collapses known synonyms into one canonical
// display name before the genre list is deduplicated.
private val genreNormalizationMap = mapOf(
    "science fiction" to "Sci-Fi & Fantasy",
    "sci-fi" to "Sci-Fi & Fantasy",
    "sci fi" to "Sci-Fi & Fantasy",
    "war & politics" to "War"
)

private fun normalizeGenreName(raw: String): String {
    val key = raw.trim().lowercase()
    return genreNormalizationMap[key] ?: raw.trim()
}

// Generic Material icons representing each genre — evocative, not literal
// (there's no official "genre icon set"), consistent across the whole app.
private fun genreIconFor(name: String): ImageVector {
    val lower = name.lowercase()
    return when {
        lower.contains("action") -> Icons.Filled.Bolt
        lower.contains("adventure") -> Icons.Filled.Explore
        lower.contains("animation") -> Icons.Filled.Brush
        lower.contains("comedy") -> Icons.Filled.TheaterComedy
        lower.contains("crime") -> Icons.Filled.Gavel
        lower.contains("documentary") -> Icons.Filled.Videocam
        lower.contains("drama") -> Icons.Filled.TheaterComedy
        lower.contains("family") -> Icons.Filled.FamilyRestroom
        lower.contains("fantasy") || lower.contains("sci-fi") -> Icons.Filled.AutoAwesome
        lower.contains("history") -> Icons.Filled.HistoryEdu
        lower.contains("horror") -> Icons.Filled.DarkMode
        lower.contains("music") -> Icons.Filled.MusicNote
        lower.contains("mystery") -> Icons.Filled.Search
        lower.contains("romance") -> Icons.Rounded.Favorite
        lower.contains("thriller") -> Icons.Filled.Warning
        lower.contains("war") -> Icons.Filled.Shield
        lower.contains("western") -> Icons.Filled.Landscape
        else -> Icons.Filled.LocalMovies
    }
}

@Composable
private fun GenreIconChip(name: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(62.dp).clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape)
                .background(GlassSurfaceStrong)
                .background(Brush.radialGradient(listOf(AmberGlow.copy(alpha = 0.30f), Color.Transparent)))
                .border(1.2.dp, Brush.verticalGradient(listOf(AmberGlow.copy(alpha = 0.70f), AmberDeep.copy(alpha = 0.30f))), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = genreIconFor(name), contentDescription = null, tint = AmberCore, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = name, color = TextBright, fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocalVideoLibraryScreen(
    videos: List<VideoWithMetadata>,
    onVideosLoaded: (List<VideoWithMetadata>) -> Unit,
    onItemClick: (VideoWithMetadata) -> Unit,
    onPlayClick: (VideoWithMetadata) -> Unit = {},
    onTvGroupClick: (TvGroup) -> Unit,
    onSecretChanged: () -> Unit = {},
    onGenreClick: (String) -> Unit = {},
    onNativeCollectionClick: (Int, String) -> Unit = { _, _ -> },
    onCuratedCollectionClick: (String) -> Unit = {},
    onRestrictedFolderClick: (RestrictedFolder) -> Unit = {}
) {
    val context = LocalContext.current
    ForceCineVaultBrightness()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    var isLoading by remember { mutableStateOf(false) }
    var scanStatus by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(LibraryScrollState.category) }
    var isGridMode by remember { mutableStateOf(LibraryScrollState.gridMode) }
    var sortOption by remember { mutableStateOf(LibraryScrollState.sort) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var toolsMenuExpanded by remember { mutableStateOf(false) }
    var secretUnlocked by remember { mutableStateOf(false) }
    var hiddenPaths by remember { mutableStateOf<Set<String>>(loadSecretVideoPaths(context)) }
    var hiddenFolders by remember { mutableStateOf<Set<String>>(loadSecretFolderPaths(context)) }
    var favoritePaths by remember { mutableStateOf(loadFavoriteVideoPaths(context)) }
    var contextSheetItem by remember { mutableStateOf<VideoWithMetadata?>(null) }
    // Long-pressing a folder TILE now asks for confirmation first instead
    // of toggling instantly — holds the folder name + its video paths while
    // the confirm dialog is showing.
    var folderSecretConfirm by remember { mutableStateOf<Pair<String, List<String>>?>(null) }

    var expandedFolders by remember { mutableStateOf<Set<String>>(emptySet()) }

    val keyguardManager = remember { context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager }

    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = LibraryScrollState.index,
        initialFirstVisibleItemScrollOffset = LibraryScrollState.offset
    )
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .collect { (i, o) -> LibraryScrollState.index = i; LibraryScrollState.offset = o }
    }
    LaunchedEffect(selectedCategory, sortOption, isGridMode) {
        LibraryScrollState.category = selectedCategory
        LibraryScrollState.sort = sortOption
        LibraryScrollState.gridMode = isGridMode
    }

    // FIX (#4): the previous version only set secretUnlocked = true here and
    // relied on the NEXT tap of the "Secret" chip to actually navigate,
    // because openSecretFolder() checks `if (secretUnlocked)` up front and
    // that check hadn't run yet on the tap that triggered the auth prompt.
    // Now we navigate immediately in the same callback that confirms the
    // unlock, so it opens on the first successful unlock.
    val secretUnlockLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            secretUnlocked = true
            selectedCategory = "Secret"
            Toast.makeText(context, "Secret folder unlocked", Toast.LENGTH_SHORT).show()
        } else {
            secretUnlocked = false; selectedCategory = "All"
            Toast.makeText(context, "Secret folder locked", Toast.LENGTH_SHORT).show()
        }
    }

    fun openSecretFolder() {
        if (secretUnlocked) { selectedCategory = "Secret"; return }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && keyguardManager.isKeyguardSecure) {
            val intent = keyguardManager.createConfirmDeviceCredentialIntent("Unlock Secret Folder", "Confirm fingerprint, PIN, pattern, or password")
            if (intent != null) secretUnlockLauncher.launch(intent)
            else Toast.makeText(context, "Device lock is not available", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Set phone screen lock first to secure this folder", Toast.LENGTH_LONG).show()
        }
    }

    fun openContextSheet(item: VideoWithMetadata) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        contextSheetItem = item
    }

    fun hideVideo(item: VideoWithMetadata) {
        val updated = hiddenPaths + item.video.path; hiddenPaths = updated
        saveSecretVideoPaths(context, updated); clearPlaybackPosition(context, item.video.path)
        onSecretChanged(); Toast.makeText(context, "Moved to Secret folder", Toast.LENGTH_SHORT).show()
    }

    fun unhideEntireFolder(item: VideoWithMetadata) {
        val folderPath = hiddenFolders.firstOrNull { item.video.path.startsWith(it) } ?: File(item.video.path).parent ?: return
        val updatedFolders = hiddenFolders - folderPath; hiddenFolders = updatedFolders
        saveSecretFolderPaths(context, updatedFolders)
        Toast.makeText(context, "Folder removed from Secret", Toast.LENGTH_SHORT).show()
    }

    fun unhideVideo(item: VideoWithMetadata) {
        val updated = hiddenPaths - item.video.path; hiddenPaths = updated
        saveSecretVideoPaths(context, updated); Toast.makeText(context, "Removed from Secret folder", Toast.LENGTH_SHORT).show()
    }

    // Long-pressing a folder (whether it's a Select-Folder TILE on the
    // TV-Shows-&-Folders shelf, OR a plain device folder row in the
    // "Folders" tab — see FIX #3 below) hides/unhides EVERY video in that
    // folder as one bulk action, using the same per-video hiddenPaths set
    // as regular Secret. This is the single folder-level hide mechanism in
    // the app now — both entry points funnel into this same function, so
    // behavior can't drift between the two again.
    fun toggleFolderSecret(folderVideoPaths: List<String>) {
        if (folderVideoPaths.isEmpty()) return
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        val pathSet = folderVideoPaths.toSet()
        val allHidden = pathSet.all { hiddenPaths.contains(it) }
        val updated = if (allHidden) hiddenPaths - pathSet else hiddenPaths + pathSet
        hiddenPaths = updated
        saveSecretVideoPaths(context, updated)
        onSecretChanged()
        Toast.makeText(
            context,
            if (allHidden) "Folder removed from Secret" else "Folder moved to Secret",
            Toast.LENGTH_SHORT
        ).show()
    }

    fun addFavorite(item: VideoWithMetadata) {
        val updated = favoritePaths + item.video.path; favoritePaths = updated
        saveFavoriteVideoPaths(context, updated); Toast.makeText(context, "Added to Favorites", Toast.LENGTH_SHORT).show()
    }

    fun removeFavorite(item: VideoWithMetadata) {
        val updated = favoritePaths - item.video.path; favoritePaths = updated
        saveFavoriteVideoPaths(context, updated); Toast.makeText(context, "Removed from Favorites", Toast.LENGTH_SHORT).show()
    }

    var pendingDeleteResult by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val deleteConsentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        pendingDeleteResult?.invoke(result.resultCode == Activity.RESULT_OK)
        pendingDeleteResult = null
    }

    fun finishDeleteSuccess(item: VideoWithMetadata) {
        clearPlaybackPosition(context, item.video.path)
        val updated = videos.filter { it.video.path != item.video.path }
        onVideosLoaded(updated)
        saveLibraryCache(context, updated)
        Toast.makeText(context, "File deleted", Toast.LENGTH_SHORT).show()
    }

    fun findMediaStoreUri(path: String): Uri? {
        val projection = arrayOf(MediaStore.Video.Media._ID)
        return try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection,
                "${MediaStore.Video.Media.DATA} = ?", arrayOf(path), null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                    ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                } else null
            }
        } catch (_: Exception) { null }
    }

    fun deleteVideoFile(item: VideoWithMetadata) {
        AlertDialog.Builder(context)
            .setTitle("Delete File")
            .setMessage("Delete \"${item.title}\"?\n\nThis cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                val f = File(item.video.path)
                val mediaUri = findMediaStoreUri(item.video.path)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && mediaUri != null) {
                    try {
                        pendingDeleteResult = { granted ->
                            if (granted) finishDeleteSuccess(item)
                            else Toast.makeText(context, "Delete cancelled", Toast.LENGTH_SHORT).show()
                        }
                        val pi = MediaStore.createDeleteRequest(context.contentResolver, listOf(mediaUri))
                        deleteConsentLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                    } catch (e: Exception) {
                        pendingDeleteResult = null
                        Toast.makeText(context, "Delete failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    try {
                        val deletedRows = if (mediaUri != null) context.contentResolver.delete(mediaUri, null, null) else 0
                        when {
                            deletedRows > 0 -> finishDeleteSuccess(item)
                            f.exists() && f.delete() -> finishDeleteSuccess(item)
                            else -> Toast.makeText(context, "Could not delete file", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: SecurityException) {
                        val recoverable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) e as? android.app.RecoverableSecurityException else null
                        if (recoverable != null) {
                            pendingDeleteResult = { granted ->
                                if (granted) finishDeleteSuccess(item)
                                else Toast.makeText(context, "Delete cancelled", Toast.LENGTH_SHORT).show()
                            }
                            deleteConsentLauncher.launch(IntentSenderRequest.Builder(recoverable.userAction.actionIntent.intentSender).build())
                        } else {
                            Toast.makeText(context, "Delete failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Delete failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (!isGranted) { scanStatus = "Storage permission denied"; return@rememberLauncherForActivityResult }
        scope.launch {
            isLoading = true; scanStatus = "Scanning device videos..."
            val deviceVideos = try { scanDeviceVideos(context) } catch (e: Exception) { e.printStackTrace(); scanStatus = "Scan failed: ${e.message ?: "Unknown error"}"; isLoading = false; return@launch }

            val smbShares = loadSmbShares(context)
            val smbVideos = mutableListOf<VideoWithMetadata>()
            for (share in smbShares) {
                scanStatus = "Scanning network share: ${share.displayName}..."
                when (val result = scanSmbShare(share)) {
                    is SmbScanResult.Success -> smbVideos.addAll(result.videos)
                    is SmbScanResult.Failure -> Toast.makeText(context, result.reason, Toast.LENGTH_LONG).show()
                }
            }

            scanStatus = "Scanning restricted folders..."
            val restrictedVideos = try { scanAllRestrictedFolders(context) } catch (e: Exception) { emptyList() }

            val scannedVideos = deviceVideos + smbVideos + restrictedVideos
            scanStatus = "Found ${scannedVideos.size} videos. Loading cached posters..."
            val instantList = scannedVideos.map { applyCachedMetadataIfAvailable(context, it) }
            onVideosLoaded(instantList); saveLibraryCache(context, instantList)

            val toEnrich = instantList.withIndex().filter { (_, item) ->
                !isRestrictedFolderItem(item) && (!hasUsefulOnlineMetadata(item) || needsRatingsUpgrade(item) || needsGenreUpgrade(item))
            }
            scanStatus = "Loaded ${instantList.size} videos. Updating missing posters & ratings..."

            val workingList = instantList.toMutableList()
            var completedCount = 0
            val semaphore = Semaphore(6)
            coroutineScope {
                toEnrich.map { (index, item) ->
                    async {
                        semaphore.withPermit {
                            val enriched = try { enrichVideoWithOnlineMetadata(context, item) } catch (e: Exception) { item }
                            completedCount++
                            scanStatus = "Metadata $completedCount/${toEnrich.size}: ${item.video.name.take(28)}"
                            if (enriched != item) {
                                workingList[index] = enriched
                                if (completedCount % 8 == 0) { val p = workingList.toList(); onVideosLoaded(p); saveLibraryCache(context, p) }
                            }
                        }
                    }
                }.awaitAll()
            }

            val finalList = workingList.toList(); onVideosLoaded(finalList); saveLibraryCache(context, finalList)
            scanStatus = "Library updated: ${finalList.size} videos"; delay(500); scanStatus = ""; isLoading = false
        }
    }

    val categories = listOf("All", "Movies", "TV Shows", "Folders", "Downloads", "Favorites", "Secret")

    val sortedVideos = remember(videos, sortOption) {
        when (sortOption) {
            LibrarySortOption.TITLE_AZ -> videos.sortedBy { it.title.lowercase() }
            LibrarySortOption.TITLE_ZA -> videos.sortedByDescending { it.title.lowercase() }
            LibrarySortOption.NEWEST -> videos.sortedByDescending { File(it.video.path).lastModified() }
            LibrarySortOption.OLDEST -> videos.sortedBy { File(it.video.path).lastModified() }
            LibrarySortOption.SIZE_BIG -> videos.sortedByDescending { File(it.video.path).length() }
            LibrarySortOption.SIZE_SMALL -> videos.sortedBy { File(it.video.path).length() }
        }
    }

    val visibleSortedVideos = sortedVideos.filter { !hiddenPaths.contains(it.video.path) && !videoIsInsideSecretFolder(it, hiddenFolders) }
    val secretVideos = sortedVideos.filter { hiddenPaths.contains(it.video.path) || videoIsInsideSecretFolder(it, hiddenFolders) }
    val favoriteVideos = visibleSortedVideos.filter { favoritePaths.contains(it.video.path) }

    // Restricted (Select-Folder) folders that were bulk-secreted as a whole
    // via toggleFolderSecret() — every one of their videos is in hiddenPaths.
    // These render as ONE folder card inside Secret (files and folders "go
    // to secret as they are"), not as hundreds of individual loose entries.
    // Long-pressing that card (same toggleFolderSecret call) unlocks the
    // whole folder in one action.
    data class SecretFolderGroup(val folder: RestrictedFolder, val items: List<VideoWithMetadata>)
    val secretRestrictedFolderGroups: List<SecretFolderGroup> = if (!secretUnlocked) emptyList() else {
        val restrictedInLibrary = sortedVideos.filter { it.type.equals("restricted", ignoreCase = true) }
        loadRestrictedFolders(context).mapNotNull { folder ->
            val items = restrictedInLibrary.filter { folderIdFromRestrictedMarker(it.video.folderPath) == folder.id }
            if (items.isNotEmpty() && items.all { hiddenPaths.contains(it.video.path) }) SecretFolderGroup(folder, items) else null
        }
    }
    val secretGroupedPaths = secretRestrictedFolderGroups.flatMap { group -> group.items.map { it.video.path } }.toSet()

    val videoFolders = remember(visibleSortedVideos) { groupVideosByFolder(visibleSortedVideos.filterNot { it.type.equals("restricted", ignoreCase = true) }) }

    val filteredVideos = when (selectedCategory) {
        "Secret" -> if (secretUnlocked) secretVideos.filter { it.video.path !in secretGroupedPaths } else emptyList()
        "Favorites" -> favoriteVideos
        "TV Shows" -> emptyList()
        "Folders" -> emptyList()
        "Downloads" -> visibleSortedVideos.filter { !it.type.equals("movie", ignoreCase = true) && !it.type.equals("tv", ignoreCase = true) && !it.type.equals("restricted", ignoreCase = true) }
        "Movies" -> visibleSortedVideos.filter { it.type.equals("movie", ignoreCase = true) }
        else -> visibleSortedVideos.filter { !it.type.equals("tv", ignoreCase = true) && !it.type.equals("restricted", ignoreCase = true) }
    }

    val tvGroups = groupTvShows(sortedVideos.filter { it.type.equals("tv", ignoreCase = true) && !hiddenPaths.contains(it.video.path) && !videoIsInsideSecretFolder(it, hiddenFolders) })

    Box(modifier = Modifier.fillMaxSize().background(SpaceBlack)) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 28.dp)
        ) {
            // ── Header (FIX #5) ──────────────────────────────────────────
            // "Library" title text removed — redundant, we're already in
            // the Library tab (bottom nav shows that). Category chips now
            // lead at the very top. Scan / Refresh / Sort / Grid-List
            // collapse into one row of small glowing icon buttons instead
            // of two full-width pill buttons + a separate sort pill +
            // separate List/Grid button, reclaiming a lot of vertical
            // space. Each icon gets a distinct tint (still same amber-glow
            // treatment as the signature play button — radial glow +
            // border in the icon's own color) so they read as separate
            // controls at a glance.
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(items = categories) { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { if (category == "Secret") openSecretFolder() else selectedCategory = category },
                                label = { Text(category) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AmberGlow.copy(alpha = 0.18f),
                                    selectedLabelColor = AmberCore,
                                    containerColor = Color.Transparent,
                                    labelColor = TextMuted
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            LibraryToolIconButton(
                                icon = if (isLoading) Icons.Filled.Radar else Icons.Filled.Radar,
                                tint = AmberCore,
                                contentDescription = "Scan Device Videos",
                                enabled = !isLoading,
                                onClick = { permissionLauncher.launch(permission) }
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        LibraryToolIconButton(
                            icon = Icons.Filled.Refresh,
                            tint = Color(0xFF6FCF97),
                            contentDescription = "Refresh / Clear Cache",
                            enabled = !isLoading,
                            onClick = { clearLibraryCache(context); onVideosLoaded(emptyList()); scanStatus = "Cache cleared. Scan again." }
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box {
                            LibraryToolIconButton(
                                icon = Icons.Filled.Sort,
                                tint = Color(0xFF56CCF2),
                                contentDescription = "Sort by: ${sortOption.label}",
                                onClick = { sortMenuExpanded = true }
                            )
                            DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }, modifier = Modifier.background(SpaceMid)) {
                                LibrarySortOption.values().forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(text = option.label, color = if (sortOption == option) AmberCore else TextBright, fontWeight = if (sortOption == option) FontWeight.Bold else FontWeight.Normal) },
                                        onClick = { sortOption = option; sortMenuExpanded = false }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        LibraryToolIconButton(
                            icon = if (isGridMode) Icons.Filled.ViewList else Icons.Filled.GridView,
                            tint = Color(0xFFBB86FC),
                            contentDescription = if (isGridMode) "Switch to List" else "Switch to Grid",
                            onClick = { isGridMode = !isGridMode }
                        )

                        if (scanStatus.isNotBlank()) {
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = scanStatus,
                                color = TextMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    loadLibraryCache(context)?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Last Scan: " + java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(it.timestamp)), color = TextFaint, fontSize = 11.sp)
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                }
            }

            // ── Collections shelf — ONLY on "All" (the default overview),
            // same fix as the TV/Folders shelf below: this used to render
            // unconditionally on every category tab.
            if (selectedCategory == "All") {
                run {
                    data class CollectionShelfEntry(val key: String, val displayName: String, val backdropUrl: String?, val isCurated: Boolean, val collectionId: Int?)
                    val nativeEntries = visibleSortedVideos
                        .filter { it.collectionId != null && it.collectionName != null }
                        .distinctBy { it.collectionId }
                        .map { CollectionShelfEntry("native:${it.collectionId}", it.collectionName!!, it.backdropUrl, false, it.collectionId) }
                    val curatedNames = visibleSortedVideos.flatMap { it.curatedCollections }.distinct()
                    val curatedEntries = curatedNames.map { name ->
                        val backdrop = visibleSortedVideos.firstOrNull { it.curatedCollections.contains(name) && !it.backdropUrl.isNullOrBlank() }?.backdropUrl
                        CollectionShelfEntry("curated:$name", name, backdrop, true, null)
                    }
                    val collectionShelf = (nativeEntries + curatedEntries).sortedBy { it.displayName.lowercase() }

                    if (collectionShelf.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column {
                                Text(text = "Collections", color = TextBright, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(10.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(items = collectionShelf, key = { it.key }) { entry ->
                                        CollectionShelfCard(
                                            title = entry.displayName,
                                            backdropUrl = entry.backdropUrl,
                                            onClick = {
                                                if (entry.isCurated) onCuratedCollectionClick(entry.displayName)
                                                else entry.collectionId?.let { onNativeCollectionClick(it, entry.displayName) }
                                            }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                            }
                        }
                    }
                }
            }

            // ── TV Shows & Folders — ONE combined scrollable row (same
            // pattern as the Collections shelf above), not two separate
            // rows stacked vertically. Previously TV shows and folders each
            // got their own LazyRow, which is why a single TV show and a
            // single folder appeared to stack on top of each other instead
            // of sitting side by side. Only shown on "All" (TV shows) /
            // "All"+"Folders"+"Downloads" (folders) per the category gate.
            if (selectedCategory in listOf("All", "TV Shows", "Folders", "Downloads")) {
                run {
                    data class RestrictedShelfEntry(val folder: RestrictedFolder, val items: List<VideoWithMetadata>)
                    val restrictedItems = visibleSortedVideos.filter { it.type.equals("restricted", ignoreCase = true) }
                    val restrictedShelf = if (restrictedItems.isEmpty()) emptyList() else {
                        loadRestrictedFolders(context).mapNotNull { folder ->
                            val items = restrictedItems.filter { folderIdFromRestrictedMarker(it.video.folderPath) == folder.id }
                            if (items.isEmpty()) null else RestrictedShelfEntry(folder, items)
                        }
                    }

                    val showTvInShelf = selectedCategory in listOf("All", "TV Shows") && tvGroups.isNotEmpty()
                    val showFoldersInShelf = selectedCategory in listOf("All", "Folders", "Downloads") && restrictedShelf.isNotEmpty()

                    // Combined into one ordered list — TV shows first, then
                    // folders — rendered by ONE LazyRow below instead of two.
                    val combinedShelf: List<Any> =
                        (if (showTvInShelf) tvGroups else emptyList()) +
                        (if (showFoldersInShelf) restrictedShelf else emptyList())

                    if (combinedShelf.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column {
                                Text(text = "TV Shows & Folders", color = TextBright, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(10.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    items(
                                        items = combinedShelf,
                                        key = { entry ->
                                            when (entry) {
                                                is TvGroup -> "tv:${entry.showName}"
                                                is RestrictedShelfEntry -> "folder:${entry.folder.id}"
                                                else -> entry.hashCode().toString()
                                            }
                                        }
                                    ) { entry ->
                                        when (entry) {
                                            is TvGroup -> Column(
                                                modifier = Modifier
                                                    .width(145.dp)
                                                    .combinedClickable(
                                                        onClick = { onTvGroupClick(entry) },
                                                        onLongClick = { entry.episodes.firstOrNull()?.let { openContextSheet(it) } }
                                                    )
                                            ) {
                                                PosterBox(posterUrl = entry.posterUrl, modifier = Modifier.fillMaxWidth().height(210.dp))
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(text = entry.showName, color = TextBright, maxLines = 1, fontWeight = FontWeight.SemiBold)
                                                Text(text = "${entry.episodes.size} Episodes", color = TextMuted, fontSize = 12.sp)
                                            }
                                            is RestrictedShelfEntry -> {
                                                val thumbnailSourcePath = entry.folder.lastPlayedVideoPath
                                                    ?.takeIf { path -> entry.items.any { it.video.path == path } }
                                                    ?: entry.items.firstOrNull()?.video?.path
                                                RestrictedFolderShelfCard(
                                                    title = entry.folder.displayName,
                                                    count = entry.items.size,
                                                    thumbnailVideoPath = thumbnailSourcePath,
                                                    onClick = { onRestrictedFolderClick(entry.folder) },
                                                    onLongClick = { folderSecretConfirm = entry.folder.displayName to entry.items.map { it.video.path } }
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                            }
                        }
                    }
                }
            }

            // ── Genres shelf — ONLY on "All". Circular glowing icon chips,
            // same horizontal-scroll format as Collections (not a wrapping
            // grid), spacing tightened to fit more per screen. Genre names
            // are normalized first so TMDB's movie/TV naming differences
            // (e.g. "Science Fiction" vs "Sci-Fi & Fantasy") collapse into
            // one chip instead of showing as near-duplicates.
            if (selectedCategory == "All") {
                run {
                    val genreNames = visibleSortedVideos
                        .flatMap { it.genres }
                        .map { normalizeGenreName(it) }
                        .distinct()
                        .sortedBy { it.lowercase() }
                    if (genreNames.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column {
                                Text(text = "Genres", color = TextBright, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(12.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(items = genreNames, key = { it }) { genre ->
                                        GenreIconChip(name = genre, onClick = { onGenreClick(genre) })
                                    }
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                            }
                        }
                    }
                }
            }

            if (selectedCategory == "Secret" && !secretUnlocked) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(modifier = Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "🔒 Secret folder is locked", color = TextBright, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { openSecretFolder() }, shape = RoundedCornerShape(40.dp), colors = ButtonDefaults.buttonColors(containerColor = AmberGlow.copy(alpha = 0.90f), contentColor = Color.Black)) {
                                Text("Unlock Secret Folder", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (selectedCategory == "Folders") {
                if (videoFolders.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            Text(text = "No folders found. Scan your library first.", color = TextMuted, fontSize = 15.sp)
                        }
                    }
                } else {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(text = "Folders (${videoFolders.size})", color = TextBright, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }

                    videoFolders.forEach { folder ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            val isExpanded = expandedFolders.contains(folder.folderPath)
                            // FIX (#3): plain device folders in this tab
                            // previously had no long-press action at all —
                            // only expand/collapse on tap. That meant a
                            // long-press here either did nothing, or (if the
                            // finger landed on an already-expanded video
                            // card underneath) silently hid just that ONE
                            // file — which is almost certainly what looked
                            // like "a random file getting hidden" instead of
                            // the whole folder. Long-press now routes
                            // through the exact same folderSecretConfirm ->
                            // toggleFolderSecret(all paths in folder) path
                            // used by the TV Shows & Folders shelf, so
                            // behavior is identical and correct in both
                            // places.
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .glassPanel(cornerRadius = 14.dp)
                                    .combinedClickable(
                                        onClick = {
                                            expandedFolders = if (isExpanded) expandedFolders - folder.folderPath else expandedFolders + folder.folderPath
                                        },
                                        onLongClick = {
                                            folderSecretConfirm = folder.folderName to folder.videos.map { it.video.path }
                                        }
                                    )
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Folder,
                                    contentDescription = null,
                                    tint = AmberGlow,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = folder.folderName, color = TextBright, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                    Text(text = "${folder.videos.size} video${if (folder.videos.size != 1) "s" else ""}", color = TextMuted, fontSize = 11.sp)
                                }
                                Icon(
                                    imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        if (expandedFolders.contains(folder.folderPath)) {
                            if (isGridMode) {
                                items(items = folder.videos, key = { it.video.path }) { item ->
                                    LibraryGridCard(item = item, onClick = { onItemClick(item) }, onPlayClick = onPlayClick, onLongPress = { openContextSheet(it) })
                                }
                                val remainder = folder.videos.size % 3
                                if (remainder != 0) {
                                    repeat(3 - remainder) {
                                        item { Spacer(modifier = Modifier.fillMaxWidth()) }
                                    }
                                }
                            } else {
                                items(items = folder.videos, key = { it.video.path }, span = { GridItemSpan(maxLineSpan) }) { item ->
                                    LibraryCard(item = item, onClick = { onItemClick(item) }, onLongPress = { openContextSheet(it) })
                                }
                            }
                        }
                    }
                }
            }

            if (selectedCategory != "Folders" && !isLoading && filteredVideos.isEmpty() && tvGroups.isEmpty() && !(selectedCategory == "Secret" && !secretUnlocked)) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(modifier = Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                        Text(text = "No videos found. Tap Scan Device Videos.", color = TextMuted, fontSize = 15.sp)
                    }
                }
            }

            if (selectedCategory == "Secret" && secretUnlocked && secretRestrictedFolderGroups.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Text(text = "Secret Folders", color = TextBright, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(items = secretRestrictedFolderGroups, key = { "secretfolder:${it.folder.id}" }) { group ->
                                val thumbnailSourcePath = group.folder.lastPlayedVideoPath
                                    ?.takeIf { path -> group.items.any { it.video.path == path } }
                                    ?: group.items.firstOrNull()?.video?.path
                                RestrictedFolderShelfCard(
                                    title = group.folder.displayName,
                                    count = group.items.size,
                                    thumbnailVideoPath = thumbnailSourcePath,
                                    onClick = { onRestrictedFolderClick(group.folder) },
                                    onLongClick = { folderSecretConfirm = group.folder.displayName to group.items.map { it.video.path } }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }

            if (filteredVideos.isNotEmpty() && selectedCategory != "Folders") {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = when (selectedCategory) { "Movies" -> "Movies"; "Downloads" -> "Downloads"; "Favorites" -> "Favorites"; "Secret" -> "Secret Folder"; else -> "Movies & Downloads" },
                        color = TextBright, fontSize = 22.sp, fontWeight = FontWeight.Bold
                    )
                }

                if (isGridMode) {
                    items(items = filteredVideos, key = { it.video.path }) { item ->
                        LibraryGridCard(item = item, onClick = { onItemClick(item) }, onPlayClick = onPlayClick, onLongPress = { openContextSheet(it) })
                    }
                } else {
                    items(items = filteredVideos, key = { it.video.path }, span = { GridItemSpan(maxLineSpan) }) { item ->
                        LibraryCard(item = item, onClick = { onItemClick(item) }, onLongPress = { openContextSheet(it) })
                    }
                }
            }
        }

        AnimatedVisibility(visible = contextSheetItem != null, enter = fadeIn(animationSpec = tween(160)), exit = fadeOut(animationSpec = tween(180))) {
            val selectedItem = contextSheetItem
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable { contextSheetItem = null },
                contentAlignment = Alignment.Center
            ) {
                if (selectedItem != null) {
                    val isFavorite = favoritePaths.contains(selectedItem.video.path)
                    val isHidden = hiddenPaths.contains(selectedItem.video.path)
                    val isInSecretFolder = videoIsInsideSecretFolder(selectedItem, hiddenFolders)

                    Column(
                        modifier = Modifier
                            .width(180.dp)
                            .glassPanel(cornerRadius = 20.dp, fill = SpaceMid.copy(alpha = 0.98f))
                            .clickable(enabled = false) { }
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .width(34.dp)
                                    .height(50.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SpaceDeep)
                            ) {
                                if (!selectedItem.posterUrl.isNullOrBlank()) {
                                    AsyncImage(model = selectedItem.posterUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = selectedItem.title,
                                color = TextBright, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = GlassBorderBottom)
                        Spacer(modifier = Modifier.height(10.dp))

                        // Icon-only actions — no text labels, small glowing
                        // circular buttons. FlowRow wraps to a second row on
                        // its own if it ever needs to.
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SheetIconButton(icon = Icons.Rounded.PlayArrow, tint = AmberCore, contentDescription = "Play") {
                                contextSheetItem = null; onPlayClick(selectedItem)
                            }
                            SheetIconButton(
                                icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                tint = if (isFavorite) AmberCore else TextBright,
                                contentDescription = if (isFavorite) "Remove from Favorites" else "Add to Favorites"
                            ) {
                                if (isFavorite) removeFavorite(selectedItem) else addFavorite(selectedItem)
                                contextSheetItem = null
                            }
                            SheetIconButton(
                                icon = if (isHidden) Icons.Filled.LockOpen else Icons.Rounded.Lock,
                                tint = TextBright,
                                contentDescription = if (isHidden) "Remove from Secret" else "Move to Secret"
                            ) {
                                if (isHidden) unhideVideo(selectedItem) else hideVideo(selectedItem)
                                contextSheetItem = null
                            }
                            // "Hide Entire Folder" removed as an option going
                            // forward — Secret (above) is the one hide
                            // mechanism now, simpler and easier to reverse.
                            // Still shown ONLY for items already hidden this
                            // way from before, so nothing already hidden is
                            // stranded with no way back to the library.
                            if (isInSecretFolder) {
                                SheetIconButton(
                                    icon = Icons.Filled.Folder,
                                    tint = TextBright,
                                    contentDescription = "Unlock Folder"
                                ) {
                                    unhideEntireFolder(selectedItem)
                                    contextSheetItem = null
                                }
                            }
                            SheetIconButton(icon = Icons.Rounded.Delete, tint = Color(0xFFFF5252), contentDescription = "Delete File") {
                                contextSheetItem = null
                                deleteVideoFile(selectedItem)
                            }
                        }
                    }
                }
            }
        }

        // Folder-level Secret confirmation — long-pressing a folder tile
        // now asks first instead of toggling instantly.
        // FIX (#2): restyled to use the same SheetIconButton glowing-circle
        // treatment as the single-file context sheet above, instead of the
        // old flat text-pill Cancel/Confirm buttons — so both long-press
        // menus in the app now share one visual language.
        folderSecretConfirm?.let { (folderName, paths) ->
            val allHidden = paths.isNotEmpty() && paths.all { hiddenPaths.contains(it) }
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f)).clickable { folderSecretConfirm = null },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .width(220.dp)
                        .glassPanel(cornerRadius = 20.dp, fill = SpaceMid.copy(alpha = 0.98f))
                        .clickable(enabled = false) { }
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SpaceDeep),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Filled.Folder, contentDescription = null, tint = AmberGlow, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = folderName, color = TextBright, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(text = "${paths.size} files", color = TextMuted, fontSize = 10.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = GlassBorderBottom)
                    Spacer(modifier = Modifier.height(10.dp))

                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SheetIconButton(
                            icon = if (allHidden) Icons.Filled.LockOpen else Icons.Rounded.Lock,
                            tint = AmberCore,
                            contentDescription = if (allHidden) "Remove Folder from Secret" else "Move Folder to Secret"
                        ) {
                            toggleFolderSecret(paths)
                            folderSecretConfirm = null
                        }
                        SheetIconButton(icon = Icons.Filled.Close, tint = TextBright, contentDescription = "Cancel") {
                            folderSecretConfirm = null
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryToolIconButton(
    icon: ImageVector,
    tint: Color,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(GlassSurfaceStrong)
            .background(Brush.radialGradient(listOf(tint.copy(alpha = if (enabled) 0.30f else 0.10f), Color.Transparent), radius = 80f))
            .border(1.2.dp, tint.copy(alpha = if (enabled) 0.60f else 0.20f), CircleShape)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = if (enabled) tint else tint.copy(alpha = 0.35f), modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun SheetIconButton(icon: ImageVector, tint: Color, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(GlassSurfaceStrong)
            .background(Brush.radialGradient(listOf(tint.copy(alpha = 0.30f), Color.Transparent), radius = 90f))
            .border(1.2.dp, tint.copy(alpha = 0.55f), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun CollectionShelfCard(title: String, backdropUrl: String?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(220.dp)
            .height(110.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(SpaceMid)
            .clickable { onClick() }
    ) {
        if (!backdropUrl.isNullOrBlank()) {
            AsyncImage(model = backdropUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.62f)))
            )
        )
        Text(
            text = title,
            color = TextBright,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RestrictedFolderShelfCard(
    title: String,
    count: Int,
    thumbnailVideoPath: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    // Same portrait card shape as the TV Shows row (145dp wide, 210dp
    // poster, title + count below), now with an amber-glass border and a
    // slightly zoomed/cropped thumbnail so it reads as a designed card
    // instead of a bare rectangle — previously this had none of the glow/
    // border treatment every other poster card on this screen already uses.
    // Long-press now opens the same context sheet movies/TV posters get
    // (Play / Favorite / Secret / Hide folder / Delete), using the folder's
    // representative item — previously long-press did nothing at all here.
    Column(
        modifier = Modifier
            .width(145.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(SpaceMid)
                .border(1.dp, Brush.verticalGradient(listOf(AmberGlow.copy(alpha = 0.55f), AmberDeep.copy(alpha = 0.25f))), RoundedCornerShape(14.dp))
        ) {
            // No posterUrl on purpose — restricted-folder items never go
            // through TMDB enrichment, so there's no online artwork.
            // PosterBox falls back to generating a thumbnail directly from a
            // frame of the video file itself when posterUrl is null.
            // Slight scale-up (1.10x) crops in a touch tighter for a more
            // "designed" zoomed look instead of the raw untouched frame.
            PosterBox(
                posterUrl = null,
                videoPath = thumbnailVideoPath,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = 1.08f; scaleY = 1.08f }
            )
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.45f)))
                )
            )
            // Folder-type badge — generic icon only (see folderIconFor), not
            // an actual brand logo.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = folderIconFor(title), contentDescription = null, tint = AmberCore, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = title, color = TextBright, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
        Text(text = "$count file${if (count != 1) "s" else ""}", color = TextMuted, fontSize = 12.sp)
    }
}

private fun looksLikePersonalOrCameraVideo(fileName: String, cleanedName: String): Boolean {
    val lower = fileName.lowercase(); val cleaned = cleanedName.trim().lowercase()
    if (cleaned.length < 4) return true
    return lower.startsWith("vid_") || lower.startsWith("img_") || lower.startsWith("video_") ||
            lower.startsWith("screenrecord") || lower.startsWith("screen_record") ||
            lower.contains("whatsapp video") || lower.contains("camera") ||
            lower.matches(Regex(".*\\b(19|20)\\d{6}[_-]?(19|20)?\\d{0,6}.*"))
}
