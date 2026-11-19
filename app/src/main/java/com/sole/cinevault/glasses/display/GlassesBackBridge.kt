package com.sole.cinevault.glasses.display

/**
 * "Go back" for the glasses copy of the app. The glasses window registers it while it is open;
 * the tablet touchpad's two-finger tap calls it, so Back happens on the screen the glasses show.
 */
internal object GlassesBackBridge {
    var handler: (() -> Unit)? = null
}
