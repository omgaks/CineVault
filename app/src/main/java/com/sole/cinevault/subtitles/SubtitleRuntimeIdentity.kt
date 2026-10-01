package com.sole.cinevault.subtitles

/**
 * Stable identity for the per-video Dual Subs runtime.
 *
 * The coordinator reads/writes caches using the active video path and also
 * bakes the selected secondary colour into the merged SRT. Both values are
 * therefore part of its identity; changing either must create a fresh runtime.
 */
data class SubtitleRuntimeIdentity(
    val videoPath: String,
    val secondaryColorHex: String,
)

fun subtitleRuntimeIdentity(
    videoPath: String,
    secondaryColorHex: String,
): SubtitleRuntimeIdentity =
    SubtitleRuntimeIdentity(
        videoPath = videoPath,
        secondaryColorHex = secondaryColorHex,
    )
