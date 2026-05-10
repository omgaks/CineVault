package com.sole.cinevault

import android.content.Context
import android.provider.MediaStore

fun scanVideos(context: Context): List<VideoFile> {

    val videoList = mutableListOf<VideoFile>()

    val projection = arrayOf(
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DATA
    )

    val cursor = context.contentResolver.query(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        projection,
        null,
        null,
        null
    )

    cursor?.use {

        val nameColumn =
            it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)

        val pathColumn =
            it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

        while (it.moveToNext()) {

            val name = it.getString(nameColumn)
            val path = it.getString(pathColumn)

            val lower = name.lowercase()

            if (
                lower.endsWith(".mp4") ||
                lower.endsWith(".mkv") ||
                lower.endsWith(".avi") ||
                lower.endsWith(".mov") ||
                lower.endsWith(".m4v") ||
                lower.endsWith(".webm") ||
                lower.endsWith(".flv") ||
                lower.endsWith(".mpeg") ||
                lower.endsWith(".mpg") ||
                lower.endsWith(".ts") ||
                lower.endsWith(".m2ts") ||
                lower.endsWith(".wmv")
            ) {

                videoList.add(
                    VideoFile(
                        name = name,
                        path = path
                    )
                )
            }
        }
    }

    return videoList
}