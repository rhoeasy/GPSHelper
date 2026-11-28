package com.example.track

import kotlin.math.abs

/**
 * Lightweight start/end coordinates of a stored track session used to show end-to-start
 * connection proximity in the track list before loading full point histories.
 */
data class TrackEndpoints(
    val sessionId: Long,
    val startLatitude: Double,
    val startLongitude: Double,
    val endLatitude: Double,
    val endLongitude: Double
)

enum class MergeJunctionQuality(val labelZh: String) {
    SEAMLESS("首尾无缝衔接"),
    NEARBY("起终点较近"),
    FAR("断点跨度较远")
}

data class TrackMergeJunction(
    val fromSessionId: Long,
    val toSessionId: Long,
    val fromName: String,
    val toName: String,
    val gapMeters: Double,
    val gapDurationMs: Long,
    val quality: MergeJunctionQuality
)

data class TrackMergePreview(
    val mode: ActivityMode,
    val orderedDetails: List<TrackSessionDetail>,
    val junctions: List<TrackMergeJunction>,
    val defaultMergedName: String,
    val mergedDurationMs: Long,
    val mergedDistanceMeters: Double,
    val mergedElevationGainMeters: Double,
    val mergedElevationLossMeters: Double,
    val totalPointCount: Int
) {
    val orderedSessions: List<TrackSessionEntity>
        get() = orderedDetails.map { it.session }

    val hasFarJunction: Boolean
        get() = junctions.any { it.quality == MergeJunctionQuality.FAR }

    val maxGapMeters: Double
        get() = junctions.maxOfOrNull { it.gapMeters } ?: 0.0
}

/**
 * Pure offline track merging engine supporting end-to-start reconnection of multiple
 * same-type tracks for [ActivityMode.HIKING] and [ActivityMode.OFFROAD_CROSSING].
 *
 * Karting ([ActivityMode.KARTING]) is intentionally excluded from track merging.
 */
object TrackMergeEngine {

    /** Gaps <= 150m are considered seamless reconnections (e.g., restarting at camp/roadside). */
    const val SEAMLESS_GAP_METERS = 150.0

    /** Gaps <= 1000m are nearby reconnections; beyond this a gentle warning is shown. */
    const val NEARBY_GAP_METERS = 1_000.0

    /** Only add junction distance into merged total distance when gap is continuous (<= 300m). */
    private const val CONTINUOUS_JUNCTION_ADD_MAX_METERS = 300.0

    fun isModeMergeable(mode: ActivityMode): Boolean {
        return mode == ActivityMode.HIKING || mode == ActivityMode.OFFROAD_CROSSING
    }

    fun classifyJunction(gapMeters: Double): MergeJunctionQuality = when {
        gapMeters <= SEAMLESS_GAP_METERS -> MergeJunctionQuality.SEAMLESS
        gapMeters <= NEARBY_GAP_METERS -> MergeJunctionQuality.NEARBY
        else -> MergeJunctionQuality.FAR
    }

    /**
     * Computes the minimum end-to-start connection distance between a candidate session and
     * an existing chronological chain of selected sessions (either appending after the chain's
     * end OR prepending before the chain's start).
     */
    fun bestChainConnectionMeters(
        selectedOrdered: List<TrackSessionEntity>,
        candidate: TrackSessionEntity,
        endpointsById: Map<Long, TrackEndpoints>
    ): Double? {
        if (selectedOrdered.isEmpty()) return null
        val candEp = endpointsById[candidate.id] ?: return null
        val sorted = selectedOrdered.sortedWith(compareBy<TrackSessionEntity> { it.startTimeMs }.thenBy { it.id })
        val distances = mutableListOf<Double>()

        // Candidate follows the chain end
        val lastEp = endpointsById[sorted.last().id]
        if (lastEp != null && candidate.startTimeMs >= sorted.last().startTimeMs) {
            distances += TrackRecorder.haversineMeters(
                lastEp.endLatitude, lastEp.endLongitude,
                candEp.startLatitude, candEp.startLongitude
            )
        }
        // Candidate precedes the chain start
        val firstEp = endpointsById[sorted.first().id]
        if (firstEp != null && candidate.startTimeMs <= sorted.first().startTimeMs) {
            distances += TrackRecorder.haversineMeters(
                candEp.endLatitude, candEp.endLongitude,
                firstEp.startLatitude, firstEp.startLongitude
            )
        }
        // Fallback if candidate sits in the middle of the selected time range
        if (distances.isEmpty()) {
            val prev = sorted.lastOrNull { it.startTimeMs <= candidate.startTimeMs }
            val next = sorted.firstOrNull { it.startTimeMs >= candidate.startTimeMs }
            prev?.let { endpointsById[it.id] }?.let { ep ->
                distances += TrackRecorder.haversineMeters(
                    ep.endLatitude, ep.endLongitude,
                    candEp.startLatitude, candEp.startLongitude
                )
            }
            next?.let { endpointsById[it.id] }?.let { ep ->
                distances += TrackRecorder.haversineMeters(
                    candEp.endLatitude, candEp.endLongitude,
                    ep.startLatitude, ep.startLongitude
                )
            }
        }
        return distances.minOrNull()
    }

    fun buildPreview(details: List<TrackSessionDetail>): Result<TrackMergePreview> {
        validateSegments(details).onFailure { return Result.failure(it) }

        val ordered = details.sortedWith(
            compareBy<TrackSessionDetail> { it.session.startTimeMs }.thenBy { it.session.id }
        )
        val mode = ordered.first().session.activityMode

        val junctions = mutableListOf<TrackMergeJunction>()
        var extraContinuousGapMeters = 0.0

        for (i in 0 until ordered.lastIndex) {
            val prev = ordered[i]
            val next = ordered[i + 1]
            val prevLastPt = prev.points.lastOrNull()
            val nextFirstPt = next.points.firstOrNull()
            val gapMeters = if (prevLastPt != null && nextFirstPt != null) {
                TrackRecorder.haversineMeters(
                    prevLastPt.latitude, prevLastPt.longitude,
                    nextFirstPt.latitude, nextFirstPt.longitude
                )
            } else {
                0.0
            }
            if (gapMeters <= CONTINUOUS_JUNCTION_ADD_MAX_METERS) {
                extraContinuousGapMeters += gapMeters
            }
            val gapDurationMs = (next.session.startTimeMs - prev.session.endTimeMs).coerceAtLeast(0L)
            junctions += TrackMergeJunction(
                fromSessionId = prev.session.id,
                toSessionId = next.session.id,
                fromName = prev.session.name,
                toName = next.session.name,
                gapMeters = gapMeters,
                gapDurationMs = gapDurationMs,
                quality = classifyJunction(gapMeters)
            )
        }

        val mergedDurationMs = ordered.sumOf { it.session.durationMs.coerceAtLeast(0L) }
        val mergedDistanceM = ordered.sumOf { it.session.distanceMeters.coerceAtLeast(0.0) } + extraContinuousGapMeters
        val mergedGainM = ordered.sumOf { it.session.elevationGainMeters.coerceAtLeast(0.0) }
        val mergedLossM = ordered.sumOf { it.session.elevationLossMeters.coerceAtLeast(0.0) }
        val totalPts = ordered.sumOf { it.points.size }

        val defaultName = "${mode.labelZh} · 合并轨迹（${ordered.size} 段）"

        return Result.success(
            TrackMergePreview(
                mode = mode,
                orderedDetails = ordered,
                junctions = junctions,
                defaultMergedName = defaultName,
                mergedDurationMs = mergedDurationMs,
                mergedDistanceMeters = mergedDistanceM,
                mergedElevationGainMeters = mergedGainM,
                mergedElevationLossMeters = mergedLossM,
                totalPointCount = totalPts
            )
        )
    }

    /**
     * Merges 2+ same-type Hiking or Off-road Crossing tracks chronologically into one
     * continuous [TrackSessionDetail] ready to persist in Room.
     *
     * Preserves recorded active duration (summing segment durations rather than overnight sleep gaps)
     * and preserves truthful point timestamps for GPX/JSON export while guaranteeing monotonic order.
     */
    fun merge(
        details: List<TrackSessionDetail>,
        customName: String? = null
    ): Result<TrackSessionDetail> {
        val preview = buildPreview(details).getOrElse { return Result.failure(it) }
        val ordered = preview.orderedDetails
        val mode = preview.mode

        val mergedPoints = ArrayList<TrackPointEntity>(preview.totalPointCount)
        var lastTimestamp = Long.MIN_VALUE

        ordered.forEach { seg ->
            seg.points.forEachIndexed { pointIndex, pt ->
                val safeTs = if (lastTimestamp != Long.MIN_VALUE && pt.timestampMs <= lastTimestamp) {
                    lastTimestamp + 1L
                } else {
                    pt.timestampMs
                }
                lastTimestamp = safeTs
                mergedPoints.add(
                    pt.copy(
                        id = 0L,
                        sessionId = 0L,
                        timestampMs = safeTs,
                        lapNumber = 0,
                        fixElapsedMs = null,
                        timingBreakBefore = if (pointIndex == 0) true else pt.timingBreakBefore
                    )
                )
            }
        }

        val startTimeMs = ordered.first().session.startTimeMs
        val endTimeMs = ordered.maxOf { it.session.endTimeMs }.coerceAtLeast(startTimeMs)
        val durationMs = preview.mergedDurationMs.coerceAtLeast(1_000L)
        val distanceM = preview.mergedDistanceMeters

        val avgSpeedMps = if (durationMs > 0L) {
            (distanceM / (durationMs / 1000.0)).toFloat()
        } else {
            0f
        }
        val maxSpeedMps = maxOf(
            ordered.maxOf { it.session.maxSpeedMps },
            mergedPoints.maxOfOrNull { it.speedMps } ?: 0f
        )

        val altitudes = mergedPoints.mapNotNull { it.altitudeMeters }
        val minAlt = altitudes.minOrNull() ?: ordered.mapNotNull { it.session.minAltitudeMeters }.minOrNull()
        val maxAlt = altitudes.maxOrNull() ?: ordered.mapNotNull { it.session.maxAltitudeMeters }.maxOrNull()

        val hrs = mergedPoints.mapNotNull { it.heartRateBpm }
        val avgHr = if (hrs.isNotEmpty()) {
            hrs.average().toInt()
        } else {
            val sessionHrs = ordered.mapNotNull { it.session.avgHeartRate }
            if (sessionHrs.isNotEmpty()) sessionHrs.average().toInt() else null
        }
        val maxHr = hrs.maxOrNull() ?: ordered.mapNotNull { it.session.maxHeartRate }.maxOrNull()

        val peakTotalG = maxOf(
            ordered.maxOf { it.session.peakTotalG },
            mergedPoints.maxOfOrNull { it.totalG } ?: 0f
        )
        val peakLateralG = maxOf(
            ordered.maxOf { it.session.peakLateralG },
            mergedPoints.maxOfOrNull { abs(it.lateralG) } ?: 0f
        )

        val resolvedName = customName?.trim().takeUnless { it.isNullOrBlank() } ?: preview.defaultMergedName
        val stepCounts = ordered.mapNotNull { it.session.stepCount }

        val mergedSession = TrackSessionEntity(
            id = 0L,
            name = resolvedName,
            mode = mode.name,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            durationMs = durationMs,
            distanceMeters = distanceM,
            maxSpeedMps = maxSpeedMps,
            avgSpeedMps = avgSpeedMps,
            elevationGainMeters = preview.mergedElevationGainMeters,
            elevationLossMeters = preview.mergedElevationLossMeters,
            minAltitudeMeters = minAlt,
            maxAltitudeMeters = maxAlt,
            lapCount = 0,
            bestLapMs = null,
            avgHeartRate = avgHr,
            maxHeartRate = maxHr,
            pointCount = mergedPoints.size,
            peakTotalG = peakTotalG,
            peakLateralG = peakLateralG,
            stepCount = if (mode == ActivityMode.HIKING && stepCounts.size == ordered.size) stepCounts.sum() else null
        )

        return Result.success(
            TrackSessionDetail(
                session = mergedSession,
                points = mergedPoints,
                laps = emptyList()
            )
        )
    }

    private fun validateSegments(details: List<TrackSessionDetail>): Result<Unit> {
        if (details.size < 2) {
            return Result.failure(IllegalArgumentException("请至少选择 2 条轨迹进行合并"))
        }
        val modes = details.map { it.session.activityMode }.toSet()
        if (modes.any { !isModeMergeable(it) }) {
            return Result.failure(IllegalArgumentException("仅支持「徒步」和「越野穿越」轨迹合并，卡丁车不支持合并"))
        }
        if (modes.size > 1) {
            return Result.failure(IllegalArgumentException("仅支持合并相同运动类型的轨迹记录"))
        }
        if (details.all { it.points.isEmpty() }) {
            return Result.failure(IllegalArgumentException("所选轨迹缺少有效坐标点，无法合并"))
        }
        return Result.success(Unit)
    }
}
