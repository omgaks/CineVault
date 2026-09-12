package com.sole.cinevault

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.media3.exoplayer.ExoPlayer
import com.sole.cinevault.subtitles.*
import kotlinx.coroutines.CoroutineScope
import java.io.File

internal data class PlayerSubtitleFileRuntime(
    val pendingDeletePaths: MutableList<String>,
    val pendingDeleteFileState: MutableState<File?>,
    val snackbarHostState: SnackbarHostState,
    val deletionCoordinator: SubtitleDeletionCoordinator,
    val launchSrtPicker: (Array<String>) -> Unit,
)

/**
 * Slice 63: owns the subtitle-file lifecycle around deletion/undo and local import.
 *
 * The player screen still owns subtitle/player state; this runtime owns the
 * file-operation state and the two Activity Result launchers that belong to it.
 */
@Composable
internal fun rememberPlayerSubtitleFileRuntime(
    context: Context,
    scope: CoroutineScope,
    player: ExoPlayer,
    playbackNavigationCoordinator: PlaybackNavigationCoordinator,
    subtitleSearchCoordinator: SubtitleSearchCoordinator,
    trackUi: SubtitleTrackSelectionState,
    coreUi: SubtitleCoreUiState,
    searchUi: SubtitleAcquisitionUiState,
    currentVideoPath: String,
): PlayerSubtitleFileRuntime {
    val latestVideoPath = rememberUpdatedState(currentVideoPath)

    val pendingDeletePaths = remember { mutableStateListOf<String>() }
    val pendingDeleteFileState = remember { mutableStateOf<File?>(null) }
    val pendingConsentFileState = remember { mutableStateOf<File?>(null) }
    val detachedSubtitleForUndoState = remember { mutableStateOf<File?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val deleteConsentLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            val consentedFile = pendingConsentFileState.value
            if (result.resultCode != Activity.RESULT_OK) {
                if (consentedFile != null) {
                    pendingDeletePaths.remove(consentedFile.absolutePath)
                }
                Toast.makeText(context, "Delete cancelled", Toast.LENGTH_SHORT).show()
            }
            pendingConsentFileState.value = null
        }

    val deletionCoordinator = remember(player, playbackNavigationCoordinator) {
        SubtitleDeletionCoordinator(
            context = context,
            scope = scope,
            pendingDeletePaths = pendingDeletePaths,
            snackbarHostState = snackbarHostState,
            deleteConsentLauncher = deleteConsentLauncher,
            setPendingConsentFile = { pendingConsentFileState.value = it },
            setPendingDeleteConfirmFile = { pendingDeleteFileState.value = it },
            onDeleteRequested = { file ->
                val isActive =
                    trackUi.selectedKey == "local:${file.absolutePath}" ||
                        trackUi.selectedKey == "downloaded" ||
                        trackUi.originalUri?.path == file.absolutePath ||
                        trackUi.primaryUri?.path == file.absolutePath

                if (isActive) {
                    detachedSubtitleForUndoState.value = file
                    val resumeAt = playerSafeResumePosition(player.currentPosition)
                    playbackNavigationCoordinator.playCurrentVideoWithSubtitle(
                        null,
                        resumeAt,
                        false,
                    )
                    trackUi.primaryUri = null
                    trackUi.originalUri = null
                    trackUi.selectedKey = "off"
                    trackUi.selectedLabel = ""
                    trackUi.selectedSource = ""
                    coreUi.subtitlesEnabled = false
                }
            },
            onDeleteUndone = { file ->
                if (
                    detachedSubtitleForUndoState.value?.absolutePath == file.absolutePath &&
                    file.exists()
                ) {
                    val resumeAt = playerSafeResumePosition(player.currentPosition)
                    val fileUri = Uri.fromFile(file)
                    coreUi.subtitlesEnabled = true
                    trackUi.primaryUri = fileUri
                    trackUi.originalUri = fileUri
                    trackUi.selectedKey = "local:${file.absolutePath}"
                    trackUi.selectedLabel = file.nameWithoutExtension
                    trackUi.selectedSource = "Local"
                    playbackNavigationCoordinator.playCurrentVideoWithSubtitle(
                        fileUri,
                        resumeAt,
                        true,
                    )
                }
                detachedSubtitleForUndoState.value = null
            },
        )
    }

    val localImportCoordinator = remember {
        SubtitleLocalImportCoordinator(
            context = context,
            scope = scope,
            getCurrentVideoPath = { latestVideoPath.value },
            getPreferredLanguage = {
                coreUi.behaviorPrefs.preferredLanguages.firstOrNull() ?: "en"
            },
            applyImportedSubtitle = { imported ->
                subtitleSearchCoordinator.applyImportedWebsiteSubtitle(imported)
            },
            setPendingImportCandidates = { result ->
                searchUi.pendingImportCandidates = result
            },
        )
    }

    val srtPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            localImportCoordinator.importPickedUri(uri)
        }

    return PlayerSubtitleFileRuntime(
        pendingDeletePaths = pendingDeletePaths,
        pendingDeleteFileState = pendingDeleteFileState,
        snackbarHostState = snackbarHostState,
        deletionCoordinator = deletionCoordinator,
        launchSrtPicker = { mimeTypes -> srtPickerLauncher.launch(mimeTypes) },
    )
}
