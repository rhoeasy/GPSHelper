package com.example.sensor

import com.example.track.ActivityMode
import com.example.track.RecordingSnapshot
import com.example.track.RecordingStatus
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotorcycleLeanMeasureTest {
    @Test
    fun angleIsRelativeToUprightMountAndDistinguishesBothDirections() {
        val measure = MotorcycleLeanMeasure()
        sample(measure, 12.0, 1_000)
        assertNull(measure.read(true, 1_000).degrees)
        assertTrue(measure.read(true, 1_000).canZero)
        measure.zero(1_000)
        assertEquals(0f, requireNotNull(measure.read(true, 1_000).degrees), 0.01f)

        sample(measure, 42.0, 1_600)
        assertEquals(30f, requireNotNull(measure.read(true, 1_600).degrees), 0.01f)
        sample(measure, -8.0, 2_200)
        assertEquals(-20f, requireNotNull(measure.read(true, 2_200).degrees), 0.01f)
        assertNull(measure.read(true, 2_701).degrees)

        measure.update(0.02f, 0.02f, 2_800)
        assertFalse(measure.read(true, 2_800).canZero)
        assertNull(measure.read(true, 2_800).degrees)
    }

    @Test
    fun leanIsVisibleOnlyWhileOffRoadRecordingIsRunning() {
        for (mode in ActivityMode.entries) {
            for (state in RecordingStatus.entries) {
                assertEquals(
                    mode == ActivityMode.OFFROAD_CROSSING && state == RecordingStatus.RECORDING,
                    RecordingSnapshot(status = state, mode = mode).showMotorcycleLean
                )
            }
        }
    }

    private fun sample(measure: MotorcycleLeanMeasure, roll: Double, timeMs: Long) {
        val angle = Math.toRadians(roll)
        measure.update((-sin(angle) * 0.8).toFloat(), (cos(angle) * 0.8).toFloat(), timeMs)
    }
}
