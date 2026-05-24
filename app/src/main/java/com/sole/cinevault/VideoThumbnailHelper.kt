package com.sole.cinevault

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

object VideoThumbnailHelper {

    data class PreviewFrame(
        val positionMs: Long,
        val bitmap: Bitmap
    )

    suspend fun generatePreviewCache(
        videoPath: String,
        durationMs: Long,
        frameCount: Int = 180
    ): List<PreviewFrame> = withContext(Dispatchers.IO) {
        val file = File(videoPath)
        if (!file.exists() || durationMs <= 0L) return@withContext emptyList()

        val retriever = MediaMetadataRetriever()
        val frames = mutableListOf<PreviewFrame>()

        try {
            retriever.setDataSource(videoPath)

            val safeCount = frameCount.coerceIn(24, 220)
            val option = if (safeCount <= 48) {
                // Fast warm-up frames. Slightly less exact, but appears quickly while cache is loading.
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            } else {
                // Dense cache. More accurate while dragging.
                MediaMetadataRetriever.OPTION_CLOSEST
            }

            for (i in 0 until safeCount) {
                val progress = i.toFloat() / (safeCount - 1).coerceAtLeast(1)
                val positionMs = (durationMs * progress).toLong().coerceIn(0L, durationMs)

                val original = retriever.getFrameAtTime(
                    positionMs * 1000L,
                    option
                )

                if (original != null) {
                    val scaled = original.scaleForPreview()
                    if (scaled !== original) {
                        try { original.recycle() } catch (_: Exception) {}
                    }
                    frames.add(PreviewFrame(positionMs, scaled))
                }
            }
        } catch (_: Exception) {
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        frames
    }

    fun nearestPreviewFrame(
        frames: List<PreviewFrame>,
        positionMs: Long
    ): Bitmap? {
        if (frames.isEmpty()) return null

        return frames.minByOrNull {
            abs(it.positionMs - positionMs)
        }?.bitmap
    }

    suspend fun generateFrameAtTime(
        videoPath: String,
        positionMs: Long
    ): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(videoPath)
        if (!file.exists()) return@withContext null

        val retriever = MediaMetadataRetriever()

        try {
            retriever.setDataSource(videoPath)

            val frame = retriever.getFrameAtTime(
                positionMs.coerceAtLeast(0L) * 1000L,
                MediaMetadataRetriever.OPTION_CLOSEST
            )

            frame?.scaleForPreview()
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    suspend fun generateLocalThumbnail(
        context: Context,
        videoPath: String
    ): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(videoPath)
        if (!file.exists()) return@withContext null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                return@withContext context.contentResolver.loadThumbnail(
                    Uri.fromFile(file),
                    Size(640, 900),
                    null
                )
            } catch (_: Exception) {
            }
        }

        val retriever = MediaMetadataRetriever()

        try {
            retriever.setDataSource(videoPath)

            val durationMs =
                retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?: 10_000L

            val frameTimeUs = durationMs * 1000L * 12L / 100L

            retriever.getFrameAtTime(
                frameTimeUs,
                MediaMetadataRetriever.OPTION_CLOSEST
            )?.scaleForPreview()
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun Bitmap.scaleForPreview(): Bitmap {
        val targetWidth = 240
        val targetHeight = 135

        if (width <= targetWidth && height <= targetHeight) return this

        return try {
            Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
        } catch (_: Exception) {
            this
        }
    }
}
