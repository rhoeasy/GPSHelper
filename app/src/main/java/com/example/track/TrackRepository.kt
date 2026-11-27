package com.example.track

import android.content.Context
import kotlinx.coroutines.flow.Flow

data class TrackSessionDetail(
    val session: TrackSessionEntity,
    val points: List<TrackPointEntity>,
    val laps: List<TrackLapEntity>
)

class TrackRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dao: TrackDao = TrackDatabase.getInstance(appContext).trackDao()

    val allSessions: Flow<List<TrackSessionEntity>> = dao.observeAllSessions()
    val allAiAnalysisHistory: Flow<List<AiAnalysisRecordEntity>> = dao.observeAllAiAnalysisHistory()

    suspend fun ensureSampleTracksSeeded(): Boolean {
        return SampleTrackSeeder.seedOnceIfNeeded(appContext, dao)
    }

    suspend fun loadSessionDetail(sessionId: Long): TrackSessionDetail? {
        val session = dao.getSessionById(sessionId) ?: return null
        val points = dao.getPointsForSession(sessionId)
        val laps = dao.getLapsForSession(sessionId)
        return TrackSessionDetail(session = session, points = points, laps = laps)
    }

    suspend fun deleteSession(sessionId: Long) {
        dao.deleteSessionById(sessionId)
    }

    suspend fun loadSessionEndpoints(sessionIds: List<Long>): Map<Long, TrackEndpoints> {
        val result = HashMap<Long, TrackEndpoints>(sessionIds.size)
        for (id in sessionIds) {
            val first = dao.getFirstPointForSession(id) ?: continue
            val last = dao.getLastPointForSession(id) ?: first
            result[id] = TrackEndpoints(
                sessionId = id,
                startLatitude = first.latitude,
                startLongitude = first.longitude,
                endLatitude = last.latitude,
                endLongitude = last.longitude
            )
        }
        return result
    }

    suspend fun saveMergedSession(
        mergedDetail: TrackSessionDetail,
        deleteSourceIds: List<Long>
    ): Long {
        return dao.saveMergedSession(
            mergedSession = mergedDetail.session,
            mergedPoints = mergedDetail.points,
            deleteSourceIds = deleteSourceIds
        )
    }

    suspend fun getAiReport(sessionId: Long): TrackAiReportEntity? {
        return dao.getAiReport(sessionId)
    }

    suspend fun saveAiReport(report: TrackAiReportEntity) {
        dao.upsertAiReport(report)
    }

    suspend fun deleteAiReport(sessionId: Long) {
        dao.deleteAiReport(sessionId)
    }

    suspend fun saveAiAnalysisRecord(record: AiAnalysisRecordEntity): Long {
        return dao.insertAiAnalysisRecord(record)
    }

    suspend fun deleteAiAnalysisRecord(recordId: Long) {
        dao.deleteAiAnalysisRecord(recordId)
    }

    suspend fun clearAllAiAnalysisHistory() {
        dao.clearAllAiAnalysisHistory()
    }
}
