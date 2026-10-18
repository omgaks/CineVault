package com.sole.cinevault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FourK
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Hd
import androidx.compose.material.icons.rounded.HdrOn
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class PlayerSpecChip(val text: String, val icon: ImageVector, val accent: Color)

private val ChipSky = Color(0xFF6FC3FF)
private val ChipGold = Color(0xFFFFC94D)
private val ChipViolet = Color(0xFFC792FF)

/**
 * Picture and sound facts for the top row, from what the player actually found in
 * the stream (falling back to the file name). Only chips we are sure about are shown.
 */
internal fun playerSpecChips(snapshot: PlaybackDiagnosticsSnapshot, fileName: String): List<PlayerSpecChip> {
    val lowerName = fileName.lowercase()
    val chips = mutableListOf<PlayerSpecChip>()

    val res = snapshot.resolution.takeIf { it != "Unknown" }
        ?: mediaBadgesFromName(fileName).firstOrNull { it == "4K" || it == "1080p" || it == "720p" }
    when (res) {
        "8K" -> chips += PlayerSpecChip("8K UHD", Icons.Rounded.FourK, ChipSky)
        "4K" -> chips += PlayerSpecChip("4K UHD", Icons.Rounded.FourK, ChipSky)
        "1440p", "1080p", "720p" -> chips += PlayerSpecChip(res, Icons.Rounded.Hd, ChipSky)
    }

    val dolbyVision = lowerName.contains("dolby vision") || lowerName.contains("dovi") ||
        Regex("(^|[^a-z0-9])dv([^a-z0-9]|$)").containsMatchIn(lowerName)
    when {
        dolbyVision -> chips += PlayerSpecChip("DOLBY VISION", Icons.Rounded.HdrOn, ChipSky)
        snapshot.dynamicRange == VideoDynamicRange.HDR10_OR_PQ ->
            chips += PlayerSpecChip("HDR10", Icons.Rounded.HdrOn, ChipSky)
        snapshot.dynamicRange == VideoDynamicRange.HLG ->
            chips += PlayerSpecChip("HLG", Icons.Rounded.HdrOn, ChipSky)
        lowerName.contains("hdr") -> chips += PlayerSpecChip("HDR", Icons.Rounded.HdrOn, ChipSky)
    }

    val audioMime = snapshot.audioMimeType?.lowercase().orEmpty()
    when {
        audioMime.contains("eac3-joc") || lowerName.contains("atmos") ->
            chips += PlayerSpecChip("ATMOS", Icons.Rounded.SurroundSound, ChipViolet)
        audioMime.contains("truehd") -> chips += PlayerSpecChip("TRUEHD", Icons.Rounded.SurroundSound, ChipViolet)
        audioMime.contains("dts") -> chips += PlayerSpecChip("DTS", Icons.Rounded.GraphicEq, ChipGold)
        audioMime.contains("eac3") -> chips += PlayerSpecChip("DD+", Icons.Rounded.SurroundSound, ChipViolet)
        audioMime.contains("ac3") -> chips += PlayerSpecChip("DOLBY DIGITAL", Icons.Rounded.SurroundSound, ChipViolet)
    }
    return chips
}

/** Small cut-corner-feel chip sized to its text and icon. */
@Composable
internal fun PlayerSpecChipView(chip: PlayerSpecChip, compact: Boolean) {
    val shape = RoundedCornerShape(if (compact) 7.dp else 9.dp)
    Row(
        Modifier
            .height(if (compact) 22.dp else 26.dp)
            .clip(shape)
            .background(Color(0xCC0A0C12))
            .background(chip.accent.copy(alpha = 0.16f))
            .border(1.dp, chip.accent.copy(alpha = 0.75f), shape)
            .padding(start = 5.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(chip.icon, contentDescription = null, tint = chip.accent, modifier = Modifier.size(if (compact) 15.dp else 18.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            chip.text,
            color = Color(0xFFF5F3EE),
            fontSize = if (compact) 9.sp else 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            maxLines = 1,
        )
    }
}
