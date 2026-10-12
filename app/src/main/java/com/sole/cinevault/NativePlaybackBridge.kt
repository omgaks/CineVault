package com.sole.cinevault

internal data class NativePlaybackSnapshot(
    val positionMs: Long,
    val durationMs: Long,
    val playing: Boolean,
    val ended: Boolean,
    val speed: Float,
    val volume: Float = 1f,
)

/** Main-thread bridge shared by screen, PiP and MediaSession; scoped to one raw player. */
internal class NativePlaybackBridge(
    val owner: Any,
    private val readSnapshot: () -> NativePlaybackSnapshot?,
    private val onPlay: () -> Unit,
    private val onPause: () -> Unit,
    private val onSeek: (Long) -> Unit,
    private val onSpeed: (Float) -> Unit,
    private val onStop: () -> Unit,
    private val onRelease: () -> Unit,
    private val onVolume: (Float) -> Unit = {},
) {
    private val listeners = mutableSetOf<(NativePlaybackSnapshot?, NativePlaybackSnapshot?, Boolean) -> Unit>()
    private var previous: NativePlaybackSnapshot? = null
    private var released = false
    val snapshot: NativePlaybackSnapshot? get() = if (released) null else readSnapshot()

    fun addListener(listener: (NativePlaybackSnapshot?, NativePlaybackSnapshot?, Boolean) -> Unit) { listeners += listener }
    fun removeListener(listener: (NativePlaybackSnapshot?, NativePlaybackSnapshot?, Boolean) -> Unit) { listeners -= listener }
    fun publish(seek: Boolean = false) {
        val next = snapshot
        val old = previous
        previous = next
        if (old != next || seek) listeners.toList().forEach { it(old, next, seek) }
    }
    fun play() { if (snapshot != null) { onPlay(); publish() } }
    fun pause() { if (snapshot != null) { onPause(); publish() } }
    fun seekTo(positionMs: Long) {
        val state = snapshot ?: return
        val bounded = if (state.durationMs > 0L) positionMs.coerceIn(0L, state.durationMs) else positionMs.coerceAtLeast(0L)
        onSeek(bounded)
        publish(seek = true)
    }
    fun setSpeed(speed: Float) {
        if (snapshot != null && speed.isFinite()) { onSpeed(speed.coerceIn(0.25f, 3f)); publish() }
    }
    fun setVolume(volume: Float) {
        if (snapshot != null && volume.isFinite()) { onVolume(volume.coerceIn(0f, 1f)); publish() }
    }
    fun stop() { if (snapshot != null) { onStop(); publish() } }
    fun release() {
        if (released) return
        onRelease()
        released = true
        publish()
        listeners.clear()
    }
}
