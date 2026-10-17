package com.sole.cinevault.playback.rescue.video

import com.sole.cinevault.subtitles.SubtitleLanguageRegistry

internal data class FfmpegNativeAudioTrack(
    val streamIndex: Int,
    val language: String,
    val codec: String,
    val channels: Int,
)

internal data class FfmpegNativeAudioTrackSnapshot(
    val tracks: List<FfmpegNativeAudioTrack> = emptyList(),
    val selectedStreamIndex: Int = -1,
)

internal object FfmpegNativeAudioTracks {
    /** JNI returns four fields per stream; never confuse a row number with a stream id. */
    fun decode(fields: Array<String>): List<FfmpegNativeAudioTrack> =
        fields.asList().chunked(4).mapNotNull { row ->
            if (row.size != 4) return@mapNotNull null
            val index = row[0].toIntOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
            val channels = row[3].toIntOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
            FfmpegNativeAudioTrack(index, row[1].ifBlank { "und" }, row[2], channels)
        }.distinctBy { it.streamIndex }

    /** Carry a known Media3 choice over only when the matching stream is unambiguous. */
    fun restoreStream(tracks: List<FfmpegNativeAudioTrack>, language: String?, channels: Int): Int? {
        if (language.isNullOrBlank() || language.equals("und", true)) return null
        fun normalized(value: String): String = SubtitleLanguageRegistry.normalize(value) ?: value.lowercase(java.util.Locale.ROOT)
        val wantedLanguage = normalized(language)
        val matches = tracks.filter { normalized(it.language) == wantedLanguage }
        return matches.singleOrNull()?.streamIndex
            ?: matches.filter { it.channels == channels }.singleOrNull()?.streamIndex
    }
}
