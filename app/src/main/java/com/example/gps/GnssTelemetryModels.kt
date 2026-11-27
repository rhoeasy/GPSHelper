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
        lastCountedAlt = null
        lastSampleAlt = null
        lastSampleMs = 0L
        gainM = 0.0
        lossM = 0.0
        smoothedVSpeedMps = 0f
        history.clear()
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
