package com.sole.cinevault.picture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimationIntelligenceTest {

    @Test
    fun `Japanese animation metadata classifies Anime`() {
        val result = AnimationIntelligence.classify(
            fileName = "Example Movie.mkv",
            genres = listOf("Animation", "Fantasy"),
            originalLanguage = "ja",
        )
        assertEquals(AnimationType.ANIME, result.type)
        assertTrue(result.confidence >= 0.95f)
    }

    @Test
    fun `Japanese live action is not classified Anime`() {
        val result = AnimationIntelligence.classify(
            fileName = "映画テスト.mkv",
            genres = listOf("Drama", "Thriller"),
            originalLanguage = "ja",
        )
        assertEquals(AnimationType.UNKNOWN, result.type)
    }

    @Test
    fun `fansub release classifies Anime`() {
        val result = AnimationIntelligence.classify(
            fileName = "[SubsPlease] Example - 01.mkv",
            genres = listOf("Animation", "Action"),
        )
        assertEquals(AnimationType.ANIME, result.type)
        assertEquals(AnimationClassificationSource.LOCAL_SIGNALS, result.source)
    }

    @Test
    fun `romanised known anime title classifies Anime`() {
        val result = AnimationIntelligence.classify(
            fileName = "Princess Mononoke 1997 1080p BluRay.mkv",
            title = "Princess Mononoke",
            genres = listOf("Animation", "Adventure"),
        )
        assertEquals(AnimationType.ANIME, result.type)
    }

    @Test
    fun `stop motion keyword wins`() {
        val result = AnimationIntelligence.classify(
            fileName = "Example.mkv",
            genres = listOf("Animation", "Family"),
            keywords = listOf("stop motion"),
        )
        assertEquals(AnimationType.STOP_MOTION, result.type)
    }

    @Test
    fun `cgi keyword classifies CGI 3D`() {
        val result = AnimationIntelligence.classify(
            fileName = "Example.mkv",
            genres = listOf("Animation"),
            keywords = listOf("computer animation"),
        )
        assertEquals(AnimationType.CGI_3D, result.type)
    }

    @Test
    fun `traditional animation classifies classic 2D`() {
        val result = AnimationIntelligence.classify(
            fileName = "Example.mkv",
            genres = listOf("Animation"),
            keywords = listOf("hand-drawn animation"),
        )
        assertEquals(AnimationType.CLASSIC_2D, result.type)
    }

    @Test
    fun `digital 2D classifies modern 2D`() {
        val result = AnimationIntelligence.classify(
            fileName = "Example.mkv",
            genres = listOf("Animation"),
            keywords = listOf("2d animation"),
        )
        assertEquals(AnimationType.MODERN_2D, result.type)
    }

    @Test
    fun `hybrid is not collapsed into CGI or 2D`() {
        val result = AnimationIntelligence.classify(
            fileName = "Example.mkv",
            genres = listOf("Animation"),
            keywords = listOf("2d and 3d"),
        )
        assertEquals(AnimationType.HYBRID, result.type)
    }

    @Test
    fun `rotoscope classifies stylized`() {
        val result = AnimationIntelligence.classify(
            fileName = "Example.mkv",
            genres = listOf("Animation"),
            keywords = listOf("rotoscope"),
        )
        assertEquals(AnimationType.STYLIZED, result.type)
    }

    @Test
    fun `generic animation remains unknown instead of guessing from resolution`() {
        val result = AnimationIntelligence.classify(
            fileName = "Example Animated Movie 2160p.mkv",
            genres = listOf("Animation", "Family"),
        )
        assertEquals(AnimationType.UNKNOWN, result.type)
        assertTrue(result.evidence.any { it.contains("no reliable subtype evidence") })
    }

    @Test
    fun `classifier version is persisted in result contract`() {
        val result = AnimationIntelligence.classify(
            fileName = "Example.mkv",
            genres = listOf("Animation"),
        )
        assertEquals(AnimationIntelligence.CLASSIFIER_VERSION, result.version)
    }
}
