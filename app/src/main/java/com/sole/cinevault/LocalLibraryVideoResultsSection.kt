package com.sole.cinevault

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.tvmode.TvFocusableSlot
import com.sole.cinevault.ui.theme.*

internal fun LazyGridScope.LocalLibraryEmptyStateSection(
    selectedCategory: String,
    isScanning: Boolean,
    filteredVideos: List<VideoWithMetadata>,
    tvGroupsEmpty: Boolean,
    secretUnlocked: Boolean,
    allVideosEmpty: Boolean,
    onScan: () -> Unit,
    isTelevision: Boolean = false
) {
    val shouldShowEmptyState =
        selectedCategory != "Folders" &&
            selectedCategory != "Duplicates" &&
            !isScanning &&
            filteredVideos.isEmpty() &&
            tvGroupsEmpty &&
            !(selectedCategory == "Secret" && !secretUnlocked)

    if (shouldShowEmptyState) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            if (allVideosEmpty) {
                EmptyStateBlock(
                    icon = Icons.Filled.LocalMovies,
                    title = "Your library is empty",
                    subtitle = "Scan your device or add a network share to get started."
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TvFocusableSlot(isTelevision = isTelevision, shape = RoundedCornerShape(50), onActivate = onScan) {
                            Text(
                                text = "Scan Device Videos",
                                color = Color.Black,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(AmberGlow.copy(alpha = 0.90f))
                                    .clickable(onClick = onScan)
                                    .padding(horizontal = 18.dp, vertical = 10.dp)
                            )
                        }
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
}

internal fun LazyGridScope.LocalLibraryVideoItemsSection(
    selectedCategory: String,
    filteredVideos: List<VideoWithMetadata>,
    isGridMode: Boolean,
    onItemClick: (VideoWithMetadata) -> Unit,
    onPlayClick: (VideoWithMetadata) -> Unit,
    onItemLongPress: (VideoWithMetadata) -> Unit,
    isTelevision: Boolean = false
) {
    if (filteredVideos.isEmpty() || selectedCategory == "Folders") return

    item(span = { GridItemSpan(maxLineSpan) }) {
        Text(
            text = when (selectedCategory) {
                "Movies" -> "Movies"
                "Downloads" -> "Downloads"
                "Favorites" -> "Favorites"
                "Secret" -> "Secret Folder"
                "Continue Watching" -> "Continue Watching"
                else -> "Movies & Downloads"
            },
            color = TextBright,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
    }

    // LibraryGridCard/LibraryCard (Screens.kt) are used in several other
    // screens (Home, Media Intelligence) and aren't touched here — wrapped
    // from outside, same pattern as every widely-shared component in this
    // project. Their onActivate is the card's main onClick (open Detail);
    // the nested QuickPlayButton shortcut and long-press context menu are
    // real, acknowledged gaps — not reachable via D-pad in this phase, since
    // both are laid out inside these shared composables, not as call-site
    // siblings, and restructuring components used in four other files was
    // judged too risky for this pass.
    if (isGridMode) {
        items(
            items = filteredVideos,
            key = { it.video.path }
        ) { item ->
            TvFocusableSlot(isTelevision = isTelevision, shape = RoundedCornerShape(10.dp), onActivate = { onItemClick(item) }) {
                LibraryGridCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    onPlayClick = onPlayClick,
                    onLongPress = { onItemLongPress(it) }
                )
            }
        }
    } else {
        items(
            items = filteredVideos,
            key = { it.video.path },
            span = { GridItemSpan(maxLineSpan) }
        ) { item ->
            TvFocusableSlot(isTelevision = isTelevision, shape = RoundedCornerShape(10.dp), onActivate = { onItemClick(item) }) {
                LibraryCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    onLongPress = { onItemLongPress(it) }
                )
            }
        }
    }
}
