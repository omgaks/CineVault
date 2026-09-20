package com.sole.cinevault.glasses.halo

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View

/**
 * D2-9 — dispatches a Halo CLICK back through Android's normal input pipeline.
 *
 * We deliberately do not keep a registry of CineVault buttons/cards. Android
 * performs the hit test at the Halo coordinate, so the real existing target
 * receives the click.
 */
class HaloCanonicalTargetDispatcher(
    private val rootView: View,
) {
    private var dispatchingSyntheticClick = false

    fun isDispatchingSyntheticClick(): Boolean = dispatchingSyntheticClick

    fun dispatchClick(position: HaloVector): Boolean {
        if (rootView.width <= 0 || rootView.height <= 0) return false

        val point = HaloTargetProjection.toPixels(
            position = position,
            widthPx = rootView.width,
            heightPx = rootView.height,
        )

        val downTime = SystemClock.uptimeMillis()
        dispatchingSyntheticClick = true
        return try {
            val down = MotionEvent.obtain(
                downTime,
                downTime,
                MotionEvent.ACTION_DOWN,
                point.xPx,
                point.yPx,
                0,
            )
            val up = MotionEvent.obtain(
                downTime,
                downTime + 1L,
                MotionEvent.ACTION_UP,
                point.xPx,
                point.yPx,
                0,
            )

            try {
                val downHandled = rootView.dispatchTouchEvent(down)
                val upHandled = rootView.dispatchTouchEvent(up)
                downHandled || upHandled
            } finally {
                down.recycle()
                up.recycle()
            }
        } finally {
            dispatchingSyntheticClick = false
        }
    }
}

data class HaloTargetPixelPoint(
    val xPx: Float,
    val yPx: Float,
)

object HaloTargetProjection {
    fun toPixels(
        position: HaloVector,
        widthPx: Int,
        heightPx: Int,
    ): HaloTargetPixelPoint {
        require(widthPx > 0) { "Halo target width must be greater than zero." }
        require(heightPx > 0) { "Halo target height must be greater than zero." }

        return HaloTargetPixelPoint(
            xPx = position.x.coerceIn(0f, 1f) * widthPx.toFloat(),
            yPx = position.y.coerceIn(0f, 1f) * heightPx.toFloat(),
        )
    }
}
