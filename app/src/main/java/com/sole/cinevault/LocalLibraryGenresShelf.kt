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

internal fun LazyGridScope.LocalLibraryGenresShelf(
    selectedCategory: String,
    visibleSortedVideos: List<VideoWithMetadata>,
    onGenreClick: (String) -> Unit
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

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(items = genreNames, key = { it }) { genre ->
                    GenreIconChip(
                        name = genre,
                        onClick = { onGenreClick(genre) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
