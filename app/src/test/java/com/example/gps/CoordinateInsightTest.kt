package com.example.gps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CoordinateInsightTest {
    @Test
    fun acceptsPastedCoordinatesAndRejectsImpossibleValues() {
        val beijing = MapCoordinate(39.9042, 116.4074)
        assertEquals(beijing, parseMapCoordinate(" 39.9042\uFF0C116.4074 "))
        assertEquals(beijing, parseMapCoordinate("116.4074, 39.9042"))
        assertEquals(beijing, parseMapCoordinate("(39.9042\n116.4074)"))
        assertNull(parseMapCoordinate("92, 200"))
        assertNull(parseMapCoordinate("NaN, 0"))
        assertNull(parseMapCoordinate("39.9, 116.4, 12"))
    }

    @Test
    fun nearbyAndTargetPromptsReflectOnlyTheSelectedCoordinateRequest() {
        val current = MapCoordinate(0.0, 0.0)
        val target = MapCoordinate(0.0, 1.0)
        val relation = compareCoordinates(current, target)
        assertEquals(111_319f, relation.distanceMeters, 20f)
        assertEquals(90f, requireNotNull(relation.bearingDegrees), 0.1f)
        assertNull(compareCoordinates(current, current).bearingDegrees)

        val nearby = coordinateInsightPrompt(current, null, null)
        assertTrue(nearby.contains("Current WGS84 position: 0.000000, 0.000000"))
        assertFalse(nearby.contains("Target WGS84 position"))
        val comparison = coordinateInsightPrompt(current, target, relation)
        assertTrue(comparison.contains("Target WGS84 position: 0.000000, 1.000000"))
        assertTrue(comparison.contains("straight-line distance"))
    }
}
