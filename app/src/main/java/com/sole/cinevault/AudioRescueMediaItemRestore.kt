package com.sole.cinevault

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import com.sole.cinevault.subtitles.detectSubtitleFormat

/**
 * Rebuilds the rescued MediaItem only when an external/local subtitle needs
 * explicit reattachment. The original MediaItem remains the base so its video
 * URI and any other media configuration are preserved.
 */
internal fun AudioRuntimeRescueHandover.mediaItemWithRestoredSubtitle():
    MediaItem {
    val restore = subtitleRestore() ?: return mediaItem
    val detectedFormat = detectSubtitleFormat(restore.uri)
    val mimeType = detectedFormat.mimeType ?: MimeTypes.APPLICATION_SUBRIP

    val subtitleConfiguration =
        MediaItem.SubtitleConfiguration.Builder(restore.uri)
            .setMimeType(mimeType)
            .setLanguage(restore.language ?: "en")
            .setLabel(restore.label)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()

    return mediaItem
        .buildUpon()
        .setSubtitleConfigurations(listOf(subtitleConfiguration))
        .build()
}
