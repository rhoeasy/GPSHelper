package com.example.track

import org.json.JSONArray
import org.json.JSONObject

internal fun KartingTimingCourse.encode(): String {
    fun KartingTimingLine.coordinates() = JSONArray(listOf(left.latitude, left.longitude, right.latitude, right.longitude))
    return JSONObject().put("finish", finish.coordinates())
        .apply { checkpoint?.let { put("checkpoint", it.coordinates()) } }.toString()
}

internal fun decodeKartingCourse(value: String?): KartingTimingCourse? = value?.let {
    runCatching {
        fun JSONArray.line() = KartingTimingLine(
            KartingTimingCoordinate(getDouble(0), getDouble(1)),
            KartingTimingCoordinate(getDouble(2), getDouble(3))
        ).also { require(it.isUsable()) }
        val json = JSONObject(it)
        KartingTimingCourse(json.getJSONArray("finish").line(), json.optJSONArray("checkpoint")?.line())
    }.getOrNull()
}

internal fun usesGpsTimingClock(points: List<TrackPointEntity>): Boolean =
    points.isNotEmpty() && points.all { it.fixElapsedMs != null }

internal class KartingTrackTiming(course: KartingTimingCourse, private val useGpsClock: Boolean = true) {
    private val timer = KartingLapTimer(course)
    private var lapStartIndex = 0
    private var speed: Float = 0f
    private var pulseSum = 0L
    private var pulseCount = 0
    private var peakG = 0f

    var startedAtMs: Long? = null
        private set
    val laps = mutableListOf<TrackLapEntity>()
    private val completedRanges = mutableListOf<IntRange>()

    fun interrupt() {
        timer.interrupt()
        startedAtMs = null
    }

    fun addPoint(index: Int, point: TrackPointEntity) {
        if (point.timingBreakBefore == true) interrupt()
        val time = if (useGpsClock) requireNotNull(point.fixElapsedMs) else point.timestampMs
        when (val event = timer.onFix(KartingTimingFix(
            KartingTimingCoordinate(point.latitude, point.longitude), time, point.accuracyMeters
        ))) {
            is KartingLapEvent.LapCompleted -> {
                completedRanges.add(lapStartIndex until index)
                laps.add(TrackLapEntity(
                    sessionId = point.sessionId,
                    lapNumber = laps.size + 1,
                    lapDurationMs = event.durationMs,
                    lapDistanceMeters = event.distanceMeters,
                    maxSpeedMps = speed,
                    avgHeartRate = if (pulseCount > 0) (pulseSum / pulseCount).toInt() else null,
                    peakG = peakG
                ))
                startLap(index, event.crossing.elapsedMs)
            }
            is KartingLapEvent.TimingStarted -> startLap(index, event.crossing.elapsedMs)
            KartingLapEvent.TimingInterrupted -> startedAtMs = null
            null -> Unit
        }
        if (startedAtMs != null) {
            point.measuredSpeedMps?.let { speed = maxOf(speed, it) }
            point.heartRateBpm?.let {
                pulseSum += it
                pulseCount++
            }
            peakG = maxOf(peakG, point.totalG)
        }
    }

    private fun startLap(index: Int, time: Long) {
        lapStartIndex = index
        startedAtMs = time
        speed = 0f
        pulseSum = 0
        pulseCount = 0
        peakG = 0f
    }

    fun lapNumbers(pointCount: Int): IntArray =
        IntArray(pointCount).also { labels ->
            completedRanges.forEachIndexed { lap, range -> labels.fill(lap + 1, range.first, range.last + 1) }
        }

    fun labelPoints(points: List<TrackPointEntity>): List<TrackPointEntity> {
        val labels = lapNumbers(points.size)
        return points.mapIndexed { index, point -> point.copy(lapNumber = labels[index]) }
    }
}

internal fun timeKartingTrack(course: KartingTimingCourse, points: List<TrackPointEntity>): KartingTrackTiming =
    KartingTrackTiming(course, usesGpsTimingClock(points)).apply { points.forEachIndexed(::addPoint) }
