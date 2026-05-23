package com.sole.cinevault

import java.io.File

fun findSubtitleFile(videoPath: String): File? {
    val videoFile = File(videoPath)
    val folder = videoFile.parentFile ?: return null
    val baseName = videoFile.nameWithoutExtension

    val exactSubtitle = File(folder, "$baseName.srt")

    if (exactSubtitle.exists()) {
        return exactSubtitle
    }

    return folder.listFiles()?.firstOrNull {
        it.extension.equals("srt", ignoreCase = true) &&
                it.nameWithoutExtension.contains(baseName, ignoreCase = true)
    }
}