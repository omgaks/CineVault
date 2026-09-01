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
internal fun StudioTimingTab(
    currentSyncOffset: Float,
    onSyncOffsetChange: (Float) -> Unit,
    onDialogueSyncClick: () -> Unit,
    onDriftFixClick: () -> Unit,
    autoSyncStatus: AutoSyncStatus,
    autoSyncAvailable: Boolean,
    onAutoSyncClick: () -> Unit,
    onApplyAutoSync: (SubtitleSyncResult) -> Unit,
    onCancelAutoSync: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        StudioSectionLabel("Auto-Sync")
        Text(
            text = "Analyzes the actual audio's speech timing against the subtitle — offline, on-device, nothing leaves your phone.",
            color = TextMuted, fontSize = 11.sp, lineHeight = 15.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        AutoSyncSection(
            status = autoSyncStatus,
            available = autoSyncAvailable,
            onStart = onAutoSyncClick,
            onApply = onApplyAutoSync,
            onCancel = onCancelAutoSync
        )

        Spacer(modifier = Modifier.height(20.dp))
        StudioSectionLabel("Delay")
        Text(
            text = if (currentSyncOffset >= 0f) "+${"%.1f".format(currentSyncOffset)}s (later)" else "${"%.1f".format(currentSyncOffset)}s (earlier)",
            color = AmberCore, fontSize = 13.sp, fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = currentSyncOffset.coerceIn(-10f, 10f), onValueChange = onSyncOffsetChange, valueRange = -10f..10f,
            colors = SliderDefaults.colors(thumbColor = AmberCore, activeTrackColor = AmberGlow, inactiveTrackColor = Color.White.copy(alpha = 0.15f))
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(-5f, -1f, -0.1f, 0f, 0.1f, 1f, 5f).forEach { step ->
                Text(
                    text = if (step == 0f) "Reset" else if (step > 0) "+${step}s" else "${step}s",
                    color = TextBright, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(GlassSurface).clickable {
                        onSyncOffsetChange(if (step == 0f) 0f else (currentSyncOffset + step).coerceIn(-10f, 10f))
                    }.padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        StudioSectionLabel("Dialogue Sync")
        Text(text = "Pause on a visible line, tap Start, then tap the instant you hear it spoken.", color = TextMuted, fontSize = 11.sp, lineHeight = 15.sp)
        Spacer(modifier = Modifier.height(8.dp))
        StudioActionButton(label = "Start Dialogue Sync") { onDialogueSyncClick() }

        Spacer(modifier = Modifier.height(20.dp))
        StudioSectionLabel("Progressive Drift")
        Text(text = "Fixes subtitles that start in sync but drift later/earlier as the video plays.", color = TextMuted, fontSize = 11.sp, lineHeight = 15.sp)
        Spacer(modifier = Modifier.height(8.dp))
        StudioActionButton(label = "Fix Gradual Drift") { onDriftFixClick() }
    }
}

@Composable
private fun StudioActionButton(label: String, onClick: () -> Unit) {
    Text(
        text = label, color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(AmberCore).clickable { onClick() }.padding(horizontal = 16.dp, vertical = 9.dp)
    )
}

@Composable
private fun AutoSyncSection(
    status: AutoSyncStatus,
    available: Boolean,
    onStart: () -> Unit,
    onApply: (SubtitleSyncResult) -> Unit,
    onCancel: () -> Unit
) {
    when (status) {
        is AutoSyncStatus.Idle -> {
            if (!available) {
                Text(
                    text = "Not available right now — needs an .srt subtitle as the primary track, a readable local/downloaded video, and no network share. Try Dialogue Sync instead.",
                    color = TextMuted, fontSize = 10.5.sp, lineHeight = 14.sp
                )
            } else {
                StudioActionButton(label = "Start Auto-Sync") { onStart() }
            }
        }
        is AutoSyncStatus.Analyzing -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = AmberCore, strokeWidth = 2.dp, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = status.stage, color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        is AutoSyncStatus.Success -> AutoSyncResultCard(
            result = status.result, highConfidence = true, onApply = { onApply(status.result) }, onCancel = onCancel
        )
        is AutoSyncStatus.LowConfidence -> AutoSyncResultCard(
            result = status.result, highConfidence = false, onApply = { onApply(status.result) }, onCancel = onCancel
        )
        is AutoSyncStatus.Failed -> {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SpaceDeep.copy(alpha = 0.6f)).padding(10.dp)
            ) {
                Text(text = "Couldn't confidently sync this subtitle", color = TextBright, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = status.reason, color = TextMuted, fontSize = 10.5.sp, lineHeight = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioActionButton(label = "Try Again") { onStart() }
                }
            }
        }
    }
}

@Composable
private fun AutoSyncResultCard(
    result: SubtitleSyncResult,
    highConfidence: Boolean,
    onApply: () -> Unit,
    onCancel: () -> Unit
) {
    val offsetSeconds = result.initialOffsetMs / 1000f
    val accentColor = if (highConfidence) AmberCore else Color(0xFFFF9800)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SpaceDeep.copy(alpha = 0.7f))
            .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        // FIX: previously always labeled this "Offset:" and never showed
        // timeScale at all — a drift correction (timeScale != 1.0) would
        // apply correctly once tapped, but the person had no way to see
        // that BEFORE applying it, since the card looked identical to a
        // flat-offset result.
        val isDrift = result.timeScale != 1.0
        Text(
            text = if (highConfidence) {
                if (isDrift) "Auto-sync complete (drift correction)" else "Auto-sync complete"
            } else "Possible correction found",
            color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isDrift) "Starting offset: ${if (offsetSeconds >= 0f) "+" else ""}${"%.2f".format(offsetSeconds)}s"
                else "Offset: ${if (offsetSeconds >= 0f) "+" else ""}${"%.2f".format(offsetSeconds)}s",
            color = TextBright, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
        )
        if (isDrift) {
            val driftPercent = (result.timeScale - 1.0) * 100.0
            Text(
                text = "Drift: ${if (driftPercent >= 0) "+" else ""}${"%.2f".format(driftPercent)}% — timing gradually shifts across the film, not just a fixed amount",
                color = TextBright, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            text = "Confidence: ${(result.confidence * 100).toInt()}%",
            color = TextMuted, fontSize = 10.sp
        )
        if (!highConfidence) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Only a limited amount of matching dialogue was found — worth previewing before you commit, or try Dialogue Sync for a manually verified result.",
                color = TextMuted, fontSize = 10.sp, lineHeight = 14.sp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Apply", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(accentColor).clickable { onApply() }.padding(horizontal = 14.dp, vertical = 7.dp)
            )
            Text(
                text = "Cancel", color = TextBright, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(GlassSurface).clickable { onCancel() }.padding(horizontal = 14.dp, vertical = 7.dp)
            )
        }
    }
}
