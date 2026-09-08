package com.sole.cinevault

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.os.Build
import android.util.Rational
import androidx.media3.exoplayer.ExoPlayer

/**
 * Slice 24: owns CineVault's explicit player-exit decision.
 *
 * This is intentionally small: it preserves the existing behavior exactly.
 * If playback is active and Android PiP is available, an explicit exit
 * request attempts PiP first. If that is unavailable or fails, navigation
 * falls back to the normal onBack callback.
 *
 * System back/swipe behavior is not changed by this extraction.
 */
class PlayerExitCoordinator(
    private val context: Context,
    private val activity: Activity?,
    private val exoPlayer: ExoPlayer,
    private val isPlaying: () -> Boolean,
    private val onBack: () -> Unit,
) {
    fun handleExitRequest() {
        if (isPlaying() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val actions = buildPipActions(
                    context,
                    exoPlayer.isPlaying,
                )

                val entered = activity?.enterPictureInPictureMode(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .setActions(actions)
                        .build()
                )

                if (entered == true) return
            } catch (_: Exception) {
                // Preserve the previous defensive fallback: device-specific
                // PiP failures should never block leaving the player.
            }
        }

        onBack()
    }
}
