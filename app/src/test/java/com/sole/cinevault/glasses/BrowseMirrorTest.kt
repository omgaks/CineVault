package com.sole.cinevault.glasses

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseMirrorTest {
    @Test fun playingFilmIsNotMirrored() {
        assertNull(browseRouteFromSession(1, "video:/a.mkv", "detail:/a.mkv", null))
    }

    @Test fun detailAndTvKeysResolveToRawIdentity() {
        assertEquals(BrowseRoute.Detail("/m/a.mkv"), browseRouteFromSession(0, null, "detail:/m/a.mkv", null))
        assertEquals(BrowseRoute.TvGroup("Dark"), browseRouteFromSession(0, null, null, "tv:Dark"))
        assertEquals(BrowseRoute.Tab(2), browseRouteFromSession(2, null, null, null))
    }

    @Test fun followsOnlyRealChanges() {
        assertFalse(shouldFollowMirror(BrowseRoute.Detail("a"), BrowseRoute.Detail("a")))
        assertTrue(shouldFollowMirror(BrowseRoute.Tab(0), BrowseRoute.Detail("a")))
        assertTrue(shouldFollowMirror(BrowseRoute.Detail("a"), BrowseRoute.Tab(0)))
        assertTrue(shouldFollowMirror(BrowseRoute.TvGroup("x"), BrowseRoute.Tab(1)))
        assertTrue(shouldFollowMirror(BrowseRoute.Tab(0), BrowseRoute.Tab(1)))
    }

    @Test fun neverPullsUnmirroredPageOrFollowsPlayer() {
        assertFalse(shouldFollowMirror(null, BrowseRoute.Tab(0)))
        assertFalse(shouldFollowMirror(BrowseRoute.Tab(0), null))
    }
}
