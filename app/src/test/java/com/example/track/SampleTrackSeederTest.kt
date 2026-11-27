package com.example.track

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
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
class SampleTrackSeederTest {
    @Test
    fun `existing hiking sample gains steps without touching another track of the same name`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, TrackDatabase::class.java).build()
        try {
            context.getSharedPreferences("track_sample_prefs", Context.MODE_PRIVATE).edit()
                .putBoolean("sample_tracks_seeded_v1", true)
                .putBoolean("sample_karting_f1_v2", true)
                .putBoolean("sample_offroad_seeded_v1", true)
                .apply()
            val dao = database.trackDao()
            val sample = SampleTrackSeeder.buildSampleHikingTrack(endTimeMs = 1_750_000_000_000L)
            val oldSession = sample.session.copy(stepCount = null)
            val sampleId = dao.saveCompleteSession(oldSession, sample.points, sample.laps)
            val userId = dao.saveCompleteSession(oldSession, sample.points.map { it.copy(latitude = it.latitude + 0.01) }, emptyList())

            assertTrue(SampleTrackSeeder.seedOnceIfNeeded(context, dao))

            assertEquals(sample.session.stepCount, dao.getSessionById(sampleId)?.stepCount)
            assertEquals(sample.points.size, dao.getPointsForSession(sampleId).size)
            assertNull(dao.getSessionById(userId)?.stepCount)
            assertEquals(setOf(sampleId, userId), dao.observeAllSessions().first().map { it.id }.toSet())
            assertFalse(SampleTrackSeeder.seedOnceIfNeeded(context, dao))
        } finally {
            database.close()
        }
    }
}
