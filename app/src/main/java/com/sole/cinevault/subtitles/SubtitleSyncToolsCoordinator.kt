package com.sole.cinevault.subtitles

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.media3.exoplayer.ExoPlayer
import com.sole.cinevault.DriftCorrectionState
import com.sole.cinevault.DualSubtitleState
import com.sole.cinevault.SubtitleCoreUiState
import com.sole.cinevault.SubtitleTrackSelectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// FIX: second slice of extracting VideoPlayerScreen()'s behavior out of
// its own body (see AutoSyncCoordinator.kt for the first slice and the
// full reasoning behind this effort). Groups three related sync-
// adjustment tools that were all inline local functions in the same
// composable — Dialogue Sync Tap, Progressive Drift Correction, and Dual
// Subtitles — since all three read and write the same core sync state
// (coreUi.syncOffset especially) rather than being fully independent.
//
// Unlike AutoSyncCoordinator, this passes the actual PlayerUiState.kt
// state-holder objects (coreUi, driftUi, dualUi, trackUi) directly rather
// than wrapping every individual field in its own getter/setter lambda —
// those are genuine stable class instances (remember { SomeState() }),
// not raw composable-local vars, so there's no need for lambda
// indirection to read/write their fields safely. Lambdas are still used
// for the handful of things that genuinely are raw composable state or
// another local function (currentVideo.path, playCurrentVideoWithSubtitle)
// — the same reasoning as before, just not applied blanket where it
// isn't needed.
class SubtitleSyncToolsCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val exoPlayer: ExoPlayer,
    private val coreUi: SubtitleCoreUiState,
    private val driftUi: DriftCorrectionState,
    private val dualUi: DualSubtitleState,
    private val trackUi: SubtitleTrackSelectionState,
    private val dualSecondaryColorHex: String,
    private val getCurrentVideoPath: () -> String,
    private val playSubtitle: (subtitleUri: Uri?, resumePosition: Long, isOriginalSubtitle: Boolean) -> Unit
) {
    // ── Dialogue Sync Tap ─────────────────────────────────────────────
    fun armDialogueSync() {
        coreUi.dialogueSyncReferenceMs = exoPlayer.currentPosition.coerceAtLeast(0L)
        coreUi.dialogueSyncArmed = true
        exoPlayer.play()
        coreUi.showSettings = false
    }

    fun cancelDialogueSync() {
        coreUi.dialogueSyncArmed = false
        coreUi.dialogueSyncReferenceMs = null
    }

    fun confirmDialogueSyncTap() {
        val reference = coreUi.dialogueSyncReferenceMs
        if (reference != null) {
            val deltaMs = exoPlayer.currentPosition - reference
            coreUi.syncOffset = (coreUi.syncOffset + deltaMs / 1000f).coerceIn(-10f, 10f)
            Toast.makeText(context, "Sync adjusted by ${if (deltaMs >= 0) "+" else ""}${String.format("%.1f", deltaMs / 1000f)}s", Toast.LENGTH_SHORT).show()
        }
        coreUi.dialogueSyncArmed = false
        coreUi.dialogueSyncReferenceMs = null
    }

    // ── Progressive Drift Correction ─────────────────────────────────
    fun markDriftPointA(correctionSeconds: Float) {
        driftUi.pointA = DriftPoint(exoPlayer.currentPosition.coerceAtLeast(0L), correctionSeconds)
    }

    fun markDriftPointB(correctionSeconds: Float) {
        driftUi.pointB = DriftPoint(exoPlayer.currentPosition.coerceAtLeast(0L), correctionSeconds)
    }

    fun applyDriftFix() {
        val a = driftUi.pointA; val b = driftUi.pointB
        if (a == null || b == null || a.positionMs == b.positionMs) return
        val (scale, shiftMs) = computeDriftTransform(a, b)
        driftUi.scale = scale
        coreUi.syncOffset = (shiftMs / 1000f).coerceIn(-30f, 30f)
        driftUi.showDialog = false
        Toast.makeText(context, "Drift correction applied", Toast.LENGTH_SHORT).show()
    }

    // ── Dual Subtitles ─────────────────────────────────────────────────
    // Finds a genuine secondary-language subtitle (not machine-translated
    // — see SubtitleDualMerge.kt), downloads it to a LANGUAGE-SPECIFIC
    // cache file (never the primary's cache slot), merges it under the
    // primary via overlap-join, and applies the merged file as the active
    // subtitle. Rebuilds automatically if the secondary language changes
    // while dual mode is already on.
    fun fetchAndApplyDualSecondary() {
        val primary = trackUi.primaryUri
        if (primary == null) {
            Toast.makeText(context, "Dual subtitles need a downloaded or local subtitle as the primary track", Toast.LENGTH_LONG).show()
            dualUi.enabled = false
            return
        }
        if (!supportsCustomTextPipeline(detectSubtitleFormat(primary))) {
            Toast.makeText(context, "Dual subtitles currently only work with .srt as the primary track", Toast.LENGTH_LONG).show()
            dualUi.enabled = false
            return
        }
        // A secondary the same as the primary would silently "merge" a
        // subtitle with itself — blocked here with a clear message, using
        // the real tracked primary language rather than guessing from
        // preferences.
        val normalizedSecondary = SubtitleLanguageRegistry.normalize(dualUi.secondaryLanguage)
        if (trackUi.primaryLanguage != null && normalizedSecondary != null && trackUi.primaryLanguage == normalizedSecondary) {
            Toast.makeText(context, "Secondary language can't be the same as the primary (${SubtitleLanguageRegistry.displayName(trackUi.primaryLanguage)}) — pick a different one", Toast.LENGTH_LONG).show()
            dualUi.enabled = false
            return
        }
        dualUi.statusText = "Searching ${SubtitleLanguageRegistry.displayName(dualUi.secondaryLanguage)} subtitles..."
        scope.launch {
            val searchQuery = OpenSubtitlesClient.cleanMovieNamePublic(getCurrentVideoPath())
            val searchResult = OpenSubtitlesClient.searchSubtitlesDetailed(searchQuery, language = dualUi.secondaryLanguage)
            val bestFileId = (searchResult as? SubtitleSearchListResult.Success)?.results?.firstOrNull()?.fileId
            if (bestFileId == null) {
                dualUi.statusText = "No ${SubtitleLanguageRegistry.displayName(dualUi.secondaryLanguage)} subtitle found for this video"
                Toast.makeText(context, dualUi.statusText, Toast.LENGTH_LONG).show()
                dualUi.enabled = false
                return@launch
            }
            dualUi.statusText = "Downloading ${SubtitleLanguageRegistry.displayName(dualUi.secondaryLanguage)} subtitle..."
            val targetFile = OpenSubtitlesClient.subtitleCacheFile(context, getCurrentVideoPath(), dualUi.secondaryLanguage)
            val downloadResult = OpenSubtitlesClient.downloadSubtitleToFile(targetFile, bestFileId, dualUi.secondaryLanguage)
            if (downloadResult !is SubtitleDownloadResult.Success) {
                dualUi.statusText = downloadResult.summary()
                Toast.makeText(context, dualUi.statusText, Toast.LENGTH_LONG).show()
                dualUi.enabled = false
                return@launch
            }
            val merged = withContext(Dispatchers.IO) {
                val primaryText = readTextFromUri(context, primary) ?: return@withContext null
                val secondaryText = readTextFromUri(context, downloadResult.uri) ?: return@withContext null
                val mergedText = mergeDualSubtitles(primaryText, secondaryText, dualSecondaryColorHex, dualUi.gapLines)
                try {
                    // Unique per video + secondary language — a single
                    // fixed filename shared by every video would let a
                    // rapid change or overlapping coroutine's write
                    // clobber a file another request/ExoPlayer was still
                    // reading.
                    val uniqueName = "cinevault_dual_${OpenSubtitlesClient.cleanMovieNamePublic(getCurrentVideoPath()).hashCode()}_$normalizedSecondary.srt"
                    val outFile = java.io.File(context.cacheDir, uniqueName)
                    outFile.writeText(mergedText)
                    Uri.fromFile(outFile)
                } catch (e: Exception) {
                    null
                }
            }
            if (merged == null) {
                dualUi.statusText = "Couldn't merge subtitles"
                Toast.makeText(context, dualUi.statusText, Toast.LENGTH_LONG).show()
                dualUi.enabled = false
                return@launch
            }
            val resumeAt = exoPlayer.currentPosition.coerceAtLeast(0L)
            playSubtitle(merged, resumeAt, false)
            trackUi.originalUri = merged
            trackUi.appliedOffsetMs = (coreUi.syncOffset * 1000f).toLong()
            dualUi.statusText = "Dual subtitles: ${if (trackUi.primaryLanguage != null) SubtitleLanguageRegistry.displayName(trackUi.primaryLanguage) else "Primary"} + ${SubtitleLanguageRegistry.displayName(dualUi.secondaryLanguage)}"
        }
    }

    fun disableDualSubtitles() {
        dualUi.enabled = false
        dualUi.statusText = ""
        val primary = trackUi.primaryUri ?: return
        val resumeAt = exoPlayer.currentPosition.coerceAtLeast(0L)
        // isOriginalSubtitle matched the original call's implicit
        // default (true) — playCurrentVideoWithSubtitle's own default
        // parameter doesn't carry over through a lambda type, so it's
        // passed explicitly here to preserve the exact original behavior.
        playSubtitle(primary, resumeAt, true)
    }
}
