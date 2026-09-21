package com.sole.cinevault.glasses.halo

/**
 * D4-7 — click feedback pulse lifecycle.
 *
 * Converts a click into a short, finite visual pulse. Keeping this as a pure
 * state machine avoids permanent rings and keeps rendering timing testable.
 */
class HaloClickPulseController(
    private val durationMillis: Long = 170L,
) {
    private var startedAtMillis: Long? = null

    init {
        require(durationMillis > 0L)
    }

    fun trigger(eventTimeMillis: Long) {
        startedAtMillis = eventTimeMillis
    }

    fun progress(eventTimeMillis: Long): Float {
        val started = startedAtMillis ?: return 0f
        val elapsed = (eventTimeMillis - started).coerceAtLeast(0L)
        if (elapsed >= durationMillis) {
            startedAtMillis = null
            return 0f
        }
        return 1f - (elapsed.toFloat() / durationMillis.toFloat())
    }

    fun isActive(eventTimeMillis: Long): Boolean =
        progress(eventTimeMillis) > 0f

    fun reset() {
        startedAtMillis = null
    }
}
