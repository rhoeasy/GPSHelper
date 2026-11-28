package com.example.track

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KartingLapTimerTest {
    private val finish = line(0.0, 5.0)

    @Test
    fun firstForwardPassStartsTimingAtTheGpsSegmentFractionNotTheGateFraction() {
        val timer = KartingLapTimer(KartingTimingCourse(finish))
        assertNull(timer.onFix(fix(-2.0, 2.0, 1_000L)))
        val event = timer.onFix(fix(8.0, 2.0, 1_500L)) as KartingLapEvent.TimingStarted
        assertEquals(1_100L, event.crossing.elapsedMs)
        assertEquals(0.2, event.crossing.fractionOfFixInterval, 1e-6)
    }

    @Test
    fun waitingInPitAndParallelOrReversePassesDoNotStartALap() {
        val timer = KartingLapTimer(KartingTimingCourse(finish))
        assertNull(timer.onFix(fix(-25.0, 9.0, 1_000L)))
        assertNull(timer.onFix(fix(-25.0, 9.0, 22_000L)))
        assertNull(timer.onFix(fix(-2.0, 9.0, 23_000L)))
        assertNull(timer.onFix(fix(8.0, 9.0, 23_500L)))
        assertNull(timer.onFix(fix(8.0, 1.0, 24_000L)))
        assertNull(timer.onFix(fix(-2.0, 1.0, 24_500L)))

        val start = timer.onFix(fix(8.0, 1.0, 25_000L)) as KartingLapEvent.TimingStarted
        assertEquals(24_600L, start.crossing.elapsedMs)
    }

    @Test
    fun nextForwardCrossingCompletesTheFirstFullLap() {
        val timer = KartingLapTimer(KartingTimingCourse(finish), minLapDurationMs = 1_000L)
        assertNull(timer.onFix(fix(-2.0, 2.0, 1_000L)))
        assertEquals(KartingLapEvent.TimingStarted::class, timer.onFix(fix(8.0, 2.0, 1_500L))!!::class)
        assertNull(timer.onFix(fix(25.0, 15.0, 2_500L)))
        assertNull(timer.onFix(fix(-16.0, 15.0, 3_500L)))
        assertNull(timer.onFix(fix(-8.0, 2.0, 4_500L)))

        val completed = timer.onFix(fix(8.0, 2.0, 5_000L)) as KartingLapEvent.LapCompleted
        assertEquals(4_750L, completed.crossing.elapsedMs)
        assertEquals(3_650L, completed.durationMs)
    }

    @Test
    fun outageAndPoorPrecisionNeverInventALapBetweenFixes() {
        val timer = KartingLapTimer(KartingTimingCourse(finish), minLapDurationMs = 1_000L)
        timer.onFix(fix(-2.0, 1.0, 1_000L))
        timer.onFix(fix(8.0, 1.0, 1_500L)) as KartingLapEvent.TimingStarted
        timer.onFix(fix(-2.0, 1.0, 2_000L))
        assertEquals(KartingLapEvent.TimingInterrupted, timer.onFix(fix(8.0, 1.0, 8_000L)))
        assertNull(timer.onFix(fix(-2.0, 1.0, 8_500L)))
        assertNull(timer.onFix(fix(8.0, 1.0, 9_000L, accuracy = 22f)))
        assertNull(timer.onFix(fix(-2.0, 1.0, 9_500L)))

        val restarted = timer.onFix(fix(8.0, 1.0, 10_000L)) as KartingLapEvent.TimingStarted
        assertEquals(9_600L, restarted.crossing.elapsedMs)
    }

    @Test
    fun aCourseWithACheckpointIgnoresLoopsThatSkipIt() {
        val timer = KartingLapTimer(KartingTimingCourse(finish, line(20.0, 5.0)), minLapDurationMs = 1_000L)
        timer.onFix(fix(-4.0, 0.0, 1_000L))
        timer.onFix(fix(8.0, 0.0, 1_500L)) as KartingLapEvent.TimingStarted
        timer.onFix(fix(10.0, 14.0, 2_500L))
        timer.onFix(fix(-10.0, 14.0, 3_500L))
        timer.onFix(fix(-4.0, 0.0, 4_500L))
        val restarted = timer.onFix(fix(8.0, 0.0, 5_000L)) as KartingLapEvent.TimingStarted
        assertEquals(4_667L, restarted.crossing.elapsedMs)

        timer.onFix(fix(26.0, 0.0, 6_000L))
        timer.onFix(fix(25.0, 15.0, 7_000L))
        timer.onFix(fix(-14.0, 15.0, 8_000L))
        timer.onFix(fix(-4.0, 0.0, 9_000L))
        val completed = timer.onFix(fix(8.0, 0.0, 9_600L)) as KartingLapEvent.LapCompleted
        assertEquals(4_533L, completed.durationMs)
    }

    private fun line(x: Double, halfWidth: Double) = KartingTimingLine(point(x, halfWidth), point(x, -halfWidth))

    private fun fix(x: Double, y: Double, time: Long, accuracy: Float = 3f) = KartingTimingFix(point(x, y), time, accuracy)

    private fun point(x: Double, y: Double) = KartingTimingCoordinate(y / 110_540.0, x / 111_320.0)
}
