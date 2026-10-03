package com.sole.cinevault

import android.content.Context
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

/**
 * Forces Media3 to render the external subtitle CineVault just attached.
 *
 * Language/flag preferences alone proved unreliable: a generated or downloaded
 * subtitle could be attached and listed, yet an embedded track (or a stale
 * preference from the previous subtitle) kept winning the automatic selection.
 * Each attached subtitle gets a unique id; once Media3 reports a text track
 * carrying that id, it is selected with an explicit override, which beats every
 * preference. If playback errors before the track appears, the user is told
 * instead of the subtitle silently turning off.
 */
@OptIn(UnstableApi::class)
class ExternalSubtitleSelector(
    private val context: Context,
    private val player: ExoPlayer,
    private val trackSelector: DefaultTrackSelector,
) {
    private var counter = 0
    private var armedListener: Player.Listener? = null

    // The trailing "-end" keeps id 1 from matching id 11 in a contains() check.
    fun nextId(): String = "cinevault-external-${++counter}-end"

    fun arm(id: String) {
        disarm()
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                for (group in tracks.groups) {
                    if (group.type != C.TRACK_TYPE_TEXT) continue
                    val index = (0 until group.length).firstOrNull {
                        group.getTrackFormat(it).id?.contains(id) == true
                    } ?: continue
                    disarm()
                    trackSelector.parameters = trackSelector.buildUponParameters()
                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(
                            TrackSelectionOverride(group.mediaTrackGroup, listOf(index))
                        )
                        .build()
                    return
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                disarm()
                Toast.makeText(
                    context,
                    "Subtitle didn't load (${error.errorCodeName})",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
        armedListener = listener
        player.addListener(listener)
    }

    fun disarm() {
        armedListener?.let { player.removeListener(it) }
        armedListener = null
    }
}
