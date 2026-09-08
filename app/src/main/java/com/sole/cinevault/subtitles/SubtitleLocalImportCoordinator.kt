package com.sole.cinevault.subtitles

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Slice 26: owns the local subtitle picker handoff after Android returns a Uri.
 *
 * The ActivityResult launcher itself remains in VideoPlayerScreen because that
 * is Compose-owned lifecycle plumbing. Everything after selection lives here:
 * persistable permission, validated import, ZIP-alternative routing and user
 * feedback on failure.
 */
class SubtitleLocalImportCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val getCurrentVideoPath: () -> String,
    private val getPreferredLanguage: () -> String,
    private val applyImportedSubtitle: (ImportedSubtitle) -> Unit,
    private val setPendingImportCandidates: (SubtitleImportResult.Success) -> Unit,
) {
    fun importPickedUri(uri: Uri?) {
        if (uri == null) return

        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: Exception) {
            // Some providers do not offer persistable grants. The stream can
            // still be readable for this import, matching the previous behavior.
        }

        scope.launch {
            val result = context.contentResolver.openInputStream(uri)?.use { stream ->
                SubtitleImportEngine.import(
                    context = context,
                    input = stream,
                    suggestedName = uri.lastPathSegment,
                    releaseHint = getCurrentVideoPath(),
                    preferredLanguage = getPreferredLanguage(),
                )
            } ?: SubtitleImportResult.Failure(
                "CineVault couldn't open that file."
            )

            when (result) {
                is SubtitleImportResult.Success -> {
                    if (result.alternatives.isEmpty()) {
                        applyImportedSubtitle(result.selected)
                    } else {
                        setPendingImportCandidates(result)
                    }
                }

                is SubtitleImportResult.Failure -> {
                    Toast.makeText(
                        context,
                        result.userMessage,
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }
}
