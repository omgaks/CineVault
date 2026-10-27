package com.sole.cinevault.subtitles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.ui.CaptionStyleCompat
import com.sole.cinevault.LumenCardShape
import com.sole.cinevault.LumenChip
import com.sole.cinevault.LumenGold
import com.sole.cinevault.LumenMint
import com.sole.cinevault.LumenRose
import com.sole.cinevault.LumenSectionLabel
import com.sole.cinevault.LumenSky
import com.sole.cinevault.LumenSwitch
import com.sole.cinevault.LumenType
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextMuted

/** Everything the Deck's Style tab needs to change how the subtitle looks (colours, edge, background). */
class DeckLooksModel(
    val presetName: String,
    val appearance: SubtitleAppearance,
    val isAssOrSsa: Boolean,
    val preserveOriginalStyling: Boolean,
    val onApplyPreset: (String, SubtitleAppearance) -> Unit,
    val onForeground: (Int) -> Unit,
    val onEdgeType: (Int) -> Unit,
    val onEdgeColor: (Int) -> Unit,
    val onBackground: (Int) -> Unit,
    val onPreserveChange: (Boolean) -> Unit,
)

/** Everything the Deck's Settings tab needs for behaviour and subtitle cleaning. */
class DeckBehaviourModel(
    val prefs: SubtitleBehaviorPrefs,
    val onPrefsChange: (SubtitleBehaviorPrefs) -> Unit,
    val cleaning: SubtitleCleaningOptions,
    val onCleaningChange: (SubtitleCleaningOptions) -> Unit,
)

/** Live sample of the subtitle on a dark strip, so every colour tap shows its result at once. */
@Composable
internal fun DeckLooksPreview(looks: DeckLooksModel, fontSizeSp: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(Color(0xFF0B0F14), LumenCardShape)
            .border(1.dp, Color.White.copy(alpha = 0.12f), LumenCardShape),
        contentAlignment = Alignment.Center,
    ) {
        StyledPreviewText(appearance = looks.appearance, fontSizeSp = fontSizeSp)
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DeckLooksContent(looks: DeckLooksModel, fontSizeSp: Float) {
    LumenSectionLabel("Look", LumenGold)
    DeckLooksPreview(looks, fontSizeSp)
    Spacer(Modifier.height(8.dp))

    if (looks.isAssOrSsa) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text("Keep original styling", color = TextBright, fontSize = LumenType.body, fontWeight = FontWeight.Bold)
                Text(
                    "Use this file's own fonts, colours and positions.",
                    color = TextMuted,
                    fontSize = LumenType.caption,
                )
            }
            LumenSwitch(looks.preserveOriginalStyling, looks.onPreserveChange, LumenGold)
        }
        if (looks.preserveOriginalStyling) {
            Text(
                "The colours below are ignored while original styling is on.",
                color = LumenGold,
                fontSize = LumenType.caption,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
    }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SubtitlePresets.all.forEach { (name, preset) ->
            LumenChip(name, looks.presetName == name, LumenGold) { looks.onApplyPreset(name, preset) }
        }
    }

    LumenSectionLabel("Text colour", LumenMint)
    DeckSwatches(textColorSwatches, looks.appearance.foregroundColor, looks.onForeground)

    LumenSectionLabel("Outline", LumenSky)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            "None" to CaptionStyleCompat.EDGE_TYPE_NONE,
            "Outline" to CaptionStyleCompat.EDGE_TYPE_OUTLINE,
            "Shadow" to CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
        ).forEach { (label, type) ->
            LumenChip(label, looks.appearance.edgeType == type, LumenSky) { looks.onEdgeType(type) }
        }
    }
    Spacer(Modifier.height(8.dp))
    DeckSwatches(edgeColorSwatches, looks.appearance.edgeColor, looks.onEdgeColor)

    LumenSectionLabel("Background", LumenRose)
    DeckSwatches(backgroundSwatches, looks.appearance.backgroundColor, looks.onBackground)
}

/** Round-cornered colour squares, 40 dp touch size, with the chosen one outlined in white. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeckSwatches(swatches: List<Pair<String, Int>>, selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        swatches.forEach { (name, argb) ->
            val on = argb == selected
            val color = Color(argb)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFF1B222A), LumenCardShape)
                        .border(if (on) 2.5.dp else 1.dp, if (on) Color.White else Color.White.copy(alpha = 0.3f), LumenCardShape)
                        .padding(5.dp)
                        .background(color, LumenCardShape)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(argb) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (color.alpha < 0.05f) Text("∅", color = TextMuted, fontSize = LumenType.body)
                }
                Text(name, color = if (on) TextBright else TextMuted, fontSize = LumenType.caption, maxLines = 1, modifier = Modifier.width(52.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DeckBehaviourContent(model: DeckBehaviourModel) {
    val prefs = model.prefs
    val languages = SubtitleLanguageRegistry.allLanguages()

    LumenSectionLabel("Language order", LumenGold)
    prefs.preferredLanguages.forEachIndexed { index, code ->
        val label = languages.firstOrNull { it.first == code }?.second ?: code.uppercase()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).background(Color.White.copy(alpha = 0.05f), LumenCardShape).padding(horizontal = 10.dp),
        ) {
            Text("${index + 1}", color = TextMuted, fontSize = LumenType.caption, modifier = Modifier.width(18.dp))
            Text(label, color = TextBright, fontSize = LumenType.body, modifier = Modifier.weight(1f))
            if (index > 0) DeckMiniButton("↑", "Move up") {
                val l = prefs.preferredLanguages.toMutableList()
                l[index] = l[index - 1].also { l[index - 1] = l[index] }
                model.onPrefsChange(prefs.copy(preferredLanguages = l))
            }
            if (prefs.preferredLanguages.size > 1) DeckMiniButton("✕", "Remove") {
                model.onPrefsChange(prefs.copy(preferredLanguages = prefs.preferredLanguages.filterIndexed { i, _ -> i != index }))
            }
        }
    }
    val addable = languages.filter { (code, _) -> code !in prefs.preferredLanguages }
    if (addable.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            addable.forEach { (code, label) ->
                LumenChip("+ $label", false, LumenGold) { model.onPrefsChange(prefs.copy(preferredLanguages = prefs.preferredLanguages + code)) }
            }
        }
    }

    LumenSectionLabel("Picking a track", LumenMint)
    DeckToggle("Prefer forced subtitles", prefs.preferForced) { model.onPrefsChange(prefs.copy(preferForced = it)) }
    DeckToggle("Prefer hearing-impaired (SDH)", prefs.preferSdh) { model.onPrefsChange(prefs.copy(preferSdh = it)) }
    DeckToggle("Use embedded subtitles automatically", prefs.autoEnableEmbeddedSubtitles) { model.onPrefsChange(prefs.copy(autoEnableEmbeddedSubtitles = it)) }
    DeckToggle("Use a matching local file", prefs.autoLoadMatchingLocalFile) { model.onPrefsChange(prefs.copy(autoLoadMatchingLocalFile = it)) }
    DeckToggle("Download when none exists", prefs.autoDownloadWhenMissing) { model.onPrefsChange(prefs.copy(autoDownloadWhenMissing = it)) }
    DeckToggle("Remember last language", prefs.rememberLastSelectedLanguage) { model.onPrefsChange(prefs.copy(rememberLastSelectedLanguage = it)) }
    DeckToggle("Off when the audio is my language", prefs.disableWhenAudioMatchesPreferred) { model.onPrefsChange(prefs.copy(disableWhenAudioMatchesPreferred = it)) }
    Text(
        "Videos in a restricted folder never download subtitles. That is fixed.",
        color = TextMuted,
        fontSize = LumenType.caption,
        modifier = Modifier.padding(top = 4.dp),
    )

    LumenSectionLabel("Gestures", LumenSky)
    DeckToggle("Subtitle gestures", prefs.enableSubtitleGestures) { model.onPrefsChange(prefs.copy(enableSubtitleGestures = it)) }
    Text(
        "Drag up or down for position, left or right for sync, pinch for size, long-press for the Deck. Off by default.",
        color = TextMuted,
        fontSize = LumenType.caption,
        modifier = Modifier.padding(top = 2.dp),
    )

    LumenSectionLabel("Glasses", LumenSky)
    DeckToggle("Subtitles travel with the picture", prefs.glassesSubtitleContentLocked) { model.onPrefsChange(prefs.copy(glassesSubtitleContentLocked = it)) }

    val c = model.cleaning
    LumenSectionLabel("Cleaning", LumenRose)
    Text(
        "Only for downloaded and local .srt files. Applies the next time a subtitle loads.",
        color = TextMuted,
        fontSize = LumenType.caption,
    )
    DeckToggle("Hide [MUSIC] and (door opens)", c.hideHearingImpairedDescriptions) { model.onCleaningChange(c.copy(hideHearingImpairedDescriptions = it)) }
    DeckToggle("Remove speaker names", c.removeSpeakerNames) { model.onCleaningChange(c.copy(removeSpeakerNames = it)) }
    DeckToggle("Fix broken line breaks", c.fixBrokenLineBreaks) { model.onCleaningChange(c.copy(fixBrokenLineBreaks = it)) }
    DeckToggle("Merge very short lines", c.mergeVeryShortLines) { model.onCleaningChange(c.copy(mergeVeryShortLines = it)) }
    DeckToggle("Correct encoding symbols", c.correctEncodingSymbols) { model.onCleaningChange(c.copy(correctEncodingSymbols = it)) }
    DeckToggle("Remove HTML tags", c.removeHtmlTags) { model.onCleaningChange(c.copy(removeHtmlTags = it)) }
    DeckToggle("Convert ALL CAPS lines", c.convertAllCaps) { model.onCleaningChange(c.copy(convertAllCaps = it)) }
    DeckToggle("Remove duplicate lines", c.removeDuplicateLines) { model.onCleaningChange(c.copy(removeDuplicateLines = it)) }
    if (c.isAnyEnabled) {
        Text("Reselect the track to see cleaning take effect.", color = LumenGold, fontSize = LumenType.caption, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun DeckToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
    ) {
        Text(label, color = TextBright, fontSize = LumenType.body, modifier = Modifier.weight(1f).padding(end = 8.dp))
        LumenSwitch(checked, onChange, LumenGold)
    }
}

@Composable
private fun DeckMiniButton(symbol: String, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, color = TextMuted, fontSize = LumenType.value, fontWeight = FontWeight.Bold)
    }
}
