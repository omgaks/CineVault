package com.sole.cinevault

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.sole.cinevault.subtitles.OpenSubtitlesClient
import com.sole.cinevault.subtitles.SubtitleTrackChoice
import com.sole.cinevault.subtitles.SubtitleLanguageRegistry

/**
 * Pure/live model builders shared by the player track menus.
 *
 * Keeping this work outside VideoPlayerScreen prevents the screen assembly
 * from owning Media3 traversal, language labels and on-disk subtitle lookup.
 * The returned callbacks still mutate the same TrackSelector instance, so the
 * extraction does not change selection behaviour.
 */
internal fun buildAudioTrackRows(
    player: ExoPlayer,
    trackSelector: DefaultTrackSelector,
    onTrackSelected: () -> Unit
): List<TrackPopupRowData> = player.currentTracks.groups
    .filter { it.type == C.TRACK_TYPE_AUDIO }
    .flatMap { group ->
        List(group.length) { trackIndex ->
            val format = group.getTrackFormat(trackIndex)
            val language = friendlyLanguageName(format.language)
            TrackPopupRowData(
                title = if (language == "Unknown" || language == "UND") {
                    "Default Audio"
                } else {
                    language
                },
                // Codec and channels tell apart several tracks in the same language.
                subtitle = audioTrackDetail(format, trackIndex),
                selected = group.isTrackSelected(trackIndex),
                onClick = {
                    trackSelector.parameters = trackSelector
                        .buildUponParameters()
                        .setOverrideForType(
                            TrackSelectionOverride(
                                group.mediaTrackGroup,
                                listOf(trackIndex)
                            )
                        )
                        .build()
                    onTrackSelected()
                }
            )
        }
    }

private fun audioTrackDetail(format: androidx.media3.common.Format, trackIndex: Int): String {
    val codec = when (format.sampleMimeType) {
        androidx.media3.common.MimeTypes.AUDIO_AC3 -> "Dolby Digital"
        androidx.media3.common.MimeTypes.AUDIO_E_AC3, androidx.media3.common.MimeTypes.AUDIO_E_AC3_JOC -> "Dolby Digital+"
        androidx.media3.common.MimeTypes.AUDIO_TRUEHD -> "TrueHD"
        androidx.media3.common.MimeTypes.AUDIO_DTS, androidx.media3.common.MimeTypes.AUDIO_DTS_HD -> "DTS"
        androidx.media3.common.MimeTypes.AUDIO_AAC -> "AAC"
        androidx.media3.common.MimeTypes.AUDIO_OPUS -> "Opus"
        androidx.media3.common.MimeTypes.AUDIO_MPEG -> "MP3"
        androidx.media3.common.MimeTypes.AUDIO_FLAC -> "FLAC"
        else -> format.sampleMimeType?.substringAfter('/')?.uppercase().orEmpty()
    }
    val channels = when (format.channelCount) {
        1 -> "Mono"
        2 -> "Stereo"
        6 -> "5.1"
        8 -> "7.1"
        androidx.media3.common.Format.NO_VALUE -> ""
        else -> "${format.channelCount} ch"
    }
    val label = listOf(codec, channels).filter { it.isNotBlank() }.joinToString(" · ")
    return if (label.isBlank()) "Track ${trackIndex + 1}" else "Track ${trackIndex + 1} · $label"
}

internal fun hasInternalSubtitleTracks(tracks: Tracks): Boolean =
    tracks.groups.any { it.type == C.TRACK_TYPE_TEXT && it.length > 0 }

internal fun buildEmbeddedSubtitleChoices(tracks: Tracks): List<SubtitleTrackChoice.Embedded> =
    tracks.groups
        .filter { it.type == C.TRACK_TYPE_TEXT }
        .flatMapIndexed { groupIndex, group ->
            (0 until group.length).map { trackIndexInGroup ->
                val format = group.getTrackFormat(trackIndexInGroup)
                SubtitleTrackChoice.Embedded(
                    groupIndex = groupIndex,
                    trackIndexInGroup = trackIndexInGroup,
                    language = format.language ?: "und",
                    isForced = (format.selectionFlags and C.SELECTION_FLAG_FORCED) != 0,
                    isSdh = (format.roleFlags and C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND) != 0
                )
            }
        }

@Composable
internal fun rememberDownloadedSubtitleChoices(
    context: Context,
    videoPath: String,
    preferredLanguages: List<String>,
    selectorVisible: Boolean,
    canDownloadExternalSubtitles: Boolean
): List<SubtitleTrackChoice.Downloaded> = remember(videoPath, selectorVisible) {
    if (!canDownloadExternalSubtitles) {
        emptyList()
    } else {
        val preferred = preferredLanguages.mapNotNull { SubtitleLanguageRegistry.normalize(it) }.toSet()
        OpenSubtitlesClient.listCachedSubtitlesForVideo(context, videoPath)
            .filter { cached -> preferred.isEmpty() || SubtitleLanguageRegistry.normalize(cached.language) in preferred }
            .mapNotNull { cached ->
                cached.uri.path?.let { path ->
                    SubtitleTrackChoice.Downloaded(
                        file = java.io.File(path),
                        language = cached.language
                    )
                }
            }
    }
}

@Composable
internal fun rememberAvailableLocalSubtitleFiles(
    videoPath: String,
    selectorVisible: Boolean,
    pendingDeletePaths: List<String>
): List<java.io.File> = remember(videoPath, selectorVisible, pendingDeletePaths) {
    findNearbySrtFiles(videoPath)
        .filter { it.absolutePath !in pendingDeletePaths }
}
