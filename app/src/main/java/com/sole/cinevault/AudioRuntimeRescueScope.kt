package com.sole.cinevault

internal data class AudioRuntimeRescueScope(
    val videoPath: String?,
    val hasPendingPlan: Boolean,
    val rendererPreference: CineAudioRendererPreference,
)

internal enum class AudioRuntimeRescueScopeDecision {
    KEEP,
    RESET,
}

internal fun decideAudioRuntimeRescueScope(
    scope: AudioRuntimeRescueScope,
    activeVideoPath: String,
): AudioRuntimeRescueScopeDecision =
    when {
        scope.videoPath == null &&
            !scope.hasPendingPlan &&
            scope.rendererPreference == CineAudioRendererPreference.PLATFORM_FIRST ->
            AudioRuntimeRescueScopeDecision.KEEP

        scope.videoPath == activeVideoPath ->
            AudioRuntimeRescueScopeDecision.KEEP

        else ->
            AudioRuntimeRescueScopeDecision.RESET
    }
