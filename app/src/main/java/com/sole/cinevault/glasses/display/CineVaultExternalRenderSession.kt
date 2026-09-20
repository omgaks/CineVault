package com.sole.cinevault.glasses.display

/**
 * One logical CineVault render-session identity across displays.
 *
 * Host and external surfaces belong to ONE logical CineVault session, never
 * two independent applications. Rendering may move between displays without
 * changing the canonical session identity.
 */
@JvmInline
value class CineVaultRenderSessionId(val value: Long) {
    init { require(value > 0L) { "CineVault render session id must be positive." } }
}

data class CineVaultRenderSession(
    val id: CineVaultRenderSessionId,
    val canonicalEntryPoint: CineVaultRenderEntryPoint = CineVaultRenderEntryPoint.CINEVAULT_ROOT,
)

data class CineVaultRenderSessionBinding(
    val sessionId: CineVaultRenderSessionId,
    val destination: CineVaultRenderDestination,
    val displayId: Int? = null,
) {
    init {
        require(destination != CineVaultRenderDestination.EXTERNAL_DISPLAY || displayId != null) {
            "External session binding requires a displayId."
        }
        require(destination != CineVaultRenderDestination.HOST_DISPLAY || displayId == null) {
            "Host session binding must not carry an external displayId."
        }
    }
}

fun CineVaultRenderSession.bind(request: CineVaultRenderRequest): CineVaultRenderSessionBinding {
    require(request.entryPoint == canonicalEntryPoint) {
        "Render request does not target this session's canonical CineVault root."
    }
    return CineVaultRenderSessionBinding(id, request.destination, request.displayId)
}

class CineVaultRenderSessionController(
    sessionId: CineVaultRenderSessionId = CineVaultRenderSessionId(1L),
) {
    val session = CineVaultRenderSession(sessionId)
    var binding: CineVaultRenderSessionBinding =
        CineVaultRenderSessionBinding(session.id, CineVaultRenderDestination.HOST_DISPLAY)
        private set

    fun apply(request: CineVaultRenderRequest): CineVaultRenderSessionBinding {
        binding = session.bind(request)
        return binding
    }
}
