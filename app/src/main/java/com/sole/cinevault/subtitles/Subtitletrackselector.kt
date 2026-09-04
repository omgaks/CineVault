package com.sole.cinevault.subtitles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.*
import java.io.File

// ── Track Selector data model ──────────────────────────────────────────
// One flat, ordered representation of every subtitle choice available for
// the current video, grouped by SOURCE (Off / Embedded / Downloaded /
// Local) rather than by language — matches the spec: sources are the top
// division, language/format/SDH/forced are per-row detail underneath.
//
// `key` is the single source of truth for "is this row selected" — built
// the same way in VideoPlayerScreen.kt wherever a track gets applied, so
// selection state can't silently drift between the two files.
sealed class SubtitleTrackChoice(val key: String) {
    object Off : SubtitleTrackChoice("off")
    data class Embedded(
        val groupIndex: Int,
        val trackIndexInGroup: Int,
        val language: String,
        val isForced: Boolean,
        val isSdh: Boolean
    ) : SubtitleTrackChoice("embedded:$groupIndex:$trackIndexInGroup")
    data class Downloaded(val file: File, val language: String) : SubtitleTrackChoice("downloaded")
    data class Local(val file: File) : SubtitleTrackChoice("local:${file.absolutePath}")
    // AI-generated (Speech to subs) or AI-translated files. `isTranslated`
    // drives the row's source label — GeneratedSubtitleStore's own
    // fileName convention ("...-translated-<code>-<ts>.srt" vs
    // "...-ai-<lang>-<ts>.srt") is the one place that distinction already
    // exists, so it's read from there rather than re-derived some other
    // way that could drift out of sync with it.
    data class Generated(val file: GeneratedSubtitleFile, val isTranslated: Boolean) : SubtitleTrackChoice("generated:${file.fileName}")
}

@Composable
fun SubtitleTrackSelectorSheet(
    embeddedTracks: List<SubtitleTrackChoice.Embedded>,
    downloadedTrack: SubtitleTrackChoice.Downloaded?,
    localFiles: List<File>,
    generatedFiles: List<GeneratedSubtitleFile> = emptyList(),
    selectedKey: String?,
    popupWidth: Dp,
    popupMaxHeight: Dp,
    onSelect: (SubtitleTrackChoice) -> Unit,
    onDeleteLocal: (File) -> Unit,
    onDeleteGenerated: (GeneratedSubtitleFile) -> Unit = {},
    onOpenFilePicker: () -> Unit,
    onDismiss: () -> Unit
) {
    // Own, self-contained display toggle — not lifted to VideoPlayerScreen
    // since nothing outside this sheet needs to know whether delete icons
    // are currently showing. Off by default: a first-glance list of
    // sources shouldn't be cluttered with delete affordances for tracks
    // nobody asked to manage yet.
    var manageMode by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(popupWidth)
            .heightIn(max = popupMaxHeight)
            .glassPanel(cornerRadius = 20.dp, fill = SpaceMid.copy(alpha = 0.98f))
            .padding(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Subtitle Tracks", color = AmberCore, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            ManageToggleChip(active = manageMode, onClick = { manageMode = !manageMode })
            Spacer(modifier = Modifier.width(6.dp))
            IconCircleSmall(icon = Icons.Default.Close, onClick = onDismiss)
        }
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = GlassBorderBottom)
        Spacer(modifier = Modifier.height(6.dp))

        Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {

            TrackSectionLabel("Off")
            TrackRow(
                icon = Icons.Default.SubtitlesOff,
                title = "Subtitles Off",
                subtitle = null,
                badges = emptyList(),
                selected = selectedKey == SubtitleTrackChoice.Off.key,
                onClick = { onSelect(SubtitleTrackChoice.Off) }
            )

            if (embeddedTracks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                TrackSectionLabel("Embedded tracks")
                embeddedTracks.forEach { track ->
                    val badges = buildList {
                        if (track.isSdh) add("SDH")
                        if (track.isForced) add("Forced")
                    }
                    TrackRow(
                        icon = null,
                        title = friendlyLanguageDisplay(track.language),
                        subtitle = "Embedded",
                        badges = badges,
                        selected = selectedKey == track.key,
                        onClick = { onSelect(track) }
                    )
                }
            }

            if (downloadedTrack != null) {
                Spacer(modifier = Modifier.height(10.dp))
                TrackSectionLabel("Downloaded subtitles")
                TrackRow(
                    icon = null,
                    title = friendlyLanguageDisplay(downloadedTrack.language),
                    subtitle = "OpenSubtitles",
                    badges = emptyList(),
                    selected = selectedKey == downloadedTrack.key,
                    onClick = { onSelect(downloadedTrack) },
                    onDelete = if (manageMode) ({ onDeleteLocal(downloadedTrack.file) }) else null
                )
            }

            if (generatedFiles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                TrackSectionLabel("Generated & translated")
                generatedFiles.forEach { file ->
                    val isTranslated = file.fileName.contains("-translated-")
                    val choice = SubtitleTrackChoice.Generated(file, isTranslated)
                    TrackRow(
                        icon = if (isTranslated) Icons.Rounded.AutoAwesome else Icons.Rounded.Mic,
                        title = file.label,
                        subtitle = if (isTranslated) "AI translated" else "Speech-generated",
                        badges = emptyList(),
                        selected = selectedKey == choice.key,
                        onClick = { onSelect(choice) },
                        onDelete = if (manageMode) ({ onDeleteGenerated(file) }) else null
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            TrackSectionLabel("Local files")
            if (localFiles.isEmpty()) {
                Text(text = "No local subtitle files found nearby", color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
            } else {
                localFiles.forEach { file ->
                    val choice = SubtitleTrackChoice.Local(file)
                    TrackRow(
                        icon = null,
                        title = file.name,
                        subtitle = null,
                        badges = emptyList(),
                        selected = selectedKey == choice.key,
                        onClick = { onSelect(choice) },
                        onDelete = if (manageMode) ({ onDeleteLocal(file) }) else null
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            TrackRow(icon = null, title = "Open subtitle file…", subtitle = null, badges = emptyList(), selected = false, onClick = onOpenFilePicker)
        }
    }
}

@Composable
private fun ManageToggleChip(active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(50))
            .background(if (active) AmberCore.copy(alpha = 0.16f) else Color.Transparent)
            .border(1.dp, if (active) AmberCore else Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = Icons.Rounded.Delete, contentDescription = null, tint = if (active) AmberCore else TextMuted, modifier = Modifier.size(13.dp))
        Spacer(modifier = Modifier.width(5.dp))
        Text(text = "Manage", color = if (active) AmberCore else TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun TrackSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = Color(0xFFC9A765),
        fontSize = 9.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun TrackRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    title: String,
    subtitle: String?,
    badges: List<String>,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) AmberGlow.copy(alpha = 0.16f) else Color.Transparent)
            .then(
                if (selected) Modifier.border(1.dp, Brush.verticalGradient(listOf(AmberGlow.copy(alpha = 0.85f), AmberDeep.copy(alpha = 0.35f))), shape)
                else Modifier
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = if (selected) AmberCore else TextMuted, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(9.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title, color = if (selected) AmberCore else TextBright, fontSize = 12.5.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                )
                badges.forEach { badge ->
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = badge, color = AmberCore, fontSize = 8.5.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(AmberGlow.copy(alpha = 0.18f)).padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
            if (subtitle != null) {
                Text(text = subtitle, color = TextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (selected) {
            Icon(imageVector = Icons.Default.Check, contentDescription = "Selected", tint = AmberCore, modifier = Modifier.size(15.dp))
        }
        if (onDelete != null) {
            Spacer(modifier = Modifier.width(6.dp))
            // FIX: the clickable used to be directly on the 15dp icon
            // itself — well under Android's 48dp recommended minimum
            // touch target, and sitting right against the much larger
            // row-wide clickable for onClick (select this track). Easy
            // to miss and select the track instead of deleting it. Visual
            // icon size unchanged; only the tappable area is bigger now.
            Box(
                modifier = Modifier.size(40.dp).clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete, contentDescription = "Delete subtitle file", tint = TextMuted,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
private fun IconCircleSmall(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    // FIX: restyled as the same amber-filled pill used everywhere else
    // now (Studio close/back, lock button, Search's close) — was a plain
    // glass circle.
    Box(
        modifier = Modifier.height(34.dp).clip(RoundedCornerShape(50)).background(AmberCore).clickable { onClick() }.padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = "Close", tint = Color.Black, modifier = Modifier.size(15.dp))
    }
}

// Language-code -> display-name mapping shared with the rest of the
// subtitle system — deliberately duplicated (not imported) from
// VideoPlayerScreen.kt's private friendlyLanguageName, since that one is
// `private` to that file. Keeping this one small and local avoids exposing
// a wider surface just for this.
private fun friendlyLanguageDisplay(code: String?): String = SubtitleLanguageRegistry.displayName(code)
