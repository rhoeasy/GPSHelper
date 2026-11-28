package com.example.overlay

import com.example.gps.GpsData
import com.example.gps.GpsFixStatus
import java.util.Locale

internal class AppRideClock {
    private var openedAtMs: Long? = null

    fun markOpened(nowMs: Long) {
        if (openedAtMs == null) openedAtMs = nowMs
    }

    fun elapsedMs(nowMs: Long): Long? = openedAtMs?.let { (nowMs - it).coerceAtLeast(0L) }
}

internal val appRideClock = AppRideClock()

internal data class RideTimerReadout(val elapsedText: String, val completedMarks: Long) {
    val visibleMarks: Int get() = completedMarks.coerceAtMost(5L).toInt()
    val extraMarks: Long get() = completedMarks - visibleMarks
}

internal fun rideTimerReadout(elapsedMs: Long?, intervalMinutes: Int): RideTimerReadout {
    if (elapsedMs == null) return RideTimerReadout("--:--:--", 0L)
    val seconds = elapsedMs / 1_000L
    return RideTimerReadout(
        elapsedText = String.format(Locale.US, "%02d:%02d:%02d", seconds / 3_600L, seconds / 60L % 60L, seconds % 60L),
        completedMarks = elapsedMs / (intervalMinutes * 60_000L)
    )
}

internal enum class OverlayGpsSignal(val bars: Int = 0) {
    NO_PERMISSION, DISABLED, SEARCHING, FUSED, LOCKED, WEAK(1), FAIR(2), GOOD(3)
}

internal fun overlayGpsSignal(gps: GpsData): OverlayGpsSignal = when (gps.fixStatus) {
    GpsFixStatus.NO_PERMISSION -> OverlayGpsSignal.NO_PERMISSION
    GpsFixStatus.DISABLED -> OverlayGpsSignal.DISABLED
    GpsFixStatus.SEARCHING -> OverlayGpsSignal.SEARCHING
    GpsFixStatus.FUSED_FALLBACK -> if (gps.hasFix) OverlayGpsSignal.FUSED else OverlayGpsSignal.SEARCHING
    GpsFixStatus.LOCKED -> {
        val accuracy = gps.accuracyMeters
        when {
            !gps.hasFix -> OverlayGpsSignal.SEARCHING
            accuracy == null || !accuracy.isFinite() || accuracy < 0f -> OverlayGpsSignal.LOCKED
            accuracy <= 10f -> OverlayGpsSignal.GOOD
            accuracy <= 25f -> OverlayGpsSignal.FAIR
            else -> OverlayGpsSignal.WEAK
        }
    }
}
