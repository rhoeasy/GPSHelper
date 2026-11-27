package com.example.track

import com.example.sensor.EnvironmentTelemetry
import com.example.sensor.GForceFusionEngine
import com.example.sensor.TrackVibrationWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackRecordingTelemetryTest {
    @Test
    fun barometerUsesGpsAnchorAndIgnoresLaterGpsAltitudeJitter() {
        val elevation = TrackElevation()
        assertNull(elevation.record(gpsAltitudeM = null, pressureHpa = 1000f))

        val start = requireNotNull(elevation.record(gpsAltitudeM = 200.0, pressureHpa = 1000f, canCalibrateBarometer = true))
        assertEquals(200.0, start.meters, 0.1)
        assertTrue(start.fromBarometer)

        elevation.record(gpsAltitudeM = 350.0, pressureHpa = 1000f)
        assertEquals(0.0, elevation.gainMeters, 0.0)
        for (pressure in listOf(999f, 998f, 997f, 996f)) {
            elevation.record(gpsAltitudeM = null, pressureHpa = pressure)
        }
        assertTrue(elevation.gainMeters > 20.0)
        assertEquals(0.0, elevation.lossMeters, 0.0)
    }

    @Test
    fun sourceTransitionsKeepTheProfileContinuousWithoutInventingClimb() {
        val elevation = TrackElevation()
        val pressure = requireNotNull(elevation.record(gpsAltitudeM = 200.0, pressureHpa = 1000f, canCalibrateBarometer = true))
        val fallback = requireNotNull(elevation.record(gpsAltitudeM = 300.0, pressureHpa = null))
        assertFalse(fallback.fromBarometer)
        assertEquals(pressure.meters, fallback.meters, 0.01)

        val regained = requireNotNull(elevation.record(gpsAltitudeM = null, pressureHpa = 1000f))
        assertTrue(regained.fromBarometer)
        assertEquals(fallback.meters, regained.meters, 0.01)
        assertEquals(0.0, elevation.gainMeters, 0.0)
        assertEquals(0.0, elevation.lossMeters, 0.0)
    }

    @Test
    fun gpsOnlyClimbStillWorksButDoesNotCountMovementDuringPause() {
        val elevation = TrackElevation()
        for (meters in listOf(100.0, 106.0, 112.0)) {
            val sample = requireNotNull(elevation.record(gpsAltitudeM = meters, pressureHpa = null))
            assertEquals(meters, sample.meters, 0.0)
        }
        assertEquals(6.0, elevation.gainMeters, 0.0)

        elevation.breakInterval()
        elevation.record(gpsAltitudeM = 200.0, pressureHpa = null)
        assertEquals(6.0, elevation.gainMeters, 0.0)
    }

    @Test
    fun staleAndFuturePressureSamplesAreNotUsed() {
        val telemetry = EnvironmentTelemetry(pressureHpa = 1000f, pressureSampleElapsedMs = 10_000L)
        assertEquals(1000f, requireNotNull(telemetry.freshPressureHpa(14_000L)), 0f)
        assertNull(telemetry.freshPressureHpa(15_001L))
        assertNull(telemetry.freshPressureHpa(9_999L))
    }

    @Test
    fun verticalRmsDistinguishesVibrationFromSteadyGravityAndResetsBetweenLegs() {
        val engine = GForceFusionEngine()
        val window = TrackVibrationWindow()
        val gravity = GForceFusionEngine.STANDARD_GRAVITY
        repeat(20) { i ->
            val sample = engine.onAccelerometerSample(0f, 0f, gravity, 20L * (i + 1), false)
            window.add(sample.verticalVibrationG)
        }
        assertEquals(0f, requireNotNull(window.takeRmsG()), 0.001f)
        assertNull(window.takeRmsG())

        repeat(20) { i ->
            val az = gravity + if (i % 2 == 0) 2f else -2f
            val sample = engine.onAccelerometerSample(0f, 0f, az, 400L + 20L * (i + 1), false)
            window.add(sample.verticalVibrationG)
        }
        assertTrue(requireNotNull(window.takeRmsG()) > 0.10f)
    }

    @Test
    fun roughSectionsRequireSustainedVibrationAndDoNotBridgeGaps() {
        val points = listOf(
            point(0L, null),
            point(500L, 0.14f),
            point(1000L, 0.18f),
            point(1500L, 0.02f),
            point(2000L, 0.30f),
            point(2500L, null),
            point(7000L, 0.16f),
            point(7500L, 0.22f),
            point(8000L, 0.16f)
        )
        assertEquals(listOf(0..2, 6..8), findRoughTrackSegments(points))
        assertTrue(findRoughTrackSegments(points.map { it.copy(verticalVibrationG = null) }).isEmpty())
    }

    private fun point(timeMs: Long, vibrationG: Float?): TrackPointEntity = TrackPointEntity(
        sessionId = 1L,
        timestampMs = timeMs,
        latitude = 31.23,
        longitude = 121.47,
        altitudeMeters = null,
        speedMps = 5f,
        accuracyMeters = null,
        bearingDegrees = null,
        heartRateBpm = null,
        lapNumber = 0,
        verticalVibrationG = vibrationG
    )
}
