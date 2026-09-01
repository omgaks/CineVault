package com.sole.cinevault

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.library.RestrictedFolder
import com.sole.cinevault.library.TvGroup
import com.sole.cinevault.library.folderIdFromRestrictedMarker
import com.sole.cinevault.library.loadRestrictedFolders
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextMuted

private data class RestrictedShelfEntry(
    val folder: RestrictedFolder,
    val items: List<VideoWithMetadata>
)

@OptIn(ExperimentalFoundationApi::class)
internal fun LazyGridScope.LocalLibraryTvAndFoldersShelf(
    selectedCategory: String,
    visibleSortedVideos: List<VideoWithMetadata>,
    tvGroups: List<TvGroup>,
    context: Context,
    onTvGroupClick: (TvGroup) -> Unit,
    onTvGroupLongClick: (TvGroup) -> Unit,
    onRestrictedFolderClick: (RestrictedFolder) -> Unit,
    onRestrictedFolderLongClick: (String, List<String>) -> Unit
) {
    if (selectedCategory !in listOf("All", "TV Shows", "Folders", "Downloads")) return

    val restrictedItems = visibleSortedVideos.filter {
        it.type.equals("restricted", ignoreCase = true)
    }

    val restrictedShelf = if (restrictedItems.isEmpty()) {
        emptyList()
    } else {
        loadRestrictedFolders(context).mapNotNull { folder ->
            val items = restrictedItems.filter {
                folderIdFromRestrictedMarker(it.video.folderPath) == folder.id
            }
            if (items.isEmpty()) null else RestrictedShelfEntry(folder, items)
        }
    }

    val showTvInShelf =
        selectedCategory in listOf("All", "TV Shows") && tvGroups.isNotEmpty()
    val showFoldersInShelf =
        selectedCategory in listOf("All", "Folders", "Downloads") && restrictedShelf.isNotEmpty()

    val combinedShelf: List<Any> =
        (if (showTvInShelf) tvGroups else emptyList()) +
            (if (showFoldersInShelf) restrictedShelf else emptyList())

    if (combinedShelf.isEmpty()) return

    item(span = { GridItemSpan(maxLineSpan) }) {
        Column {
            Text(
                text = "TV Shows & Folders",
                color = TextBright,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

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
                                    onLongClick = { onTvGroupLongClick(entry) }
                                )
                        ) {
                            PosterBox(
                                posterUrl = entry.posterUrl,
                                modifier = Modifier.fillMaxWidth().height(210.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = entry.showName,
                                color = TextBright,
                                maxLines = 1,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${entry.episodes.size} Episodes",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
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
                                onLongClick = {
                                    onRestrictedFolderLongClick(
                                        entry.folder.displayName,
                                        entry.items.map { it.video.path }
                                    )
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
