package com.example.track

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToLong

data class KartingTimingCoordinate(val latitude: Double, val longitude: Double) {
    internal fun isValid(): Boolean = latitude in -90.0..90.0 && longitude in -180.0..180.0
}

/** The two ends are ordered from the driver's left to right in the valid direction of travel. */
data class KartingTimingLine(val left: KartingTimingCoordinate, val right: KartingTimingCoordinate) {
    fun reversed() = KartingTimingLine(right, left)

    fun isUsable(): Boolean = left.isValid() && right.isValid() && LocalTimingLine(this).length in 2.0..100.0
}

data class KartingTimingCourse(val finish: KartingTimingLine, val checkpoint: KartingTimingLine? = null)

internal data class KartingTimingFix(val coordinate: KartingTimingCoordinate, val elapsedMs: Long, val accuracyM: Float?)

internal data class KartingLineCrossing(val elapsedMs: Long, val fractionOfFixInterval: Double)

internal sealed interface KartingLapEvent {
    data class TimingStarted(val crossing: KartingLineCrossing) : KartingLapEvent
    data class LapCompleted(val crossing: KartingLineCrossing, val durationMs: Long) : KartingLapEvent
    data object TimingInterrupted : KartingLapEvent
}

/** A recording starts outside timing; only a valid forward crossing can open an official lap. */
internal class KartingLapTimer(
    course: KartingTimingCourse,
    private val minLapDurationMs: Long = 15_000L,
    private val maxFixGapMs: Long = 2_000L,
    private val minLapDistanceM: Double = 25.0
) {
    private val finish = LocalTimingLine(course.finish)
    private val checkpoint = course.checkpoint?.let(::LocalTimingLine)
    private val accuracyLimit = minOf(8.0, finish.length / 2.0, checkpoint?.length?.div(2.0) ?: 8.0)
    private var previous: KartingTimingFix? = null
    private var openLapAtMs: Long? = null
    private var travelledMeters = 0.0
    private var crossedCheckpoint = false

    fun interrupt(): KartingLapEvent? {
        val wasTiming = openLapAtMs != null
        previous = null
        openLapAtMs = null
        travelledMeters = 0.0
        crossedCheckpoint = false
        return if (wasTiming) KartingLapEvent.TimingInterrupted else null
    }

    fun onFix(fix: KartingTimingFix): KartingLapEvent? {
        val before = previous
        if (before != null && fix.elapsedMs <= before.elapsedMs) return null
        if (!fix.coordinate.isValid() || fix.accuracyM == null || fix.accuracyM.toDouble() !in 0.0..accuracyLimit) {
            return interrupt()
        }
        if (before == null) {
            previous = fix
            return null
        }
        if (fix.elapsedMs - before.elapsedMs > maxFixGapMs) {
            val event = interrupt()
            previous = fix
            return event
        }
        previous = fix

        val finishCrossing = finish.crossing(before, fix)
        val checkpointCrossing = checkpoint?.crossing(before, fix)
        val stepMeters = finish.distance(before.coordinate, fix.coordinate)
        val startedAt = openLapAtMs
        if (finishCrossing == null) {
            if (startedAt != null) {
                travelledMeters += stepMeters
                if (checkpointCrossing != null) crossedCheckpoint = true
            }
            return null
        }
        val checkedBeforeFinish = crossedCheckpoint ||
            (checkpointCrossing != null && checkpointCrossing.fractionOfFixInterval < finishCrossing.fractionOfFixInterval)
        val event = if (startedAt == null) {
            KartingLapEvent.TimingStarted(finishCrossing)
        } else {
            val duration = finishCrossing.elapsedMs - startedAt
            val distance = travelledMeters + stepMeters * finishCrossing.fractionOfFixInterval
            if (duration < minLapDurationMs || distance < minLapDistanceM) {
                travelledMeters += stepMeters
                if (checkpointCrossing != null) crossedCheckpoint = true
                return null
            }
            if (checkpoint != null && !checkedBeforeFinish) {
                KartingLapEvent.TimingStarted(finishCrossing)
            } else {
                KartingLapEvent.LapCompleted(finishCrossing, duration)
            }
        }

        openLapAtMs = finishCrossing.elapsedMs
        travelledMeters = stepMeters * (1.0 - finishCrossing.fractionOfFixInterval)
        crossedCheckpoint = checkpointCrossing != null &&
            checkpointCrossing.fractionOfFixInterval > finishCrossing.fractionOfFixInterval
        return event
    }
}

private data class TimingVector(val x: Double, val y: Double) {
    operator fun minus(other: TimingVector) = TimingVector(x - other.x, y - other.y)
    val length: Double get() = hypot(x, y)
    fun cross(other: TimingVector): Double = x * other.y - y * other.x
}

private class LocalTimingLine(line: KartingTimingLine) {
    private val origin = line.left
    private val metresPerLongitude = 111_320.0 * cos(Math.toRadians((line.left.latitude + line.right.latitude) / 2.0))
    private val gate = project(line.right)
    val length: Double get() = gate.length

    private fun project(point: KartingTimingCoordinate): TimingVector {
        val longitudeDelta = (point.longitude - origin.longitude + 540.0) % 360.0 - 180.0
        return TimingVector(longitudeDelta * metresPerLongitude, (point.latitude - origin.latitude) * 110_540.0)
    }

    fun distance(from: KartingTimingCoordinate, to: KartingTimingCoordinate): Double = (project(to) - project(from)).length

    fun crossing(before: KartingTimingFix, after: KartingTimingFix): KartingLineCrossing? {
        val a = project(before.coordinate)
        val b = project(after.coordinate)
        val motion = b - a
        val normalComponent = gate.cross(motion)
        if (normalComponent < motion.length * length * 0.5 || normalComponent <= 0.0) return null
        if (gate.cross(a) >= 0.0 || gate.cross(b) < 0.0) return null

        // Adapted from Blackbox's line_segment_intersection_f64 (MIT, assets/licenses/blackbox.txt).
        // t is along the two GPS fixes; u is along the timing line, never a time fraction.
        val gap = TimingVector(-a.x, -a.y)
        val denominator = motion.cross(gate)
        if (abs(denominator) < 1e-9) return null
        val t = gap.cross(gate) / denominator
        val u = gap.cross(motion) / denominator
        if (t !in 0.0..1.0 || u !in 0.0..1.0) return null
        return KartingLineCrossing(before.elapsedMs + (t * (after.elapsedMs - before.elapsedMs)).roundToLong(), t)
    }
}
