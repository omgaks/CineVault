package com.sole.cinevault.subtitles

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Slice 40: owns loading generated / translated subtitle files for the
 * currently playing video.
 *
 * VideoPlayerScreen keeps the LaunchedEffect so refresh cancellation remains
 * Compose lifecycle-aware; this class owns the IO/store call.
 */
class GeneratedSubtitleLibraryCoordinator(
    private val context: Context,
    private val getCurrentVideoPath: () -> String,
) {
    suspend fun loadForCurrentVideo(): List<GeneratedSubtitleFile> =
        withContext(Dispatchers.IO) {
            GeneratedSubtitleStore.listForVideo(
                context = context,
                videoPath = getCurrentVideoPath(),
            )
        }
}
