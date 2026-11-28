package com.example.track

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max

/**
 * Formula 1 style sector performance color classification:
 * - [PURPLE]: Overall session fastest in this sector (or overall fastest lap) ("刷紫")
 * - [GREEN]: Personal improvement over all prior laps in this session ("刷绿")
 * - [YELLOW]: Slower than the best benchmark ("刷黄")
 */
enum class F1SectorStatus(val labelZh: String) {
    PURPLE("刷紫"),
    GREEN("刷绿"),
    YELLOW("刷黄")
}

data class KartingSectorSplit(
    val sectorIndex: Int, // 0 = S1, 1 = S2, 2 = S3
    val durationMs: Long,
    val deltaToBestMs: Long,
    val distanceMeters: Double,
    val avgSpeedMps: Float,
    val maxSpeedMps: Float,
    val peakLateralG: Float,
    val status: F1SectorStatus
) {
    val label: String get() = "S${sectorIndex + 1}"
}

data class KartingLapSectorTiming(
    val lapNumber: Int,
    val lapDurationMs: Long,
    val lapStatus: F1SectorStatus,
    val sectors: List<KartingSectorSplit>, // Always size 3 (S1, S2, S3)
    val pointIndices: IntRange,
    val pointSectorMap: Map<Int, Int> // global point index -> sectorIndex (0, 1, 2)
)

data class KartingSectorGate(
    val label: String, // "S1", "S2", "S3" (start of each sector)
    val latitude: Double,
    val longitude: Double,
    val referencePointIndex: Int
)

data class KartingF1SectorAnalysis(
    val isMultiLap: Boolean,
    val lapTimings: List<KartingLapSectorTiming>,
    val bestSectorTimesMs: List<Long>, // [bestS1, bestS2, bestS3]
    val bestSectorLapNumbers: List<Int>,
    val bestLapNumber: Int,
    val bestLapDurationMs: Long,
    val theoreticalBestLapMs: Long,
    val gates: List<KartingSectorGate>,
    val allPointsSectorIndex: IntArray // parallel to session points, value 0..2
) {
    val potentialGainMs: Long
        get() = (bestLapDurationMs - theoreticalBestLapMs).coerceAtLeast(0L)

    fun timingForLap(lapNumber: Int?): KartingLapSectorTiming? {
        if (lapTimings.isEmpty()) return null
        if (lapNumber == null) {
            return lapTimings.firstOrNull { it.lapNumber == bestLapNumber } ?: lapTimings.first()
        }
        return lapTimings.firstOrNull { it.lapNumber == lapNumber }
            ?: lapTimings.firstOrNull { it.lapNumber == bestLapNumber }
            ?: lapTimings.first()
    }
}

/**
 * Automatically divides any karting circuit into 3 F1-style sectors (S1, S2, S3)
 * by locating low-curvature straightaway/corner-exit timing gates near 1/3 and 2/3
 * of the reference lap distance, then computes per-lap sector splits, theoretical best lap,
 * and authentic F1 Purple / Green / Yellow sector color statuses.
 */
object KartingSectorEngine {

    private const val SECTOR_COUNT = 3

    fun analyze(
        points: List<TrackPointEntity>,
        laps: List<TrackLapEntity>
    ): KartingF1SectorAnalysis? {
        if (points.size < 9) return null

        // Group consecutive points belonging to each positive lapNumber
        val groupedRanges = extractLapRanges(points, laps)
        if (groupedRanges.isEmpty()) {
            return buildSingleRunFallback(points)
        }

        // Choose reference lap: fastest completed lap (or longest valid lap)
        val referenceLap = groupedRanges.minByOrNull { range ->
            val duration = lapDurationForRange(points, range, laps)
            if (duration > 5_000L) duration else Long.MAX_VALUE
        } ?: groupedRanges.first()

        val refPoints = points.subList(referenceLap.range.first, referenceLap.range.last + 1)
        if (refPoints.size < 9) {
            return buildSingleRunFallback(points)
        }

        // Project reference lap into local meters and find natural low-curvature S1->S2 and S2->S3 boundaries
        val refCumDist = cumulativeDistances(refPoints)
        val totalRefDist = refCumDist.last().coerceAtLeast(1.0)
        val refCurvatures = estimateCurvatures(refPoints)

        val gate1LocalIdx = findNaturalBoundaryIndex(
            cumDist = refCumDist,
            curvatures = refCurvatures,
            targetFraction = 1.0 / 3.0,
            searchHalfWidth = 0.055
        )
        val gate2LocalIdx = findNaturalBoundaryIndex(
            cumDist = refCumDist,
            curvatures = refCurvatures,
            targetFraction = 2.0 / 3.0,
            searchHalfWidth = 0.055
        ).coerceAtLeast(gate1LocalIdx + 2).coerceAtMost(refPoints.lastIndex - 1)

        val gate1Frac = (refCumDist[gate1LocalIdx] / totalRefDist).coerceIn(0.20, 0.48)
        val gate2Frac = (refCumDist[gate2LocalIdx] / totalRefDist).coerceIn(gate1Frac + 0.15, 0.85)

        val gates = listOf(
            KartingSectorGate(
                label = "S1",
                latitude = refPoints.first().latitude,
                longitude = refPoints.first().longitude,
                referencePointIndex = referenceLap.range.first
            ),
            KartingSectorGate(
                label = "S2",
                latitude = refPoints[gate1LocalIdx].latitude,
                longitude = refPoints[gate1LocalIdx].longitude,
                referencePointIndex = referenceLap.range.first + gate1LocalIdx
            ),
            KartingSectorGate(
                label = "S3",
                latitude = refPoints[gate2LocalIdx].latitude,
                longitude = refPoints[gate2LocalIdx].longitude,
                referencePointIndex = referenceLap.range.first + gate2LocalIdx
            )
        )

        // Compute raw sector timings per lap
        data class RawSector(
            val durationMs: Long,
            val distanceM: Double,
            val avgSpeedMps: Float,
            val maxSpeedMps: Float,
            val peakLatG: Float
        )

        data class RawLap(
            val lapNumber: Int,
            val lapDurationMs: Long,
            val range: IntRange,
            val sectors: List<RawSector>,
            val pointSectorMap: Map<Int, Int>
        )

        val allPointsSector = IntArray(points.size) { 0 }
        val rawLaps = groupedRanges.map { lapInfo ->
            val slice = points.subList(lapInfo.range.first, lapInfo.range.last + 1)
            val lapCumDist = cumulativeDistances(slice)
            val lapTotalDist = lapCumDist.last().coerceAtLeast(1.0)

            // Match S1->S2 and S2->S3 gate crossings spatially near the target distance window
            val split1Idx = findGateCrossingIndex(
                lapPoints = slice,
                lapCumDist = lapCumDist,
                lapTotalDist = lapTotalDist,
                gateLat = gates[1].latitude,
                gateLon = gates[1].longitude,
                expectedFrac = gate1Frac,
                minIndex = 1,
                maxIndex = (slice.lastIndex - 2).coerceAtLeast(1)
            )
            val split2Idx = findGateCrossingIndex(
                lapPoints = slice,
                lapCumDist = lapCumDist,
                lapTotalDist = lapTotalDist,
                gateLat = gates[2].latitude,
                gateLon = gates[2].longitude,
                expectedFrac = gate2Frac,
                minIndex = (split1Idx + 1).coerceAtMost(slice.lastIndex - 1),
                maxIndex = (slice.lastIndex - 1).coerceAtLeast(split1Idx + 1)
            )

            val sectorRanges = listOf(
                0..split1Idx,
                split1Idx..split2Idx,
                split2Idx..slice.lastIndex
            )

            val pointSectorMap = HashMap<Int, Int>(slice.size)
            for (localIdx in slice.indices) {
                val sec = when {
                    localIdx < split1Idx -> 0
                    localIdx < split2Idx -> 1
                    else -> 2
                }
                val globalIdx = lapInfo.range.first + localIdx
                pointSectorMap[globalIdx] = sec
                allPointsSector[globalIdx] = sec
            }

            val rawMeasuredDurations = sectorRanges.map { r ->
                val startT = slice[r.first].timestampMs
                val endT = slice[r.last].timestampMs
                (endT - startT).coerceAtLeast(100L)
            }
            val rawSum = rawMeasuredDurations.sum().coerceAtLeast(1L)
            val officialLapMs = lapDurationForRange(points, lapInfo, laps).coerceAtLeast(rawSum)
            val scale = officialLapMs.toDouble() / rawSum.toDouble()

            val scaledDurations = LongArray(SECTOR_COUNT)
            var assignedMs = 0L
            for (s in 0 until SECTOR_COUNT) {
                if (s < SECTOR_COUNT - 1) {
                    val d = (rawMeasuredDurations[s] * scale).toLong().coerceAtLeast(100L)
                    scaledDurations[s] = d
                    assignedMs += d
                } else {
                    scaledDurations[s] = (officialLapMs - assignedMs).coerceAtLeast(100L)
                }
            }

            val sectors = sectorRanges.mapIndexed { sIdx, r ->
                val distM = (lapCumDist[r.last] - lapCumDist[r.first]).coerceAtLeast(0.0)
                val segPts = slice.subList(r.first, r.last + 1)
                val measuredSpeeds = segPts.mapNotNull { it.measuredSpeedMps }
                val maxSpd = measuredSpeeds.maxOrNull() ?: 0f
                val avgSpd = if (scaledDurations[sIdx] > 0L && distM > 1.0) {
                    (distM / (scaledDurations[sIdx] / 1000.0)).toFloat()
                } else {
                    measuredSpeeds.takeIf { it.isNotEmpty() }?.average()?.toFloat() ?: 0f
                }
                val peakLatG = segPts.maxOfOrNull { abs(it.lateralG) } ?: 0f
                RawSector(
                    durationMs = scaledDurations[sIdx],
                    distanceM = distM,
                    avgSpeedMps = avgSpd,
                    maxSpeedMps = maxSpd,
                    peakLatG = peakLatG
                )
            }

            RawLap(
                lapNumber = lapInfo.lapNumber,
                lapDurationMs = officialLapMs,
                range = lapInfo.range,
                sectors = sectors,
                pointSectorMap = pointSectorMap
            )
        }

        // Fill any remaining out-of-lap points (e.g. cooldown/out-lap) by nearest sector progress
        val groupedIndices = rawLaps.flatMapTo(HashSet()) { it.pointSectorMap.keys }
        if (groupedIndices.size < points.size) {
            val totalCum = cumulativeDistances(points)
            val totalSpan = totalCum.last().coerceAtLeast(1.0)
            for (i in points.indices) {
                if (i !in groupedIndices) {
                    val f = totalCum[i] / totalSpan
                    allPointsSector[i] = when {
                        f < gate1Frac -> 0
                        f < gate2Frac -> 1
                        else -> 2
                    }
                }
            }
        }

        // Determine overall session bests (Purple benchmarks)
        val bestSectorTimes = (0 until SECTOR_COUNT).map { s ->
            rawLaps.minOf { it.sectors[s].durationMs }
        }
        val bestSectorLaps = (0 until SECTOR_COUNT).map { s ->
            rawLaps.minBy { it.sectors[s].durationMs }.lapNumber
        }
        val bestLap = rawLaps.minBy { it.lapDurationMs }
        val theoreticalBestMs = bestSectorTimes.sum()

        val isMultiLap = rawLaps.size >= 2

        // Assign authentic F1 colors:
        // - PURPLE: Overall session fastest (within 15ms tie tolerance)
        // - GREEN: Improved over all prior laps (personal best at that lap)
        // - YELLOW: Slower than best benchmark so far
        val runningBestSector = LongArray(SECTOR_COUNT) { Long.MAX_VALUE }
        var runningBestLap = Long.MAX_VALUE

        val finalTimings = rawLaps.mapIndexed { lapIdx, rawLap ->
            val lapStatus = if (!isMultiLap) {
                F1SectorStatus.PURPLE
            } else {
                val isOverallBestLap = rawLap.lapDurationMs <= bestLap.lapDurationMs + 15L
                val isPersonalBestNow = lapIdx > 0 && rawLap.lapDurationMs < runningBestLap
                if (rawLap.lapDurationMs < runningBestLap) {
                    runningBestLap = rawLap.lapDurationMs
                }
                when {
                    isOverallBestLap -> F1SectorStatus.PURPLE
                    isPersonalBestNow -> F1SectorStatus.GREEN
                    else -> F1SectorStatus.YELLOW
                }
            }

            val finalSectors = rawLap.sectors.mapIndexed { sIdx, sec ->
                val overallBest = bestSectorTimes[sIdx]
                val status = if (!isMultiLap) {
                    // Single-lap mode: highlight fastest average-speed sector in Purple, slowest in Yellow
                    val maxAvg = rawLap.sectors.maxOf { it.avgSpeedMps }
                    val minAvg = rawLap.sectors.minOf { it.avgSpeedMps }
                    when {
                        sec.avgSpeedMps >= maxAvg - 0.05f -> F1SectorStatus.PURPLE
                        sec.avgSpeedMps <= minAvg + 0.05f -> F1SectorStatus.YELLOW
                        else -> F1SectorStatus.GREEN
                    }
                } else {
                    val isOverallBest = sec.durationMs <= overallBest + 15L
                    val improvedOverPrior = lapIdx > 0 && sec.durationMs < runningBestSector[sIdx]
                    if (sec.durationMs < runningBestSector[sIdx]) {
                        runningBestSector[sIdx] = sec.durationMs
                    }
                    when {
                        isOverallBest -> F1SectorStatus.PURPLE
                        improvedOverPrior -> F1SectorStatus.GREEN
                        else -> F1SectorStatus.YELLOW
                    }
                }
                KartingSectorSplit(
                    sectorIndex = sIdx,
                    durationMs = sec.durationMs,
                    deltaToBestMs = (sec.durationMs - overallBest).coerceAtLeast(0L),
                    distanceMeters = sec.distanceM,
                    avgSpeedMps = sec.avgSpeedMps,
                    maxSpeedMps = sec.maxSpeedMps,
                    peakLateralG = sec.peakLatG,
                    status = status
                )
            }

            KartingLapSectorTiming(
                lapNumber = rawLap.lapNumber,
                lapDurationMs = rawLap.lapDurationMs,
                lapStatus = lapStatus,
                sectors = finalSectors,
                pointIndices = rawLap.range,
                pointSectorMap = rawLap.pointSectorMap
            )
        }

        return KartingF1SectorAnalysis(
            isMultiLap = isMultiLap,
            lapTimings = finalTimings,
            bestSectorTimesMs = bestSectorTimes,
            bestSectorLapNumbers = bestSectorLaps,
            bestLapNumber = bestLap.lapNumber,
            bestLapDurationMs = bestLap.lapDurationMs,
            theoreticalBestLapMs = theoreticalBestMs,
            gates = gates,
            allPointsSectorIndex = allPointsSector
        )
    }

    private data class LapPointRange(
        val lapNumber: Int,
        val range: IntRange
    )

    private fun extractLapRanges(
        points: List<TrackPointEntity>,
        laps: List<TrackLapEntity>
    ): List<LapPointRange> {
        val knownLapNumbers = laps.map { it.lapNumber }.toSet()
        val byLap = LinkedHashMap<Int, MutableList<Int>>()
        points.forEachIndexed { index, pt ->
            if (pt.lapNumber > 0) {
                byLap.getOrPut(pt.lapNumber) { mutableListOf() }.add(index)
            }
        }

        val candidates = byLap.entries
            .sortedBy { it.key }
            .mapNotNull { (lapNum, indices) ->
                if (indices.size < 9) return@mapNotNull null
                if (knownLapNumbers.isNotEmpty() && lapNum !in knownLapNumbers) return@mapNotNull null
                LapPointRange(lapNum, indices.first()..indices.last())
            }

        if (candidates.isNotEmpty()) return candidates

        // Fallback if laps table has entries or points all share lapNumber=0
        return byLap.entries
            .sortedBy { it.key }
            .filter { it.value.size >= 9 }
            .map { LapPointRange(it.key, it.value.first()..it.value.last()) }
    }

    private fun lapDurationForRange(
        points: List<TrackPointEntity>,
        lapInfo: LapPointRange,
        laps: List<TrackLapEntity>
    ): Long {
        val official = laps.firstOrNull { it.lapNumber == lapInfo.lapNumber }?.lapDurationMs
        if (official != null && official > 0L) return official
        val startMs = points[lapInfo.range.first].timestampMs
        val endMs = points[lapInfo.range.last].timestampMs
        return (endMs - startMs).coerceAtLeast(1_000L)
    }

    private fun buildSingleRunFallback(points: List<TrackPointEntity>): KartingF1SectorAnalysis {
        val cumDist = cumulativeDistances(points)
        val totalDist = cumDist.last().coerceAtLeast(1.0)
        val curvatures = estimateCurvatures(points)
        val split1 = findNaturalBoundaryIndex(cumDist, curvatures, 1.0 / 3.0, 0.05)
        val split2 = findNaturalBoundaryIndex(cumDist, curvatures, 2.0 / 3.0, 0.05)
            .coerceAtLeast(split1 + 2)
            .coerceAtMost(points.lastIndex - 1)

        val sectorRanges = listOf(0..split1, split1..split2, split2..points.lastIndex)
        val allPointsSector = IntArray(points.size) { idx ->
            when {
                idx < split1 -> 0
                idx < split2 -> 1
                else -> 2
            }
        }
        val pointMap = points.indices.associateWith { allPointsSector[it] }

        val rawDurations = sectorRanges.map { r ->
            (points[r.last].timestampMs - points[r.first].timestampMs).coerceAtLeast(100L)
        }
        val avgSpeeds = sectorRanges.mapIndexed { sIdx, r ->
            val d = (cumDist[r.last] - cumDist[r.first]).coerceAtLeast(0.0)
            if (d > 1.0) (d / (rawDurations[sIdx] / 1000.0)).toFloat()
            else points.subList(r.first, r.last + 1).mapNotNull { it.measuredSpeedMps }
                .takeIf { it.isNotEmpty() }?.average()?.toFloat() ?: 0f
        }
        val maxAvg = avgSpeeds.maxOrNull() ?: 0f
        val minAvg = avgSpeeds.minOrNull() ?: 0f

        val splits = sectorRanges.mapIndexed { sIdx, r ->
            val slice = points.subList(r.first, r.last + 1)
            val status = when {
                avgSpeeds[sIdx] >= maxAvg - 0.05f -> F1SectorStatus.PURPLE
                avgSpeeds[sIdx] <= minAvg + 0.05f -> F1SectorStatus.YELLOW
                else -> F1SectorStatus.GREEN
            }
            KartingSectorSplit(
                sectorIndex = sIdx,
                durationMs = rawDurations[sIdx],
                deltaToBestMs = 0L,
                distanceMeters = (cumDist[r.last] - cumDist[r.first]).coerceAtLeast(0.0),
                avgSpeedMps = avgSpeeds[sIdx],
                maxSpeedMps = slice.mapNotNull { it.measuredSpeedMps }.maxOrNull() ?: 0f,
                peakLateralG = slice.maxOfOrNull { abs(it.lateralG) } ?: 0f,
                status = status
            )
        }

        val totalMs = rawDurations.sum()
        val lapTiming = KartingLapSectorTiming(
            lapNumber = 1,
            lapDurationMs = totalMs,
            lapStatus = F1SectorStatus.PURPLE,
            sectors = splits,
            pointIndices = 0..points.lastIndex,
            pointSectorMap = pointMap
        )
        val gates = listOf(
            KartingSectorGate("S1", points.first().latitude, points.first().longitude, 0),
            KartingSectorGate("S2", points[split1].latitude, points[split1].longitude, split1),
            KartingSectorGate("S3", points[split2].latitude, points[split2].longitude, split2)
        )
        return KartingF1SectorAnalysis(
            isMultiLap = false,
            lapTimings = listOf(lapTiming),
            bestSectorTimesMs = rawDurations,
            bestSectorLapNumbers = listOf(1, 1, 1),
            bestLapNumber = 1,
            bestLapDurationMs = totalMs,
            theoreticalBestLapMs = totalMs,
            gates = gates,
            allPointsSectorIndex = allPointsSector
        )
    }

    private fun cumulativeDistances(points: List<TrackPointEntity>): DoubleArray {
        val result = DoubleArray(points.size)
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            val d = TrackRecorder.haversineMeters(
                prev.latitude,
                prev.longitude,
                curr.latitude,
                curr.longitude
            )
            result[i] = result[i - 1] + d
        }
        return result
    }

    private fun estimateCurvatures(points: List<TrackPointEntity>): DoubleArray {
        val n = points.size
        val curv = DoubleArray(n)
        if (n < 3) return curv
        val avgLat = points.map { it.latitude }.average()
        val lonScale = cos(Math.toRadians(avgLat)).coerceIn(0.2, 1.0)
        for (i in 1 until n - 1) {
            val p0 = points[i - 1]
            val p1 = points[i]
            val p2 = points[i + 1]
            val v1x = (p1.longitude - p0.longitude) * lonScale
            val v1y = p1.latitude - p0.latitude
            val v2x = (p2.longitude - p1.longitude) * lonScale
            val v2y = p2.latitude - p1.latitude
            val len = max(hypot(v1x, v1y), hypot(v2x, v2y))
            if (len > 1e-8) {
                val cross = v1x * v2y - v1y * v2x
                val dot = v1x * v2x + v1y * v2y
                curv[i] = abs(atan2(cross, dot))
            }
        }
        return curv
    }

    /**
     * Picks the lowest-curvature point within [targetFraction ± searchHalfWidth] so sector gates
     * sit naturally on straightaways or corner exits rather than mid-apex.
     */
    private fun findNaturalBoundaryIndex(
        cumDist: DoubleArray,
        curvatures: DoubleArray,
        targetFraction: Double,
        searchHalfWidth: Double
    ): Int {
        val total = cumDist.last().coerceAtLeast(1.0)
        val minFrac = (targetFraction - searchHalfWidth).coerceIn(0.15, 0.85)
        val maxFrac = (targetFraction + searchHalfWidth).coerceIn(minFrac, 0.90)

        var bestIdx = (cumDist.lastIndex * targetFraction).toInt().coerceIn(2, cumDist.lastIndex - 2)
        var bestScore = Double.POSITIVE_INFINITY

        for (i in 2 until cumDist.lastIndex - 1) {
            val frac = cumDist[i] / total
            if (frac in minFrac..maxFrac) {
                val distPenalty = abs(frac - targetFraction) * 2.5
                val score = curvatures[i] * 3.0 + distPenalty
                if (score < bestScore) {
                    bestScore = score
                    bestIdx = i
                }
            }
        }
        return bestIdx
    }

    private fun findGateCrossingIndex(
        lapPoints: List<TrackPointEntity>,
        lapCumDist: DoubleArray,
        lapTotalDist: Double,
        gateLat: Double,
        gateLon: Double,
        expectedFrac: Double,
        minIndex: Int,
        maxIndex: Int
    ): Int {
        val safeMin = minIndex.coerceIn(1, lapPoints.lastIndex - 1)
        val safeMax = maxIndex.coerceIn(safeMin, lapPoints.lastIndex - 1)
        val searchMinFrac = (expectedFrac - 0.16).coerceAtLeast(0.08)
        val searchMaxFrac = (expectedFrac + 0.16).coerceAtMost(0.92)

        var bestIdx = safeMin
        var bestMeters = Double.POSITIVE_INFINITY

        for (i in safeMin..safeMax) {
            val frac = lapCumDist[i] / lapTotalDist
            if (frac in searchMinFrac..searchMaxFrac) {
                val d = TrackRecorder.haversineMeters(
                    lapPoints[i].latitude,
                    lapPoints[i].longitude,
                    gateLat,
                    gateLon
                )
                if (d < bestMeters) {
                    bestMeters = d
                    bestIdx = i
                }
            }
        }

        // If lap GPS drifted far from gate (> 45m), fall back to matching cumulative distance fraction
        if (bestMeters > 45.0) {
            val targetDist = lapTotalDist * expectedFrac
            var closestDistDiff = Double.POSITIVE_INFINITY
            for (i in safeMin..safeMax) {
                val diff = abs(lapCumDist[i] - targetDist)
                if (diff < closestDistDiff) {
                    closestDistDiff = diff
                    bestIdx = i
                }
            }
        }
        return bestIdx
    }
}
