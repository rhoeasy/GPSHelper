package com.example.track

import com.example.gps.SpeedUnit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object TrackFormatters {

    fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatLapTime(durationMs: Long?): String {
        if (durationMs == null || durationMs <= 0L) return "--"
        val minutes = (durationMs / 60_000L)
        val seconds = (durationMs % 60_000L) / 1000L
        val hundredths = (durationMs % 1000L) / 10L
        return if (minutes > 0L) {
            String.format(Locale.US, "%d:%02d.%02d", minutes, seconds, hundredths)
        } else {
            String.format(Locale.US, "%02d.%02ds", seconds, hundredths)
        }
    }

    fun formatLapDelta(lapMs: Long, bestMs: Long): String {
        val diff = lapMs - bestMs
        if (diff <= 10L) return "最佳"
        val seconds = diff / 1000.0
        return String.format(Locale.US, "+%.2fs", seconds)
    }

    fun formatDistance(distanceMeters: Double): String {
        return if (distanceMeters >= 1000.0) {
            String.format(Locale.US, "%.2f km", distanceMeters / 1000.0)
        } else {
            String.format(Locale.US, "%d m", distanceMeters.roundToInt())
        }
    }

    fun formatSpeed(speedMps: Float, unit: SpeedUnit): String {
        val converted = unit.convertFromMps(speedMps)
        return String.format(Locale.US, "%.1f %s", converted, unit.label)
    }

    fun formatPaceMinPerKm(distanceMeters: Double, durationMs: Long): String {
        if (distanceMeters < 50.0 || durationMs < 5_000L) return "--"
        val secondsPerKm = (durationMs / 1000.0) / (distanceMeters / 1000.0)
        if (secondsPerKm > 3600.0 || secondsPerKm < 60.0) return "--"
        val min = (secondsPerKm / 60.0).toInt()
        val sec = (secondsPerKm % 60.0).roundToInt() % 60
        return String.format(Locale.US, "%d'%02d\"/km", min, sec)
    }

    fun formatElevationGain(meters: Double): String {
        return String.format(Locale.US, "+%d m", meters.roundToInt().coerceAtLeast(0))
    }

    fun formatElevationLoss(meters: Double): String {
        return String.format(Locale.US, "-%d m", meters.roundToInt().coerceAtLeast(0))
    }

    fun formatAltitude(meters: Double?): String {
        if (meters == null) return "--"
        return String.format(Locale.US, "%.1f m", meters)
    }

    fun formatSessionTitle(mode: ActivityMode, startTimeMs: Long): String {
        val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        return "${mode.labelZh} · ${fmt.format(Date(startTimeMs))}"
    }

    fun formatDateTime(timestampMs: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return fmt.format(Date(timestampMs))
    }
}
