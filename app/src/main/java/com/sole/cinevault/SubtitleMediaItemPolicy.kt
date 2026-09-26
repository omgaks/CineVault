package com.sole.cinevault

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import com.sole.cinevault.subtitles.detectSubtitleFormat

/**
 * Builds the same video MediaItem with an optional external subtitle.
 * Kept pure apart from subtitle-format detection so navigation can replace
 * the current item without deliberately tearing down the PlayerView/surface.
 */
internal fun buildPlaybackMediaItem(
    videoUri: String,
    subtitleUri: Uri?,
): MediaItem {
    val builder = MediaItem.Builder().setUri(videoUri)
    if (subtitleUri != null) {
        val detectedFormat = detectSubtitleFormat(subtitleUri)
        val subtitleMimeType = detectedFormat.mimeType ?: MimeTypes.APPLICATION_SUBRIP
        builder.setSubtitleConfigurations(
            listOf(
                MediaItem.SubtitleConfiguration.Builder(subtitleUri)
                    .setMimeType(subtitleMimeType)
                    .setLanguage("en")
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
            )
        )
    }
    return builder.build()
}
