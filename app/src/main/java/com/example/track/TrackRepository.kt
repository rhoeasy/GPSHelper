package com.example.track

import android.content.Context
import kotlinx.coroutines.flow.Flow

data class TrackSessionDetail(
    val session: TrackSessionEntity,
    val points: List<TrackPointEntity>,
    val laps: List<TrackLapEntity>
)

class TrackRepository(context: Context) {
    private val dao: TrackDao = TrackDatabase.getInstance(context).trackDao()

    val allSessions: Flow<List<TrackSessionEntity>> = dao.observeAllSessions()

    suspend fun loadSessionDetail(sessionId: Long): TrackSessionDetail? {
        val session = dao.getSessionById(sessionId) ?: return null
        val points = dao.getPointsForSession(sessionId)
        val laps = dao.getLapsForSession(sessionId)
        return TrackSessionDetail(session = session, points = points, laps = laps)
    }

    suspend fun deleteSession(sessionId: Long) {
        dao.deleteSessionById(sessionId)
    }
}
