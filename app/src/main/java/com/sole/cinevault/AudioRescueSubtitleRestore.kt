package com.sole.cinevault

import android.net.Uri
import com.sole.cinevault.subtitles.SubtitleSourceResolver

internal data class AudioRescueSubtitleRestore(
    val uri: Uri,
    val language: String?,
    val label: String,
    val source: String,
)

internal fun AudioRescueSubtitleSnapshot.resolveForRestore():
    AudioRescueSubtitleRestore? =
    SubtitleSourceResolver.resolve(toSourceResolverSnapshot())
        ?.let { resolved ->
            AudioRescueSubtitleRestore(
                uri = resolved.uri,
                language = resolved.language,
                label = resolved.label,
                source = resolved.source,
            )
        }

/**
 * A rescue handover may carry subtitle state even when there is nothing that
 * needs to be reattached manually. Embedded selections remain represented by
 * the complete MediaItem/track state; readable external/local subtitles can
 * be resolved explicitly for the post-rebuild restore step.
 */
internal fun AudioRuntimeRescueHandover.subtitleRestore():
    AudioRescueSubtitleRestore? =
    subtitleSnapshot?.resolveForRestore()
