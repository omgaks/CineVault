package com.sole.cinevault.glasses.display

/**
 * D1-13: integration adapter between the existing CineVaultApp navigation
 * callbacks and the D1-12 session-owned app-state contract.
 *
 * This deliberately does NOT introduce a second navigation system. It exposes
 * the same small actions CineVaultApp already performs:
 * tab selection, detail/TV/player opening, search updates and back.
 *
 * MainActivity/CineVaultApp wiring comes after this contract is green.
 */
class CineVaultNavigationAdapter(
    private val state: CineVaultSharedAppState,
    private val keyFactory: CineVaultNavigationKeyFactory =
        CineVaultNavigationKeyFactory(),
) {
    val sessionId: CineVaultRenderSessionId
        get() = state.sessionId

    val snapshot: CineVaultAppStateSnapshot
        get() = state.snapshot

    fun onTabSelected(tab: Int) {
        state.selectTab(tab)
    }

    fun onSearchQueryChanged(query: String) {
        state.setSearchQuery(query)
    }

    fun onDetailSelected(videoPath: String) {
        state.openDetail(keyFactory.detail(videoPath))
    }

    fun onTvGroupSelected(groupIdentity: String) {
        state.openTvGroup(keyFactory.tvGroup(groupIdentity))
    }

    fun onVideoSelected(videoPath: String) {
        state.playVideo(keyFactory.video(videoPath))
    }

    fun onPlayFromDetail(videoPath: String) {
        state.playVideo(keyFactory.video(videoPath))
    }

    fun onEpisodeSelected(videoPath: String) {
        state.playVideo(keyFactory.video(videoPath))
    }

    fun onPlayNext(videoPath: String) {
        state.playVideo(keyFactory.video(videoPath))
    }

    fun onBack(): Boolean = state.back()
}

/**
 * Stable namespace for route keys.
 *
 * We intentionally accept primitive identities here rather than importing
 * VideoFile / VideoWithMetadata / TvGroup into the glasses display package.
 * Existing CineVault code can pass video.path and the TV group's existing
 * stable identity when D1 wiring reaches MainActivity.
 */
class CineVaultNavigationKeyFactory {

    fun video(path: String): String = "video:${normalise(path)}"

    fun detail(path: String): String = "detail:${normalise(path)}"

    fun tvGroup(identity: String): String = "tv:${normalise(identity)}"

    fun rawIdentity(key: String): String =
        key.substringAfter(':', missingDelimiterValue = key)

    private fun normalise(value: String): String {
        val clean = value.trim()
        require(clean.isNotEmpty()) { "CineVault navigation identity must not be blank." }
        return clean
    }
}

/**
 * Session-scoped factory used by host and external render surfaces.
 *
 * Asking for an adapter twice with the same session id intentionally gives two
 * lightweight adapters backed by the SAME CineVaultSharedAppState instance.
 * No UI/navigation state is copied into the adapter.
 */
class CineVaultNavigationAdapterRegistry(
    private val stateRegistry: CineVaultSharedAppStateRegistry =
        CineVaultSharedAppStateRegistry(),
) {
    fun adapterFor(
        sessionId: CineVaultRenderSessionId,
    ): CineVaultNavigationAdapter =
        CineVaultNavigationAdapter(
            state = stateRegistry.stateFor(sessionId),
        )

    fun release(sessionId: CineVaultRenderSessionId) {
        stateRegistry.release(sessionId)
    }

    fun clear() {
        stateRegistry.clear()
    }
}
