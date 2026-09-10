package com.sole.cinevault

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Slice 37: owns rebuilding/reapplying a subtitle when manual sync offset or
 * gradual-drift scale changes.
 *
 * VideoPlayerScreen retains the LaunchedEffect so Compose still cancels stale
 * work automatically when its keys change.
 */
class SubtitleSyncRenderCoordinator(
    private val context: Context,
    private val getBaseUri: () -> Uri?,
    private val areSubtitlesEnabled: () -> Boolean,
    private val getSyncOffsetSeconds: () -> Float,
    private val getRequestedScale: () -> Float,
    private val getAppliedOffsetMs: () -> Long,
    private val getAppliedScale: () -> Float,
    private val setAppliedOffsetMs: (Long) -> Unit,
    private val setAppliedScale: (Float) -> Unit,
    private val getResumePosition: () -> Long,
    private val playShiftedSubtitle: (Uri, Long) -> Unit,
) {
    suspend fun applyIfNeeded() {
        val baseUri = getBaseUri() ?: return
        val offsetMs = playerSubtitleSyncOffsetMs(getSyncOffsetSeconds())
        val requestedScale = getRequestedScale()

        if (
            !shouldRebuildShiftedSubtitle(
                subtitlesEnabled = areSubtitlesEnabled(),
                hasBaseSubtitle = true,
                requestedOffsetMs = offsetMs,
                appliedOffsetMs = getAppliedOffsetMs(),
                requestedScale = requestedScale,
                appliedScale = getAppliedScale(),
            )
        ) {
            return
        }

        delay(playerSubtitleSyncDebounceMs())

        val resumeAt = getResumePosition()
        val shiftedUri = withContext(Dispatchers.IO) {
            buildShiftedSubtitleFile(
                context,
                baseUri,
                offsetMs,
                requestedScale,
            )
        } ?: return

        setAppliedOffsetMs(offsetMs)
        setAppliedScale(requestedScale)
        playShiftedSubtitle(shiftedUri, resumeAt)
    }
}

/**
 * Pure gate used by the coordinator and covered by plain JUnit4.
 */
fun shouldRebuildShiftedSubtitle(
    subtitlesEnabled: Boolean,
    hasBaseSubtitle: Boolean,
    requestedOffsetMs: Long,
    appliedOffsetMs: Long,
    requestedScale: Float,
    appliedScale: Float,
): Boolean {
    if (!subtitlesEnabled || !hasBaseSubtitle) return false

    return requestedOffsetMs != appliedOffsetMs ||
        requestedScale != appliedScale
}
