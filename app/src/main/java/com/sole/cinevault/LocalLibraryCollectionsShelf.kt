package com.sole.cinevault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import com.sole.cinevault.collections.CollectionPlanner
import com.sole.cinevault.collections.CollectionRepository
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
import com.sole.cinevault.tvmode.TvFocusableSlot
import com.sole.cinevault.ui.theme.TextBright

private data class CollectionShelfEntry(
    val key: String,
    val displayName: String,
    val backdropUrl: String?,
    val isCurated: Boolean,
    val collectionId: Int?,
    val ownedCount: Int = 0
)

internal fun LazyGridScope.LocalLibraryCollectionsShelf(
    selectedCategory: String,
    visibleSortedVideos: List<VideoWithMetadata>,
    onNativeCollectionClick: (Int, String) -> Unit,
    onCuratedCollectionClick: (String) -> Unit,
    isTelevision: Boolean = false
) {
    if (selectedCategory != "All") return

    // A TMDB collection earns a shelf card once the user owns TWO OR MORE of its
    // films — a single owned film no longer clutters the shelf. That film's Detail
    // page still links to the full collection page. Restricted-folder films are
    // excluded here, matching the collection page's own owned list.
    val countable = visibleSortedVideos.filterNot { it.type.equals("restricted", ignoreCase = true) }
    val ownedCounts = CollectionPlanner.ownedCountsByCollection(countable)
    val nativeEntries = countable
        .filter { it.collectionId != null && it.collectionName != null }
        .distinctBy { it.collectionId }
        .filter { CollectionPlanner.earnsShelfCard(ownedCounts[it.collectionId] ?: 0) }
        .map { video ->
            CollectionShelfEntry(
                key = "native:${video.collectionId}",
                displayName = video.collectionName.orEmpty(),
                backdropUrl = video.backdropUrl,
                isCurated = false,
                collectionId = video.collectionId,
                ownedCount = ownedCounts[video.collectionId] ?: 0
            )
        }

    val curatedNames = visibleSortedVideos
        .flatMap { it.curatedCollections }
        .distinct()

    val curatedEntries = curatedNames.map { name ->
        val backdrop = visibleSortedVideos
            .firstOrNull {
                it.curatedCollections.contains(name) &&
                    !it.backdropUrl.isNullOrBlank()
            }
            ?.backdropUrl

        CollectionShelfEntry(
            key = "curated:$name",
            displayName = name,
            backdropUrl = backdrop,
            isCurated = true,
            collectionId = null
        )
    }

    val collectionShelf = (nativeEntries + curatedEntries)
        .sortedBy { it.displayName.lowercase() }

    if (collectionShelf.isEmpty()) return

    item(span = { GridItemSpan(maxLineSpan) }) {
        Column {
            Text(
                text = "Collections",
                color = TextBright,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))

            val focusManager = LocalFocusManager.current
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                items(items = collectionShelf, key = { it.key }) { entry ->
                    val onActivate = {
                        if (entry.isCurated) {
                            onCuratedCollectionClick(entry.displayName)
                        } else {
                            entry.collectionId?.let {
                                onNativeCollectionClick(it, entry.displayName)
                            }
                            Unit
                        }
                    }
                    // Released-film total from TMDB's collection (cache-first, 7-day TTL;
                    // honours the metadata privacy switch). Null until known.
                    val context = LocalContext.current
                    val total by produceState<Int?>(initialValue = null, key1 = entry.collectionId) {
                        value = entry.collectionId?.let { CollectionRepository.releasedTotal(context, it) }
                    }
                    TvFocusableSlot(isTelevision = isTelevision, shape = RoundedCornerShape(18.dp), onActivate = onActivate) {
                        CollectionShelfCard(
                            title = entry.displayName,
                            backdropUrl = entry.backdropUrl,
                            onClick = onActivate,
                            ownedCount = if (entry.isCurated) null else entry.ownedCount,
                            totalCount = total
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
