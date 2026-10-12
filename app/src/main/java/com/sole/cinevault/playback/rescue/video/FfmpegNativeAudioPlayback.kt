package com.sole.cinevault.playback.rescue.video

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Native FFmpeg audio output engine. Runs on its own worker and owns AudioTrack.
 * This does not yet transfer Media3 session ownership or enable automatic rescue.
 */
internal class FfmpegNativeAudioPlayback {
    private val stopped = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)
    private val pendingSeek = AtomicLong(-1L)

    fun stop() { stopped.set(true) }
    fun pause() { paused.set(true) }
    fun resume() { paused.set(false) }
    fun seekTo(positionMs: Long) {
        require(positionMs >= 0L)
        pendingSeek.set(positionMs)
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
            maxOf(minBytes, 19200),
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE,
        )
        try {
            check(track.state == AudioTrack.STATE_INITIALIZED)
            FfmpegNativeAudioDecoder.open(path).use { decoder ->
                while (!stopped.get()) {
                    val seek = pendingSeek.getAndSet(-1L)
                    if (seek >= 0L) {
                        track.pause()
                        track.flush()
                        decoder.seekTo(seek)
                    }
                    if (paused.get()) {
                        track.pause()
                        Thread.sleep(10)
                        continue
                    }
                    track.play()
                    val pcm = decoder.nextPcm() ?: break
                    var offset = 0
                    while (offset < pcm.size && !stopped.get()) {
                        if (paused.get() || pendingSeek.get() >= 0L) break
                        val written = track.write(pcm, offset, pcm.size - offset,
                            AudioTrack.WRITE_NON_BLOCKING)
                        check(written >= 0) { "AudioTrack write failed: $written" }
                        if (written == 0) Thread.sleep(5) else offset += written
                    }
                }
            }
        } finally {
            track.pause()
            track.flush()
            track.release()
        }
    }
}
