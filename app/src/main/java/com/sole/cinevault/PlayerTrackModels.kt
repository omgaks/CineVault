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
                subtitle = "Track ${trackIndex + 1}",
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
internal fun rememberDownloadedSubtitleChoice(
    context: Context,
    videoPath: String,
    preferredLanguages: List<String>,
    selectorVisible: Boolean,
    canDownloadExternalSubtitles: Boolean
): SubtitleTrackChoice.Downloaded? = remember(videoPath, selectorVisible) {
    if (!canDownloadExternalSubtitles) {
        null
    } else {
        OpenSubtitlesClient.findCachedSubtitle(
            context,
            videoPath,
            preferredLanguages
        )?.let { cached ->
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
