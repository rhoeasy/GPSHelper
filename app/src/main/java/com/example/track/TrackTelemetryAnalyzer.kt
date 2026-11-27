package com.example.track

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class HikeDifficultyLevel(
    val code: String,
    val labelZh: String
) {
    EASY("H1", "轻松漫步"),
    MODERATE("H2", "进阶山野"),
    CHALLENGING("H3", "硬核拉练"),
    EXTREME("H4", "极限重装");

    val fullLabel: String
        get() = "$code · $labelZh"
}

enum class KartDifficultyLevel(
    val code: String,
    val labelZh: String
) {
    EASY("R1", "顺滑新手场"),
    MODERATE("R2", "标准节奏场"),
    CHALLENGING("R3", "技术多弯道"),
    EXTREME("R4", "极限竞技场");

    val fullLabel: String
        get() = "$code · $labelZh"
}

enum class BodyPerformanceLevel(
    val code: String,
    val labelZh: String
) {
    RECOVERY("P1", "轻松恢复"),
    AEROBIC("P2", "稳定有氧"),
    INTENSE("P3", "强效高负荷"),
    PEAK("P4", "极限拉满");

    val fullLabel: String
        get() = "$code · $labelZh"
}

data class HikeDifficultyAnalysis(
    val level: HikeDifficultyLevel,
    val score: Int,
    val gainPerKmMeters: Double,
    val maxGradePct: Double,
    val avgClimbGradePct: Double,
    val steepDistanceRatioPct: Int,
    val summaryZh: String
)

data class KartDifficultyAnalysis(
    val level: KartDifficultyLevel,
    val score: Int,
    val turnsPerKm: Double,
    val hairpinCount: Int,
    val peakLateralG: Float,
    val peakBrakingG: Float,
    val speedSpanKmh: Double,
    val summaryZh: String
)

data class BodyPerformanceAnalysis(
    val level: BodyPerformanceLevel,
    val score: Int,
    val hasHeartRateData: Boolean,
    val avgHeartRate: Int?,
    val maxHeartRate: Int?,
    val easyZonePct: Int,
    val aerobicZonePct: Int,
    val highZonePct: Int,
    val estimatedCaloriesKcal: Int,
    val stabilityPct: Int,
    val stabilityLabelZh: String,
    val highLoadRatioPct: Int,
    val summaryZh: String
)

data class HikingTrackAnalysis(
    val difficulty: HikeDifficultyAnalysis,
    val surface: SurfaceComplexityAnalysis,
    val body: BodyPerformanceAnalysis
)

data class KartingTrackAnalysis(
    val difficulty: KartDifficultyAnalysis,
    val body: BodyPerformanceAnalysis
)

/**
 * Pure offline telemetry analyzer for Hiking and Karting track records.
 * Complements [OffroadAnalysisEngine] with mode-tailored difficulty grading,
 * trail surface complexity, and heart-rate / physiological body performance metrics.
 */
object TrackTelemetryAnalyzer {

    fun analyzeHiking(
        session: TrackSessionEntity,
        points: List<TrackPointEntity>
    ): HikingTrackAnalysis {
        val base = OffroadAnalysisEngine.analyze(session, points)
        val difficulty = buildHikeDifficulty(session, base.climb)
        val surface = buildHikingSurfaceSummary(base.surface)
        val body = buildBodyPerformance(
            session = session,
            points = points,
            laps = emptyList(),
            isKarting = false
        )
        return HikingTrackAnalysis(
            difficulty = difficulty,
            surface = surface,
            body = body
        )
    }

    fun analyzeKarting(
        session: TrackSessionEntity,
        points: List<TrackPointEntity>,
        laps: List<TrackLapEntity>
    ): KartingTrackAnalysis {
        val base = OffroadAnalysisEngine.analyze(session, points)
        val difficulty = buildKartDifficulty(session, points, base.surface)
        val body = buildBodyPerformance(
            session = session,
            points = points,
            laps = laps,
            isKarting = true
        )
        return KartingTrackAnalysis(
            difficulty = difficulty,
            body = body
        )
    }

    private fun buildHikeDifficulty(
        session: TrackSessionEntity,
        climb: ClimbDifficultyAnalysis
    ): HikeDifficultyAnalysis {
        val distKm = (session.distanceMeters / 1000.0).coerceAtLeast(0.05)
        val durationHours = (session.durationMs / 3_600_000.0).coerceAtLeast(0.02)
        val gainM = session.elevationGainMeters.coerceAtLeast(0.0)

        // Hiking effort score (0..100):
        // - Elevation gain (0..38 pts, saturates around 900 m)
        // - Distance (0..24 pts, saturates around 15 km)
        // - Max grade & steepness (0..22 pts)
        // - Duration / endurance factor (0..16 pts, saturates around 4.5 h)
        val gainPts = ((gainM / 900.0).coerceIn(0.0, 1.0) * 38.0)
        val distPts = ((distKm / 15.0).coerceIn(0.0, 1.0) * 24.0)
        val gradePts = ((climb.maxGradePct / 22.0).coerceIn(0.0, 1.0) * 14.0) +
            ((climb.steepDistanceRatioPct / 40.0).coerceIn(0.0, 1.0) * 8.0)
        val timePts = ((durationHours / 4.5).coerceIn(0.0, 1.0) * 16.0)

        val score = (gainPts + distPts + gradePts + timePts).roundToInt().coerceIn(0, 100)
        val level = when {
            score >= 76 -> HikeDifficultyLevel.EXTREME
            score >= 52 -> HikeDifficultyLevel.CHALLENGING
            score >= 28 -> HikeDifficultyLevel.MODERATE
            else -> HikeDifficultyLevel.EASY
        }

        val summaryZh = when (level) {
            HikeDifficultyLevel.EXTREME ->
                String.format(
                    Locale.US,
                    "高强度长距离大爬升山野拉练，最大坡度 %.1f%%、陡坡占比 %d%%，体力消耗极大。",
                    climb.maxGradePct,
                    climb.steepDistanceRatioPct
                )
            HikeDifficultyLevel.CHALLENGING ->
                String.format(
                    Locale.US,
                    "中高难度爬升路线（爬升密度 %.0f m/km），最大坡度 %.1f%%，适合进阶户外拉练。",
                    climb.gainPerKmMeters,
                    climb.maxGradePct
                )
            HikeDifficultyLevel.MODERATE ->
                String.format(
                    Locale.US,
                    "经典山野进阶路线，平均上坡 %.1f%%、最大坡度 %.1f%%，爬升与平路节奏均衡。",
                    climb.avgClimbGradePct,
                    climb.maxGradePct
                )
            HikeDifficultyLevel.EASY ->
                String.format(
                    Locale.US,
                    "坡度温和舒适（爬升密度 %.0f m/km），适合日常休闲徒步与恢复散步。",
                    climb.gainPerKmMeters
                )
        }

        return HikeDifficultyAnalysis(
            level = level,
            score = score,
            gainPerKmMeters = climb.gainPerKmMeters,
            maxGradePct = climb.maxGradePct,
            avgClimbGradePct = climb.avgClimbGradePct,
            steepDistanceRatioPct = climb.steepDistanceRatioPct,
            summaryZh = summaryZh
        )
    }

    private fun buildHikingSurfaceSummary(baseSurface: SurfaceComplexityAnalysis): SurfaceComplexityAnalysis {
        val summaryZh = when (baseSurface.level) {
            SurfaceComplexityLevel.EXTREME ->
                "连续陡坡与高频折返交替，复杂野路占比达 ${baseSurface.technicalPct}%，建议配备防滑登山鞋与登山杖。"
            SurfaceComplexityLevel.ROUGH ->
                String.format(
                    Locale.US,
                    "包含多段陡坡土路/碎石路段（%d%%）与 %.1f 弯/km 折返山径，步频与配速起伏明显。",
                    baseSurface.technicalPct,
                    baseSurface.turnsPerKm
                )
            SurfaceComplexityLevel.TWISTY ->
                String.format(
                    Locale.US,
                    "以山林起伏步道与盘山小径为主（%.1f 弯/km，急弯 %d 处），地形层次丰富。",
                    baseSurface.turnsPerKm,
                    baseSurface.hairpinCount
                )
            SurfaceComplexityLevel.SMOOTH ->
                "整体步道平缓顺畅（平缓路段占比 ${baseSurface.smoothPct}%），行进节奏稳定。"
        }
        return baseSurface.copy(summaryZh = summaryZh)
    }

    private fun buildKartDifficulty(
        session: TrackSessionEntity,
        points: List<TrackPointEntity>,
        baseSurface: SurfaceComplexityAnalysis
    ): KartDifficultyAnalysis {
        val peakLatG = session.peakLateralG.coerceAtLeast(
            points.maxOfOrNull { abs(it.lateralG) } ?: 0f
        )
        val peakBrakingG = points.maxOfOrNull { (-it.longitudinalG).coerceAtLeast(0f) } ?: 0f
        val maxSpeedKmh = (session.maxSpeedMps * 3.6).coerceAtLeast(0.0)
        val movingSpeedsKmh = points.map { it.speedMps * 3.6 }.filter { it >= 5.0 }
        val minCornerKmh = movingSpeedsKmh.minOrNull() ?: 0.0
        val speedSpanKmh = (maxSpeedKmh - minCornerKmh).coerceAtLeast(0.0)

        // Karting track difficulty score (0..100):
        // - Peak lateral cornering G (0..32 pts, saturates around 1.45 G)
        // - Peak braking G (0..22 pts, saturates around 1.0 G)
        // - Corner density + hairpin count (0..28 pts)
        // - Speed range delta straight-to-apex (0..18 pts, saturates around 55 km/h)
        val latPts = ((peakLatG / 1.45).coerceIn(0.0, 1.0) * 32.0)
        val brakePts = ((peakBrakingG / 1.0).coerceIn(0.0, 1.0) * 22.0)
        val cornerPts = ((baseSurface.turnsPerKm / 12.0).coerceIn(0.0, 1.0) * 18.0) +
            ((baseSurface.hairpinCount / 8.0).coerceIn(0.0, 1.0) * 10.0)
        val speedPts = ((speedSpanKmh / 55.0).coerceIn(0.0, 1.0) * 18.0)

        val score = (latPts + brakePts + cornerPts + speedPts).roundToInt().coerceIn(0, 100)
        val level = when {
            score >= 76 -> KartDifficultyLevel.EXTREME
            score >= 54 -> KartDifficultyLevel.CHALLENGING
            score >= 30 -> KartDifficultyLevel.MODERATE
            else -> KartDifficultyLevel.EASY
        }

        val summaryZh = when (level) {
            KartDifficultyLevel.EXTREME ->
                String.format(
                    Locale.US,
                    "高负载竞技级赛道，峰值横向 %.2f G、重刹 %.2f G，急弯密集（%d 处），极度考验走线与控车。",
                    peakLatG,
                    peakBrakingG,
                    baseSurface.hairpinCount
                )
            KartDifficultyLevel.CHALLENGING ->
                String.format(
                    Locale.US,
                    "技术型多弯赛道（%.1f 弯/km），横向过弯达 %.2f G，直道极速与弯心落差 %.0f km/h。",
                    baseSurface.turnsPerKm,
                    peakLatG,
                    speedSpanKmh
                )
            KartDifficultyLevel.MODERATE ->
                String.format(
                    Locale.US,
                    "节奏流畅的标准卡丁车赛道，峰值横向 %.2f G，兼顾高速直道与中速弯角。",
                    peakLatG
                )
            KartDifficultyLevel.EASY ->
                "弯角舒缓、制动负荷较轻，非常适合新手熟悉油门刹车衔接与基础走线。"
        }

        return KartDifficultyAnalysis(
            level = level,
            score = score,
            turnsPerKm = baseSurface.turnsPerKm,
            hairpinCount = baseSurface.hairpinCount,
            peakLateralG = peakLatG,
            peakBrakingG = peakBrakingG,
            speedSpanKmh = speedSpanKmh,
            summaryZh = summaryZh
        )
    }

    private fun buildBodyPerformance(
        session: TrackSessionEntity,
        points: List<TrackPointEntity>,
        laps: List<TrackLapEntity>,
        isKarting: Boolean
    ): BodyPerformanceAnalysis {
        val durationMin = (session.durationMs / 60_000.0).coerceAtLeast(0.5)
        val hrPoints = points.mapNotNull { it.heartRateBpm }
        val hasHr = hrPoints.size >= 3 || session.avgHeartRate != null
        val avgHr = session.avgHeartRate ?: hrPoints.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
        val maxHr = session.maxHeartRate ?: hrPoints.maxOrNull()

        // Heart rate zone breakdown: Easy (<130), Aerobic (130..154), High Intensity (>=155)
        val easyPct: Int
        val aerobicPct: Int
        val highPct: Int
        if (hrPoints.isNotEmpty()) {
            var easyCnt = 0
            var aeroCnt = 0
            var highCnt = 0
            hrPoints.forEach { bpm ->
                when {
                    bpm < 130 -> easyCnt++
                    bpm < 155 -> aeroCnt++
                    else -> highCnt++
                }
            }
            val total = hrPoints.size.toDouble()
            highPct = ((highCnt / total) * 100.0).roundToInt().coerceIn(0, 100)
            aerobicPct = ((aeroCnt / total) * 100.0).roundToInt().coerceIn(0, 100 - highPct)
            easyPct = (100 - highPct - aerobicPct).coerceIn(0, 100)
        } else if (avgHr != null) {
            when {
                avgHr >= 155 -> { easyPct = 10; aerobicPct = 30; highPct = 60 }
                avgHr >= 130 -> { easyPct = 20; aerobicPct = 60; highPct = 20 }
                else -> { easyPct = 70; aerobicPct = 25; highPct = 5 }
            }
        } else {
            easyPct = 35
            aerobicPct = 45
            highPct = 20
        }

        // Stability / Consistency score:
        // - Karting: lap-time consistency if >= 2 laps, else HR / speed control stability
        // - Hiking: pace consistency across active segments (lower pace drift = better endurance pacing)
        val stabilityLabelZh: String
        val stabilityPct: Int
        if (isKarting && laps.size >= 2) {
            stabilityLabelZh = "圈速稳定"
            val lapSecs = laps.map { it.lapDurationMs / 1000.0 }
            val meanLap = lapSecs.average().coerceAtLeast(1.0)
            val stdLap = sqrt(lapSecs.map { (it - meanLap) * (it - meanLap) }.average())
            val cv = stdLap / meanLap
            stabilityPct = ((1.0 - (cv / 0.15)).coerceIn(0.25, 0.99) * 100.0).roundToInt()
        } else if (isKarting) {
            stabilityLabelZh = "抗 G 稳定"
            val highGCount = points.count { it.totalG >= 0.55f }
            val ratio = if (points.isNotEmpty()) highGCount.toDouble() / points.size else 0.0
            stabilityPct = (68 + (ratio * 30.0).roundToInt()).coerceIn(50, 98)
        } else {
            stabilityLabelZh = "配速稳定"
            val activeSpeeds = points.map { it.speedMps.toDouble() }.filter { it >= 0.4 }
            stabilityPct = if (activeSpeeds.size >= 4) {
                val mean = activeSpeeds.average().coerceAtLeast(0.3)
                val std = sqrt(activeSpeeds.map { (it - mean) * (it - mean) }.average())
                val cv = std / mean
                ((1.0 - (cv / 0.65)).coerceIn(0.30, 0.98) * 100.0).roundToInt()
            } else {
                80
            }
        }

        // High-load ratio:
        // - Karting: % of points with >= 0.60G lateral/braking load OR HR >= 155
        // - Hiking: high HR zone % when HR connected, else steep climb effort ratio
        val highLoadRatioPct = if (isKarting) {
            if (points.isNotEmpty()) {
                val loadPts = points.count { it.totalG >= 0.60f || (it.heartRateBpm ?: 0) >= 155 }
                ((loadPts.toDouble() / points.size) * 100.0).roundToInt().coerceIn(0, 100)
            } else {
                highPct
            }
        } else {
            highPct
        }

        // Estimated energy expenditure (kcal):
        // Uses standard HR-based burn when HR is available, else MET-based climb/distance/time model.
        val estimatedKcal = if (avgHr != null) {
            val kcalPerMin = ((avgHr - 55).coerceAtLeast(25) * 0.115).coerceIn(3.5, 16.5)
            val climbBonus = if (!isKarting) (session.elevationGainMeters * 0.16) else 0.0
            (durationMin * kcalPerMin + climbBonus).roundToInt().coerceAtLeast(15)
        } else if (isKarting) {
            val gFactor = (1.0 + session.peakTotalG.coerceIn(0f, 1.8f) * 0.35)
            (durationMin * 6.8 * gFactor).roundToInt().coerceAtLeast(15)
        } else {
            val distKm = session.distanceMeters / 1000.0
            (distKm * 58.0 + session.elevationGainMeters * 0.45).roundToInt().coerceAtLeast(20)
        }

        // Composite body performance / exertion score (0..100)
        val score = if (avgHr != null) {
            val hrIntensityPts = (((avgHr - 90) / 85.0).coerceIn(0.0, 1.0) * 44.0)
            val peakHrPts = if (maxHr != null) (((maxHr - 115) / 75.0).coerceIn(0.0, 1.0) * 20.0) else 8.0
            val zoneLoadPts = ((highPct * 0.65 + aerobicPct * 0.35) / 80.0).coerceIn(0.0, 1.0) * 20.0
            val endurancePts = if (isKarting) {
                ((highLoadRatioPct / 50.0).coerceIn(0.0, 1.0) * 16.0)
            } else {
                ((durationMin / 90.0).coerceIn(0.0, 1.0) * 16.0)
            }
            (hrIntensityPts + peakHrPts + zoneLoadPts + endurancePts).roundToInt().coerceIn(10, 100)
        } else if (isKarting) {
            val gPts = ((session.peakTotalG / 1.5).coerceIn(0.0, 1.0) * 45.0)
            val loadPts = ((highLoadRatioPct / 55.0).coerceIn(0.0, 1.0) * 30.0)
            val durPts = ((durationMin / 15.0).coerceIn(0.0, 1.0) * 25.0)
            (gPts + loadPts + durPts).roundToInt().coerceIn(15, 100)
        } else {
            val distPts = (((session.distanceMeters / 1000.0) / 14.0).coerceIn(0.0, 1.0) * 36.0)
            val gainPts = ((session.elevationGainMeters / 700.0).coerceIn(0.0, 1.0) * 38.0)
            val durPts = ((durationMin / 120.0).coerceIn(0.0, 1.0) * 26.0)
            (distPts + gainPts + durPts).roundToInt().coerceIn(15, 100)
        }

        val level = when {
            score >= 76 -> BodyPerformanceLevel.PEAK
            score >= 54 -> BodyPerformanceLevel.INTENSE
            score >= 32 -> BodyPerformanceLevel.AEROBIC
            else -> BodyPerformanceLevel.RECOVERY
        }

        val summaryZh = if (hasHr && avgHr != null) {
            if (isKarting) {
                when (level) {
                    BodyPerformanceLevel.PEAK ->
                        "驾驶对抗强度拉满，均心率 $avgHr bpm（峰值 ${maxHr ?: avgHr} bpm），高负荷区间达 $highPct%，建议赛后充分拉伸补水。"
                    BodyPerformanceLevel.INTENSE ->
                        "高专注强负荷竞速表现，均心率 $avgHr bpm，$stabilityLabelZh $stabilityPct%，核心与上肢抗 G 表现扎实。"
                    BodyPerformanceLevel.AEROBIC ->
                        "心率控制平稳（均 $avgHr bpm），有氧区占比 $aerobicPct%，驾驶节奏从容且具备冲刺余量。"
                    BodyPerformanceLevel.RECOVERY ->
                        "心肺压力轻松（均 $avgHr bpm），适合赛道热身与试探刹车点。"
                }
            } else {
                when (level) {
                    BodyPerformanceLevel.PEAK ->
                        "高心肺负荷强效输出，均心率 $avgHr bpm（峰值 ${maxHr ?: avgHr} bpm），估算消耗 $estimatedKcal kcal，注意电解质与碳水补给。"
                    BodyPerformanceLevel.INTENSE ->
                        "强效有氧与爬坡冲刺结合，有氧+高强度占比 ${aerobicPct + highPct}%，估算消耗 $estimatedKcal kcal，体能表现优异。"
                    BodyPerformanceLevel.AEROBIC ->
                        "黄金有氧燃脂区间（有氧占比 $aerobicPct%，均心率 $avgHr bpm），配速稳定度 $stabilityPct%，耐力节奏出色。"
                    BodyPerformanceLevel.RECOVERY ->
                        "心率平稳轻松（均 $avgHr bpm），以低强度有氧恢复为主，身心负荷舒适。"
                }
            }
        } else {
            if (isKarting) {
                "未绑定心率手环；基于 G 力对抗（高负荷占比 $highLoadRatioPct%）与 $stabilityLabelZh（$stabilityPct%）估算消耗约 $estimatedKcal kcal。"
            } else {
                "未绑定心率手环；基于距离、爬升强度与配速稳定度（$stabilityPct%）估算消耗约 $estimatedKcal kcal。"
            }
        }

        return BodyPerformanceAnalysis(
            level = level,
            score = score,
            hasHeartRateData = hasHr,
            avgHeartRate = avgHr,
            maxHeartRate = maxHr,
            easyZonePct = easyPct,
            aerobicZonePct = aerobicPct,
            highZonePct = highPct,
            estimatedCaloriesKcal = estimatedKcal,
            stabilityPct = stabilityPct,
            stabilityLabelZh = stabilityLabelZh,
            highLoadRatioPct = highLoadRatioPct,
            summaryZh = summaryZh
        )
    }
}
