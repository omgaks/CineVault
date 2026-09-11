package com.sole.cinevault

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
    val generatedSubtitleLibraryCoordinator = remember {
        GeneratedSubtitleLibraryCoordinator(
            context = context,
            getCurrentVideoPath = { currentVideoPath },
        )
    }

    LaunchedEffect(currentVideoPath, generatedSubtitleRefreshKey) {
        onGeneratedSubtitleFilesLoaded(
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
                playCurrentVideoWithSubtitle(uri, resumeAt)
            },
        )
    }

    val speechSubtitleCoordinator = remember(exoPlayer) {
        SpeechSubtitleCoordinator(
            context = context,
            scope = scope,
            exoPlayer = exoPlayer,
            getCurrentVideoPath = { currentVideoPath },
            getStatus = { speechSubtitleStatus },
            setStatus = onSpeechSubtitleStatusChanged,
            onSubtitleReady = { file, language ->
                generatedSubtitleOrchestrator.apply(
                    file,
                    language,
                    "Speech recognition",
                )
            },
            onGeneratedLibraryChanged = onGeneratedSubtitleRefreshRequested,
        )
    }

    val subtitleTranslationResultCoordinator = remember {
        SubtitleTranslationResultCoordinator(
            scope = scope,
            isDualEnabled = { dualUi.enabled },
            getPendingDualLanguage = { pendingDualAiLanguage },
            clearPendingDualLanguage = {
                onPendingDualAiLanguageChanged(null)
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
                onTranslationSuccessLanguageChanged(
                    SubtitleLanguageRegistry.displayName(language)
                )
            },
            clearTranslationSuccess = {
                onTranslationSuccessLanguageChanged(null)
            },
        )
    }

    val subtitleTranslationCoordinator = remember(exoPlayer) {
        SubtitleTranslationCoordinator(
            context = context,
            scope = scope,
            getCurrentVideoPath = { currentVideoPath },
            resolveActiveSubtitle = {
                generatedSubtitleOrchestrator.resolveActiveSubtitle()
            },
            getStatus = { subtitleTranslationStatus },
            setStatus = onSubtitleTranslationStatusChanged,
            onSubtitleReady = { file, language ->
                subtitleTranslationResultCoordinator.onTranslationReady(
                    file = file,
                    language = language,
                )
            },
            onGeneratedLibraryChanged = onGeneratedSubtitleRefreshRequested,
        )
    }

    val dualAiTranslationCoordinator = remember {
        DualAiTranslationCoordinator(
            getPendingLanguage = { pendingDualAiLanguage },
            clearPendingLanguage = {
                onPendingDualAiLanguageChanged(null)
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
