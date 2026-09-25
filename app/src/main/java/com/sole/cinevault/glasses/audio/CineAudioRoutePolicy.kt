package com.sole.cinevault.glasses.audio

internal enum class CineAudioRouteKind { GLASSES, HDMI, BLUETOOTH, WIRED, BUILTIN, OTHER }

internal data class CineAudioRouteDevice(
    val id: Int,
    val kind: CineAudioRouteKind,
    val name: String,
    val isSink: Boolean = true,
)

internal enum class CineAudioRouteReason {
    GLASSES_PREFERRED, SYSTEM_EXTERNAL_FALLBACK, BUILTIN_FALLBACK, SYSTEM_DEFAULT
}

internal data class CineAudioRouteDecision(
    val preferredDeviceId: Int?,
    val reason: CineAudioRouteReason,
)

internal object CineAudioRoutePolicy {
    fun decide(
        devices: List<CineAudioRouteDevice>,
        glassesSessionActive: Boolean,
    ): CineAudioRouteDecision {
        if (!glassesSessionActive) {
            return CineAudioRouteDecision(null, CineAudioRouteReason.SYSTEM_DEFAULT)
        }
        val sinks = devices.filter { it.isSink }
        fun first(kind: CineAudioRouteKind) = sinks.firstOrNull { it.kind == kind }

        first(CineAudioRouteKind.GLASSES)?.let {
            return CineAudioRouteDecision(it.id, CineAudioRouteReason.GLASSES_PREFERRED)
        }
        listOf(CineAudioRouteKind.HDMI, CineAudioRouteKind.BLUETOOTH, CineAudioRouteKind.WIRED)
            .firstNotNullOfOrNull(::first)?.let {
                return CineAudioRouteDecision(it.id, CineAudioRouteReason.SYSTEM_EXTERNAL_FALLBACK)
            }
        first(CineAudioRouteKind.BUILTIN)?.let {
            return CineAudioRouteDecision(it.id, CineAudioRouteReason.BUILTIN_FALLBACK)
        }
        return CineAudioRouteDecision(null, CineAudioRouteReason.SYSTEM_DEFAULT)
    }
}
