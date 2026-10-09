package com.sole.cinevault.picture

/** Recovery planning is separate from actual player mutation. */
object PictureHdrRecoveryPolicy {
    data class Snapshot(val positionMs: Long, val playWhenReady: Boolean)
    fun snapshot(positionMs: Long, playWhenReady: Boolean): Snapshot =
        Snapshot(positionMs.coerceAtLeast(0L), playWhenReady)
    fun mayRetry(handled: Boolean, attempts: Int): Boolean = !handled && attempts == 0
}
