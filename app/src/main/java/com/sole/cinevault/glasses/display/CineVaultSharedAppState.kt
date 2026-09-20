package com.sole.cinevault.glasses.display

/**
 * D1-12: shared app-state ownership contract.
 *
 * The current CineVaultApp() keeps navigation selections in local remember
 * state. If host and external CineVaultRoot() compositions each own those
 * values, they become independent navigation universes.
 *
 * This contract moves only the ROUTE/SELECTION identity into one session-owned
 * state holder. It intentionally does not own Android/Compose objects,
 * ExoPlayer, subtitle engines, LazyListState, Context, or UI windows.
 */
enum class CineVaultAppSurface {
    HOME,
    LIBRARY,
    SEARCH,
    DETAIL,
    TV_SHOW,
    PLAYER,
}

data class CineVaultAppStateSnapshot(
    val selectedTab: Int = 0,
    val selectedVideoKey: String? = null,
    val selectedDetailKey: String? = null,
    val selectedTvGroupKey: String? = null,
    val searchQuery: String = "",
) {
    init {
        require(selectedTab >= 0) { "selectedTab must not be negative." }
    }

    val surface: CineVaultAppSurface
        get() = when {
            selectedVideoKey != null -> CineVaultAppSurface.PLAYER
            selectedTvGroupKey != null -> CineVaultAppSurface.TV_SHOW
            selectedDetailKey != null -> CineVaultAppSurface.DETAIL
            selectedTab == 1 -> CineVaultAppSurface.LIBRARY
            selectedTab == 2 -> CineVaultAppSurface.SEARCH
            else -> CineVaultAppSurface.HOME
        }
}

/**
 * One state owner per CineVault render session.
 *
 * Keys are used instead of VideoFile/VideoWithMetadata/TvGroup objects so this
 * display architecture stays independent from library/model implementation.
 * Later integration resolves keys against the existing CineVault data.
 */
class CineVaultSharedAppState(
    val sessionId: CineVaultRenderSessionId,
    initial: CineVaultAppStateSnapshot = CineVaultAppStateSnapshot(),
) {
    var snapshot: CineVaultAppStateSnapshot = initial
        private set

    fun selectTab(tab: Int) {
        require(tab >= 0) { "tab must not be negative." }
        snapshot = snapshot.copy(
            selectedTab = tab,
            selectedVideoKey = null,
            selectedDetailKey = null,
            selectedTvGroupKey = null,
        )
    }

    fun openDetail(key: String) {
        snapshot = snapshot.copy(
            selectedVideoKey = null,
            selectedDetailKey = key.requireKey(),
            selectedTvGroupKey = null,
        )
    }

    fun openTvGroup(key: String) {
        snapshot = snapshot.copy(
            selectedVideoKey = null,
            selectedDetailKey = null,
            selectedTvGroupKey = key.requireKey(),
        )
    }

    fun playVideo(key: String) {
        snapshot = snapshot.copy(
            selectedVideoKey = key.requireKey(),
        )
    }

    fun setSearchQuery(query: String) {
        snapshot = snapshot.copy(searchQuery = query)
    }

    /**
     * Mirrors CineVaultApp's current back priority:
     * player -> detail -> TV group -> Home tab.
     */
    fun back(): Boolean {
        snapshot = when {
            snapshot.selectedVideoKey != null ->
                snapshot.copy(selectedVideoKey = null)

            snapshot.selectedDetailKey != null ->
                snapshot.copy(selectedDetailKey = null)

            snapshot.selectedTvGroupKey != null ->
                snapshot.copy(selectedTvGroupKey = null)

            snapshot.selectedTab != 0 ->
                snapshot.copy(selectedTab = 0)

            else -> return false
        }
        return true
    }

    private fun String.requireKey(): String {
        val value = trim()
        require(value.isNotEmpty()) { "CineVault state key must not be blank." }
        return value
    }
}

/**
 * Session registry: repeated host/external lookups for the same logical session
 * return the SAME state owner.
 */
class CineVaultSharedAppStateRegistry {
    private val states = mutableMapOf<CineVaultRenderSessionId, CineVaultSharedAppState>()

    fun stateFor(sessionId: CineVaultRenderSessionId): CineVaultSharedAppState =
        states.getOrPut(sessionId) {
            CineVaultSharedAppState(sessionId)
        }

    fun release(sessionId: CineVaultRenderSessionId) {
        states.remove(sessionId)
    }

    fun clear() {
        states.clear()
    }
}
