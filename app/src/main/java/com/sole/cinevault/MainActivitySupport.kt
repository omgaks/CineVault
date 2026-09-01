package com.sole.cinevault

import com.sole.cinevault.library.*

import android.os.Build
import android.os.Bundle
import android.app.Activity
import android.content.Intent
import android.content.Context
import android.content.res.Configuration
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.exoplayer.ExoPlayer
import com.sole.cinevault.ui.theme.CineVaultTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.sole.cinevault.subtitles.SubtitleImportEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object CineVaultPlayerHolder {
    var currentPlayer: ExoPlayer? = null
    // Set/cleared by VideoPlayerScreen.kt alongside currentPlayer. Bridges
    // hardware media-button next/previous (headset, Bluetooth) into the
    // app's own episode-switching logic (playNext()/playPrevious() in
    // VideoPlayerScreen.kt), since the player only ever holds one MediaItem
    // at a time rather than a real ExoPlayer playlist — see
    // CineVaultForwardingPlayer.kt for how these get wired to the actual
    // hardware key events via the MediaSession.
    var onNextRequested: (() -> Unit)? = null
    var onPreviousRequested: (() -> Unit)? = null
    // Bridges MainActivity's onPictureInPictureModeChanged (an Android
    // lifecycle callback, not something Compose can observe directly) into
    // Compose. State-backed (unlike the two above) specifically so
    // VideoPlayerScreen.kt can react to it — hiding its overlay chrome
    // (transport controls, lock button, menus, the Auto-Sync pill) while
    // in the tiny PiP window, leaving just the bare video visible. Without
    // this, the full player UI would try to render into that postage-
    // stamp-sized window, which is what "only tiny control buttons
    // visible, nothing usable" was actually describing.
    var isInPipMode by mutableStateOf(false)
}

// "Open with CineVault" support — MainActivity receives the incoming VIEW
// (open a video file / stream link) or SEND (shared link) Intent at the
// Activity level, outside Compose entirely, but the actual navigation
// (pushing a Player destination) has to happen inside CineVaultApp()'s
// Compose back-stack. This plain observable object is the bridge between
// the two: MainActivity writes to it from onCreate/onNewIntent, and a
// LaunchedEffect inside CineVaultApp() (keyed on this value) reacts to it
// and pushes the player destination, then clears it.
object IncomingIntentHolder {
    var pendingUri: android.net.Uri? by mutableStateOf(null)
}

// FIX: Player screen hides system bars completely (true immersive/fullscreen)
// NOTE: findCineActivity() already exists in Screens.kt — reused here, not redefined.
fun Activity.enterImmersiveModeForPlayer() {
    WindowInsetsControllerCompat(window, window.decorView).apply {
        hide(WindowInsetsCompat.Type.systemBars())
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

// FIX: Every other screen (Home/Library/Search/Settings) shows normal system bars
fun Activity.exitImmersiveModeForPlayer() {
    WindowInsetsControllerCompat(window, window.decorView).apply {
        show(WindowInsetsCompat.Type.systemBars())
    }
}

// FIX: was ComponentActivity — changed to FragmentActivity specifically
// because androidx.biometric's stable BiometricPrompt API (used for Secret
// Folder's unlock, replacing the deprecated KeyguardManager.
// createConfirmDeviceCredentialIntent) requires a FragmentActivity host.
// FragmentActivity extends ComponentActivity, so setContent {} and
// everything else already working here is unaffected — this is additive,
// not a behavior change to anything except gaining Fragment support.

// Best-effort display name for a video opened via "Open with" / "Share to
// CineVault" — content:// Uris carry a real filename via OpenableColumns,
// everything else (file://, http://, https://) falls back to the last path
// segment. Never throws; worst case the video just shows a generic name.
private fun resolveOpenedVideoDisplayName(context: android.content.Context, uri: android.net.Uri): String {
    if (uri.scheme.equals("content", ignoreCase = true)) {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    val name = cursor.getString(nameIndex)
                    if (!name.isNullOrBlank()) return name
                }
            }
        } catch (_: Exception) {}
    }
    return uri.lastPathSegment?.substringAfterLast("/")?.takeIf { it.isNotBlank() } ?: "Video"
}

// ── Crash logger ────────────────────────────────────────────────────────
// See onCreate() above. File lives in the app's private internal storage
// (context.filesDir) — no permissions or file-manager access required to
// write it; Settings' "View Crash Log" reads it back through the app itself.
const val CRASH_LOG_FILE_NAME = "cinevault_crash_log.txt"

fun installCrashLogger(context: Context) {
    val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        try {
            val logFile = java.io.File(context.filesDir, CRASH_LOG_FILE_NAME)
            val writer = java.io.StringWriter()
            throwable.printStackTrace(java.io.PrintWriter(writer))
            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            val entry = "\n\n=== Crash at $timestamp (thread: ${thread.name}) ===\n$writer"
            // Keeps the log from growing forever — trims to the last ~50KB
            // (roughly the last several crashes) before appending the new one.
            val existing = try { logFile.readText() } catch (_: Exception) { "" }
            val trimmed = if (existing.length > 50_000) existing.takeLast(50_000) else existing
            logFile.writeText(trimmed + entry)
        } catch (_: Exception) {
            // If logging itself fails, don't let that mask the real crash.
        }
        defaultHandler?.uncaughtException(thread, throwable)
    }
}

fun readCrashLog(context: Context): String {
    return try {
        val logFile = java.io.File(context.filesDir, CRASH_LOG_FILE_NAME)
        if (logFile.exists()) logFile.readText().trim() else ""
    } catch (_: Exception) {
        ""
    }
}

fun clearCrashLog(context: Context) {
    try {
        java.io.File(context.filesDir, CRASH_LOG_FILE_NAME).delete()
    } catch (_: Exception) {}
}
