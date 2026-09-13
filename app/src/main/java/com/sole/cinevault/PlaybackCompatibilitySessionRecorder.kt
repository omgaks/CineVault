package com.sole.cinevault

/**
 * In-memory recorder for one CineVault compatibility-testing session.
 *
 * A single test case is updated in-place as playback progresses:
 *
 * STARTING -> NATIVE_HEALTHY -> NATIVE_UNSTABLE -> SOFTWARE_RESCUED
 *
 * Lower-information snapshots never overwrite a more advanced result. This
 * prevents a late lifecycle/startup callback from accidentally downgrading a
 * useful compatibility result.
 */
class PlaybackCompatibilitySessionRecorder(
    val device: PlaybackCompatibilityDevice,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    private val entriesByTestId =
        LinkedHashMap<String, PlaybackCompatibilityMatrixEntry>()

    init {
        require(maxEntries > 0) {
            "maxEntries must be greater than zero"
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
            // Reinsert so the map also reflects most recently updated order.
            entriesByTestId.remove(testCase.testId)
            entriesByTestId[testCase.testId] = candidate
            trimToLimit()
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
        entriesByTestId.clear()
    }

    private fun trimToLimit() {
        while (entriesByTestId.size > maxEntries) {
            val oldestKey = entriesByTestId.keys.firstOrNull()
                ?: return
            entriesByTestId.remove(oldestKey)
        }
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 200
    }
}

/**
 * Chooses which observation should represent one test case in the session.
 *
 * Equal-stage observations replace older ones so updated decoder/health
 * details are retained. A lower-stage observation cannot downgrade a result.
 */
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
