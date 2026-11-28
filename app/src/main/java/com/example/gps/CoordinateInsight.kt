package com.example.gps

import android.location.Location
import java.util.Locale
import kotlin.math.abs

internal data class MapCoordinate(val latitude: Double, val longitude: Double) {
    fun formatted(): String = String.format(Locale.US, "%.6f, %.6f", latitude, longitude)
}

internal fun GpsData.currentCoordinate(): MapCoordinate? {
    val lat = latitude ?: return null
    val lon = longitude ?: return null
    return if (hasFix && lat in -90.0..90.0 && lon in -180.0..180.0) MapCoordinate(lat, lon) else null
}

private val coordinateSeparators = Regex("[\\s,\uFF0C;\uFF1B]+")

internal fun parseMapCoordinate(text: String): MapCoordinate? {
    val parts = text.trim().removeSurrounding("(", ")").trim().split(coordinateSeparators)
    if (parts.size != 2) return null
    val first = parts[0].toDoubleOrNull() ?: return null
    val second = parts[1].toDoubleOrNull() ?: return null
    return when {
        first in -90.0..90.0 && second in -180.0..180.0 -> MapCoordinate(first, second)
        abs(first) in 90.0..180.0 && second in -90.0..90.0 -> MapCoordinate(second, first)
        else -> null
    }
}

internal data class CoordinateRelation(val distanceMeters: Float, val bearingDegrees: Float?)

internal fun compareCoordinates(from: MapCoordinate, to: MapCoordinate): CoordinateRelation {
    val result = FloatArray(2)
    Location.distanceBetween(from.latitude, from.longitude, to.latitude, to.longitude, result)
    return CoordinateRelation(result[0], if (result[0] >= 1f) (result[1] + 360f) % 360f else null)
}

internal const val COORDINATE_INSIGHT_SYSTEM_PROMPT = """
You help an outdoor traveler interpret WGS84 coordinates. Answer concisely in Simplified Chinese.
You have no live maps, road, weather, or business data. Separate geographic estimates from facts supplied
by the app. Do not invent exact addresses, named nearby businesses, live conditions, or a navigable route.
If the location is unfamiliar, say so and suggest what the traveler can verify on a map.
"""

internal fun coordinateInsightPrompt(
    current: MapCoordinate,
    target: MapCoordinate?,
    relation: CoordinateRelation?
): String = buildString {
    appendLine("Current WGS84 position: ${current.formatted()}")
    if (target == null || relation == null) {
        append("Explain my approximate surroundings within about 1 km: region, likely terrain and map features to verify, and practical checks for someone riding or walking here. Mark uncertainty explicitly.")
    } else {
        appendLine("Target WGS84 position: ${target.formatted()}")
        appendLine("App-calculated straight-line distance in meters: ${relation.distanceMeters}")
        relation.bearingDegrees?.let { appendLine("Initial bearing from my position to the target in degrees clockwise from true north: $it") }
        append("Interpret the target's approximate location and its position relative to me. Suggest terrain or access details I should verify before visiting. Distinguish straight-line distance from travel distance.")
    }
}
