package com.sole.cinevault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onCuratedCollectionClick: (String) -> Unit
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

            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(items = collectionShelf, key = { it.key }) { entry ->
                    CollectionShelfCard(
                        title = entry.displayName,
                        backdropUrl = entry.backdropUrl,
                        onClick = {
                            if (entry.isCurated) {
                                onCuratedCollectionClick(entry.displayName)
                            } else {
                                entry.collectionId?.let {
                                    onNativeCollectionClick(it, entry.displayName)
                                }
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
