package com.sole.cinevault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.subtitles.SubtitleLanguageRegistry
import com.sole.cinevault.tvmode.TelevisionModeDetector
import com.sole.cinevault.tvmode.TvFocusableSlot
import com.sole.cinevault.ui.theme.*

data class TrackPopupRowData(val title: String, val subtitle: String, val onClick: () -> Unit)

internal fun friendlyLanguageName(code: String?): String = SubtitleLanguageRegistry.displayName(code)

internal fun cleanVideoTitle(path: String): String {
    var t = path.substringAfterLast("/").substringAfterLast("\\").substringBeforeLast(".")
        .replace(Regex("\\[.*?]"), " ").replace(Regex("\\(.*?\\)"), " ").replace(".", " ").replace("_", " ").replace("-", " ")
    t = t.replace(Regex("\\b(2160p|1080p|720p|480p|4k|uhd|hdr10\\+?|hdr|dv|dolby\\s*vision|dolby|vision|imax|remux|bluray|blu\\s*ray|brrip|hdrip|webrip|web\\s*dl|webdl|web|nf|amzn|dsnp|hulu|itunes|x264|x265|h264|h265|hevc|10bit|8bit|aac5?|aac|ddp5?\\.?1?|dd\\+|dts|truehd|atmos|5\\s*1|7\\s*1|yts|rarbg|tgx|eztv|pir8|ag|proper|repack|extended|theatrical|directors?\\s*cut|multi|dual|audio|english|hindi|ita|eng|mkv|mp4|avi|subs?|esub)\\b", RegexOption.IGNORE_CASE), " ")
    t = t.replace(Regex("\\bS\\d{1,2}E\\d{1,2}\\b", RegexOption.IGNORE_CASE), " ").replace(Regex("\\bseason\\s*\\d+\\b", RegexOption.IGNORE_CASE), " ").replace(Regex("\\bepisode\\s*\\d+\\b", RegexOption.IGNORE_CASE), " ").replace(Regex("\\b(19|20)\\d{2}\\b.*$", RegexOption.IGNORE_CASE), " ")
    return t.replace(Regex("\\s+"), " ").trim().ifBlank { "Now Playing" }
}

internal fun formatTime(ms: Long): String { val s = ms/1000; val h = s/3600; val m = (s%3600)/60; val sec = s%60; return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec) }

@Composable
fun FloatingTrackPopup(title: String, modifier: Modifier, rows: List<TrackPopupRowData>, audioSyncMs: Int = 0, onAudioSyncChange: (Int) -> Unit = {}, onAnyClick: () -> Unit = {}, onClose: () -> Unit) {
    // Phase 7 of TV support: same closure as the Speed/Sleep menus — this
    // popup is only reachable from the transport row's Audio button (phase
    // 4 made that focusable), but had no focus handling of its own once
    // open. This one has a genuine 2D layout (a vertical list of track
    // rows, then a horizontal pair of delay chips, then Close), so both
    // Up/Down AND Left/Right are handled here — FocusManager.moveFocus()
    // does real spatial reasoning based on actual layout position, so this
    // works correctly without the two directions needing separate logic.
    val context = LocalContext.current
    val isTelevision = remember { TelevisionModeDetector.isRunningOnTelevision(context) }
    val focusManager = LocalFocusManager.current
    val firstRowFocusRequester = remember { FocusRequester() }

    if (isTelevision) {
        LaunchedEffect(Unit) {
            firstRowFocusRequester.requestFocus()
        }
    }

    Column(
        modifier = modifier
            .glassPanel(cornerRadius = 16.dp, fill = SpaceMid.copy(alpha = 0.97f))
            .padding(6.dp)
            .then(
                if (isTelevision) {
                    Modifier.onKeyEvent { keyEvent ->
                        if (keyEvent.type != KeyEventType.KeyUp) return@onKeyEvent false
                        when (keyEvent.key) {
                            Key.DirectionUp -> {
                                focusManager.moveFocus(FocusDirection.Up)
                                true
                            }
                            Key.DirectionDown -> {
                                focusManager.moveFocus(FocusDirection.Down)
                                true
                            }
                            Key.DirectionLeft -> {
                                focusManager.moveFocus(FocusDirection.Left)
                                true
                            }
                            Key.DirectionRight -> {
                                focusManager.moveFocus(FocusDirection.Right)
                                true
                            }
                            else -> false
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        Text(text = title, color = AmberCore, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
        rows.forEachIndexed { index, row ->
            TvFocusableSlot(
                isTelevision = isTelevision,
                focusRequester = if (index == 0) firstRowFocusRequester else null,
                shape = RoundedCornerShape(10.dp),
                onActivate = { onAnyClick(); row.onClick() }
            ) {
                CompactGlassMenuRow(icon = Icons.Rounded.Audiotrack, label = row.title, onClick = { onAnyClick(); row.onClick() })
            }
            Spacer(modifier = Modifier.height(3.dp))
        }
        Text(text = "Audio Delay", color = AmberCore, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val onDecreaseSync = { onAnyClick(); onAudioSyncChange((audioSyncMs - 50).coerceAtLeast(-2000)) }
            TvFocusableSlot(
                isTelevision = isTelevision,
                focusRequester = if (rows.isEmpty()) firstRowFocusRequester else null,
                shape = RoundedCornerShape(10.dp),
                onActivate = onDecreaseSync
            ) {
                SyncStepChip(text = "−50", onClick = onDecreaseSync)
            }
            Text(
                text = if (audioSyncMs == 0) "0ms" else "${if (audioSyncMs > 0) "+" else ""}$audioSyncMs",
                color = if (audioSyncMs == 0) TextBright else AmberCore,
                fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            val onIncreaseSync = { onAnyClick(); onAudioSyncChange((audioSyncMs + 50).coerceAtMost(2000)) }
            TvFocusableSlot(isTelevision = isTelevision, shape = RoundedCornerShape(10.dp), onActivate = onIncreaseSync) {
                SyncStepChip(text = "+50", onClick = onIncreaseSync)
            }
        }
        Spacer(modifier = Modifier.height(5.dp))
        val onCloseClick = { onAnyClick(); onClose() }
        TvFocusableSlot(isTelevision = isTelevision, shape = RoundedCornerShape(10.dp), onActivate = onCloseClick) {
            CompactGlassMenuRow(icon = null, label = "Close", onClick = onCloseClick)
        }
    }
}

@Composable
private fun CompactGlassMenuRow(icon: ImageVector?, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(13.dp))
            Spacer(modifier = Modifier.width(7.dp))
        }
        Text(text = label, color = TextBright, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

@Composable
private fun SyncStepChip(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(GlassSurface)
            .border(1.dp, Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderBottom)), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = TextBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
