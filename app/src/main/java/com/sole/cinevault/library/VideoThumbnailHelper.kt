package com.sole.cinevault.library

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
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

    // FIX: LruCache's default sizeOf() returns 1 per entry regardless of
    // what that entry actually is — so LruCache<_, _>(5) meant "keep 5
    // entries" with zero regard for the fact that each entry here is a
    // list of up to 120 real bitmaps. Same problem for the thumbnail
    // cache at 80 entries of full poster-sized bitmaps. Together these
    // could legitimately approach the app's entire heap ceiling before
    // Auto-Sync even starts. Both now size themselves by actual bitmap
    // memory (KB, via Bitmap.allocationByteCount) instead of entry count.
    private const val PREVIEW_CACHE_LIMIT_KB = 20 * 1024   // ~20MB
    private const val THUMBNAIL_CACHE_LIMIT_KB = 28 * 1024 // ~28MB

    private val previewMemoryCache = object : LruCache<String, List<PreviewFrame>>(PREVIEW_CACHE_LIMIT_KB) {
        override fun sizeOf(key: String, value: List<PreviewFrame>): Int =
            value.sumOf { it.bitmap.allocationByteCount / 1024 }.coerceAtLeast(1)
    }

    // Lets a memory-hungry one-off operation (Auto-Sync analysis) ask for
    // its headroom back before it starts, rather than competing with
    // whatever preview frames happen to already be cached from seeking.
    // Regenerates on demand afterward — nothing here is lost permanently,
    // just freed early.
    fun clearPreviewCache() {
        previewMemoryCache.evictAll()
    }

    // FIX: same entry-count-vs-actual-memory problem as previewMemoryCache
    // above — 80 entries of full poster-sized bitmaps had no ceiling on
    // actual bytes. Now sized by real bitmap memory (KB) instead.
    private val localThumbnailCache = object : LruCache<String, Bitmap>(THUMBNAIL_CACHE_LIMIT_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.allocationByteCount / 1024).coerceAtLeast(1)
    }

    suspend fun generatePreviewCache(
        context: Context,
        videoPath: String,
        durationMs: Long,
        frameCount: Int = 180
    ): List<PreviewFrame> = withContext(Dispatchers.IO) {
        if (durationMs <= 0L) return@withContext emptyList()

        val safeCount = frameCount.coerceIn(12, 120)
        val cacheKey = "${videoPath}_${durationMs}_${safeCount}"

        previewMemoryCache.get(cacheKey)?.let {
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
            previewMemoryCache.put(cacheKey, frames)
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

        previewMemoryCache.get(cacheKey)?.let {
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
            previewMemoryCache.put(cacheKey, frames)
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

        localThumbnailCache.get(cacheKey)?.let {
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

                localThumbnailCache.put(cacheKey, bitmap)
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
                localThumbnailCache.put(cacheKey, bitmap)
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
