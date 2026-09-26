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
    val collectionId: Int?
)

internal fun LazyGridScope.LocalLibraryCollectionsShelf(
    selectedCategory: String,
    visibleSortedVideos: List<VideoWithMetadata>,
    onNativeCollectionClick: (Int, String) -> Unit,
    onCuratedCollectionClick: (String) -> Unit,
    isTelevision: Boolean = false
) {
    if (selectedCategory != "All") return

    val nativeEntries = visibleSortedVideos
        .distinctBy { it.collectionId }
        .mapNotNull { video ->
            val collectionId = video.collectionId
            val collectionName = video.collectionName
            if (collectionId == null || collectionName == null) {
                null
            } else {
                CollectionShelfEntry(
                    key = "native:$collectionId",
                    displayName = collectionName,
                    backdropUrl = video.backdropUrl,
                    isCurated = false,
                    collectionId = collectionId
                )
            }
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
                    TvFocusableSlot(isTelevision = isTelevision, shape = RoundedCornerShape(10.dp), onActivate = onActivate) {
                        CollectionShelfCard(
                            title = entry.displayName,
                            backdropUrl = entry.backdropUrl,
                            onClick = onActivate
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
