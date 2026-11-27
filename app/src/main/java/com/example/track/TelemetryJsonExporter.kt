package com.example.track

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.round

/**
 * Generates clean, self-describing structured JSON telemetry packages designed for
 * direct ingestion by AI Agents (combining GPS, altitude, speed, BLE heart rate zones,
 * G-Force peaks, and karting lap splits), with optional AI analysis prompt wrapper.
 */
object TelemetryJsonExporter {

    private const val CLIPBOARD_MAX_SAMPLES = 360

    fun buildStructuredJson(
        session: TrackSessionEntity,
        points: List<TrackPointEntity>,
        laps: List<TrackLapEntity>,
        maxSamples: Int? = null
    ): String {
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val root = JSONObject().apply {
            put("schema_version", "1.0")
            put("exported_at_iso", iso.format(Date()))
            put("activity_type", session.activityMode.name)
            put("activity_label", session.activityMode.labelZh)
        }

        val hrSamples = points.mapNotNull { it.heartRateBpm }
        val hrZones = computeHeartRateZonesSec(points)
        val peakBrakingG = points.maxOfOrNull { (-it.longitudinalG).coerceAtLeast(0f) } ?: 0f

        val sessionObj = JSONObject().apply {
            put("id", session.id)
            put("name", session.name)
            put("start_time_iso", iso.format(Date(session.startTimeMs)))
            put("end_time_iso", iso.format(Date(session.endTimeMs)))
            put("duration_sec", round2(session.durationMs / 1000.0))
            put("distance_m", round2(session.distanceMeters))
            put("point_count", points.size)

            put("speed_kmh", JSONObject().apply {
                put("avg", round2(session.avgSpeedMps * 3.6))
                put("max", round2(session.maxSpeedMps * 3.6))
            })

            put("elevation_m", JSONObject().apply {
                put("gain", round1(session.elevationGainMeters))
                put("loss", round1(session.elevationLossMeters))
                put("min", session.minAltitudeMeters?.let { round1(it) } ?: JSONObject.NULL)
                put("max", session.maxAltitudeMeters?.let { round1(it) } ?: JSONObject.NULL)
            })

            put("heart_rate_bpm", JSONObject().apply {
                put("connected_samples", hrSamples.size)
                put("avg", session.avgHeartRate ?: JSONObject.NULL)
                put("max", session.maxHeartRate ?: JSONObject.NULL)
                put("min", hrSamples.minOrNull() ?: JSONObject.NULL)
                if (hrSamples.isNotEmpty()) {
                    put("zones_sec", hrZones)
                }
            })

            put("g_force", JSONObject().apply {
                put("peak_total_g", round2(session.peakTotalG.toDouble()))
                put("peak_lateral_g", round2(session.peakLateralG.toDouble()))
                put("peak_braking_g", round2(peakBrakingG.toDouble()))
            })

            if (session.activityMode == ActivityMode.KARTING || laps.isNotEmpty()) {
                put("laps_summary", JSONObject().apply {
                    put("completed_count", laps.size)
                    put("best_lap_sec", session.bestLapMs?.let { round2(it / 1000.0) } ?: JSONObject.NULL)
                })
            }

            if (session.activityMode == ActivityMode.OFFROAD_CROSSING) {
                val analysis = OffroadAnalysisEngine.analyze(session, points)
                put("offroad_analysis", JSONObject().apply {
                    put("climb_difficulty", JSONObject().apply {
                        put("level_code", analysis.climb.level.code)
                        put("level_label", analysis.climb.level.labelZh)
                        put("score", analysis.climb.score)
                        put("gain_per_km_m", round1(analysis.climb.gainPerKmMeters))
                        put("max_grade_pct", round1(analysis.climb.maxGradePct))
                        put("avg_climb_grade_pct", round1(analysis.climb.avgClimbGradePct))
                        put("steep_distance_ratio_pct", analysis.climb.steepDistanceRatioPct)
                    })
                    put("surface_complexity", JSONObject().apply {
                        put("level_code", analysis.surface.level.code)
                        put("level_label", analysis.surface.level.labelZh)
                        put("score", analysis.surface.score)
                        put("turns_per_km", round2(analysis.surface.turnsPerKm))
                        put("hairpin_count", analysis.surface.hairpinCount)
                        put("vibration_index_g", round2(analysis.surface.vibrationIndexG.toDouble()))
                        put("speed_variability_pct", analysis.surface.speedVariabilityPct)
                        put("smooth_pct", analysis.surface.smoothPct)
                        put("twisty_pct", analysis.surface.twistyPct)
                        put("technical_offroad_pct", analysis.surface.technicalPct)
                    })
                })
            }
        }
        root.put("session", sessionObj)

        if (laps.isNotEmpty()) {
            val bestMs = session.bestLapMs ?: laps.minOf { it.lapDurationMs }
            val lapsArr = JSONArray()
            laps.forEach { lap ->
                lapsArr.put(
                    JSONObject().apply {
                        put("lap", lap.lapNumber)
                        put("duration_sec", round2(lap.lapDurationMs / 1000.0))
                        put("delta_to_best_sec", round2((lap.lapDurationMs - bestMs).coerceAtLeast(0L) / 1000.0))
                        put("distance_m", round1(lap.lapDistanceMeters))
                        put("max_speed_kmh", round2(lap.maxSpeedMps * 3.6))
                        put("avg_heart_rate_bpm", lap.avgHeartRate ?: JSONObject.NULL)
                        put("peak_g", round2(lap.peakG.toDouble()))
                    }
                )
            }
            root.put("laps", lapsArr)
        }

        val exportPoints = downsampleIfNeeded(points, maxSamples)
        if (exportPoints.size < points.size) {
            root.put("samples_downsampled_from", points.size)
        }

        val samplesArr = JSONArray()
        val t0 = points.firstOrNull()?.timestampMs ?: session.startTimeMs
        exportPoints.forEach { pt ->
            samplesArr.put(
                JSONObject().apply {
                    put("t_offset_sec", round2((pt.timestampMs - t0) / 1000.0))
                    put("lat", round6(pt.latitude))
                    put("lon", round6(pt.longitude))
                    pt.altitudeMeters?.let { put("alt_m", round1(it)) }
                    put("speed_kmh", round2(pt.speedMps * 3.6))
                    pt.bearingDegrees?.let { put("bearing_deg", round1(it.toDouble())) }
                    pt.accuracyMeters?.let { put("accuracy_m", round1(it.toDouble())) }
                    pt.heartRateBpm?.let { put("hr_bpm", it) }
                    put("lat_g", round2(pt.lateralG.toDouble()))
                    put("long_g", round2(pt.longitudinalG.toDouble()))
                    put("total_g", round2(pt.totalG.toDouble()))
                    put("lap", pt.lapNumber)
                }
            )
        }
        root.put("samples", samplesArr)

        return root.toString(2)
    }

    /**
     * Builds a tailored Chinese analysis prompt so users can paste the telemetry directly
     * into any AI assistant for immediate coaching feedback.
     */
    fun buildAiAnalysisPrompt(
        session: TrackSessionEntity,
        laps: List<TrackLapEntity>
    ): String {
        return when (session.activityMode) {
            ActivityMode.KARTING -> {
                val lapNote = if (laps.isNotEmpty()) {
                    "共记录 ${laps.size} 圈，请重点对比各圈成绩与最佳圈差距。"
                } else {
                    "本次未形成完整绕圈记录，请重点分析速度与过弯 G 力表现。"
                }
                """
你是一位专业的卡丁车赛道遥测数据分析教练。以下是我刚刚记录的一次卡丁车 GPS + G 力 + 心率 JSON 遥测数据（${session.name}）。$lapNote
请帮我按以下维度输出清晰、可落地的中文复盘报告：
1. 总体表现与圈速稳定性评价（逐圈对比极速、用时波动和体能衰减趋势）。
2. 刹车与过弯分析：结合 speed_kmh、lat_g（横向 G 力）、long_g（加减速 G 力），找出可能的重刹点、弯心速度偏低处以及出弯加速节奏问题。
3. 心率负荷评估：结合 heart_rate_bpm 与各心率区间耗时，评估本次驾驶的紧张度与心肺负荷。
4. 给出 3 条下一节赛道可以直接执行的提速改进建议。

以下是本次完整 JSON 遥测数据：
                """.trimIndent()
            }
            ActivityMode.OFFROAD_CROSSING -> {
                """
你是一位专业的摩托车越野穿越与山路骑行遥测分析教练。以下是我刚刚骑摩托车翻山越岭记录的 GPS + 海拔 + 速度 + G 力 + 心率 JSON 遥测数据（${session.name}）。
请帮我按以下维度输出清晰、实用的中文复盘报告：
1. 翻山穿越强度总评：结合总里程、用时、累计爬升/下降、最高海拔与连续翻越起伏，评估本次越野穿越路线难度。
2. 山路速度与弯道姿态分析：结合 alt_m（海拔起伏）、speed_kmh（骑行时速）、lat_g（过弯压弯横向 G 力）与 long_g（加减速/重刹 G 力），分析上坡攀爬、垭口山脊与长下坡控速节奏是否合理，识别高负荷弯道或急刹路段。
3. 骑行体能与心率负荷评估：结合 heart_rate_bpm 与 zones_sec（各心率区间时长），评估复杂非铺装或连续爬坡路段的体能消耗与专注度压力。
4. 给出 3 条下次摩托车越野穿越的控车节奏、体能补给与安全驾驶建议。

以下是本次完整 JSON 遥测数据：
                """.trimIndent()
            }
            ActivityMode.HIKING -> {
                """
你是一位专业的户外徒步与运动生理分析教练。以下是我刚刚记录的一次徒步 GPS + 海拔 + 速度 + 心率 JSON 遥测数据（${session.name}）。
请帮我按以下维度输出清晰、易读的中文复盘报告：
1. 路线强度总评：结合总距离、用时、累计爬升/下降与最高海拔，评估本次徒步难度等级。
2. 爬升与配速节奏分析：结合 alt_m（海拔变化）与 speed_kmh（移动速度），分析上坡爬升、山脊平路及下撤阶段的体能分配是否合理，找出掉速或疲劳节点。
3. 心肺区间与安全评估：结合 heart_rate_bpm 与 zones_sec（心率区间时长），分析有氧/无氧占比及高心率爬坡段表现。
4. 给出下次徒步的配速策略、补水补给和恢复建议。

以下是本次完整 JSON 遥测数据：
                """.trimIndent()
            }
        }
    }

    fun buildClipboardPayload(
        session: TrackSessionEntity,
        points: List<TrackPointEntity>,
        laps: List<TrackLapEntity>,
        includeAiPrompt: Boolean
    ): String {
        val json = buildStructuredJson(
            session = session,
            points = points,
            laps = laps,
            maxSamples = CLIPBOARD_MAX_SAMPLES
        )
        if (!includeAiPrompt) return json
        val prompt = buildAiAnalysisPrompt(session, laps)
        return "$prompt\n\n```json\n$json\n```"
    }

    fun exportToShareUri(
        context: Context,
        session: TrackSessionEntity,
        points: List<TrackPointEntity>,
        laps: List<TrackLapEntity>,
        includeAiPrompt: Boolean = false
    ): Uri {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val fileDate = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(session.startTimeMs))
        val prefix = when (session.activityMode) {
            ActivityMode.KARTING -> "karting"
            ActivityMode.HIKING -> "hiking"
            ActivityMode.OFFROAD_CROSSING -> "offroad"
        }
        val (ext, content) = if (includeAiPrompt) {
            "txt" to buildClipboardPayload(session, points, laps, includeAiPrompt = true)
        } else {
            "json" to buildStructuredJson(session, points, laps)
        }
        val outFile = File(dir, "${prefix}_${fileDate}_telemetry.$ext")
        outFile.writeText(content, Charsets.UTF_8)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outFile
        )
    }

    private fun downsampleIfNeeded(
        points: List<TrackPointEntity>,
        maxSamples: Int?
    ): List<TrackPointEntity> {
        if (maxSamples == null || points.size <= maxSamples || maxSamples < 2) {
            return points
        }
        val result = ArrayList<TrackPointEntity>(maxSamples)
        val step = (points.size - 1).toDouble() / (maxSamples - 1).toDouble()
        for (i in 0 until maxSamples) {
            val idx = round(i * step).toInt().coerceIn(0, points.lastIndex)
            result.add(points[idx])
        }
        return result
    }

    private fun computeHeartRateZonesSec(points: List<TrackPointEntity>): JSONObject {
        var z1 = 0.0
        var z2 = 0.0
        var z3 = 0.0
        var z4 = 0.0
        var z5 = 0.0
        for (i in 1 until points.size) {
            val hr = points[i].heartRateBpm ?: continue
            val dtSec = ((points[i].timestampMs - points[i - 1].timestampMs) / 1000.0)
                .coerceIn(0.0, 5.0)
            when {
                hr < 115 -> z1 += dtSec
                hr < 135 -> z2 += dtSec
                hr < 155 -> z3 += dtSec
                hr < 175 -> z4 += dtSec
                else -> z5 += dtSec
            }
        }
        return JSONObject().apply {
            put("z1_lt115", round1(z1))
            put("z2_115_134", round1(z2))
            put("z3_135_154", round1(z3))
            put("z4_155_174", round1(z4))
            put("z5_ge175", round1(z5))
        }
    }

    private fun round1(v: Double): Double = round(v * 10.0) / 10.0
    private fun round2(v: Double): Double = round(v * 100.0) / 100.0
    private fun round6(v: Double): Double = round(v * 1_000_000.0) / 1_000_000.0
}
