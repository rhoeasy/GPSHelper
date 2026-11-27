package com.example.gps

import java.util.Locale
import kotlin.math.roundToInt

enum class SpeedUnit(val label: String) {
    KMH("km/h"),
    MPH("mph");

    fun convertFromMps(speedMps: Float): Float {
        val clamped = speedMps.coerceAtLeast(0f)
        return when (this) {
            KMH -> clamped * 3.6f
            MPH -> clamped * 2.2369363f
        }
    }
}

enum class GpsFixStatus {
    NO_PERMISSION,
    DISABLED,
    SEARCHING,
    FUSED_FALLBACK,
    LOCKED
}

data class GpsData(
    val hasFix: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val speedMps: Float? = null,
    val altitudeMeters: Double? = null,
    val accuracyMeters: Float? = null,
    val bearingDegrees: Float? = null,
    val satellitesUsed: Int = 0,
    val satellitesVisible: Int = 0,
    val hasSatelliteInfo: Boolean = false,
    val provider: String? = null,
    val fixStatus: GpsFixStatus = GpsFixStatus.SEARCHING,
    val lastUpdateElapsedRealtimeMs: Long = 0L,
    val smoothedAltitudeMeters: Double? = null,
    val verticalAccuracyMeters: Float? = null
) {
    val displayAltitudeMeters: Double?
        get() = if (hasFix) altitudeMeters?.takeIf { it.isFinite() }?.let { smoothedAltitudeMeters ?: it } else null

    fun speedInUnit(unit: SpeedUnit): Float? {
        val mps = speedMps ?: return null
        if (!hasFix) return null
        val converted = unit.convertFromMps(mps)
        // Filter sub-walking GPS jitter (< 0.5 km/h) cleanly to 0.0 when locked
        return if (converted < 0.5f) 0f else converted
    }

    fun formattedSpeedValue(unit: SpeedUnit): String {
        val speed = speedInUnit(unit) ?: return "--"
        return if (speed >= 100f) {
            String.format(Locale.US, "%.0f", speed)
        } else {
            String.format(Locale.US, "%.1f", speed)
        }
    }

    fun formattedSpeedCompact(unit: SpeedUnit): String {
        val speed = speedInUnit(unit) ?: return "-- ${unit.label}"
        val rounded = speed.roundToInt()
        return "$rounded ${unit.label}"
    }

    fun formattedCoordinates(): String? {
        val lat = latitude ?: return null
        val lng = longitude ?: return null
        if (!hasFix) return null
        return String.format(Locale.US, "%.6f, %.6f", lat, lng)
    }

    fun formattedLatitude(): String {
        val lat = latitude ?: return "--"
        if (!hasFix) return "--"
        return String.format(Locale.US, "%.6f", lat)
    }

    fun formattedLongitude(): String {
        val lng = longitude ?: return "--"
        if (!hasFix) return "--"
        return String.format(Locale.US, "%.6f", lng)
    }

    fun formattedAltitude(): String {
        val alt = displayAltitudeMeters ?: return "--"
        return "${alt.roundToInt()} m"
    }

    fun formattedAccuracy(): String {
        val acc = accuracyMeters ?: return "--"
        if (!hasFix) return "--"
        return String.format(Locale.US, "±%.1f m", acc)
    }

    fun formattedBearing(): String {
        val bearing = bearingDegrees ?: return "--"
        if (!hasFix) return "--"
        val normalized = ((bearing % 360f) + 360f) % 360f
        val degreesInt = normalized.roundToInt() % 360
        return "${degreesInt}° ${compassDirectionZh(normalized)}"
    }

    fun formattedSatellites(): String {
        if (!hasSatelliteInfo && !hasFix) return "--"
        return "$satellitesUsed / $satellitesVisible 颗"
    }

    companion object {
        private val COMPASS_DIRECTIONS_ZH = arrayOf(
            "北", "东北", "东", "东南", "南", "西南", "西", "西北"
        )

        fun compassDirectionZh(bearingDegrees: Float): String {
            val normalized = ((bearingDegrees % 360f) + 360f) % 360f
            val index = (((normalized + 22.5f) / 45f).toInt()) % 8
            return COMPASS_DIRECTIONS_ZH[index]
        }
    }
}
