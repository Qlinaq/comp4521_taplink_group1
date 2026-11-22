package com.taplink.app.platform

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.annotation.MainThread
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

/** Foreground, opt-in gesture. The sole callback must toggle balance visibility, never pay. */
@MainThread
class LifecycleShakeObserver(
    context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onToggleVisibility: () -> Unit,
) : DefaultLifecycleObserver, SensorEventListener, AutoCloseable {
    private val manager = context.applicationContext.getSystemService(SensorManager::class.java)
    private val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val detector = ShakeDetector()
    private var enabled = false
    private var registered = false
    private var closed = false
    val isAvailable: Boolean get() = sensor != null

    init { lifecycleOwner.lifecycle.addObserver(this) }

    fun setEnabled(value: Boolean) {
        enabled = value
        updateRegistration()
    }

    override fun onResume(owner: LifecycleOwner) = updateRegistration()
    override fun onPause(owner: LifecycleOwner) = unregister()
    override fun onDestroy(owner: LifecycleOwner) = close()

    private fun updateRegistration() {
        if (!closed && enabled && sensor != null && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            if (!registered) registered = manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        } else unregister()
    }

    private fun unregister() {
        if (registered) manager.unregisterListener(this)
        registered = false
        detector.reset()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (registered && enabled && !closed && event.values.size >= 3 &&
            detector.sample(event.values[0], event.values[1], event.values[2], event.timestamp / 1_000_000L)) {
            onToggleVisibility()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun close() {
        if (closed) return
        closed = true
        unregister()
        lifecycleOwner.lifecycle.removeObserver(this)
    }
}
