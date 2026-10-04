package com.sole.cinevault.subtitles

import com.sole.cinevault.CineVaultToast

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
    private val getDualSecondaryColorHex: () -> String,
    private val getCurrentVideoPath: () -> String,
    private val playSubtitle: (subtitleUri: Uri?, resumePosition: Long, isOriginalSubtitle: Boolean) -> Unit,
    private val findCachedAiSecondary: (String) -> Uri?,
    private val requestAiSecondary: (String) -> Unit,
    private val clearPendingAiSecondary: () -> Unit
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
            CineVaultToast.show(context, "Sync adjusted by ${if (deltaMs >= 0) "+" else ""}${String.format(java.util.Locale.US, "%.1f", deltaMs / 1000f)}s")
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
        CineVaultToast.show(context, "Drift correction applied")
    }

    // ── Dual Subtitles ─────────────────────────────────────────────────
    // Priority: cached real subtitle -> cached AI translation -> online real
    // subtitle -> AI translation fallback. Regardless of origin, the result
    // becomes the secondary track and is merged through the same pipeline.
    fun fetchAndApplyDualSecondary() {
        dualUi.lastSecondaryUri = null
        val primary = trackUi.primaryUri
        if (primary == null) {
            CineVaultToast.show(context, "Dual subtitles need a downloaded or local subtitle as the primary track", long = true)
            dualUi.enabled = false
            return
        }
        if (!supportsCustomTextPipeline(detectSubtitleFormat(primary))) {
            CineVaultToast.show(context, "Dual subtitles currently only work with .srt as the primary track", long = true)
            dualUi.enabled = false
            return
        }

        val normalizedSecondary = SubtitleLanguageRegistry.normalize(dualUi.secondaryLanguage)
        if (trackUi.primaryLanguage != null && normalizedSecondary != null && trackUi.primaryLanguage == normalizedSecondary) {
            CineVaultToast.show(context, "Secondary language can't be the same as the primary (${SubtitleLanguageRegistry.displayName(trackUi.primaryLanguage)}) — pick a different one", long = true)
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

    // Colour or gap changed: re-merge from the secondary already in use. This used to
    // repeat the whole provider search, which was slow and made a tap look ignored.
    //
    // [colorHex] is the colour the user JUST picked. It is passed in directly because the
    // screen's colour state only updates on the next recomposition: reading it here made a tap
    // sometimes apply the previous colour (so it needed a second tap).
    fun reapplyDualStyle(colorHex: String? = null) {
        val uri = dualUi.lastSecondaryUri
        if (uri != null && dualUi.lastSecondaryVideoPath == getCurrentVideoPath()) {
            applyDualSecondaryUri(uri, dualUi.lastSecondaryLabel.ifBlank { "Saved" }, colorHex)
        } else {
            fetchAndApplyDualSecondary()
        }
    }

    // Only the newest colour/gap request may finish: rapid taps never apply out of order.
    private var dualStyleJob: kotlinx.coroutines.Job? = null

    fun applyDualSecondaryUri(secondaryUri: Uri, sourceLabel: String, colorHex: String? = null) {
        val primary = trackUi.primaryUri
        if (primary == null || !dualUi.enabled) return
        dualUi.lastSecondaryUri = secondaryUri
        dualUi.lastSecondaryVideoPath = getCurrentVideoPath()
        dualUi.lastSecondaryLabel = sourceLabel

        val normalizedSecondary =
            SubtitleLanguageRegistry.normalize(dualUi.secondaryLanguage)
                ?: dualUi.secondaryLanguage.take(2).lowercase()

        val colorToUse = colorHex ?: getDualSecondaryColorHex()
        val gapToUse = dualUi.gapLines
        dualStyleJob?.cancel()
        dualStyleJob = scope.launch {
            val merged = withContext(Dispatchers.IO) {
                val primaryText = readTextFromUri(context, primary) ?: return@withContext null
                val secondaryText = readTextFromUri(context, secondaryUri) ?: return@withContext null
                val mergedText = mergeDualSubtitles(
                    primaryText,
                    secondaryText,
                    colorToUse,
                    gapToUse
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
                CineVaultToast.show(context, dualUi.statusText, long = true)
                dualUi.enabled = false
                return@launch
            }

            // Keep originalUri/primaryUri as the true unshifted primary. The merged
            // file is only the render base while Dual Subs is enabled; timing is
            // then reapplied to that base by SubtitleSyncRenderCoordinator.
            trackUi.renderBaseUri = merged
            coreUi.subtitlesEnabled = true
            dualUi.secondarySourceLabel = sourceLabel
            dualUi.statusText =
                "Dual subtitles: ${if (trackUi.primaryLanguage != null) SubtitleLanguageRegistry.displayName(trackUi.primaryLanguage) else "Primary"} + ${SubtitleLanguageRegistry.displayName(dualUi.secondaryLanguage)}"

            // Do not wait for a Compose effect to notice renderBaseUri. Dual Subs
            // has already produced the exact subtitle payload that must be shown,
            // so attach it to Media3 immediately while preserving the current
            // sync/drift transform. This also makes a successful Dual toggle
            // deterministic when the merge completes between recompositions.
            val offsetMs = (coreUi.syncOffset * 1000f).toLong()
            val requestedScale = driftUi.scale
            val renderUri = withContext(Dispatchers.IO) {
                com.sole.cinevault.buildShiftedSubtitleFile(
                    context,
                    merged,
                    offsetMs,
                    requestedScale,
                )
            } ?: merged
            trackUi.appliedOffsetMs = offsetMs
            driftUi.appliedScale = requestedScale
            val resumeAt = exoPlayer.currentPosition.coerceAtLeast(0L)
            playSubtitle(renderUri, resumeAt, false)
        }
    }

    fun disableDualSubtitles() {
        dualUi.lastSecondaryUri = null
        // Slice 25: cancelling Dual Subs also cancels any pending AI-secondary
        // request here, so the player wrapper no longer owns one piece of the
        // dual-subtitle lifecycle.
        clearPendingAiSecondary()
        dualUi.enabled = false
        dualUi.statusText = ""
        dualUi.secondarySourceLabel = ""
        val primary = trackUi.primaryUri ?: return
        val resumeAt = exoPlayer.currentPosition.coerceAtLeast(0L)
        coreUi.subtitlesEnabled = true
        trackUi.renderBaseUri = null
        trackUi.originalUri = primary
        trackUi.appliedOffsetMs = Long.MIN_VALUE
        driftUi.appliedScale = Float.NaN
        playSubtitle(primary, resumeAt, false)
    }

}
