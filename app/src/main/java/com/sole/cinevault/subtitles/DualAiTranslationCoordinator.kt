package com.sole.cinevault.subtitles

/**
 * Slice 32: owns the decision flow for a pending Dual Subs AI translation.
 *
 * This coordinator is deliberately Android-free and callback-driven so the
 * state transitions can be covered by plain JVM/JUnit4 tests.
 */
class DualAiTranslationCoordinator(
    private val getPendingLanguage: () -> String?,
    private val clearPendingLanguage: () -> Unit,
    private val isDualEnabled: () -> Boolean,
    private val disableDual: () -> Unit,
    private val setStatusText: (String) -> Unit,
    private val translateActive: (SubtitleTranslationEngine.SupportedLanguage) -> Unit,
    private val resolveTarget: (String) -> SubtitleTranslationEngine.SupportedLanguage? =
        ::resolveDualAiTranslationTarget,
) {
    fun processPendingRequest() {
        val requested = getPendingLanguage() ?: return

        if (!isDualEnabled()) {
            clearPendingLanguage()
            return
        }

        val target = resolveTarget(requested)
        if (target == null) {
            setStatusText(
                "AI translation isn't available for " +
                    SubtitleLanguageRegistry.displayName(requested)
            )
            disableDual()
            clearPendingLanguage()
            return
        }

        translateActive(target)
    }
}

internal fun resolveDualAiTranslationTarget(
    requested: String,
): SubtitleTranslationEngine.SupportedLanguage? {
    val normalized =
        SubtitleLanguageRegistry.normalize(requested)
            ?: requested.take(2).lowercase()

    return SubtitleTranslationEngine.commonTargetLanguages
        .firstOrNull { target ->
            SubtitleLanguageRegistry.normalize(target.mlKitCode) == normalized ||
                target.mlKitCode.equals(normalized, ignoreCase = true)
        }
}
