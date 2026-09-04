package com.sole.cinevault.subtitles

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import com.sole.cinevault.library.VideoThumbnailHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// FIX: first slice of extracting VideoPlayerScreen()'s BEHAVIOR out of its
// own body — that composable was (and, for everything not yet extracted,
// still is) one single ~2,500-line function holding UI rendering and every
// piece of playback/subtitle/Auto-Sync logic inline together. AutoSync was
// chosen as the first piece specifically because its actual engine logic
// already lives in its own files (AutoSyncEngine.kt, AutoSyncAudioExtractor.kt)
// — what was still tangled into the composable was only the orchestration
// glue: kicking off analysis, updating status, applying a result. This
// class holds exactly that glue now, nothing about its actual behavior
// changed from what runAutoSync()/applyAutoSyncResult() did before.
//
// Deliberately decoupled from PlayerUiState.kt's specific state-holder
// types (SubtitleTrackUiState, SubtitleStudioUiState, etc.) — every piece
// of state this needs to read or write comes in as a plain getter/setter
// lambda instead, created at the call site inside VideoPlayerScreen()
// where those state holders are actually in scope. That keeps this class
// generic and testable in isolation, and means it doesn't need to import
// or know about composable-scoped state machinery at all.
class AutoSyncCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val exoPlayer: ExoPlayer,
    private val getPrimarySubtitleUri: () -> Uri?,
    private val getCurrentVideoPath: () -> String,
    private val getAutoSyncStatus: () -> AutoSyncStatus,
    private val setAutoSyncStatus: (AutoSyncStatus) -> Unit,
    private val setStudioVisible: (Boolean) -> Unit,
    private val resetPreviewFrames: () -> Unit,
    private val incrementPreviewReloadKey: () -> Unit,
    private val setSyncOffsetSeconds: (Float) -> Unit,
    private val setDriftScale: (Float) -> Unit,
    private val incrementStudioMenuTouchKey: () -> Unit,
    // Optional: null-safe no-op default, so any existing construction
    // site that hasn't been updated yet still compiles unchanged.
    private val setSpeechTimeline: (FloatArray?) -> Unit = {}
) {
    fun runAutoSync() {
        // Same guard as before: SubtitleStudioSheet.kt swaps the "Start
        // Auto-Sync" button out for a progress spinner the instant status
        // becomes Analyzing, but that swap happens on the next
        // recomposition, not synchronously — a narrow window where two
        // taps landing before that frame renders could both call this.
        // Checked first, before reading anything else, to close that
        // window as tightly as possible.
        if (getAutoSyncStatus() is AutoSyncStatus.Analyzing) return
        val primary = getPrimarySubtitleUri()
        if (primary == null) {
            Toast.makeText(context, "Auto-Sync needs a downloaded or local subtitle loaded first", Toast.LENGTH_LONG).show()
            return
        }
        // Studio closes the moment analysis actually starts — the
        // floating indicator shows progress and results on its own from
        // here.
        setStudioVisible(false)
        setAutoSyncStatus(AutoSyncStatus.Analyzing("Extracting audio…"))
        // Clears the currently-playing video's own resident preview
        // bitmaps (not just the LruCache) right before the memory-heavy
        // analysis pass — see the original fix's own reasoning, unchanged
        // here, just relocated.
        resetPreviewFrames()
        VideoThumbnailHelper.clearPreviewCache()
        scope.launch {
            val srtText = withContext(Dispatchers.IO) { readTextFromUri(context, primary) }
            if (srtText == null) {
                setAutoSyncStatus(AutoSyncStatus.Failed("Couldn't read the subtitle file"))
                incrementPreviewReloadKey()
                return@launch
            }
            setAutoSyncStatus(AutoSyncStatus.Analyzing("Analysing dialogue…"))
            val audioLang = exoPlayer.currentTracks.groups
                .firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
                ?.let { g -> (0 until g.length).firstOrNull { g.isTrackSelected(it) }?.let { idx -> g.getTrackFormat(idx).language } }
            // Read on the main thread, before switching dispatchers —
            // ExoPlayer requires every access, even a simple property
            // read, to happen on the thread it was created on.
            val videoDurationMs = exoPlayer.duration.coerceAtLeast(0L)
            val result = try {
                withContext(Dispatchers.Default) {
                    AutoSyncEngine.run(context, getCurrentVideoPath(), videoDurationMs, audioLang, srtText)
                }
            } catch (oom: OutOfMemoryError) {
                AutoSyncStatus.Failed("Not enough available memory for Auto-Sync right now. Close other apps and try again.")
            }
            setAutoSyncStatus(result)
            // Full-runtime speech timeline for the Delay slider's waveform
            // — kicked off sequentially AFTER the offset-search result is
            // already set, never concurrently with it, so the two passes
            // never compete for memory at the same time. Best-effort: any
            // failure here just leaves the slider on its plain fallback
            // track, it never affects the actual sync result above.
            try {
                val timeline = withContext(Dispatchers.Default) {
                    AutoSyncEngine.buildFullSpeechTimeline(context, getCurrentVideoPath(), videoDurationMs, audioLang)
                }
                setSpeechTimeline(timeline)
            } catch (e: OutOfMemoryError) {
                setSpeechTimeline(null)
            }
            // Short delay before regenerating previews — gives the
            // collector breathing room right after a memory-intensive
            // analysis pass, and lets the result UI render first without
            // competing for memory. Unchanged from the original.
            delay(1500)
            incrementPreviewReloadKey()
        }
    }

    fun applyAutoSyncResult(result: SubtitleSyncResult) {
        setSyncOffsetSeconds((result.initialOffsetMs / 1000f).coerceIn(-10f, 10f))
        setDriftScale(result.timeScale.toFloat())
        setAutoSyncStatus(AutoSyncStatus.Idle)
        incrementStudioMenuTouchKey()
        Toast.makeText(context, if (result.timeScale != 1.0) "Auto-Sync applied (drift correction)" else "Auto-Sync applied", Toast.LENGTH_SHORT).show()
    }
}
