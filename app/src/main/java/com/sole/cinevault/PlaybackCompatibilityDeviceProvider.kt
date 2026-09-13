package com.sole.cinevault

import android.os.Build

/**
 * Captures the Android device identity used for compatibility-matrix results.
 *
 * Keep android.os.Build access at this boundary so the matrix and recorder
 * remain pure and straightforward to unit test.
 */
fun currentPlaybackCompatibilityDevice(): PlaybackCompatibilityDevice =
    PlaybackCompatibilityDevice(
        manufacturer = Build.MANUFACTURER
            ?.trim()
            .orEmpty()
            .ifBlank { "Unknown" },
        model = Build.MODEL
            ?.trim()
            .orEmpty()
            .ifBlank { "Unknown" },
        sdkInt = Build.VERSION.SDK_INT,
    )
