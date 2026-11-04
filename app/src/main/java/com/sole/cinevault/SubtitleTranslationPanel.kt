package com.sole.cinevault

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    onRereadEmbedded: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var favoriteCodes by remember { mutableStateOf(loadFavoriteLanguageCodes(context)) }
    var languageQuery by remember { mutableStateOf("") }

    val busy = status is SubtitleTranslationStatus.Translating
    val allLanguages = SubtitleTranslationEngine.commonTargetLanguages
    val normalizedQuery = languageQuery.trim()
    val visibleLanguages =
        if (normalizedQuery.isBlank()) {
            allLanguages
        } else {
            allLanguages.filter { language ->
                language.label.contains(normalizedQuery, ignoreCase = true) ||
                    language.mlKitCode.contains(normalizedQuery, ignoreCase = true)
            }
        }
    val favorites = visibleLanguages.filter { it.mlKitCode in favoriteCodes }
    val others = visibleLanguages.filterNot { it.mlKitCode in favoriteCodes }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .lumenFrame(LumenGold)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        LumenPanelHeader("AI TRANSLATION", LumenGold, onDismiss)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                activeSource?.let { "Translating: ${friendlySourceLabel(it.label)}, ${it.source}" }
                    ?: "Load a subtitle first: a download, a local file or a generated one.",
                color = if (activeSource != null) TextBright else LumenGold,
                fontSize = LumenType.body,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                "The translation is added under your subtitle, so both stay on screen. No speech model is needed.",
                color = TextMuted,
                fontSize = LumenType.caption,
                modifier = Modifier.padding(top = 4.dp),
            )

            // Embedded subtitles are saved after the first read; this clears that copy so the next
            // translation shows the "Reading subtitle" step again.
            if (onRereadEmbedded != null && activeSource?.source == "read from the movie" && !busy) {
                Spacer(Modifier.height(8.dp))
                LumenSmallButton("Re-read from movie", LumenSky) { onRereadEmbedded() }
            }

            when (status) {
                is SubtitleTranslationStatus.Translating -> {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                        Text(status.phase, color = TextBright, fontSize = LumenType.body, modifier = Modifier.weight(1f))
                        Text("${status.percent}%", color = LumenGold, fontSize = LumenType.value, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    LumenProgressBar(status.percent, LumenGold)
                    Spacer(Modifier.height(8.dp))
                    LumenActionButton("Stop translation", LumenRose) { onStop() }
                }

                is SubtitleTranslationStatus.Ready ->
                    Text(
                        "Translation ready. ${status.cueCount} lines.",
                        color = LumenMint,
                        fontSize = LumenType.body,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 10.dp),
                    )

                is SubtitleTranslationStatus.Failed ->
                    Text(
                        status.reason,
                        color = LumenRose,
                        fontSize = LumenType.body,
                        modifier = Modifier.padding(top = 10.dp),
                    )

                SubtitleTranslationStatus.Idle -> Unit
            }

            if (generatedFiles.isNotEmpty()) {
                LumenSectionLabel("Generated and translated", LumenSky)
                generatedFiles.forEach { file ->
                    val selected = activeSubtitleUri?.toString() == file.uri.toString()
                    LumenOptionRow(
                        label = if (file.cueCount >= 0) "${file.label} (${file.cueCount})" else file.label,
                        selected = selected,
                        accent = LumenSky,
                    ) { if (!busy) onLoadGenerated(file) }
                }
            }

            LumenSectionLabel("Translate to", LumenGold)
            OutlinedTextField(
                value = languageQuery,
                onValueChange = { languageQuery = it },
                enabled = !busy,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = LumenType.body),
                placeholder = { Text("Search language", fontSize = LumenType.body) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )

            if (visibleLanguages.isEmpty()) {
                Text(
                    "\u201c$normalizedQuery\u201d is not available for AI translation on this device.",
                    color = LumenGold,
                    fontSize = LumenType.body,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                if (favorites.isNotEmpty()) {
                    LumenSectionLabel("Favourites", LumenGold)
                    favorites.forEach { language ->
                        LanguageChoiceRow(
                            language = language,
                            favorite = true,
                            enabled = !busy && activeSource != null,
                            onTranslate = { onTranslate(language) },
                            onFavorite = {
                                favoriteCodes = toggleFavoriteLanguage(context, favoriteCodes, language.mlKitCode)
                            },
                        )
                    }
                }
                LumenSectionLabel("Languages", LumenSky)
                others.forEach { language ->
                    LanguageChoiceRow(
                        language = language,
                        favorite = false,
                        enabled = !busy && activeSource != null,
                        onTranslate = { onTranslate(language) },
                        onFavorite = {
                            favoriteCodes = toggleFavoriteLanguage(context, favoriteCodes, language.mlKitCode)
                        },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
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
        Box(modifier = Modifier.weight(1f).alpha(if (enabled) 1f else 0.4f)) {
            LumenOptionRow(language.label, false, if (favorite) LumenGold else LumenSky) { if (enabled) onTranslate() }
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .semantics { contentDescription = if (favorite) "Remove favourite" else "Add favourite" }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onFavorite),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (favorite) "★" else "☆", color = if (favorite) LumenGold else TextMuted, fontSize = LumenType.title)
        }
    }
}

private fun loadFavoriteLanguageCodes(context: Context): Set<String> =
    context
        .getSharedPreferences(LANGUAGE_PREFS, Context.MODE_PRIVATE)
        .getStringSet(FAVORITES_KEY, emptySet())
        ?.toSet()
        .orEmpty()

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

private val GENERATED_FILE_NAME =
    Regex("""-(translated|ai)-([A-Za-z_]+)-\d{10,}\.srt$""", RegexOption.IGNORE_CASE)

/**
 * Generated subtitle files carry the whole movie file name plus a timestamp; showing that raw
 * name wrapped over several lines. Show "AI translated · Hindi" instead.
 */
internal fun friendlySourceLabel(label: String): String {
    GENERATED_FILE_NAME.find(label)?.let { m ->
        val kind = if (m.groupValues[1].equals("ai", ignoreCase = true)) "Speech-to-subs" else "AI translated"
        val code = m.groupValues[2]
        val language = java.util.Locale.forLanguageTag(code).getDisplayLanguage(java.util.Locale.ENGLISH)
            .ifBlank { code.uppercase() }
            .replaceFirstChar { it.uppercase() }
        return "$kind · $language"
    }
    return if (label.length > 42) label.take(40) + "…" else label
}
