package com.sole.cinevault.subtitles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sole.cinevault.LumenActionButton
import com.sole.cinevault.LumenCardShape
import com.sole.cinevault.LumenChip
import com.sole.cinevault.LumenGold
import com.sole.cinevault.LumenMint
import com.sole.cinevault.LumenOptionRow
import com.sole.cinevault.LumenRose
import com.sole.cinevault.LumenSectionLabel
import com.sole.cinevault.LumenSky
import com.sole.cinevault.LumenStepper
import com.sole.cinevault.LumenSwitch
import com.sole.cinevault.LumenType
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextMuted
import java.io.File

/** Everything the Tracks tab needs, passed in so the Deck owns no player state. */
data class DeckTracksModel(
    val embedded: List<SubtitleTrackChoice.Embedded>,
    val downloaded: List<SubtitleTrackChoice.Downloaded>,
    val local: List<File>,
    val generated: List<GeneratedSubtitleFile>,
    val selectedKey: String?,
    val onSelect: (SubtitleTrackChoice) -> Unit,
    val onDeleteFile: (File) -> Unit,
    val onDeleteGenerated: (GeneratedSubtitleFile) -> Unit,
    val onOpenFilePicker: () -> Unit,
)

/** Dual subtitles, as the Deck needs it. */
data class DeckDualModel(
    val enabled: Boolean,
    val canEnable: Boolean,
    val primaryLabel: String,
    val secondaryLanguage: String,
    val secondaryLanguageLabel: String,
    val sourceLabel: String,
    val availableLanguages: List<Pair<String, String>>,
    val gapLines: Int,
    val colorHex: String,
    val statusText: String,
    val onEnabledChange: (Boolean) -> Unit,
    val onLanguageChange: (String) -> Unit,
    val onGapChange: (Int) -> Unit,
    val onColorChange: (String) -> Unit,
)

private val dualColours = listOf("#00E5FF", "#FFD740", "#FFFFFF", "#69F0AE", "#FF80AB", "#B388FF")

private fun providerLabel(file: File): String? {
    val parts = file.name.split('.')
    return when (parts.getOrNull(parts.size - 2)?.lowercase()) {
        "subdl" -> "SubDL"
        "opensubtitles" -> "OpenSubtitles"
        else -> null
    }
}

@Composable
internal fun DeckTracksContent(model: DeckTracksModel, dual: DeckDualModel?) {
    var manage by remember { mutableStateOf(false) }
    val off = model.selectedKey == SubtitleTrackChoice.Off.key

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            if (off) "Subtitles off" else "Subtitles on",
            color = if (off) TextMuted else LumenGold,
            fontSize = LumenType.value,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        LumenSwitch(
            checked = !off,
            onCheckedChange = { on -> model.onSelect(if (on) SubtitleTrackChoice.On else SubtitleTrackChoice.Off) },
            accent = LumenGold,
        )
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LumenChip("Tracks", !manage, LumenGold) { manage = false }
        LumenChip("Manage files", manage, LumenRose) { manage = true }
    }

    if (!manage) {
        if (model.embedded.isNotEmpty()) {
            LumenSectionLabel("Embedded", LumenGold)
            model.embedded.forEach { item ->
                val tags = buildList {
                    if (item.isSdh) add("SDH")
                    if (item.isForced) add("Forced")
                }.joinToString(" · ").ifBlank { null }
                LumenOptionRow(SubtitleLanguageRegistry.displayName(item.language), model.selectedKey == item.key, LumenGold, tags) { model.onSelect(item) }
            }
        }
        if (model.downloaded.isNotEmpty()) {
            LumenSectionLabel("Downloaded", LumenMint)
            model.downloaded.forEach { item ->
                LumenOptionRow(SubtitleLanguageRegistry.displayName(item.language), model.selectedKey == item.key, LumenMint, providerLabel(item.file)) { model.onSelect(item) }
            }
        }
        if (model.generated.isNotEmpty()) {
            LumenSectionLabel("Generated and translated", LumenSky)
            model.generated.forEach { file ->
                val translated = file.fileName.contains("-translated-")
                val choice = SubtitleTrackChoice.Generated(file, translated)
                LumenOptionRow(file.label, model.selectedKey == choice.key, LumenSky, if (translated) "AI translated" else "Speech") { model.onSelect(choice) }
            }
        }
        LumenSectionLabel("Local files", LumenGold)
        if (model.local.isEmpty()) {
            Text("No subtitle files found next to the video.", color = TextMuted, fontSize = LumenType.caption)
        } else {
            model.local.forEach { file ->
                val choice = SubtitleTrackChoice.Local(file)
                LumenOptionRow(file.name, model.selectedKey == choice.key, LumenGold, null) { model.onSelect(choice) }
            }
        }
        Spacer(Modifier.height(8.dp))
        LumenActionButton("Open subtitle file", LumenGold) { model.onOpenFilePicker() }
    } else {
        val nothing = model.downloaded.isEmpty() && model.generated.isEmpty() && model.local.isEmpty()
        if (nothing) {
            Text("No subtitle files to manage.", color = TextMuted, fontSize = LumenType.body, modifier = Modifier.padding(top = 10.dp))
        }
        if (model.downloaded.isNotEmpty()) {
            LumenSectionLabel("Downloaded", LumenMint)
            model.downloaded.forEach { item ->
                DeleteRow(SubtitleLanguageRegistry.displayName(item.language), providerLabel(item.file)) { model.onDeleteFile(item.file) }
            }
        }
        if (model.generated.isNotEmpty()) {
            LumenSectionLabel("Generated", LumenSky)
            model.generated.forEach { file -> DeleteRow(file.label, "AI or speech") { model.onDeleteGenerated(file) } }
        }
        if (model.local.isNotEmpty()) {
            LumenSectionLabel("Local files", LumenGold)
            model.local.forEach { file -> DeleteRow(file.name, "Local") { model.onDeleteFile(file) } }
        }
        if (!nothing) {
            Text("Deleting asks you to confirm and can be undone.", color = TextMuted, fontSize = LumenType.caption, modifier = Modifier.padding(top = 8.dp))
        }
    }

    if (dual != null) DeckDualSection(dual)
}

@Composable
private fun DeleteRow(title: String, subtitle: String?, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .background(Color.White.copy(alpha = 0.05f), LumenCardShape)
            .padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextBright, fontSize = LumenType.body, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = TextMuted, fontSize = LumenType.caption, maxLines = 1)
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDelete() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Delete, contentDescription = "Delete $title", tint = LumenRose, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun DeckDualSection(dual: DeckDualModel) {
    var picking by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    LumenSectionLabel("Dual subtitles", LumenSky)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("Show two languages", color = TextBright, fontSize = LumenType.body, modifier = Modifier.weight(1f))
        LumenSwitch(dual.enabled, { if (dual.canEnable) dual.onEnabledChange(it) }, LumenSky)
    }
    if (!dual.canEnable) {
        Text(
            "Needs a downloaded, local or generated subtitle as the main one. Embedded tracks can't be merged.",
            color = TextMuted,
            fontSize = LumenType.caption,
            modifier = Modifier.padding(top = 4.dp),
        )
        return
    }
    Spacer(Modifier.height(6.dp))
    Row(modifier = Modifier.fillMaxWidth()) {
        Text("Main", color = TextMuted, fontSize = LumenType.caption, modifier = Modifier.width(64.dp))
        Text(dual.primaryLabel, color = TextBright, fontSize = LumenType.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.height(4.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(LumenSky.copy(alpha = 0.10f), LumenCardShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { picking = !picking }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Second", color = TextMuted, fontSize = LumenType.caption, modifier = Modifier.width(54.dp))
        Text(dual.secondaryLanguageLabel, color = LumenSky, fontSize = LumenType.body, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (dual.sourceLabel.isNotBlank()) Text(dual.sourceLabel.uppercase(), color = LumenSky, fontSize = LumenType.caption, fontWeight = FontWeight.Bold)
    }
    if (picking) {
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Search language", fontSize = LumenType.body) },
            modifier = Modifier.fillMaxWidth(),
        )
        val visible = dual.availableLanguages.filter { (_, label) -> query.isBlank() || label.contains(query, ignoreCase = true) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 190.dp)
                .padding(top = 4.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (visible.isEmpty()) {
                Text("No matching language", color = TextMuted, fontSize = LumenType.caption, modifier = Modifier.padding(8.dp))
            }
            visible.forEach { (code, label) ->
                LumenOptionRow(label, code == dual.secondaryLanguage, LumenSky, null) {
                    dual.onLanguageChange(code)
                    picking = false
                    query = ""
                }
            }
        }
    }
    LumenStepper(
        label = "Gap lines",
        valueText = dual.gapLines.toString(),
        unit = "",
        accent = LumenSky,
        onStep = { n -> dual.onGapChange((dual.gapLines + if (n < 0) -1 else 1).coerceIn(0, 2)) },
    )
    LumenSectionLabel("Second line colour", LumenSky)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        dualColours.forEach { hex ->
            val selected = hex.equals(dual.colorHex, ignoreCase = true)
            val c = Color(android.graphics.Color.parseColor(hex))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(c, LumenCardShape)
                    .border(if (selected) 2.5.dp else 1.dp, if (selected) Color.White else Color.White.copy(alpha = 0.3f), LumenCardShape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { dual.onColorChange(hex) }
            )
        }
    }
    if (dual.statusText.isNotBlank()) {
        Text(dual.statusText, color = TextMuted, fontSize = LumenType.caption, modifier = Modifier.padding(top = 8.dp))
    }
}
