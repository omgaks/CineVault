package com.sole.cinevault.subtitles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.*
import java.io.File
import kotlin.math.roundToInt

// ── Studio tabs ───────────────────────────────────────────────────────────
// FIX (UI redesign round): was TRACK/SYNC/STYLE/POSITION/ADVANCED (5 tabs,
// with Position and Style artificially split even though the approved
// mockup's own Style panel example shows Text Size and Vertical Offset
// together in one place) — now 5 tabs matching the 6-tile Tools Grid minus
// "Find" (which isn't a Studio tab at all; it closes Studio and opens the
// separate Search sheet instead, same as it always has). Position's
// controls now live inside Appearance; Dual Subtitles — previously buried
// at the bottom of the old catch-all Advanced tab — is its own tool now.
// MANAGE added this round — lists every language already downloaded for
// THIS video (via OpenSubtitlesClient.listCachedSubtitlesForVideo) with a
// delete action per language, addressing "no way to manage downloaded
// subtitles" directly instead of leaving cached files to just accumulate
// silently.

@Composable
internal fun StudioBehaviourTab(
    prefs: SubtitleBehaviorPrefs,
    onChange: (SubtitleBehaviorPrefs) -> Unit,
    cleaningOptions: SubtitleCleaningOptions,
    onCleaningOptionsChange: (SubtitleCleaningOptions) -> Unit
) {
    val languages = SubtitleLanguageRegistry.allLanguages()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .clip(RoundedCornerShape(16.dp))
            .background(GlassSurfaceStrong)
            .padding(2.dp)
    ) {
        StudioSectionLabel("Language priority", tight = true)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(horizontal = 8.dp)) {
            prefs.preferredLanguages.forEachIndexed { index, code ->
                val label = languages.firstOrNull { it.first == code }?.second ?: code.uppercase()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.03f)).padding(horizontal = 9.dp, vertical = 6.dp)
                ) {
                    Text(text = "${index + 1}", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(14.dp))
                    Text(text = label, color = TextBright, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    if (index > 0) {
                        Text(
                            text = "↑", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                val reordered = prefs.preferredLanguages.toMutableList()
                                reordered[index] = reordered[index - 1].also { reordered[index - 1] = reordered[index] }
                                onChange(prefs.copy(preferredLanguages = reordered))
                            }.padding(horizontal = 5.dp)
                        )
                    }
                    if (prefs.preferredLanguages.size > 1) {
                        Text(
                            text = "✕", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                onChange(prefs.copy(preferredLanguages = prefs.preferredLanguages.filterIndexed { i, _ -> i != index }))
                            }.padding(horizontal = 5.dp)
                        )
                    }
                }
            }
            val addable = languages.filter { (code, _) -> code !in prefs.preferredLanguages }
            if (addable.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    addable.forEach { (code, label) ->
                        Text(
                            text = "+ $label", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(50))
                                .clickable { onChange(prefs.copy(preferredLanguages = prefs.preferredLanguages + code)) }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        StudioDivider()
        StudioToggleRow(label = "Prefer forced subtitles", checked = prefs.preferForced) { onChange(prefs.copy(preferForced = it)) }
        StudioToggleRow(label = "Prefer SDH (hearing-impaired) subtitles", checked = prefs.preferSdh) { onChange(prefs.copy(preferSdh = it)) }

        StudioDivider()
        StudioSectionLabel("Automatic behavior", tight = true)
        StudioToggleRow(label = "Automatically enable embedded subtitles", checked = prefs.autoEnableEmbeddedSubtitles) { onChange(prefs.copy(autoEnableEmbeddedSubtitles = it)) }
        StudioToggleRow(label = "Automatically load matching local subtitle", checked = prefs.autoLoadMatchingLocalFile) { onChange(prefs.copy(autoLoadMatchingLocalFile = it)) }
        StudioToggleRow(label = "Automatically download when none exists", checked = prefs.autoDownloadWhenMissing) { onChange(prefs.copy(autoDownloadWhenMissing = it)) }
        StudioToggleRow(label = "Remember last selected language", checked = prefs.rememberLastSelectedLanguage) { onChange(prefs.copy(rememberLastSelectedLanguage = it)) }
        StudioToggleRow(label = "Disable subtitles when audio matches preferred language", checked = prefs.disableWhenAudioMatchesPreferred) { onChange(prefs.copy(disableWhenAudioMatchesPreferred = it)) }

        StudioDivider()
        StudioSectionLabel("Gestures", tight = true)
        StudioToggleRow(label = "Enable subtitle gestures (swipe/pinch/long-press)", checked = prefs.enableSubtitleGestures) { onChange(prefs.copy(enableSubtitleGestures = it)) }
        Text(
            text = "Off by default. When on, a zone above the player controls responds to: drag up/down for position, drag left/right for sync, pinch to resize, long-press to pause, double-tap to reset sync. Off elsewhere on screen — brightness, volume, and seek gestures are unaffected either way.",
            color = TextMuted, fontSize = 9.5.sp, lineHeight = 13.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
        Text(
            text = "Restricted-folder videos never auto-download subtitles, regardless of these settings — that protection is fixed, not optional.",
            color = TextFaint, fontSize = 9.sp, lineHeight = 13.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )

        StudioDivider()
        StudioSectionLabel("Subtitle cleaning", tight = true)
        Text(
            text = "Applies to downloaded and local .srt files only — embedded tracks can't be rewritten this way. SDH users who want the sound descriptions should leave the first toggle off.",
            color = TextMuted, fontSize = 9.5.sp, lineHeight = 13.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
        StudioToggleRow(label = "Hide hearing-impaired descriptions ([MUSIC], (door opens))", checked = cleaningOptions.hideHearingImpairedDescriptions) {
            onCleaningOptionsChange(cleaningOptions.copy(hideHearingImpairedDescriptions = it))
        }
        StudioToggleRow(label = "Remove speaker names (JOHN:)", checked = cleaningOptions.removeSpeakerNames) {
            onCleaningOptionsChange(cleaningOptions.copy(removeSpeakerNames = it))
        }
        StudioToggleRow(label = "Fix broken line breaks", checked = cleaningOptions.fixBrokenLineBreaks) {
            onCleaningOptionsChange(cleaningOptions.copy(fixBrokenLineBreaks = it))
        }
        StudioToggleRow(label = "Merge very short lines", checked = cleaningOptions.mergeVeryShortLines) {
            onCleaningOptionsChange(cleaningOptions.copy(mergeVeryShortLines = it))
        }
        StudioToggleRow(label = "Correct encoding symbols", checked = cleaningOptions.correctEncodingSymbols) {
            onCleaningOptionsChange(cleaningOptions.copy(correctEncodingSymbols = it))
        }
        StudioToggleRow(label = "Remove HTML tags", checked = cleaningOptions.removeHtmlTags) {
            onCleaningOptionsChange(cleaningOptions.copy(removeHtmlTags = it))
        }
        StudioToggleRow(label = "Convert ALL CAPS lines", checked = cleaningOptions.convertAllCaps) {
            onCleaningOptionsChange(cleaningOptions.copy(convertAllCaps = it))
        }
        StudioToggleRow(label = "Remove duplicate lines", checked = cleaningOptions.removeDuplicateLines) {
            onCleaningOptionsChange(cleaningOptions.copy(removeDuplicateLines = it))
        }
        if (cleaningOptions.isAnyEnabled) {
            Text(
                text = "Cleaning applies the next time a subtitle is (re)loaded — reopen Tracks and reselect if you don't see it yet.",
                color = AmberCore, fontSize = 9.5.sp, fontWeight = FontWeight.Medium, lineHeight = 13.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun StudioDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.06f))
    )
}

@Composable
internal fun StudioToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextBright, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Switch(
            checked = checked,
            onCheckedChange = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onCheckedChange(it) },
            colors = SwitchDefaults.colors(checkedThumbColor = AmberCore, checkedTrackColor = AmberGlow.copy(alpha = 0.4f)),
            modifier = Modifier.scale(0.8f)
        )
    }
}
