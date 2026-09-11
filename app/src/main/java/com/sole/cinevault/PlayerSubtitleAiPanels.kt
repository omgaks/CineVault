package com.sole.cinevault

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sole.cinevault.subtitles.*

/**
 * Slice 50: owns the floating Speech → Subs / AI Translate presentation:
 * background job pills plus the two draggable control panels.
 *
 * This is UI wiring only. Translation/transcription behavior remains in the
 * existing coordinators and generated-subtitle orchestrator.
 */
@Composable
fun BoxScope.PlayerSubtitleAiPanels(
    context: Context,
    containerWidth: Dp,
    containerHeight: Dp,
    isInPipMode: Boolean,
    externalDisplayActive: Boolean,
    speechJobLabel: String?,
    speechJobProgress: Int?,
    translationJobLabel: String?,
    translationJobProgress: Int?,
    showSpeechPanel: Boolean,
    showTranslationPanel: Boolean,
    speechStatus: SpeechSubtitleStatus,
    translationStatus: SubtitleTranslationStatus,
    generatedFiles: List<GeneratedSubtitleFile>,
    activeSubtitleUri: Uri?,
    speechCoordinator: SpeechSubtitleCoordinator,
    translationCoordinator: SubtitleTranslationCoordinator,
    generatedSubtitleOrchestrator: GeneratedSubtitleOrchestrator,
    onShowSpeechPanel: () -> Unit,
    onHideSpeechPanel: () -> Unit,
    onShowTranslationPanel: () -> Unit,
    onHideTranslationPanel: () -> Unit,
) {
    PlayerFloatingJobOverlay(
        visible =
            speechJobLabel != null &&
                !showSpeechPanel &&
                !isInPipMode &&
                !externalDisplayActive,
        containerWidth = containerWidth,
        containerHeight = containerHeight,
        label = speechJobLabel ?: "Speech",
        progress = speechJobProgress,
        onOpen = onShowSpeechPanel,
    )

    PlayerFloatingJobOverlay(
        visible =
            translationJobLabel != null &&
                !showTranslationPanel &&
                !isInPipMode &&
                !externalDisplayActive,
        containerWidth = containerWidth,
        containerHeight = containerHeight,
        label = translationJobLabel ?: "Translate",
        progress = translationJobProgress,
        onOpen = onShowTranslationPanel,
    )

    if (
        showSpeechPanel &&
        !isInPipMode &&
        !externalDisplayActive
    ) {
        val panelWidth =
            (containerWidth * 0.46f)
                .coerceAtMost(320.dp)
                .coerceAtLeast(250.dp)

        val panelHeight =
            (containerHeight * 0.72f)
                .coerceAtMost(340.dp)
                .coerceAtLeast(240.dp)

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            DraggableFloatingPopup(
                containerWidth = containerWidth,
                containerHeight = containerHeight,
                popupWidth = panelWidth,
                popupMaxHeight = panelHeight,
                onUserInteraction = {},
            ) {
                Box(modifier = Modifier.width(panelWidth)) {
                    SpeechSubtitlePanel(
                        status = speechStatus,
                        models = WhisperModelManager.modelCatalog(context),
                        onSelectModel = speechCoordinator::selectModel,
                        onDownloadModel = speechCoordinator::downloadModel,
                        onDeleteModel = speechCoordinator::deleteModel,
                        onGenerate = {
                            onHideSpeechPanel()
                            speechCoordinator.generateSubtitles()
                        },
                        onStop = {
                            speechCoordinator.cancelTranscription()
                        },
                        onDismiss = onHideSpeechPanel,
                    )
                }
            }
        }
    }

    if (
        showTranslationPanel &&
        !isInPipMode &&
        !externalDisplayActive
    ) {
        val panelWidth =
            (containerWidth * 0.44f)
                .coerceAtMost(310.dp)
                .coerceAtLeast(245.dp)

        val panelHeight =
            (containerHeight * 0.60f)
                .coerceAtMost(350.dp)
                .coerceAtLeast(245.dp)

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            DraggableFloatingPopup(
                containerWidth = containerWidth,
                containerHeight = containerHeight,
                popupWidth = panelWidth,
                popupMaxHeight = panelHeight,
                onUserInteraction = {},
            ) {
                Box(modifier = Modifier.width(panelWidth)) {
                    SubtitleTranslationPanel(
                        status = translationStatus,
                        activeSource =
                            generatedSubtitleOrchestrator.resolveActiveSubtitle(),
                        generatedFiles = generatedFiles,
                        activeSubtitleUri = activeSubtitleUri,
                        onLoadGenerated = { file ->
                            generatedSubtitleOrchestrator.apply(
                                file,
                                null,
                                "Generated subtitle",
                            )
                        },
                        onTranslate = { language ->
                            onHideTranslationPanel()
                            translationCoordinator.translateActive(language)
                        },
                        onStop = {
                            translationCoordinator.cancelTranslation()
                        },
                        onDismiss = onHideTranslationPanel,
                    )
                }
            }
        }
    }
}
