package com.sole.cinevault.glasses.display

/**
 * D1-11: one logical CineVault render-session identity across displays.
 *
 * D1-10 proved that an external display can host the real CineVaultRoot().
 * The next guardrail is making the ownership model explicit before we wire
 * navigation/player state: host and external surfaces belong to ONE logical
 * CineVault session, never two independent applications.
 *
 * This is deliberately Android/Compose-free so the contract is unit-testable.
 */
@JvmInline
value class CineVaultRenderSessionId(val value: Long) {
    init {
        require(value > 0L) { "CineVault render session id must be positive." }
    }
}

data class CineVaultRenderSession(
    val id: CineVaultRenderSessionId,
    val canonicalEntryPoint: CineVaultRenderEntryPoint =
        CineVaultRenderEntryPoint.CINEVAULT_ROOT,
)

/**
 * A surface binding says where the one logical CineVault session is rendered.
 *
 * NORMAL:
 *   session X -> HOST_DISPLAY
 *
 * GLASSES:
 *   session X -> EXTERNAL_DISPLAY(displayId)
 *
 * The session id does not change just because rendering moves to RayNeo.
 */
data class CineVaultRenderSessionBinding(
    val sessionId: CineVaultRenderSessionId,
    val destination: CineVaultRenderDestination,
    val displayId: Int? = null,
) {
    init {
        require(
            destination != CineVaultRenderDestination.EXTERNAL_DISPLAY ||
                displayId != null
        ) {
            "External session binding requires a displayId."
        }

        require(
            destination != CineVaultRenderDestination.HOST_DISPLAY ||
                displayId == null
        ) {
            "Host session binding must not carry an external displayId."
        }
    }
}

fun CineVaultRenderSession.bind(
    request: CineVaultRenderRequest,
): CineVaultRenderSessionBinding {
    require(request.entryPoint == canonicalEntryPoint) {
        "Render request does not target this session's canonical CineVault root."
    }

    return CineVaultRenderSessionBinding(
        sessionId = id,
        destination = request.destination,
        displayId = request.displayId,
    )
}

/**
 * Small state machine used by later Android/Compose wiring.
 *
 * It remembers ONE session identity while allowing the physical render target
 * to move between tablet and glasses. It does not own navigation, ExoPlayer,
 * subtitles, menus, or Halo state.
 */
class CineVaultRenderSessionController(
    sessionId: CineVaultRenderSessionId = CineVaultRenderSessionId(1L),
) {
    val session = CineVaultRenderSession(sessionId)

    var binding: CineVaultRenderSessionBinding =
        CineVaultRenderSessionBinding(
            sessionId = session.id,
            destination = CineVaultRenderDestination.HOST_DISPLAY,
        )
        private set

    fun apply(request: CineVaultRenderRequest): CineVaultRenderSessionBinding {
        binding = session.bind(request)
        return binding
    }
}
