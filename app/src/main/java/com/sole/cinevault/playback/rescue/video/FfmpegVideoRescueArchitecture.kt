package com.sole.cinevault.playback.rescue.video

/**
 * B-series architecture freeze marker.
 *
 * B20 freezes the Kotlin-side rescue boundaries before JNI/FFmpeg implementation:
 * request/backend -> session owner -> runtime -> decoder/native bridge ->
 * generation-aware callbacks -> scheduled frame presentation.
 *
 * Native implementation may plug into these boundaries, but should not bypass
 * generation protection, single-session ownership, or runtime release semantics.
 */
object FfmpegVideoRescueArchitecture {
    const val CONTRACT_VERSION: Int = 1

    val invariants: Set<String> = setOf(
        "single_session_owner",
        "generation_guarded_callbacks",
        "seek_flushes_old_frames",
        "terminal_state_resets_on_seek",
        "release_is_idempotent",
        "native_bridge_hidden_behind_contract",
    )
}
