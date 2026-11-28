package com.example.overlay

import com.example.gps.GpsData
import com.example.gps.GpsFixStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OverlayTelemetryTest {
    @Test
    fun reopeningTheAppInTheSameProcessDoesNotResetRideTime() {
        val clock = AppRideClock()
        assertNull(clock.elapsedMs(5_000L))
        clock.markOpened(10_000L)

        assertEquals(300_000L, clock.elapsedMs(310_000L))
        clock.markOpened(370_000L)
        assertEquals(420_000L, clock.elapsedMs(430_000L))
    }

    @Test
    fun timeMarksAppearAtWholeIntervalsAndRecalculateFromAppLaunch() {
        assertEquals("00:04:59", rideTimerReadout(299_999L, 5).elapsedText)
        assertEquals(0L, rideTimerReadout(299_999L, 5).completedMarks)
        assertEquals(1L, rideTimerReadout(300_000L, 5).completedMarks)
        assertEquals(2L, rideTimerReadout(600_000L, 5).completedMarks)
        assertEquals(5L, rideTimerReadout(600_000L, 2).completedMarks)

        val longRide = rideTimerReadout(4_500_000L, 5)
        assertEquals("01:15:00", longRide.elapsedText)
        assertEquals(0, longRide.visibleMarks)
        assertEquals(true, longRide.showTimeOnly)
    }

    @Test
    fun timerRefreshesEverySecondAfterMarkSixEvenWhenTimeIsTurnedOff() {
        assertEquals(5, rideTimerReadout(1_799_999L, 5).visibleMarks)
        assertEquals(false, rideTimerReadout(1_799_999L, 5).showTimeOnly)
        assertEquals(1L, rideTimerRefreshDelayMs(1_799_999L, 5, false))
        assertEquals(1_000L, rideTimerRefreshDelayMs(1_800_000L, 5, false))
        assertEquals(1_000L, rideTimerRefreshDelayMs(600_000L, 5, true))
        assertEquals(299_000L, rideTimerRefreshDelayMs(601_000L, 5, false))
    }

    @Test
    fun gpsSignalDistinguishesAccurateFixesFromFallbackAndLoss() {
        val locked = GpsData(hasFix = true, fixStatus = GpsFixStatus.LOCKED, accuracyMeters = 4f)
        assertEquals(OverlayGpsSignal.GOOD, overlayGpsSignal(locked))
        assertEquals(OverlayGpsSignal.FAIR, overlayGpsSignal(locked.copy(accuracyMeters = 18f)))
        assertEquals(OverlayGpsSignal.WEAK, overlayGpsSignal(locked.copy(accuracyMeters = 30f)))
        assertEquals(OverlayGpsSignal.LOCKED, overlayGpsSignal(locked.copy(accuracyMeters = null)))
        assertEquals(OverlayGpsSignal.FUSED, overlayGpsSignal(locked.copy(fixStatus = GpsFixStatus.FUSED_FALLBACK)))
        assertEquals(OverlayGpsSignal.SEARCHING, overlayGpsSignal(locked.copy(hasFix = false)))
        assertEquals(OverlayGpsSignal.DISABLED, overlayGpsSignal(GpsData(fixStatus = GpsFixStatus.DISABLED)))
        assertEquals(OverlayGpsSignal.NO_PERMISSION, overlayGpsSignal(GpsData(fixStatus = GpsFixStatus.NO_PERMISSION)))
    }
}
