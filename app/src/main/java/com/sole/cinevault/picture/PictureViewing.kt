package com.sole.cinevault.picture

enum class PictureViewClass { UNKNOWN, DOWNSCALED, NATIVE, LIGHT, MODERATE, STRONG }

/**
 * What the picture will honestly go through on THIS screen: how much the source has to be
 * stretched to fill the display. Pure maths on measured sizes — no guessing, no Android types.
 */
data class PictureViewAssessment(
    val fitRatio: Double,
    val viewClass: PictureViewClass,
    val headline: String,
    val advice: String,
    val upscaleWorthIt: Boolean,
)

object PictureViewing {
    /** Below this fit ratio an extra resize pass costs GPU/battery for no visible gain. */
    const val MIN_USEFUL_SCALE = 1.15

    fun fitRatio(sourceWidth: Int, sourceHeight: Int, displayWidth: Int, displayHeight: Int): Double {
        if (sourceWidth <= 0 || sourceHeight <= 0 || displayWidth <= 0 || displayHeight <= 0) return 0.0
        return minOf(displayWidth.toDouble() / sourceWidth, displayHeight.toDouble() / sourceHeight)
    }

    fun classify(ratio: Double): PictureViewClass = when {
        ratio <= 0.0 -> PictureViewClass.UNKNOWN
        ratio < 0.95 -> PictureViewClass.DOWNSCALED
        ratio < MIN_USEFUL_SCALE -> PictureViewClass.NATIVE
        ratio < 1.5 -> PictureViewClass.LIGHT
        ratio < 2.5 -> PictureViewClass.MODERATE
        else -> PictureViewClass.STRONG
    }

    fun assess(
        sourceWidth: Int,
        sourceHeight: Int,
        displayWidth: Int,
        displayHeight: Int,
        content: PictureContent = PictureContent.AUTO,
    ): PictureViewAssessment {
        val ratio = fitRatio(sourceWidth, sourceHeight, displayWidth, displayHeight)
        val cls = classify(ratio)
        val animeLike = content == PictureContent.ANIME || content == PictureContent.ANIMATION
        val sizes = "${sourceWidth}×$sourceHeight on ${displayWidth}×$displayHeight"
        return when (cls) {
            PictureViewClass.UNKNOWN -> PictureViewAssessment(
                ratio, cls, "Measuring your screen…",
                "Recommendations appear once the video and screen sizes are known.", false,
            )
            PictureViewClass.DOWNSCALED -> PictureViewAssessment(
                ratio, cls, "Screen is smaller than the video ($sizes)",
                "Resizing can't add detail here. Clean-up and colour work still help; skip upscaling.",
                false,
            )
            PictureViewClass.NATIVE -> PictureViewAssessment(
                ratio, cls, "Video matches your screen ($sizes)",
                "Native detail. Gentle sharpening and colour are enough; upscaling would add nothing.",
                false,
            )
            PictureViewClass.LIGHT -> PictureViewAssessment(
                ratio, cls, "Slightly stretched on your screen ($sizes)",
                "A light upscale plus sharpening gives a modest, visible improvement.",
                true,
            )
            PictureViewClass.MODERATE -> PictureViewAssessment(
                ratio, cls, "Clearly stretched on your screen ($sizes)",
                if (animeLike) "Upscaling with line sharpening is worth it for this anime."
                else "Upscaling with careful sharpening is worth it; keep grain-friendly settings.",
                true,
            )
            PictureViewClass.STRONG -> PictureViewAssessment(
                ratio, cls, "Heavily stretched on your screen ($sizes)",
                "Low-resolution source on a big screen: the strongest reconstruction helps most here.",
                true,
            )
        }
    }
}

/**
 * Measured, not requested: what the upscale stage actually reported from the GL thread.
 * Written by the effect on the render thread, read by the UI.
 */
class PictureStageProbe {
    @Volatile var inputWidth = 0
        private set
    @Volatile var inputHeight = 0
        private set
    @Volatile var outputWidth = 0
        private set
    @Volatile var outputHeight = 0
        private set
    private val drawn = java.util.concurrent.atomic.AtomicLong()

    val frames: Long get() = drawn.get()

    fun onConfigured(inW: Int, inH: Int, outW: Int, outH: Int) {
        inputWidth = inW; inputHeight = inH; outputWidth = outW; outputHeight = outH
    }

    fun onFrame() { drawn.incrementAndGet() }

    fun summary(): String? =
        if (frames > 0 && outputWidth > 0) {
            "Upscale active · ${inputWidth}×$inputHeight → ${outputWidth}×$outputHeight"
        } else null
}
