package com.sole.cinevault.playback.rescue.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FfmpegNativeAudioTracksTest {
    @Test fun streamIdentitySurvivesVideoAndSubtitleStreamsBetweenAudioRows() {
        val tracks = FfmpegNativeAudioTracks.decode(arrayOf("1", "eng", "aac", "2", "5", "hin", "ac3", "6"))
        assertEquals(listOf(1, 5), tracks.map { it.streamIndex })
        assertEquals(5, FfmpegNativeAudioTracks.restoreStream(tracks, "hi", 6))
    }

    @Test fun malformedOrTruncatedNativeRecordsCannotBecomeMenuChoices() {
        val tracks = FfmpegNativeAudioTracks.decode(arrayOf("-1", "en", "aac", "2", "3", "hi", "ac3", "-2", "4", "en"))
        assertEquals(emptyList<FfmpegNativeAudioTrack>(), tracks)
    }

    @Test fun duplicateLanguageUsesChannelIdentityWhenItIsUnique() {
        val tracks = listOf(FfmpegNativeAudioTrack(1, "eng", "aac", 2), FfmpegNativeAudioTrack(4, "eng", "ac3", 6))
        assertEquals(4, FfmpegNativeAudioTracks.restoreStream(tracks, "en", 6))
    }

    @Test fun ambiguousCommentaryTracksDoNotSilentlyReplaceNativeDefault() {
        val tracks = listOf(FfmpegNativeAudioTrack(1, "en", "aac", 2), FfmpegNativeAudioTrack(4, "en", "aac", 2))
        assertNull(FfmpegNativeAudioTracks.restoreStream(tracks, "en", 2))
    }

    @Test fun missingLanguageKeepsNativeDefaultInsteadOfGuessing() {
        val tracks = listOf(FfmpegNativeAudioTrack(3, "und", "aac", 2))
        assertNull(FfmpegNativeAudioTracks.restoreStream(tracks, null, 2))
        assertNull(FfmpegNativeAudioTracks.restoreStream(tracks, "und", 2))
    }

    @Test fun repeatedStreamRecordsDoNotProduceDuplicateMenuRows() {
        val tracks = FfmpegNativeAudioTracks.decode(arrayOf("3", "", "aac", "2", "3", "", "aac", "2"))
        assertEquals(listOf(FfmpegNativeAudioTrack(3, "und", "aac", 2)), tracks)
    }
}
