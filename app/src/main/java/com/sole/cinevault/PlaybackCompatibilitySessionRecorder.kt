package com.sole.cinevault

/**
 * Recorder for CineVault compatibility testing.
 *
 * Results can be seeded from local persistence and every accepted change can
 * be persisted through onEntriesChanged. A single test case still progresses
 * monotonically:
 *
 * STARTING -> NATIVE_HEALTHY -> NATIVE_UNSTABLE -> SOFTWARE_RESCUED
 */
class PlaybackCompatibilitySessionRecorder(
    val device: PlaybackCompatibilityDevice,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    initialEntries: List<PlaybackCompatibilityMatrixEntry> = emptyList(),
    private val onEntriesChanged:
        ((List<PlaybackCompatibilityMatrixEntry>) -> Unit)? = null,
) {
    private val entriesByTestId =
        LinkedHashMap<String, PlaybackCompatibilityMatrixEntry>()

    init {
        require(maxEntries > 0) {
            "maxEntries must be greater than zero"
        }

        initialEntries
            .asSequence()
            .filter { it.device == device }
            .takeLastCompat(maxEntries)
            .forEach { entry ->
                entriesByTestId[entry.testCase.testId] = entry
            }
    }

    fun record(
        testCase: PlaybackCompatibilityTestCase,
        snapshot: PlaybackDiagnosticsSnapshot,
    ): PlaybackCompatibilityMatrixEntry =
        recordObservation(
            testCase = testCase,
            observation =
                buildPlaybackCompatibilityObservation(snapshot),
        )

    fun recordObservation(
        testCase: PlaybackCompatibilityTestCase,
        observation: PlaybackCompatibilityObservation,
    ): PlaybackCompatibilityMatrixEntry {
        val candidate = buildPlaybackCompatibilityMatrixEntry(
            testCase = testCase,
            device = device,
            observation = observation,
        )

        val existing = entriesByTestId[testCase.testId]
        val selected = selectCompatibilityEntry(
            existing = existing,
            candidate = candidate,
        )

        if (selected === candidate) {
            entriesByTestId.remove(testCase.testId)
            entriesByTestId[testCase.testId] = candidate
            trimToLimit()
            notifyChanged()
        }

        return selected
    }

    fun entries(): List<PlaybackCompatibilityMatrixEntry> =
        entriesByTestId.values.toList()

    fun entryFor(
        testId: String,
    ): PlaybackCompatibilityMatrixEntry? =
        entriesByTestId[testId]

    fun report(): String =
        formatPlaybackCompatibilityMatrixReport(entries())

    fun clear() {
        if (entriesByTestId.isEmpty()) {
            return
        }

        entriesByTestId.clear()
        notifyChanged()
    }

    private fun trimToLimit() {
        while (entriesByTestId.size > maxEntries) {
            val oldestKey = entriesByTestId.keys.firstOrNull()
                ?: return
            entriesByTestId.remove(oldestKey)
        }
    }

    private fun notifyChanged() {
        onEntriesChanged?.invoke(entries())
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 200
    }
}

fun selectCompatibilityEntry(
    existing: PlaybackCompatibilityMatrixEntry?,
    candidate: PlaybackCompatibilityMatrixEntry,
): PlaybackCompatibilityMatrixEntry {
    if (existing == null) {
        return candidate
    }

    return if (
        compatibilityOutcomeRank(candidate.observation.outcome) >=
        compatibilityOutcomeRank(existing.observation.outcome)
    ) {
        candidate
    } else {
        existing
    }
}

private fun compatibilityOutcomeRank(
    outcome: PlaybackCompatibilityOutcome,
): Int = when (outcome) {
    PlaybackCompatibilityOutcome.STARTING -> 0
    PlaybackCompatibilityOutcome.NATIVE_HEALTHY -> 1
    PlaybackCompatibilityOutcome.NATIVE_UNSTABLE -> 2
    PlaybackCompatibilityOutcome.SOFTWARE_RESCUED -> 3
}

private fun <T> Sequence<T>.takeLastCompat(
    count: Int,
): List<T> {
    val items = toList()
    return if (items.size <= count) {
        items
    } else {
        items.takeLast(count)
    }
}
