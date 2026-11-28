package com.example.gps

import kotlin.math.abs

internal data class FilteredAltitude(
    val acceptedMeters: Double?,
    val displayMeters: Double?,
    val startsNewInterval: Boolean = false
)

/** Filters altitude without discarding the rest of the location fix. */
internal class LiveAltitudeFilter {
    private var filteredMeters: Double? = null
    private var previousRawMeters = 0.0
    private var olderRawMeters = 0.0
    private var lastElapsedMs = 0L
    private var missingAltitude = false

    fun update(rawMeters: Double?, verticalAccuracyM: Float?, isGpsPrimary: Boolean, elapsedMs: Long): FilteredAltitude {
        if (rawMeters == null || !rawMeters.isFinite()) {
            missingAltitude = true
            return FilteredAltitude(null, null)
        }
        if ((verticalAccuracyM == null && !isGpsPrimary) ||
            (verticalAccuracyM != null && verticalAccuracyM !in 0f..MAX_VERTICAL_ACCURACY_M)
        ) {
            return FilteredAltitude(null, null)
        }

        val previous = filteredMeters
        val elapsedSinceAccepted = elapsedMs - lastElapsedMs
        val startsNewInterval = previous == null || elapsedSinceAccepted >= GpsRepository.GPS_FRESHNESS_WINDOW_MS
        if (previous != null && !startsNewInterval) {
            val allowedJump = JUMP_ALLOWANCE_M + MAX_VERTICAL_SPEED_MPS * elapsedSinceAccepted / 1_000.0
            if (abs(rawMeters - previousRawMeters) > allowedJump) {
                // Bridge a brief spike on screen, without renewing freshness or recording it.
                val display = previous.takeIf { !missingAltitude && elapsedSinceAccepted < MAX_SPIKE_HOLD_MS }
                return FilteredAltitude(null, display)
            }
        }
        if (previous == null || startsNewInterval || missingAltitude) {
            filteredMeters = rawMeters
            previousRawMeters = rawMeters
            olderRawMeters = rawMeters
            lastElapsedMs = elapsedMs
            missingAltitude = false
            return FilteredAltitude(rawMeters, rawMeters, startsNewInterval)
        }

        // Median of three rejects a single bad fix; time-based smoothing softens the remaining noise.
        val median = rawMeters.coerceIn(minOf(previousRawMeters, olderRawMeters), maxOf(previousRawMeters, olderRawMeters))
        val intervalMs = elapsedSinceAccepted.toDouble()
        val smoothingMs = if (verticalAccuracyM != null && verticalAccuracyM >= 15f) 3_000.0 else 1_500.0
        val deadband = ((verticalAccuracyM ?: 6f) / 3.0).coerceIn(1.5, 4.0)
        val delta = median - previous
        val result = if (abs(delta) <= deadband) previous else previous + delta * (intervalMs / (intervalMs + smoothingMs))
        filteredMeters = result
        olderRawMeters = previousRawMeters
        previousRawMeters = rawMeters
        lastElapsedMs = elapsedMs
        return FilteredAltitude(rawMeters, result)
    }

    fun reset() {
        filteredMeters = null
        missingAltitude = false
    }

    private companion object {
        const val MAX_VERTICAL_ACCURACY_M = 25f
        const val JUMP_ALLOWANCE_M = 12.0
        const val MAX_VERTICAL_SPEED_MPS = 5.0
        const val MAX_SPIKE_HOLD_MS = 3_000L
    }
}
