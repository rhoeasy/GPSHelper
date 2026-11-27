package com.example.track

import com.example.sensor.CompassMath

private const val ELEVATION_DEADBAND_M = 2.5
private const val ROUGHNESS_THRESHOLD_G = 0.10f
private const val MIN_ROUGH_SECTION_MS = 1_000L
internal const val MAX_CONTINUOUS_TRACK_INTERVAL_MS = 4_000L

internal data class RecordedAltitude(val meters: Double, val fromBarometer: Boolean)

internal class TrackElevation {
    private var seaLevelPressureHpa: Float? = null
    private val window = ArrayDeque<Double>()
    private var anchor: Double? = null
    private var fromBarometer: Boolean? = null
    private var lastAltitudeMeters: Double? = null
    private var sourceOffsetMeters = 0.0

    var gainMeters = 0.0
        private set
    var lossMeters = 0.0
        private set
    var minMeters: Double? = null
        private set
    var maxMeters: Double? = null
        private set

    fun record(gpsAltitudeM: Double?, pressureHpa: Float?): RecordedAltitude? {
        val gps = gpsAltitudeM?.takeIf { it.isFinite() }
        val pressure = pressureHpa?.takeIf { it in 200f..1200f }
        if (seaLevelPressureHpa == null && pressure != null && gps != null) {
            seaLevelPressureHpa = CompassMath.calibrateSeaLevelPressureHpa(pressure, gps)
        }
        val qnh = seaLevelPressureHpa
        val reading = when {
            pressure != null && qnh != null ->
                RecordedAltitude(CompassMath.pressureToAltitudeMeters(qnh, pressure).toDouble(), true)
            gps != null -> RecordedAltitude(gps, false)
            else -> {
                breakInterval()
                return null
            }
        }

        // The first point from a new source keeps the profile continuous; later points use its deltas.
        if (fromBarometer != reading.fromBarometer) {
            val previousAltitude = lastAltitudeMeters
            breakInterval()
            sourceOffsetMeters = previousAltitude?.minus(reading.meters) ?: 0.0
        }
        val sample = reading.copy(meters = reading.meters + sourceOffsetMeters)
        fromBarometer = sample.fromBarometer
        lastAltitudeMeters = sample.meters
        minMeters = minMeters?.let { minOf(it, sample.meters) } ?: sample.meters
        maxMeters = maxMeters?.let { maxOf(it, sample.meters) } ?: sample.meters

        window.addLast(sample.meters)
        if (window.size > 3) window.removeFirst()
        val smoothed = window.average()
        val baseline = anchor
        if (baseline == null) {
            anchor = smoothed
        } else {
            val delta = smoothed - baseline
            if (delta >= ELEVATION_DEADBAND_M) {
                gainMeters += delta
                anchor = smoothed
            } else if (delta <= -ELEVATION_DEADBAND_M) {
                lossMeters -= delta
                anchor = smoothed
            }
        }
        return sample
    }

    fun breakInterval() {
        window.clear()
        anchor = null
        fromBarometer = null
        lastAltitudeMeters = null
        sourceOffsetMeters = 0.0
    }
}

/** Ranges of point indices. Each reading describes the leg ending at that point. */
internal fun findRoughTrackSegments(points: List<TrackPointEntity>): List<IntRange> {
    val segments = mutableListOf<IntRange>()
    var start = -1

    fun finish(end: Int) {
        if (start >= 0 && end - start >= 2 && points[end].timestampMs - points[start].timestampMs >= MIN_ROUGH_SECTION_MS) {
            segments.add(start..end)
        }
        start = -1
    }

    for (i in 1 until points.size) {
        val duration = points[i].timestampMs - points[i - 1].timestampMs
        val rmsG = points[i].verticalVibrationG
        if (duration in 1L..MAX_CONTINUOUS_TRACK_INTERVAL_MS && rmsG != null && rmsG.isFinite() && rmsG >= ROUGHNESS_THRESHOLD_G) {
            if (start < 0) start = i - 1
        } else {
            finish(i - 1)
        }
    }
    finish(points.lastIndex)
    return segments
}
