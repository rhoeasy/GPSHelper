package com.example.track

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
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
class KartingTrackTimingTest {
    private val course = KartingTimingCourse(KartingTimingLine(at(0.0, 6.0), at(0.0, -6.0)))

    @Test
    fun liveAndReplayTimeTheSameLapWithoutIncludingThePitOrPartialLaps() {
        val points = route()
        val live = KartingTrackTiming(course)
        points.forEachIndexed(live::addPoint)
        val replay = timeKartingTrack(course, points)

        assertEquals(live.laps, replay.laps)
        assertEquals(16_000L, replay.laps.single().lapDurationMs)
        assertEquals(8f, replay.laps.single().maxSpeedMps)
        assertEquals(listOf(0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0), replay.lapNumbers(points.size).toList())
        assertNull(replay.startedAtMs)
        assertTrue(usesGpsTimingClock(points))

        val old = points.map { it.copy(fixElapsedMs = null) }
        assertFalse(usesGpsTimingClock(old))
        assertEquals(16_000L, timeKartingTrack(course, old).laps.single().lapDurationMs)
    }

    @Test
    fun recomputingKeepsPointIdsAndCoachReportsAndUpdatesOnlyLapLabels() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, TrackDatabase::class.java).build()
        try {
            val dao = db.trackDao()
            val sessionId = dao.saveCompleteSession(
                session(),
                route(), emptyList()
            )
            val report = TrackAiReportEntity(sessionId, "test", "Coach note", 1L)
            dao.upsertAiReport(report)
            val points = dao.getPointsForSession(sessionId)
            val timing = timeKartingTrack(course, points)
            val groups = timing.labelPoints(points).filter { it.lapNumber > 0 }
                .groupBy({ it.lapNumber }, { it.id })

            assertTrue(dao.replaceKartingTiming(sessionId, course.encode(), timing.laps, groups))
            assertEquals(course, decodeKartingCourse(dao.getSessionById(sessionId)?.kartingCourseJson))
            assertEquals(points.map { it.id }, dao.getPointsForSession(sessionId).map { it.id })
            assertEquals(report, dao.getAiReport(sessionId))
            assertEquals(16_000L, dao.getLapsForSession(sessionId).single().lapDurationMs)
            assertEquals(1, dao.getSessionById(sessionId)?.lapCount)
            assertTrue(dao.replaceKartingTiming(sessionId, course.encode(), emptyList(), emptyMap()))
            assertTrue(dao.getLapsForSession(sessionId).isEmpty())
            assertTrue(dao.getPointsForSession(sessionId).all { it.lapNumber == 0 })
        } finally {
            db.close()
        }
    }

    @Test
    fun upgradingFromVersionSixPreservesOldTrackPointsAndLapData() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "karting-migration-test.db"
        context.deleteDatabase(name)
        val points = route().map { it.copy(fixElapsedMs = null, timingBreakBefore = null, speedUsable = null) }
        val lap = timeKartingTrack(course, points).laps.single()
        val initial = Room.databaseBuilder(context, TrackDatabase::class.java, name).build()
        val sessionId = try {
            initial.trackDao().saveCompleteSession(
                session().copy(lapCount = 1, bestLapMs = lap.lapDurationMs), points, listOf(lap)
            )
        } finally {
            initial.close()
        }
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("ALTER TABLE track_sessions DROP COLUMN kartingCourseJson")
            db.execSQL("ALTER TABLE track_points DROP COLUMN fixElapsedMs")
            db.execSQL("ALTER TABLE track_points DROP COLUMN timingBreakBefore")
            db.execSQL("ALTER TABLE track_points DROP COLUMN speedUsable")
            db.version = 6
        }
        val migrated = Room.databaseBuilder(context, TrackDatabase::class.java, name)
            .addMigrations(TrackDatabase.MIGRATION_6_7).build()
        try {
            val dao = migrated.trackDao()
            val old = dao.getPointsForSession(sessionId)
            assertEquals(points.map { it.speedMps }, old.map { it.measuredSpeedMps })
            assertEquals(points.size, old.size)
            assertFalse(usesGpsTimingClock(old))
            assertEquals(lap.lapDurationMs, dao.getLapsForSession(sessionId).single().lapDurationMs)
            assertNull(dao.getSessionById(sessionId)?.kartingCourseJson)
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
        Unit
    }

    private fun session() = TrackSessionEntity(
        name = "Track day", mode = ActivityMode.KARTING.name, startTimeMs = 0L, endTimeMs = 30_000L,
        durationMs = 30_000L, distanceMeters = 100.0, maxSpeedMps = 8f, avgSpeedMps = 4f,
        elevationGainMeters = 0.0, elevationLossMeters = 0.0, minAltitudeMeters = null,
        maxAltitudeMeters = null, lapCount = 0, bestLapMs = null, avgHeartRate = null,
        maxHeartRate = null, pointCount = route().size
    )

    private fun route(): List<TrackPointEntity> = listOf(
        -2.0 to 13.0, -2.0 to 0.0, 8.0 to 0.0, 22.0 to 0.0,
        30.0 to 10.0, 30.0 to 15.0, 3.0 to 15.0, -12.0 to 15.0,
        -12.0 to 1.0, -2.0 to 0.0, 8.0 to 0.0, 12.0 to 0.0
    ).mapIndexed { index, (x, y) ->
        val location = at(x, y)
        TrackPointEntity(
            sessionId = 0L, timestampMs = 90_000L + index * 2_000L, fixElapsedMs = 10_000L + index * 2_000L,
            latitude = location.latitude, longitude = location.longitude, altitudeMeters = null,
            speedMps = if (index == 3) 80f else 8f, accuracyMeters = 3f, bearingDegrees = null,
            heartRateBpm = 120, lapNumber = 0, timingBreakBefore = index == 11, speedUsable = index != 3
        )
    }

    private fun at(x: Double, y: Double) = KartingTimingCoordinate(y / 110_540.0, x / 111_320.0)
}
