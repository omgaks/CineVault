package com.sole.cinevault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.sole.cinevault.ui.theme.*

internal enum class LibrarySearchSort(val label: String) {
    BestMatch("Best match"), TitleAz("A–Z"), Rating("Rating")
}

/**
 * The Search card. It never closes on its own while you type; the words stay
 * in the box (and keep filtering the shelf) even after the card is closed.
 */
@Composable
internal fun LibrarySearchPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    filters: SearchFilters,
    onFiltersChange: (SearchFilters) -> Unit,
    sort: LibrarySearchSort,
    onSortChange: (LibrarySearchSort) -> Unit,
    recents: List<String>,
    libraryEmpty: Boolean,
    resultCount: Int
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (libraryEmpty) {
            Text(
                text = "Your library is empty. Scan for films first, then search them here.",
                color = TextMuted,
                fontSize = CineType.Label
            )
            return@Column
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            shape = RoundedCornerShape(50),
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextMuted) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = TextMuted)
                    }
                }
            },
            placeholder = { Text("Title, cast, director, genre, year", color = TextFaint) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextBright,
                unfocusedTextColor = TextBright,
                focusedContainerColor = GlassSurfaceStrong,
                unfocusedContainerColor = GlassSurface,
                focusedBorderColor = GelGold.copy(alpha = 0.65f),
                unfocusedBorderColor = GlassBorderTop,
                cursorColor = GelGold
            )
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CineChip("Films", filters.films, { onFiltersChange(filters.copy(films = !filters.films)) }, gel = GelGold)
            CineChip("Rating 7+", filters.rating7, { onFiltersChange(filters.copy(rating7 = !filters.rating7)) }, gel = GelGold)
            CineChip("4K / HDR", filters.fourKOrHdr, { onFiltersChange(filters.copy(fourKOrHdr = !filters.fourKOrHdr)) }, gel = GelGold)
            CineChip("Unwatched", filters.unwatched, { onFiltersChange(filters.copy(unwatched = !filters.unwatched)) }, gel = GelGold)
            CineChip("Favourites", filters.favourites, { onFiltersChange(filters.copy(favourites = !filters.favourites)) }, gel = GelGold)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LibrarySearchSort.values().forEach { option ->
                CineChip(option.label, sort == option, { onSortChange(option) }, gel = GelMint)
            }
        }
        if (query.isBlank() && !filters.anyOn && recents.isNotEmpty()) {
            Text("Recent", color = TextFaint, fontSize = CineType.Caption)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recents.forEach { r -> CineChip(r, false, { onQueryChange(r) }, gel = GelSky) }
            }
        } else if (query.isNotBlank() || filters.anyOn) {
            Text(
                text = if (resultCount == 0) "No matches. Try fewer words or clear a filter."
                else "$resultCount ${if (resultCount == 1) "result" else "results"} below",
                color = TextMuted,
                fontSize = CineType.Label
            )
        }
    }
}
