package com.sole.cinevault.glasses.stereo

/**
 * Per-video stereo state for the canonical CineVault playback session.
 *
 * This is deliberately independent of ExoPlayer/Compose. D12-S2 connects
 * detection and policy to the live movie while rendering remains unchanged.
 */
internal class StereoPlaybackSession(
    fileName: String,
    path: String,
) {
    var detected: StereoPlaybackDecision = StereoPlaybackDetector.detect(fileName, path)
        private set

    var userOverride: StereoPlaybackMode? = null
        private set

    fun onVideoChanged(fileName: String, path: String) {
        sourceFileName = fileName
        sourcePath = path
        detected = StereoPlaybackDetector.detect(fileName, path)
        userOverride = null
    }

    fun updateVideoDimensions(width: Int, height: Int) {
        // Re-run the same conservative detector only to strengthen confidence.
        // Dimensions by themselves can never turn ordinary 2D into stereo.
        detected = StereoPlaybackDetector.detect(
            fileName = sourceFileName,
            path = sourcePath,
            width = width,
            height = height,
        )
    }

    private var sourceFileName: String = fileName
    private var sourcePath: String = path

    fun replaceSource(fileName: String, path: String) {
        sourceFileName = fileName
        sourcePath = path
        onVideoChanged(fileName, path)
    }

    fun setUserOverride(mode: StereoPlaybackMode?) {
        userOverride = mode
    }

    fun resolve(externalDisplayActive: Boolean): StereoPlaybackDecision =
        StereoPlaybackPolicy.resolve(
            detected = detected,
            externalDisplayActive = externalDisplayActive,
            userOverride = userOverride,
        )
}
