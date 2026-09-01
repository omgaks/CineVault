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

@Composable
fun SubtitleGenerationMenu(
    status: SubtitleGenerationStatus,
    modelReady: Boolean,
    modelName: String,
    modelSizeLabel: String,
    generatedFiles: List<GeneratedSubtitleFile>,
    activeSubtitleUri: android.net.Uri?,
    onDownloadModel: () -> Unit,
    onGenerate: () -> Unit,
    onLoadGenerated: (GeneratedSubtitleFile) -> Unit,
    onTranslate: (SubtitleTranslationEngine.SupportedLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var favoriteCodes by remember {
        mutableStateOf(loadFavoriteLanguageCodes(context))
    }

    val busy =
        status is SubtitleGenerationStatus.DownloadingModel ||
            status is SubtitleGenerationStatus.Generating ||
            status is SubtitleGenerationStatus.Translating

    val allLanguages = SubtitleTranslationEngine.commonTargetLanguages
    val favoriteLanguages = allLanguages.filter { it.mlKitCode in favoriteCodes }
    val remainingLanguages = allLanguages.filterNot { it.mlKitCode in favoriteCodes }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel(
                cornerRadius = 22.dp,
                fill = GlassSurfaceStrong,
            )
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "AI Subtitles",
                color = TextBright,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close AI Subtitles",
                    tint = TextMuted,
                )
            }
        }

        if (!modelReady) {
            Text(
                "$modelName • optional $modelSizeLabel download",
                color = TextMuted,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                "Stored separately from the CineVault APK.",
                color = TextMuted,
                modifier = Modifier.padding(top = 2.dp),
            )

            Button(
                onClick = onDownloadModel,
                enabled = !busy,
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Text("Download Standard Model")
            }
        } else {
            Text(
                "$modelName installed ✓",
                color = TextMuted,
                modifier = Modifier.padding(top = 6.dp),
            )

            Button(
                onClick = onGenerate,
                enabled = !busy,
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Text("Generate from audio")
            }
        }

        if (generatedFiles.isNotEmpty()) {
            Text(
                "Generated subtitles",
                color = TextBright,
                modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(
                    items = generatedFiles,
                    key = { it.fileName },
                ) { generated ->
                    val selected =
                        activeSubtitleUri?.toString() == generated.uri.toString()

                    OutlinedButton(
                        onClick = { onLoadGenerated(generated) },
                        enabled = !busy,
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                        }

                        Text(
                            text =
                                if (generated.cueCount >= 0) {
                                    "${generated.label} • ${generated.cueCount}"
                                } else {
                                    generated.label
                                },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        when (status) {
            is SubtitleGenerationStatus.DownloadingModel ->
                ProgressRow("Downloading model", status.percent)

            is SubtitleGenerationStatus.Generating ->
                ProgressRow(
                    if (status.phase.equals("Transcribing", ignoreCase = true)) {
                        "Transcribing"
                    } else {
                        status.phase
                    },
                    status.percent,
                )

            is SubtitleGenerationStatus.Translating ->
                ProgressRow("Translating", status.percent)

            is SubtitleGenerationStatus.Failed ->
                Text(
                    status.reason,
                    color = AmberCore,
                    modifier = Modifier.padding(top = 10.dp),
                )

            is SubtitleGenerationStatus.Ready ->
                Text(
                    if (status.cueCount >= 0) {
                        "Subtitle ready • ${status.cueCount} cues"
                    } else {
                        "Subtitle ready"
                    },
                    color = TextBright,
                    modifier = Modifier.padding(top = 10.dp),
                )

            SubtitleGenerationStatus.Idle -> Unit
        }

        Text(
            "Translate current subtitle",
            color = TextBright,
            modifier = Modifier.padding(top = 14.dp),
        )

        Text(
            "Tap ★ to keep favorite languages first.",
            color = TextMuted,
            modifier = Modifier.padding(top = 2.dp, bottom = 7.dp),
        )

        if (favoriteLanguages.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(
                    items = favoriteLanguages,
                    key = { it.mlKitCode },
                ) { language ->
                    OutlinedButton(
                        onClick = { onTranslate(language) },
                        enabled = !busy,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = "Favorite",
                            tint = AmberCore,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(language.label, maxLines = 1)
                    }
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .height(if (favoriteLanguages.isEmpty()) 170.dp else 135.dp)
                .padding(top = 8.dp),
        ) {
            items(
                items = remainingLanguages + favoriteLanguages,
                key = { it.mlKitCode },
            ) { language ->
                val favorite = language.mlKitCode in favoriteCodes

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedButton(
                        onClick = { onTranslate(language) },
                        enabled = !busy,
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
                            favoriteCodes =
                                toggleFavoriteLanguage(
                                    context = context,
                                    current = favoriteCodes,
                                    code = language.mlKitCode,
                                )
                        },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector =
                                if (favorite) Icons.Rounded.Star
                                else Icons.Rounded.StarBorder,
                            contentDescription =
                                if (favorite) "Remove favorite"
                                else "Add favorite",
                            tint =
                                if (favorite) AmberCore
                                else TextMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressRow(
    label: String,
    percent: Int,
) {
    Column(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                label,
                color = TextBright,
                modifier = Modifier.weight(1f),
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                "$percent%",
                color = TextMuted,
            )
        }

        LinearProgressIndicator(
            progress = { percent.coerceIn(0, 100) / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        )
    }
}

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
