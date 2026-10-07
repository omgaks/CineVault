package com.sole.cinevault.picture

/**
 * P7-S1: pure, deterministic performance planning. No playback or Media3 side effects.
 * The quality controls remain user-owned; the policy only recommends GPU workload.
 */
object PictureAdaptivePerformancePolicy {
    enum class Load { NORMAL, ELEVATED, CRITICAL }
    enum class Budget { ECONOMY, BALANCED, FULL }

    data class Input(
        val sourceWidth: Int,
        val sourceHeight: Int,
        val frameRate: Float,
        val intensity: Float,
        val droppedFrames: Int = 0,
        val observedFrames: Int = 0,
        val thermalStatus: Int = 0,
    )

    data class Plan(
        val budget: Budget,
        val load: Load,
        val repairScale: Float,
        val chromaScale: Float,
        val detailScale: Float,
        val reason: String,
    )

    fun plan(input: Input): Plan {
        val width = input.sourceWidth.coerceAtLeast(1)
        val height = input.sourceHeight.coerceAtLeast(1)
        val fps = input.frameRate.takeIf { it.isFinite() && it > 0f } ?: 30f
        val pixelsPerSecond = width.toDouble() * height.toDouble() * fps
        val dropRatio = if (input.observedFrames > 0)
            input.droppedFrames.coerceAtLeast(0).toDouble() / input.observedFrames
        else 0.0
        val load = when {
            input.thermalStatus >= 4 || dropRatio >= 0.12 -> Load.CRITICAL
            input.thermalStatus >= 3 || dropRatio >= 0.05 || pixelsPerSecond > 1920.0 * 1080 * 60 -> Load.ELEVATED
            else -> Load.NORMAL
        }
        val intensity = input.intensity.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: .9f
        val requested = when {
            intensity < .72f -> Budget.ECONOMY
            intensity >= .96f -> Budget.FULL
            else -> Budget.BALANCED
        }
        val budget = when (load) {
            Load.NORMAL -> requested
            Load.ELEVATED -> if (requested == Budget.FULL) Budget.BALANCED else requested
            Load.CRITICAL -> Budget.ECONOMY
        }
        val factor = when (budget) {
            Budget.ECONOMY -> .65f
            Budget.BALANCED -> .85f
            Budget.FULL -> 1f
        }
        return Plan(budget, load, factor, factor, factor,
            when (load) {
                Load.NORMAL -> "Requested quality"
                Load.ELEVATED -> "High processing load"
                Load.CRITICAL -> "Playback protection"
            })
    }
}
