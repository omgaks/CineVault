package com.sole.cinevault.glasses.halo

import kotlin.math.roundToLong

/**
 * D2-12 — bridge from D2-11 Halo player intents to the EXISTING CineVault
 * player actions.
 *
 * This class deliberately owns no brightness, volume, playback or UI state.
 * VideoPlayerScreen supplies its existing callbacks (brightness drag, volume
 * drag, seek and user-activity). Halo only converts normalized display-space
 * intent into the units those canonical actions already understand.
 *
 * One bridge session == one owned Halo drag.
 */
class HaloPlayerActionBridge(
    private val actions: HaloCanonicalPlayerActions,
) {
    private var session: Session? = null

    fun begin(
        start: HaloVector,
        viewportHeightPx: Int,
        playbackPositionMs: Long,
        durationMs: Long,
    ): HaloPlayerGestureZone {
        require(viewportHeightPx > 0) { "viewportHeightPx must be > 0" }

        val zone = HaloPlayerGestureRouter.zoneFor(start)
        session = Session(
            start = start,
            zone = zone,
            viewportHeightPx = viewportHeightPx,
            playbackPositionMs = playbackPositionMs.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L),
        )
        actions.onUserActivity()
        return zone
    }

    fun update(current: HaloVector): HaloPlayerGestureIntent? {
        val active = session ?: return null
        val intent = HaloPlayerGestureRouter.classifyDrag(
            start = active.start,
            current = current,
        )

        when (intent) {
            is HaloPlayerGestureIntent.Brightness -> {
                // Existing player drag callback uses Android pointer convention:
                // upward == negative deltaY.
                actions.onBrightnessDrag(
                    -intent.verticalDeltaFraction * active.viewportHeightPx,
                )
            }

            is HaloPlayerGestureIntent.Volume -> {
                actions.onVolumeDrag(
                    -intent.verticalDeltaFraction * active.viewportHeightPx,
                )
            }

            is HaloPlayerGestureIntent.Seek -> {
                val target = HaloSeekProjection.targetPositionMs(
                    startPositionMs = active.playbackPositionMs,
                    durationMs = active.durationMs,
                    horizontalDeltaFraction = intent.horizontalDeltaFraction,
                )
                actions.onSeekTo(target)
            }

            HaloPlayerGestureIntent.CanonicalUi -> Unit
        }

        actions.onUserActivity()
        return intent
    }

    fun end() {
        if (session != null) actions.onUserActivity()
        session = null
    }

    fun cancel() {
        session = null
    }

    fun activeZone(): HaloPlayerGestureZone? = session?.zone

    private data class Session(
        val start: HaloVector,
        val zone: HaloPlayerGestureZone,
        val viewportHeightPx: Int,
        val playbackPositionMs: Long,
        val durationMs: Long,
    )
}

/**
 * Adapter contract implemented at the existing VideoPlayerScreen boundary.
 *
 * D2-12 intentionally reuses the player's established actions/HUDs:
 * - onBrightnessDrag -> existing brightness logic + circle
 * - onVolumeDrag     -> existing volume logic + circle
 * - onSeekTo         -> existing ExoPlayer seek path / seek UI
 * - onUserActivity   -> existing controls visibility timer/activity bump
 */
interface HaloCanonicalPlayerActions {
    fun onBrightnessDrag(deltaYPx: Float)
    fun onVolumeDrag(deltaYPx: Float)
    fun onSeekTo(positionMs: Long)
    fun onUserActivity()
}

object HaloSeekProjection {
    fun targetPositionMs(
        startPositionMs: Long,
        durationMs: Long,
        horizontalDeltaFraction: Float,
    ): Long {
        if (durationMs <= 0L) return startPositionMs.coerceAtLeast(0L)

        val start = startPositionMs.coerceIn(0L, durationMs)
        val delta = (
            horizontalDeltaFraction.coerceIn(-1f, 1f) *
                durationMs.toFloat()
            ).roundToLong()

        return (start + delta).coerceIn(0L, durationMs)
    }
}
