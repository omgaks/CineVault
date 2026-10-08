package com.sole.cinevault.picture

/**
 * P8-S1: pure HDR routing policy. No player, shader, or persisted settings are changed.
 * Transfer identifiers are normalized by the caller; this class does not guess from resolution.
 */
object PictureHdrPolicy {
    enum class Transfer { SDR, PQ, HLG, UNKNOWN }
    enum class Format { STANDARD, DOLBY_VISION }
    enum class Route { SDR_ENHANCEMENT, HDR_PASSTHROUGH }

    data class Input(
        val transfer: Transfer,
        val format: Format = Format.STANDARD,
        val hdrEffectSupported: Boolean = false,
    )

    data class Decision(
        val route: Route,
        val hdrDetected: Boolean,
        val reason: String,
    )

    fun decide(input: Input): Decision {
        val hdr = input.transfer == Transfer.PQ || input.transfer == Transfer.HLG ||
            input.format == Format.DOLBY_VISION
        return if (hdr) {
            // HDR effects remain disabled until a verified HDR-safe GL pipeline is available.
            Decision(Route.HDR_PASSTHROUGH, true, "HDR playback preserved without SDR shader processing")
        } else if (input.transfer == Transfer.UNKNOWN) {
            Decision(Route.HDR_PASSTHROUGH, false, "Unknown transfer: preserve original playback")
        } else {
            Decision(Route.SDR_ENHANCEMENT, false, "SDR enhancement available")
        }
    }
}
