package com.sole.cinevault.metadata.artworkstudio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkStudioModelsTest {
    @Test
    fun sourceFilterMatchesOnlyRequestedProvider() {
        assertTrue(artworkSourceMatches("TMDB", ArtworkStudioSource.ALL))
        assertTrue(artworkSourceMatches("Fanart.tv", ArtworkStudioSource.FANART))
        assertFalse(artworkSourceMatches("TMDB", ArtworkStudioSource.FANART))
    }

    @Test
    fun preferredLanguageRanksBeforeEnglishAndNeutral() {
        val preferred = artworkLanguageScore("fr", "fr")
        val english = artworkLanguageScore("en", "fr")
        val neutral = artworkLanguageScore(null, "fr")
        assertTrue(preferred > english)
        assertTrue(english > neutral)
    }

    @Test
    fun sourceNameRecognizesProviderAndLocalArtwork() {
        assertEquals("TMDB", artworkSourceName("https://image.tmdb.org/t/p/w780/example.jpg"))
        assertEquals("Fanart.tv", artworkSourceName("https://assets.fanart.tv/example.jpg"))
        assertEquals("Local", artworkSourceName("file:///data/user/0/app/files/artwork/poster.jpg"))
    }
}
