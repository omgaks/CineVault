package com.sole.cinevault.subtitles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleDualMergeTest {

    private val primary = """
        1
        00:00:01,000 --> 00:00:03,000
        Hello there.

        2
        00:00:04,000 --> 00:00:06,000
        How are you?
    """.trimIndent()

    private val secondary = """
        1
        00:00:01,000 --> 00:00:03,000
        नमस्ते।

        2
        00:00:04,000 --> 00:00:06,000
        आप कैसे हैं?
    """.trimIndent()

    @Test
    fun merge_containsPrimaryAndSecondaryForEveryMatchedCue() {
        val merged = mergeDualSubtitles(primary, secondary, "#55CCFF", 0)

        assertTrue(merged.contains("Hello there."))
        assertTrue(merged.contains("How are you?"))
        assertTrue(merged.contains("नमस्ते।"))
        assertTrue(merged.contains("आप कैसे हैं?"))
        assertTrue(dualMergeContainsSecondary(merged))
    }

    @Test
    fun gapLines_neverCreateAnSrtCueTerminatorInsideCue() {
        val merged = mergeDualSubtitles(primary, secondary, "#55CCFF", 2)

        assertTrue(merged.contains("\u200B\n\u200B\n<font"))
        assertFalse(merged.contains("Hello there.\n\n<font"))
    }

    @Test
    fun nearestCueFallback_handlesSmallReleaseBoundaryDifference() {
        val shifted = """
            1
            00:00:03,050 --> 00:00:03,900
            नमस्ते।
        """.trimIndent()

        val merged = mergeDualSubtitles(
            """
                1
                00:00:01,000 --> 00:00:03,000
                Hello there.
            """.trimIndent(),
            shifted,
            "#55CCFF",
            0,
        )

        assertTrue(merged.contains("नमस्ते।"))
        assertTrue(dualMergeContainsSecondary(merged))
    }

    @Test
    fun invalidSecondaryColor_isSanitizedInsteadOfBreakingMarkup() {
        val merged = mergeDualSubtitles(primary, secondary, "not-a-colour", 0)

        assertTrue(merged.contains("<font color=\"#FFC107\">"))
        assertTrue(dualMergeContainsSecondary(merged))
    }

    @Test
    fun colorNormalization_isStable() {
        assertEquals("#55CCFF", normalizeDualSubtitleColor("#55ccff"))
        assertEquals("#FFC107", normalizeDualSubtitleColor(""))
    }

    @Test
    fun noUsableSecondary_doesNotReportDualReady() {
        val merged = mergeDualSubtitles(primary, "", "#55CCFF", 0)

        assertFalse(dualMergeContainsSecondary(merged))
    }
}
