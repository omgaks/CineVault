package com.sole.cinevault

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** What the open player offers to voice. Set by the player screen while it is open. */
internal class VoicePlayerController(
    /** Runs a command on the main thread. Returns a short "done" line, or null if not possible. */
    val apply: (VoiceCommand) -> String?,
    val duck: (Boolean) -> Unit,
    val controlsVisible: () -> Boolean
)

internal object VoicePlayerRegistry {
    var current by mutableStateOf<VoicePlayerController?>(null)
}
