package com.sole.cinevault.subtitles

import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Slice 39: routes a completed AI translation to the correct destination.
 *
 * If it matches the language currently pending for Dual Subs, it becomes the
 * secondary subtitle. Otherwise it becomes the active primary AI translation.
 */
class SubtitleTranslationResultCoordinator(
    private val scope: CoroutineScope,
    private val isDualEnabled: () -> Boolean,
    private val getPendingDualLanguage: () -> String?,
    private val clearPendingDualLanguage: () -> Unit,
    private val applyDualSecondary: (Uri) -> Unit,
    private val applyPrimaryTranslation: (GeneratedSubtitleFile, String) -> Unit,
    private val showTranslationSuccess: (String) -> Unit,
    private val clearTranslationSuccess: () -> Unit,
) {
    fun onTranslationReady(
        file: GeneratedSubtitleFile,
        language: String,
    ) {
        if (
            shouldRouteTranslationToDualSecondary(
                dualEnabled = isDualEnabled(),
                pendingDualLanguage = getPendingDualLanguage(),
                completedLanguage = language,
            )
        ) {
            clearPendingDualLanguage()
            applyDualSecondary(file.uri)
            return
        }

        applyPrimaryTranslation(file, language)
        showTranslationSuccess(language)

        scope.launch {
            delay(3_000L)
            clearTranslationSuccess()
        }
    }
}

/**
 * Pure routing decision, covered by JVM tests.
 */
fun shouldRouteTranslationToDualSecondary(
    dualEnabled: Boolean,
    pendingDualLanguage: String?,
    completedLanguage: String,
): Boolean {
    if (!dualEnabled || pendingDualLanguage == null) return false

    val normalizedPending =
        SubtitleLanguageRegistry.normalize(pendingDualLanguage)
    val normalizedCompleted =
        SubtitleLanguageRegistry.normalize(completedLanguage)

    return normalizedPending != null &&
        normalizedPending == normalizedCompleted
}
