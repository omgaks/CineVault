package com.sole.cinevault.picture

/** Safe one-shot playback recovery facts. */
object PictureHdrRecoveryPolicy {
    data class Snapshot(val positionMs: Long, val playWhenReady: Boolean)

    fun snapshot(positionMs: Long, playWhenReady: Boolean): Snapshot =
        Snapshot(positionMs.coerceAtLeast(0L), playWhenReady)

    fun mayRetry(alreadyHandled: Boolean, attempts: Int): Boolean =
        !alreadyHandled && attempts == 0
}
