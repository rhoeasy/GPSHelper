package com.example.track

import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class ClimbDifficultyLevel(
    val code: String,
    val labelZh: String
) {
    EASY("S1", "轻松巡山"),
    MODERATE("S2", "进阶翻山"),
    CHALLENGING("S3", "硬核爬坡"),
    EXTREME("S4", "极限天路");

    val fullLabel: String
        get() = "$code · $labelZh"
}

enum class SurfaceComplexityLevel(
    val code: String,
    val labelZh: String
) {
    SMOOTH("T1", "平顺铺装"),
    TWISTY("T2", "盘山多弯"),
    ROUGH("T3", "复杂非铺装"),
    EXTREME("T4", "极难越野");

    val fullLabel: String
        get() = "$code · $labelZh"
}

data class ClimbDifficultyAnalysis(
    val level: ClimbDifficultyLevel,
    val score: Int,
    val gainPerKmMeters: Double,
    val maxGradePct: Double,
    val avgClimbGradePct: Double,
    val steepDistanceRatioPct: Int,
    val elevationSpanMeters: Double,
    val summaryZh: String
)

data class SurfaceComplexityAnalysis(
    val level: SurfaceComplexityLevel,
    val score: Int,
    val turnsPerKm: Double,
    val hairpinCount: Int,
    val speedVariabilityPct: Int,
    val vibrationIndexG: Float,
    val smoothPct: Int,
    val twistyPct: Int,
    val technicalPct: Int,
    val summaryZh: String
)

data class OffroadCrossingAnalysis(
    val climb: ClimbDifficultyAnalysis,
    val surface: SurfaceComplexityAnalysis
)

/**
 * Pure offline telemetry analyzer for motorcycle off-road mountain crossing tracks.
 * Computes climb difficulty grading (S1-S4) and surface/terrain complexity (T1-T4)
 * from recorded GPS elevation, distance, heading curvature, speed volatility, and G-force samples.
 */
object OffroadAnalysisEngine {

    private const val EARTH_RADIUS_M = 6_371_000.0
    private const val GRADE_WINDOW_MIN_DIST_M = 40.0
    private const val MAX_REALISTIC_GRADE_PCT = 45.0

    fun analyze(
        session: TrackSessionEntity,
        points: List<TrackPointEntity>
    ): OffroadCrossingAnalysis {
        val climb = analyzeClimbDifficulty(session, points)
        val surface = analyzeSurfaceComplexity(session, points)
        return OffroadCrossingAnalysis(climb = climb, surface = surface)
    }

    private fun analyzeClimbDifficulty(
        session: TrackSessionEntity,
        points: List<TrackPointEntity>
    ): ClimbDifficultyAnalysis {
        val distKm = (session.distanceMeters / 1000.0).coerceAtLeast(0.05)
        val gainMeters = session.elevationGainMeters.coerceAtLeast(0.0)
        val gainPerKm = gainMeters / distKm

        val minAlt = session.minAltitudeMeters
        val maxAlt = session.maxAltitudeMeters
        val elevSpan = if (minAlt != null && maxAlt != null) {
            (maxAlt - minAlt).coerceAtLeast(0.0)
        } else {
            gainMeters
        }

        // Compute smoothed grade windows over points that have altitude readings
        val altIndices = points.indices.filter { points[it].altitudeMeters != null }
        var maxGradePct = 0.0
        var climbGradeWeightedSum = 0.0
        var climbDistTotal = 0.0
        var steepDistTotal = 0.0
        var measuredDistTotal = 0.0

        if (altIndices.size >= 2) {
            // Light 3-point moving average to suppress single-sample GNSS altitude jitter
            val smoothedAlt = DoubleArray(altIndices.size) { idx ->
                val a0 = points[altIndices[(idx - 1).coerceAtLeast(0)]].altitudeMeters!!
                val a1 = points[altIndices[idx]].altitudeMeters!!
                val a2 = points[altIndices[(idx + 1).coerceAtMost(altIndices.lastIndex)]].altitudeMeters!!
                (a0 + a1 * 2.0 + a2) / 4.0
            }

            // Cumulative distance along altitude-bearing points
            val cumDist = DoubleArray(altIndices.size)
            for (k in 1 until altIndices.size) {
                val pPrev = points[altIndices[k - 1]]
                val pCurr = points[altIndices[k]]
                cumDist[k] = cumDist[k - 1] + haversineMeters(
                    pPrev.latitude, pPrev.longitude,
                    pCurr.latitude, pCurr.longitude
                )
            }

            // Local segment stats + ~40m lookahead window for realistic max grade
            var j = 0
            for (k in 0 until altIndices.lastIndex) {
                while (j < altIndices.lastIndex && (cumDist[j] - cumDist[k]) < GRADE_WINDOW_MIN_DIST_M) {
                    j++
                }
                val windowDist = cumDist[j] - cumDist[k]
                if (windowDist >= 15.0) {
                    val dAlt = smoothedAlt[j] - smoothedAlt[k]
                    val absGradePct = (abs(dAlt) / windowDist * 100.0).coerceAtMost(MAX_REALISTIC_GRADE_PCT)
                    if (absGradePct > maxGradePct) {
                        maxGradePct = absGradePct
                    }
                }

                val segDist = cumDist[k + 1] - cumDist[k]
                if (segDist >= 5.0) {
                    measuredDistTotal += segDist
                    val dAlt = smoothedAlt[k + 1] - smoothedAlt[k]
                    val signedGradePct = (dAlt / segDist * 100.0).coerceIn(-MAX_REALISTIC_GRADE_PCT, MAX_REALISTIC_GRADE_PCT)
                    if (signedGradePct >= 1.5) {
                        climbGradeWeightedSum += signedGradePct * segDist
                        climbDistTotal += segDist
                    }
                    if (abs(signedGradePct) >= 8.0) {
                        steepDistTotal += segDist
                    }
                }
            }
        }

        val avgClimbGradePct = if (climbDistTotal > 0.0) climbGradeWeightedSum / climbDistTotal else 0.0
        val steepRatioPct = if (measuredDistTotal > 0.0) {
            ((steepDistTotal / measuredDistTotal) * 100.0).roundToInt().coerceIn(0, 100)
        } else {
            0
        }

        // Composite 0..100 climb difficulty score:
        // - Total elevation gain (0..40 pts, saturates around 1100 m)
        // - Climb density m/km (0..26 pts, saturates around 65 m/km)
        // - Max smoothed grade % (0..20 pts, saturates around 20%)
        // - Summit altitude factor (0..14 pts, scales from 300 m to 2000 m)
        val gainPts = ((gainMeters / 1100.0).coerceIn(0.0, 1.0) * 40.0)
        val densityPts = ((gainPerKm / 65.0).coerceIn(0.0, 1.0) * 26.0)
        val gradePts = ((maxGradePct / 20.0).coerceIn(0.0, 1.0) * 20.0)
        val peakAltPts = if (maxAlt != null) {
            (((maxAlt - 300.0) / 1700.0).coerceIn(0.0, 1.0) * 14.0)
        } else {
            0.0
        }
        val score = (gainPts + densityPts + gradePts + peakAltPts).roundToInt().coerceIn(0, 100)

        val level = when {
            score >= 75 -> ClimbDifficultyLevel.EXTREME
            score >= 52 -> ClimbDifficultyLevel.CHALLENGING
            score >= 28 -> ClimbDifficultyLevel.MODERATE
            else -> ClimbDifficultyLevel.EASY
        }

        val summaryZh = when (level) {
            ClimbDifficultyLevel.EXTREME ->
                String.format(
                    Locale.US,
                    "高海拔大落差连续翻山，最大坡度 %.1f%%，陡坡路段占比 %d%%，对动力与控车要求极高。",
                    maxGradePct,
                    steepRatioPct
                )
            ClimbDifficultyLevel.CHALLENGING ->
                String.format(
                    Locale.US,
                    "翻越起伏显著（爬升密度 %.0f m/km），最大坡度 %.1f%%，适合中高阶越野爬坡节奏。",
                    gainPerKm,
                    maxGradePct
                )
            ClimbDifficultyLevel.MODERATE ->
                String.format(
                    Locale.US,
                    "中等级别山脊爬升，平均上坡 %.1f%%、最大坡度 %.1f%%，节奏流畅且具挑战感。",
                    avgClimbGradePct,
                    maxGradePct
                )
            ClimbDifficultyLevel.EASY ->
                String.format(
                    Locale.US,
                    "整体海拔起伏较缓（爬升密度 %.0f m/km），适合轻松巡山热身与长距离穿越。",
                    gainPerKm
                )
        }

        return ClimbDifficultyAnalysis(
            level = level,
            score = score,
            gainPerKmMeters = gainPerKm,
            maxGradePct = maxGradePct,
            avgClimbGradePct = avgClimbGradePct,
            steepDistanceRatioPct = steepRatioPct,
            elevationSpanMeters = elevSpan,
            summaryZh = summaryZh
        )
    }

    private fun analyzeSurfaceComplexity(
        session: TrackSessionEntity,
        points: List<TrackPointEntity>
    ): SurfaceComplexityAnalysis {
        if (points.size < 3) {
            return SurfaceComplexityAnalysis(
                level = SurfaceComplexityLevel.SMOOTH,
                score = 12,
                turnsPerKm = 0.0,
                hairpinCount = 0,
                speedVariabilityPct = 0,
                vibrationIndexG = 0f,
                smoothPct = 100,
                twistyPct = 0,
                technicalPct = 0,
                summaryZh = "有效采样点较少，暂按平顺巡航路况统计。"
            )
        }

        val distKm = (session.distanceMeters / 1000.0).coerceAtLeast(0.05)

        // 1. Heading change / corner density + hairpin switchback detection
        val segBearings = DoubleArray(points.size - 1)
        val segDists = DoubleArray(points.size - 1)
        for (i in 0 until points.lastIndex) {
            val p1 = points[i]
            val p2 = points[i + 1]
            segDists[i] = haversineMeters(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
            segBearings[i] = computeBearingDeg(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
        }

        var turnEvents = 0
        var hairpinCount = 0
        var cumTurnDist = 0.0
        var cumSignedTurnDeg = 0.0
        var turnCooldownDist = 0.0
        var inCorner = false
        var cornerAccDeg = 0.0

        for (i in 1 until segBearings.size) {
            val d = segDists[i]
            if (d < 4.0) continue
            val deltaDeg = normalizeAngleDeltaDeg(segBearings[i] - segBearings[i - 1])
            val absDelta = abs(deltaDeg)

            // Count distinct corners when sustained turn exceeds 24 deg
            if (absDelta >= 5.0) {
                cornerAccDeg += absDelta
                if (!inCorner && cornerAccDeg >= 24.0) {
                    turnEvents++
                    inCorner = true
                }
            } else {
                inCorner = false
                cornerAccDeg = 0.0
            }

            // Detect tight hairpins / switchbacks (> 105 deg turn within 220m)
            if (turnCooldownDist > 0.0) {
                turnCooldownDist -= d
            } else {
                if ((cumSignedTurnDeg == 0.0 || deltaDeg * cumSignedTurnDeg > 0.0) && absDelta >= 4.0) {
                    cumSignedTurnDeg += deltaDeg
                    cumTurnDist += d
                    if (abs(cumSignedTurnDeg) >= 105.0 && cumTurnDist <= 240.0) {
                        hairpinCount++
                        cumSignedTurnDeg = 0.0
                        cumTurnDist = 0.0
                        turnCooldownDist = 120.0
                    } else if (cumTurnDist > 240.0) {
                        cumSignedTurnDeg = deltaDeg
                        cumTurnDist = d
                    }
                } else {
                    cumSignedTurnDeg = 0.0
                    cumTurnDist = 0.0
                }
            }
        }
        val turnsPerKm = turnEvents / distKm

        // 2. Speed variability coefficient (CV = std / mean) across moving points
        val movingSpeeds = points.map { it.speedMps.toDouble() }.filter { it >= 0.8 }
        val speedVariabilityPct = if (movingSpeeds.size >= 3) {
            val mean = movingSpeeds.average().coerceAtLeast(0.5)
            val variance = movingSpeeds.map { (it - mean) * (it - mean) }.average()
            (sqrt(variance) / mean * 100.0).roundToInt().coerceIn(0, 100)
        } else {
            0
        }

        // 3. G-force roughness / vibration RMS index from adjacent G deltas + lateral load
        var gDeltaSqSum = 0.0
        var gSamples = 0
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            val dLat = (curr.lateralG - prev.lateralG).toDouble()
            val dLon = (curr.longitudinalG - prev.longitudinalG).toDouble()
            val dTot = (curr.totalG - prev.totalG).toDouble()
            val energy = dLat * dLat + dLon * dLon + dTot * dTot * 0.5
            gDeltaSqSum += energy
            gSamples++
        }
        val vibrationIndexG = if (gSamples > 0) {
            sqrt(gDeltaSqSum / gSamples).toFloat().coerceIn(0f, 1.5f)
        } else {
            0f
        }

        // 4. Segment-by-segment terrain classification (Smooth / Twisty Mountain / Technical Off-road)
        var smoothDist = 0.0
        var twistyDist = 0.0
        var technicalDist = 0.0

        for (i in 0 until points.lastIndex) {
            val d = segDists[i].coerceAtLeast(1.0)
            val p1 = points[i]
            val p2 = points[i + 1]
            val altDelta = if (p1.altitudeMeters != null && p2.altitudeMeters != null) {
                abs(p2.altitudeMeters - p1.altitudeMeters)
            } else {
                0.0
            }
            val gradePct = (altDelta / d * 100.0).coerceAtMost(MAX_REALISTIC_GRADE_PCT)
            val bearingDelta = if (i > 0) abs(normalizeAngleDeltaDeg(segBearings[i] - segBearings[i - 1])) else 0.0
            val gShock = abs(p2.totalG - p1.totalG) + abs(p2.longitudinalG - p1.longitudinalG) * 0.6f
            val latLoad = maxOf(abs(p1.lateralG), abs(p2.lateralG))
            val segSpeedKmh = ((p1.speedMps + p2.speedMps) * 0.5f) * 3.6f

            val isTechnical = (gradePct >= 8.5 && segSpeedKmh < 38f) ||
                gShock >= 0.34f ||
                (bearingDelta >= 18.0 && gradePct >= 5.0) ||
                (segSpeedKmh in 3f..24f && (gradePct >= 5.5 || gShock >= 0.20f))

            val isTwisty = !isTechnical && (
                bearingDelta >= 7.5 ||
                    latLoad >= 0.28f ||
                    gradePct >= 4.2 ||
                    gShock >= 0.16f
                )

            when {
                isTechnical -> technicalDist += d
                isTwisty -> twistyDist += d
                else -> smoothDist += d
            }
        }

        val totalClassified = (smoothDist + twistyDist + technicalDist).coerceAtLeast(1.0)
        val technicalPct = ((technicalDist / totalClassified) * 100.0).roundToInt().coerceIn(0, 100)
        val twistyPct = ((twistyDist / totalClassified) * 100.0).roundToInt().coerceIn(0, 100 - technicalPct)
        val smoothPct = (100 - technicalPct - twistyPct).coerceIn(0, 100)

        // Composite 0..100 surface complexity score:
        // - Technical off-road distance ratio (0..34 pts)
        // - Corner density + hairpin bonus (0..26 pts)
        // - G-force vibration / attitude roughness (0..24 pts)
        // - Speed volatility CV (0..16 pts)
        val techPts = ((technicalPct / 55.0).coerceIn(0.0, 1.0) * 34.0)
        val cornerPts = ((turnsPerKm / 4.5).coerceIn(0.0, 1.0) * 18.0) +
            ((hairpinCount / 5.0).coerceIn(0.0, 1.0) * 8.0)
        val vibPts = ((vibrationIndexG / 0.42).coerceIn(0.0, 1.0) * 24.0)
        val speedPts = ((speedVariabilityPct / 45.0).coerceIn(0.0, 1.0) * 16.0)
        val score = (techPts + cornerPts + vibPts + speedPts).roundToInt().coerceIn(0, 100)

        val level = when {
            score >= 77 -> SurfaceComplexityLevel.EXTREME
            score >= 55 -> SurfaceComplexityLevel.ROUGH
            score >= 30 -> SurfaceComplexityLevel.TWISTY
            else -> SurfaceComplexityLevel.SMOOTH
        }

        val summaryZh = when (level) {
            SurfaceComplexityLevel.EXTREME ->
                "高频陡弯与强烈颠簸叠加，复杂非铺装路段达 $technicalPct%，属于高强度技术型越野穿越。"
            SurfaceComplexityLevel.ROUGH ->
                String.format(
                    Locale.US,
                    "包含多处非铺装碎石/陡坡段（%d%%）与 %.1f 弯/km 连续弯道，车速节奏变化明显。",
                    technicalPct,
                    turnsPerKm
                )
            SurfaceComplexityLevel.TWISTY ->
                String.format(
                    Locale.US,
                    "以盘山起伏与连续弯道为主（%.1f 弯/km，急弯 %d 处），过弯压弯与控速节奏丰富。",
                    turnsPerKm,
                    hairpinCount
                )
            SurfaceComplexityLevel.SMOOTH ->
                "整体路面平顺（平缓巡航占比 $smoothPct%），姿态稳定，适合中高速山路巡航。"
        }

        return SurfaceComplexityAnalysis(
            level = level,
            score = score,
            turnsPerKm = turnsPerKm,
            hairpinCount = hairpinCount,
            speedVariabilityPct = speedVariabilityPct,
            vibrationIndexG = vibrationIndexG,
            smoothPct = smoothPct,
            twistyPct = twistyPct,
            technicalPct = technicalPct,
            summaryZh = summaryZh
        )
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2.0) * sin(dLat / 2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2.0) * sin(dLon / 2.0)
        return 2.0 * EARTH_RADIUS_M * atan2(sqrt(a), sqrt((1.0 - a).coerceAtLeast(0.0)))
    }

    private fun computeBearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(rLat2)
        val x = cos(rLat1) * sin(rLat2) - sin(rLat1) * cos(rLat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    private fun normalizeAngleDeltaDeg(delta: Double): Double {
        var d = delta % 360.0
        if (d > 180.0) d -= 360.0
        if (d < -180.0) d += 360.0
        return d
    }
}
