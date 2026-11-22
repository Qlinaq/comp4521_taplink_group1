package com.taplink.app.platform

import org.junit.Assert.*
import org.junit.Test

class ShakeDetectorTest {
    private fun peak(detector: ShakeDetector, time: Long): Boolean = detector.sample(30f, 0f, 0f, time)
    private fun quiet(detector: ShakeDetector, time: Long) = detector.sample(0f, 0f, 9.80665f, time)

    @Test fun twoSeparatePeaksToggleOnce() {
        val detector = ShakeDetector()
        assertFalse(peak(detector, 1_000))
        assertFalse(quiet(detector, 1_100))
        assertTrue(peak(detector, 1_200))
        quiet(detector, 1_300)
        assertFalse(peak(detector, 1_400))
        quiet(detector, 1_500)
        assertFalse(peak(detector, 1_600))
    }

    @Test fun sustainedAccelerationAndSingleBumpsDoNotToggle() {
        val detector = ShakeDetector()
        assertFalse(peak(detector, 1_000))
        assertFalse(peak(detector, 1_200))
        assertFalse(peak(detector, 1_400))
        quiet(detector, 2_000)
        assertFalse(peak(detector, 2_200))
    }

    @Test fun newGestureWorksAfterCooldown() {
        val detector = ShakeDetector()
        peak(detector, 1_000); quiet(detector, 1_100)
        assertTrue(peak(detector, 1_200))
        quiet(detector, 2_800); peak(detector, 2_900); quiet(detector, 3_000)
        assertTrue(peak(detector, 3_100))
    }

    @Test fun resetRemovesPartialGestureWhenScreenLeavesForeground() {
        val detector = ShakeDetector()
        peak(detector, 1_000); quiet(detector, 1_100)
        detector.reset()
        assertFalse(peak(detector, 1_200))
    }

    @Test fun normalMovementAndInvalidSensorValuesAreIgnored() {
        val detector = ShakeDetector()
        assertFalse(quiet(detector, 1_000))
        assertFalse(detector.sample(10f, 5f, 10f, 1_100))
        assertFalse(detector.sample(Float.NaN, 0f, 0f, 1_200))
        assertFalse(detector.sample(Float.POSITIVE_INFINITY, 0f, 0f, 1_300))
        assertFalse(peak(detector, -1))
    }
}
