package com.sole.cinevault.glasses.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Applies D13 routing to the canonical ExoPlayer only.
 *
 * Media3 exposes preferred audio-device routing on ExoPlayer on supported
 * Android versions. A null preference returns ownership to Android.
 */
internal class CineAudioRouteController(
    private val context: Context,
    private val player: ExoPlayer,
) {
    fun apply(glassesSessionActive: Boolean): CineAudioRouteDecision {
        val devices = AndroidAudioRouteDiscovery.discover(context)
        val decision = CineAudioRoutePolicy.decide(devices, glassesSessionActive)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val device = decision.preferredDeviceId?.let {
                AndroidAudioRouteDiscovery.findOutput(context, it)
            }
            player.setPreferredAudioDevice(device)
        }

        return decision
    }

    fun releaseToSystem() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            player.setPreferredAudioDevice(null)
        }
    }
}
