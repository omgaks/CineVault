package com.sole.cinevault.subtitles

import android.net.Uri
import java.io.File

/**
 * Resolves the readable subtitle source currently selected by CineVault.
 *
 * Translation must not depend on Whisper or on an AI-generated subtitle.
 * This resolver accepts the normal Subtitle Studio/download/local paths
 * represented by SubtitleTrackSelectionState.
 */
object SubtitleSourceResolver {

    data class Snapshot(
        val primaryUri: Uri?,
        val originalUri: Uri?,
        val selectedKey: String?,
        val selectedLabel: String,
        val selectedSource: String,
        val primaryLanguage: String?,
    )

    data class Resolved(
        val uri: Uri,
        val language: String?,
        val label: String,
        val source: String,
    )

    fun resolve(snapshot: Snapshot): Resolved? {
        snapshot.primaryUri?.let {
            return Resolved(
                uri = it,
                language = snapshot.primaryLanguage,
                label = snapshot.selectedLabel.ifBlank { "Current subtitle" },
                source = snapshot.selectedSource.ifBlank { "Current" },
            )
        }

        // Sync/drift operations may leave the currently readable subtitle
        // represented by originalUri even when primaryUri is unavailable.
        snapshot.originalUri?.let {
            return Resolved(
                uri = it,
                language = snapshot.primaryLanguage,
                label = snapshot.selectedLabel.ifBlank { "Current subtitle" },
                source = snapshot.selectedSource.ifBlank { "Current" },
            )
        }

        // Last-resort recovery for a normal local subtitle selected through
        // the track selector. This does not guess downloaded cache paths.
        val key = snapshot.selectedKey
        if (key != null && key.startsWith("local:")) {
            val path = key.removePrefix("local:")
            val file = File(path)
            if (file.exists() && file.isFile) {
                return Resolved(
                    uri = Uri.fromFile(file),
                    language = snapshot.primaryLanguage,
                    label = snapshot.selectedLabel.ifBlank { file.nameWithoutExtension },
                    source = snapshot.selectedSource.ifBlank { "Local file" },
                )
            }
        }

        return null
    }
}
