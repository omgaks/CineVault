package com.sole.cinevault

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever

fun getVideoThumbnail(path: String): Bitmap? {

    return try {

        val retriever = MediaMetadataRetriever()

        retriever.setDataSource(path)

        val bitmap =
            retriever.getFrameAtTime(
                1000000,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )

        retriever.release()

        bitmap

    } catch (e: Exception) {
        null
    }
}