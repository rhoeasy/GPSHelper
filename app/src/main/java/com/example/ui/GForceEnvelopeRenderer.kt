package com.example.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.sensor.GForceData
import com.example.sensor.GForceFusionEngine
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Draws the recorded 360° polar max G-Force friction-circle envelope shape (and peak vertex
 * marker) inside both the floating HUD dial and the dashboard G-Force radar.
 *
 * Sector index `i` (0..35) represents `i * 10°` in Canvas coordinates:
 *   0°   = Screen Right (+lateralG)
 *   90°  = Screen Down  (-longitudinalG, braking)
 *   180° = Screen Left  (-lateralG)
 *   270° = Screen Up    (+longitudinalG, acceleration)
 */
fun DrawScope.drawGForcePeakEnvelope(
    gForceData: GForceData,
    cx: Float,
    cy: Float,
    maxTravel: Float,
    maxScaleG: Float,
    peakColor: Color,
    strokeWidthPx: Float,
    peakDotRadiusPx: Float
) {
    if (gForceData.peakG < 0.05f) return

    val envelope = gForceData.peakEnvelope
    val sectorCount = GForceFusionEngine.PEAK_ENVELOPE_SECTORS

    if (envelope.size == sectorCount) {
        val active = ArrayList<Pair<Int, Float>>(sectorCount)
        for (i in 0 until sectorCount) {
            val v = envelope[i]
            if (v >= 0.04f) {
                active.add(i to v)
            }
        }

        if (active.isNotEmpty()) {
            val points = buildEnvelopeControlPoints(
                active = active,
                sectorCount = sectorCount,
                cx = cx,
                cy = cy,
                maxTravel = maxTravel,
                maxScaleG = maxScaleG
            )

            if (points.size >= 3) {
                val path = buildSmoothClosedPath(points)
                drawPath(
                    path = path,
                    color = peakColor.copy(alpha = 0.22f),
                    style = Fill
                )
                drawPath(
                    path = path,
                    color = peakColor.copy(alpha = 0.88f),
                    style = Stroke(
                        width = strokeWidthPx,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }

    // Highlight the single overall highest peak G-Force vertex on the envelope boundary
    val nx = gForceData.peakLateralG / maxScaleG
    val ny = -gForceData.peakLongitudinalG / maxScaleG
    val mag = hypot(nx, ny)
    val scale = if (mag > 1f) 1f / mag else 1f
    val peakPos = Offset(cx + nx * scale * maxTravel, cy + ny * scale * maxTravel)

    drawCircle(
        color = peakColor.copy(alpha = 0.30f),
        radius = peakDotRadiusPx * 1.6f,
        center = peakPos
    )
    drawCircle(
        color = peakColor,
        radius = peakDotRadiusPx,
        center = peakPos,
        style = Stroke(width = strokeWidthPx * 1.15f)
    )
}

private fun buildEnvelopeControlPoints(
    active: List<Pair<Int, Float>>,
    sectorCount: Int,
    cx: Float,
    cy: Float,
    maxTravel: Float,
    maxScaleG: Float
): List<Offset> {
    val degPerSector = 360.0 / sectorCount
    val center = Offset(cx, cy)

    if (active.size == 1) {
        val (idx, gVal) = active[0]
        val rPx = ((gVal / maxScaleG).coerceIn(0f, 1f)) * maxTravel
        val centerRad = idx * degPerSector * PI / 180.0
        val leftRad = (idx * degPerSector - 14.0) * PI / 180.0
        val rightRad = (idx * degPerSector + 14.0) * PI / 180.0
        return listOf(
            center,
            Offset(cx + (cos(leftRad) * rPx * 0.65).toFloat(), cy + (sin(leftRad) * rPx * 0.65).toFloat()),
            Offset(cx + (cos(centerRad) * rPx).toFloat(), cy + (sin(centerRad) * rPx).toFloat()),
            Offset(cx + (cos(rightRad) * rPx * 0.65).toFloat(), cy + (sin(rightRad) * rPx * 0.65).toFloat())
        )
    }

    val result = ArrayList<Offset>(active.size + 2)
    for (k in active.indices) {
        val (idx, gVal) = active[k]
        val nextIdx = active[(k + 1) % active.size].first
        val rad = idx * degPerSector * PI / 180.0
        val rPx = ((gVal / maxScaleG).coerceIn(0f, 1f)) * maxTravel
        result.add(Offset(cx + (cos(rad) * rPx).toFloat(), cy + (sin(rad) * rPx).toFloat()))

        val sectorGap = (nextIdx - idx + sectorCount) % sectorCount
        val gapDeg = if (sectorGap == 0) 360.0 else sectorGap * degPerSector
        // Anchor back through origin when an entire >115° side has zero recorded G-force
        if (gapDeg > 115.0) {
            result.add(center)
        }
    }
    return result
}

private fun buildSmoothClosedPath(points: List<Offset>): Path {
    val path = Path()
    val count = points.size
    val firstMid = midpoint(points[count - 1], points[0])
    path.moveTo(firstMid.x, firstMid.y)
    for (i in 0 until count) {
        val current = points[i]
        val next = points[(i + 1) % count]
        val nextMid = midpoint(current, next)
        path.quadraticTo(current.x, current.y, nextMid.x, nextMid.y)
    }
    path.close()
    return path
}

private fun midpoint(a: Offset, b: Offset): Offset {
    return Offset((a.x + b.x) * 0.5f, (a.y + b.y) * 0.5f)
}
