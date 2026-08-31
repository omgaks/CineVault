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

@androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
internal fun buildPipActions(context: Context, isPlaying: Boolean): List<RemoteAction> {
    fun action(code: Int, iconRes: Int, title: String): RemoteAction {
        val intent = Intent(CINEVAULT_PIP_ACTION)
            .setPackage(context.packageName)
            .putExtra(CINEVAULT_PIP_ACTION_EXTRA, code)
        val pi = PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return RemoteAction(AndroidIcon.createWithResource(context, iconRes), title, title, pi)
    }
    return listOf(
        action(1, android.R.drawable.ic_media_rew, "Rewind"),
        action(0, if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, if (isPlaying) "Pause" else "Play"),
        action(2, android.R.drawable.ic_media_ff, "Forward")
    )
}
