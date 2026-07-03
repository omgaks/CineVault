package com.sole.cinevault

import android.app.Activity
import android.content.Context
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

// ─────────────────────────────────────────────────────────────────────────────
// THE ONE AND ONLY findCineActivity() for the whole app.
// All other copies in Screens.kt, LocalVideoLibraryScreen.kt, VideoPlayerScreen.kt
// and MainActivity.kt must be deleted. This file is the single source of truth.
// ─────────────────────────────────────────────────────────────────────────────
fun Context.findCineActivity(): Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

// Hide system bars — called when entering the video player
fun Activity.enterImmersiveModeForPlayer() {
    WindowInsetsControllerCompat(window, window.decorView).apply {
        hide(WindowInsetsCompat.Type.systemBars())
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

// Show system bars — called when leaving the video player
fun Activity.exitImmersiveModeForPlayer() {
    WindowInsetsControllerCompat(window, window.decorView).apply {
        show(WindowInsetsCompat.Type.systemBars())
    }
}
