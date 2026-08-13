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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.sole.cinevault.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Extracted unchanged from VideoPlayerScreen.kt.
// Owns the reusable long-press draggable container for standalone player popups.

@OptIn(UnstableApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
// Reusable floating-window wrapper for popups whose own composable body
// can't be modified directly (SubtitleTrackSelectorSheet and
// SubtitleAppearanceStudioSheet are both ALSO rendered as tabs embedded
// inside Studio itself — adding drag logic straight into them would
// double up with Studio's own dragging when used that way). This wraps
// the popup at its STANDALONE call site only, layering a user-draggable
// offset on top of whatever initial position the call site already
// establishes (anchored-near-icon for Track Selector, centered for
// Appearance Studio) — same proven long-press-then-drag + bounds-
// clamping + activity-ping pattern already used for Studio and Search.
@Composable
fun DraggableFloatingPopup(
    containerWidth: Dp,
    containerHeight: Dp,
    popupWidth: Dp,
    popupMaxHeight: Dp,
    onUserInteraction: () -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    var dragOffsetX by remember { mutableStateOf(0f) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val maxOffsetXPx = with(density) { ((containerWidth - popupWidth) / 2).coerceAtLeast(0.dp).toPx() }
    val maxOffsetYPx = with(density) { ((containerHeight - popupMaxHeight) / 2).coerceAtLeast(0.dp).toPx() }

    Box(
        modifier = Modifier
            .offset { IntOffset(dragOffsetX.roundToInt(), dragOffsetY.roundToInt()) }
            // Root containment — same A1 fix, applied here too: nothing
            // inside this popup should ever be able to leak a touch
            // through to the video surface behind it.
            .pointerInput(Unit) { detectTapGestures { } }
            // Any touch anywhere in the popup resets its auto-close timer.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onUserInteraction()
                }
            }
            // Long-press ANYWHERE in the popup (not just a header, since
            // this wrapper has no header of its own to isolate) starts a
            // drag. Quick taps/scrolls inside the shared composable's own
            // content resolve well before the long-press timeout elapses,
            // so this doesn't compete with normal clicking or scrolling.
            .pointerInput(maxOffsetXPx, maxOffsetYPx) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
                ) { change, dragAmount ->
                    change.consume()
                    dragOffsetX = (dragOffsetX + dragAmount.x).coerceIn(-maxOffsetXPx, maxOffsetXPx)
                    dragOffsetY = (dragOffsetY + dragAmount.y).coerceIn(-maxOffsetYPx, maxOffsetYPx)
                }
            }
    ) {
        content()
    }
}
