package com.sole.cinevault

import android.Manifest
import android.app.Activity
import android.content.Context
import android.os.Build
import android.app.KeyguardManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

@Composable
fun LocalVideoLibraryScreen(
    videos: List<VideoWithMetadata>,
    onVideosLoaded: (List<VideoWithMetadata>) -> Unit,
    onItemClick: (VideoWithMetadata) -> Unit,
    onPlayClick: (VideoWithMetadata) -> Unit = {},
    onTvGroupClick: (TvGroup) -> Unit,
    onSecretChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(false) }
    var scanStatus by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var isGridMode by remember { mutableStateOf(true) }
    var sortOption by remember { mutableStateOf(LibrarySortOption.TITLE_AZ) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var secretUnlocked by remember { mutableStateOf(false) }
    var hiddenPaths by remember { mutableStateOf<Set<String>>(loadSecretVideoPaths(context)) }
    var hiddenFolders by remember { mutableStateOf<Set<String>>(loadSecretFolderPaths(context)) }
    var favoritePaths by remember { mutableStateOf(loadFavoriteVideoPaths(context)) }
    var actionMenuItem by remember { mutableStateOf<VideoWithMetadata?>(null) }
    var actionMenuExpanded by remember { mutableStateOf(false) }

    // FOLDER VIEW: track which folders are expanded
    var expandedFolders by remember { mutableStateOf<Set<String>>(emptySet()) }

    val keyguardManager = remember { context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager }

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

    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (!isGranted) { scanStatus = "Storage permission denied"; return@rememberLauncherForActivityResult }
        scope.launch {
            isLoading = true; scanStatus = "Scanning device videos..."
            val scannedVideos = try { scanDeviceVideos(context) } catch (e: Exception) { e.printStackTrace(); scanStatus = "Scan failed: ${e.message ?: "Unknown error"}"; isLoading = false; return@launch }
            scanStatus = "Found ${scannedVideos.size} videos. Loading cached posters..."
            val instantList = scannedVideos.map { applyCachedMetadataIfAvailable(context, it) }
            onVideosLoaded(instantList); saveLibraryCache(context, instantList)
            scanStatus = "Loaded ${instantList.size} videos. Updating missing posters..."
            val workingList = instantList.toMutableList(); var updatedCount = 0
            instantList.forEachIndexed { index, item ->
                if (!hasUsefulOnlineMetadata(item)) {
                    scanStatus = "Poster ${index + 1}/${instantList.size}: ${item.video.name.take(28)}"
                    val enriched = enrichVideoWithOnlineMetadata(context, item)
                    if (enriched != item) {
                        workingList[index] = enriched; updatedCount++
                        if (updatedCount % 5 == 0) { val p = workingList.toList(); onVideosLoaded(p); saveLibraryCache(context, p) }
                    }
                }
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

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF070707))) {
        LazyVerticalGrid(
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
                        Text(text = "Library", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Button(onClick = { isGridMode = !isGridMode }, shape = RoundedCornerShape(30.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.10f), contentColor = Color.White)) {
                            Text(if (isGridMode) "List" else "Grid")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(enabled = !isLoading, onClick = { permissionLauncher.launch(permission) }, shape = RoundedCornerShape(40.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.10f), contentColor = Color.White)) {
                            Text(if (isLoading) scanStatus.ifBlank { "Scanning..." } else "Scan Device Videos")
                        }
                        OutlinedButton(enabled = !isLoading, onClick = { clearLibraryCache(context); onVideosLoaded(emptyList()); scanStatus = "Cache cleared. Scan again." }, shape = RoundedCornerShape(40.dp)) {
                            Text("Refresh")
                        }
                    }

                    if (scanStatus.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(text = scanStatus, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    loadLibraryCache(context)?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Last Scan: " + java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(it.timestamp)), color = Color.Gray, fontSize = 11.sp)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(items = categories) { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { if (category == "Secret") openSecretFolder() else selectedCategory = category },
                                label = { Text(category) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color.White.copy(alpha = 0.18f),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color.Transparent,
                                    labelColor = Color(0xFFB8B8B8)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Box {
                        Box(modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFFFB300).copy(alpha = 0.22f)).clickable { sortMenuExpanded = true }.padding(horizontal = 18.dp, vertical = 10.dp)) {
                            Text(text = "Sort by: ${sortOption.label}", color = Color(0xFFFFD54F), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }, modifier = Modifier.background(Color(0xFF151515))) {
                            LibrarySortOption.values().forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(text = option.label, color = if (sortOption == option) Color(0xFFFFD54F) else Color.White, fontWeight = if (sortOption == option) FontWeight.Bold else FontWeight.Normal) },
                                    onClick = { sortOption = option; sortMenuExpanded = false }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            // Secret locked
            if (selectedCategory == "Secret" && !secretUnlocked) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(modifier = Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "🔒 Secret folder is locked", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { openSecretFolder() }, shape = RoundedCornerShape(40.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300).copy(alpha = 0.85f), contentColor = Color.Black)) {
                                Text("Unlock Secret Folder")
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
                            Text(text = "No folders found. Scan your library first.", color = Color(0xFFBDBDD0), fontSize = 15.sp)
                        }
                    }
                } else {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(text = "Folders (${videoFolders.size})", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }

                    videoFolders.forEach { folder ->
                        // Folder header row
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            val isExpanded = expandedFolders.contains(folder.folderPath)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF1A1A1A))
                                    .clickable {
                                        expandedFolders = if (isExpanded) expandedFolders - folder.folderPath else expandedFolders + folder.folderPath
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.Filled.Folder,
                                    contentDescription = null,
                                    tint = Color(0xFFE8A020),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = folder.folderName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                    Text(text = "${folder.videos.size} video${if (folder.videos.size != 1) "s" else ""}", color = Color(0xFF888888), fontSize = 11.sp)
                                }
                                androidx.compose.material3.Icon(
                                    imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = Color(0xFF888888),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Expanded folder contents — respects isGridMode toggle
                        if (expandedFolders.contains(folder.folderPath)) {
                            if (isGridMode) {
                                items(items = folder.videos, key = { it.video.path }) { item ->
                                    LibraryGridCard(item = item, onClick = { onItemClick(item) }, onPlayClick = onPlayClick)
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
                                    LibraryCard(item = item, onClick = { onItemClick(item) })
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
                        Text(text = "No videos found. Tap Scan Device Videos.", color = Color(0xFFBDBDD0), fontSize = 15.sp)
                    }
                }
            }

            // TV Shows row
            if (tvGroups.isNotEmpty() && selectedCategory in listOf("All", "TV Shows")) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Text(text = "TV Shows", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(items = tvGroups, key = { it.showName }) { show ->
                                Column(modifier = Modifier.width(145.dp)) {
                                    Box {
                                        PosterBox(posterUrl = show.posterUrl, modifier = Modifier.fillMaxWidth().height(210.dp).clickable { onTvGroupClick(show) })
                                        Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(34.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.66f)).clickable {
                                            show.episodes.firstOrNull()?.let { episode -> actionMenuItem = episode; actionMenuExpanded = true }
                                        }, contentAlignment = Alignment.Center) {
                                            Text(text = "⋮", color = Color(0xFFFFD54F), fontSize = 22.sp, fontWeight = FontWeight.Black)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(text = show.showName, color = Color.White, maxLines = 1, fontWeight = FontWeight.SemiBold)
                                    Text(text = "${show.episodes.size} Episodes", color = Color.Gray, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Regular video grid/list
            if (filteredVideos.isNotEmpty() && selectedCategory != "Folders") {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = when (selectedCategory) { "Movies" -> "Movies"; "Downloads" -> "Downloads"; "Favorites" -> "Favorites"; "Secret" -> "Secret Folder"; else -> "Movies & Downloads" },
                        color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold
                    )
                }

                if (isGridMode) {
                    items(items = filteredVideos, key = { it.video.path }) { item ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            LibraryGridCard(item = item, onClick = { onItemClick(item) }, onPlayClick = onPlayClick)
                            Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(34.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.66f)).clickable { actionMenuItem = item; actionMenuExpanded = true }, contentAlignment = Alignment.Center) {
                                Text(text = "⋮", color = Color(0xFFFFD54F), fontSize = 22.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                } else {
                    items(items = filteredVideos, key = { it.video.path }, span = { GridItemSpan(maxLineSpan) }) { item ->
                        Box {
                            LibraryCard(item = item, onClick = { onItemClick(item) })
                            Box(modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(36.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.66f)).clickable { actionMenuItem = item; actionMenuExpanded = true }, contentAlignment = Alignment.Center) {
                                Text(text = "⋮", color = Color(0xFFFFD54F), fontSize = 22.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }

        // Action menu overlay
        if (actionMenuExpanded && actionMenuItem != null) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)).clickable { actionMenuExpanded = false; actionMenuItem = null }, contentAlignment = Alignment.Center) {
                val selectedItem = actionMenuItem
                if (selectedItem != null) {
                    Column(modifier = Modifier.width(120.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF111111).copy(alpha = 0.96f)).clickable { }.padding(vertical = 6.dp)) {
                        Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(Color(0xFFFFB300)))
                        MiniLibraryActionItem(text = if (hiddenPaths.contains(selectedItem.video.path)) "Unhide" else "Secret", onClick = {
                            if (hiddenPaths.contains(selectedItem.video.path)) unhideVideo(selectedItem) else hideVideo(selectedItem)
                            actionMenuExpanded = false; actionMenuItem = null
                        })
                        MiniLibraryActionItem(text = if (videoIsInsideSecretFolder(selectedItem, hiddenFolders)) "Unlock Folder" else "Hide Folder", onClick = {
                            if (videoIsInsideSecretFolder(selectedItem, hiddenFolders)) unhideEntireFolder(selectedItem) else hideEntireFolder(selectedItem)
                            actionMenuExpanded = false; actionMenuItem = null
                        })
                        MiniLibraryActionItem(text = if (favoritePaths.contains(selectedItem.video.path)) "Unfavorite" else "Favorite", onClick = {
                            if (favoritePaths.contains(selectedItem.video.path)) removeFavorite(selectedItem) else addFavorite(selectedItem)
                            actionMenuExpanded = false; actionMenuItem = null
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniLibraryActionItem(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp).clip(RoundedCornerShape(50)).background(Color.White).clickable { onClick() }.padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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
