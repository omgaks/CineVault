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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sole.cinevault.tvmode.TelevisionModeDetector
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
    // Collections shelf (horizontal row) position, so coming back from a collection page
    // lands on the card you opened instead of the first one.
    var shelfIndex: Int = 0
    var shelfOffset: Int = 0
    var category: String = "All"
    var sort: LibrarySortOption = LibrarySortOption.TITLE_AZ
    var gridMode: Boolean = true
    var viewMode: LibraryViewMode = LibraryViewMode.Grid
    // Search words survive leaving Library and coming back.
    var searchQuery: String = ""
    var searchRecents: List<String> = emptyList()
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

    var selectedCategory by remember { mutableStateOf(normalizeLibraryCategory(LibraryScrollState.category)) }
    var viewMode by remember { mutableStateOf(LibraryScrollState.viewMode) }
    var sortOption by remember { mutableStateOf(LibraryScrollState.sort) }
    // Which pill card is open (one at a time), plus the little state the Scan
    // card needs to say "Up to date" for two seconds after a scan finishes.
    // A search asked for from another screen opens the Search card pre-filled.
    val requestedSearch = remember { LibrarySearchRequest.pending.also { LibrarySearchRequest.pending = null } }
    var searchQuery by remember { mutableStateOf(requestedSearch ?: LibraryScrollState.searchQuery) }
    var searchFilters by remember { mutableStateOf(SearchFilters()) }
    var searchSort by remember { mutableStateOf(LibrarySearchSort.BestMatch) }
    var searchRecents by remember { mutableStateOf(LibraryScrollState.searchRecents) }
    var openPanel by remember { mutableStateOf<LibraryPanel?>(if (requestedSearch != null) LibraryPanel.Search else null) }
    var mainCopyChoices by remember { mutableStateOf(loadMainCopyChoices(context)) }
    var manageCopiesMainPath by remember { mutableStateOf<String?>(null) }
    var scanWasRunning by remember { mutableStateOf(false) }
    var scanUpToDate by remember { mutableStateOf(false) }
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
    LaunchedEffect(selectedCategory, sortOption, viewMode) {
        LibraryScrollState.category = selectedCategory
        LibraryScrollState.sort = sortOption
        LibraryScrollState.viewMode = viewMode
        LibraryScrollState.gridMode = viewMode == LibraryViewMode.Grid
    }

    // Back always closes the front-most window first.
    androidx.activity.compose.BackHandler(enabled = openPanel != null) { openPanel = null }

    // A scan keeps running when its card is closed. When it finishes and the
    // card is still open, show "Up to date" and close it after two seconds.
    LaunchedEffect(LibraryScanController.isScanning) {
        if (LibraryScanController.isScanning) {
            scanWasRunning = true
        } else if (scanWasRunning) {
            scanWasRunning = false
            if (openPanel == LibraryPanel.Scan && LibraryScanController.lastError == null) {
                scanUpToDate = true
                delay(2000)
                if (openPanel == LibraryPanel.Scan) openPanel = null
                scanUpToDate = false
            }
        }
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

    val categories = LIBRARY_CATEGORIES

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
    // Copies of the same film are folded behind the best one. Nothing is deleted.
    val foldPlan = remember(duplicateGroups, mainCopyChoices) {
        planDuplicateFold(
            duplicateGroups.map { group ->
                group.videos.map { CopyInfo(it.video.path, it.video.name, File(it.video.path).length()) }
            },
            mainCopyChoices
        )
    }
    val foldedVideos = remember(visibleSortedVideos, foldPlan) {
        visibleSortedVideos.filter { it.video.path !in foldPlan.hiddenPaths }
    }
    val copyCounts = remember(foldPlan) { foldPlan.groupsByMain.mapValues { it.value.size } }

    val searching = searchQuery.isNotBlank() || searchFilters.anyOn
    val searchResults = remember(foldedVideos, searchQuery, searchFilters, searchSort, favoritePaths) {
        if (!searching) emptyList() else {
            val scored = foldedVideos.mapNotNull { v ->
                val hit = if (searchQuery.isBlank()) SearchHit(SearchMatch.Title, 0) else searchDocument(
                    SearchDoc(
                        title = v.title,
                        fileName = v.video.name,
                        cast = v.cast.map { it.name },
                        director = v.director,
                        genres = v.genres,
                        year = extractYearFromName(v.video.name)
                    ),
                    searchQuery
                ) ?: return@mapNotNull null
                val ok = passesSearchFilters(
                    filters = searchFilters,
                    isFilm = v.type.equals("movie", ignoreCase = true),
                    rating = v.rating,
                    fileName = v.video.name,
                    watched = loadPlaybackPosition(context, v.video.path) > 15_000L,
                    favourite = favoritePaths.contains(v.video.path)
                )
                if (ok) v to hit else null
            }
            when (searchSort) {
                LibrarySearchSort.BestMatch -> scored.sortedWith(compareBy({ it.second.rank }, { it.first.title.lowercase() }))
                LibrarySearchSort.TitleAz -> scored.sortedBy { it.first.title.lowercase() }
                LibrarySearchSort.Rating -> scored.sortedByDescending { it.first.rating ?: 0.0 }
            }
        }
    }
    val matchTags = remember(searchResults, searchQuery) {
        if (searchQuery.isBlank()) emptyMap() else searchResults.associate { it.first.video.path to it.second.matchedBy.label }
    }
    // While searching the shelves step aside and results take the page.
    val shownCategory = if (searching && selectedCategory != "Secret") "Search" else selectedCategory

    val filteredVideos = when (selectedCategory) {
        "Secret" -> if (secretUnlocked) secretVideos.filter { it.video.path !in secretGroupedPaths } else emptyList()
        "Search" -> searchResults.map { it.first }
        "Favorites" -> favoriteVideos.filter { it.video.path !in foldPlan.hiddenPaths }
        // Same 15-second threshold Home's own Continue Watching row uses —
        // kept identical on purpose so "See All" from Home shows exactly
        // the same set, just not capped to 12.
        "Continue Watching" -> foldedVideos.filter { loadPlaybackPosition(context, it.video.path) > 15_000L }
        "TV Shows" -> emptyList()
        "Folders" -> emptyList()
        "Movies" -> foldedVideos.filter { it.type.equals("movie", ignoreCase = true) }
        else -> foldedVideos.filter { !it.type.equals("tv", ignoreCase = true) && !it.type.equals("restricted", ignoreCase = true) }
    }

    val tvGroups = groupTvShows(sortedVideos.filter { it.type.equals("tv", ignoreCase = true) && !hiddenPaths.contains(it.video.path) && !videoIsInsideSecretFolder(it, hiddenFolders) })

    LocalLibrarySecretScreenProtection(
        context = context,
        enabled = selectedCategory == "Secret" && secretUnlocked
    )

    // Phase 14 of TV support: Library is composed of ~8 separate section
    // files, all LazyGridScope extensions feeding this ONE shared grid — so
    // the key handling only needs to be set up once, here, rather than per
    // section. This phase covers the header (category chips, sort/grid/
    // refresh/scan tools) and the main video items grid specifically —
    // Collections/TV & Folders/Genres/Folders/Duplicates/Secret shelves are
    // deliberately left for their own follow-up phases rather than
    // attempting all ~1,000 lines across those files in one pass.
    val isTelevision = remember { TelevisionModeDetector.isRunningOnTelevision(context) }
    val focusManager = LocalFocusManager.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(SpaceBlack)) {
        // Columns come from the window width alone (never the device): posters
        // stay at least 100dp wide, so a phone gets 3 and a big tablet gets more.
        val gridColumns = libraryColumnsFor(maxWidth.value)

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(gridColumns),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .then(
                    if (isTelevision) {
                        Modifier.onKeyEvent { keyEvent ->
                            if (keyEvent.type != KeyEventType.KeyUp) return@onKeyEvent false
                            when (keyEvent.key) {
                                Key.DirectionUp -> {
                                    focusManager.moveFocus(FocusDirection.Up)
                                    true
                                }
                                Key.DirectionDown -> {
                                    focusManager.moveFocus(FocusDirection.Down)
                                    true
                                }
                                Key.DirectionLeft -> {
                                    focusManager.moveFocus(FocusDirection.Left)
                                    true
                                }
                                Key.DirectionRight -> {
                                    focusManager.moveFocus(FocusDirection.Right)
                                    true
                                }
                                else -> false
                            }
                        }
                    } else {
                        Modifier
                    }
                ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 28.dp)
        ) {
            // Room for the floating pill that sits on top of the grid.
            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(modifier = Modifier.height(64.dp))
            }

            LocalLibraryCollectionsShelf(
                selectedCategory = shownCategory,
                visibleSortedVideos = visibleSortedVideos,
                onNativeCollectionClick = onNativeCollectionClick,
                onCuratedCollectionClick = onCuratedCollectionClick
            )

            LocalLibraryTvAndFoldersShelf(
                selectedCategory = shownCategory,
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

            LocalLibrarySecretLockedSection(
                selectedCategory = selectedCategory,
                secretUnlocked = secretUnlocked,
                onUnlock = { openSecretFolder() }
            )

            LocalLibraryFoldersSection(
                selectedCategory = shownCategory,
                videoFolders = videoFolders,
                expandedFolders = expandedFolders,
                onExpandedFoldersChange = { expandedFolders = it },
                isGridMode = viewMode == LibraryViewMode.Grid,
                gridColumns = gridColumns,
                onItemClick = onItemClick,
                onPlayClick = onPlayClick,
                onItemLongPress = { openContextSheet(it) },
                onFolderLongPress = { folderName, paths ->
                    folderSecretConfirm = folderName to paths
                }
            )

            LocalLibraryEmptyStateSection(
                selectedCategory = shownCategory,
                isScanning = LibraryScanController.isScanning,
                filteredVideos = filteredVideos,
                tvGroupsEmpty = tvGroups.isEmpty(),
                secretUnlocked = secretUnlocked,
                allVideosEmpty = videos.isEmpty(),
                onScan = { permissionLauncher.launch(permission) },
                isTelevision = isTelevision
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
                selectedCategory = shownCategory,
                filteredVideos = filteredVideos,
                viewMode = viewMode,
                onItemClick = onItemClick,
                onPlayClick = onPlayClick,
                onItemLongPress = { openContextSheet(it) },
                isTelevision = isTelevision,
                copyCounts = copyCounts,
                onManageCopies = { manageCopiesMainPath = it.video.path },
                matchTags = matchTags
            )
        }

        // ── Floating pill and its cards ───────────────────────────────────
        // Tapping outside closes the card, except for Refresh, which only
        // responds to its own buttons so a stray touch can never clear the library.
        val genreNames = remember(visibleSortedVideos) {
            visibleSortedVideos
                .flatMap { it.genres }
                .map { normalizeGenreName(it) }
                .distinct()
                .sortedBy { it.lowercase() }
        }
        // Formatted inside the producer (not in the composable body) so lint
        // does not flag a non-observable locale read.
        val lastScanText by produceState<String?>(initialValue = null, context, LibraryScanController.isScanning) {
            value = loadLibraryCache(context)?.let {
                java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(it.timestamp))
            }
        }
        val panelNow = openPanel
        if (panelNow != null && panelClosesOnOutsideTap(panelNow)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { openPanel = null }
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LibraryPill(
                openPanel = panelNow,
                viewMode = viewMode,
                isScanning = LibraryScanController.isScanning,
                onToggle = { panel -> openPanel = if (openPanel == panel) null else panel }
            )
            if (panelNow != null) {
                LibraryPanelCard(panel = panelNow, onClose = { openPanel = null }) {
                    when (panelNow) {
                        LibraryPanel.Search -> LibrarySearchPanel(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it; LibraryScrollState.searchQuery = it },
                            onSubmit = {
                                searchRecents = pushRecentSearch(searchRecents, searchQuery)
                                LibraryScrollState.searchRecents = searchRecents
                                openPanel = null
                            },
                            filters = searchFilters,
                            onFiltersChange = { searchFilters = it },
                            sort = searchSort,
                            onSortChange = { searchSort = it },
                            recents = searchRecents,
                            libraryEmpty = videos.isEmpty(),
                            resultCount = searchResults.size
                        )
                        LibraryPanel.Category -> LibraryCategoryPanel(selected = selectedCategory) { category ->
                            searchQuery = ""; LibraryScrollState.searchQuery = ""; searchFilters = SearchFilters()
                            if (category == "Secret") openSecretFolder() else selectedCategory = category
                            openPanel = null
                        }
                        LibraryPanel.Genre -> LibraryGenrePanel(genres = genreNames) { genre ->
                            openPanel = null
                            onGenreClick(genre)
                        }
                        LibraryPanel.Sort -> LibrarySortPanel(selected = sortOption) { option ->
                            sortOption = option
                            openPanel = null
                        }
                        LibraryPanel.View -> LibraryViewPanel(selected = viewMode) { mode ->
                            viewMode = mode
                            openPanel = null
                        }
                        LibraryPanel.Refresh -> LibraryRefreshPanel(
                            onCancel = { openPanel = null },
                            onConfirm = {
                                scope.launch { clearLibraryCache(context) }
                                onVideosLoaded(emptyList())
                                openPanel = LibraryPanel.Scan
                                permissionLauncher.launch(permission)
                            }
                        )
                        LibraryPanel.Scan -> LibraryScanPanel(
                            isScanning = LibraryScanController.isScanning,
                            status = LibraryScanController.status,
                            upToDate = scanUpToDate,
                            lastScan = lastScanText,
                            onScan = { permissionLauncher.launch(permission) }
                        )
                    }
                }
            }
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

        manageCopiesMainPath?.let { mainPath ->
            val groupPaths = foldPlan.groupsByMain[mainPath]
            if (groupPaths == null) {
                manageCopiesMainPath = null
            } else {
                ManageCopiesSheet(
                    copies = groupPaths.mapNotNull { path -> videos.firstOrNull { it.video.path == path } },
                    mainPath = mainPath,
                    onMakeMain = { chosen ->
                        mainCopyChoices = withMainCopy(mainCopyChoices, chosen.video.path, groupPaths)
                        saveMainCopyChoices(context, mainCopyChoices)
                        manageCopiesMainPath = null
                    },
                    onDelete = { target ->
                        manageCopiesMainPath = null
                        deleteVideoFile(target)
                    },
                    onClose = { manageCopiesMainPath = null }
                )
            }
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
