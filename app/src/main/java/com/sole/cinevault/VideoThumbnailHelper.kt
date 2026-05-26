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

    private val previewMemoryCache = linkedMapOf<String, List<PreviewFrame>>()
    private val localThumbnailCache = linkedMapOf<String, Bitmap>()

    suspend fun generatePreviewCache(
        context: Context,
        videoPath: String,
        durationMs: Long,
        frameCount: Int = 180
    ): List<PreviewFrame> = withContext(Dispatchers.IO) {
        if (durationMs <= 0L) return@withContext emptyList()

        val safeCount = frameCount.coerceIn(12, 120)
        val cacheKey = "${videoPath}_${durationMs}_${safeCount}"

        previewMemoryCache[cacheKey]?.let {
            return@withContext it
        }

        val retriever = MediaMetadataRetriever()
        val frames = mutableListOf<PreviewFrame>()

        try {
            if (videoPath.startsWith("content://")) {
                retriever.setDataSource(context, Uri.parse(videoPath))
            } else {
                val file = File(videoPath)
                if (!file.exists()) return@withContext emptyList()
                retriever.setDataSource(videoPath)
            }

            val option =
                if (safeCount <= 36) {
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                } else {
                    MediaMetadataRetriever.OPTION_CLOSEST
                }

            for (i in 0 until safeCount) {
                val progress = i.toFloat() / (safeCount - 1).coerceAtLeast(1)
                val positionMs = (durationMs * progress).toLong().coerceIn(0L, durationMs)

                val original =
                    retriever.getFrameAtTime(
                        positionMs * 1000L,
                        option
                    )

                if (original != null) {
                    val scaled = original.scaleForPreview()
                    if (scaled !== original) {
                        try {
                            original.recycle()
                        } catch (_: Exception) {
                        }
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

        if (frames.isNotEmpty()) {
            previewMemoryCache[cacheKey] = frames

            while (previewMemoryCache.size > 3) {
                val firstKey = previewMemoryCache.keys.firstOrNull()
                if (firstKey != null) {
                    previewMemoryCache.remove(firstKey)
                } else {
                    break
                }
            }
        }

        frames
    }

    suspend fun generatePreviewCache(
        videoPath: String,
        durationMs: Long,
        frameCount: Int = 180
    ): List<PreviewFrame> = withContext(Dispatchers.IO) {
        if (videoPath.startsWith("content://")) {
            return@withContext emptyList()
        }

        val file = File(videoPath)
        if (!file.exists() || durationMs <= 0L) return@withContext emptyList()

        val safeCount = frameCount.coerceIn(12, 120)
        val cacheKey = "${file.absolutePath}_${file.lastModified()}_${file.length()}_${durationMs}_${safeCount}"

        previewMemoryCache[cacheKey]?.let {
            return@withContext it
        }

        val retriever = MediaMetadataRetriever()
        val frames = mutableListOf<PreviewFrame>()

        try {
            retriever.setDataSource(videoPath)

            val option =
                if (safeCount <= 36) {
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                } else {
                    MediaMetadataRetriever.OPTION_CLOSEST
                }

            for (i in 0 until safeCount) {
                val progress = i.toFloat() / (safeCount - 1).coerceAtLeast(1)
                val positionMs = (durationMs * progress).toLong().coerceIn(0L, durationMs)

                val original =
                    retriever.getFrameAtTime(
                        positionMs * 1000L,
                        option
                    )

                if (original != null) {
                    val scaled = original.scaleForPreview()
                    if (scaled !== original) {
                        try {
                            original.recycle()
                        } catch (_: Exception) {
                        }
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

        if (frames.isNotEmpty()) {
            previewMemoryCache[cacheKey] = frames

            while (previewMemoryCache.size > 3) {
                val firstKey = previewMemoryCache.keys.firstOrNull()
                if (firstKey != null) {
                    previewMemoryCache.remove(firstKey)
                } else {
                    break
                }
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
        context: Context,
        videoPath: String,
        positionMs: Long
    ): Bitmap? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()

        try {
            if (videoPath.startsWith("content://")) {
                retriever.setDataSource(context, Uri.parse(videoPath))
            } else {
                val file = File(videoPath)
                if (!file.exists()) return@withContext null
                retriever.setDataSource(videoPath)
            }

            val frame =
                retriever.getFrameAtTime(
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

    suspend fun generateFrameAtTime(
        videoPath: String,
        positionMs: Long
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (videoPath.startsWith("content://")) {
            return@withContext null
        }

        val file = File(videoPath)
        if (!file.exists()) return@withContext null

        val retriever = MediaMetadataRetriever()

        try {
            retriever.setDataSource(videoPath)

            val frame =
                retriever.getFrameAtTime(
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
        val cacheKey = videoPath

        localThumbnailCache[cacheKey]?.let {
            return@withContext it
        }

        val uri =
            try {
                Uri.parse(videoPath)
            } catch (_: Exception) {
                null
            } ?: return@withContext null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val bitmap =
                    context.contentResolver.loadThumbnail(
                        uri,
                        Size(640, 900),
                        null
                    )

                localThumbnailCache[cacheKey] = bitmap
                trimLocalCache()
                return@withContext bitmap
            } catch (_: Exception) {
            }
        }

        val retriever = MediaMetadataRetriever()

        try {
            if (videoPath.startsWith("content://")) {
                retriever.setDataSource(context, uri)
            } else {
                val file = File(videoPath)
                if (!file.exists()) return@withContext null
                retriever.setDataSource(videoPath)
            }

            val durationMs =
                retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?: 10_000L

            val frameTimeUs = durationMs * 1000L * 12L / 100L

            val bitmap =
                retriever.getFrameAtTime(
                    frameTimeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST
                )?.scalePosterThumbnail()

            if (bitmap != null) {
                localThumbnailCache[cacheKey] = bitmap
                trimLocalCache()
            }

            bitmap
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun trimLocalCache() {
        while (localThumbnailCache.size > 80) {
            val firstKey = localThumbnailCache.keys.firstOrNull()
            if (firstKey != null) {
                localThumbnailCache.remove(firstKey)
            } else {
                break
            }
        }
    }

    private fun Bitmap.scaleForPreview(): Bitmap {
        val targetWidth = 280
        val targetHeight = 158

        if (width <= targetWidth && height <= targetHeight) return this

        return try {
            Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
        } catch (_: Exception) {
            this
        }
    }

    private fun Bitmap.scalePosterThumbnail(): Bitmap {
        val targetWidth = 640
        val targetHeight = 900

        if (width <= targetWidth && height <= targetHeight) return this

        return try {
            Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
        } catch (_: Exception) {
            this
        }
    }
}
