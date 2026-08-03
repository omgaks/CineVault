package com.sole.cinevault.subtitles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
            .amberGlow(radius = 18.dp, alpha = 0.55f)
            .glassPanel(cornerRadius = 50.dp, fill = GlassSurfaceStrong)
            .padding(horizontal = 14.dp, vertical = if (isLandscape) 8.dp else 10.dp)
    ) {
        Text(text = "Listening… tap when you hear the line", color = TextBright, fontSize = if (isLandscape) 11.sp else 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.width(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(AmberCore).clickable { onTap() }.padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Icon(imageVector = Icons.Default.TouchApp, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(5.dp))
            Text(text = "Tap Now", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Default.Close, contentDescription = "Cancel", tint = TextMuted,
            modifier = Modifier.size(16.dp).clickable { onCancel() }
        )
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
            .glassPanel(cornerRadius = 20.dp, fill = SpaceMid.copy(alpha = 0.98f))
            .padding(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Default.TrendingUp, contentDescription = null, tint = AmberCore, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "Fix Gradual Drift", color = AmberCore, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextBright,
                modifier = Modifier.size(16.dp).clip(CircleShape).background(GlassSurface).padding(2.dp).clickable { onDismiss() }
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Play to a spot early in the video, dial in perfect sync, mark it. Do the same later in the video where it's drifted. CineVault fixes the slope between them.",
            color = TextMuted, fontSize = 10.sp, lineHeight = 13.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

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

        Spacer(modifier = Modifier.height(12.dp))
        val canApply = pointA != null && pointB != null && pointA.positionMs != pointB.positionMs
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(if (canApply) AmberCore else GlassSurface)
                .clickable(enabled = canApply) { onApply() }
                .padding(vertical = 9.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = Icons.Default.Adjust, contentDescription = null, tint = if (canApply) Color.Black else TextMuted, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "Apply Drift Fix", color = if (canApply) Color.Black else TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Black)
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
            .clip(RoundedCornerShape(12.dp))
            .background(SpaceDeep.copy(alpha = 0.6f))
            .border(1.dp, Brush.verticalGradient(listOf(AmberGlow.copy(alpha = if (positionMs != null) 0.6f else 0.2f), AmberDeep.copy(alpha = 0.2f))), RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Text(text = label, color = Color(0xFFC9A765), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (positionMs != null) "Marked at ${formatSyncTime(positionMs)}" else "Not marked yet — currently at ${formatSyncTime(currentPositionMs)}",
            color = if (positionMs != null) AmberCore else TextMuted, fontSize = 10.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = correctionText,
                onValueChange = onCorrectionChange,
                singleLine = true,
                label = { Text("Correction (sec)", fontSize = 8.5.sp) },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = TextBright),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f).height(52.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AmberCore.copy(alpha = 0.6f), unfocusedBorderColor = AmberCore.copy(alpha = 0.25f))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Mark Here", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(AmberGlow).clickable { onMark() }.padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }
    }
}

private fun formatSyncTime(ms: Long): String {
    val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}
