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
import kotlin.math.abs
import kotlin.math.round

/**
 * Generates clean, self-describing structured JSON telemetry packages designed for
 * direct ingestion by AI Agents (combining GPS, altitude, speed, BLE heart rate zones,
 * G-Force peaks, and karting lap splits).
 */
object TelemetryJsonExporter {

    fun buildStructuredJson(
        session: TrackSessionEntity,
        points: List<TrackPointEntity>,
        laps: List<TrackLapEntity>
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

        val samplesArr = JSONArray()
        val t0 = points.firstOrNull()?.timestampMs ?: session.startTimeMs
        points.forEach { pt ->
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

    fun exportToShareUri(
        context: Context,
        session: TrackSessionEntity,
        points: List<TrackPointEntity>,
        laps: List<TrackLapEntity>
    ): Uri {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val fileDate = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(session.startTimeMs))
        val prefix = if (session.activityMode == ActivityMode.KARTING) "karting" else "hiking"
        val outFile = File(dir, "${prefix}_${fileDate}_telemetry.json")
        outFile.writeText(buildStructuredJson(session, points, laps), Charsets.UTF_8)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outFile
        )
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
