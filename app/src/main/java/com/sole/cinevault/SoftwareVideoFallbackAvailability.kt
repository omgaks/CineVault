package com.sole.cinevault

/**
 * True only when Android exposes a software-only MediaCodec decoder that fully
 * supports the exact selected video Format.
 *
 * This is intentionally stricter than "some decoder exists": CineVault must
 * not advertise a software rescue path that cannot actually accept the stream.
 */
fun isPlatformSoftwareVideoFallbackAvailable(
    report: VideoDecoderCapabilityReport?,
): Boolean {
    return report?.decoders?.any { candidate ->
        candidate.softwareOnly && candidate.formatSupported
    } == true
}
