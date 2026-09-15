package com.sole.cinevault

import android.net.Uri
import com.sole.cinevault.subtitles.SubtitleSourceResolver

internal data class AudioRescueSubtitleSnapshot(
    val primaryUri: Uri?,
    val originalUri: Uri?,
    val selectedKey: String?,
    val selectedLabel: String,
    val selectedSource: String,
    val primaryLanguage: String?,
)

internal fun SubtitleTrackSelectionState.audioRescueSubtitleSnapshot():
    AudioRescueSubtitleSnapshot =
    AudioRescueSubtitleSnapshot(
        primaryUri = primaryUri,
        originalUri = originalUri,
        selectedKey = selectedKey,
        selectedLabel = selectedLabel,
        selectedSource = selectedSource,
        primaryLanguage = primaryLanguage,
    )

internal fun AudioRescueSubtitleSnapshot.toSourceResolverSnapshot():
    SubtitleSourceResolver.Snapshot =
    SubtitleSourceResolver.Snapshot(
        primaryUri = primaryUri,
        originalUri = originalUri,
        selectedKey = selectedKey,
        selectedLabel = selectedLabel,
        selectedSource = selectedSource,
        primaryLanguage = primaryLanguage,
    )

/**
 * Verifies that CineVault's Compose-owned subtitle selection survives an
 * ExoPlayer audio-runtime rebuild independently of the player instance.
 */
internal fun AudioRescueSubtitleSnapshot.hasRestorableSubtitleSelection(): Boolean =
    primaryUri != null ||
        originalUri != null ||
        !selectedKey.isNullOrBlank()
