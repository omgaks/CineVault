package com.sole.cinevault

import com.sole.cinevault.library.VideoFile
import com.sole.cinevault.library.sameFilmGroups
import org.junit.Assert.assertEquals
import org.junit.Test

class SameFilmGroupsTest {
    private fun film(path: String, tmdb: Int?, type: String = "movie") = VideoWithMetadata(
        video = VideoFile(path.substringAfterLast('/'), path), title = "T", subtitle = "", posterUrl = null,
        backdropUrl = null, overview = null, rating = null, tmdbId = tmdb, type = type
    )

    @Test fun differentQualitiesOfOneFilmFoldTogether() {
        val groups = sameFilmGroups(listOf(film("/a/Brand.New.Day.2160p.HDR.mkv", 1), film("/a/Brand.New.Day.1080p.mkv", 1), film("/a/Other.mkv", 2)))
        assertEquals(1, groups.size)
        assertEquals(2, groups.first().size)
    }

    @Test fun tvEpisodesAndUnmatchedFilmsAreLeftAlone() {
        val groups = sameFilmGroups(listOf(film("/a/e1.mkv", 5, "tv"), film("/a/e2.mkv", 5, "tv"), film("/a/x.mkv", null), film("/a/y.mkv", null)))
        assertEquals(0, groups.size)
    }

    @Test fun samePathCountsOnce() {
        assertEquals(0, sameFilmGroups(listOf(film("/a/x.mkv", 3), film("/a/x.mkv", 3))).size)
    }
}
