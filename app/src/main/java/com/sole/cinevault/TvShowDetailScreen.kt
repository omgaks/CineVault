package com.sole.cinevault

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
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
import java.io.File

@Composable
fun TvShowDetailScreen(
    group: TvGroup,
    onBack: () -> Unit,
    onEpisodeClick: (VideoWithMetadata) -> Unit,
    onEpisodesChanged: (List<VideoWithMetadata>) -> Unit = {},
    onSecretChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val heroImage = group.backdropUrl ?: group.posterUrl

    // Local copy so delete/hide can update the visible list immediately;
    // onEpisodesChanged lets the caller sync its own master list too.
    var episodes by remember(group) { mutableStateOf(group.episodes) }
    var hiddenPaths by remember { mutableStateOf<Set<String>>(loadSecretVideoPaths(context)) }
    var hiddenFolders by remember { mutableStateOf<Set<String>>(loadSecretFolderPaths(context)) }
    var favoritePaths by remember { mutableStateOf(loadFavoriteVideoPaths(context)) }
    var contextSheetItem by remember { mutableStateOf<VideoWithMetadata?>(null) }

    val visibleEpisodes = episodes.filter { !hiddenPaths.contains(it.video.path) && !videoIsInsideSecretFolder(it, hiddenFolders) }

    fun openContextSheet(item: VideoWithMetadata) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        contextSheetItem = item
    }

    fun addFavorite(item: VideoWithMetadata) {
        val updated = favoritePaths + item.video.path; favoritePaths = updated
        saveFavoriteVideoPaths(context, updated); Toast.makeText(context, "Added to Favorites", Toast.LENGTH_SHORT).show()
    }
    fun removeFavorite(item: VideoWithMetadata) {
        val updated = favoritePaths - item.video.path; favoritePaths = updated
        saveFavoriteVideoPaths(context, updated); Toast.makeText(context, "Removed from Favorites", Toast.LENGTH_SHORT).show()
    }
    fun hideVideo(item: VideoWithMetadata) {
        val updated = hiddenPaths + item.video.path; hiddenPaths = updated
        saveSecretVideoPaths(context, updated); clearPlaybackPosition(context, item.video.path)
        onSecretChanged(); Toast.makeText(context, "Moved to Secret folder", Toast.LENGTH_SHORT).show()
    }
    fun unhideVideo(item: VideoWithMetadata) {
        val updated = hiddenPaths - item.video.path; hiddenPaths = updated
        saveSecretVideoPaths(context, updated); Toast.makeText(context, "Removed from Secret folder", Toast.LENGTH_SHORT).show()
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

    // Delete — same direct MediaStore flow as the Library screen (Android 11+
    // consent request, Android 10 RecoverableSecurityException, plain delete
    // fallback). Not routed through FileManagementHelper, which turned out to
    // only ever handle the app's own subtitle files, not MediaStore-scanned videos.
    var pendingDeleteResult by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val deleteConsentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        pendingDeleteResult?.invoke(result.resultCode == Activity.RESULT_OK)
        pendingDeleteResult = null
    }

    fun finishDeleteSuccess(item: VideoWithMetadata) {
        clearPlaybackPosition(context, item.video.path)
        val updated = episodes.filter { it.video.path != item.video.path }
        episodes = updated
        onEpisodesChanged(updated)
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

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF080808))) {

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // ── Hero backdrop ─────────────────────────────────────────────
            item {
                Box(modifier = Modifier.fillMaxWidth().height(340.dp)) {
                    if (!heroImage.isNullOrBlank()) {
                        AsyncImage(
                            model = heroImage,
                            contentDescription = group.showName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF161616)))
                    }

                    // Gradient overlay
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.60f),
                                    Color(0xFF080808)
                                )
                            )
                        )
                    )

                    // Show title + episode count at bottom
                    Column(
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = 22.dp, end = 22.dp, bottom = 20.dp)
                    ) {
                        Text(text = group.showName, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "${visibleEpisodes.size} Episodes", color = Color(0xFFAAAAAA), fontSize = 14.sp)
                    }
                }
            }

            // ── Episode stills horizontal row ─────────────────────────────
            item {
                Column(modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)) {
                    Text(
                        text = "Episodes",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 22.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 22.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(visibleEpisodes) { index, episode ->
                            EpisodeStillCard(
                                episode = episode,
                                episodeNumber = index + 1,
                                context = context,
                                onClick = { onEpisodeClick(episode) },
                                onLongPress = { openContextSheet(episode) }
                            )
                        }
                    }
                }
            }

            // ── Full episode list ─────────────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "All Episodes",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 22.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            itemsIndexed(visibleEpisodes) { index, episode ->
                EpisodeListRow(
                    episode = episode,
                    episodeNumber = index + 1,
                    context = context,
                    onClick = { onEpisodeClick(episode) },
                    onLongPress = { openContextSheet(episode) }
                )
                Spacer(modifier = Modifier.height(2.dp))
            }

            item { Spacer(modifier = Modifier.height(100.dp)) }
        }

        // ── Back button ───────────────────────────────────────────────────
        Box(
            modifier = Modifier.align(Alignment.TopStart).padding(14.dp)
                .size(42.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(22.dp))
        }

        // ── Long-press context sheet — same pattern as the Library screen ──
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .width(56.dp)
                                    .height(82.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SpaceDeep)
                            ) {
                                val thumb = selectedItem.episodeStill ?: selectedItem.posterUrl
                                if (!thumb.isNullOrBlank()) {
                                    AsyncImage(model = thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
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

                        SheetActionRow(icon = Icons.Filled.PlayArrow, label = "Play", tint = AmberCore) {
                            contextSheetItem = null; onEpisodeClick(selectedItem)
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

// ── Horizontal episode still card (in the top row) ───────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeStillCard(
    episode: VideoWithMetadata,
    episodeNumber: Int,
    context: android.content.Context,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {}
) {
    val imageUrl = episode.episodeStill ?: episode.backdropUrl ?: episode.posterUrl
    val savedPosition = remember { loadPlaybackPosition(context, episode.video.path) }
    val hasProgress = savedPosition > 5_000L
    val episodeDurationEstimate = 45L * 60L * 1000L
    val progress = if (hasProgress) (savedPosition.toFloat() / episodeDurationEstimate).coerceIn(0f, 1f) else 0f

    Column(
        modifier = Modifier.width(220.dp).combinedClickable(onClick = onClick, onLongClick = onLongPress)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(124.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1A1A1A))
        ) {
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }

            // Episode number badge
            Box(
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                    .clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.70f))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(text = "E$episodeNumber", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            // Play icon overlay
            Box(
                modifier = Modifier.align(Alignment.Center).size(38.dp).clip(CircleShape)
                    .background(Color(0xFFE8A020).copy(alpha = 0.88f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(22.dp))
            }

            // Progress bar at bottom
            if (hasProgress) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                    color = Color(0xFFE8A020),
                    trackColor = Color.White.copy(alpha = 0.20f)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = cleanEpisodeSubtitle(episode, episodeNumber),
            color = Color(0xFFAAAAAA),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = cleanEpisodeTitle(episode, episodeNumber),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ── List row for each episode below the stills ───────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeListRow(
    episode: VideoWithMetadata,
    episodeNumber: Int,
    context: android.content.Context,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {}
) {
    val imageUrl = episode.episodeStill ?: episode.backdropUrl ?: episode.posterUrl
    val savedPosition = remember { loadPlaybackPosition(context, episode.video.path) }
    val hasProgress = savedPosition > 5_000L
    val episodeDurationEstimate = 45L * 60L * 1000L
    val progress = if (hasProgress) (savedPosition.toFloat() / episodeDurationEstimate).coerceIn(0f, 1f) else 0f

    Row(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 22.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Still thumbnail
        Box(
            modifier = Modifier.width(110.dp).height(62.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1A1A1A))
        ) {
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            if (hasProgress) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                    color = Color(0xFFE8A020),
                    trackColor = Color.White.copy(alpha = 0.20f)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = cleanEpisodeSubtitle(episode, episodeNumber),
                color = Color(0xFFAAAAAA),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = cleanEpisodeTitle(episode, episodeNumber),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!episode.overview.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = episode.overview!!,
                    color = Color(0xFF888888),
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = Color(0xFFE8A020),
            modifier = Modifier.size(24.dp)
        )
    }
}

// ── Title cleaning helpers ────────────────────────────────────────────────────

private fun cleanEpisodeSubtitle(episode: VideoWithMetadata, episodeNumber: Int): String {
    val fileName = episode.video.name
    val match = Regex("s(\\d{1,2})e(\\d{1,2})", RegexOption.IGNORE_CASE).find(fileName)
    return if (match != null) {
        "S${match.groupValues[1].padStart(2,'0')}E${match.groupValues[2].padStart(2,'0')}"
    } else {
        episode.subtitle.substringBefore("•").trim().ifBlank { "Episode $episodeNumber" }
    }
}

private fun cleanEpisodeTitle(episode: VideoWithMetadata, episodeNumber: Int): String {
    val fromSubtitle = episode.subtitle.substringAfter("•", "").trim()
    if (fromSubtitle.isNotBlank()) return fromSubtitle
    val fileNameTitle = cleanTvTitle(episode.video.name)
    val showTitle = cleanTvTitle(episode.title)
    return when {
        fileNameTitle.isNotBlank() && !fileNameTitle.equals(showTitle, ignoreCase = true) -> fileNameTitle
        showTitle.isNotBlank() -> showTitle
        else -> "Episode $episodeNumber"
    }
}

private fun cleanTvTitle(title: String): String {
    var cleaned = title.substringAfterLast("/").substringAfterLast("\\").substringBeforeLast(".")
        .replace(Regex("\\[.*?]"), " ").replace(Regex("\\(.*?\\)"), " ")
        .replace(".", " ").replace("_", " ").replace("-", " ")
    cleaned = cleaned.replace(Regex("s\\d{1,2}e\\d{1,2}", RegexOption.IGNORE_CASE), " ")
    cleaned = cleaned.replace(Regex("\\bseason\\s*\\d+\\b", RegexOption.IGNORE_CASE), " ")
    cleaned = cleaned.replace(Regex("\\bepisode\\s*\\d+\\b", RegexOption.IGNORE_CASE), " ")
    cleaned = cleaned.replace(Regex("\\b(2160p|1080p|720p|480p|4k|uhd|hdr10\\+?|hdr|dv|dolby|vision|x264|x265|h264|h265|hevc|web|webdl|webrip|bluray|brrip|hdrip|dvdrip|aac|ddp|dts|atmos|10bit|nf|amzn|yts|rarbg|eztv|tgx|mkv|mp4|avi)\\b", RegexOption.IGNORE_CASE), " ")
    return cleaned.replace(Regex("\\b(19|20)\\d{2}\\b"), " ").replace(Regex("\\s+"), " ").trim()
}
