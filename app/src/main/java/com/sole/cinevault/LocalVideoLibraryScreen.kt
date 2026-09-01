package com.sole.cinevault

import com.sole.cinevault.metadata.*
import com.sole.cinevault.library.*
import com.sole.cinevault.smb.*

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import android.provider.MediaStore
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TrackChanges
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

// Not private — HomeScreen.kt's "See All" button on Continue Watching sets
// LibraryScrollState.category directly before navigating here, so Library
// opens straight into the right filter instead of landing on "All" and
// making the person tap again. Same file-private-vs-package-visible
// reasoning as ForceCineVaultBrightness() in Screens.kt.
object LibraryScrollState {
    var index: Int = 0
    var offset: Int = 0
    var category: String = "All"
    var sort: LibrarySortOption = LibrarySortOption.TITLE_AZ
    var gridMode: Boolean = true
}

// ── Folder type icon heuristic ────────────────────────────────────────────
// Generic Material icons only — deliberately NOT actual TikTok/Instagram
// brand marks (those are trademarked assets, not something to reproduce).
// Matches on the folder's display name, which for restricted folders is
// usually whatever the source app named its export/download folder.
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

    // Persistent error banner — for consequential failures (scan failure,
    // SMB share failure, delete failure) that need a Retry action and
    // shouldn't just flash by as a toast during a long-running operation.
    // Lightweight confirmations ("Added to Favorites" etc.) stay as toasts
    // on purpose — a banner would be overkill for those.
    var activeError by remember { mutableStateOf<ErrorBannerState?>(null) }

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

    fun openSecretFolder() {
        if (secretUnlocked) {
            selectedCategory = "Secret"
            return
        }

        requestSecretFolderUnlock(
            context = context,
            onUnlocked = {
                secretUnlocked = true
                selectedCategory = "Secret"
            },
            onAuthenticationError = {
                secretUnlocked = false
            }
        )
    }

    fun openContextSheet(item: VideoWithMetadata) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        contextSheetItem = item
    }

    fun hideVideo(item: VideoWithMetadata) {
        val updated = addVideoToSecret(
            context = context,
            hiddenPaths = hiddenPaths,
            item = item
        )
        hiddenPaths = updated
        onSecretChanged()
    }

    fun unhideEntireFolder(item: VideoWithMetadata) {
        val updatedFolders = removeContainingFolderFromSecret(
            context = context,
            hiddenFolders = hiddenFolders,
            item = item
        ) ?: return
        hiddenFolders = updatedFolders
    }

    fun unhideVideo(item: VideoWithMetadata) {
        hiddenPaths = removeVideoFromSecret(
            context = context,
            hiddenPaths = hiddenPaths,
            item = item
        )
    }

    fun toggleFolderSecret(folderVideoPaths: List<String>) {
        val result = toggleVideosInSecretFolder(
            context = context,
            hiddenPaths = hiddenPaths,
            folderVideoPaths = folderVideoPaths,
            onLongPressHaptic = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        ) ?: return

        hiddenPaths = result
        onSecretChanged()
    }

    fun addFavorite(item: VideoWithMetadata) {
        favoritePaths = addVideoToFavorites(
            context = context,
            favoritePaths = favoritePaths,
            item = item
        )
    }

    fun removeFavorite(item: VideoWithMetadata) {
        favoritePaths = removeVideoFromFavorites(
            context = context,
            favoritePaths = favoritePaths,
            item = item
        )
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
        // FIX: saveLibraryCache is now suspend (see PlaybackMemory.kt) —
        // this function itself is called from raw dialog/ActivityResult
        // callbacks, not coroutines, so the call needs its own launch.
        // Fire-and-forget is fine here: the rest of this function (the
        // Toast, the onVideosLoaded update above) doesn't need to wait
        // for the cache write to finish.
        scope.launch { saveLibraryCache(context, updated) }
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
        val path = item.video.path
        AlertDialog.Builder(context)
            .setTitle("Delete File")
            .setMessage("Delete \"${item.title}\"?\n\nThis cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                // FIX: this used to unconditionally do File(item.video.path)
                // and a MediaStore DATA-column lookup, both of which only
                // make sense for a real local filesystem path — a
                // content:// (restricted-folder/SAF) or smb:// (network
                // share) path would never match either one, silently
                // failing with a generic "Could not delete" error. Real,
                // live bug for both categories, not hypothetical.
                if (path.startsWith("smb://", ignoreCase = true)) {
                    Toast.makeText(context, "Can't delete files on a network share from CineVault — delete it from the source device instead", Toast.LENGTH_LONG).show()
                } else if (path.startsWith("content://")) {
                    // SAF-based (restricted folder) — the app already holds
                    // a persisted permission from when the folder was
                    // picked. Same pattern already proven working in
                    // MediaIntelligenceScreens.kt's deleteGridVideo.
                    try {
                        val deleted = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, Uri.parse(path))?.delete() == true
                        if (deleted) finishDeleteSuccess(item)
                        else activeError = ErrorBannerState("Could not delete \"${item.title}\"") { deleteVideoFile(item) }
                    } catch (e: Exception) {
                        activeError = ErrorBannerState("Delete failed: ${e.message}") { deleteVideoFile(item) }
                    }
                } else {
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
                        activeError = ErrorBannerState("Delete failed: ${e.message}") { deleteVideoFile(item) }
                    }
                } else {
                    try {
                        val deletedRows = if (mediaUri != null) context.contentResolver.delete(mediaUri, null, null) else 0
                        when {
                            deletedRows > 0 -> finishDeleteSuccess(item)
                            f.exists() && f.delete() -> finishDeleteSuccess(item)
                            else -> activeError = ErrorBannerState("Could not delete \"${item.title}\"") { deleteVideoFile(item) }
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
                            activeError = ErrorBannerState("Delete failed: ${e.message}") { deleteVideoFile(item) }
                        }
                    } catch (e: Exception) {
                        activeError = ErrorBannerState("Delete failed: ${e.message}") { deleteVideoFile(item) }
                    }
                }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (!isGranted) { LibraryScanController.status = "Storage permission denied"; return@rememberLauncherForActivityResult }
        LibraryScanController.start(context, onVideosLoaded)
    }

    val categories = listOf("All", "Continue Watching", "Movies", "TV Shows", "Folders", "Downloads", "Favorites", "Duplicates", "Secret")

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
    val secretRestrictedFolderGroups: List<SecretFolderGroup> = if (!secretUnlocked) emptyList() else {
        val restrictedInLibrary = sortedVideos.filter { it.type.equals("restricted", ignoreCase = true) }
        loadRestrictedFolders(context).mapNotNull { folder ->
            val items = restrictedInLibrary.filter { folderIdFromRestrictedMarker(it.video.folderPath) == folder.id }
            if (items.isNotEmpty() && items.all { hiddenPaths.contains(it.video.path) }) SecretFolderGroup(folder, items) else null
        }
    }
    val secretGroupedPaths = secretRestrictedFolderGroups.flatMap { group -> group.items.map { it.video.path } }.toSet()

    val videoFolders = remember(visibleSortedVideos) { groupVideosByFolder(visibleSortedVideos.filterNot { it.type.equals("restricted", ignoreCase = true) }) }

    // Only checked against the videos the person can actually SEE right
    // now (visibleSortedVideos already excludes Secret-hidden entries) —
    // duplicate detection has no business surfacing anything from Secret
    // outside of it, same boundary every other category already respects.
    val duplicateGroups = remember(visibleSortedVideos) { findDuplicateGroups(context, visibleSortedVideos) }

    val filteredVideos = when (selectedCategory) {
        "Secret" -> if (secretUnlocked) secretVideos.filter { it.video.path !in secretGroupedPaths } else emptyList()
        "Favorites" -> favoriteVideos
        // Same 15-second threshold Home's own Continue Watching row uses —
        // kept identical on purpose so "See All" from Home shows exactly
        // the same set, just not capped to 12.
        "Continue Watching" -> visibleSortedVideos.filter { loadPlaybackPosition(context, it.video.path) > 15_000L }
        "TV Shows" -> emptyList()
        "Folders" -> emptyList()
        "Duplicates" -> emptyList()
        "Downloads" -> visibleSortedVideos.filter { !it.type.equals("movie", ignoreCase = true) && !it.type.equals("tv", ignoreCase = true) && !it.type.equals("restricted", ignoreCase = true) }
        "Movies" -> visibleSortedVideos.filter { it.type.equals("movie", ignoreCase = true) }
        else -> visibleSortedVideos.filter { !it.type.equals("tv", ignoreCase = true) && !it.type.equals("restricted", ignoreCase = true) }
    }

    val tvGroups = groupTvShows(sortedVideos.filter { it.type.equals("tv", ignoreCase = true) && !hiddenPaths.contains(it.video.path) && !videoIsInsideSecretFolder(it, hiddenFolders) })

    LocalLibrarySecretScreenProtection(
        context = context,
        enabled = selectedCategory == "Secret" && secretUnlocked
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(SpaceBlack)) {
        // ── Adaptive grid columns ─────────────────────────────────────────
        // Was a hardcoded GridCells.Fixed(3) regardless of device or
        // orientation — meaning the Xiaomi Pad 7 in landscape got the exact
        // same column count as a phone, wasting real screen space. Floor is
        // kept at 3 (never regresses below the original phone behavior);
        // only bumps UP as more width becomes available. Width-based rather
        // than strictly landscape-gated, since a tablet in PORTRAIT (e.g.
        // ~820dp) still has plenty of room to deserve more than 3 too.
        val gridColumns = when {
            maxWidth >= 900.dp -> 5   // large tablet landscape
            maxWidth >= 700.dp -> 4   // tablet portrait / smaller tablet landscape
            else -> 3                 // phone — unchanged from before
        }

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(gridColumns),
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 28.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LocalLibraryHeader(
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onCategorySelected = { category ->
                        if (category == "Secret") openSecretFolder() else selectedCategory = category
                    },
                    sortOption = sortOption,
                    sortMenuExpanded = sortMenuExpanded,
                    onSortMenuExpandedChange = { sortMenuExpanded = it },
                    onSortSelected = { option -> sortOption = option },
                    isGridMode = isGridMode,
                    onToggleGridMode = { isGridMode = !isGridMode },
                    isScanning = LibraryScanController.isScanning,
                    scanStatus = LibraryScanController.status,
                    onRefresh = {
                        scope.launch { clearLibraryCache(context) }
                        onVideosLoaded(emptyList())
                        LibraryScanController.status = "Cache cleared. Scan again."
                    },
                    onScan = { permissionLauncher.launch(permission) },
                    context = context
                )
            }

            LocalLibraryCollectionsShelf(
                selectedCategory = selectedCategory,
                visibleSortedVideos = visibleSortedVideos,
                onNativeCollectionClick = onNativeCollectionClick,
                onCuratedCollectionClick = onCuratedCollectionClick
            )

            LocalLibraryTvAndFoldersShelf(
                selectedCategory = selectedCategory,
                visibleSortedVideos = visibleSortedVideos,
                tvGroups = tvGroups,
                context = context,
                onTvGroupClick = onTvGroupClick,
                onTvGroupLongClick = { group -> group.episodes.firstOrNull()?.let { openContextSheet(it) } },
                onRestrictedFolderClick = onRestrictedFolderClick,
                onRestrictedFolderLongClick = { folderName, paths ->
                    folderSecretConfirm = folderName to paths
                }
            )

            LocalLibraryGenresShelf(
                selectedCategory = selectedCategory,
                visibleSortedVideos = visibleSortedVideos,
                onGenreClick = onGenreClick
            )

            LocalLibrarySecretLockedSection(
                selectedCategory = selectedCategory,
                secretUnlocked = secretUnlocked,
                onUnlock = { openSecretFolder() }
            )

            LocalLibraryFoldersSection(
                selectedCategory = selectedCategory,
                videoFolders = videoFolders,
                expandedFolders = expandedFolders,
                onExpandedFoldersChange = { expandedFolders = it },
                isGridMode = isGridMode,
                gridColumns = gridColumns,
                onItemClick = onItemClick,
                onPlayClick = onPlayClick,
                onItemLongPress = { openContextSheet(it) },
                onFolderLongPress = { folderName, paths ->
                    folderSecretConfirm = folderName to paths
                }
            )

            LocalLibraryDuplicatesSection(
                selectedCategory = selectedCategory,
                duplicateGroups = duplicateGroups,
                onDeleteCopy = { copy -> deleteVideoFile(copy) }
            )

            LocalLibraryEmptyStateSection(
                selectedCategory = selectedCategory,
                isScanning = LibraryScanController.isScanning,
                filteredVideos = filteredVideos,
                tvGroupsEmpty = tvGroups.isEmpty(),
                secretUnlocked = secretUnlocked,
                allVideosEmpty = videos.isEmpty(),
                onScan = { permissionLauncher.launch(permission) }
            )

            LocalLibrarySecretFoldersShelf(
                selectedCategory = selectedCategory,
                secretUnlocked = secretUnlocked,
                groups = secretRestrictedFolderGroups,
                onRestrictedFolderClick = onRestrictedFolderClick,
                onRestrictedFolderLongClick = { folderName, paths ->
                    folderSecretConfirm = folderName to paths
                }
            )

            LocalLibraryVideoItemsSection(
                selectedCategory = selectedCategory,
                filteredVideos = filteredVideos,
                isGridMode = isGridMode,
                onItemClick = onItemClick,
                onPlayClick = onPlayClick,
                onItemLongPress = { openContextSheet(it) }
            )
        }

        // ── Persistent error banner — slides down from the top rather than
        // just fading, since it's an alert that should feel like it's
        // dropping in to demand attention, distinct from the bottom-sheet
        // slide-up used for the context menus below. Shows a local delete
        // failure if there is one, otherwise a scan/SMB failure surfaced by
        // LibraryScanController — which may have started from Home, not
        // this screen, so this can't just be local state.
        val bannerError = activeError ?: LibraryScanController.lastError
        AnimatedVisibility(
            visible = bannerError != null,
            enter = slideInVertically(initialOffsetY = { -it }, animationSpec = tween(280, easing = FastOutSlowInEasing)) + fadeIn(tween(220)),
            exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(200)) + fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            bannerError?.let { err -> ErrorBanner(state = err, onDismiss = { activeError = null; LibraryScanController.lastError = null }) }
        }

        LibraryItemContextSheet(
            item = contextSheetItem,
            isFavorite = contextSheetItem?.let { favoritePaths.contains(it.video.path) } ?: false,
            isHidden = contextSheetItem?.let { hiddenPaths.contains(it.video.path) } ?: false,
            isInSecretFolder = contextSheetItem?.let { videoIsInsideSecretFolder(it, hiddenFolders) } ?: false,
            onDismiss = { contextSheetItem = null },
            onPlay = { selectedItem -> contextSheetItem = null; onPlayClick(selectedItem) },
            onFavoriteToggle = { selectedItem ->
                if (favoritePaths.contains(selectedItem.video.path)) removeFavorite(selectedItem) else addFavorite(selectedItem)
                contextSheetItem = null
            },
            onSecretToggle = { selectedItem ->
                if (hiddenPaths.contains(selectedItem.video.path)) unhideVideo(selectedItem) else hideVideo(selectedItem)
                contextSheetItem = null
            },
            onUnlockFolder = { selectedItem ->
                unhideEntireFolder(selectedItem)
                contextSheetItem = null
            },
            onDelete = { selectedItem ->
                contextSheetItem = null
                deleteVideoFile(selectedItem)
            }
        )

        LibraryFolderSecretConfirmation(
            confirmation = folderSecretConfirm,
            onDismiss = { folderSecretConfirm = null },
            onToggleSecret = { paths ->
                toggleFolderSecret(paths)
                folderSecretConfirm = null
            },
            hiddenPaths = hiddenPaths
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
