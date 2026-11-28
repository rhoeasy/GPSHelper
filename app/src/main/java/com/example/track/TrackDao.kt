package com.example.track

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM track_sessions ORDER BY startTimeMs DESC")
    fun observeAllSessions(): Flow<List<TrackSessionEntity>>

    @Query("SELECT * FROM track_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: Long): TrackSessionEntity?

    @Query("SELECT * FROM track_sessions WHERE name = :name AND mode = :mode AND stepCount IS NULL")
    suspend fun getSessionsMissingStepCount(name: String, mode: String): List<TrackSessionEntity>

    @Query("UPDATE track_sessions SET stepCount = :stepCount WHERE id = :sessionId AND stepCount IS NULL")
    suspend fun setMissingStepCount(sessionId: Long, stepCount: Int): Int

    @Query("SELECT * FROM track_points WHERE sessionId = :sessionId ORDER BY COALESCE(fixElapsedMs, timestampMs), id")
    suspend fun getPointsForSession(sessionId: Long): List<TrackPointEntity>

    @Query("SELECT * FROM track_laps WHERE sessionId = :sessionId ORDER BY lapNumber ASC")
    suspend fun getLapsForSession(sessionId: Long): List<TrackLapEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: TrackSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoints(points: List<TrackPointEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLaps(laps: List<TrackLapEntity>)

    @Query("UPDATE track_sessions SET kartingCourseJson = :courseJson, lapCount = :lapCount, bestLapMs = :bestLapMs WHERE id = :sessionId AND mode = 'KARTING'")
    suspend fun updateKartingTiming(sessionId: Long, courseJson: String, lapCount: Int, bestLapMs: Long?): Int

    @Query("DELETE FROM track_laps WHERE sessionId = :sessionId")
    suspend fun deleteLapsForSession(sessionId: Long)

    @Query("UPDATE track_points SET lapNumber = 0 WHERE sessionId = :sessionId AND lapNumber != 0")
    suspend fun clearPointLapNumbers(sessionId: Long)

    @Query("UPDATE track_points SET lapNumber = :lapNumber WHERE sessionId = :sessionId AND id IN (:ids)")
    suspend fun labelKartingPoints(sessionId: Long, lapNumber: Int, ids: List<Long>)

    @Transaction
    suspend fun replaceKartingTiming(
        sessionId: Long,
        courseJson: String,
        laps: List<TrackLapEntity>,
        pointIdsByLap: Map<Int, List<Long>>
    ): Boolean {
        val updated = updateKartingTiming(sessionId, courseJson, laps.size, laps.minOfOrNull { it.lapDurationMs })
        if (updated != 1) return false
        deleteLapsForSession(sessionId)
        insertLaps(laps)
        clearPointLapNumbers(sessionId)
        for ((lapNumber, ids) in pointIdsByLap) {
            for (chunk in ids.chunked(500)) labelKartingPoints(sessionId, lapNumber, chunk)
        }
        return true
    }

    @Query("DELETE FROM track_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: Long)

    @Query("DELETE FROM track_sessions WHERE name = :name")
    suspend fun deleteSessionsByName(name: String)

    @Query("SELECT * FROM track_points WHERE sessionId = :sessionId ORDER BY timestampMs ASC LIMIT 1")
    suspend fun getFirstPointForSession(sessionId: Long): TrackPointEntity?

    @Query("SELECT * FROM track_points WHERE sessionId = :sessionId ORDER BY timestampMs DESC LIMIT 1")
    suspend fun getLastPointForSession(sessionId: Long): TrackPointEntity?

    @Transaction
    suspend fun saveCompleteSession(
        session: TrackSessionEntity,
        points: List<TrackPointEntity>,
        laps: List<TrackLapEntity>
    ): Long {
        val sessionId = insertSession(session)
        if (points.isNotEmpty()) {
            insertPoints(points.map { it.copy(sessionId = sessionId) })
        }
        if (laps.isNotEmpty()) {
            insertLaps(laps.map { it.copy(sessionId = sessionId) })
        }
        return sessionId
    }

    @Transaction
    suspend fun saveMergedSession(
        mergedSession: TrackSessionEntity,
        mergedPoints: List<TrackPointEntity>,
        deleteSourceIds: List<Long>
    ): Long {
        val newId = saveCompleteSession(
            session = mergedSession,
            points = mergedPoints,
            laps = emptyList()
        )
        deleteSourceIds.forEach { srcId ->
            if (srcId != newId) {
                deleteSessionById(srcId)
            }
        }
        return newId
    }

    @Query("SELECT * FROM track_ai_reports WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getAiReport(sessionId: Long): TrackAiReportEntity?

    @Query("SELECT * FROM track_ai_reports WHERE sessionId = :sessionId LIMIT 1")
    fun observeAiReport(sessionId: Long): Flow<TrackAiReportEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAiReport(report: TrackAiReportEntity)

    @Query("DELETE FROM track_ai_reports WHERE sessionId = :sessionId")
    suspend fun deleteAiReport(sessionId: Long)

    @Query("SELECT * FROM ai_analysis_history ORDER BY generatedAtMs DESC")
    fun observeAllAiAnalysisHistory(): Flow<List<AiAnalysisRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAiAnalysisRecord(record: AiAnalysisRecordEntity): Long

    @Transaction
    suspend fun saveAiCoachResult(record: AiAnalysisRecordEntity) {
        val sessionId = requireNotNull(record.sessionId)
        if (getSessionById(sessionId) == null) return
        val historyId = insertAiAnalysisRecord(record)
        upsertAiReport(TrackAiReportEntity(sessionId, record.modelId, record.reportText, record.generatedAtMs, historyId))
    }

    @Query("DELETE FROM track_ai_reports WHERE historyRecordId = :recordId")
    suspend fun deleteReportLinkedToHistory(recordId: Long)

    @Query("DELETE FROM track_ai_reports")
    suspend fun clearAiReports()

    @Query("DELETE FROM ai_analysis_history WHERE id = :recordId")
    suspend fun deleteAiAnalysisRecord(recordId: Long)

    @Query("DELETE FROM ai_analysis_history")
    suspend fun clearAllAiAnalysisHistory()

    @Transaction
    suspend fun deleteAiAnalysisWithReport(recordId: Long) {
        deleteReportLinkedToHistory(recordId)
        deleteAiAnalysisRecord(recordId)
    }

    @Transaction
    suspend fun clearAiHistoryWithReports() {
        clearAiReports()
        clearAllAiAnalysisHistory()
    }
}
