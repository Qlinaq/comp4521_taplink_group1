package com.taplink.app.platform

import kotlin.math.sqrt

/** Two distinct acceleration peaks, with a quiet interval, avoid toggling on a single bump. */
internal class ShakeDetector(
    private val thresholdG: Float = 2.7f,
    private val debounceMillis: Long = 1_500L,
) {
    private var firstPeak: Long? = null
    private var lastShake: Long? = null
    private var armed = true

    fun sample(x: Float, y: Float, z: Float, nowMillis: Long): Boolean {
        if (!x.isFinite() || !y.isFinite() || !z.isFinite() || nowMillis < 0) return false
        val g = sqrt(x * x + y * y + z * z) / 9.80665f
        if (g < 1.5f) armed = true
        if (g < thresholdG || !armed) return false
        armed = false
        val last = lastShake
        if (last != null && nowMillis - last < debounceMillis) return false
        val first = firstPeak
        if (first == null || nowMillis - first !in 80L..600L) {
            firstPeak = nowMillis
            return false
        }
        lastShake = nowMillis
        firstPeak = null
        return true
    }

    fun reset() { firstPeak = null; lastShake = null; armed = true }
}
