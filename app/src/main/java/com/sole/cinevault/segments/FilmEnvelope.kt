package com.sole.cinevault.segments

import kotlin.math.log10
import kotlin.math.pow

/**
 * The film's loudness, one value per second, in dB. A value that has not been measured yet is NaN.
 * Pure Kotlin so the maths is unit-tested without a phone.
 *
 * It feeds two things: the real waveform on the seek bar, and the scene detector.
 */
object FilmEnvelope {
    const val HOP_MS = 1000

    fun secondsFor(durationMs: Long): Int = ((durationMs + HOP_MS - 1) / HOP_MS).toInt().coerceAtLeast(0)

    fun dbOfRms(rms: Float): Float = (20.0 * log10(rms.toDouble() + 1e-6)).toFloat()
    fun rmsOfDb(db: Float): Float = 10.0.pow(db / 20.0).toFloat()

    /** One byte per second: 0 = not measured, else dB from -90 to +12 in steps of 0.4. */
    fun encode(db: FloatArray): ByteArray = ByteArray(db.size) {
        val v = db[it]
        if (v.isNaN()) 0 else ((v + 90f) * 2.5f).toInt().coerceIn(1, 255).toByte()
    }

    fun decode(bytes: ByteArray): FloatArray = FloatArray(bytes.size) {
        val q = bytes[it].toInt() and 0xFF
        if (q == 0) Float.NaN else q / 2.5f - 90f
    }

    fun isMeasured(db: FloatArray, fromSec: Int, toSecExclusive: Int): Boolean {
        val from = fromSec.coerceIn(0, db.size)
        val to = toSecExclusive.coerceIn(from, db.size)
        if (to - from <= 0) return false
        for (i in from until to) if (db[i].isNaN()) return false
        return true
    }

    /** Detector input: the measured seconds as an envelope that starts at time zero. */
    fun toAudioEnvelope(db: FloatArray): AudioEnvelope =
        AudioEnvelope(0L, HOP_MS, FloatArray(db.size) { if (db[it].isNaN()) 0f else rmsOfDb(db[it]) })

    /**
     * Bar heights for the seek bar: 0..1 for each of [bars] bars, or -1 for a bar whose part of
     * the film has not been measured yet. Loudness is judged against the film's own loud end
     * (95th percentile) so a quiet drama and an action film both use the whole bar height.
     */
    fun barLevels(db: FloatArray, bars: Int): FloatArray {
        val out = FloatArray(bars) { -1f }
        if (db.isEmpty() || bars <= 0) return out
        val measured = db.filter { !it.isNaN() }
        if (measured.size < 8) return out
        val sorted = measured.sorted()
        val ceiling = sorted[((sorted.size - 1) * 0.95).toInt()]
        val range = 36f
        for (b in 0 until bars) {
            val from = (b.toLong() * db.size / bars).toInt()
            val to = ((b + 1).toLong() * db.size / bars).toInt().coerceAtLeast(from + 1).coerceAtMost(db.size)
            var sum = 0f
            var count = 0
            for (i in from until to) if (!db[i].isNaN()) { sum += db[i]; count++ }
            if (count == 0) continue
            val level = ((sum / count - (ceiling - range)) / range).coerceIn(0f, 1f)
            out[b] = level
        }
        return out
    }
}
