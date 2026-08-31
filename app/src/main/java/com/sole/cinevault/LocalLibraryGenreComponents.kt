package com.sole.cinevault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.*

// ── Genre normalization ─────────────────────────────────────────────────
// TMDB uses slightly different genre names/groupings for movies vs TV shows
// (e.g. movies get "Science Fiction", TV shows get "Sci-Fi & Fantasy" for
// essentially the same thing), which previously showed up as two separate,
// near-duplicate chips. This collapses known synonyms into one canonical
// display name before the genre list is deduplicated.
private val genreNormalizationMap = mapOf(
    "science fiction" to "Sci-Fi & Fantasy",
    "sci-fi" to "Sci-Fi & Fantasy",
    "sci fi" to "Sci-Fi & Fantasy",
    "war & politics" to "War"
)

internal fun normalizeGenreName(raw: String): String {
    val key = raw.trim().lowercase()
    return genreNormalizationMap[key] ?: raw.trim()
}

// Generic Material icons representing each genre — evocative, not literal
// (there's no official "genre icon set"), consistent across the whole app.
private fun genreIconFor(name: String): ImageVector {
    val lower = name.lowercase()
    return when {
        lower.contains("action") -> Icons.Filled.Bolt
        lower.contains("adventure") -> Icons.Filled.Explore
        lower.contains("animation") -> Icons.Filled.Brush
        lower.contains("comedy") -> Icons.Filled.TheaterComedy
        lower.contains("crime") -> Icons.Filled.Gavel
        lower.contains("documentary") -> Icons.Filled.Videocam
        lower.contains("drama") -> Icons.Filled.TheaterComedy
        lower.contains("family") -> Icons.Filled.FamilyRestroom
        lower.contains("fantasy") || lower.contains("sci-fi") -> Icons.Filled.AutoAwesome
        lower.contains("history") -> Icons.Filled.HistoryEdu
        lower.contains("horror") -> Icons.Filled.DarkMode
        lower.contains("music") -> Icons.Filled.MusicNote
        lower.contains("mystery") -> Icons.Filled.Search
        lower.contains("romance") -> Icons.Rounded.Favorite
        lower.contains("thriller") -> Icons.Filled.Warning
        lower.contains("war") -> Icons.Filled.Shield
        lower.contains("western") -> Icons.Filled.Landscape
        else -> Icons.Filled.LocalMovies
    }
}

@Composable
internal fun GenreIconChip(name: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(62.dp).clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape)
                .background(GlassSurfaceStrong)
                .background(Brush.radialGradient(listOf(AmberGlow.copy(alpha = 0.30f), Color.Transparent)))
                .border(1.2.dp, Brush.verticalGradient(listOf(AmberGlow.copy(alpha = 0.70f), AmberDeep.copy(alpha = 0.30f))), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = genreIconFor(name), contentDescription = null, tint = AmberCore, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = name, color = TextBright, fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
        )
    }
}
