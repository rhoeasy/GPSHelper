package com.example.track

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

data class AiAnalysisExcerpt(val activityMode: String, val reportText: String)

enum class ActivityMode(val labelZh: String) {
    KARTING("卡丁车"),
    HIKING("徒步"),
    OFFROAD_CROSSING("越野穿越");

    /** True for mountain / elevation-focused trail modes (Hiking and Moto Off-road Crossing). */
    val isElevationTrail: Boolean
        get() = this == HIKING || this == OFFROAD_CROSSING

    companion object {
        fun fromName(name: String): ActivityMode =
            entries.firstOrNull { it.name == name } ?: KARTING
    }
}

@Entity(tableName = "track_sessions")
data class TrackSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val mode: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val durationMs: Long,
    val distanceMeters: Double,
    val maxSpeedMps: Float,
    val avgSpeedMps: Float,
    val elevationGainMeters: Double,
    val elevationLossMeters: Double,
    val minAltitudeMeters: Double?,
    val maxAltitudeMeters: Double?,
    val lapCount: Int,
    val bestLapMs: Long?,
    val avgHeartRate: Int?,
    val maxHeartRate: Int?,
    val pointCount: Int,
    val peakTotalG: Float = 0f,
    val peakLateralG: Float = 0f,
    val stepCount: Int? = null,
    val kartingCourseJson: String? = null
) {
    val activityMode: ActivityMode
        get() = ActivityMode.fromName(mode)
}

@Entity(
    tableName = "track_points",
    foreignKeys = [
        ForeignKey(
            entity = TrackSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: Long,
    val timestampMs: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val speedMps: Float,
    val accuracyMeters: Float?,
    val bearingDegrees: Float?,
    val heartRateBpm: Int?,
    val lapNumber: Int,
    val lateralG: Float = 0f,
    val longitudinalG: Float = 0f,
    val totalG: Float = 0f,
    val altitudeFromBarometer: Boolean? = null,
    val verticalVibrationG: Float? = null,
    val fixElapsedMs: Long? = null,
    val timingBreakBefore: Boolean? = null,
    val speedUsable: Boolean? = null
) {
    val measuredSpeedMps: Float?
        get() = speedMps.takeIf { speedUsable != false && it.isFinite() && it >= 0f }
}

@Entity(
    tableName = "track_laps",
    foreignKeys = [
        ForeignKey(
            entity = TrackSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class TrackLapEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: Long,
    val lapNumber: Int,
    val lapDurationMs: Long,
    val lapDistanceMeters: Double,
    val maxSpeedMps: Float,
    val avgHeartRate: Int?,
    val peakG: Float = 0f
)

@Entity(
    tableName = "track_ai_reports",
    foreignKeys = [
        ForeignKey(
            entity = TrackSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TrackAiReportEntity(
    @PrimaryKey val sessionId: Long,
    val modelId: String,
    val reportText: String,
    val generatedAtMs: Long,
    val historyRecordId: Long? = null
)

@Entity(
    tableName = "ai_analysis_history",
    indices = [
        Index("generatedAtMs"),
        Index("sessionId")
    ]
)
data class AiAnalysisRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: Long?,
    val sessionName: String,
    val activityMode: String,
    val modelId: String,
    val reportText: String,
    val summaryMetrics: String,
    val generatedAtMs: Long
) {
    val mode: ActivityMode
        get() = ActivityMode.fromName(activityMode)
}
