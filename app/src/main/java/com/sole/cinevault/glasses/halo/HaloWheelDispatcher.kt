package com.sole.cinevault.glasses.halo

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View

/**
 * Mouse-wheel scrolling for the tablet's two-finger scroll.
 *
 * Instead of faking a finger dragging across the glasses screen (which runs out of room at the
 * edges and can grab sliders), this sends wheel ticks to whatever is under the Halo, exactly
 * like a mouse wheel: no edge limit, the pointer never moves, and a slider is never dragged.
 */
class HaloWheelDispatcher(
    private val rootView: View,
    private val syntheticDispatchGuard: HaloSyntheticDispatchGuard,
) {
    /** Hover once so the target knows where the "mouse" is before the wheel turns. */
    fun hover(position: HaloVector): Boolean = send(MotionEvent.ACTION_HOVER_MOVE, position, 0f, 0f)

    /** Positive [vScroll] = wheel up (shows content above); positive [hScroll] = wheel right. */
    fun scroll(position: HaloVector, vScroll: Float, hScroll: Float): Boolean {
        if (vScroll == 0f && hScroll == 0f) return false
        return send(MotionEvent.ACTION_SCROLL, position, vScroll, hScroll)
    }

    private fun send(action: Int, position: HaloVector, vScroll: Float, hScroll: Float): Boolean {
        if (rootView.width <= 0 || rootView.height <= 0) return false
        val point = HaloTargetProjection.toPixels(position, rootView.width, rootView.height)
        val props = arrayOf(MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_MOUSE
        })
        val coords = arrayOf(MotionEvent.PointerCoords().apply {
            x = point.xPx
            y = point.yPx
            setAxisValue(MotionEvent.AXIS_VSCROLL, vScroll)
            setAxisValue(MotionEvent.AXIS_HSCROLL, hScroll)
        })
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(
            now, now, action, 1, props, coords, 0, 0, 1f, 1f, 0, 0,
            InputDevice.SOURCE_MOUSE, 0,
        )
        return try {
            syntheticDispatchGuard.run { rootView.dispatchGenericMotionEvent(event) }
        } finally {
            event.recycle()
        }
    }
}
