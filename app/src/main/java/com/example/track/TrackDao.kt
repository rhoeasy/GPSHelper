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
}
