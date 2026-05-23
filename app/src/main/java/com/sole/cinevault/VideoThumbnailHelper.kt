package com.sole.cinevault

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun getVideoThumbnailAt(
    context: Context,
    videoPath: String,
    timeMs: Long
): Bitmap? {
    return withContext(Dispatchers.IO) {
        try {
            val retriever = MediaMetadataRetriever()

            if (videoPath.startsWith("content://")) {
                retriever.setDataSource(context, Uri.parse(videoPath))
            } else {
                retriever.setDataSource(videoPath)
            }

            val bitmap = retriever.getFrameAtTime(
                timeMs * 1000,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )

            retriever.release()

            bitmap?.let {
                Bitmap.createScaledBitmap(it, 260, 146, true)
            }
        } catch (e: Exception) {
            null
        }
    }
}