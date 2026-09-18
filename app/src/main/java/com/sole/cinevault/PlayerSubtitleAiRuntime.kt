package com.sole.cinevault

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.media3.exoplayer.ExoPlayer
import com.sole.cinevault.subtitles.*
import kotlinx.coroutines.CoroutineScope

/**
 * Slice 52: groups the generated-subtitle / Speech-to-Subs / AI Translation
 * runtime wiring that previously lived inline in VideoPlayerScreen.
 *
 * Existing coordinators still own all behavior. This helper only owns their
 * Compose lifecycle, generated-library refresh effect, pending Dual AI effect,
 * job presentation values, and the AI-panel BackHandler wiring.
 */
data class PlayerSubtitleAiRuntime(
    val generatedSubtitleOrchestrator: GeneratedSubtitleOrchestrator,
    val speechSubtitleCoordinator: SpeechSubtitleCoordinator,
    val subtitleTranslationCoordinator: SubtitleTranslationCoordinator,
    val speechJobLabel: String?,
    val speechJobProgress: Int?,
    val translationJobLabel: String?,
    val translationJobProgress: Int?,
)

@Composable
fun rememberPlayerSubtitleAiRuntime(
    context: Context,
    scope: CoroutineScope,
    exoPlayer: ExoPlayer,
    currentVideoPath: String,
    trackUi: SubtitleTrackSelectionState,
    coreUi: SubtitleCoreUiState,
    dualUi: DualSubtitleState,
    subtitleSyncTools: SubtitleSyncToolsCoordinator,
    speechSubtitleStatus: SpeechSubtitleStatus,
    onSpeechSubtitleStatusChanged: (SpeechSubtitleStatus) -> Unit,
    subtitleTranslationStatus: SubtitleTranslationStatus,
    onSubtitleTranslationStatusChanged: (SubtitleTranslationStatus) -> Unit,
    pendingDualAiLanguage: String?,
    onPendingDualAiLanguageChanged: (String?) -> Unit,
    generatedSubtitleRefreshKey: Int,
    onGeneratedSubtitleRefreshRequested: () -> Unit,
    onGeneratedSubtitleFilesLoaded: (List<GeneratedSubtitleFile>) -> Unit,
    showSpeechSubtitlePanel: Boolean,
    showSubtitleTranslationPanel: Boolean,
    onShowSpeechSubtitlePanelChanged: (Boolean) -> Unit,
    onShowSubtitleTranslationPanelChanged: (Boolean) -> Unit,
    onTranslationSuccessLanguageChanged: (String?) -> Unit,
    playCurrentVideoWithSubtitle: (uri: android.net.Uri, resumeAt: Long) -> Unit,
): PlayerSubtitleAiRuntime {
    // Coordinators are deliberately remembered so active jobs survive ordinary
    // recomposition. Their callbacks therefore must read the latest Compose
    // values instead of capturing the values from the composition in which the
    // coordinator was first created. Without this, AI translation could keep
    // doing real work while the progress gate still saw Idle, leaving the pill
    // visually stuck at 0%; the same stale capture could also lose the pending
    // Dual-Subs AI destination when translation completed.
    val latestVideoPath by rememberUpdatedState(currentVideoPath)
    val latestSpeechSubtitleStatus by rememberUpdatedState(speechSubtitleStatus)
    val latestTranslationStatus by rememberUpdatedState(subtitleTranslationStatus)
    val latestPendingDualAiLanguage by rememberUpdatedState(pendingDualAiLanguage)
    val latestSpeechStatusChanged by rememberUpdatedState(onSpeechSubtitleStatusChanged)
    val latestTranslationStatusChanged by rememberUpdatedState(onSubtitleTranslationStatusChanged)
    val latestPendingDualChanged by rememberUpdatedState(onPendingDualAiLanguageChanged)
    val latestGeneratedRefreshRequested by rememberUpdatedState(onGeneratedSubtitleRefreshRequested)
    val latestGeneratedFilesLoaded by rememberUpdatedState(onGeneratedSubtitleFilesLoaded)
    val latestTranslationSuccessChanged by rememberUpdatedState(onTranslationSuccessLanguageChanged)
    val latestPlayCurrentVideoWithSubtitle by rememberUpdatedState(playCurrentVideoWithSubtitle)

    val generatedSubtitleLibraryCoordinator = remember {
        GeneratedSubtitleLibraryCoordinator(
            context = context,
            getCurrentVideoPath = { latestVideoPath },
        )
    }

    LaunchedEffect(currentVideoPath, generatedSubtitleRefreshKey) {
        latestGeneratedFilesLoaded(
            generatedSubtitleLibraryCoordinator.loadForCurrentVideo()
        )
    }

    val generatedSubtitleOrchestrator = remember(exoPlayer) {
        GeneratedSubtitleOrchestrator(
            getResumePosition = {
                playerSafeResumePosition(exoPlayer.currentPosition)
            },
            getPrimaryUri = { trackUi.primaryUri },
            getOriginalUri = { trackUi.originalUri },
            getSelectedKey = { trackUi.selectedKey },
            getSelectedLabel = { trackUi.selectedLabel },
            getSelectedSource = { trackUi.selectedSource },
            getPrimaryLanguage = { trackUi.primaryLanguage },
            setSubtitlesEnabled = { coreUi.subtitlesEnabled = it },
            setPrimaryUri = { trackUi.primaryUri = it },
            setOriginalUri = { trackUi.originalUri = it },
            setPrimaryLanguage = { trackUi.primaryLanguage = it },
            setSelectedKey = { trackUi.selectedKey = it },
            setSelectedLabel = { trackUi.selectedLabel = it },
            setSelectedSource = { trackUi.selectedSource = it },
            playWithSubtitle = { uri, resumeAt ->
                latestPlayCurrentVideoWithSubtitle(uri, resumeAt)
            },
        )
    }

    val speechSubtitleCoordinator = remember(exoPlayer) {
        SpeechSubtitleCoordinator(
            context = context,
            scope = scope,
            exoPlayer = exoPlayer,
            getCurrentVideoPath = { latestVideoPath },
            getStatus = { latestSpeechSubtitleStatus },
            setStatus = { latestSpeechStatusChanged(it) },
            onSubtitleReady = { file, language ->
                generatedSubtitleOrchestrator.apply(
                    file,
                    language,
                    "Speech recognition",
                )
            },
            onGeneratedLibraryChanged = { latestGeneratedRefreshRequested() },
        )
    }

    val subtitleTranslationResultCoordinator = remember {
        SubtitleTranslationResultCoordinator(
            scope = scope,
            isDualEnabled = { dualUi.enabled },
            getPendingDualLanguage = { latestPendingDualAiLanguage },
            clearPendingDualLanguage = {
                latestPendingDualChanged(null)
            },
            applyDualSecondary = { uri ->
                subtitleSyncTools.applyDualSecondaryUri(uri, "AI")
            },
            applyPrimaryTranslation = { file, language ->
                generatedSubtitleOrchestrator.apply(
                    file,
                    language,
                    "AI Translation",
                )
            },
            showTranslationSuccess = { language ->
                latestTranslationSuccessChanged(
                    SubtitleLanguageRegistry.displayName(language)
                )
            },
            clearTranslationSuccess = {
                latestTranslationSuccessChanged(null)
            },
        )
    }

    val subtitleTranslationCoordinator = remember(exoPlayer) {
        SubtitleTranslationCoordinator(
            context = context,
            scope = scope,
            getCurrentVideoPath = { latestVideoPath },
            resolveActiveSubtitle = {
                generatedSubtitleOrchestrator.resolveActiveSubtitle()
            },
            getStatus = { latestTranslationStatus },
            setStatus = { latestTranslationStatusChanged(it) },
            onSubtitleReady = { file, language ->
                subtitleTranslationResultCoordinator.onTranslationReady(
                    file = file,
                    language = language,
                )
            },
            onGeneratedLibraryChanged = { latestGeneratedRefreshRequested() },
        )
    }

    val dualAiTranslationCoordinator = remember {
        DualAiTranslationCoordinator(
            getPendingLanguage = { latestPendingDualAiLanguage },
            clearPendingLanguage = {
                latestPendingDualChanged(null)
            },
            isDualEnabled = { dualUi.enabled },
            disableDual = { dualUi.enabled = false },
            setStatusText = { dualUi.statusText = it },
            translateActive = { target ->
                subtitleTranslationCoordinator.translateActive(target)
            },
        )
    }

    LaunchedEffect(pendingDualAiLanguage) {
        dualAiTranslationCoordinator.processPendingRequest()
    }

    val speechJobPresentation =
        speechSubtitleJobPresentation(speechSubtitleStatus)
    val translationJobPresentation =
        subtitleTranslationJobPresentation(subtitleTranslationStatus)

    BackHandler(
        enabled = showSpeechSubtitlePanel || showSubtitleTranslationPanel
    ) {
        when (
            subtitlePanelBackAction(
                showSpeechSubtitlePanel = showSpeechSubtitlePanel,
                showSubtitleTranslationPanel = showSubtitleTranslationPanel,
            )
        ) {
            SubtitlePanelBackAction.CLOSE_TRANSLATION ->
                onShowSubtitleTranslationPanelChanged(false)

            SubtitlePanelBackAction.CLOSE_SPEECH ->
                onShowSpeechSubtitlePanelChanged(false)

            SubtitlePanelBackAction.NONE -> Unit
        }
    }

    return PlayerSubtitleAiRuntime(
        generatedSubtitleOrchestrator = generatedSubtitleOrchestrator,
        speechSubtitleCoordinator = speechSubtitleCoordinator,
        subtitleTranslationCoordinator = subtitleTranslationCoordinator,
        speechJobLabel = speechJobPresentation.label,
        speechJobProgress = speechJobPresentation.progress,
        translationJobLabel = translationJobPresentation.label,
        translationJobProgress = translationJobPresentation.progress,
    )
}
