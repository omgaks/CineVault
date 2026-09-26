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

internal fun LazyGridScope.LocalLibraryGenresShelf(
    selectedCategory: String,
    visibleSortedVideos: List<VideoWithMetadata>,
    onGenreClick: (String) -> Unit,
    isTelevision: Boolean = false
) {
    if (selectedCategory != "All") return

    val genreNames = visibleSortedVideos
        .flatMap { it.genres }
        .map { normalizeGenreName(it) }
        .distinct()
        .sortedBy { it.lowercase() }

    if (genreNames.isEmpty()) return

    item(span = { GridItemSpan(maxLineSpan) }) {
        Column {
            Text(
                text = "Genres",
                color = TextBright,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            val focusManager = LocalFocusManager.current
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                items(items = genreNames, key = { it }) { genre ->
                    TvFocusableSlot(isTelevision = isTelevision, shape = RoundedCornerShape(50), onActivate = { onGenreClick(genre) }) {
                        GenreIconChip(
                            name = genre,
                            onClick = { onGenreClick(genre) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
