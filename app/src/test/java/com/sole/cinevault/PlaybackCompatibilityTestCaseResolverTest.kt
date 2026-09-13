package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCompatibilityTestCaseResolverTest {

    @Test
    fun namedTortureFileUsesStableIdInsteadOfPath() {
        val result = resolvePlaybackCompatibilityTestCase(
            path = "/storage/emulated/0/CineVault/Torture/" +
                "CVTEST__hevc-main10-4k-hdr__HEVC Main10 4K HDR.mkv",
            displayName =
                "CVTEST__hevc-main10-4k-hdr__HEVC Main10 4K HDR.mkv",
        )

        assertTrue(result.isNamedCase)
        assertEquals(
            "hevc-main10-4k-hdr",
            result.testCase.testId,
        )
        assertEquals(
            "HEVC Main10 4K HDR",
            result.testCase.sourceLabel,
        )
    }

    @Test
    fun sameNamedCaseInDifferentFoldersGetsSameStableId() {
        val phoneA = resolvePlaybackCompatibilityTestCase(
            path = "/Movies/A/" +
                "CVTEST__av1-4k60__AV1 4K60.mp4",
            displayName =
                "CVTEST__av1-4k60__AV1 4K60.mp4",
        )

        val phoneB = resolvePlaybackCompatibilityTestCase(
            path = "/sdcard/Download/TestSuite/" +
                "CVTEST__av1-4k60__AV1 4K60.mp4",
            displayName =
                "CVTEST__av1-4k60__AV1 4K60.mp4",
        )

        assertEquals(
            phoneA.testCase.testId,
            phoneB.testCase.testId,
        )
    }

    @Test
    fun idIsNormalizedForPortableMatrixUse() {
        val result = resolvePlaybackCompatibilityTestCase(
            path = "/test.mkv",
            displayName =
                "CVTEST__ HEVC Main10 4K HDR !! __Reference Clip.mkv",
        )

        assertEquals(
            "hevc-main10-4k-hdr",
            result.testCase.testId,
        )
        assertEquals(
            "Reference Clip",
            result.testCase.sourceLabel,
        )
    }

    @Test
    fun markerWithoutFriendlyLabelUsesStableIdAsLabel() {
        val result = resolvePlaybackCompatibilityTestCase(
            path = "/CVTEST__vp9-profile2-10bit.webm",
            displayName =
                "CVTEST__vp9-profile2-10bit.webm",
        )

        assertTrue(result.isNamedCase)
        assertEquals(
            "vp9-profile2-10bit",
            result.testCase.testId,
        )
        assertEquals(
            "vp9-profile2-10bit",
            result.testCase.sourceLabel,
        )
    }

    @Test
    fun ordinaryVideoPreservesExistingPathBasedIdentity() {
        val path = "/storage/emulated/0/Movies/My Movie.mkv"

        val result = resolvePlaybackCompatibilityTestCase(
            path = path,
            displayName = "My Movie.mkv",
        )

        assertFalse(result.isNamedCase)
        assertEquals(path, result.testCase.testId)
        assertEquals(
            "My Movie.mkv",
            result.testCase.sourceLabel,
        )
    }

    @Test
    fun malformedNamedMarkerSafelyFallsBackToNormalPlayback() {
        val path = "/Movies/CVTEST____Broken.mkv"

        val result = resolvePlaybackCompatibilityTestCase(
            path = path,
            displayName = "CVTEST____Broken.mkv",
        )

        assertFalse(result.isNamedCase)
        assertEquals(path, result.testCase.testId)
    }

    @Test
    fun normalizerRejectsEmptyOrSymbolOnlyIds() {
        assertEquals(
            null,
            normalizeCompatibilityTestId(" !!! "),
        )
    }
}
