package com.sole.cinevault

data class PlaybackStatusPillPresentation(
    val primaryLabel: String,
    val rotatingLabels: List<String>,
    val emphasized: Boolean,
)

fun buildPlaybackStatusPillPresentation(
    snapshot: PlaybackDiagnosticsSnapshot,
): PlaybackStatusPillPresentation {
    val pipelineKind = classifyPlaybackSnapshotPipeline(snapshot)
    val audioResilience = assessAudioPlaybackResilience(
        selectedAudio = snapshot.audioMimeType?.let {
            PlaybackStreamDescriptor(
                kind = PlaybackStreamKind.AUDIO,
                mimeType = snapshot.audioMimeType,
                codecString = snapshot.audioCodecString,
                language = snapshot.audioLanguage,
                selected = true,
            )
        },
        activeDecoder = ActiveAudioDecoderStatus(
            kind = snapshot.activeAudioDecoderKind,
            decoderName = snapshot.audioDecoderName,
        ),
    )

    val failure = snapshot.lastFailureDiagnostic
    val failureLabel = failure?.let(::playbackFailureDiagnosticSummary)?.uppercase()

    val primary = when {
        failure?.severity == PlaybackFailureSeverity.TERMINAL ->
            when (failure.streamKind) {
                PlaybackFailureStreamKind.VIDEO -> "VIDEO FAILED"
                PlaybackFailureStreamKind.AUDIO -> "AUDIO FAILED"
                PlaybackFailureStreamKind.TEXT -> "SUBTITLE FAILED"
                PlaybackFailureStreamKind.OTHER -> "PLAYBACK FAILED"
                PlaybackFailureStreamKind.UNKNOWN -> "PLAYBACK FAILED"
            }

        failure?.recoveryAction == PlaybackRecoveryAction.SWITCH_TO_SOFTWARE ->
            "SW RESCUE"

        pipelineKind == PlaybackPipelineKind.MIXED_VIDEO_AUDIO_RESCUE ->
            "MIXED RESCUE"

        pipelineKind == PlaybackPipelineKind.VIDEO_SOFTWARE_RESCUE ->
            "SW VIDEO"

        pipelineKind == PlaybackPipelineKind.AUDIO_FFMPEG_RESCUE ->
            "FFMPEG AUDIO"

        pipelineKind == PlaybackPipelineKind.NATIVE ->
            "HW VIDEO"

        else -> "PLAYBACK"
    }

    val rotating = buildList {
        add(primary)

        failureLabel
            ?.takeIf { it != primary }
            ?.let(::add)

        buildAudioResilienceLabel(audioResilience)
            ?.takeIf { it != primary }
            ?.let(::add)

        buildVideoCodecLabel(snapshot)
            ?.takeIf { it != primary }
            ?.let(::add)

        buildAudioCodecLabel(snapshot)
            ?.takeIf { it != primary }
            ?.let(::add)

        buildVideoFormatLabel(snapshot)
            ?.takeIf { it != primary }
            ?.let(::add)
    }.distinct()

    val audioNeedsAttention =
        audioResilience.readiness ==
            AudioPlaybackReadiness.FFMPEG_RESCUE_EXPECTED ||
            audioResilience.readiness ==
            AudioPlaybackReadiness.FFMPEG_RESCUED

    return PlaybackStatusPillPresentation(
        primaryLabel = primary,
        rotatingLabels = rotating.ifEmpty { listOf(primary) },
        emphasized =
            failure != null ||
                pipelineKind == PlaybackPipelineKind.VIDEO_SOFTWARE_RESCUE ||
                pipelineKind == PlaybackPipelineKind.AUDIO_FFMPEG_RESCUE ||
                pipelineKind == PlaybackPipelineKind.MIXED_VIDEO_AUDIO_RESCUE ||
                audioNeedsAttention,
    )
}

fun classifyPlaybackSnapshotPipeline(
    snapshot: PlaybackDiagnosticsSnapshot,
): PlaybackPipelineKind {
    val videoRescued =
        snapshot.decoderMode == PlaybackEngineMode.SOFTWARE ||
            snapshot.activeDecoderKind == ActiveVideoDecoderKind.SOFTWARE ||
            snapshot.fallbackOccurred

    val audioRescued =
        snapshot.activeAudioDecoderKind == ActiveAudioDecoderKind.FFMPEG

    return when {
        videoRescued && audioRescued ->
            PlaybackPipelineKind.MIXED_VIDEO_AUDIO_RESCUE
        videoRescued ->
            PlaybackPipelineKind.VIDEO_SOFTWARE_RESCUE
        audioRescued ->
            PlaybackPipelineKind.AUDIO_FFMPEG_RESCUE
        snapshot.activeDecoderKind == ActiveVideoDecoderKind.HARDWARE ->
            PlaybackPipelineKind.NATIVE
        else ->
            PlaybackPipelineKind.UNKNOWN
    }
}

private fun buildAudioResilienceLabel(
    assessment: AudioPlaybackResilienceAssessment,
): String? = when (assessment.readiness) {
    AudioPlaybackReadiness.FFMPEG_RESCUE_EXPECTED -> "FFMPEG READY"
    AudioPlaybackReadiness.FFMPEG_RESCUED -> "FFMPEG AUDIO"
    AudioPlaybackReadiness.PLATFORM_ACTIVE -> "PLATFORM AUDIO"
    AudioPlaybackReadiness.PLATFORM_EXPECTED,
    AudioPlaybackReadiness.NONE,
    AudioPlaybackReadiness.UNKNOWN -> null
}

private fun buildVideoCodecLabel(
    snapshot: PlaybackDiagnosticsSnapshot,
): String? {
    val details = parseVideoCodecDetails(
        mimeType = snapshot.mimeType,
        codecString = snapshot.codecString,
    )

    val parts = buildList {
        details.codecLabel?.takeIf { it.isNotBlank() }?.let(::add)
        details.profileLabel?.takeIf { it.isNotBlank() }?.let(::add)
        details.inferredBitDepth?.let { add("$it-BIT") }
    }

    return parts.takeIf { it.isNotEmpty() }
        ?.joinToString(" · ")
        ?.uppercase()
}

private fun buildAudioCodecLabel(
    snapshot: PlaybackDiagnosticsSnapshot,
): String? {
    val descriptor = snapshot.audioMimeType?.let {
        PlaybackStreamDescriptor(
            kind = PlaybackStreamKind.AUDIO,
            mimeType = snapshot.audioMimeType,
            codecString = snapshot.audioCodecString,
            language = snapshot.audioLanguage,
            selected = true,
        )
    } ?: return null

    val assessment = assessAudioStreamCapability(descriptor) ?: return null

    return buildList {
        add(assessment.codecLabel.uppercase())
        snapshot.audioLanguage?.takeIf { it.isNotBlank() }?.uppercase()?.let(::add)
        when (snapshot.activeAudioDecoderKind) {
            ActiveAudioDecoderKind.FFMPEG -> add("FFMPEG")
            ActiveAudioDecoderKind.PLATFORM,
            ActiveAudioDecoderKind.UNKNOWN -> Unit
        }
    }.joinToString(" · ")
}

private fun buildVideoFormatLabel(
    snapshot: PlaybackDiagnosticsSnapshot,
): String? {
    val parts = buildList {
        snapshot.resolution
            .takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
            ?.uppercase()
            ?.let(::add)

        snapshot.frameRate
            ?.takeIf { it > 0f }
            ?.let {
                val rounded = kotlin.math.round(it * 100f) / 100f
                val fps = if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()
                add("$fps FPS")
            }

        if (
            snapshot.dynamicRange != VideoDynamicRange.SDR &&
            snapshot.dynamicRange != VideoDynamicRange.UNKNOWN
        ) {
            add(
                snapshot.dynamicRange.name
                    .replace('_', ' ')
                    .replace(" OR ", "/")
                    .uppercase()
            )
        }
    }

    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
