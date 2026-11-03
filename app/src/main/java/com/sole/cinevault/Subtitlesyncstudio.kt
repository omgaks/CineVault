package com.sole.cinevault.subtitles

import com.sole.cinevault.lumenFrame
import com.sole.cinevault.LumenActionButton
import com.sole.cinevault.LumenCardShape
import com.sole.cinevault.LumenGold
import com.sole.cinevault.LumenMint
import com.sole.cinevault.LumenPanelHeader
import com.sole.cinevault.LumenSky
import com.sole.cinevault.LumenSmallButton
import com.sole.cinevault.LumenType

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.*

// ── Dialogue Tap Sync ────────────────────────────────────────────────────
// A floating pill shown while "armed": the person paused on a subtitle line
// they can see, tapped "Start", playback resumed automatically, and now
// they tap this pill the instant they HEAR the matching line. The math
// (currentPosition - referencePosition) happens in VideoPlayerScreen.kt,
// which owns the player; this is purely presentational.
@Composable
fun DialogueTapSyncBar(isLandscape: Boolean, onTap: () -> Unit, onCancel: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .widthIn(max = 560.dp)
            .lumenFrame(LumenSky)
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = "LISTENING",
                color = LumenSky,
                fontSize = LumenType.section,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Text(
                text = if (isLandscape) "Hear the matching line" else "Tap when you hear the matching line",
                color = TextBright,
                fontSize = LumenType.body,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        LumenSmallButton("Tap now", LumenSky, filled = true) { onTap() }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable { onCancel() },
            contentAlignment = Alignment.Center,
        ) {
            Text("×", color = TextMuted, fontSize = 22.sp)
        }
    }
}

// ── Progressive Drift Correction ─────────────────────────────────────────
// Two reference points (a known-good moment near the start, a known-drift
// moment later), each with a correction in seconds. From these CineVault
// derives a linear scale+shift transform applied across the whole subtitle
// file, rather than one flat offset — fixes subtitles that start in sync
// but drift increasingly late/early as the video plays (a different FPS
// between the subtitle and the video, most commonly).
data class DriftPoint(val positionMs: Long, val correctionSeconds: Float)

@Composable
fun DriftCorrectionSheet(
    videoDurationMs: Long,
    currentPositionMs: Long,
    pointA: DriftPoint?,
    pointB: DriftPoint?,
    popupWidth: Dp,
    popupMaxHeight: Dp,
    onMarkPointA: (Float) -> Unit,
    onMarkPointB: (Float) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    var draftCorrectionA by remember { mutableStateOf(pointA?.correctionSeconds?.toString() ?: "0") }
    var draftCorrectionB by remember { mutableStateOf(pointB?.correctionSeconds?.toString() ?: "0") }

    Column(
        modifier = Modifier
            .width(popupWidth)
            .heightIn(max = popupMaxHeight)
            .lumenFrame(LumenGold)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        LumenPanelHeader("FIX GRADUAL DRIFT", LumenGold, onDismiss)
        Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            Text(
                text = "Play to a spot early in the film, set the delay until it is perfect, and mark it. Do the same later where it has drifted. CineVault fixes the slope between the two.",
                color = TextMuted, fontSize = LumenType.caption,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )

            DriftPointRow(
                label = "Point A (early)",
                positionMs = pointA?.positionMs,
                correctionText = draftCorrectionA,
                onCorrectionChange = { draftCorrectionA = it },
                onMark = { onMarkPointA(draftCorrectionA.toFloatOrNull() ?: 0f) },
                currentPositionMs = currentPositionMs
            )
            Spacer(modifier = Modifier.height(8.dp))
            DriftPointRow(
                label = "Point B (later)",
                positionMs = pointB?.positionMs,
                correctionText = draftCorrectionB,
                onCorrectionChange = { draftCorrectionB = it },
                onMark = { onMarkPointB(draftCorrectionB.toFloatOrNull() ?: 0f) },
                currentPositionMs = currentPositionMs
            )

            Spacer(modifier = Modifier.height(10.dp))
            val canApply = pointA != null && pointB != null && pointA.positionMs != pointB.positionMs
            LumenActionButton("Apply drift fix", LumenGold, enabled = canApply) { onApply() }
            if (!canApply) {
                Text(
                    "Mark two different spots to apply.",
                    color = TextMuted, fontSize = LumenType.caption,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun DriftPointRow(
    label: String,
    positionMs: Long?,
    correctionText: String,
    onCorrectionChange: (String) -> Unit,
    onMark: () -> Unit,
    currentPositionMs: Long
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.05f), LumenCardShape)
            .border(if (positionMs != null) 1.6.dp else 1.dp, if (positionMs != null) LumenGold else Color.White.copy(alpha = 0.22f), LumenCardShape)
            .padding(10.dp)
    ) {
        Text(text = label, color = LumenGold, fontSize = LumenType.body, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (positionMs != null) "Marked at ${formatSyncTime(positionMs)}" else "Not marked yet. Now at ${formatSyncTime(currentPositionMs)}",
            color = if (positionMs != null) LumenMint else TextMuted, fontSize = LumenType.caption
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = correctionText,
                onValueChange = onCorrectionChange,
                singleLine = true,
                label = { Text("Correction (s)", fontSize = LumenType.caption) },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = LumenType.body, color = TextBright),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LumenGold.copy(alpha = 0.8f), unfocusedBorderColor = LumenGold.copy(alpha = 0.3f))
            )
            Spacer(modifier = Modifier.width(8.dp))
            LumenSmallButton("Mark here", LumenGold, filled = true) { onMark() }
        }
    }
}

private fun formatSyncTime(ms: Long): String {
    val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}
