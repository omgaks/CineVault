package com.sole.cinevault.subtitles

import android.content.Context
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.sole.cinevault.library.FileManagementHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

// FIX: third slice of extracting VideoPlayerScreen()'s behavior out of
// its own body (see AutoSyncCoordinator.kt and
// SubtitleSyncToolsCoordinator.kt for the first two slices and the full
// reasoning). Small, self-contained cluster — the whole "delete a
// subtitle file, with an Undo snackbar" flow.
//
// pendingDeletePaths and snackbarHostState/deleteConsentLauncher are
// passed as direct typed references (SnapshotStateList, SnackbarHostState,
// ActivityResultLauncher are all stable objects once created, same
// reasoning as passing coreUi/driftUi directly in the previous slice) —
// only pendingConsentFile/pendingDeleteConfirmFile need setter lambdas,
// since those are plain composable-local `var`s, not stable object
// references.
class SubtitleDeletionCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val pendingDeletePaths: SnapshotStateList<String>,
    private val snackbarHostState: SnackbarHostState,
    private val deleteConsentLauncher: ActivityResultLauncher<IntentSenderRequest>,
    private val setPendingConsentFile: (File?) -> Unit,
    private val setPendingDeleteConfirmFile: (File?) -> Unit
) {
    private fun finalizeSubtitleDeletion(file: File) {
        setPendingConsentFile(file)
        FileManagementHelper.deleteFile(
            context = context,
            file = file,
            onNeedsConsent = { intentSender -> deleteConsentLauncher.launch(IntentSenderRequest.Builder(intentSender).build()) },
            onDeleted = { setPendingConsentFile(null) },
            onFailed = { e ->
                pendingDeletePaths.remove(file.absolutePath)
                setPendingConsentFile(null)
                Toast.makeText(context, "Couldn't delete: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Hides the file immediately and shows the amber Undo snackbar. Real
    // deletion only happens if the snackbar times out or is dismissed
    // without the person tapping Undo.
    fun deleteWithUndo(file: File) {
        pendingDeletePaths.add(file.absolutePath)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Subtitle deleted",
                actionLabel = "UNDO",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                pendingDeletePaths.remove(file.absolutePath)
            } else {
                finalizeSubtitleDeletion(file)
            }
        }
    }

    // Entry point every delete button in this screen calls — opens the
    // styled warning dialog rather than deleting immediately.
    fun requestDeleteSubtitle(file: File) {
        setPendingDeleteConfirmFile(file)
    }
}
