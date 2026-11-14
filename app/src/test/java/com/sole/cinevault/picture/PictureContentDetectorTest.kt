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

    @Test
    fun `Chinese and Korean animation get the anime engine`() {
        assertEquals(PictureContent.ANIME, PictureContentDetector.detect("Some Donghua - 01.mkv", listOf("Animation", "Fantasy"), "zh"))
        assertEquals(PictureContent.ANIME, PictureContentDetector.detect("Some Korean Toon 2024.mkv", listOf("Animation"), "ko"))
        assertEquals(PictureContent.ANIME, PictureContentDetector.detect("Some Show.mkv", listOf("Animation"), "ja"))
    }

    @Test
    fun `western animation and live action stay put whatever the language`() {
        assertEquals(PictureContent.ANIMATION, PictureContentDetector.detect("Toy Tale.mkv", listOf("Animation"), "en"))
        assertEquals(PictureContent.FILM, PictureContentDetector.detect("Chinese Drama.mkv", listOf("Drama"), "zh"))
    }

    @Test
    fun `detection carries a readable reason`() {
        val d = PictureContentDetector.describe("Some Donghua - 01.mkv", listOf("Animation"), "zh")
        assertEquals(PictureContent.ANIME, d.content)
        assertEquals("animation from East Asia, anime style", d.reason)
        assertEquals("live action", PictureContentDetector.describe("x.mkv", listOf("Drama"), "ja").reason)
    }

    @Test
    fun `known anime film is recognised by name even before genres arrive`() {
        assertEquals(PictureContent.ANIME, PictureContentDetector.detect("Princess Mononoke 1997 1080p.mkv", emptyList()))
        assertEquals(PictureContent.FILM, PictureContentDetector.detect("Some Random Film 2020.mkv", emptyList()))
        assertEquals(PictureContent.FILM, PictureContentDetector.detect("Akira Kurosawa Seven.mkv", listOf("Drama")))
    }
}
