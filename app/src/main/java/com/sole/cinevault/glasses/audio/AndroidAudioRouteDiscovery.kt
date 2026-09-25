package com.sole.cinevault.glasses.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/**
 * D13-S2: translates Android output devices into D13's Android-free route model.
 */
internal object AndroidAudioRouteDiscovery {
    fun discover(context: Context): List<CineAudioRouteDevice> {
        val manager = context.getSystemService(AudioManager::class.java) ?: return emptyList()
        return manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .map { it.toCineAudioRouteDevice() }
    }

    fun findOutput(context: Context, deviceId: Int): AudioDeviceInfo? {
        val manager = context.getSystemService(AudioManager::class.java) ?: return null
        return manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { it.id == deviceId && it.isSink }
    }

    private fun AudioDeviceInfo.toCineAudioRouteDevice(): CineAudioRouteDevice =
        CineAudioRouteDevice(
            id = id,
            kind = classify(this),
            name = productName?.toString().orEmpty(),
            isSink = isSink,
        )

    private fun classify(device: AudioDeviceInfo): CineAudioRouteKind {
        val name = device.productName?.toString()?.lowercase().orEmpty()
        if (name.contains("rayneo") || name.contains("air 3") || name.contains("xr glass")) {
            return CineAudioRouteKind.GLASSES
        }

        return when (device.type) {
            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_HDMI_ARC,
            AudioDeviceInfo.TYPE_HDMI_EARC -> CineAudioRouteKind.HDMI

            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> CineAudioRouteKind.BLUETOOTH

            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET -> CineAudioRouteKind.WIRED

            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> CineAudioRouteKind.BUILTIN

            else -> CineAudioRouteKind.OTHER
        }
    }
}
