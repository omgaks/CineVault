package com.sole.cinevault

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val scrollState = rememberScrollState()

    // One vertical scroll owner for the ENTIRE window. Generated files,
    // favorites and languages are normal rows inside it — no nested
    // LazyVerticalGrid fighting for the tiny remaining viewport.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel(cornerRadius = 20.dp, fill = GlassSurfaceStrong.copy(alpha = 0.82f))
            .border(1.dp, AmberCore.copy(alpha = 0.20f), RoundedCornerShape(20.dp))
            .verticalScroll(scrollState)
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "AI TRANSLATION",
                color = AmberCore,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(AmberCore.copy(alpha = 0.12f))
                    .border(1.dp, AmberCore.copy(alpha = 0.30f), RoundedCornerShape(50))
                    .padding(horizontal = 11.dp, vertical = 6.dp),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Rounded.Close, "Close", tint = TextMuted)
            }
        }

        Text(
            activeSource?.let { "Active: ${it.label} • ${it.source}" }
                ?: "Load a Subtitle Studio download or local/generated SRT first.",
            color = if (activeSource != null) TextBright else AmberCore,
            fontSize = 11.5.sp,
            modifier = Modifier.padding(top = 7.dp),
        )

        Text(
            "Whisper download is not required for translation.",
            color = TextMuted,
            fontSize = 10.5.sp,
            modifier = Modifier.padding(top = 3.dp),
        )

        when (status) {
            is SubtitleTranslationStatus.Translating -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                ) {
                    Text(status.phase, color = TextBright, fontSize = 10.5.sp, modifier = Modifier.weight(1f))
                    Text("${status.percent}%", color = TextMuted, fontSize = 10.5.sp)
                }
                LinearProgressIndicator(
                    progress = { status.percent.coerceIn(0, 100) / 100f },
                    modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                )
                OutlinedButton(onClick = onStop, modifier = Modifier.padding(top = 6.dp)) {
                    Text("Stop translation", fontSize = 10.5.sp)
                }
            }
            is SubtitleTranslationStatus.Ready ->
                Text(
                    "Translation ready • ${status.cueCount} cues",
                    color = TextBright,
                    fontSize = 10.5.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            is SubtitleTranslationStatus.Failed ->
                Text(
                    status.reason,
                    color = AmberCore,
                    fontSize = 10.5.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            SubtitleTranslationStatus.Idle -> Unit
        }

        if (generatedFiles.isNotEmpty()) {
            SectionPill("GENERATED / TRANSLATED FILES")
            generatedFiles.forEach { file ->
                val selected = activeSubtitleUri?.toString() == file.uri.toString()
                OutlinedButton(
                    onClick = { onLoadGenerated(file) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp),
                ) {
                    if (selected) {
                        Icon(Icons.Rounded.Check, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                    }
                    Text(
                        if (file.cueCount >= 0) "${file.label} • ${file.cueCount}" else file.label,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (favorites.isNotEmpty()) {
            SectionPill("FAVORITES")
            favorites.forEach { language ->
                LanguageChoiceRow(
                    language = language,
                    favorite = true,
                    enabled = !busy && activeSource != null,
                    onTranslate = { onTranslate(language) },
                    onFavorite = {
                        favoriteCodes = toggleFavoriteLanguage(
                            context, favoriteCodes, language.mlKitCode
                        )
                    },
                )
            }
        }

        SectionPill("LANGUAGES")
        others.forEach { language ->
            LanguageChoiceRow(
                language = language,
                favorite = false,
                enabled = !busy && activeSource != null,
                onTranslate = { onTranslate(language) },
                onFavorite = {
                    favoriteCodes = toggleFavoriteLanguage(
                        context, favoriteCodes, language.mlKitCode
                    )
                },
            )
        }

        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun SectionPill(text: String) {
    Text(
        text,
        color = AmberCore,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .padding(top = 10.dp, bottom = 6.dp)
            .clip(RoundedCornerShape(50))
            .background(AmberCore.copy(alpha = 0.10f))
            .border(1.dp, AmberCore.copy(alpha = 0.24f), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

@Composable
private fun LanguageChoiceRow(
    language: SubtitleTranslationEngine.SupportedLanguage,
    favorite: Boolean,
    enabled: Boolean,
    onTranslate: () -> Unit,
    onFavorite: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onTranslate,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        ) {
            if (favorite) {
                Icon(Icons.Rounded.Star, null, tint = AmberCore, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
            }
            Text(language.label, maxLines = 1, fontSize = 10.5.sp)
        }
        Spacer(Modifier.width(5.dp))
        IconButton(onClick = onFavorite, modifier = Modifier.size(34.dp)) {
            Icon(
                if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                if (favorite) "Remove favorite" else "Add favorite",
                tint = if (favorite) AmberCore else TextMuted,
                modifier = Modifier.size(17.dp),
            )
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
