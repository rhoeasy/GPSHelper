package com.example

import com.example.track.F1SectorStatus
import com.example.track.KartingSectorEngine
import com.example.track.SampleTrackSeeder
import com.example.track.TelemetryJsonExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class KartingSectorEngineTest {

    @Test
    fun `sample karting track automatically splits into 3 F1 sectors with purple green and yellow statuses`() {
        val detail = SampleTrackSeeder.buildSampleKartingTrack(endTimeMs = 1_750_000_000_000L)
        val analysis = KartingSectorEngine.analyze(detail.points, detail.laps)

        assertNotNull("Expected non-null F1 sector analysis", analysis)
        requireNotNull(analysis)

        assertTrue("Sample karting track should be recognized as multi-lap", analysis.isMultiLap)
        assertEquals(3, analysis.lapTimings.size)
        assertEquals(listOf("S1", "S2", "S3"), analysis.gates.map { it.label })
        assertEquals(detail.points.size, analysis.allPointsSectorIndex.size)

        // Each lap must have S1, S2, S3 summing to the official lap duration
        analysis.lapTimings.forEach { lap ->
            assertEquals(3, lap.sectors.size)
            assertEquals(listOf("S1", "S2", "S3"), lap.sectors.map { it.label })
            assertEquals(lap.lapDurationMs, lap.sectors.sumOf { it.durationMs })
            assertTrue(lap.sectors.all { it.distanceMeters > 100.0 })
        }

        // Authentic F1 color distribution across the 3 laps: must contain Purple, Green, and Yellow
        val allSectorStatuses = analysis.lapTimings.flatMap { lap -> lap.sectors.map { it.status } }.toSet()
        assertTrue("Expected PURPLE sector in session", F1SectorStatus.PURPLE in allSectorStatuses)
        assertTrue("Expected GREEN sector in session", F1SectorStatus.GREEN in allSectorStatuses)
        assertTrue("Expected YELLOW sector in session", F1SectorStatus.YELLOW in allSectorStatuses)

        // Each sector S1/S2/S3 must have at least one PURPLE overall best
        for (s in 0..2) {
            assertTrue(
                "Sector S${s + 1} should have at least one PURPLE split",
                analysis.lapTimings.any { it.sectors[s].status == F1SectorStatus.PURPLE }
            )
        }

        // Because Lap 2 wins S2 while Lap 3 wins S1 & S3, theoretical best lap should beat actual best lap
        assertEquals(3, analysis.bestLapNumber)
        assertTrue(
            "Theoretical best (${analysis.theoreticalBestLapMs}) should be faster than actual best (${analysis.bestLapDurationMs})",
            analysis.theoreticalBestLapMs < analysis.bestLapDurationMs
        )
        assertTrue("Potential gain should be positive", analysis.potentialGainMs > 0L)
    }

    @Test
    fun `single run without completed laps falls back cleanly to 3 sectors`() {
        val detail = SampleTrackSeeder.buildSampleKartingTrack(endTimeMs = 1_750_000_000_000L)
        val firstLapOnly = detail.points
            .filter { it.lapNumber == 1 }
            .map { it.copy(lapNumber = 0) }

        val analysis = KartingSectorEngine.analyze(firstLapOnly, emptyList())
        assertNotNull(analysis)
        requireNotNull(analysis)

        assertFalse(analysis.isMultiLap)
        assertEquals(1, analysis.lapTimings.size)
        assertEquals(3, analysis.lapTimings.first().sectors.size)
        assertEquals(listOf("S1", "S2", "S3"), analysis.gates.map { it.label })
    }

    @Test
    fun `karting structured json export includes f1_sectors telemetry`() {
        val detail = SampleTrackSeeder.buildSampleKartingTrack(endTimeMs = 1_750_000_000_000L)
        val json = TelemetryJsonExporter.buildStructuredJson(
            session = detail.session,
            points = detail.points,
            laps = detail.laps
        )

        assertTrue("Expected f1_sectors block in JSON", json.contains("\"f1_sectors\""))
        assertTrue("Expected theoretical_best_lap_sec in JSON", json.contains("\"theoretical_best_lap_sec\""))
        assertTrue("Expected PURPLE status in JSON", json.contains("\"PURPLE\""))
        assertTrue("Expected YELLOW status in JSON", json.contains("\"YELLOW\""))
    }
}
