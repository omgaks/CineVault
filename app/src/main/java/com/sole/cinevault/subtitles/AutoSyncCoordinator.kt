package com.sole.cinevault.subtitles

import com.sole.cinevault.CineVaultToast
import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import com.sole.cinevault.library.VideoThumbnailHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AutoSyncCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val exoPlayer: ExoPlayer,
    private val getPrimarySubtitleUri: () -> Uri?,
    private val getCurrentVideoPath: () -> String,
    private val getAutoSyncStatus: () -> AutoSyncStatus,
    private val setAutoSyncStatus: (AutoSyncStatus) -> Unit,
    private val resetPreviewFrames: () -> Unit,
    private val incrementPreviewReloadKey: () -> Unit,
    private val setSyncOffsetSeconds: (Float) -> Unit,
    private val setDriftScale: (Float) -> Unit,
    private val incrementStudioMenuTouchKey: () -> Unit,
    private val setSpeechTimeline: (FloatArray?) -> Unit = {}
) {
    private var autoSyncJob: Job? = null

    fun runAutoSync() {
        if (autoSyncJob?.isActive == true || getAutoSyncStatus() is AutoSyncStatus.Analyzing) return

        val primary = getPrimarySubtitleUri()
        val expectedVideoPath = getCurrentVideoPath()
        if (primary == null) {
            CineVaultToast.show(context, "Auto-Sync needs a downloaded or local subtitle loaded first", long = true)
            return
        }

        setAutoSyncStatus(AutoSyncStatus.Analyzing("Extracting audio…"))
        resetPreviewFrames()
        VideoThumbnailHelper.clearPreviewCache()

        autoSyncJob = scope.launch {
            try {
                val srtText = withContext(Dispatchers.IO) { readTextFromUri(context, primary) }
                if (srtText == null) {
                    setAutoSyncStatus(AutoSyncStatus.Failed("Couldn't read the subtitle file"))
                    incrementPreviewReloadKey()
                    return@launch
                }

                ensureSessionStillMatches(expectedVideoPath, primary)
                setAutoSyncStatus(AutoSyncStatus.Analyzing("Analysing dialogue…"))

                val audioLang = exoPlayer.currentTracks.groups
                    .firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
                    ?.let { group ->
                        (0 until group.length)
                            .firstOrNull { group.isTrackSelected(it) }
                            ?.let { index -> group.getTrackFormat(index).language }
                    }
                val videoDurationMs = exoPlayer.duration.coerceAtLeast(0L)

                val result = try {
                    withContext(Dispatchers.Default) {
                        AutoSyncEngine.run(context, expectedVideoPath, videoDurationMs, audioLang, srtText)
                    }
                } catch (_: OutOfMemoryError) {
                    AutoSyncStatus.Failed("Not enough available memory for Auto-Sync right now. Close other apps and try again.")
                }

                ensureSessionStillMatches(expectedVideoPath, primary)
                setAutoSyncStatus(result)

                try {
                    val timeline = withContext(Dispatchers.Default) {
                        AutoSyncEngine.buildFullSpeechTimeline(context, expectedVideoPath, videoDurationMs, audioLang)
                    }
                    ensureSessionStillMatches(expectedVideoPath, primary)
                    setSpeechTimeline(timeline)
                } catch (_: OutOfMemoryError) {
                    setSpeechTimeline(null)
                }

                delay(1500)
                ensureSessionStillMatches(expectedVideoPath, primary)
                incrementPreviewReloadKey()
            } catch (_: CancellationException) {
                setSpeechTimeline(null)
                if (getAutoSyncStatus() is AutoSyncStatus.Analyzing) {
                    setAutoSyncStatus(AutoSyncStatus.Idle)
                }
            } finally {
                autoSyncJob = null
            }
        }
    }

    fun cancelAutoSync(resetStatus: Boolean = true) {
        autoSyncJob?.cancel()
        autoSyncJob = null
        setSpeechTimeline(null)
        if (resetStatus && getAutoSyncStatus() is AutoSyncStatus.Analyzing) {
            setAutoSyncStatus(AutoSyncStatus.Idle)
        }
    }

    private fun ensureSessionStillMatches(expectedVideoPath: String, expectedPrimary: Uri) {
        if (getCurrentVideoPath() != expectedVideoPath || getPrimarySubtitleUri() != expectedPrimary) {
            throw CancellationException("Auto-Sync session changed")
        }
    }

    fun applyAutoSyncResult(result: SubtitleSyncResult) {
        if (getAutoSyncStatus() !is AutoSyncStatus.Success &&
            getAutoSyncStatus() !is AutoSyncStatus.LowConfidence
        ) return
        setSyncOffsetSeconds((result.initialOffsetMs / 1000f).coerceIn(-10f, 10f))
        setDriftScale(result.timeScale.toFloat())
        setAutoSyncStatus(AutoSyncStatus.Idle)
        incrementStudioMenuTouchKey()
        CineVaultToast.show(
            context,
            if (result.timeScale != 1.0) "Auto-Sync applied (drift correction)" else "Auto-Sync applied"
        )
    }
}
