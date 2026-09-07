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
    private val playSubtitle: (subtitleUri: Uri?, resumePosition: Long, isOriginalSubtitle: Boolean) -> Unit,
    private val findCachedAiSecondary: (String) -> Uri?,
    private val requestAiSecondary: (String) -> Unit
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
            Toast.makeText(context, "Sync adjusted by ${if (deltaMs >= 0) "+" else ""}${String.format(java.util.Locale.US, "%.1f", deltaMs / 1000f)}s", Toast.LENGTH_SHORT).show()
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
    // Priority: cached real subtitle -> cached AI translation -> online real
    // subtitle -> AI translation fallback. Regardless of origin, the result
    // becomes the secondary track and is merged through the same pipeline.
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

        val normalizedSecondary = SubtitleLanguageRegistry.normalize(dualUi.secondaryLanguage)
        if (trackUi.primaryLanguage != null && normalizedSecondary != null && trackUi.primaryLanguage == normalizedSecondary) {
            Toast.makeText(
                context,
                "Secondary language can't be the same as the primary (${SubtitleLanguageRegistry.displayName(trackUi.primaryLanguage)}) — pick a different one",
                Toast.LENGTH_LONG
            ).show()
            dualUi.enabled = false
            return
        }

        val languageLabel = SubtitleLanguageRegistry.displayName(dualUi.secondaryLanguage)
        dualUi.secondarySourceLabel = ""
        dualUi.statusText = "Finding exact $languageLabel match…"

        scope.launch {
            val videoPath = getCurrentVideoPath()

            // Automatic Dual Subs must NOT trust a title-only cached/search
            // result. Ambiguous titles (Avatar, Crash, Frozen, etc.) can
            // return a perfectly valid subtitle for the wrong movie/release.
            // First attempt an exact OpenSubtitles movie-hash match.
            val movieHash = withContext(Dispatchers.IO) {
                if (videoPath.startsWith("content://", ignoreCase = true)) {
                    MovieHash.compute(context, Uri.parse(videoPath))
                } else {
                    MovieHash.compute(videoPath)
                }
            }

            if (movieHash != null) {
                dualUi.statusText = "Checking exact $languageLabel movie match…"
                val hashResult = OpenSubtitlesClient.searchByHash(
                    movieHash,
                    dualUi.secondaryLanguage
                )
                val exact = (hashResult as? SubtitleSearchListResult.Success)
                    ?.results
                    ?.firstOrNull { it.hashMatch }

                if (exact != null) {
                    dualUi.statusText = "Downloading exact $languageLabel subtitle…"
                    val targetFile = OpenSubtitlesClient.subtitleCacheFile(
                        context,
                        videoPath,
                        dualUi.secondaryLanguage,
                        exact.provider
                    )
                    val result = OpenSubtitlesClient.downloadSubtitleToFile(
                        targetFile,
                        exact.fileId,
                        dualUi.secondaryLanguage,
                        exact.provider
                    )
                    if (result is SubtitleDownloadResult.Success) {
                        applyDualSecondaryUri(result.uri, "Exact match")
                        return@launch
                    }
                }
            }

            // Reuse an AI subtitle previously generated from this movie's
            // active primary subtitle before translating again.
            val cachedAi = withContext(Dispatchers.IO) {
                findCachedAiSecondary(dualUi.secondaryLanguage)
            }
            if (cachedAi != null) {
                dualUi.statusText = "Using saved AI $languageLabel secondary…"
                applyDualSecondaryUri(cachedAi, "AI")
                return@launch
            }

            // No provably-correct external subtitle: translate the PRIMARY
            // subtitle instead of guessing from a title search. Since the
            // primary already matches the movie, this keeps both content and
            // cue timing tied to the actual file being watched.
            dualUi.statusText = "Creating $languageLabel secondary from primary…"
            dualUi.secondarySourceLabel = "AI"
            requestAiSecondary(dualUi.secondaryLanguage)
        }
    }

    fun applyDualSecondaryUri(secondaryUri: Uri, sourceLabel: String) {
        val primary = trackUi.primaryUri
        if (primary == null || !dualUi.enabled) return

        val normalizedSecondary =
            SubtitleLanguageRegistry.normalize(dualUi.secondaryLanguage)
                ?: dualUi.secondaryLanguage.take(2).lowercase()

        scope.launch {
            val merged = withContext(Dispatchers.IO) {
                val primaryText = readTextFromUri(context, primary) ?: return@withContext null
                val secondaryText = readTextFromUri(context, secondaryUri) ?: return@withContext null
                val mergedText = mergeDualSubtitles(
                    primaryText,
                    secondaryText,
                    dualSecondaryColorHex,
                    dualUi.gapLines
                )
                if (!dualMergeContainsSecondary(mergedText)) {
                    return@withContext null
                }
                try {
                    val uniqueName =
                        "cinevault_dual_${OpenSubtitlesClient.cleanMovieNamePublic(getCurrentVideoPath()).hashCode()}_$normalizedSecondary.srt"
                    val outFile = java.io.File(context.cacheDir, uniqueName)
                    outFile.writeText(mergedText)
                    Uri.fromFile(outFile)
                } catch (_: Exception) {
                    null
                }
            }

            if (merged == null) {
                if (!sourceLabel.equals("AI", ignoreCase = true)) {
                    val languageLabel =
                        SubtitleLanguageRegistry.displayName(dualUi.secondaryLanguage)
                    dualUi.statusText =
                        "$languageLabel subtitle did not align — creating AI secondary…"
                    dualUi.secondarySourceLabel = "AI"
                    requestAiSecondary(dualUi.secondaryLanguage)
                    return@launch
                }

                dualUi.statusText = "Couldn't build the AI secondary subtitle"
                Toast.makeText(context, dualUi.statusText, Toast.LENGTH_LONG).show()
                dualUi.enabled = false
                return@launch
            }

            val resumeAt = exoPlayer.currentPosition.coerceAtLeast(0L)
            coreUi.subtitlesEnabled = true
            playSubtitle(merged, resumeAt, false)
            trackUi.originalUri = merged
            trackUi.appliedOffsetMs = (coreUi.syncOffset * 1000f).toLong()
            dualUi.secondarySourceLabel = sourceLabel
            dualUi.statusText =
                "Dual subtitles: ${if (trackUi.primaryLanguage != null) SubtitleLanguageRegistry.displayName(trackUi.primaryLanguage) else "Primary"} + ${SubtitleLanguageRegistry.displayName(dualUi.secondaryLanguage)}"
        }
    }

    fun disableDualSubtitles() {
        dualUi.enabled = false
        dualUi.statusText = ""
        dualUi.secondarySourceLabel = ""
        val primary = trackUi.primaryUri ?: return
        val resumeAt = exoPlayer.currentPosition.coerceAtLeast(0L)
        coreUi.subtitlesEnabled = true
        trackUi.originalUri = primary
        trackUi.appliedOffsetMs = 0L
        playSubtitle(primary, resumeAt, true)
    }

}
