package com.sole.cinevault

import com.sole.cinevault.library.VideoFile
import com.sole.cinevault.library.hiddenSetWith
import com.sole.cinevault.library.hiddenSetWithout
import com.sole.cinevault.library.isSecretItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretItemTest {
    private fun item(path: String, type: String = "movie") = VideoWithMetadata(
        video = VideoFile(name = path.substringAfterLast('/'), path = path),
        title = "t", subtitle = "", posterUrl = null, backdropUrl = null,
        overview = null, rating = null, type = type
    )

    @Test fun hidingOneCopyHidesTheOtherRoute() {
        val library = item("/storage/emulated/0/Download/Truth and Dare.mkv")
        val addedFolder = item("content://tree/primary%3AMovies/document/primary%3AMovies%2FTruth%20and%20Dare.mkv", "restricted")
        val hidden = hiddenSetWith(emptySet(), library)
        assertTrue(isSecretItem(library, hidden, emptySet()))
        assertTrue(isSecretItem(addedFolder, hidden, emptySet()))
    }

    @Test fun unhideClearsBoth() {
        val a = item("/x/Truth and Dare.mkv")
        val hidden = hiddenSetWithout(hiddenSetWith(emptySet(), a), a)
        assertEquals(emptySet<String>(), hidden)
        assertFalse(isSecretItem(a, hidden, emptySet()))
    }

    @Test fun episodesAreHiddenByPathOnly() {
        val ep = item("/shows/A/S01E01.mkv", "tv")
        val other = item("/shows/B/S01E01.mkv", "tv")
        val hidden = hiddenSetWith(emptySet(), ep)
        assertTrue(isSecretItem(ep, hidden, emptySet()))
        assertFalse(isSecretItem(other, hidden, emptySet()))
    }
}
