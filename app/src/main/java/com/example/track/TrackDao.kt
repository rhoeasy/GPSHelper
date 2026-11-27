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

    @Query("SELECT * FROM track_points WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    suspend fun getPointsForSession(sessionId: Long): List<TrackPointEntity>

    @Query("SELECT * FROM track_laps WHERE sessionId = :sessionId ORDER BY lapNumber ASC")
    suspend fun getLapsForSession(sessionId: Long): List<TrackLapEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: TrackSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoints(points: List<TrackPointEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLaps(laps: List<TrackLapEntity>)

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAiReport(report: TrackAiReportEntity)

    @Query("DELETE FROM track_ai_reports WHERE sessionId = :sessionId")
    suspend fun deleteAiReport(sessionId: Long)

    @Query("SELECT * FROM ai_analysis_history ORDER BY generatedAtMs DESC")
    fun observeAllAiAnalysisHistory(): Flow<List<AiAnalysisRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAiAnalysisRecord(record: AiAnalysisRecordEntity): Long

    @Query("DELETE FROM ai_analysis_history WHERE id = :recordId")
    suspend fun deleteAiAnalysisRecord(recordId: Long)

    @Query("DELETE FROM ai_analysis_history")
    suspend fun clearAllAiAnalysisHistory()
}
