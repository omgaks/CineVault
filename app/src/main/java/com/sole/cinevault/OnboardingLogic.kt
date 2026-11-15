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

/** One tile of the scan-screen poster wall. */
internal data class WallSlot(val title: String, val posterUrl: String?)

/**
 * Picks what the poster wall shows: films with a real poster first, then films that
 * only have a name so far (they show as a titled tile until a poster arrives).
 * Repeats are skipped. Fewer than [count] films leaves the rest as shimmering gaps.
 */
internal fun posterWallSlots(entries: List<Pair<String, String?>>, count: Int): List<WallSlot> {
    val seen = HashSet<String>()
    val withPoster = entries.filter { !it.second.isNullOrBlank() }
    val withoutPoster = entries.filter { it.second.isNullOrBlank() }
    val out = ArrayList<WallSlot>()
    for ((title, poster) in withPoster + withoutPoster) {
        if (out.size >= count) break
        val key = if (poster.isNullOrBlank()) "t:$title" else "p:$poster"
        if (title.isBlank() || !seen.add(key)) continue
        out += WallSlot(title, poster?.takeIf { it.isNotBlank() })
    }
    return out
}

/**
 * Pill [i] sits at angle (angleDeg + i * step) degrees round the orbit, with 90 degrees
 * being the front (bottom). Returns the pill currently at the front.
 */
internal fun orbitFrontIndex(angleDeg: Float, size: Int): Int {
    if (size <= 0) return 0
    val step = 360f / size
    val raw = Math.round((90f - angleDeg) / step)
    return ((raw % size) + size) % size
}

/** Shortest turn (in degrees) that brings pill [index] to the front from [angleDeg]. */
internal fun orbitTurnToFront(angleDeg: Float, index: Int, size: Int): Float {
    if (size <= 0) return 0f
    val target = 90f - index * (360f / size)
    return ((target - angleDeg + 540f) % 360f + 360f) % 360f - 180f
}
