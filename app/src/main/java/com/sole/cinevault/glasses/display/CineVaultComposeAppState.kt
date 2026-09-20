package com.sole.cinevault.glasses.display

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * D1-14: Compose-facing observable bridge for one CineVault session.
 *
 * D1-12/D1-13 intentionally kept the display/navigation contracts free of
 * Compose. This class makes the shared snapshot observable so host and external
 * compositions can react to the SAME session state.
 *
 * It owns no Android Context, player, subtitle engine, menu or display object.
 */
class CineVaultComposeAppState(
    val sessionId: CineVaultRenderSessionId,
    private val sharedState: CineVaultSharedAppState,
) {
    init {
        require(sharedState.sessionId == sessionId) {
            "Compose state and shared state must belong to the same CineVault session."
        }
    }

    private val _snapshot = MutableStateFlow(sharedState.snapshot)
    val snapshot: StateFlow<CineVaultAppStateSnapshot> = _snapshot.asStateFlow()

    private inline fun mutate(block: CineVaultSharedAppState.() -> Unit) {
        sharedState.block()
        _snapshot.value = sharedState.snapshot
    }

    fun onTabSelected(tab: Int) = mutate { selectTab(tab) }

    fun onSearchQueryChanged(query: String) = mutate { setSearchQuery(query) }

    fun onDetailSelected(key: String) = mutate { openDetail(key) }

    fun onTvGroupSelected(key: String) = mutate { openTvGroup(key) }

    fun onVideoSelected(key: String) = mutate { playVideo(key) }

    fun onBack(): Boolean {
        val handled = sharedState.back()
        if (handled) _snapshot.value = sharedState.snapshot
        return handled
    }

    /**
     * Sync point for legacy callbacks that still pass through D1-13 while the
     * migration is incremental. No state is duplicated; we publish the current
     * session owner's snapshot.
     */
    fun publishCurrentSnapshot() {
        _snapshot.value = sharedState.snapshot
    }
}

@Immutable
data class CineVaultComposeNavigation(
    val snapshot: CineVaultAppStateSnapshot,
    val onTabSelected: (Int) -> Unit,
    val onSearchQueryChanged: (String) -> Unit,
    val onDetailSelected: (String) -> Unit,
    val onTvGroupSelected: (String) -> Unit,
    val onVideoSelected: (String) -> Unit,
    val onBack: () -> Boolean,
)

/**
 * Small Compose adapter. Later CineVaultRoot/CineVaultApp wiring consumes this
 * instead of creating another set of remember { mutableStateOf(...) } routes.
 */
@Composable
fun CineVaultComposeAppState.asNavigation(): CineVaultComposeNavigation {
    val current: State<CineVaultAppStateSnapshot> = snapshot.collectAsState()

    return CineVaultComposeNavigation(
        snapshot = current.value,
        onTabSelected = ::onTabSelected,
        onSearchQueryChanged = ::onSearchQueryChanged,
        onDetailSelected = ::onDetailSelected,
        onTvGroupSelected = ::onTvGroupSelected,
        onVideoSelected = ::onVideoSelected,
        onBack = ::onBack,
    )
}

/**
 * One observable Compose bridge per logical CineVault render session.
 */
class CineVaultComposeAppStateRegistry(
    private val sharedRegistry: CineVaultSharedAppStateRegistry =
        CineVaultSharedAppStateRegistry(),
) {
    private val composeStates =
        mutableMapOf<CineVaultRenderSessionId, CineVaultComposeAppState>()

    fun stateFor(sessionId: CineVaultRenderSessionId): CineVaultComposeAppState =
        composeStates.getOrPut(sessionId) {
            val shared = sharedRegistry.stateFor(sessionId)
            CineVaultComposeAppState(
                sessionId = sessionId,
                sharedState = shared,
            )
        }

    fun release(sessionId: CineVaultRenderSessionId) {
        composeStates.remove(sessionId)
        sharedRegistry.release(sessionId)
    }

    fun clear() {
        composeStates.clear()
        sharedRegistry.clear()
    }
}
