package com.sole.cinevault

import com.sole.cinevault.subtitles.detectSubtitleFormat
import com.sole.cinevault.subtitles.supportsCustomTextPipeline
import java.io.File

/**
 * Slice 33: single source of truth for whether the player can offer Auto-Sync.
 *
 * Kept Android-free so the decision is testable with plain JUnit4.
 */
internal fun isPlayerAutoSyncAvailable(
    primarySubtitleName: String?,
    isStreamMedia: Boolean,
    videoPath: String,
    localFileExists: (String) -> Boolean = { File(it).exists() },
): Boolean {
    val subtitleName = primarySubtitleName ?: return false

    if (!supportsCustomTextPipeline(detectSubtitleFormat(subtitleName))) {
        return false
    }

    if (isStreamMedia) return false

    if (videoPath.startsWith("smb://", ignoreCase = true)) {
        return false
    }

    return videoPath.startsWith("content://", ignoreCase = true) ||
        localFileExists(videoPath)
}
