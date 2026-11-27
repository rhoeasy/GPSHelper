package com.example.gps

/** Filters the live readout only; raw altitude is still available for recording and diagnostics. */
internal class LiveAltitudeFilter {
    private var filteredMeters: Double? = null
    private var previousRawMeters = 0.0
    private var olderRawMeters = 0.0
    private var lastElapsedMs = 0L

    fun update(rawMeters: Double?, verticalAccuracyM: Float?, elapsedMs: Long): Double? {
        if (rawMeters == null) {
            reset()
            return null
        }

        val previous = filteredMeters
        if (previous == null) {
            filteredMeters = rawMeters
            previousRawMeters = rawMeters
            olderRawMeters = rawMeters
            lastElapsedMs = elapsedMs
            return rawMeters
        }

        // Median of three rejects a single bad fix; time-based smoothing softens the remaining noise.
        val median = rawMeters.coerceIn(minOf(previousRawMeters, olderRawMeters), maxOf(previousRawMeters, olderRawMeters))
        val intervalMs = (elapsedMs - lastElapsedMs).toDouble()
        val smoothingMs = if (verticalAccuracyM != null && verticalAccuracyM >= 20f) 3_000.0 else 1_500.0
        val result = previous + (median - previous) * (intervalMs / (intervalMs + smoothingMs))
        filteredMeters = result
        olderRawMeters = previousRawMeters
        previousRawMeters = rawMeters
        lastElapsedMs = elapsedMs
        return result
    }

    fun reset() {
        filteredMeters = null
    }
}
