package com.sole.cinevault

enum class ActiveVideoDecoderKind {
    HARDWARE,
    SOFTWARE,
    UNKNOWN,
}

data class ActiveVideoDecoderStatus(
    val kind: ActiveVideoDecoderKind = ActiveVideoDecoderKind.UNKNOWN,
    val decoderName: String? = null,
)

fun classifyActiveVideoDecoder(
    decoderName: String?,
    capabilityReport: VideoDecoderCapabilityReport?,
    engineMode: PlaybackEngineMode,
): ActiveVideoDecoderKind {
    if (decoderName.isNullOrBlank()) {
        return ActiveVideoDecoderKind.UNKNOWN
    }

    val matched = capabilityReport
        ?.decoders
        ?.firstOrNull { it.name.equals(decoderName, ignoreCase = true) }

    if (matched?.softwareOnly == true) {
        return ActiveVideoDecoderKind.SOFTWARE
    }

    if (matched?.hardwareAccelerated == true) {
        return ActiveVideoDecoderKind.HARDWARE
    }

    // In CineVault SOFTWARE mode the RecoveryAwareMediaCodecSelector exposes
    // only software-only VIDEO decoders. This gives us a reliable fallback
    // classification even if the capability report was refreshed slightly
    // before the decoder-initialized callback arrives.
    if (engineMode == PlaybackEngineMode.SOFTWARE) {
        return ActiveVideoDecoderKind.SOFTWARE
    }

    return ActiveVideoDecoderKind.UNKNOWN
}
