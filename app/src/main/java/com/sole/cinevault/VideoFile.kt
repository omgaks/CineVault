package com.sole.cinevault

import androidx.compose.runtime.Immutable

@Immutable
data class VideoFile(
    val name: String,
    val path: String,
    val folderPath: String = ""
)