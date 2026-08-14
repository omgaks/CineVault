package com.sole.cinevault

import com.sole.cinevault.library.*
import com.sole.cinevault.smb.*
import com.sole.cinevault.glasses.rememberExternalDisplayState as rememberGlassesDisplayState
import com.sole.cinevault.glasses.rememberExternalVideoPresentation as rememberGlassesVideoPresentation

// All subtitle-system files (search, import, sync, appearance, dual-merge,
// providers) moved to their own package on this pass. Single wildcard
// import used deliberately instead of ~45 explicit ones, since the
// cross-reference check confirmed this file is the ONLY outside caller
// into that package.
import com.sole.cinevault.subtitles.*
import com.sole.cinevault.segments.*

import androidx.compose.ui.graphics.Brush
import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon as AndroidIcon
import android.media.AudioManager
import android.util.TypedValue
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.view.WindowManager
import android.widget.Toast
import android.graphics.Color as AndroidColor
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.sole.cinevault.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Extracted from VideoPlayerScreen.kt without behavioural changes.
// This file owns reusable player controls, menus, overlays, HUDs and formatters.

@Composable
internal fun TopIconCluster(
    isLandscape: Boolean,
    iconSize: Dp,
    playbackSpeed: Float,
    sleepTimerActive: Boolean,
    showSpeedMenu: Boolean,
    showSleepMenu: Boolean,
    onSpeedClick: () -> Unit,
    onSleepClick: () -> Unit,
    onPipClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val content: @Composable () -> Unit = {
        AmberPillIcon(icon = Icons.Rounded.Tv, contentDescription = "Picture in picture", onClick = onPipClick)
        AmberPillIcon(icon = Icons.Rounded.Timer, contentDescription = "Sleep timer", activeDot = sleepTimerActive || showSleepMenu, onClick = onSleepClick)
        LabeledGlowIcon(icon = Icons.Rounded.Speed, label = "Speed", size = iconSize, tint = if (playbackSpeed != 1f || showSpeedMenu) AmberCore else TextBright, active = playbackSpeed != 1f || showSpeedMenu, onClick = onSpeedClick)
    }
    if (isLandscape) {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { content() }
    } else {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

// FEATURE: matches the lock/unlock button's exact styling (solid amber
// pill, black icon, no text label) rather than the glass-surface +
// label look LabeledGlowIcon uses — requested specifically for PiP and
// Sleep Timer, to bring them visually in line with Lock. activeDot is a
// small optional indicator (used for Sleep Timer) since a solid-fill
// pill has no obvious way to show an "active" state via icon swap the
// way Lock does (Lock/LockOpen) — Timer doesn't have a natural active
// variant, so a subtle dot preserves that information without breaking
// the requested visual consistency.
@Composable
private fun AmberPillIcon(icon: ImageVector, contentDescription: String, activeDot: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(46.dp)
            .clip(RoundedCornerShape(50))
            .background(AmberCore)
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = Color.Black, modifier = Modifier.size(22.dp))
        if (activeDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp)
                    .size(7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFE53935))
            )
        }
    }
}

// Icon + text label, with a persistent amber-glass glow border (same
// visual language as the always-visible lock button, just a border/glow
// treatment rather than a solid fill so active/inactive state — e.g.
// playback speed != 1x, sleep timer running — still reads clearly through
// tint and glow intensity). Labels sit below the icon so PiP/Sleep/Speed
// don't need to be guessed from silhouette alone.
@Composable
private fun LabeledGlowIcon(icon: ImageVector, label: String, size: Dp, tint: Color = TextBright, active: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(20.dp))
                .background(GlassSurface)
                .background(Brush.radialGradient(listOf(AmberGlow.copy(alpha = if (active) 0.38f else 0.20f), Color.Transparent)))
                .border(
                    width = 1.2.dp,
                    brush = Brush.verticalGradient(listOf(AmberGlow.copy(alpha = if (active) 0.90f else 0.55f), AmberDeep.copy(alpha = if (active) 0.55f else 0.25f))),
                    shape = RoundedCornerShape(20.dp)
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(size * 0.44f))
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(text = label, color = if (active) AmberCore else TextMuted, fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun GlassMenuRow(icon: ImageVector?, label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier.fillMaxWidth().clip(shape)
            .background(if (selected) AmberGlow.copy(alpha = 0.16f) else Color.Transparent)
            .then(
                if (selected) Modifier.border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(listOf(AmberGlow.copy(alpha = 0.85f), AmberDeep.copy(alpha = 0.35f))),
                    shape = shape
                ) else Modifier
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = if (selected) AmberCore else TextMuted, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(9.dp))
        }
        Text(
            text = label,
            color = if (selected) AmberCore else TextBright,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

// Extensions the local-subtitle browser/matcher recognizes — matches
// SubtitleFormatSupport.kt's SRT/VTT/ASS/SSA/TTML set (VobSub .idx and
// MicroDVD .sub deliberately excluded since CineVault can't decode either;
// see that file for why).
private val SUPPORTED_LOCAL_SUBTITLE_EXTENSIONS = setOf("srt", "vtt", "ass", "ssa", "ttml")

internal fun findNearbySrtFiles(videoPath: String): List<java.io.File> {
    val results = LinkedHashSet<java.io.File>()
    try {
        val videoFile = java.io.File(videoPath)
        val folder = videoFile.parentFile
        val base = videoFile.nameWithoutExtension.lowercase()
        val dirs = listOfNotNull(
            folder,
            folder?.let { java.io.File(it, "Subs") },
            folder?.let { java.io.File(it, "Subtitles") },
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        )
        for (dir in dirs) {
            if (!dir.isDirectory) continue
            dir.listFiles()
                ?.filter { it.isFile && it.extension.lowercase() in SUPPORTED_LOCAL_SUBTITLE_EXTENSIONS }
                ?.sortedByDescending { it.nameWithoutExtension.lowercase().contains(base) }
                ?.take(25)
                ?.forEach { results.add(it) }
            if (results.size >= 40) break
        }
    } catch (_: Exception) {}
    return results.toList()
}

// FIX (tolerant parser round): accepts both comma (spec-correct SRT) and
// dot (common in files converted from VTT, which uses dots) as the
// milliseconds separator. Previously comma-only, so a dot-decimal file's
// timestamps simply never matched this regex at all — sync/drift
// adjustment silently did nothing rather than failing loudly. Output is
// unaffected either way: shiftSrtTimestampMatch() below regenerates the
// whole matched substring from scratch using comma, so a dot-decimal
// input file gets normalized to spec-correct comma on its first shift,
// which is a strict improvement, not a behavior change to guard against.
private val SRT_TIME_REGEX = Regex("(\\d{2}):(\\d{2}):(\\d{2})[,.](\\d{3})")

// FIX/FEATURE: Auto-Sync progress and results previously only rendered
// inside Subtitle Studio's Timing tab — meaning closing or navigating
// away from Studio while analysis was running (or while a result was
// waiting to be applied) made it invisible, with no way to see it again
// short of reopening Studio and navigating back to Timing. This floating
// indicator sits above the player itself, independent of Studio's
// open/closed state, so progress and results stay visible regardless of
// what else the person is doing on screen. Only rendered while Studio is
// NOT open (studioUi.showStudio == false at the call site) — when Studio
// IS open on the Timing tab, the existing inline card there already shows
// the same information, and showing both at once would just be visual
// clutter for no benefit.
//
// Draggable: wrapped at its call site in the existing
// DraggableFloatingPopup (same long-press-drag + bounds-clamping already
// proven for Track Selector/Appearance Studio) — this composable itself
// only owns the visual content, not positioning or drag, which live at
// the call site instead.
@Composable
internal fun AutoSyncFloatingIndicator(
    status: AutoSyncStatus,
    onApply: (SubtitleSyncResult) -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    if (status is AutoSyncStatus.Idle) return

    val infiniteTransition = rememberInfiniteTransition(label = "autoSyncSpin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(1100, easing = LinearEasing)),
        label = "autoSyncSpinAngle"
    )

    Column(
        horizontalAlignment = Alignment.Start,
        modifier = Modifier
            .widthIn(max = 260.dp)
            .animateContentSize(animationSpec = tween(220))
            .clip(RoundedCornerShape(16.dp))
            .background(SpaceDeep.copy(alpha = 0.72f))
            .border(1.dp, AmberGlow.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
    ) {
        when (status) {
            is AutoSyncStatus.Analyzing -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Sync, contentDescription = null, tint = AmberCore,
                        modifier = Modifier.size(15.dp).graphicsLayer { rotationZ = spinAngle }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = status.stage, color = TextBright, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
            is AutoSyncStatus.Success, is AutoSyncStatus.LowConfidence -> {
                val result = if (status is AutoSyncStatus.Success) status.result else (status as AutoSyncStatus.LowConfidence).result
                val highConfidence = status is AutoSyncStatus.Success
                val accentColor = if (highConfidence) Color(0xFF4CAF50) else Color(0xFFFF9800)
                val offsetSeconds = result.initialOffsetMs / 1000f
                val isDrift = result.timeScale != 1.0
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = if (highConfidence) "Auto-sync complete" else "Possible correction found",
                        color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${if (isDrift) "Starting offset" else "Offset"}: ${if (offsetSeconds >= 0f) "+" else ""}${"%.2f".format(offsetSeconds)}s",
                        color = TextBright, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                    )
                    if (isDrift) {
                        val driftPercent = (result.timeScale - 1.0) * 100.0
                        Text(
                            text = "Drift: ${if (driftPercent >= 0) "+" else ""}${"%.2f".format(driftPercent)}%",
                            color = TextBright, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(text = "Confidence: ${(result.confidence * 100).toInt()}%", color = TextMuted, fontSize = 10.sp)
                    if (!highConfidence) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Limited matching dialogue found — worth previewing before you commit.",
                            color = TextMuted, fontSize = 9.5.sp, lineHeight = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Apply", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF4CAF50)).clickable { onApply(result) }.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                        Text(
                            text = "Cancel", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFE53935)).clickable { onCancel() }.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }
            is AutoSyncStatus.Failed -> {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(text = "Couldn't sync", color = TextBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Icon(
                            imageVector = Icons.Rounded.Close, contentDescription = "Dismiss", tint = TextMuted,
                            modifier = Modifier.size(15.dp).clickable { onCancel() }
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = status.reason, color = TextMuted, fontSize = 10.sp, lineHeight = 13.sp, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Try Again", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(AmberCore).clickable { onRetry() }.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
            AutoSyncStatus.Idle -> {}
        }
    }
}

// FIX: was private (file-scoped) — AutoSyncCoordinator.kt, in a
// different file, needs this too. internal keeps it out of any public
// API surface while making it visible across files in this module.
// FIX: readTextFromUri/computeDriftTransform used to be defined right
// here — moved to SubtitleSharedUtils.kt (see that file for why: this
// file changes on every slice of the ongoing logic-extraction effort,
// which made it an unstable place for two small, unrelated coordinator
// classes to depend on). Already resolves via the existing
// `import com.sole.cinevault.subtitles.*` at the top of this file.

private fun shiftSrtTimestampMatch(match: MatchResult, offsetMs: Long, scale: Float = 1f): String {
    val h = match.groupValues[1].toLong()
    val m = match.groupValues[2].toLong()
    val s = match.groupValues[3].toLong()
    val ms = match.groupValues[4].toLong()
    val originalMs = (h * 3_600_000L) + (m * 60_000L) + (s * 1_000L) + ms
    var totalMs = (originalMs * scale).toLong() + offsetMs
    if (totalMs < 0L) totalMs = 0L
    val newH = totalMs / 3_600_000L
    val newM = (totalMs % 3_600_000L) / 60_000L
    val newS = (totalMs % 60_000L) / 1_000L
    val newMs = totalMs % 1_000L
    return "%02d:%02d:%02d,%03d".format(newH, newM, newS, newMs)
}

internal fun buildShiftedSubtitleFile(context: Context, sourceUri: Uri, offsetMs: Long, scale: Float = 1f): Uri? {
    if (offsetMs == 0L && scale == 1f) return sourceUri
    // Only SRT's specific timing syntax is understood by shiftSrtTimestampMatch
    // below — for any other format, sync/drift adjustment is a no-op rather
    // than a silent corruption risk. See SubtitleFormatSupport.kt.
    if (!supportsCustomTextPipeline(detectSubtitleFormat(sourceUri))) return sourceUri
    val original = readTextFromUri(context, sourceUri) ?: return null
    val shifted = SRT_TIME_REGEX.replace(original) { shiftSrtTimestampMatch(it, offsetMs, scale) }
    return try {
        // FIX: was a single fixed filename ("cinevault_synced_subtitle.srt")
        // shared by EVERY video and EVERY offset/drift value — rapid sync
        // adjustments or overlapping coroutines could have one request's
        // write clobber a file ExoPlayer was still actively reading from a
        // different request. Now unique per source file + exact transform
        // parameters, so two different requests can never collide.
        val uniqueName = "cinevault_synced_${sourceUri.hashCode()}_${offsetMs}_${scale.hashCode()}.srt"
        val outFile = java.io.File(context.cacheDir, uniqueName)
        outFile.writeText(shifted)
        Uri.fromFile(outFile)
    } catch (e: Exception) { null }
}

// Cleaning is applied BEFORE sync/drift shifting whenever a subtitle first
// becomes the active one — since cleaning only ever touches text lines and
// shifting only ever touches timestamp lines, running clean-then-shift (in
// that order, on the cleaned file's own timestamps) composes correctly
// regardless of what order the person actually adjusts settings in.
// buildCleanedSubtitleFile also moved to SubtitleSharedUtils.kt, for the
// same reason — needed from the subtitle-search extraction, in a
// different file.

// Given two (position, correction) reference points, derives the linear
// scale + shift that makes both points land exactly on their intended
// corrected time — the math behind "Fix Gradual Drift". Point A is assumed
// to be earlier in the video than Point B; if they're passed in reverse
// order this still works since it solves the two-point line algebraically
// rather than assuming an order.
// FIX: was private (file-scoped) — SubtitleSyncToolsCoordinator.kt, in
// a different file, needs this too. Same reasoning as readTextFromUri
// above.
// computeDriftTransform also moved to SubtitleSharedUtils.kt.

@Composable
fun SrtBrowserPopup(
    files: List<java.io.File>,
    modifier: Modifier,
    popupWidth: Dp,
    popupMaxHeight: Dp,
    onPick: (java.io.File) -> Unit,
    onDelete: (java.io.File) -> Unit,
    onSystemPicker: () -> Unit,
    onClose: () -> Unit
) {
    Column(modifier = modifier.width(popupWidth).heightIn(max = popupMaxHeight).glassPanel(cornerRadius = 18.dp, fill = SpaceMid.copy(alpha = 0.97f)).padding(8.dp)) {
        Text(text = "Subtitle Files", color = AmberCore, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
        if (files.isEmpty()) {
            Text(
                text = "No .srt files found near this video",
                color = TextMuted, fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            )
        } else {
            Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                files.forEach { file ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.weight(1f)) {
                            GlassMenuRow(icon = Icons.Rounded.ClosedCaption, label = file.name, selected = false, onClick = { onPick(file) })
                        }
                        IconButton(onClick = { onDelete(file) }, modifier = Modifier.size(30.dp)) {
                            Icon(imageVector = Icons.Rounded.Delete, contentDescription = "Delete subtitle file", tint = TextMuted, modifier = Modifier.size(15.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
        GlassMenuRow(icon = null, label = "System file picker…", selected = false, onClick = onSystemPicker)
        Spacer(modifier = Modifier.height(4.dp))
        GlassMenuRow(icon = null, label = "Close", selected = false, onClick = onClose)
    }
}

@Composable
fun SpeedMenuPopup(currentSpeed: Float, popupWidth: Dp, popupMaxHeight: Dp, onSpeedSelected: (Float) -> Unit, onDismiss: () -> Unit) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    Column(modifier = Modifier.width(popupWidth).heightIn(max = popupMaxHeight).glassPanel(cornerRadius = 13.dp, fill = SpaceMid.copy(alpha = 0.97f)).padding(5.dp).verticalScroll(rememberScrollState())) {
        Text(text = "Speed", color = AmberCore, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
        speeds.forEach { speed ->
            CompactSelectableRow(
                label = if (speed == 1.0f) "1x Normal" else "${speed}x",
                selected = speed == currentSpeed,
                onClick = { onSpeedSelected(speed) }
            )
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}

@Composable
fun SleepMenuPopup(currentMinutes: Int, popupWidth: Dp, popupMaxHeight: Dp, onSelected: (Int) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(0 to "Off", 15 to "15 min", 30 to "30 min", 45 to "45 min", 60 to "60 min")
    Column(modifier = Modifier.width(popupWidth).heightIn(max = popupMaxHeight).glassPanel(cornerRadius = 13.dp, fill = SpaceMid.copy(alpha = 0.97f)).padding(5.dp).verticalScroll(rememberScrollState())) {
        Text(text = "Sleep Timer", color = AmberCore, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
        options.forEach { (mins, label) ->
            CompactSelectableRow(
                label = label,
                selected = mins == currentMinutes,
                onClick = { onSelected(mins) }
            )
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}

@Composable
private fun CompactSelectableRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(9.dp)
    Row(
        modifier = Modifier.fillMaxWidth().clip(shape)
            .background(if (selected) AmberGlow.copy(alpha = 0.16f) else Color.Transparent)
            .then(
                if (selected) Modifier.border(width = 1.dp, brush = Brush.verticalGradient(listOf(AmberGlow.copy(alpha = 0.85f), AmberDeep.copy(alpha = 0.35f))), shape = shape) else Modifier
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = if (selected) AmberCore else TextBright, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

@Composable
internal fun BackIconButton(size: Dp, onClick: () -> Unit) {
    Box(modifier = Modifier.size(size).clip(RoundedCornerShape(20.dp))
        .background(GlassSurface)
        .background(Brush.verticalGradient(0f to AmberGlow.copy(alpha = 0.10f), 1f to Color.Transparent))
        .border(1.2.dp, Brush.verticalGradient(listOf(AmberGlow.copy(alpha = 0.55f), AmberDeep.copy(alpha = 0.25f))), RoundedCornerShape(20.dp))
        .clickable { onClick() }, contentAlignment = Alignment.Center) {
        Icon(imageVector = Icons.Rounded.ArrowBack, contentDescription = "Back", tint = AmberCore, modifier = Modifier.size(size * 0.42f))
    }
}

@Composable
internal fun GlassTransportButton(icon: ImageVector, size: Dp, onClick: () -> Unit) {
    Box(modifier = Modifier.size(size).clip(RoundedCornerShape(20.dp))
        .background(GlassSurface)
        .background(Brush.verticalGradient(0f to GlassHighlight, 0.4f to Color.Transparent, 1f to Color.Transparent))
        .border(1.dp, Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderBottom)), RoundedCornerShape(20.dp))
        .clickable { onClick() }, contentAlignment = Alignment.Center) {
        Icon(imageVector = icon, contentDescription = null, tint = TextBright, modifier = Modifier.size(size * 0.46f))
    }
}

@Composable
internal fun FrostedPlayButton(isPlaying: Boolean, isEnded: Boolean, size: Dp, onClick: () -> Unit) {
    val infinite = rememberInfiniteTransition(label = "playGlow")
    val glowAlpha by infinite.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(animation = tween(1400, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "playGlowAlpha"
    )
    val density = LocalDensity.current
    val glowRadiusPx = with(density) { (size / 2f * 1.05f).toPx() }.coerceAtLeast(1f)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(GlassSurfaceStrong)
            .background(Brush.verticalGradient(0f to GlassHighlight, 0.45f to Color.Transparent, 1f to Color.Transparent))
            .background(Brush.radialGradient(colors = listOf(AmberGlow.copy(alpha = glowAlpha * 0.55f), Color.Transparent), radius = glowRadiusPx))
            .border(
                width = 1.4.dp,
                brush = Brush.verticalGradient(listOf(AmberGlow.copy(alpha = 0.75f + 0.2f * glowAlpha), AmberDeep.copy(alpha = 0.30f))),
                shape = CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = when {
                isEnded -> Icons.Rounded.Replay
                isPlaying -> Icons.Rounded.Pause
                else -> Icons.Rounded.PlayArrow
            },
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = AmberCore,
            modifier = Modifier.size(size * 0.50f)
        )
    }
}

@Composable
internal fun IconCircle(icon: ImageVector, size: Dp, tint: Color = TextBright, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier = modifier.size(size).clip(RoundedCornerShape(20.dp))
        .background(GlassSurface)
        .background(Brush.verticalGradient(0f to GlassHighlight, 0.4f to Color.Transparent, 1f to Color.Transparent))
        .border(1.dp, Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderBottom)), RoundedCornerShape(20.dp))
        .clickable { onClick() }, contentAlignment = Alignment.Center) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.44f))
    }
}

@Composable
internal fun CinematicSeekBar(position: Long, duration: Long, isDragging: Boolean, seed: Int, onPreviewPositionChanged: (Long) -> Unit, onSeekFinished: (Long) -> Unit) {
    var localPosition by remember { mutableLongStateOf(position) }
    LaunchedEffect(position, isDragging) { if (!isDragging) localPosition = position }
    val haptic = LocalHapticFeedback.current
    var waveformVisible by remember { mutableStateOf(false) }
    LaunchedEffect(isDragging) {
        if (isDragging) { waveformVisible = true }
        else if (waveformVisible) { delay(2000); waveformVisible = false }
    }
    // Same bar width/gap as the waveform drawn below — used here purely to
    // detect when a drag crosses into a new bar, so a haptic tick can fire
    // per bar instead of only 4 times across the whole seek bar. Gives a
    // scroll-wheel/ratchet feel instead of a few coarse clicks.
    val density = LocalDensity.current
    val barStepPx = with(density) { (3.dp + 2.2.dp).toPx() }
    var lastBarIndex by remember { mutableIntStateOf(-1) }
    fun barIndexOf(x: Float): Int = (x / barStepPx).toInt()
    val bloom by animateFloatAsState(targetValue = if (isDragging || waveformVisible) 1f else 0f, animationSpec = tween(if (isDragging || waveformVisible) 300 else 600, easing = FastOutSlowInEasing), label = "liquidBloom")
    val glow by animateFloatAsState(targetValue = if (isDragging) 1f else 0.45f, animationSpec = tween(220), label = "seekGlow")
    fun positionFromX(x: Float, width: Float): Long { if (duration <= 0L || width <= 0f) return 0L; return (duration * (x / width).coerceIn(0f, 1f)).toLong().coerceIn(0L, duration) }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val trackWidth = maxWidth
        Box(modifier = Modifier.fillMaxWidth().height(38.dp)
            .pointerInput(duration) { detectTapGestures { o -> val p = positionFromX(o.x, size.width.toFloat()); localPosition = p; onPreviewPositionChanged(p); onSeekFinished(p) } }
            .pointerInput(duration) { detectDragGestures(
                onDragStart = { o -> localPosition = positionFromX(o.x, size.width.toFloat()); lastBarIndex = barIndexOf(o.x); onPreviewPositionChanged(localPosition) },
                onDrag = { c, _ ->
                    localPosition = positionFromX(c.position.x, size.width.toFloat())
                    val bar = barIndexOf(c.position.x)
                    if (bar != lastBarIndex) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); lastBarIndex = bar }
                    onPreviewPositionChanged(localPosition)
                },
                onDragEnd = { onSeekFinished(localPosition) },
                onDragCancel = { onSeekFinished(localPosition) }) }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cy = size.height / 2f
                val prog = (localPosition.toFloat() / duration.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                val tx = (size.width * prog).coerceIn(0f, size.width)

                val threadAlpha = 1f - bloom
                if (threadAlpha > 0.01f) {
                    val th = 2.2.dp.toPx(); val r = th / 2f
                    drawRoundRect(color = Color.White.copy(alpha = 0.15f * threadAlpha), topLeft = Offset(0f, cy - th / 2f), size = Size(size.width, th), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
                    drawRoundRect(color = AmberGlow.copy(alpha = 0.95f * threadAlpha), topLeft = Offset(0f, cy - th / 2f), size = Size(tx, th), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
                }

                if (bloom > 0.01f) {
                    val barW = 3.dp.toPx(); val gap = 2.2.dp.toPx(); val step = barW + gap
                    val n = (size.width / step).toInt().coerceAtLeast(1)
                    val maxH = size.height * 0.92f
                    val cr = androidx.compose.ui.geometry.CornerRadius(barW / 2f, barW / 2f)
                    for (i in 0 until n) {
                        val bx = i * step + barW / 2f
                        val h1 = i * 374761393 + seed * 668265263
                        val h2 = (h1 xor (h1 shr 13)) * 1274126177
                        val noise = ((h2 ushr 16) and 0xFFFF) / 65535f
                        val wave = 0.5f + 0.5f * kotlin.math.sin(i * 0.31f + (seed % 360) / 57.3f)
                        val wave2 = 0.5f + 0.5f * kotlin.math.sin(i * 0.071f + (seed % 13).toFloat())
                        val amp = (0.18f + 0.82f * (0.40f * wave + 0.25f * wave2 + 0.35f * noise)).coerceIn(0.12f, 1f)
                        val hgt = amp * maxH * bloom
                        val played = bx <= tx
                        val prox = 1f - (kotlin.math.abs(bx - tx) / (size.width * 0.30f)).coerceIn(0f, 1f)
                        val alpha = if (played) bloom * (0.55f + 0.45f * prox) else bloom * (0.20f + 0.45f * prox)
                        val barColor = if (played) AmberGlow else Color.White
                        drawRoundRect(color = barColor.copy(alpha = alpha), topLeft = Offset(bx - barW / 2f, cy - hgt / 2f), size = Size(barW, hgt), cornerRadius = cr)
                    }
                }

                val tickY = cy - bloom * (size.height * 0.40f)
                listOf(0.25f, 0.50f, 0.75f).forEach {
                    drawCircle(color = Color.White.copy(alpha = 0.45f + 0.20f * bloom), radius = 2.2.dp.toPx(), center = Offset(size.width * it, tickY))
                }

                drawCircle(color = AmberGlow.copy(alpha = 0.22f * glow), radius = 16.dp.toPx(), center = Offset(tx, cy))
                drawCircle(color = AmberCore.copy(alpha = 0.40f * glow), radius = 10.dp.toPx(), center = Offset(tx, cy))
                if (bloom > 0.01f) {
                    drawLine(color = Color(0xFFFFF3D6).copy(alpha = 0.90f * bloom), start = Offset(tx, cy - size.height * 0.46f), end = Offset(tx, cy + size.height * 0.46f), strokeWidth = 2.dp.toPx())
                }
                drawCircle(color = Color(0xFFFFF3D6), radius = if (isDragging) 5.4.dp.toPx() else 4.6.dp.toPx(), center = Offset(tx, cy))
            }
        }

        val prog = (localPosition.toFloat() / duration.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
        val pillWidthEstimate = 52.dp
        AnimatedVisibility(
            visible = isDragging || waveformVisible,
            enter = fadeIn(animationSpec = tween(120)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(
                    x = (trackWidth * prog - pillWidthEstimate / 2f).coerceIn(0.dp, (trackWidth - pillWidthEstimate).coerceAtLeast(0.dp)),
                    y = (-30).dp
                )
        ) {
            Text(
                text = formatTime(localPosition),
                color = AmberCore, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.glassPanel(cornerRadius = 50.dp, fill = GlassSurfaceStrong).padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
internal fun SeekPreviewBubble(
    isVisible: Boolean,
    bitmap: Bitmap?,
    timeText: String,
    isLandscape: Boolean,
    isLarge: Boolean,
    progress: Float,
    bottomPadding: Dp,
) {
    // Landscape has much less vertical room. Keeping the portrait-sized
    // card there made its top half occupy the visual centre even when its
    // bottom edge was correctly anchored above the seek dock.
    val pw by animateDpAsState(
        if (isLarge) (if (isLandscape) 168.dp else 210.dp)
        else (if (isLandscape) 128.dp else 160.dp),
        tween(160),
        "pw",
    )
    val ph by animateDpAsState(
        if (isLarge) (if (isLandscape) 88.dp else 118.dp)
        else (if (isLandscape) 64.dp else 90.dp),
        tween(160),
        "ph",
    )
    AnimatedVisibility(visible = isVisible, enter = fadeIn(animationSpec = tween(80)), exit = fadeOut(animationSpec = tween(80)), modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val hp = 18.dp; val aw = maxWidth - (hp * 2)
            val raw = aw * progress.coerceIn(0f, 1f) - (pw / 2); val max = aw - pw
            val safe = when { max < 0.dp -> 0.dp; raw < 0.dp -> 0.dp; raw > max -> max; else -> raw }
            Column(modifier = Modifier.align(Alignment.BottomStart).offset(x = hp + safe).padding(bottom = bottomPadding)
                .graphicsLayer { scaleX = if (isLarge) 1.02f else 0.98f; scaleY = if (isLarge) 1.02f else 0.98f; shadowElevation = if (isLarge) 18f else 10f }
                .glassPanel(cornerRadius = 18.dp, fill = GlassSurfaceStrong)
                .padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (bitmap != null) Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.width(pw).height(ph).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                else Box(modifier = Modifier.width(pw).height(ph).clip(RoundedCornerShape(14.dp)).background(GlassSurfaceFaint), contentAlignment = Alignment.Center) { Text(text = timeText, color = AmberCore, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = timeText, color = AmberCore, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
internal fun buildPipActions(context: Context, isPlaying: Boolean): List<RemoteAction> {
    fun action(code: Int, iconRes: Int, title: String): RemoteAction {
        val intent = Intent("com.sole.cinevault.PIP_ACTION").putExtra("pip_action", code)
        val pi = PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return RemoteAction(AndroidIcon.createWithResource(context, iconRes), title, title, pi)
    }
    return listOf(
        action(1, android.R.drawable.ic_media_rew, "Rewind"),
        action(0, if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, if (isPlaying) "Pause" else "Play"),
        action(2, android.R.drawable.ic_media_ff, "Forward")
    )
}

@Composable
internal fun NowPlayingTitlePill(text: String, fontSize: androidx.compose.ui.unit.TextUnit) {
    val infinite = rememberInfiniteTransition(label = "titlePulse")
    val dotAlpha by infinite.animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1100, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "titleDotAlpha"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .glassPanel(cornerRadius = 50.dp, fill = GlassSurfaceStrong)
            .border(1.dp, Brush.horizontalGradient(listOf(AmberGlow.copy(alpha = 0.15f), AmberGlow.copy(alpha = 0.55f), AmberGlow.copy(alpha = 0.15f))), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(AmberCore.copy(alpha = dotAlpha)))
        Spacer(modifier = Modifier.width(7.dp))
        Text(text = text, color = TextBright, fontSize = fontSize, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun FloatingScoreCapsule(meta: VideoWithMetadata?, vertical: Boolean = false) {
    if (meta == null) return
    val imdb = meta.imdbRating?.takeIf { it.isNotBlank() && it != "N/A" }
    val rt = meta.rottenTomatoesRating?.takeIf { it.isNotBlank() && it != "N/A" }
    val tmdb = meta.rating?.takeIf { it > 0.0 }
    if (imdb == null && rt == null && tmdb == null) return

    val entries: List<@Composable () -> Unit> = buildList {
        if (imdb != null) add {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ImdbLogoMark()
                Spacer(modifier = Modifier.width(5.dp))
                Text(text = imdb, color = TextBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (rt != null) add {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TomatoLogoMark(value = rt)
                Spacer(modifier = Modifier.width(5.dp))
                Text(text = rt, color = TextBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (tmdb != null) add {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TmdbLogoMark()
                Spacer(modifier = Modifier.width(5.dp))
                Text(text = String.format("%.1f", tmdb), color = TextBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (vertical) {
        Column(
            modifier = Modifier.glassPanel(cornerRadius = 24.dp, fill = GlassSurfaceStrong).padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) { entries.forEach { it() } }
    } else {
        Row(
            modifier = Modifier.glassPanel(cornerRadius = 50.dp, fill = GlassSurfaceStrong).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) { entries.forEach { it() } }
    }
}

@Composable
private fun RatingLogoGlow(size: Dp, content: @Composable BoxScope.() -> Unit) {
    val breathe = rememberInfiniteTransition(label = "ratingGlow")
    val glowAlpha by breathe.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ratingGlowAlpha"
    )
    Box(
        modifier = Modifier
            .size(size)
            .amberGlow(radius = size * 1.4f, alpha = glowAlpha),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
private fun ImdbLogoMark() {
    RatingLogoGlow(size = 22.dp) {
        Image(
            painter = painterResource(R.drawable.ic_imdb),
            contentDescription = "IMDb",
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun TmdbLogoMark() {
    RatingLogoGlow(size = 22.dp) {
        Image(
            painter = painterResource(R.drawable.ic_tmdb),
            contentDescription = "TMDB",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun TomatoLogoMark(value: String) {
    val percent = value.replace("%", "").trim().toIntOrNull() ?: 0
    val isFresh = percent >= 60
    RatingLogoGlow(size = 22.dp) {
        Image(
            painter = painterResource(R.drawable.ic_rotten_tomatoes),
            contentDescription = "Rotten Tomatoes",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            colorFilter = if (!isFresh) ColorFilter.tint(Color(0xFF8BC34A)) else null
        )
    }
}

@Composable
internal fun VerticalBrightnessHud(value: Int, size: Dp) {
    val fill = (value.toFloat() / 100f).coerceIn(0f, 1f)
    Row(modifier = Modifier.glassPanel(cornerRadius = 26.dp, fill = GlassSurfaceStrong).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = Icons.Rounded.BrightnessHigh, contentDescription = null, tint = AmberCore, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Box(modifier = Modifier.width(9.dp).height(size).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.16f))) {
            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(fill).align(Alignment.BottomCenter).clip(RoundedCornerShape(50)).background(Brush.verticalGradient(colors = listOf(Color(0xFFFFF3D6), Color(0xFFFFC857)))))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = "$value%", color = TextBright, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun FilledCircleHud(value: Int, maxValue: Int, color: Color, size: Dp) {
    val fill = (value.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)
    Row(modifier = Modifier.glassPanel(cornerRadius = 26.dp, fill = GlassSurfaceStrong).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = Icons.Rounded.VolumeUp, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Box(modifier = Modifier.width(9.dp).height(size).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.16f))) {
            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(fill).align(Alignment.BottomCenter).clip(RoundedCornerShape(50)).background(Brush.verticalGradient(colors = listOf(color.copy(alpha = 0.9f), color.copy(alpha = 0.6f)))))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = "$value%", color = TextBright, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

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

private fun cleanEpisodeDisplayName(fileName: String): String {
    val m = Regex("s(\\d{1,2})e(\\d{1,2})", RegexOption.IGNORE_CASE).find(fileName)
    val prefix = if (m != null) "S${m.groupValues[1].padStart(2,'0')}E${m.groupValues[2].padStart(2,'0')}" else "Episode"
    var n = fileName.substringAfterLast("/").substringAfterLast("\\").substringBeforeLast(".").replace(Regex("\\[.*?]"), " ").replace(Regex("\\(.*?\\)"), " ").replace(".", " ").replace("_", " ").replace("-", " ")
    n = n.replace(Regex("s\\d{1,2}e\\d{1,2}", RegexOption.IGNORE_CASE), " ").replace(Regex("\\b(2160p|1080p|720p|480p|4k|uhd|hdr10\\+?|hdr|dv|dolby|vision|bluray|brrip|webrip|webdl|web|x264|x265|h264|h265|hevc|10bit|aac|ddp|dts|atmos|mkv|mp4|avi|rarbg|yts|eztv|tgx|nf|amzn)\\b", RegexOption.IGNORE_CASE), " ").replace(Regex("\\s+"), " ").trim()
    return if (n.isBlank()) prefix else "$prefix • $n"
}

@Composable
internal fun NextEpisodeCountdownOverlay(nextEpisode: VideoWithMetadata?, countdown: Int, isLandscape: Boolean, onPlayNow: () -> Unit, onCancel: () -> Unit) {
    if (nextEpisode == null) return
    Column(modifier = Modifier.widthIn(min = if (isLandscape) 260.dp else 270.dp, max = if (isLandscape) 330.dp else 310.dp)
        .glassPanel(cornerRadius = 22.dp, fill = GlassSurfaceStrong)
        .padding(horizontal = 16.dp, vertical = 14.dp), horizontalAlignment = Alignment.Start) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "UP NEXT", color = AmberCore, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Text(text = "${countdown.coerceAtLeast(1)}s", color = AmberCore, fontSize = 17.sp, fontWeight = FontWeight.Black)
        }
        Spacer(modifier = Modifier.height(7.dp))
        Text(text = nextEpisode.subtitle.ifBlank { cleanEpisodeDisplayName(nextEpisode.video.name) }, color = TextBright, fontSize = if (isLandscape) 13.sp else 14.sp, fontWeight = FontWeight.Bold, maxLines = 2)
        Spacer(modifier = Modifier.height(11.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Play Now", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.clip(RoundedCornerShape(50)).background(AmberCore).clickable { onPlayNow() }.padding(horizontal = 15.dp, vertical = 8.dp))
            Text(text = "Cancel", color = TextBright, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f)).clickable { onCancel() }.padding(horizontal = 15.dp, vertical = 8.dp))
        }
    }
}

@Composable
fun FloatingTrackPopup(title: String, modifier: Modifier, rows: List<TrackPopupRowData>, audioSyncMs: Int = 0, onAudioSyncChange: (Int) -> Unit = {}, onAnyClick: () -> Unit = {}, onClose: () -> Unit) {
    Column(modifier = modifier.glassPanel(cornerRadius = 16.dp, fill = SpaceMid.copy(alpha = 0.97f)).padding(6.dp)) {
        Text(text = title, color = AmberCore, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
        rows.forEach { row ->
            CompactGlassMenuRow(icon = Icons.Rounded.Audiotrack, label = row.title, onClick = { onAnyClick(); row.onClick() })
            Spacer(modifier = Modifier.height(3.dp))
        }
        Text(text = "Audio Delay", color = AmberCore, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SyncStepChip(text = "−50") { onAnyClick(); onAudioSyncChange((audioSyncMs - 50).coerceAtLeast(-2000)) }
            Text(
                text = if (audioSyncMs == 0) "0ms" else "${if (audioSyncMs > 0) "+" else ""}$audioSyncMs",
                color = if (audioSyncMs == 0) TextBright else AmberCore,
                fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            SyncStepChip(text = "+50") { onAnyClick(); onAudioSyncChange((audioSyncMs + 50).coerceAtMost(2000)) }
        }
        Spacer(modifier = Modifier.height(5.dp))
        CompactGlassMenuRow(icon = null, label = "Close", onClick = { onAnyClick(); onClose() })
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
