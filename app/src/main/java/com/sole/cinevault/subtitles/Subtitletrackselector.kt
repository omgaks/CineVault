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
    onBack: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    // Tracks and Manage share the same visual shell but NOT the same
    // content anymore. Tracks selects/loads. Manage only shows files that
    // CineVault can actually delete or inspect.
    initialManageMode: Boolean = false
) {
    val manageMode = initialManageMode

    Column(
        modifier = Modifier
            .width(popupWidth)
            .heightIn(
                min = (popupMaxHeight * 0.72f).coerceAtMost(popupMaxHeight),
                max = popupMaxHeight
            )
            .glassPanel(cornerRadius = 20.dp, fill = SpaceMid.copy(alpha = 0.84f))
            .border(1.dp, AmberCore.copy(alpha = 0.20f), RoundedCornerShape(20.dp))
            .padding(13.dp)
    ) {
        // Two-row chrome prevents the title from collapsing vertically on
        // tablet/compact landscape when Back + Manage + Close all need space.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                Text(
                    text = "‹",
                    color = AmberCore,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(AmberCore.copy(alpha = 0.10f))
                        .clickable { onBack.invoke() }
                        .padding(horizontal = 8.dp, vertical = 1.dp)
                )
                Spacer(modifier = Modifier.width(7.dp))
            }

            Text(
                text = if (manageMode) "MANAGE SUBTITLES" else "SUBTITLE TRACKS",
                color = AmberCore,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(AmberCore.copy(alpha = 0.12f))
                    .border(1.dp, AmberCore.copy(alpha = 0.28f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )

            Spacer(modifier = Modifier.width(7.dp))
            IconCircleSmall(icon = Icons.Default.Close, onClick = onDismiss)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (manageMode) "STORED FILES" else "TRACK LIBRARY",
                color = TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (manageMode) {
                Text(
                    text = "DELETE MODE",
                    color = AmberCore,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(AmberCore.copy(alpha = 0.10f))
                        .border(1.dp, AmberCore.copy(alpha = 0.22f), RoundedCornerShape(50))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
        }

        val activeTrackLabel = when {
            selectedKey == SubtitleTrackChoice.Off.key -> "Subtitles off"
            downloadedTrack != null && selectedKey == downloadedTrack.key ->
                "${friendlyLanguageDisplay(downloadedTrack.language)} · OpenSubtitles"
            else -> localFiles.firstOrNull { selectedKey == "local:${it.absolutePath}" }
                    ?.let { "${it.nameWithoutExtension} · Local" }
                ?: generatedFiles.firstOrNull { selectedKey == "generated:${it.fileName}" }
                    ?.let { "${it.label} · Generated" }
                ?: embeddedTracks.firstOrNull { selectedKey == it.key }
                    ?.let { "${friendlyLanguageDisplay(it.language)} · Embedded" }
                ?: "No subtitle selected"
        }
        Text(
            text = "ACTIVE  ·  $activeTrackLabel",
            color = TextBright,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AmberCore.copy(alpha = 0.07f))
                .border(1.dp, AmberCore.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = GlassBorderBottom)
        Spacer(modifier = Modifier.height(6.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {

            if (!manageMode) {
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
                    embeddedTracks.forEach { item ->
                        val badges = buildList {
                            if (item.isSdh) add("SDH")
                            if (item.isForced) add("Forced")
                        }
                        TrackRow(
                            icon = null,
                            title = friendlyLanguageDisplay(item.language),
                            subtitle = "Embedded",
                            badges = badges,
                            selected = selectedKey == item.key,
                            onClick = { onSelect(item) }
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
                        onClick = { onSelect(downloadedTrack) }
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
                            onClick = { onSelect(choice) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                TrackSectionLabel("Local files")
                if (localFiles.isEmpty()) {
                    Text(
                        text = "No local subtitle files found nearby",
                        color = TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                } else {
                    localFiles.forEach { file ->
                        val choice = SubtitleTrackChoice.Local(file)
                        TrackRow(
                            icon = null,
                            title = file.name,
                            subtitle = null,
                            badges = emptyList(),
                            selected = selectedKey == choice.key,
                            onClick = { onSelect(choice) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                TrackRow(
                    icon = null,
                    title = "Open subtitle file…",
                    subtitle = null,
                    badges = emptyList(),
                    selected = false,
                    onClick = onOpenFilePicker
                )
            } else {
                // Manage deliberately excludes Off + embedded tracks because
                // neither corresponds to a CineVault-owned file that can be deleted.
                if (downloadedTrack != null) {
                    TrackSectionLabel("Downloaded")
                    TrackRow(
                        icon = null,
                        title = friendlyLanguageDisplay(downloadedTrack.language),
                        subtitle = "OpenSubtitles file",
                        badges = emptyList(),
                        selected = selectedKey == downloadedTrack.key,
                        onClick = {},
                        onDelete = { onDeleteLocal(downloadedTrack.file) }
                    )
                }

                if (generatedFiles.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    TrackSectionLabel("AI / generated")
                    generatedFiles.forEach { file ->
                        val isTranslated = file.fileName.contains("-translated-")
                        TrackRow(
                            icon = if (isTranslated) Icons.Rounded.AutoAwesome else Icons.Rounded.Mic,
                            title = file.label,
                            subtitle = if (isTranslated) "AI translated" else "Speech-generated",
                            badges = emptyList(),
                            selected = selectedKey == "generated:${file.fileName}",
                            onClick = {},
                            onDelete = { onDeleteGenerated(file) }
                        )
                    }
                }

                if (localFiles.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    TrackSectionLabel("Local files")
                    localFiles.forEach { file ->
                        TrackRow(
                            icon = null,
                            title = file.name,
                            subtitle = "Local subtitle",
                            badges = emptyList(),
                            selected = selectedKey == "local:${file.absolutePath}",
                            onClick = {},
                            onDelete = { onDeleteLocal(file) }
                        )
                    }
                }

                if (downloadedTrack == null && generatedFiles.isEmpty() && localFiles.isEmpty()) {
                    Text(
                        text = "No subtitle files available to manage.",
                        color = TextMuted,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = Color(0xFFC9A765),
        fontSize = 10.5.sp,
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
                Text(text = subtitle, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
