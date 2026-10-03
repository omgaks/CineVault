package com.sole.cinevault

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import com.sole.cinevault.subtitles.detectSubtitleFormat

/**
 * Builds the same video MediaItem with an optional external subtitle.
 * A subtitle explicitly chosen by the user is marked DEFAULT so Media3
 * selects the attached external track instead of leaving a preferred
 * embedded track active. The caller still owns the text-renderer policy.
 */
internal fun buildPlaybackMediaItem(
    videoUri: String,
    subtitleUri: Uri?,
    subtitleLanguage: String? = null,
    subtitleId: String = "cinevault-external",
): MediaItem {
    val builder = MediaItem.Builder().setUri(videoUri)
    if (subtitleUri != null) {
        val detectedFormat = detectSubtitleFormat(subtitleUri)
        val subtitleMimeType = detectedFormat.mimeType ?: MimeTypes.APPLICATION_SUBRIP
        builder.setSubtitleConfigurations(
            listOf(
                MediaItem.SubtitleConfiguration.Builder(subtitleUri)
                    .setMimeType(subtitleMimeType)
                    .setLanguage(subtitleLanguage)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .setId(subtitleId)
                    .build()
            )
        )
    }
    return builder.build()
}
