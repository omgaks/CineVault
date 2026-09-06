package com.sole.cinevault.subtitles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.GlassSurfaceStrong
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextFaint
import com.sole.cinevault.ui.theme.TextMuted

enum class StudioCategory { DOWNLOAD, STYLE, POWER_TOOLS, SETTINGS }

private fun StudioCategory.label() = when (this) {
    StudioCategory.DOWNLOAD -> "Download"
    StudioCategory.STYLE -> "Style"
    StudioCategory.POWER_TOOLS -> "Power tools"
    StudioCategory.SETTINGS -> "Settings"
}

/**
 * Long-press CC destination — one draggable pill with four categories.
 * Tapping a category is a separate concern (handled by the caller); this
 * composable only draws the pill and reports which one was tapped.
 */
@Composable
fun SubtitleStudioPill(
    activeCategory: StudioCategory?,
    onCategorySelected: (StudioCategory) -> Unit,
    containerSize: IntSize,
    initialOffset: Offset,
    modifier: Modifier = Modifier
) {
    DraggableStudioWindow(
        initialOffset = initialOffset,
        containerSize = containerSize,
        modifier = modifier
    ) { dragHandleModifier ->
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(GlassSurfaceStrong)
                .then(dragHandleModifier)
                .padding(6.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
        ) {
            StudioCategory.entries.forEach { category ->
                val active = category == activeCategory
                Text(
                    text = category.label(),
                    color = if (active) AmberCore else TextMuted,
                    fontWeight = FontWeight.Medium,
                    fontSize = 9.5.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .then(if (active) Modifier.background(AmberCore.copy(alpha = 0.16f)) else Modifier)
                        .clickable { onCategorySelected(category) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

data class StudioListItem(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    // For toggle-style rows like Auto Download — null means a plain
    // navigating row, non-null means this row IS the toggle (amber-filled
    // when on, per the approved design) and tapping it flips the value
    // rather than opening anything else.
    val toggledOn: Boolean? = null
)

/**
 * The generic list window both Download and Power Tools use — same chrome,
 * different items, so this exists once instead of twice. Back arrow falls
 * back to the 4-category pill — this composable just calls `onBack`, the
 * caller wires that to reopening the pill.
 */
@Composable
fun StudioListWindow(
    title: String,
    items: List<StudioListItem>,
    onBack: () -> Unit,
    containerSize: IntSize,
    initialOffset: Offset,
    modifier: Modifier = Modifier
) {
    DraggableStudioWindow(
        initialOffset = initialOffset,
        containerSize = containerSize,
        modifier = modifier
    ) { dragHandleModifier ->
        Column(
            modifier = Modifier
                .widthIn(min = 210.dp, max = 260.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(GlassSurfaceStrong)
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().then(dragHandleModifier),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.ChevronLeft,
                    contentDescription = "Back to Studio",
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp).clickable { onBack() }
                )
                Text(
                    text = title,
                    color = TextBright,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            items.forEach { item ->
                val isOnToggle = item.toggledOn == true
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (isOnToggle) AmberCore.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.03f))
                        .clickable { item.onClick() }
                        .padding(horizontal = 7.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        item.icon,
                        contentDescription = null,
                        tint = if (isOnToggle) AmberCore else TextMuted,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.label,
                        color = if (isOnToggle) AmberCore else TextBright,
                        fontWeight = FontWeight.Medium,
                        fontSize = 9.5.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (item.toggledOn != null) {
                        Text(
                            text = if (item.toggledOn) "ON" else "OFF",
                            color = if (isOnToggle) Color(0xFF1A1206) else TextFaint,
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (isOnToggle) AmberCore else Color.White.copy(alpha = 0.08f))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
            }
        }
    }
}

/**
 * Dedicated Dual Subs window — built on the existing DualSubtitleState /
 * fetchAndApplyDualSecondary() plumbing (already real, already working
 * from the old DUAL tab), just given its own destination instead of
 * living inside the old 8-tile grid. Gap lines is real, existing
 * functionality from that same old tab — kept here rather than silently
 * dropped just because it wasn't in the approved mockup's sketch.
 */
@Composable
fun DualSubsWindow(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    canEnable: Boolean,
    primaryLabel: String,
    secondaryLanguage: String,
    secondaryLanguageLabel: String,
    onSecondaryLanguageChange: (String) -> Unit,
    availableLanguages: List<Pair<String, String>>,
    gapLines: Int,
    onGapLinesChange: (Int) -> Unit,
    statusText: String,
    onBack: () -> Unit,
    containerSize: IntSize,
    initialOffset: Offset,
    onUserInteraction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showLanguagePicker by remember { mutableStateOf(false) }

    DraggableStudioWindow(
        initialOffset = initialOffset,
        containerSize = containerSize,
        modifier = modifier
    ) { dragHandleModifier ->
        Column(
            modifier = Modifier
                .widthIn(min = 230.dp, max = 270.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(GlassSurfaceStrong)
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().then(dragHandleModifier),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.ChevronLeft,
                    contentDescription = "Back to Studio",
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp).clickable { onBack() }
                )
                Icon(Icons.Rounded.Language, contentDescription = null, tint = AmberCore, modifier = Modifier.size(15.dp).padding(start = 2.dp))
                Text(
                    text = "Dual subtitles",
                    color = TextBright,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f).padding(start = 6.dp)
                )
                ToggleSwitch(checked = enabled, onCheckedChange = { if (canEnable) onEnabledChange(it) })
            }

            if (!canEnable) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "Select a primary subtitle first.", color = TextFaint, fontSize = 8.5.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.03f))
                    .padding(horizontal = 9.dp, vertical = 7.dp)
            ) {
                Text(text = "Primary", color = TextMuted, fontSize = 8.5.sp)
                Text(text = primaryLabel, color = TextBright, fontSize = 10.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            }
            Spacer(modifier = Modifier.height(5.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.03f))
                    .clickable { showLanguagePicker = !showLanguagePicker; onUserInteraction() }
                    .padding(horizontal = 9.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Secondary", color = TextMuted, fontSize = 8.5.sp)
                Text(
                    text = "AI \u00b7 $secondaryLanguageLabel",
                    color = AmberCore,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End
                )
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = TextFaint, modifier = Modifier.size(13.dp))
            }

            if (showLanguagePicker) {
                Spacer(modifier = Modifier.height(4.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.3f))
                        .padding(4.dp)
                ) {
                    availableLanguages.forEach { (code, label) ->
                        Text(
                            text = label,
                            color = if (code == secondaryLanguage) AmberCore else TextBright,
                            fontSize = 9.5.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSecondaryLanguageChange(code); showLanguagePicker = false }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Gap lines", color = TextMuted, fontSize = 8.5.sp, modifier = Modifier.weight(1f))
                Text(
                    text = "\u2212",
                    color = TextBright,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.06f))
                        .clickable { if (gapLines > 0) onGapLinesChange(gapLines - 1) }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
                Text(text = "$gapLines", color = AmberCore, fontSize = 9.5.sp, modifier = Modifier.padding(horizontal = 8.dp))
                Text(
                    text = "+",
                    color = TextBright,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.06f))
                        .clickable { onGapLinesChange(gapLines + 1) }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }

            if (statusText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = statusText, color = TextFaint, fontSize = 8.sp)
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Picked up automatically once enabled.", color = TextFaint, fontSize = 8.sp)
        }
    }
}

@Composable
private fun ToggleSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .width(30.dp)
            .height(17.dp)
            .clip(RoundedCornerShape(50))
            .background(if (checked) AmberCore else Color.White.copy(alpha = 0.1f))
            .clickable { onCheckedChange(!checked) }
    ) {
        Box(
            modifier = Modifier
                .padding(2.dp)
                .size(13.dp)
                .clip(CircleShape)
                .background(if (checked) Color(0xFF1A1206) else TextMuted)
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
        )
    }
}

// Icons for Download/Power Tools/Style/Settings rows, gathered here so
// VideoPlayerScreen doesn't need to pick Material icon names directly.
object StudioRowIcons {
    val Manage: ImageVector = Icons.Rounded.Storage
    val Tracks: ImageVector = Icons.Rounded.List
    val Web: ImageVector = Icons.Rounded.Public
    val SmartSearch: ImageVector = Icons.Rounded.AutoAwesome
    val AutoDownload: ImageVector = Icons.Rounded.SwapHoriz
    val SpeechToSubs: ImageVector = Icons.Rounded.Mic
    val AiTranslate: ImageVector = Icons.Rounded.AutoAwesome
    val AutoSync: ImageVector = Icons.Rounded.SwapHoriz
    val DialogueSync: ImageVector = Icons.Rounded.SwapHoriz
    val DriftSync: ImageVector = Icons.Rounded.SwapHoriz
    val DualSubs: ImageVector = Icons.Rounded.Language
    val Style: ImageVector = Icons.Rounded.Palette
    val SettingsIcon: ImageVector = Icons.Rounded.Settings
}

/**
 * Standalone Settings destination for the long-press Sub Studio.
 *
 * This intentionally reuses the already-working StudioBehaviourTab content,
 * but removes SubtitleStudioSheet / the legacy 8-tile grid from the navigation
 * path.  A later visual pass can freely restyle the internals without ever
 * needing to bring that grid back.
 */
@Composable
fun SubtitleBehaviourWindow(
    prefs: SubtitleBehaviorPrefs,
    onChange: (SubtitleBehaviorPrefs) -> Unit,
    cleaningOptions: SubtitleCleaningOptions,
    onCleaningOptionsChange: (SubtitleCleaningOptions) -> Unit,
    onBack: () -> Unit,
    containerSize: IntSize,
    initialOffset: Offset,
    onUserInteraction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val containerHeightDp = with(density) { containerSize.height.toDp() }
    val maxWindowHeight = (containerHeightDp - 40.dp).coerceAtLeast(220.dp)

    DraggableStudioWindow(
        initialOffset = initialOffset,
        containerSize = containerSize,
        modifier = modifier
    ) { dragHandleModifier ->
        Column(
            modifier = Modifier
                .widthIn(min = 280.dp, max = 330.dp)
                .heightIn(max = maxWindowHeight)
                .clip(RoundedCornerShape(18.dp))
                .background(GlassSurfaceStrong)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        onUserInteraction()
                    }
                }
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().then(dragHandleModifier),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.ChevronLeft,
                    contentDescription = "Back to Studio",
                    tint = AmberCore,
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(50))
                        .background(AmberCore.copy(alpha = 0.12f))
                        .clickable { onBack() }
                        .padding(2.dp)
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = "Settings",
                    color = AmberCore,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(AmberCore.copy(alpha = 0.12f))
                        .border(1.dp, AmberCore.copy(alpha = 0.28f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(7.dp))
            Box(modifier = Modifier.weight(1f, fill = false).fillMaxWidth()) {
                StudioBehaviourTab(
                    prefs = prefs,
                    onChange = onChange,
                    cleaningOptions = cleaningOptions,
                    onCleaningOptionsChange = onCleaningOptionsChange
                )
            }
        }
    }
}
