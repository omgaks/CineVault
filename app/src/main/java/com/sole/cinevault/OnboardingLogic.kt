package com.sole.cinevault

/** The three first-run steps, in order. */
internal enum class OnboardingStep(val number: Int) {
    Welcome(1), Scan(2), Reel(3);

    val next: OnboardingStep? get() = values().getOrNull(ordinal + 1)
    val previous: OnboardingStep? get() = values().getOrNull(ordinal - 1)
}

/**
 * Onboarding is for a brand-new install only. Anyone who already has films in
 * their library, or already told the app their name, is an existing user and
 * never sees it. Finishing or skipping once is remembered.
 */
internal fun shouldShowOnboarding(alreadyDone: Boolean, hasLibrary: Boolean, hasName: Boolean): Boolean =
    !alreadyDone && !hasLibrary && !hasName

/** Which chip the feature ticker highlights after [tick] steps. */
internal fun reelIndexAt(tick: Int, size: Int): Int =
    if (size <= 0) 0 else ((tick % size) + size) % size
