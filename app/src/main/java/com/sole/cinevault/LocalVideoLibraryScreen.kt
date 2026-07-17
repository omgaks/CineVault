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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
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

// NOTE: findCineActivity() removed — lives in Screens.kt as the single shared version.

// Persists the library grid's scroll position across navigation (Detail -> back).
// A plain object survives composable disposal since it isn't tied to the
// composition, unlike remember/rememberSaveable which reset when this screen
// leaves the composition entirely.
private object LibraryScrollState {
    var index: Int = 0
    var offset: Int = 0
    var category: String = "All"
    var sort: LibrarySortOption = LibrarySortOption.TITLE_AZ
    var gridMode: Boolean = true
}

// Data class for folder grouping
private data class VideoFolder(
    val folderName: String,
    val folderPath: String,
    val videos: List<VideoWithMetadata>
)

// Group videos by their parent folder
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
    onNativeCollectionClick: (Int, String) -> Unit = {},
    onCuratedCollectionClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    var isLoading by remember { mutableStateOf(false) }
    var scanStatus by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(LibraryScrollState.category) }
    var isGridMode by remember { mutableStateOf(LibraryScrollState.gridMode) }
    var sortOption by remember { mutableStateOf(LibraryScrollState.sort) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var secretUnlocked by remember { mutableStateOf(false) }
    var hiddenPaths by remember { mutableStateOf<Set<String>>(loadSecretVideoPaths(context)) }
    var hiddenFolders by remember { mutableStateOf<Set<String>>(loadSecretFolderPaths(context)) }
    var favoritePaths by remember { mutableStateOf(loadFavoriteVideoPaths(context)) }
    var contextSheetItem by remember { mutableStateOf<VideoWithMetadata?>(null) }

    // FOLDER VIEW: track which folders are expanded
    var expandedFolders by remember { mutableStateOf<Set<String>>(emptySet()) }

    val keyguardManager = remember { context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager }

    // Restores the grid's scroll position from LibraryScrollState so returning
    // from the Detail screen lands you back where you left off, not the top.
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

    val secretUnlockLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            secretUnlocked = true
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

    fun hideEntireFolder(item: VideoWithMetadata) {
        val folderPath = getVideoFolderKey(item); if (folderPath.isBlank()) return
        val updatedFolders = hiddenFolders + folderPath; hiddenFolders = updatedFolders
        saveSecretFolderPaths(context, updatedFolders); clearPlaybackFolderPositions(context, folderPath)
        createNoMediaFileForFolder(folderPath); onSecretChanged()
        Toast.makeText(context, "Folder hidden in CineVault. Gallery hide is not guaranteed on all Android versions.", Toast.LENGTH_LONG).show()
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

    fun addFavorite(item: VideoWithMetadata) {
        val updated = favoritePaths + item.video.path; favoritePaths = updated
        saveFavoriteVideoPaths(context, updated); Toast.makeText(context, "Added to Favorites", Toast.LENGTH_SHORT).show()
    }

    fun removeFavorite(item: VideoWithMetadata) {
        val updated = favoritePaths - item.video.path; favoritePaths = updated
        saveFavoriteVideoPaths(context, updated); Toast.makeText(context, "Removed from Favorites", Toast.LENGTH_SHORT).show()
    }

    // DELETE — direct MediaStore flow, not routed through FileManagementHelper.
    // That helper's delete path is apparently built for the app's own
    // subtitle files (plain local storage, no MediaStore involved), which is
    // why reusing it for scanned videos silently kept failing. Videos here
    // are indexed by MediaStore and live outside the app's own storage, so
    // scoped storage requires going through MediaStore's consent flow.
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

    // Looks up the MediaStore content Uri for a file path — needed because
    // deleting through the raw java.io.File path is what scoped storage blocks;
    // deleting through the matching content:// Uri is what's actually allowed.
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
                    // Android 11+: proactively trigger the system's own delete
                    // confirmation for this MediaStore item. This is the only
                    // reliable path under scoped storage for files the app
                    // didn't create itself.
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
                        // Android 10 throws a RecoverableSecurityException with an
                        // IntentSender the system provides specifically to grant
                        // one-time delete permission for this file.
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
            val scannedVideos = try { scanDeviceVideos(context) } catch (e: Exception) { e.printStackTrace(); scanStatus = "Scan failed: ${e.message ?: "Unknown error"}"; isLoading = false; return@launch }
            scanStatus = "Found ${scannedVideos.size} videos. Loading cached posters..."
            val instantList = scannedVideos.map { applyCachedMetadataIfAvailable(context, it) }
            onVideosLoaded(instantList); saveLibraryCache(context, instantList)

            // Which items actually need a network fetch (missing metadata, or
            // cached but predates the ratings/genre-upgrade passes).
            val toEnrich = instantList.withIndex().filter { (_, item) ->
                !hasUsefulOnlineMetadata(item) || needsRatingsUpgrade(item) || needsGenreUpgrade(item)
            }
            scanStatus = "Loaded ${instantList.size} videos. Updating missing posters & ratings..."

            // SPEED FIX: this used to enrich items one at a time, fully
            // sequentially — each item involves ~2-3 chained network calls
            // (TMDB search, TMDB details+credits+keywords, OMDB ratings), so
            // a large library could take several minutes just waiting on
            // network round-trips one by one. Bounded parallelism (6 at a
            // time) processes the whole batch far faster while still being
            // polite to TMDB/OMDB's rate limits rather than firing everything
            // at once.
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

    // FOLDERS category added, Downloads kept
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

    // FOLDER grouping
    val videoFolders = remember(visibleSortedVideos) { groupVideosByFolder(visibleSortedVideos) }

    val filteredVideos = when (selectedCategory) {
        "Secret" -> if (secretUnlocked) secretVideos else emptyList()
        "Favorites" -> favoriteVideos
        "TV Shows" -> emptyList()
        "Folders" -> emptyList() // handled separately below
        "Downloads" -> visibleSortedVideos.filter { !it.type.equals("movie", ignoreCase = true) && !it.type.equals("tv", ignoreCase = true) }
        "Movies" -> visibleSortedVideos.filter { it.type.equals("movie", ignoreCase = true) }
        else -> visibleSortedVideos.filter { !it.type.equals("tv", ignoreCase = true) }
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
            // Header
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Library", color = TextBright, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Button(onClick = { isGridMode = !isGridMode }, shape = RoundedCornerShape(30.dp), colors = ButtonDefaults.buttonColors(containerColor = GlassSurface, contentColor = TextBright)) {
                            Text(if (isGridMode) "List" else "Grid")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(enabled = !isLoading, onClick = { permissionLauncher.launch(permission) }, shape = RoundedCornerShape(40.dp), colors = ButtonDefaults.buttonColors(containerColor = GlassSurface, contentColor = TextBright)) {
                            Text(if (isLoading) scanStatus.ifBlank { "Scanning..." } else "Scan Device Videos")
                        }
                        OutlinedButton(enabled = !isLoading, onClick = { clearLibraryCache(context); onVideosLoaded(emptyList()); scanStatus = "Cache cleared. Scan again." }, shape = RoundedCornerShape(40.dp)) {
                            Text("Refresh")
                        }
                    }

                    if (scanStatus.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(modifier = Modifier.fillMaxWidth().glassPanel(cornerRadius = 18.dp).padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(text = scanStatus, color = TextBright, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    loadLibraryCache(context)?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Last Scan: " + java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(it.timestamp)), color = TextFaint, fontSize = 11.sp)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

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

                    Box {
                        Box(modifier = Modifier.clip(RoundedCornerShape(50)).background(AmberGlow.copy(alpha = 0.18f)).clickable { sortMenuExpanded = true }.padding(horizontal = 18.dp, vertical = 10.dp)) {
                            Text(text = "Sort by: ${sortOption.label}", color = AmberCore, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }, modifier = Modifier.background(SpaceMid)) {
                            LibrarySortOption.values().forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(text = option.label, color = if (sortOption == option) AmberCore else TextBright, fontWeight = if (sortOption == option) FontWeight.Bold else FontWeight.Normal) },
                                    onClick = { sortOption = option; sortMenuExpanded = false }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            // ── Collections shelf — native TMDB collections + curated ones
            // (e.g. Marvel Cinematic Universe), only shown if the library
            // actually has any. Respects hidden/secret items same as the
            // rest of this screen (built from visibleSortedVideos).
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

            // ── Genres shelf — every distinct genre present in the library ──
            run {
                val genreNames = visibleSortedVideos.flatMap { it.genres }.distinct().sortedBy { it.lowercase() }
                if (genreNames.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            Text(text = "Genres", color = TextBright, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(10.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(items = genreNames, key = { it }) { genre ->
                                    Box(
                                        modifier = Modifier.clip(RoundedCornerShape(50)).background(GlassSurface)
                                            .border(1.dp, Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderBottom)), RoundedCornerShape(50))
                                            .clickable { onGenreClick(genre) }
                                            .padding(horizontal = 16.dp, vertical = 9.dp)
                                    ) {
                                        Text(text = genre, color = TextBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }
                }
            }

            // Secret locked
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

            // FOLDER VIEW
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
                        // Folder header row
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            val isExpanded = expandedFolders.contains(folder.folderPath)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .glassPanel(cornerRadius = 14.dp)
                                    .clickable {
                                        expandedFolders = if (isExpanded) expandedFolders - folder.folderPath else expandedFolders + folder.folderPath
                                    }
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

                        // Expanded folder contents — respects isGridMode toggle
                        if (expandedFolders.contains(folder.folderPath)) {
                            if (isGridMode) {
                                items(items = folder.videos, key = { it.video.path }) { item ->
                                    LibraryGridCard(item = item, onClick = { onItemClick(item) }, onPlayClick = onPlayClick, onLongPress = { openContextSheet(it) })
                                }
                                // Fill remaining columns if last row isn't complete
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

            // Empty state for non-folder views
            if (selectedCategory != "Folders" && !isLoading && filteredVideos.isEmpty() && tvGroups.isEmpty() && !(selectedCategory == "Secret" && !secretUnlocked)) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(modifier = Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                        Text(text = "No videos found. Tap Scan Device Videos.", color = TextMuted, fontSize = 15.sp)
                    }
                }
            }

            // TV Shows row — long-press the poster for actions
            if (tvGroups.isNotEmpty() && selectedCategory in listOf("All", "TV Shows")) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Text(text = "TV Shows", color = TextBright, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(items = tvGroups, key = { it.showName }) { show ->
                                Column(
                                    modifier = Modifier
                                        .width(145.dp)
                                        .combinedClickable(
                                            onClick = { onTvGroupClick(show) },
                                            onLongClick = { show.episodes.firstOrNull()?.let { openContextSheet(it) } }
                                        )
                                ) {
                                    PosterBox(posterUrl = show.posterUrl, modifier = Modifier.fillMaxWidth().height(210.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(text = show.showName, color = TextBright, maxLines = 1, fontWeight = FontWeight.SemiBold)
                                    Text(text = "${show.episodes.size} Episodes", color = TextMuted, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Regular video grid/list — long-press for actions, no more ⋮
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

        // ── Long-press context sheet — the premium replacement for the ⋮ menu ──
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
                            .width(300.dp)
                            .glassPanel(cornerRadius = 24.dp, fill = SpaceMid.copy(alpha = 0.98f))
                            .clickable(enabled = false) { }
                            .padding(14.dp)
                    ) {
                        // Header: poster thumbnail + title
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .width(56.dp)
                                    .height(82.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SpaceDeep)
                            ) {
                                if (!selectedItem.posterUrl.isNullOrBlank()) {
                                    AsyncImage(model = selectedItem.posterUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = selectedItem.title, color = TextBright, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (selectedItem.subtitle.isNotBlank()) {
                                    Text(text = selectedItem.subtitle, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = GlassBorderBottom)
                        Spacer(modifier = Modifier.height(8.dp))

                        SheetActionRow(icon = Icons.Rounded.PlayArrow, label = "Play", tint = AmberCore) {
                            contextSheetItem = null; onPlayClick(selectedItem)
                        }
                        SheetActionRow(
                            icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            label = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                            tint = if (isFavorite) AmberCore else TextBright
                        ) {
                            if (isFavorite) removeFavorite(selectedItem) else addFavorite(selectedItem)
                            contextSheetItem = null
                        }
                        SheetActionRow(
                            icon = if (isHidden) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                            label = if (isHidden) "Remove from Secret" else "Move to Secret",
                            tint = TextBright
                        ) {
                            if (isHidden) unhideVideo(selectedItem) else hideVideo(selectedItem)
                            contextSheetItem = null
                        }
                        SheetActionRow(
                            icon = Icons.Filled.Folder,
                            label = if (isInSecretFolder) "Unlock Folder" else "Hide Entire Folder",
                            tint = TextBright
                        ) {
                            if (isInSecretFolder) unhideEntireFolder(selectedItem) else hideEntireFolder(selectedItem)
                            contextSheetItem = null
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider(color = GlassBorderBottom)
                        Spacer(modifier = Modifier.height(4.dp))

                        SheetActionRow(icon = Icons.Rounded.Delete, label = "Delete File", tint = Color(0xFFFF5252)) {
                            contextSheetItem = null
                            deleteVideoFile(selectedItem)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetActionRow(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = label, color = if (tint == Color(0xFFFF5252)) tint else TextBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
                Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))
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

private fun looksLikePersonalOrCameraVideo(fileName: String, cleanedName: String): Boolean {
    val lower = fileName.lowercase(); val cleaned = cleanedName.trim().lowercase()
    if (cleaned.length < 4) return true
    return lower.startsWith("vid_") || lower.startsWith("img_") || lower.startsWith("video_") ||
            lower.startsWith("screenrecord") || lower.startsWith("screen_record") ||
            lower.contains("whatsapp video") || lower.contains("camera") ||
            lower.matches(Regex(".*\\b(19|20)\\d{6}[_-]?(19|20)?\\d{0,6}.*"))
}
