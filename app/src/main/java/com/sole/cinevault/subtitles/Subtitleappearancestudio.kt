package com.sole.cinevault.subtitles

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.ui.CaptionStyleCompat
import com.sole.cinevault.ui.theme.*

// ── Appearance presets ───────────────────────────────────────────────────
// CaptionStyleCompat only supports ONE edge treatment at a time (outline OR
// shadow OR raised/depressed, never combined), so "thin outline + soft
// shadow" from the original spec is approximated here as outline alone —
// that reads better than a shadow at the small sizes subtitles actually
// render at. Documented rather than silently simplified.
data class SubtitleAppearance(
    val foregroundColor: Int,
    val edgeType: Int,
    val edgeColor: Int,
    val backgroundColor: Int
)

object SubtitlePresets {
    val CineVault = SubtitleAppearance(0xFFFFF3D6.toInt(), CaptionStyleCompat.EDGE_TYPE_OUTLINE, AndroidColor.BLACK, AndroidColor.TRANSPARENT)
    val Netflix = SubtitleAppearance(AndroidColor.WHITE, CaptionStyleCompat.EDGE_TYPE_OUTLINE, AndroidColor.BLACK, 0x99000000.toInt())
    val Cinema = SubtitleAppearance(AndroidColor.WHITE, CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, AndroidColor.BLACK, AndroidColor.TRANSPARENT)
    val HighContrast = SubtitleAppearance(AndroidColor.YELLOW, CaptionStyleCompat.EDGE_TYPE_NONE, AndroidColor.BLACK, 0xFF000000.toInt())
    val Minimal = SubtitleAppearance(AndroidColor.WHITE, CaptionStyleCompat.EDGE_TYPE_NONE, AndroidColor.BLACK, AndroidColor.TRANSPARENT)
    val ClassicYellow = SubtitleAppearance(AndroidColor.YELLOW, CaptionStyleCompat.EDGE_TYPE_OUTLINE, AndroidColor.BLACK, AndroidColor.TRANSPARENT)

    val all: List<Pair<String, SubtitleAppearance>> = listOf(
        "CineVault" to CineVault, "Netflix Style" to Netflix, "Cinema" to Cinema,
        "High Contrast" to HighContrast, "Minimal" to Minimal, "Classic Yellow" to ClassicYellow
    )
}

private val textColorSwatches = listOf(
    "Warm White" to 0xFFFFF3D6.toInt(), "White" to AndroidColor.WHITE, "Yellow" to AndroidColor.YELLOW,
    "Cyan" to AndroidColor.CYAN, "Amber" to 0xFFFFC857.toInt(), "Light Gray" to 0xFFD9D9D9.toInt()
)
private val edgeColorSwatches = listOf("Black" to AndroidColor.BLACK, "Dark Gray" to 0xFF333333.toInt(), "None" to AndroidColor.TRANSPARENT)
private val backgroundSwatches = listOf(
    "None" to AndroidColor.TRANSPARENT, "Soft Black" to 0x99000000.toInt(), "Solid Black" to 0xFF000000.toInt()
)

@Composable
fun SubtitleAppearanceStudioSheet(
    presetName: String,
    appearance: SubtitleAppearance,
    fontSizeSp: Float,
    popupWidth: Dp,
    popupMaxHeight: Dp,
    onApplyPreset: (String, SubtitleAppearance) -> Unit,
    onForegroundChange: (Int) -> Unit,
    onEdgeTypeChange: (Int) -> Unit,
    onEdgeColorChange: (Int) -> Unit,
    onBackgroundChange: (Int) -> Unit,
    isAssOrSsaFormat: Boolean = false,
    preserveOriginalStyling: Boolean = false,
    onPreserveOriginalStylingChange: (Boolean) -> Unit = {},
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(popupWidth)
            .heightIn(max = popupMaxHeight)
            .glassPanel(cornerRadius = 20.dp, fill = SpaceMid.copy(alpha = 0.98f))
            .padding(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Default.FormatColorText, contentDescription = null, tint = AmberCore, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "Subtitle Style", color = AmberCore, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(
                // FIX: restyled as the same amber-filled pill used
                // everywhere else now — was a plain glass circle.
                imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.Black,
                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(50)).background(AmberCore).padding(6.dp).clickable { onDismiss() }
            )
        }
        Spacer(modifier = Modifier.height(10.dp))

        // Live preview — sample line styled with the exact same
        // ForegroundColor/edge/background combo about to be applied, so
        // changes are visible immediately without leaving this sheet.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1A1A1A), Color(0xFF0A0A0A)))),
            contentAlignment = Alignment.Center
        ) {
            StyledPreviewText(appearance = appearance, fontSizeSp = fontSizeSp)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            if (isAssOrSsaFormat) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Preserve original ASS/SSA styling", color = TextBright, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        Text(text = "Uses this subtitle's own fonts/colors/positioning instead of CineVault's", color = TextMuted, fontSize = 9.sp, lineHeight = 12.sp)
                    }
                    androidx.compose.material3.Switch(
                        checked = preserveOriginalStyling, onCheckedChange = onPreserveOriginalStylingChange,
                        colors = androidx.compose.material3.SwitchDefaults.colors(checkedThumbColor = AmberCore, checkedTrackColor = AmberGlow.copy(alpha = 0.4f))
                    )
                }
                if (preserveOriginalStyling) {
                    Text(
                        text = "The controls below are ignored while this is on — this subtitle renders with its own embedded styling.",
                        color = AmberCore, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, lineHeight = 13.sp,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }
            }

            SectionLabel2("Presets")
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SubtitlePresets.all.forEach { (name, preset) ->
                    PresetChip(label = name, selected = presetName == name, onClick = { onApplyPreset(name, preset) })
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            SectionLabel2("Text Color")
            SwatchRow(swatches = textColorSwatches, selectedColor = appearance.foregroundColor, onSelect = onForegroundChange)

            Spacer(modifier = Modifier.height(10.dp))
            SectionLabel2("Outline / Shadow")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                EdgeTypeChip("None", CaptionStyleCompat.EDGE_TYPE_NONE, appearance.edgeType, onEdgeTypeChange)
                EdgeTypeChip("Outline", CaptionStyleCompat.EDGE_TYPE_OUTLINE, appearance.edgeType, onEdgeTypeChange)
                EdgeTypeChip("Shadow", CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, appearance.edgeType, onEdgeTypeChange)
            }
            Spacer(modifier = Modifier.height(6.dp))
            SwatchRow(swatches = edgeColorSwatches, selectedColor = appearance.edgeColor, onSelect = onEdgeColorChange)

            Spacer(modifier = Modifier.height(10.dp))
            SectionLabel2("Background Box")
            SwatchRow(swatches = backgroundSwatches, selectedColor = appearance.backgroundColor, onSelect = onBackgroundChange)
        }
    }
}

@Composable
private fun StyledPreviewText(appearance: SubtitleAppearance, fontSizeSp: Float) {
    val textColor = Color(appearance.foregroundColor)
    val bgColor = Color(appearance.backgroundColor)
    Box(
        modifier = Modifier
            .background(if (bgColor.alpha > 0.01f) bgColor else Color.Transparent, RoundedCornerShape(3.dp))
            .padding(horizontal = if (bgColor.alpha > 0.01f) 8.dp else 0.dp, vertical = if (bgColor.alpha > 0.01f) 2.dp else 0.dp)
    ) {
        // Outline/shadow approximated with layered Text draws, matching
        // roughly how CaptionStyleCompat's own edge rendering looks —
        // exact native rendering is left to SubtitleView on the real player.
        if (appearance.edgeType == CaptionStyleCompat.EDGE_TYPE_OUTLINE && appearance.edgeColor != AndroidColor.TRANSPARENT) {
            val edgeColor = Color(appearance.edgeColor)
            listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1).forEach { (dx, dy) ->
                Text(
                    text = "Sample Subtitle Line",
                    color = edgeColor, fontSize = (fontSizeSp * 0.75f).sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.offset(x = dx.dp, y = dy.dp)
                )
            }
        } else if (appearance.edgeType == CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW && appearance.edgeColor != AndroidColor.TRANSPARENT) {
            Text(
                text = "Sample Subtitle Line",
                color = Color(appearance.edgeColor).copy(alpha = 0.7f), fontSize = (fontSizeSp * 0.75f).sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.offset(x = 1.5.dp, y = 1.5.dp)
            )
        }
        Text(text = "Sample Subtitle Line", color = textColor, fontSize = (fontSizeSp * 0.75f).sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SectionLabel2(text: String) {
    Text(text = text.uppercase(), color = Color(0xFFC9A765), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, modifier = Modifier.padding(bottom = 5.dp))
}

@Composable
private fun PresetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Text(
        text = label,
        color = if (selected) Color.Black else TextBright,
        fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) AmberCore else SpaceDeep.copy(alpha = 0.7f))
            .border(1.dp, if (selected) AmberCore else AmberCore.copy(alpha = 0.3f), RoundedCornerShape(50))
            .clickable { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }
            .padding(horizontal = 11.dp, vertical = 6.dp)
    )
}

@Composable
private fun SwatchRow(swatches: List<Pair<String, Int>>, selectedColor: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        swatches.forEach { (label, colorInt) ->
            val isSelected = colorInt == selectedColor
            val composeColor = Color(colorInt)
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (composeColor.alpha > 0.01f) composeColor else Color.DarkGray)
                    .border(if (isSelected) 2.dp else 1.dp, if (isSelected) AmberCore else Color.White.copy(alpha = 0.3f), CircleShape)
                    .clickable { onSelect(colorInt) },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = label, tint = if (composeColor.luminance() > 0.5f) Color.Black else Color.White, modifier = Modifier.size(13.dp))
                }
            }
        }
    }
}

private fun Color.luminance(): Float = (0.299f * red + 0.587f * green + 0.114f * blue)

@Composable
private fun EdgeTypeChip(label: String, type: Int, currentType: Int, onSelect: (Int) -> Unit) {
    val selected = type == currentType
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) AmberGlow.copy(alpha = 0.18f) else SpaceDeep.copy(alpha = 0.6f))
            .border(1.dp, if (selected) AmberCore.copy(alpha = 0.7f) else AmberCore.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
            .clickable { onSelect(type) }
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        if (label == "Shadow") Icon(imageVector = Icons.Default.BlurOn, contentDescription = null, tint = if (selected) AmberCore else TextMuted, modifier = Modifier.size(12.dp))
        if (label == "Shadow") Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, color = if (selected) AmberCore else TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
