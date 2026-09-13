package com.sole.cinevault

enum class AudioCodecFamily {
    AAC,
    AC3,
    EAC3,
    AC4,
    DTS,
    DTS_HD,
    TRUEHD,
    FLAC,
    OPUS,
    VORBIS,
    MP3,
    PCM,
    UNKNOWN,
}

enum class AudioDecoderPreference {
    PLATFORM_PREFERRED,
    FFMPEG_RESCUE_CANDIDATE,
    UNKNOWN,
}

data class AudioStreamCapabilityAssessment(
    val codecFamily: AudioCodecFamily,
    val decoderPreference: AudioDecoderPreference,
    val codecLabel: String,
)

/**
 * Classifies the selected audio stream without pretending to know which
 * renderer actually initialized at runtime.
 *
 * CineVault currently registers MediaCodec first and FfmpegAudioRenderer
 * second. DTS/DTS-HD/TrueHD are therefore strong FFmpeg rescue candidates,
 * while common Android codecs remain platform-preferred with the extension
 * still available if Media3 needs it.
 */
fun assessAudioStreamCapability(
    stream: PlaybackStreamDescriptor?,
): AudioStreamCapabilityAssessment? {
    if (stream == null || stream.kind != PlaybackStreamKind.AUDIO) {
        return null
    }

    val mime = stream.mimeType
        ?.trim()
        ?.lowercase()
        .orEmpty()

    val codecs = stream.codecString
        ?.trim()
        ?.lowercase()
        .orEmpty()

    val family = identifyAudioCodecFamily(
        mimeType = mime,
        codecString = codecs,
    )

    val preference = when (family) {
        AudioCodecFamily.DTS,
        AudioCodecFamily.DTS_HD,
        AudioCodecFamily.TRUEHD ->
            AudioDecoderPreference.FFMPEG_RESCUE_CANDIDATE

        AudioCodecFamily.AAC,
        AudioCodecFamily.AC3,
        AudioCodecFamily.EAC3,
        AudioCodecFamily.AC4,
        AudioCodecFamily.FLAC,
        AudioCodecFamily.OPUS,
        AudioCodecFamily.VORBIS,
        AudioCodecFamily.MP3,
        AudioCodecFamily.PCM ->
            AudioDecoderPreference.PLATFORM_PREFERRED

        AudioCodecFamily.UNKNOWN ->
            AudioDecoderPreference.UNKNOWN
    }

    return AudioStreamCapabilityAssessment(
        codecFamily = family,
        decoderPreference = preference,
        codecLabel = audioCodecLabel(family, mime, codecs),
    )
}

fun identifyAudioCodecFamily(
    mimeType: String?,
    codecString: String?,
): AudioCodecFamily {
    val mime = mimeType
        ?.trim()
        ?.lowercase()
        .orEmpty()
    val codecs = codecString
        ?.trim()
        ?.lowercase()
        .orEmpty()

    return when {
        mime.contains("dts.hd") ||
            mime.contains("dts-hd") ||
            codecs.contains("dtsh") ||
            codecs.contains("dtsl") ->
            AudioCodecFamily.DTS_HD

        mime.contains("dts") ||
            codecs == "dtsc" ||
            codecs.startsWith("dts") ->
            AudioCodecFamily.DTS

        mime.contains("true-hd") ||
            mime.contains("truehd") ||
            codecs.contains("mlpa") ->
            AudioCodecFamily.TRUEHD

        mime.contains("eac3") ||
            mime.contains("e-ac3") ||
            codecs == "ec-3" ->
            AudioCodecFamily.EAC3

        mime.contains("ac3") ||
            mime.contains("ac-3") ||
            codecs == "ac-3" ->
            AudioCodecFamily.AC3

        mime.contains("ac4") ||
            mime.contains("ac-4") ||
            codecs == "ac-4" ->
            AudioCodecFamily.AC4

        mime.contains("mp4a") ||
            mime.contains("aac") ||
            codecs.startsWith("mp4a") ->
            AudioCodecFamily.AAC

        mime.contains("flac") ||
            codecs.contains("flac") ->
            AudioCodecFamily.FLAC

        mime.contains("opus") ||
            codecs.contains("opus") ->
            AudioCodecFamily.OPUS

        mime.contains("vorbis") ||
            codecs.contains("vorbis") ->
            AudioCodecFamily.VORBIS

        mime.contains("mpeg") ||
            codecs == "mp3" ->
            AudioCodecFamily.MP3

        mime.contains("raw") ||
            mime.contains("pcm") ||
            codecs.startsWith("pcm") ->
            AudioCodecFamily.PCM

        else ->
            AudioCodecFamily.UNKNOWN
    }
}

private fun audioCodecLabel(
    family: AudioCodecFamily,
    mimeType: String,
    codecString: String,
): String = when (family) {
    AudioCodecFamily.AAC -> "AAC"
    AudioCodecFamily.AC3 -> "AC-3"
    AudioCodecFamily.EAC3 -> "E-AC-3"
    AudioCodecFamily.AC4 -> "AC-4"
    AudioCodecFamily.DTS -> "DTS"
    AudioCodecFamily.DTS_HD -> "DTS-HD"
    AudioCodecFamily.TRUEHD -> "TrueHD"
    AudioCodecFamily.FLAC -> "FLAC"
    AudioCodecFamily.OPUS -> "Opus"
    AudioCodecFamily.VORBIS -> "Vorbis"
    AudioCodecFamily.MP3 -> "MP3"
    AudioCodecFamily.PCM -> "PCM"
    AudioCodecFamily.UNKNOWN ->
        codecString.takeIf { it.isNotBlank() }
            ?: mimeType.takeIf { it.isNotBlank() }
            ?: "Unknown audio"
}
