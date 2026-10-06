package com.sole.cinevault.picture

import org.junit.Assert.assertEquals
import org.junit.Test

class PictureContentDetectorTest {

    @Test
    fun `Princess Mononoke with TMDB Animation routes to Anime`() {
        assertEquals(
            PictureContent.ANIME,
            PictureContentDetector.detect(
                "Princess Mononoke 1997 1080p BluRay.mkv",
                listOf("Animation", "Adventure", "Fantasy"),
            ),
        )
    }

    @Test
    fun `plain western animation remains Animation`() {
        assertEquals(
            PictureContent.ANIMATION,
            PictureContentDetector.detect(
                "Example Animated Movie 2026 1080p.mkv",
                listOf("Animation", "Family", "Comedy"),
            ),
        )
    }

    @Test
    fun `fansub release routes to Anime`() {
        assertEquals(
            PictureContent.ANIME,
            PictureContentDetector.detect(
                "[SubsPlease] Example - 01.mkv",
                listOf("Animation", "Action"),
            ),
        )
    }

    @Test
    fun `Japanese script without metadata routes to Anime`() {
        assertEquals(
            PictureContent.ANIME,
            PictureContentDetector.detect("映画テスト.mkv", emptyList()),
        )
    }

    @Test
    fun `non animation metadata protects Japanese live action`() {
        assertEquals(
            PictureContent.FILM,
            PictureContentDetector.detect(
                "映画テスト.mkv",
                listOf("Drama", "Thriller"),
            ),
        )
    }

    @Test
    fun `explicit Anime genre routes to Anime`() {
        assertEquals(
            PictureContent.ANIME,
            PictureContentDetector.detect(
                "Example Title.mkv",
                listOf("Anime", "Action"),
            ),
        )
    }
}
