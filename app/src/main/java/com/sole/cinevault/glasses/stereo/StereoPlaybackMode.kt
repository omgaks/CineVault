package com.sole.cinevault.glasses.stereo
internal enum class StereoPlaybackMode { NORMAL_2D, SIDE_BY_SIDE, TOP_BOTTOM }
internal enum class StereoDetectionConfidence { NONE, WEAK, STRONG }
internal data class StereoPlaybackDecision(val mode: StereoPlaybackMode, val confidence: StereoDetectionConfidence, val reason: String) { val isStereo get() = mode != StereoPlaybackMode.NORMAL_2D }
