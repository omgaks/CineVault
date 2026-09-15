package com.sole.cinevault

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
internal fun AudioRuntimeRescueVideoScopeEffect(videoPath: String) {
    LaunchedEffect(videoPath) {
        AudioRuntimeRescueController.resetForVideo(videoPath)
    }
}
