package com.sole.cinevault.glasses

/**
 * The screens the phone and the glasses copy of CineVault keep in step with each other.
 * Only top-level browsing is mirrored: a tab, a film's detail page, or a TV show page.
 * Playing a film is deliberately NOT mirrored yet (one player must own playback).
 */
internal sealed interface BrowseRoute {
    data class Tab(val index: Int) : BrowseRoute
    data class Detail(val videoPath: String) : BrowseRoute
    data class TvGroup(val showName: String) : BrowseRoute
}

private fun rawIdentity(key: String): String = key.substringAfter(':', missingDelimiterValue = key)

/** The browse screen the shared session describes, or null while a film is playing. */
internal fun browseRouteFromSession(
    selectedTab: Int,
    videoKey: String?,
    detailKey: String?,
    tvGroupKey: String?,
): BrowseRoute? = when {
    videoKey != null -> null
    detailKey != null -> BrowseRoute.Detail(rawIdentity(detailKey))
    tvGroupKey != null -> BrowseRoute.TvGroup(rawIdentity(tvGroupKey))
    else -> BrowseRoute.Tab(selectedTab)
}

/**
 * Follow the other screen only when it is somewhere different from where we already are.
 * A page that is not mirrored (null, e.g. a genre page) is never pulled back to a tab.
 */
internal fun shouldFollowMirror(local: BrowseRoute?, target: BrowseRoute?): Boolean = when (target) {
    null -> false
    is BrowseRoute.Tab -> when (local) {
        is BrowseRoute.Tab -> local != target
        is BrowseRoute.Detail, is BrowseRoute.TvGroup -> true
        null -> false
    }
    else -> local != target
}
