package com.sole.cinevault.playback.rescue.video

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.PlaybackParams
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicInteger

/** Native PCM output; only its worker touches AudioTrack or the decoder. */
internal class FfmpegNativeAudioPlayback(
    private val restoreLanguage: String? = null,
    private val restoreChannels: Int = -1,
) {
    private val stopped = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)
    private val pendingSeek = AtomicLong(-1L)
    private val pendingStream = AtomicInteger(-1)
    @Volatile private var trackSnapshot = FfmpegNativeAudioTrackSnapshot()
    @Volatile private var volume = 1f
    private var restoredStream: Int? = null
    @Volatile private var requestedSpeed = 1f
    @Volatile private var appliedSpeed: Float? = null
    @Volatile private var lastPositionMs = 0L
    @Volatile private var clockPositionMs: Long? = null
    @Volatile private var mediaDurationMs: Long? = null

    fun restoreAudioStream(streamIndex: Int?) { restoredStream = streamIndex }
    fun audioTracks(): FfmpegNativeAudioTrackSnapshot = trackSnapshot
    fun selectAudioTrack(streamIndex: Int): Boolean {
        if (stopped.get() || trackSnapshot.tracks.none { it.streamIndex == streamIndex }) return false
        pendingStream.set(streamIndex)
        clockPositionMs = null
        return true
    }

    fun positionMs(): Long = lastPositionMs
    fun playbackClockMs(): Long? = clockPositionMs
    fun durationMs(): Long? = mediaDurationMs
    fun playbackSpeed(): Float? = appliedSpeed
    fun setSpeed(value: Float) { require(value.isFinite()); requestedSpeed = value.coerceIn(0.25f, 3f) }
    fun setVolume(value: Float) { volume = value.coerceIn(0f, 1f) }
    fun stop() { stopped.set(true) }
    fun pause() { paused.set(true) }
    fun resume() { paused.set(false) }
    fun seekTo(positionMs: Long) {
        require(positionMs >= 0L)
        lastPositionMs = positionMs
        pendingSeek.set(positionMs)
        // The video worker must not pace a new seek against the previous clock.
        clockPositionMs = null
    }

    fun play(path: String) {
        if (stopped.get()) return
        val minBytes = AudioTrack.getMinBufferSize(
            48000, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT
        )
        check(minBytes > 0) { "AudioTrack buffer unavailable" }
        val track = AudioTrack(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build(),
            AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(48000).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build(),
            maxOf(minBytes, 19200), AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE,
        )
        val clock = FfmpegNativeAudioClock()
        var submittedFrames = 0L
        var appliedVolume = -1f
        fun updateClock() {
            lastPositionMs = clock.positionMs(track.playbackHeadPosition)
            clockPositionMs = if (pendingSeek.get() < 0L && pendingStream.get() < 0) lastPositionMs else null
        }
        fun updateSpeed() {
            val wanted = requestedSpeed
            if (appliedSpeed == wanted) return
            try {
                track.playbackParams = PlaybackParams().allowDefaults().setSpeed(wanted).setPitch(1f)
                appliedSpeed = track.playbackParams.speed
            } catch (_: IllegalArgumentException) {
                // A device may reject an extreme rate. Preserve playback at 1x.
                track.playbackParams = PlaybackParams().allowDefaults().setSpeed(1f).setPitch(1f)
                requestedSpeed = 1f
                appliedSpeed = 1f
            }
        }
        fun updateVolume() {
            val requested = volume
            if (requested != appliedVolume) {
                track.setVolume(requested)
                appliedVolume = requested
            }
        }
        try {
            check(track.state == AudioTrack.STATE_INITIALIZED)
            var decoder = FfmpegNativeAudioDecoder.open(path)
            try {
                val tracks = decoder.tracks()
                val restored = restoredStream?.takeIf { requested -> tracks.any { it.streamIndex == requested } }
                    ?: FfmpegNativeAudioTracks.restoreStream(tracks, restoreLanguage, restoreChannels)
                if (restored != null && restored != decoder.selectedStreamIndex()) {
                    val replacement = FfmpegNativeAudioDecoder.open(path, restored)
                    decoder.close()
                    decoder = replacement
                }
                trackSnapshot = FfmpegNativeAudioTrackSnapshot(tracks, decoder.selectedStreamIndex())
                mediaDurationMs = decoder.durationMs().takeIf { it > 0L }
                var pcm: ByteArray? = null
                var offset = 0
                while (!stopped.get()) {
                    val selectedStream = pendingStream.getAndSet(-1)
                    val requestedSeek = pendingSeek.getAndSet(-1L)
                    val seek = if (requestedSeek >= 0L) requestedSeek else if (selectedStream >= 0) lastPositionMs else -1L
                    if (selectedStream >= 0) {
                        // Open first so an invalid choice never destroys the current decoder.
                        val replacement = FfmpegNativeAudioDecoder.open(path, selectedStream)
                        decoder.close()
                        decoder = replacement
                        trackSnapshot = trackSnapshot.copy(selectedStreamIndex = selectedStream)
                    }
                    if (seek >= 0L) {
                        track.pause()
                        track.flush()
                        decoder.seekTo(seek)
                        clock.reset(seek)
                        submittedFrames = 0L
                        pcm = null
                        offset = 0
                    }
                    updateClock()
                    updateVolume()
                    updateSpeed()
                    if (paused.get()) {
                        track.pause()
                        Thread.sleep(10)
                        continue
                    }
                    track.play()
                    if (pcm == null) {
                        pcm = decoder.nextPcm()
                        offset = 0
                        if (pcm == null) {
                            // Drain queued sound before releasing the track. A
                            // queued seek or pause still interrupts this wait.
                            while (!stopped.get() && pendingSeek.get() < 0L && pendingStream.get() < 0) {
                                updateClock()
                                updateVolume()
                                updateSpeed()
                                if (paused.get()) track.pause() else track.play()
                                if (clock.playedFrames >= submittedFrames) break
                                Thread.sleep(5)
                            }
                            if (pendingSeek.get() >= 0L || pendingStream.get() >= 0) continue
                            break
                        }
                    }
                    val bytes = checkNotNull(pcm)
                    if (bytes.isEmpty()) {
                        pcm = null
                        continue
                    }
                    val written = track.write(bytes, offset, bytes.size - offset, AudioTrack.WRITE_NON_BLOCKING)
                    check(written >= 0) { "AudioTrack write failed: $written" }
                    if (written == 0) Thread.sleep(5) else {
                        offset += written
                        submittedFrames += written / 4
                        if (offset == bytes.size) pcm = null
                    }
                }
                updateClock()
            } finally {
                decoder.close()
            }
        } finally {
            clockPositionMs = null
            try {
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.pause()
                    track.flush()
                }
            } finally {
                track.release()
            }
        }
    }
}
