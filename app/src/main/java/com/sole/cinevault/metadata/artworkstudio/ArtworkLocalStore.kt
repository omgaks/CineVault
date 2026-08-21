package com.sole.cinevault.metadata.artworkstudio

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.sole.cinevault.metadata.ArtworkKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ArtworkLocalStore {
    private fun directory(context: Context): File =
        // Artwork can be a frame captured from private/Secret media. Keep it
        // out of Android cloud backup while still persisting across restarts.
        File(context.noBackupFilesDir, "artwork").also { it.mkdirs() }

    private fun destination(context: Context, videoPath: String, kind: ArtworkKind): File {
        val stableName = videoPath.hashCode().toUInt().toString(16)
        return File(
            directory(context),
            "${stableName}_${kind.name.lowercase()}_${System.currentTimeMillis()}.jpg"
        )
    }

    private fun removeOlderVersions(
        context: Context,
        videoPath: String,
        kind: ArtworkKind,
        keep: File
    ) {
        val stableName = videoPath.hashCode().toUInt().toString(16)
        val prefix = "${stableName}_${kind.name.lowercase()}_"
        directory(context).listFiles()
            ?.filter { it != keep && it.name.startsWith(prefix) }
            ?.forEach { it.delete() }
    }

    suspend fun importImage(
        context: Context,
        videoPath: String,
        kind: ArtworkKind,
        source: Uri
    ): ArtworkStudioResult<String> = withContext(Dispatchers.IO) {
        try {
            val output = destination(context, videoPath, kind)
            context.contentResolver.openInputStream(source)?.use { input ->
                FileOutputStream(output).use { target -> input.copyTo(target) }
            } ?: return@withContext ArtworkStudioResult.Failure("The selected image could not be opened")
            removeOlderVersions(context, videoPath, kind, output)
            ArtworkStudioResult.Success(Uri.fromFile(output).toString())
        } catch (e: Exception) {
            ArtworkStudioResult.Failure(e.message ?: "The selected image could not be saved")
        }
    }

    suspend fun captureFrame(
        context: Context,
        videoPath: String,
        kind: ArtworkKind
    ): ArtworkStudioResult<String> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            val uri = Uri.parse(videoPath)
            if (uri.scheme.isNullOrBlank()) retriever.setDataSource(videoPath)
            else retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val frameTimeUs = (durationMs.coerceAtLeast(1L) / 3L) * 1_000L
            val frame = retriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return@withContext ArtworkStudioResult.Failure("A frame could not be extracted from this video")
            val output = destination(context, videoPath, kind)
            FileOutputStream(output).use { stream ->
                frame.compress(Bitmap.CompressFormat.JPEG, 92, stream)
            }
            frame.recycle()
            removeOlderVersions(context, videoPath, kind, output)
            ArtworkStudioResult.Success(Uri.fromFile(output).toString())
        } catch (e: Exception) {
            ArtworkStudioResult.Failure(e.message ?: "A frame could not be extracted from this source")
        } finally {
            retriever.release()
        }
    }
}
