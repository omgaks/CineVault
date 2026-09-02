package com.sole.cinevault

import android.content.Context
import android.net.Uri
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
import com.sole.cinevault.subtitles.SubtitleSourceResolver
import com.sole.cinevault.subtitles.SubtitleTranslationEngine
import com.sole.cinevault.subtitles.SubtitleTranslationStatus
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.GlassSurfaceStrong
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextMuted
import com.sole.cinevault.ui.theme.glassPanel

private const val LANGUAGE_PREFS = "ai_subtitle_language_preferences"
private const val FAVORITES_KEY = "favorite_translation_languages"

@Composable
fun SubtitleTranslationPanel(
    status: SubtitleTranslationStatus,
    activeSource: SubtitleSourceResolver.Resolved?,
    generatedFiles: List<GeneratedSubtitleFile>,
    activeSubtitleUri: Uri?,
    onLoadGenerated: (GeneratedSubtitleFile) -> Unit,
    onTranslate: (SubtitleTranslationEngine.SupportedLanguage) -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var favoriteCodes by remember { mutableStateOf(loadFavoriteLanguageCodes(context)) }
    val busy = status is SubtitleTranslationStatus.Translating
    val allLanguages = SubtitleTranslationEngine.commonTargetLanguages
    val favorites = allLanguages.filter { it.mlKitCode in favoriteCodes }
    val others = allLanguages.filterNot { it.mlKitCode in favoriteCodes }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel(cornerRadius = 22.dp, fill = GlassSurfaceStrong)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "AI Translation",
                color = TextBright,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Rounded.Close, "Close", tint = TextMuted)
            }
        }

        Text(
            activeSource?.let {
                "Active: ${it.label} • ${it.source}"
            } ?: "Load a Subtitle Studio download or local/generated SRT first.",
            color = if (activeSource != null) TextBright else AmberCore,
            modifier = Modifier.padding(top = 6.dp),
        )

        Text(
            "Whisper download is not required for translation.",
            color = TextMuted,
            modifier = Modifier.padding(top = 3.dp),
        )

        if (generatedFiles.isNotEmpty()) {
            Text(
                "Generated / translated files",
                color = TextBright,
                modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(generatedFiles, key = { it.fileName }) { file ->
                    val selected = activeSubtitleUri?.toString() == file.uri.toString()
                    OutlinedButton(
                        onClick = { onLoadGenerated(file) },
                        enabled = !busy,
                    ) {
                        if (selected) {
                            Icon(Icons.Rounded.Check, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                        }
                        Text(
                            if (file.cueCount >= 0) "${file.label} • ${file.cueCount}" else file.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        when (status) {
            is SubtitleTranslationStatus.Translating -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    Text(status.phase, color = TextBright, modifier = Modifier.weight(1f))
                    Text("${status.percent}%", color = TextMuted)
                }
                LinearProgressIndicator(
                    progress = { status.percent.coerceIn(0, 100) / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
                OutlinedButton(onClick = onStop, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Stop translation")
                }
            }

            is SubtitleTranslationStatus.Ready ->
                Text(
                    "Translation ready • ${status.cueCount} cues",
                    color = TextBright,
                    modifier = Modifier.padding(top = 10.dp),
                )

            is SubtitleTranslationStatus.Failed ->
                Text(
                    status.reason,
                    color = AmberCore,
                    modifier = Modifier.padding(top = 10.dp),
                )

            SubtitleTranslationStatus.Idle -> Unit
        }

        if (favorites.isNotEmpty()) {
            Text(
                "Favorites",
                color = TextBright,
                modifier = Modifier.padding(top = 12.dp, bottom = 5.dp),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(favorites, key = { it.mlKitCode }) { language ->
                    OutlinedButton(
                        onClick = { onTranslate(language) },
                        enabled = !busy && activeSource != null,
                    ) {
                        Icon(
                            Icons.Rounded.Star,
                            null,
                            tint = AmberCore,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(language.label, maxLines = 1)
                    }
                }
            }
        }

        Text(
            "Languages",
            color = TextBright,
            modifier = Modifier.padding(top = 12.dp, bottom = 5.dp),
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.height(155.dp),
        ) {
            items(others + favorites, key = { it.mlKitCode }) { language ->
                val favorite = language.mlKitCode in favoriteCodes
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedButton(
                        onClick = { onTranslate(language) },
                        enabled = !busy && activeSource != null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            language.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(
                        onClick = {
                            favoriteCodes = toggleFavoriteLanguage(
                                context,
                                favoriteCodes,
                                language.mlKitCode,
                            )
                        },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            if (favorite) "Remove favorite" else "Add favorite",
                            tint = if (favorite) AmberCore else TextMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun loadFavoriteLanguageCodes(context: Context): Set<String> =
    context.getSharedPreferences(LANGUAGE_PREFS, Context.MODE_PRIVATE)
        .getStringSet(FAVORITES_KEY, emptySet())
        ?.toSet()
        .orEmpty()

private fun toggleFavoriteLanguage(
    context: Context,
    current: Set<String>,
    code: String,
): Set<String> {
    val updated = current.toMutableSet().apply {
        if (!add(code)) remove(code)
    }.toSet()

    context.getSharedPreferences(LANGUAGE_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putStringSet(FAVORITES_KEY, updated)
        .apply()

    return updated
}
