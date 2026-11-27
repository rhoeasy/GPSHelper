package com.example.sensor

import com.example.track.TrackFormatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientAndHikingSensorsTest {
    @Test
    fun ambientPolicyCancelsShortShadowsAndDoesNotRestartIdenticalPendingChange() {
        val policy = AmbientThemePolicy()
        assertNull(policy.settle())

        assertTrue(policy.offer(8f))
        assertEquals(true, policy.pendingIsDark)
        assertFalse(policy.offer(5f))
        assertTrue(policy.offer(30f))
        assertNull(policy.pendingIsDark)
        assertNull(policy.settle())
    }

    @Test
    fun ambientPolicyKeepsLastThemeInDeadbandAndReturnsToLightWhenBright() {
        val policy = AmbientThemePolicy()
        assertTrue(policy.offer(15f))
        assertEquals(true, policy.settle())

        assertFalse(policy.offer(Float.NaN))
        assertFalse(policy.offer(30f))
        assertEquals(true, policy.settle())
        assertTrue(policy.offer(40f))
        assertEquals(false, policy.settle())
    }

    @Test
    fun stepDetectorSkipsPausedAndDelayedEvents() {
        val steps = HikingStepCount(initialCount = 0)
        steps.resume(100L)
        steps.detectStep(95L)
        steps.detectStep(110L)
        steps.pause(120L)
        steps.detectStep(130L)
        steps.resume(200L)
        steps.detectStep(180L)
        steps.detectStep(210L)

        assertEquals(2, steps.count)
    }

    @Test
    fun cumulativeCounterDoesNotCopySystemTotalOrIncludePausedSteps() {
        val steps = HikingStepCount()
        steps.resume(100L)
        steps.readCounter(800, 90L)
        assertNull(steps.count)
        steps.readCounter(802, 110L)
        assertEquals(2, steps.count)

        steps.pause(120L)
        steps.readCounter(825, 130L)
        steps.resume(200L)
        steps.readCounter(826, 190L)
        steps.readCounter(829, 195L)
        steps.readCounter(830, 210L)
        assertEquals(3, steps.count)

        steps.readCounter(4, 220L)
        steps.readCounter(6, 230L)
        assertEquals(5, steps.count)
    }

    @Test
    fun cumulativeCounterCountsTheFirstConfirmedStepAfterResume() {
        val steps = HikingStepCount()
        steps.resume(100L)
        steps.readCounter(800, 90L)
        steps.readCounter(802, 110L)

        steps.pause(120L)
        steps.resume(200L)
        steps.readCounter(803, 210L)

        assertEquals(3, steps.count)
    }

    @Test
    fun averageCadenceUsesActiveRecordingTime() {
        assertEquals(90, TrackFormatters.averageStepsPerMinute(180, 120_000L))
        assertNull(TrackFormatters.averageStepsPerMinute(2, 1_000L))
    }
}
