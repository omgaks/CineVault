package com.sole.cinevault

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File

object FileManagementHelper {

    /**
     * Attempts to delete a file. Returns immediately via [onDeleted] if the file
     * is app-owned or on API < 29. On API 30+, if the file lives in shared storage
     * and needs user consent, [onNeedsConsent] is called with an IntentSender —
     * launch it via an ActivityResultLauncher (StartIntentSenderForResult).
     */
    fun deleteFile(
        context: Context,
        file: File,
        onNeedsConsent: (IntentSender) -> Unit,
        onDeleted: () -> Unit,
        onFailed: (Exception) -> Unit
    ) {
        try {
            // Fast path — works for files the app created/owns, or below API 29
            if (file.delete()) {
                onDeleted()
                return
            }
        } catch (_: SecurityException) {
            // fall through to MediaStore path below
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val uri = getMediaStoreUriForFile(context, file) ?: run {
                    onFailed(Exception("File not found in MediaStore: ${file.path}"))
                    return
                }
                val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, listOf(uri))
                onNeedsConsent(pendingIntent.intentSender)
            } catch (e: Exception) {
                onFailed(e)
            }
        } else {
            onFailed(Exception("Unable to delete file: ${file.path}"))
        }
    }

    private fun getMediaStoreUriForFile(context: Context, file: File): Uri? {
        val projection = arrayOf(MediaStore.Files.FileColumns._ID)
        val selection = "${MediaStore.Files.FileColumns.DATA} = ?"
        val selectionArgs = arrayOf(file.absolutePath)
        val queryUri = MediaStore.Files.getContentUri("external")

        context.contentResolver.query(queryUri, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                return Uri.withAppendedPath(queryUri, id.toString())
            }
        }
        return null
    }
}
