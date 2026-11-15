package com.sole.cinevault

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sole.cinevault.subtitles.GeneratedSubtitleFile
import com.sole.cinevault.subtitles.SubtitleGenerationStatus
import com.sole.cinevault.subtitles.SubtitleTranslationEngine
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.GlassSurfaceStrong
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextMuted
import com.sole.cinevault.ui.theme.glassPanel

private const val LANGUAGE_PREFS = "ai_subtitle_language_preferences"
private const val FAVORITES_KEY = "favorite_translation_languages"

private fun loadFavoriteLanguageCodes(
    context: Context,
): Set<String> {
    return context
        .getSharedPreferences(LANGUAGE_PREFS, Context.MODE_PRIVATE)
        .getStringSet(FAVORITES_KEY, emptySet())
        ?.toSet()
        .orEmpty()
}

private fun toggleFavoriteLanguage(
    context: Context,
    current: Set<String>,
    code: String,
): Set<String> {
    val updated =
        current.toMutableSet().apply {
            if (!add(code)) remove(code)
        }.toSet()

    context
        .getSharedPreferences(LANGUAGE_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putStringSet(FAVORITES_KEY, updated)
        .apply()

    return updated
}
