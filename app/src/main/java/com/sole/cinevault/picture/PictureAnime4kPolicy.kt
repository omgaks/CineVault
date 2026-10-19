package com.sole.cinevault.picture

/**
 * Capability gate for genuine Anime 4K output scaling.
 *
 * A renderer must explicitly report output resizing support before a scale request
 * can be issued. Shader sharpening alone must never be reported as 4K upscaling.
 * This policy does not install a renderer or modify Media3 playback.
 */
object PictureAnime4kPolicy {
    enum class Mode { ORIGINAL, ENHANCE_ONLY, UPSCALE }
    data class Input(
        val animeSelected: Boolean,
        val sourceWidth: Int,
        val sourceHeight: Int,
        val outputWidth: Int,
        val outputHeight: Int,
        val rendererSupportsOutputScaling: Boolean,
        val hdrSource: Boolean,
        val thermalLimited: Boolean,
        val droppedFramePressure: Boolean,
    )
    data class Plan(val mode: Mode, val targetWidth: Int, val targetHeight: Int, val reason: String)

    fun plan(input: Input): Plan {
        val sourceValid = input.sourceWidth > 0 && input.sourceHeight > 0
        val original = Plan(Mode.ORIGINAL, input.sourceWidth, input.sourceHeight, "Preserve source")
        if (!input.animeSelected || !sourceValid) return original
        val enhanced = Plan(Mode.ENHANCE_ONLY, input.sourceWidth, input.sourceHeight, "Anime enhancement without output resizing")
        if (input.hdrSource || input.thermalLimited || input.droppedFramePressure) return enhanced
        if (!input.rendererSupportsOutputScaling) return enhanced
        if (input.outputWidth < 3840 || input.outputHeight < 2160) return enhanced
        if (input.sourceWidth >= 3840 || input.sourceHeight >= 2160) return enhanced
        val scale = minOf(3840.0 / input.sourceWidth, 2160.0 / input.sourceHeight)
        if (scale <= 1.0) return enhanced
        val width = ((input.sourceWidth * scale).toInt() / 2) * 2
        val height = ((input.sourceHeight * scale).toInt() / 2) * 2
        return Plan(Mode.UPSCALE, width, height, "Renderer-supported output upscale")
    }
}
