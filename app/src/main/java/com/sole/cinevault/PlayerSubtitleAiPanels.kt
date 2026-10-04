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
import com.sole.cinevault.glasses.display.CineVaultRenderDestination
import com.sole.cinevault.glasses.display.LocalCineVaultRenderDestination
import com.sole.cinevault.subtitles.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding

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
    embeddedSubtitleLabel: String? = null,
    speechCoordinator: SpeechSubtitleCoordinator,
    translationCoordinator: SubtitleTranslationCoordinator,
    generatedSubtitleOrchestrator: GeneratedSubtitleOrchestrator,
    onShowSpeechPanel: () -> Unit,
    onHideSpeechPanel: () -> Unit,
    onShowTranslationPanel: () -> Unit,
    onHideTranslationPanel: () -> Unit,
) {
    val renderDestination = LocalCineVaultRenderDestination.current
    val subtitleAiSurfacesAvailable =
        shouldRenderSubtitleAiSurfaces(
            isInPipMode = isInPipMode,
            externalDisplayActive = externalDisplayActive,
            renderDestination = renderDestination,
        )

    PlayerFloatingJobOverlay(
        visible =
            speechJobLabel != null &&
                !showSpeechPanel &&
                subtitleAiSurfacesAvailable,
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
                subtitleAiSurfacesAvailable,
        containerWidth = containerWidth,
        containerHeight = containerHeight,
        label = translationJobLabel ?: "Translate",
        progress = translationJobProgress,
        onOpen = onShowTranslationPanel,
    )

    if (
        showSpeechPanel &&
        subtitleAiSurfacesAvailable
    ) {
        val panelWidth =
            (containerWidth * 0.46f)
                .coerceAtMost(320.dp)
                .coerceAtLeast(250.dp)

        val panelHeight =
            (containerHeight * 0.72f)
                .coerceAtMost(340.dp)
                .coerceAtLeast(240.dp)

        Box(modifier = Modifier.fillMaxSize()) {
            // Tap on empty space minimises the panel (a running job keeps going as a pill).
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { onHideSpeechPanel() } }
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .width(panelWidth)
                    .heightIn(max = (containerHeight * 0.85f).coerceAtMost(440.dp))
                    .blockTapThrough()
            ) {
                    SpeechSubtitlePanel(
                        status = speechStatus,
                        models = WhisperModelManager.modelCatalog(context),
                        onSelectModel = speechCoordinator::selectModel,
                        onDownloadModel = speechCoordinator::downloadModel,
                        onDeleteModel = speechCoordinator::deleteModel,
                        onGenerate = {
                            speechCoordinator.generateSubtitles()
                            // Minimise to the floating pill straight away so the movie stays
                            // visible; tapping the pill reopens this panel (with Stop).
                            onHideSpeechPanel()
                        },
                        onStop = {
                            speechCoordinator.cancelTranscription()
                        },
                        onDismiss = onHideSpeechPanel,
                    )
            }
        }
    }

    if (
        showTranslationPanel &&
        subtitleAiSurfacesAvailable
    ) {
        val panelWidth =
            (containerWidth * 0.44f)
                .coerceAtMost(310.dp)
                .coerceAtLeast(245.dp)

        val panelHeight =
            (containerHeight * 0.60f)
                .coerceAtMost(350.dp)
                .coerceAtLeast(245.dp)

        Box(modifier = Modifier.fillMaxSize()) {
            // Tap on empty space closes the panel.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { onHideTranslationPanel() } }
            )
            // The 59-language list used to stretch this panel to the full screen height.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .width(panelWidth)
                    .heightIn(max = (containerHeight * 0.80f).coerceAtMost(420.dp))
                    .blockTapThrough()
            ) {
                    SubtitleTranslationPanel(
                        status = translationStatus,
                        activeSource =
                            generatedSubtitleOrchestrator.resolveActiveSubtitle()
                                ?: embeddedSubtitleLabel?.let {
                                    // An embedded track is read out of the movie when translation
                                    // starts, so it counts as a usable source.
                                    SubtitleSourceResolver.Resolved(
                                        uri = Uri.EMPTY,
                                        language = null,
                                        label = it,
                                        source = "read from the movie",
                                    )
                                },
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


internal fun shouldRenderSubtitleAiSurfaces(
    isInPipMode: Boolean,
    externalDisplayActive: Boolean,
    renderDestination: CineVaultRenderDestination,
): Boolean {
    if (isInPipMode) return false

    // The tablet becomes Cinema Void while an external display is active,
    // so AI subtitle panels stay suppressed there. The external destination
    // is the canonical CineVault render and must retain the real Speech and
    // Translation surfaces.
    if (
        externalDisplayActive &&
        renderDestination == CineVaultRenderDestination.HOST_DISPLAY
    ) {
        return false
    }

    return true
}
