package com.example

import com.example.gps.AccuracyQualityGrade
import com.example.gps.AccuracySessionTracker
import com.example.track.ActivityMode
import com.example.track.MergeJunctionQuality
import com.example.track.TrackEndpoints
import com.example.track.TrackMergeEngine
import com.example.track.TrackPointEntity
import com.example.track.TrackSessionDetail
import com.example.track.TrackSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackMergeAndAccuracyTest {

    @Test
    fun modeMergeability_allowsOnlyHikingAndOffroad_rejectsKarting() {
        assertTrue(TrackMergeEngine.isModeMergeable(ActivityMode.HIKING))
        assertTrue(TrackMergeEngine.isModeMergeable(ActivityMode.OFFROAD_CROSSING))
        assertFalse(TrackMergeEngine.isModeMergeable(ActivityMode.KARTING))
    }

    @Test
    fun mergeOffroadTracks_withOvernightRestGap_excludesSleepTimeAndConnectsSeamlessly() {
        val day1Start = 1_700_000_000_000L
        val day1Pts = listOf(
            point(1L, day1Start, 30.0000, 102.0000, 1000.0, 10f, 115, 0.35f),
            point(1L, day1Start + 60_000L, 30.0030, 102.0000, 1025.0, 12f, 122, 0.48f),
            point(1L, day1Start + 120_000L, 30.0060, 102.0000, 1050.0, 11f, 120, 0.42f)
        )
        val day1Session = session(
            id = 1L,
            mode = ActivityMode.OFFROAD_CROSSING,
            name = "川西穿越 Day 1",
            startTimeMs = day1Start,
            endTimeMs = day1Start + 120_000L,
            durationMs = 120_000L,
            distanceMeters = 667.0
        )

        // 12 hours overnight sleep, restarting ~22m away from Day 1 end point
        val day2Start = day1Start + 120_000L + 12 * 3_600_000L
        val day2Pts = listOf(
            point(2L, day2Start, 30.0062, 102.0000, 1052.0, 9f, 110, 0.30f),
            point(2L, day2Start + 60_000L, 30.0092, 102.0000, 1090.0, 14f, 130, 0.65f),
            point(2L, day2Start + 120_000L, 30.0122, 102.0000, 1110.0, 13f, 128, 0.55f)
        )
        val day2Session = session(
            id = 2L,
            mode = ActivityMode.OFFROAD_CROSSING,
            name = "川西穿越 Day 2",
            startTimeMs = day2Start,
            endTimeMs = day2Start + 120_000L,
            durationMs = 120_000L,
            distanceMeters = 667.0
        )

        // Pass in reverse order to ensure chronological sorting works
        val details = listOf(
            TrackSessionDetail(day2Session, day2Pts, emptyList()),
            TrackSessionDetail(day1Session, day1Pts, emptyList())
        )

        val preview = TrackMergeEngine.buildPreview(details).getOrThrow()
        assertEquals(ActivityMode.OFFROAD_CROSSING, preview.mode)
        assertEquals(listOf(1L, 2L), preview.orderedSessions.map { it.id })
        assertEquals(1, preview.junctions.size)
        val junction = preview.junctions.first()
        assertEquals(MergeJunctionQuality.SEAMLESS, junction.quality)
        assertTrue("Expected ~22m gap, was ${junction.gapMeters}", junction.gapMeters in 15.0..30.0)
        assertEquals(12 * 3_600_000L, junction.gapDurationMs)
        assertEquals(240_000L, preview.mergedDurationMs)

        val merged = TrackMergeEngine.merge(details, customName = "川西完整穿越").getOrThrow()
        assertEquals("川西完整穿越", merged.session.name)
        assertEquals(ActivityMode.OFFROAD_CROSSING, merged.session.activityMode)
        assertEquals(6, merged.points.size)
        // Active duration excludes the 12-hour overnight break
        assertEquals(240_000L, merged.session.durationMs)
        assertEquals(day1Start, merged.session.startTimeMs)
        assertEquals(day2Start + 120_000L, merged.session.endTimeMs)
        // Verify timestamps are strictly monotonic
        for (i in 1 until merged.points.size) {
            assertTrue(merged.points[i].timestampMs >= merged.points[i - 1].timestampMs)
        }
        // Verify recomputed metrics
        assertEquals(14f, merged.session.maxSpeedMps, 0.01f)
        assertEquals(130, merged.session.maxHeartRate)
        assertNotNull(merged.session.avgHeartRate)
        assertTrue(merged.session.elevationGainMeters >= 100.0)
        assertTrue(merged.session.distanceMeters > 1300.0)
    }

    @Test
    fun mergeHikingTracks_succeedsAndPreservesHikingMode() {
        val t0 = 1_710_000_000_000L
        val seg1 = TrackSessionDetail(
            session = session(10L, ActivityMode.HIKING, "武功山 D1", t0, t0 + 300_000L, 300_000L, 500.0, elevationGainMeters = 60.0),
            points = listOf(
                point(10L, t0, 27.4600, 114.1700, 1200.0, 1.5f),
                point(10L, t0 + 300_000L, 27.4640, 114.1700, 1260.0, 1.4f)
            ),
            laps = emptyList()
        )
        val t1 = t0 + 10 * 3_600_000L
        val seg2 = TrackSessionDetail(
            session = session(11L, ActivityMode.HIKING, "武功山 D2", t1, t1 + 300_000L, 300_000L, 500.0, elevationGainMeters = 78.0),
            points = listOf(
                point(11L, t1, 27.4643, 114.1700, 1262.0, 1.6f),
                point(11L, t1 + 300_000L, 27.4685, 114.1700, 1340.0, 1.5f)
            ),
            laps = emptyList()
        )

        val merged = TrackMergeEngine.merge(listOf(seg1, seg2)).getOrThrow()
        assertEquals(ActivityMode.HIKING, merged.session.activityMode)
        assertEquals(600_000L, merged.session.durationMs)
        assertEquals(4, merged.points.size)
        assertEquals(138.0, merged.session.elevationGainMeters, 0.1)

        val firstWithSteps = seg1.copy(session = seg1.session.copy(stepCount = 480))
        val secondWithSteps = seg2.copy(session = seg2.session.copy(stepCount = 560))
        assertEquals(1040, TrackMergeEngine.merge(listOf(firstWithSteps, secondWithSteps)).getOrThrow().session.stepCount)
        assertNull(TrackMergeEngine.merge(listOf(firstWithSteps, seg2)).getOrThrow().session.stepCount)
    }

    @Test
    fun mergeKartingTracks_isStrictlyRejected() {
        val t0 = 1_720_000_000_000L
        val k1 = TrackSessionDetail(
            session = session(20L, ActivityMode.KARTING, "卡丁车 1", t0, t0 + 60_000L, 60_000L, 800.0),
            points = listOf(
                point(20L, t0, 31.2000, 121.4000, 10.0, 15f),
                point(20L, t0 + 60_000L, 31.2010, 121.4000, 10.0, 16f)
            ),
            laps = emptyList()
        )
        val k2 = TrackSessionDetail(
            session = session(21L, ActivityMode.KARTING, "卡丁车 2", t0 + 120_000L, t0 + 180_000L, 60_000L, 800.0),
            points = listOf(
                point(21L, t0 + 120_000L, 31.2011, 121.4000, 10.0, 15f),
                point(21L, t0 + 180_000L, 31.2020, 121.4000, 10.0, 17f)
            ),
            laps = emptyList()
        )

        assertTrue(TrackMergeEngine.buildPreview(listOf(k1, k2)).isFailure)
        assertTrue(TrackMergeEngine.merge(listOf(k1, k2)).isFailure)
    }

    @Test
    fun mergeMixedActivityModes_isRejected() {
        val t0 = 1_725_000_000_000L
        val hike = TrackSessionDetail(
            session = session(30L, ActivityMode.HIKING, "徒步", t0, t0 + 60_000L, 60_000L, 200.0),
            points = listOf(
                point(30L, t0, 30.000, 102.000, 1000.0, 1.4f),
                point(30L, t0 + 60_000L, 30.001, 102.000, 1010.0, 1.4f)
            ),
            laps = emptyList()
        )
        val offroad = TrackSessionDetail(
            session = session(31L, ActivityMode.OFFROAD_CROSSING, "越野", t0 + 120_000L, t0 + 180_000L, 60_000L, 600.0),
            points = listOf(
                point(31L, t0 + 120_000L, 30.0011, 102.000, 1010.0, 10f),
                point(31L, t0 + 180_000L, 30.0040, 102.000, 1040.0, 10f)
            ),
            laps = emptyList()
        )

        assertTrue(TrackMergeEngine.buildPreview(listOf(hike, offroad)).isFailure)
        assertTrue(TrackMergeEngine.merge(listOf(hike, offroad)).isFailure)
    }

    @Test
    fun bestChainConnectionMeters_measuresEndToStartGap() {
        val s1 = session(1L, ActivityMode.HIKING, "Seg 1", 1000L, 2000L, 1000L, 500.0)
        val s2 = session(2L, ActivityMode.HIKING, "Seg 2", 5000L, 6000L, 1000L, 500.0)
        val endpoints = mapOf(
            1L to TrackEndpoints(1L, 30.0000, 102.0000, 30.0100, 102.0000),
            2L to TrackEndpoints(2L, 30.0105, 102.0000, 30.0200, 102.0000)
        )

        val gap = TrackMergeEngine.bestChainConnectionMeters(listOf(s1), s2, endpoints)
        assertNotNull(gap)
        // 0.0005 deg latitude is ~55.6 m
        assertTrue("Expected ~55m gap, was $gap", gap!! in 50.0..62.0)
        assertEquals(MergeJunctionQuality.SEAMLESS, TrackMergeEngine.classifyJunction(gap))
    }

    @Test
    fun accuracySessionTracker_tracksStatsGradesAndResetsCleanly() {
        val tracker = AccuracySessionTracker()

        val t1 = tracker.onAccuracySample(
            horizontalAccuracyM = 3.2f,
            verticalAccuracyM = 4.5f,
            speedAccuracyMps = 0.4f,
            bearingAccuracyDeg = 6.0f,
            elapsedMs = 1_000L
        )
        assertEquals(AccuracyQualityGrade.EXCELLENT, t1.qualityGrade)
        assertEquals(3.2f, t1.bestAccuracyM!!, 0.01f)
        assertEquals(3.2f, t1.worstAccuracyM!!, 0.01f)
        assertEquals(1, t1.sampleCount)
        assertTrue((t1.stabilityScore ?: 0) >= 85)

        val t2 = tracker.onAccuracySample(
            horizontalAccuracyM = 9.0f,
            verticalAccuracyM = 8.0f,
            speedAccuracyMps = 0.9f,
            bearingAccuracyDeg = 12.0f,
            elapsedMs = 2_000L
        )
        assertEquals(AccuracyQualityGrade.GOOD, t2.qualityGrade)
        assertEquals(3.2f, t2.bestAccuracyM!!, 0.01f)
        assertEquals(9.0f, t2.worstAccuracyM!!, 0.01f)
        assertEquals(6.1f, t2.avgAccuracyM!!, 0.05f)
        assertEquals(2, t2.sampleCount)
        assertEquals(2, t2.samples.size)
        assertEquals(1_000L, t2.fixIntervalMs)

        val t3 = tracker.onAccuracySample(
            horizontalAccuracyM = 30.0f,
            verticalAccuracyM = null,
            speedAccuracyMps = null,
            bearingAccuracyDeg = null,
            elapsedMs = 3_000L
        )
        assertEquals(AccuracyQualityGrade.POOR, t3.qualityGrade)

        tracker.reset()
        val afterReset = tracker.onAccuracySample(
            horizontalAccuracyM = 4.0f,
            verticalAccuracyM = 5.0f,
            speedAccuracyMps = 0.3f,
            bearingAccuracyDeg = 5.0f,
            elapsedMs = 10_000L
        )
        assertEquals(1, afterReset.sampleCount)
        assertEquals(4.0f, afterReset.bestAccuracyM!!, 0.01f)
        assertEquals(4.0f, afterReset.worstAccuracyM!!, 0.01f)
    }

    private fun session(
        id: Long,
        mode: ActivityMode,
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        durationMs: Long,
        distanceMeters: Double,
        elevationGainMeters: Double = 50.0
    ): TrackSessionEntity = TrackSessionEntity(
        id = id,
        mode = mode.name,
        name = name,
        startTimeMs = startTimeMs,
        endTimeMs = endTimeMs,
        durationMs = durationMs,
        distanceMeters = distanceMeters,
        maxSpeedMps = 10f,
        avgSpeedMps = 5f,
        elevationGainMeters = elevationGainMeters,
        elevationLossMeters = 10.0,
        maxAltitudeMeters = 1100.0,
        minAltitudeMeters = 1000.0,
        avgHeartRate = 120,
        maxHeartRate = 135,
        peakTotalG = 0.5f,
        peakLateralG = 0.4f,
        lapCount = 0,
        bestLapMs = null,
        pointCount = 3
    )

    private fun point(
        sessionId: Long,
        timestampMs: Long,
        lat: Double,
        lon: Double,
        alt: Double,
        speedMps: Float,
        hr: Int? = null,
        gTotal: Float = 0f
    ): TrackPointEntity = TrackPointEntity(
        sessionId = sessionId,
        timestampMs = timestampMs,
        latitude = lat,
        longitude = lon,
        altitudeMeters = alt,
        speedMps = speedMps,
        bearingDegrees = 0f,
        accuracyMeters = 4.0f,
        heartRateBpm = hr,
        lateralG = gTotal * 0.7f,
        longitudinalG = gTotal * 0.3f,
        totalG = gTotal,
        lapNumber = 0
    )
}
