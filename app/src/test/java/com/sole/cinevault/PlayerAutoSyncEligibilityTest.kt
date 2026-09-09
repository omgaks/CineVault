package com.sole.cinevault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerAutoSyncEligibilityTest {

    @Test
    fun noPrimarySubtitle_isUnavailable() {
        assertFalse(
            isPlayerAutoSyncAvailable(
                primarySubtitleName = null,
                isStreamMedia = false,
                videoPath = "/movies/movie.mkv",
                localFileExists = { true },
            )
        )
    }

    @Test
    fun supportedSrtWithExistingLocalVideo_isAvailable() {
        assertTrue(
            isPlayerAutoSyncAvailable(
                primarySubtitleName = "movie.en.srt",
                isStreamMedia = false,
                videoPath = "/movies/movie.mkv",
                localFileExists = { true },
            )
        )
    }

    @Test
    fun contentVideoDoesNotRequireFilesystemExistence() {
        assertTrue(
            isPlayerAutoSyncAvailable(
                primarySubtitleName = "movie.srt",
                isStreamMedia = false,
                videoPath = "content://media/external/video/42",
                localFileExists = { false },
            )
        )
    }

    @Test
    fun streamMedia_isUnavailable() {
        assertFalse(
            isPlayerAutoSyncAvailable(
                primarySubtitleName = "movie.srt",
                isStreamMedia = true,
                videoPath = "https://example.com/movie.mkv",
                localFileExists = { true },
            )
        )
    }

    @Test
    fun smbVideo_isUnavailable() {
        assertFalse(
            isPlayerAutoSyncAvailable(
                primarySubtitleName = "movie.srt",
                isStreamMedia = false,
                videoPath = "smb://nas/movies/movie.mkv",
                localFileExists = { true },
            )
        )
    }

    @Test
    fun missingLocalVideo_isUnavailable() {
        assertFalse(
            isPlayerAutoSyncAvailable(
                primarySubtitleName = "movie.srt",
                isStreamMedia = false,
                videoPath = "/movies/missing.mkv",
                localFileExists = { false },
            )
        )
    }

    @Test
    fun unsupportedSubtitleFormat_isUnavailable() {
        assertFalse(
            isPlayerAutoSyncAvailable(
                primarySubtitleName = "movie.ass",
                isStreamMedia = false,
                videoPath = "/movies/movie.mkv",
                localFileExists = { true },
            )
        )
    }

    @Test
    fun unknownSubtitleFormat_isUnavailable() {
        assertFalse(
            isPlayerAutoSyncAvailable(
                primarySubtitleName = "movie.xyz",
                isStreamMedia = false,
                videoPath = "/movies/movie.mkv",
                localFileExists = { true },
            )
        )
    }
}
