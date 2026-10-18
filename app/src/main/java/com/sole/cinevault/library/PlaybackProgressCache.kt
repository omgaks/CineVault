package com.sole.cinevault.library

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.sole.cinevault.VideoDurationDatabase

/**
 * In-memory copy of saved watch positions and durations, so library cards can
 * draw their progress bar without querying the database while scrolling.
 * Loaded once in the background; kept current by the save/clear functions.
 * State maps, so cards redraw when a value changes.
 */
object PlaybackProgressCache {
    val positions = mutableStateMapOf<String, Long>()
    val durations = mutableStateMapOf<String, Long>()

    @Volatile
    private var started = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun ensureLoaded(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        scope.launch {
            try {
                loadPlaybackPosition(app, "") // runs the one-time legacy migration
                val pos = PlaybackPositionDatabase.getInstance(app).playbackPositionDao().getAll()
                val dur = VideoDurationDatabase.getInstance(app).videoDurationDao().getAll()
                pos.forEach { if (positions[it.videoPath] == null) positions[it.videoPath] = it.positionMs }
                dur.forEach { if (durations[it.videoPath] == null) durations[it.videoPath] = it.durationMs }
            } catch (_: Exception) {
                started = false
            }
        }
    }
}
