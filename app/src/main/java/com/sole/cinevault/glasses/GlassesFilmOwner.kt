package com.sole.cinevault.glasses

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** A film the phone's copy of the app wants the glasses copy to open. */
internal class ExternalPlayRequest(
    val video: com.sole.cinevault.library.VideoFile,
    val mediaType: String,
    val episodeList: List<com.sole.cinevault.VideoWithMetadata>,
)

/**
 * Who is showing the film's real player screen right now.
 *
 * While glasses are connected, a film opens in the glasses copy of the app, so the glasses
 * show the SAME player screen the tablet normally shows (same controls, same look). The
 * tablet stays the touchpad. If a film was already playing on the tablet when the glasses
 * were plugged in, the tablet keeps that player and the glasses mirror it (the older path).
 */
internal object GlassesFilmOwner {
    /** The tablet is showing the real player screen. */
    var hostShowsPlayer by mutableStateOf(false)

    /** The glasses copy is showing the real player screen. */
    var externalShowsPlayer by mutableStateOf(false)

    /** Set by the tablet; picked up (and cleared) by the glasses copy. */
    var request by mutableStateOf<ExternalPlayRequest?>(null)

    fun take(): ExternalPlayRequest? {
        val r = request
        request = null
        return r
    }
}

/** Pure rule: should a film start be handed to the glasses copy instead of the tablet? */
internal fun shouldOpenFilmOnGlasses(isHostCopy: Boolean, glassesSessionActive: Boolean): Boolean =
    isHostCopy && glassesSessionActive
