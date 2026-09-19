package com.sole.cinevault.glasses.gestures

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs

enum class HeadGesture { NOD, SHAKE }

/**
 * Phase 8 — best-effort head gesture detection.
 *
 * See the Scope Change section of the Glasses Mode roadmap doc: CineVault
 * has no way to confirm a rotation-vector sensor actually tracks the
 * GLASSES' orientation rather than the phone sitting in a pocket or on a
 * table. The one signal available without a vendor SDK is whether
 * Android reports MORE THAN ONE rotation-vector sensor — the phone's own
 * default one, plus a second, external one an accessory driver may have
 * registered for the glasses. Only that second case is treated as
 * usable. If the only sensor available is the phone's default, gestures
 * are disabled entirely rather than "detecting" nods and shakes from a
 * phone that isn't on anyone's head — that would be actively misleading,
 * not a degraded feature.
 *
 * Only nod and shake are implemented here — both are discrete,
 * threshold-and-return events that fit a state machine cleanly.
 * Tilt-scroll (a continuous roll-to-scroll mapping) is a different shape
 * of gesture and isn't built yet; it's a reasonable follow-up once nod/
 * shake have been validated on real hardware.
 */
fun findExternalRotationSensor(context: Context): Sensor? {
    val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return null
    val default = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    return manager.getSensorList(Sensor.TYPE_ROTATION_VECTOR).firstOrNull { it != default }
}

private const val NOD_PITCH_THRESHOLD_DEG = 12f
private const val SHAKE_YAW_THRESHOLD_DEG = 12f
private const val RETURN_FRACTION = 0.4f
private const val GESTURE_WINDOW_MS = 600L
private const val COOLDOWN_MS = 700L

private enum class GestureState { IDLE, ARMED, COOLDOWN }

/**
 * Registers the listener only while [enabled] is true (glasses connected
 * and actually playing — never in the background). Returns whether an
 * external sensor was actually found, so the caller can show "gestures
 * aren't available on this pair" instead of silently doing nothing.
 */
@Composable
fun rememberHeadGestureDetector(
    enabled: Boolean,
    onGesture: (HeadGesture) -> Unit,
): Boolean {
    val context = LocalContext.current
    var available by remember { mutableStateOf(false) }

    DisposableEffect(enabled) {
        if (!enabled) {
            available = false
            onDispose { }
        } else {
            val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val sensor = manager?.let { findExternalRotationSensor(context) }
            if (manager == null || sensor == null) {
                available = false
                onDispose { }
            } else {
                available = true

                var state = GestureState.IDLE
                var armedGesture: HeadGesture? = null
                var gestureStartTime = 0L
                var cooldownUntil = 0L
                var baselinePitch = 0f
                var baselineYaw = 0f
                var haveBaseline = false
                val rotationMatrix = FloatArray(9)
                val orientation = FloatArray(3)

                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        SensorManager.getOrientation(rotationMatrix, orientation)
                        val yawDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
                        val pitchDeg = Math.toDegrees(orientation[1].toDouble()).toFloat()
                        val now = System.currentTimeMillis()

                        if (state == GestureState.COOLDOWN) {
                            if (now >= cooldownUntil) {
                                state = GestureState.IDLE
                                haveBaseline = false
                            }
                            return
                        }

                        if (!haveBaseline) {
                            baselinePitch = pitchDeg
                            baselineYaw = yawDeg
                            haveBaseline = true
                            return
                        }

                        val pitchDelta = pitchDeg - baselinePitch
                        val yawDelta = angleDelta(yawDeg, baselineYaw)

                        when (state) {
                            GestureState.IDLE -> {
                                when {
                                    abs(pitchDelta) > NOD_PITCH_THRESHOLD_DEG -> {
                                        state = GestureState.ARMED
                                        armedGesture = HeadGesture.NOD
                                        gestureStartTime = now
                                    }
                                    abs(yawDelta) > SHAKE_YAW_THRESHOLD_DEG -> {
                                        state = GestureState.ARMED
                                        armedGesture = HeadGesture.SHAKE
                                        gestureStartTime = now
                                    }
                                    else -> {
                                        // Baseline drifts slowly so a held
                                        // pose (not a gesture) doesn't stay
                                        // "off-center" forever — only while
                                        // clearly idle, never mid-gesture.
                                        baselinePitch = baselinePitch * 0.98f + pitchDeg * 0.02f
                                        baselineYaw = baselineYaw * 0.98f + yawDeg * 0.02f
                                    }
                                }
                            }
                            GestureState.ARMED -> {
                                val elapsed = now - gestureStartTime
                                val returned = abs(pitchDelta) < NOD_PITCH_THRESHOLD_DEG * RETURN_FRACTION &&
                                    abs(yawDelta) < SHAKE_YAW_THRESHOLD_DEG * RETURN_FRACTION
                                when {
                                    returned -> {
                                        armedGesture?.let(onGesture)
                                        state = GestureState.COOLDOWN
                                        cooldownUntil = now + COOLDOWN_MS
                                        armedGesture = null
                                        haveBaseline = false
                                    }
                                    elapsed > GESTURE_WINDOW_MS -> {
                                        // Took too long to return — a held
                                        // tilt, not a deliberate nod/shake.
                                        state = GestureState.IDLE
                                        armedGesture = null
                                        haveBaseline = false
                                    }
                                }
                            }
                            GestureState.COOLDOWN -> Unit // handled above
                        }
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }

                manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
                onDispose { manager.unregisterListener(listener) }
            }
        }
    }

    return available
}

private fun angleDelta(a: Float, b: Float): Float {
    var d = a - b
    while (d > 180f) d -= 360f
    while (d < -180f) d += 360f
    return d
}
