package com.sole.cinevault

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * Routes short subtitle messages into the player's glass snackbar (amber-accented
 * CineVault pill) instead of the system toast, which shows the app icon in a
 * plain grey bubble. Falls back to the system toast when no player is on screen.
 */
object CineVaultToast {
    @Volatile
    var sink: ((String) -> Unit)? = null

    fun show(context: Context, message: String, long: Boolean = false) {
        val target = sink
        if (target != null) {
            Handler(Looper.getMainLooper()).post { target(message) }
        } else {
            Toast.makeText(
                context,
                message,
                if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT,
            ).show()
        }
    }
}
