package com.sole.cinevault.subtitles

/**
 * Slice 35: pure UI presentation mapping for long-running subtitle jobs.
 *
 * Keeping this outside VideoPlayerScreen removes status branching from the
 * composable and makes the labels/progress values regression-testable.
 */
data class SubtitleJobPresentation(
    val label: String?,
    val progress: Int?,
)

fun speechSubtitleJobPresentation(
    status: SpeechSubtitleStatus,
): SubtitleJobPresentation =
    when (status) {
        is SpeechSubtitleStatus.DownloadingModel ->
            SubtitleJobPresentation(
                label = "Whisper model",
                progress = status.percent,
            )

        is SpeechSubtitleStatus.Generating ->
            SubtitleJobPresentation(
                label = "Speech → Subs",
                progress = status.percent,
            )

        else ->
            SubtitleJobPresentation(
                label = null,
                progress = null,
            )
    }

fun subtitleTranslationJobPresentation(
    status: SubtitleTranslationStatus,
): SubtitleJobPresentation =
    when (status) {
        is SubtitleTranslationStatus.Translating ->
            SubtitleJobPresentation(
                label = "AI Translate",
                progress = status.percent,
            )

        else ->
            SubtitleJobPresentation(
                label = null,
                progress = null,
            )
    }
