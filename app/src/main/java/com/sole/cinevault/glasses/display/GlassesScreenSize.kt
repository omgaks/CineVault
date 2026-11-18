package com.sole.cinevault.glasses.display

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

/** Pure rules for the glasses screen size (no Android, so it is unit-tested). */
internal object GlassesScreenSizePolicy {
    val STEPS = listOf(1f, 0.95f, 0.90f, 0.85f, 0.80f, 0.75f)

    /** Snaps any stored value to the nearest allowed step. */
    fun snap(value: Float): Float = STEPS.minByOrNull { kotlin.math.abs(it - value) } ?: 1f

    /** 100 -> 95 -> ... -> 75 -> back to 100. */
    fun next(current: Float): Float {
        val i = STEPS.indexOf(snap(current))
        return STEPS[(i + 1) % STEPS.size]
    }

    /** One step smaller, stopping at the smallest size (no wrap-around). */
    fun smaller(current: Float): Float {
        val i = STEPS.indexOf(snap(current))
        return STEPS[(i + 1).coerceAtMost(STEPS.lastIndex)]
    }

    /** One step bigger, stopping at full size (no wrap-around). */
    fun bigger(current: Float): Float {
        val i = STEPS.indexOf(snap(current))
        return STEPS[(i - 1).coerceAtLeast(0)]
    }

    /** 3D side-by-side must fill the glasses frame or the depth breaks; everything else shrinks. */
    fun effective(chosen: Float, fullFrame3d: Boolean): Float =
        if (fullFrame3d) 1f else snap(chosen)

    fun label(value: Float): String = "${(snap(value) * 100).toInt()}%"
}

/**
 * How big the app/film is on the glasses, remembered per pair of glasses. The tablet pad, the
 * glasses controls and Settings all change this one value.
 */
internal object GlassesScreenSize {
    private const val PREFS = "cinevault_glasses_screen_size"
    private var displayName: String = "default"

    var scale by mutableFloatStateOf(1f)
        private set

    fun bind(context: Context, name: String?) {
        displayName = name ?: "default"
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // New glasses start from the last size chosen anywhere, until they get their own.
        scale = GlassesScreenSizePolicy.snap(
            prefs.getFloat("scale_$displayName", prefs.getFloat("scale_default", 1f))
        )
    }

    fun set(context: Context, value: Float) {
        scale = GlassesScreenSizePolicy.snap(value)
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putFloat("scale_$displayName", scale).putFloat("scale_default", scale).apply()
    }

    fun smaller(context: Context) = set(context, GlassesScreenSizePolicy.smaller(scale))

    fun bigger(context: Context) = set(context, GlassesScreenSizePolicy.bigger(scale))
}
