package com.sole.cinevault.glasses.audio

internal enum class CineAudioRouteStatusKind { SYSTEM, GLASSES, EXTERNAL_FALLBACK, BUILTIN_FALLBACK }

internal data class CineAudioRouteStatus(
    val kind: CineAudioRouteStatusKind,
    val compactLabel: String?,
    val guidance: String?,
)

internal object CineAudioRouteStatusResolver {
    fun resolve(decision: CineAudioRouteDecision): CineAudioRouteStatus =
        when (decision.reason) {
            CineAudioRouteReason.GLASSES_PREFERRED ->
                CineAudioRouteStatus(CineAudioRouteStatusKind.GLASSES, "AUDIO • GLASSES", null)
            CineAudioRouteReason.SYSTEM_EXTERNAL_FALLBACK ->
                CineAudioRouteStatus(
                    CineAudioRouteStatusKind.EXTERNAL_FALLBACK,
                    "AUDIO • EXTERNAL",
                    "Glasses audio unavailable; using another external audio output.",
                )
            CineAudioRouteReason.BUILTIN_FALLBACK ->
                CineAudioRouteStatus(
                    CineAudioRouteStatusKind.BUILTIN_FALLBACK,
                    "AUDIO • DEVICE",
                    "Glasses audio unavailable; using this device.",
                )
            CineAudioRouteReason.SYSTEM_DEFAULT ->
                CineAudioRouteStatus(CineAudioRouteStatusKind.SYSTEM, null, null)
        }
}
