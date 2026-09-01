package com.sole.cinevault

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.*
import java.io.File

internal data class VideoFolder(
    val folderName: String,
    val folderPath: String,
    val videos: List<VideoWithMetadata>
)

internal fun groupVideosByFolder(
    videos: List<VideoWithMetadata>
): List<VideoFolder> {
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
internal fun LazyGridScope.LocalLibraryFoldersSection(
    selectedCategory: String,
    videoFolders: List<VideoFolder>,
    expandedFolders: Set<String>,
    onExpandedFoldersChange: (Set<String>) -> Unit,
    isGridMode: Boolean,
    gridColumns: Int,
    onItemClick: (VideoWithMetadata) -> Unit,
    onPlayClick: (VideoWithMetadata) -> Unit,
    onItemLongPress: (VideoWithMetadata) -> Unit,
    onFolderLongPress: (String, List<String>) -> Unit
) {
    if (selectedCategory != "Folders") return

    if (videoFolders.isEmpty()) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No folders found. Scan your library first.",
                    color = TextMuted,
                    fontSize = 15.sp
                )
            }
        }
        return
    }

    item(span = { GridItemSpan(maxLineSpan) }) {
        Text(
            text = "Folders (${videoFolders.size})",
            color = TextBright,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
    }

    videoFolders.forEach { folder ->
        item(span = { GridItemSpan(maxLineSpan) }) {
            val isExpanded = expandedFolders.contains(folder.folderPath)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassPanel(cornerRadius = 14.dp)
                    .combinedClickable(
                        onClick = {
                            onExpandedFoldersChange(
                                if (isExpanded) {
                                    expandedFolders - folder.folderPath
                                } else {
                                    expandedFolders + folder.folderPath
                                }
                            )
                        },
                        onLongClick = {
                            onFolderLongPress(
                                folder.folderName,
                                folder.videos.map { it.video.path }
                            )
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
                    Text(
                        text = folder.folderName,
                        color = TextBright,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = "${folder.videos.size} video${if (folder.videos.size != 1) "s" else ""}",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }

                Icon(
                    imageVector = if (isExpanded) {
                        Icons.Filled.KeyboardArrowUp
                    } else {
                        Icons.Filled.KeyboardArrowDown
                    },
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (expandedFolders.contains(folder.folderPath)) {
            if (isGridMode) {
                items(
                    items = folder.videos,
                    key = { it.video.path }
                ) { item ->
                    LibraryGridCard(
                        item = item,
                        onClick = { onItemClick(item) },
                        onPlayClick = onPlayClick,
                        onLongPress = { onItemLongPress(it) }
                    )
                }

                val remainder = folder.videos.size % gridColumns
                if (remainder != 0) {
                    repeat(gridColumns - remainder) {
                        item {
                            Spacer(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            } else {
                items(
                    items = folder.videos,
                    key = { it.video.path },
                    span = { GridItemSpan(maxLineSpan) }
                ) { item ->
                    LibraryCard(
                        item = item,
                        onClick = { onItemClick(item) },
                        onLongPress = { onItemLongPress(it) }
                    )
                }
            }
        }
    }
}
