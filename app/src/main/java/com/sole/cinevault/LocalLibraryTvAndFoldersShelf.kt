package com.sole.cinevault

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.library.RestrictedFolder
import com.sole.cinevault.library.TvGroup
import com.sole.cinevault.library.folderIdFromRestrictedMarker
import com.sole.cinevault.library.loadRestrictedFolders
import com.sole.cinevault.tvmode.TvFocusableSlot
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
    onRestrictedFolderLongClick: (String, List<String>) -> Unit,
    isTelevision: Boolean = false
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

            val focusManager = LocalFocusManager.current
            // Keep the shelf where the person left it when a show is opened and closed again.
            val shelfState = rememberLazyListState(ShelfScrollMemory.index, ShelfScrollMemory.offset)
            DisposableEffect(shelfState) {
                onDispose {
                    ShelfScrollMemory.index = shelfState.firstVisibleItemIndex
                    ShelfScrollMemory.offset = shelfState.firstVisibleItemScrollOffset
                }
            }
            LazyRow(
                state = shelfState,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.then(
                    if (isTelevision) {
                        Modifier.onKeyEvent { keyEvent ->
                            if (keyEvent.type != KeyEventType.KeyUp) return@onKeyEvent false
                            when (keyEvent.key) {
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
                )
            ) {
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
                        is TvGroup -> TvFocusableSlot(isTelevision = isTelevision, shape = RoundedCornerShape(10.dp), onActivate = { onTvGroupClick(entry) }) {
                            Column(
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
                                val owned = entry.episodes.size
                                val aired by produceState<Int?>(null, entry.tmdbId) {
                                    value = entry.tmdbId?.let { id ->
                                        com.sole.cinevault.metadata.ShowCatalogRepository.load(context, id)?.let { cat ->
                                            com.sole.cinevault.library.airedEpisodeCount(cat, com.sole.cinevault.metadata.ShowCatalogRepository.today())
                                        }
                                    }
                                }
                                Text(
                                    text = aired?.takeIf { it > owned }?.let { "$owned of $it episodes" } ?: "$owned Episodes",
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        is RestrictedShelfEntry -> {
                            val thumbnailSourcePath = entry.folder.lastPlayedVideoPath
                                ?.takeIf { path -> entry.items.any { it.video.path == path } }
                                ?: entry.items.firstOrNull()?.video?.path

                            TvFocusableSlot(isTelevision = isTelevision, shape = RoundedCornerShape(10.dp), onActivate = { onRestrictedFolderClick(entry.folder) }) {
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
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/** Where the TV Shows & Folders shelf was last scrolled to; survives leaving and re-entering the library. */
internal object ShelfScrollMemory {
    @Volatile var index: Int = 0
    @Volatile var offset: Int = 0
}
