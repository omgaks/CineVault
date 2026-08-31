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

    // FIX: KeyguardManager.createConfirmDeviceCredentialIntent has been
    // deprecated since API 30, with inconsistent behavior on newer
    // platforms — androidx.biometric's BiometricPrompt is the current
    // standard replacement, and additionally supports actual biometric
    // auth (fingerprint/face) rather than only PIN/pattern/password.
    // setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) means
    // either biometric OR the device's own PIN/pattern/password satisfies
    // it — matching the original's fallback behavior — and deliberately
    // has NO setNegativeButtonText(), since combining that with
    // DEVICE_CREDENTIAL throws IllegalStateException (DEVICE_CREDENTIAL
    // already provides its own way out of the prompt).
    fun openSecretFolder() {
        if (secretUnlocked) { selectedCategory = "Secret"; return }
        val activity = context.findCineActivity() as? FragmentActivity
        if (activity == null) {
            Toast.makeText(context, "Couldn't open Secret Folder unlock", Toast.LENGTH_SHORT).show()
            return
        }
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(context).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            // Never unlocks by default — an unavailable/unset device lock
            // means the folder simply stays locked, with a clear reason why.
            Toast.makeText(context, "Set a device lock (fingerprint, PIN, pattern, or password) first to secure this folder", Toast.LENGTH_LONG).show()
            return
        }
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Secret Folder")
            .setSubtitle("Confirm fingerprint, PIN, pattern, or password")
            .setAllowedAuthenticators(authenticators)
            .build()
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(context), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                // FIX (#4, carried over): navigate in the same callback
                // that confirms the unlock, not on the NEXT tap of the
                // "Secret" chip — otherwise it only opens on the second tap.
                secretUnlocked = true
                selectedCategory = "Secret"
                Toast.makeText(context, "Secret folder unlocked", Toast.LENGTH_SHORT).show()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // FIX: was trying to distinguish "user cancelled" from
                // "genuine error" by excluding a specific denylist of error
                // codes (ERROR_USER_CANCELED/NEGATIVE_BUTTON/CANCELED) —
                // but backing out of the DEVICE_CREDENTIAL fallback screen
                // (PIN/pattern, as opposed to the fingerprint dialog)
                // apparently returns a different code than expected on at
                // least one real device, since a cancel was still showing
                // this toast. Rather than keep guessing at BiometricPrompt's
                // full error-code enum one device-report at a time, just
                // don't show a toast on any error — staying on the same
                // screen, still locked, is self-evident feedback on its
                // own, and there was never a strong need to distinguish
                // "you cancelled" from "something went wrong" here anyway.
                secretUnlocked = false
            }
            // A single failed attempt (e.g. one bad fingerprint read) keeps
            // the prompt open for retry — no state change here, matching
            // BiometricPrompt's own intended UX.
            override fun onAuthenticationFailed() {}
        })
        prompt.authenticate(promptInfo)
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

    // ── Secret folder screenshot/recording protection ───────────────────
    // FLAG_SECURE is a window-level flag — CineVault is single-Activity, and
    // "Secret" isn't its own screen, it's a category filter within THIS
    // screen, so the effect keys off (selectedCategory == "Secret" &&
    // secretUnlocked) rather than a separate screen's lifecycle. Blocks
    // screenshots, screen recording, AND the Recents-tray thumbnail while
    // unlocked Secret content is actually on screen; clears automatically
    // the instant the person switches to any other category or leaves this
    // screen, so it never lingers and affects some other part of the app.
    val activity = context.findCineActivity()
    val isViewingSecret = selectedCategory == "Secret" && secretUnlocked
    DisposableEffect(isViewingSecret) {
        if (isViewingSecret) {
            activity?.window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            if (isViewingSecret) {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

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

                    // FIX: tool row now lives on the right, reading
                    // left-to-right on screen as Sort → Grid/List → Refresh
                    // → Scan — i.e. Scan is the rightmost/outermost icon,
                    // matching "icons position right to left: Scan – Refresh
                    // – Grid – Sort" (Scan first from the right edge).
                    // Icons swapped for more distinct/recognizable shapes:
                    // Scan uses a radar-style sweep, Sort uses ascending
                    // bars instead of the generic sort glyph, so all four
                    // silhouettes are easy to tell apart at a glance even
                    // before reading color.
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.Top) {
                        if (LibraryScanController.status.isNotBlank()) {
                            Text(
                                text = LibraryScanController.status,
                                color = TextMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(top = 10.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }

                        Box {
                            LibraryToolIconButton(
                                icon = Icons.Filled.SwapVert,
                                tint = Color(0xFF56CCF2),
                                contentDescription = "Sort by: ${sortOption.label}",
                                label = "Sort",
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
                            icon = if (isGridMode) Icons.Filled.ViewAgenda else Icons.Filled.GridView,
                            tint = Color(0xFFBB86FC),
                            contentDescription = if (isGridMode) "Switch to List" else "Switch to Grid",
                            label = if (isGridMode) "List" else "Grid",
                            onClick = { isGridMode = !isGridMode }
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        LibraryToolIconButton(
                            icon = Icons.Filled.Refresh,
                            tint = Color(0xFF6FCF97),
                            contentDescription = "Refresh / Clear Cache",
                            label = "Refresh",
                            enabled = !LibraryScanController.isScanning,
                            onClick = { scope.launch { clearLibraryCache(context) }; onVideosLoaded(emptyList()); LibraryScanController.status = "Cache cleared. Scan again." }
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        LibraryToolIconButton(
                            icon = Icons.Filled.TrackChanges,
                            tint = AmberCore,
                            contentDescription = "Scan Device Videos",
                            label = "Scan",
                            enabled = !LibraryScanController.isScanning,
                            onClick = { permissionLauncher.launch(permission) }
                        )
                    }

                    // FIX: loadLibraryCache is now suspend (see
                    // PlaybackMemory.kt) — was called directly here,
                    // synchronously, on every recomposition. produceState
                    // loads it asynchronously instead; the timestamp
                    // simply doesn't render until the read completes
                    // (typically near-instant), rather than blocking
                    // composition to get it immediately.
                    val lastScanCache by produceState<CachedLibrary?>(initialValue = null, context) {
                        value = loadLibraryCache(context)
                    }
                    lastScanCache?.let {
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
                        .distinctBy { it.collectionId }
                        .mapNotNull { video ->
                            val collectionId = video.collectionId
                            val collectionName = video.collectionName
                            if (collectionId == null || collectionName == null) null
                            else CollectionShelfEntry("native:$collectionId", collectionName, video.backdropUrl, false, collectionId)
                        }
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
                                val remainder = folder.videos.size % gridColumns
                                if (remainder != 0) {
                                    repeat(gridColumns - remainder) {
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

            // Duplicates — same movie sitting in more than one download
            // folder, detected by real on-disk file size (see
            // DuplicateDetector.kt), not filename or title. Each group
            // shows every copy with its exact size and folder path so the
            // person can see which is which before deciding — nothing is
            // ever auto-deleted. "Delete this copy" reuses the exact same
            // consent-gated deleteVideoFile() flow every other delete
            // action in this screen already goes through.
            if (selectedCategory == "Duplicates") {
                if (duplicateGroups.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            Text(text = "No duplicates found.", color = TextMuted, fontSize = 15.sp)
                        }
                    }
                } else {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            Text(text = "Duplicates (${duplicateGroups.size})", color = TextBright, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Grouped by matching file size — review before deleting anything.",
                                color = TextMuted, fontSize = 12.sp
                            )
                        }
                    }

                    duplicateGroups.forEach { group ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column(
                                modifier = Modifier.fillMaxWidth().glassPanel(cornerRadius = 14.dp).padding(14.dp)
                            ) {
                                Text(
                                    text = "${group.videos.size} copies · ${group.videos.firstOrNull()?.title ?: "Unknown"}",
                                    color = AmberGlow, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                group.videos.forEach { copy ->
                                    val copyFile = File(copy.video.path)
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = copyFile.name, color = TextBright, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                            Text(text = copyFile.parent ?: copy.video.path, color = TextMuted, fontSize = 10.sp, maxLines = 1)
                                            Text(text = formatFileSize(copyFile.length()), color = TextMuted, fontSize = 10.sp)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Delete", color = Color(0xFFFF8080), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF2A0A0A))
                                                .clickable { deleteVideoFile(copy) }
                                                .padding(horizontal = 12.dp, vertical = 7.dp)
                                        )
                                    }
                                }
                            }
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) { Spacer(modifier = Modifier.height(4.dp)) }
                    }
                }
            }

            if (selectedCategory != "Folders" && selectedCategory != "Duplicates" && !LibraryScanController.isScanning && filteredVideos.isEmpty() && tvGroups.isEmpty() && !(selectedCategory == "Secret" && !secretUnlocked)) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    if (videos.isEmpty()) {
                        EmptyStateBlock(
                            icon = Icons.Filled.LocalMovies,
                            title = "Your library is empty",
                            subtitle = "Scan your device or add a network share to get started."
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "Scan Device Videos", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clip(RoundedCornerShape(50)).background(AmberGlow.copy(alpha = 0.90f))
                                        .clickable { permissionLauncher.launch(permission) }
                                        .padding(horizontal = 18.dp, vertical = 10.dp)
                                )
                            }
                        }
                    } else {
                        EmptyStateBlock(
                            icon = Icons.Filled.LocalMovies,
                            title = "Nothing here yet",
                            subtitle = when (selectedCategory) {
                                "Continue Watching" -> "Videos you've started watching will show up here."
                                "Favorites" -> "Tap the heart on anything to add it here."
                                else -> "Try a different category, or rescan your library."
                            }
                        )
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
                        text = when (selectedCategory) { "Movies" -> "Movies"; "Downloads" -> "Downloads"; "Favorites" -> "Favorites"; "Secret" -> "Secret Folder"; "Continue Watching" -> "Continue Watching"; else -> "Movies & Downloads" },
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

                    // Sheet slides up + fades in, distinct from the scrim's
                    // plain fade above — gives this the "bottom sheet
                    // arriving" feel instead of just materializing in place.
                    androidx.compose.animation.AnimatedVisibility(
                        visible = contextSheetItem != null,
                        enter = slideInVertically(initialOffsetY = { it / 3 }, animationSpec = tween(260, easing = FastOutSlowInEasing)) + fadeIn(tween(200)),
                        exit = slideOutVertically(targetOffsetY = { it / 3 }, animationSpec = tween(180)) + fadeOut(tween(140))
                    ) {
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
                androidx.compose.animation.AnimatedVisibility(
                    visible = folderSecretConfirm != null,
                    enter = slideInVertically(initialOffsetY = { it / 3 }, animationSpec = tween(260, easing = FastOutSlowInEasing)) + fadeIn(tween(200)),
                    exit = slideOutVertically(targetOffsetY = { it / 3 }, animationSpec = tween(180)) + fadeOut(tween(140))
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
}

private fun looksLikePersonalOrCameraVideo(fileName: String, cleanedName: String): Boolean {
    val lower = fileName.lowercase(); val cleaned = cleanedName.trim().lowercase()
    if (cleaned.length < 4) return true
    return lower.startsWith("vid_") || lower.startsWith("img_") || lower.startsWith("video_") ||
            lower.startsWith("screenrecord") || lower.startsWith("screen_record") ||
            lower.contains("whatsapp video") || lower.contains("camera") ||
            lower.matches(Regex(".*\\b(19|20)\\d{6}[_-]?(19|20)?\\d{0,6}.*"))
}
