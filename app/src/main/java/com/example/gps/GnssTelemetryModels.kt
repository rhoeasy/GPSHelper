package com.example.gps

import android.location.GnssStatus
import android.os.Build
import kotlin.math.abs

enum class GnssConstellation(val shortCode: String, val labelZh: String) {
    BEIDOU("BDS", "北斗"),
    GPS("GPS", "GPS"),
    GLONASS("GLO", "格洛纳斯"),
    GALILEO("GAL", "伽利略"),
    QZSS("QZS", "准天顶"),
    SBAS("SBA", "星基增强"),
    IRNSS("IRN", "NavIC"),
    UNKNOWN("OTH", "其他");

    companion object {
        fun fromGnssStatusType(type: Int): GnssConstellation {
            return when (type) {
                GnssStatus.CONSTELLATION_BEIDOU -> BEIDOU
                GnssStatus.CONSTELLATION_GPS -> GPS
                GnssStatus.CONSTELLATION_GLONASS -> GLONASS
                GnssStatus.CONSTELLATION_GALILEO -> GALILEO
                GnssStatus.CONSTELLATION_QZSS -> QZSS
                GnssStatus.CONSTELLATION_SBAS -> SBAS
                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        type == GnssStatus.CONSTELLATION_IRNSS
                    ) {
                        IRNSS
                    } else {
                        UNKNOWN
                    }
                }
            }
        }
    }
}

data class GnssSatelliteInfo(
    val svid: Int,
    val constellation: GnssConstellation,
    val cn0DbHz: Float,
    val elevationDegrees: Float,
    val azimuthDegrees: Float,
    val usedInFix: Boolean,
    val hasAlmanac: Boolean = false,
    val hasEphemeris: Boolean = false
)

data class AltitudeSample(
    val elapsedMs: Long,
    val altitudeMeters: Double
)

data class AltitudeTelemetry(
    val currentAltitudeM: Double? = null,
    val verticalAccuracyM: Float? = null,
    val minAltitudeM: Double? = null,
    val maxAltitudeM: Double? = null,
    val elevationGainM: Double = 0.0,
    val elevationLossM: Double = 0.0,
    val verticalSpeedMps: Float = 0f,
    val gradePercent: Float? = null,
    val samples: List<AltitudeSample> = emptyList()
)

/**
 * Pure deterministic altitude statistics & climb/descent tracker with jitter deadband filtering.
 */
class AltitudeSessionTracker {
    companion object {
        private const val ELEVATION_DEADBAND_M = 1.5
        private const val MAX_HISTORY_SAMPLES = 120
        private const val MIN_SAMPLE_INTERVAL_MS = 1_500L
    }

    private var minAlt: Double? = null
    private var maxAlt: Double? = null
    private var lastCountedAlt: Double? = null
    private var lastSampleAlt: Double? = null
    private var lastSampleMs: Long = 0L
    private var gainM: Double = 0.0
    private var lossM: Double = 0.0
    private var smoothedVSpeedMps: Float = 0f
    private val history = ArrayDeque<AltitudeSample>()

    fun reset() {
        minAlt = null
        maxAlt = null
        breakInterval()
        gainM = 0.0
        lossM = 0.0
        history.clear()
    }

    internal fun breakInterval() {
        lastCountedAlt = null
        lastSampleAlt = null
        lastSampleMs = 0L
        smoothedVSpeedMps = 0f
    }

    fun onAltitudeSample(
        altitudeM: Double,
        verticalAccuracyM: Float?,
        horizontalSpeedMps: Float?,
        elapsedMs: Long
    ): AltitudeTelemetry {
        if (minAlt == null || altitudeM < minAlt!!) minAlt = altitudeM
        if (maxAlt == null || altitudeM > maxAlt!!) maxAlt = altitudeM

        val prevCounted = lastCountedAlt
        if (prevCounted == null) {
            lastCountedAlt = altitudeM
        } else {
            val delta = altitudeM - prevCounted
            if (abs(delta) >= ELEVATION_DEADBAND_M) {
                if (delta > 0.0) gainM += delta else lossM += -delta
                lastCountedAlt = altitudeM
            }
        }

        val prevAlt = lastSampleAlt
        if (prevAlt != null && lastSampleMs > 0L) {
            val dtSec = ((elapsedMs - lastSampleMs) / 1000.0).toFloat()
            if (dtSec >= 0.5f && dtSec <= 30f) {
                val rawVSpeed = ((altitudeM - prevAlt).toFloat() / dtSec).coerceIn(-30f, 30f)
                smoothedVSpeedMps += 0.35f * (rawVSpeed - smoothedVSpeedMps)
            }
        }

        if (history.isEmpty() || (elapsedMs - lastSampleMs) >= MIN_SAMPLE_INTERVAL_MS) {
            history.addLast(AltitudeSample(elapsedMs, altitudeM))
            if (history.size > MAX_HISTORY_SAMPLES) {
                history.removeFirst()
            }
            lastSampleAlt = altitudeM
            lastSampleMs = elapsedMs
        }

        val gradePct = if (horizontalSpeedMps != null && horizontalSpeedMps >= 1.2f) {
            ((smoothedVSpeedMps / horizontalSpeedMps) * 100f).coerceIn(-45f, 45f)
        } else {
            null
        }

        return AltitudeTelemetry(
            currentAltitudeM = altitudeM,
            verticalAccuracyM = verticalAccuracyM,
            minAltitudeM = minAlt,
            maxAltitudeM = maxAlt,
            elevationGainM = gainM,
            elevationLossM = lossM,
            verticalSpeedMps = if (abs(smoothedVSpeedMps) < 0.08f) 0f else smoothedVSpeedMps,
            gradePercent = gradePct,
            samples = history.toList()
        )
    }
}

enum class AccuracyQualityGrade(val labelZh: String, val descZh: String) {
    EXCELLENT("极佳", "误差 ≤ 5 m，高精度锁定，适合弯道与越野精细记录"),
    GOOD("良好", "误差 5–12 m，户外正常骑行与徒步定位水准"),
    FAIR("一般", "误差 12–25 m，可能存在峡谷、树冠或楼宇遮挡"),
    POOR("偏弱", "误差 > 25 m，建议移步开阔地带等待卫星收敛"),
    WAITING("等待定位", "正在接收 GNSS 定位精度数据")
}

data class AccuracySample(
    val elapsedMs: Long,
    val horizontalAccuracyM: Float
)

data class AccuracyTelemetry(
    val currentHorizontalAccuracyM: Float? = null,
    val verticalAccuracyM: Float? = null,
    val speedAccuracyMps: Float? = null,
    val bearingAccuracyDeg: Float? = null,
    val bestAccuracyM: Float? = null,
    val worstAccuracyM: Float? = null,
    val avgAccuracyM: Float? = null,
    val sampleCount: Int = 0,
    val fixIntervalMs: Long? = null,
    val samples: List<AccuracySample> = emptyList()
) {
    val qualityGrade: AccuracyQualityGrade
        get() {
            val acc = currentHorizontalAccuracyM ?: return AccuracyQualityGrade.WAITING
            return when {
                acc <= 5.0f -> AccuracyQualityGrade.EXCELLENT
                acc <= 12.0f -> AccuracyQualityGrade.GOOD
                acc <= 25.0f -> AccuracyQualityGrade.FAIR
                else -> AccuracyQualityGrade.POOR
            }
        }

    /**
     * Stability score (0..100) combining average horizontal precision and consistency across samples.
     */
    val stabilityScore: Int?
        get() {
            val avg = avgAccuracyM ?: return null
            if (sampleCount <= 0) return null
            val precisionPts = when {
                avg <= 4f -> 70f
                avg <= 10f -> 70f - (avg - 4f) * 3.5f
                avg <= 25f -> 49f - (avg - 10f) * 2.0f
                else -> (19f - (avg - 25f) * 0.4f).coerceAtLeast(4f)
            }
            val spread = if (bestAccuracyM != null && worstAccuracyM != null) {
                (worstAccuracyM - bestAccuracyM).coerceAtLeast(0f)
            } else {
                0f
            }
            val consistencyPts = (30f - spread * 1.1f).coerceIn(4f, 30f)
            return (precisionPts + consistencyPts).toInt().coerceIn(0, 100)
        }
}

/**
 * Pure deterministic GNSS positioning accuracy statistics & trend tracker.
 */
class AccuracySessionTracker {
    companion object {
        private const val MAX_HISTORY_SAMPLES = 120
        private const val MIN_SAMPLE_INTERVAL_MS = 1_000L
    }

    private var bestAcc: Float? = null
    private var worstAcc: Float? = null
    private var accSum: Double = 0.0
    private var count: Int = 0
    private var lastFixElapsedMs: Long = 0L
    private var lastHistoryElapsedMs: Long = 0L
    private var smoothedIntervalMs: Long? = null
    private val history = ArrayDeque<AccuracySample>()

    fun reset() {
        bestAcc = null
        worstAcc = null
        accSum = 0.0
        count = 0
        lastFixElapsedMs = 0L
        lastHistoryElapsedMs = 0L
        smoothedIntervalMs = null
        history.clear()
    }

    fun onAccuracySample(
        horizontalAccuracyM: Float,
        verticalAccuracyM: Float?,
        speedAccuracyMps: Float?,
        bearingAccuracyDeg: Float?,
        elapsedMs: Long
    ): AccuracyTelemetry {
        val acc = horizontalAccuracyM.coerceAtLeast(0.1f)
        if (bestAcc == null || acc < bestAcc!!) bestAcc = acc
        if (worstAcc == null || acc > worstAcc!!) worstAcc = acc
        accSum += acc
        count++

        if (lastFixElapsedMs > 0L) {
            val delta = elapsedMs - lastFixElapsedMs
            if (delta in 100L..30_000L) {
                smoothedIntervalMs = if (smoothedIntervalMs == null) {
                    delta
                } else {
                    ((smoothedIntervalMs!! * 0.65) + (delta * 0.35)).toLong()
                }
            }
        }
        lastFixElapsedMs = elapsedMs

        if (history.isEmpty() || (elapsedMs - lastHistoryElapsedMs) >= MIN_SAMPLE_INTERVAL_MS) {
            history.addLast(AccuracySample(elapsedMs, acc))
            if (history.size > MAX_HISTORY_SAMPLES) {
                history.removeFirst()
            }
            lastHistoryElapsedMs = elapsedMs
        }

        return AccuracyTelemetry(
            currentHorizontalAccuracyM = acc,
            verticalAccuracyM = verticalAccuracyM,
            speedAccuracyMps = speedAccuracyMps,
            bearingAccuracyDeg = bearingAccuracyDeg,
            bestAccuracyM = bestAcc,
            worstAccuracyM = worstAcc,
            avgAccuracyM = (accSum / count).toFloat(),
            sampleCount = count,
            fixIntervalMs = smoothedIntervalMs,
            samples = history.toList()
        )
    }
}

