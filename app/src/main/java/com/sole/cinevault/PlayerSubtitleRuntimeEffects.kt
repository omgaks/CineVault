package com.sole.cinevault

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.sole.cinevault.subtitles.*
import kotlinx.coroutines.CoroutineScope

/**
 * Slice 51: owns the subtitle runtime effect cluster that used to live inline
 * in VideoPlayerScreen.
 *
 * This is orchestration only. Existing coordinators still own the actual
 * subtitle work; this helper keeps their Compose lifecycle/effect wiring
 * together and returns only the two handles the player UI still needs.
 */
data class PlayerSubtitleRuntimeEffects(
    val syncTools: SubtitleSyncToolsCoordinator,
    val isAssOrSsaFormat: Boolean,
)

@Composable
fun rememberPlayerSubtitleRuntimeEffects(
    context: Context,
    scope: CoroutineScope,
    exoPlayer: ExoPlayer,
    trackSelector: DefaultTrackSelector,
    currentVideoPath: String,
    pendingSrtUri: Uri?,
    dualSecondaryColorHex: String,
    movieSubtitleMemoryReady: Boolean,
    restoredDualNeedsApply: Boolean,
    coreUi: SubtitleCoreUiState,
    trackUi: SubtitleTrackSelectionState,
    autoSubtitleFetch: AutoSubtitleFetchState,
    appearanceUi: SubtitleAppearanceUiState,
    studioUi: SubtitleStudioUiState,
    dualUi: DualSubtitleState,
    driftUi: DriftCorrectionState,
    onShowControls: () -> Unit,
    onClearPendingSrtUri: () -> Unit,
    onRestoredDualApplied: () -> Unit,
    onPendingDualAiLanguageChanged: (String?) -> Unit,
    playCurrentVideoWithSubtitle: (
        subtitleUri: Uri?,
        resumePosition: Long,
        isOriginalSubtitle: Boolean,
    ) -> Unit,
): PlayerSubtitleRuntimeEffects {
    val pendingSubtitleApplyCoordinator = remember(exoPlayer, trackSelector) {
        PendingSubtitleApplyCoordinator(
            context = context,
            coreUi = coreUi,
            trackUi = trackUi,
            autoSubtitleFetch = autoSubtitleFetch,
            getResumePosition = {
                playerSafeResumePosition(exoPlayer.currentPosition)
            },
            enableTextTracks = {
                trackSelector.parameters =
                    trackSelector.buildUponParameters()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .build()
            },
            playSubtitle = { subtitleUri, resumePosition ->
                playCurrentVideoWithSubtitle(
                    subtitleUri,
                    resumePosition,
                    true,
                )
            },
            showControls = onShowControls,
            clearPendingUri = onClearPendingSrtUri,
        )
    }

    LaunchedEffect(pendingSrtUri) {
        pendingSrtUri?.let { pendingSubtitleApplyCoordinator.apply(it) }
    }

    val activeSubtitleFormat = remember(trackUi.originalUri) {
        trackUi.originalUri?.let { detectSubtitleFormat(it) }
            ?: SubtitleFormat.UNKNOWN
    }
    val isAssOrSsaFormat =
        activeSubtitleFormat == SubtitleFormat.ASS ||
            activeSubtitleFormat == SubtitleFormat.SSA

    val subtitleAppearanceCoordinator = remember {
        SubtitleAppearanceCoordinator()
    }

    LaunchedEffect(
        studioUi.playerView,
        appearanceUi.textSizeSp,
        appearanceUi.bottomPadding,
        appearanceUi.appearance,
        dualUi.enabled,
        appearanceUi.preserveOriginalStyling,
        isAssOrSsaFormat,
    ) {
        subtitleAppearanceCoordinator.apply(
            playerView = studioUi.playerView,
            appearanceUi = appearanceUi,
            dualSubtitlesEnabled = dualUi.enabled,
            isAssOrSsaFormat = isAssOrSsaFormat,
        )
    }

    val subtitleSyncRenderCoordinator = remember(exoPlayer) {
        SubtitleSyncRenderCoordinator(
            context = context,
            getBaseUri = { trackUi.originalUri },
            areSubtitlesEnabled = { coreUi.subtitlesEnabled },
            getSyncOffsetSeconds = { coreUi.syncOffset },
            getRequestedScale = { driftUi.scale },
            getAppliedOffsetMs = { trackUi.appliedOffsetMs },
            getAppliedScale = { driftUi.appliedScale },
            setAppliedOffsetMs = { trackUi.appliedOffsetMs = it },
            setAppliedScale = { driftUi.appliedScale = it },
            getResumePosition = {
                playerSafeResumePosition(exoPlayer.currentPosition)
            },
            playShiftedSubtitle = { uri, resumeAt ->
                playCurrentVideoWithSubtitle(
                    uri,
                    resumeAt,
                    false,
                )
            },
        )
    }

    LaunchedEffect(
        coreUi.syncOffset,
        driftUi.scale,
        trackUi.originalUri,
    ) {
        subtitleSyncRenderCoordinator.applyIfNeeded()
    }

    val subtitleSyncTools = remember(exoPlayer, dualSecondaryColorHex) {
        SubtitleSyncToolsCoordinator(
            context = context,
            scope = scope,
            exoPlayer = exoPlayer,
            coreUi = coreUi,
            driftUi = driftUi,
            dualUi = dualUi,
            trackUi = trackUi,
            getDualSecondaryColorHex = { dualSecondaryColorHex },
            getCurrentVideoPath = { currentVideoPath },
            playSubtitle = { subtitleUri, resumePosition, isOriginalSubtitle ->
                playCurrentVideoWithSubtitle(
                    subtitleUri,
                    resumePosition,
                    isOriginalSubtitle,
                )
            },
            findCachedAiSecondary = { language ->
                val normalized =
                    SubtitleLanguageRegistry.normalize(language)
                        ?: language.take(2).lowercase()

                GeneratedSubtitleStore.listForVideo(
                    context,
                    currentVideoPath,
                ).firstOrNull { file ->
                    file.fileName.contains(
                        "translated-$normalized-",
                        ignoreCase = true,
                    )
                }?.uri
            },
            requestAiSecondary = { language ->
                onPendingDualAiLanguageChanged(language)
            },
            clearPendingAiSecondary = {
                onPendingDualAiLanguageChanged(null)
            },
        )
    }

    LaunchedEffect(
        currentVideoPath,
        movieSubtitleMemoryReady,
        restoredDualNeedsApply,
        trackUi.primaryUri,
    ) {
        if (
            shouldApplyRestoredDualSubtitles(
                movieSubtitleMemoryReady = movieSubtitleMemoryReady,
                restoredDualNeedsApply = restoredDualNeedsApply,
                dualSubtitlesEnabled = dualUi.enabled,
                hasPrimarySubtitle = trackUi.primaryUri != null,
            )
        ) {
            onRestoredDualApplied()
            subtitleSyncTools.fetchAndApplyDualSecondary()
        }
    }

    return PlayerSubtitleRuntimeEffects(
        syncTools = subtitleSyncTools,
        isAssOrSsaFormat = isAssOrSsaFormat,
    )
}
