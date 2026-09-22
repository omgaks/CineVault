package com.sole.cinevault.glasses.display

/** D6-4: presentation changes preserve one shared CineVault session. */
object CineVaultSessionContinuityPolicy {
    fun resolve(
        previous: CineVaultPresentationDecision,
        next: CineVaultPresentationDecision,
        sessionAvailable: Boolean,
    ): CineVaultSessionContinuityDecision {
        val valid = previous.sharesFeatureState && next.sharesFeatureState &&
            !previous.allowsGlassesSpecificFeatureTree &&
            !next.allowsGlassesSpecificFeatureTree
        val preserve = sessionAvailable && valid
        return CineVaultSessionContinuityDecision(
            preserveExistingSession = preserve,
            recreatePlaybackEngine = false,
            recreateFeatureState = false,
            recreateNavigationState = false,
            recreateSubtitleState = false,
            recreateAudioState = false,
            transferPresentationOnly = preserve,
        )
    }
}

data class CineVaultSessionContinuityDecision(
    val preserveExistingSession: Boolean,
    val recreatePlaybackEngine: Boolean,
    val recreateFeatureState: Boolean,
    val recreateNavigationState: Boolean,
    val recreateSubtitleState: Boolean,
    val recreateAudioState: Boolean,
    val transferPresentationOnly: Boolean,
)
