package com.example.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.gps.SpeedUnit
import com.example.track.ActivityMode
import com.example.track.F1SectorStatus
import com.example.track.KartingF1SectorAnalysis
import com.example.track.TrackFormatters
import com.example.track.TrackPointEntity
import com.example.track.findRoughTrackSegments
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalTelemetryColors
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos

private const val CHART_PAUSE_GAP_THRESHOLD_MS = 60_000L
private const val CHART_COMPRESSED_GAP_MS = 1_000L

/**
 * Computes active elapsed milliseconds at each point, compressing overnight/rest gaps (> 60s)
 * to 1s so merged multi-day tracks plot seamlessly without squashing active segments to the edges.
 * For continuous single-session tracks, this equals exact wall-clock elapsed time.
 */
private fun computeActiveElapsedMs(points: List<TrackPointEntity>): LongArray {
    if (points.isEmpty()) return LongArray(0)
    val result = LongArray(points.size)
    for (i in 1 until points.size) {
        val rawDt = (points[i].timestampMs - points[i - 1].timestampMs).coerceAtLeast(0L)
        val effectiveDt = if (rawDt > CHART_PAUSE_GAP_THRESHOLD_MS) CHART_COMPRESSED_GAP_MS else rawDt
        result[i] = result[i - 1] + effectiveDt
    }
    return result
}

/**
 * Pure helper mapping a normalized horizontal time fraction [0f..1f] to the nearest track point index
 * by active elapsed time so stacked profile charts share an exact X-axis alignment.
 */
fun findNearestPointIndexByTimeFraction(
    points: List<TrackPointEntity>,
    fraction: Float
): Int {
    if (points.size <= 1) return 0
    val clamped = fraction.coerceIn(0f, 1f)
    val activeMs = computeActiveElapsedMs(points)
    val spanMs = activeMs.last()
    if (spanMs <= 0L) {
        return ((points.lastIndex) * clamped).toInt().coerceIn(0, points.lastIndex)
    }
    val targetMs = (spanMs * clamped).toLong()
    var bestIdx = 0
    var bestDiff = Long.MAX_VALUE
    for (i in points.indices) {
        val diff = abs(activeMs[i] - targetMs)
        if (diff < bestDiff) {
            bestDiff = diff
            bestIdx = i
        }
    }
    return bestIdx
}

fun normalizedTimeFraction(points: List<TrackPointEntity>, index: Int): Float {
    if (points.size <= 1) return 0f
    val clampedIdx = index.coerceIn(0, points.lastIndex)
    val activeMs = computeActiveElapsedMs(points)
    val spanMs = activeMs.last()
    return if (spanMs <= 0L) {
        clampedIdx.toFloat() / points.lastIndex.toFloat()
    } else {
        (activeMs[clampedIdx].toFloat() / spanMs.toFloat()).coerceIn(0f, 1f)
    }
}

@Composable
fun VectorTrackCanvasCard(
    points: List<TrackPointEntity>,
    mode: ActivityMode,
    speedUnit: SpeedUnit,
    highlightedIndex: Int? = null,
    f1SectorAnalysis: KartingF1SectorAnalysis? = null,
    selectedLapNumber: Int? = null,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val lowColor = cs.primary
    val midColor = cs.secondary
    val highColor = cs.tertiary
    val peakColor = cs.error
    val markerRingColor = cs.surface
    val roughSegmentColor = Color(0xFFFF9B28)

    val canShowF1Sectors = mode == ActivityMode.KARTING && f1SectorAnalysis != null
    var showF1Sectors by remember(mode, canShowF1Sectors) { mutableStateOf(canShowF1Sectors) }
    val activeF1Lap = remember(f1SectorAnalysis, selectedLapNumber) {
        f1SectorAnalysis?.timingForLap(selectedLapNumber)
    }
    val showingF1Lap = canShowF1Sectors && showF1Sectors && activeF1Lap != null
    val roughSegments = remember(points) { findRoughTrackSegments(points) }
    val hasVibrationData = remember(points) { points.any { it.verticalVibrationG != null } }
    var showRoughSegments by remember(points) { mutableStateOf(true) }
    val density = LocalDensity.current
    val gateTextPaint = remember(density) {
        Paint().apply {
            isAntiAlias = true
            color = Color.White.toArgb()
            textSize = with(density) { 10.sp.toPx() }
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = cs.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.detail_track_canvas_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = cs.onSurface
                )

                if (canShowF1Sectors) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(cs.surfaceContainer)
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        MapModePill(
                            label = stringResource(R.string.detail_map_mode_f1),
                            selected = showF1Sectors,
                            testTag = "btn_track_map_f1_mode",
                            onClick = { showF1Sectors = true }
                        )
                        MapModePill(
                            label = stringResource(R.string.detail_map_mode_speed),
                            selected = !showF1Sectors,
                            testTag = "btn_track_map_speed_mode",
                            onClick = { showF1Sectors = false }
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (mode == ActivityMode.KARTING) {
                                "${stringResource(R.string.detail_chart_speed)} ${stringResource(R.string.detail_legend_low)}"
                            } else {
                                "${stringResource(R.string.detail_chart_elevation)} ${stringResource(R.string.detail_legend_low)}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant
                        )
                        Box(
                            modifier = Modifier
                                .size(width = 52.dp, height = 6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = if (mode == ActivityMode.KARTING) {
                                            listOf(lowColor, midColor, highColor, peakColor)
                                        } else {
                                            listOf(lowColor, midColor, highColor)
                                        }
                                    )
                                )
                        )
                        Text(
                            text = stringResource(R.string.detail_legend_high),
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant
                        )
                    }
                }
            }

            if (!showingF1Lap && hasVibrationData) {
                FilterChip(
                    selected = showRoughSegments && roughSegments.isNotEmpty(),
                    enabled = roughSegments.isNotEmpty(),
                    onClick = { showRoughSegments = !showRoughSegments },
                    label = { Text(stringResource(R.string.detail_rough_track_segments, roughSegments.size)) },
                    modifier = Modifier.testTag("track_rough_segments_toggle")
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(236.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(cs.surfaceContainer)
            ) {
                if (points.size < 2) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "--",
                            style = MaterialTheme.typography.titleMedium,
                            color = cs.outline
                        )
                    }
                } else {
                    val gridColor = cs.outlineVariant.copy(alpha = 0.65f)
                    val underlayColor = cs.outlineVariant.copy(alpha = 0.32f)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        for (i in 1..3) {
                            val y = size.height * i / 4f
                            val x = size.width * i / 4f
                            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1f, pathEffect = dash)
                            drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), 1f, pathEffect = dash)
                        }

                        val avgLat = points.map { it.latitude }.average()
                        val lonScale = cos(Math.toRadians(avgLat)).coerceIn(0.2, 1.0)

                        val xs = points.map { it.longitude * lonScale }
                        val ys = points.map { it.latitude }
                        val minX = xs.min()
                        val maxX = xs.max()
                        val minY = ys.min()
                        val maxY = ys.max()

                        val spanX = (maxX - minX).coerceAtLeast(1e-5)
                        val spanY = (maxY - minY).coerceAtLeast(1e-5)

                        val pad = 26.dp.toPx()
                        val drawW = (size.width - pad * 2f).coerceAtLeast(1f)
                        val drawH = (size.height - pad * 2f).coerceAtLeast(1f)
                        val scale = minOf(drawW / spanX.toFloat(), drawH / spanY.toFloat())
                        val offsetX = pad + (drawW - spanX.toFloat() * scale) / 2f
                        val offsetY = pad + (drawH - spanY.toFloat() * scale) / 2f

                        fun project(lat: Double, lon: Double): Offset {
                            val sx = offsetX + (((lon * lonScale) - minX).toFloat() * scale)
                            val sy = size.height - (offsetY + ((lat - minY).toFloat() * scale))
                            return Offset(sx, sy)
                        }

                        val screenPoints = points.indices.map { i ->
                            val sx = offsetX + ((xs[i] - minX).toFloat() * scale)
                            val sy = size.height - (offsetY + ((ys[i] - minY).toFloat() * scale))
                            Offset(sx, sy)
                        }

                        if (canShowF1Sectors && showF1Sectors && f1SectorAnalysis != null && activeF1Lap != null) {
                            // Subtle full-session reference underlay for multi-lap circuits
                            if (f1SectorAnalysis.isMultiLap) {
                                val underlayStroke = 2.4.dp.toPx()
                                for (i in 0 until screenPoints.lastIndex) {
                                    drawLine(
                                        color = underlayColor,
                                        start = screenPoints[i],
                                        end = screenPoints[i + 1],
                                        strokeWidth = underlayStroke,
                                        cap = StrokeCap.Round
                                    )
                                }
                            }

                            // Draw selected lap segments colored by authentic F1 sector status (Purple/Green/Yellow)
                            val lapRange = activeF1Lap.pointIndices
                            val startIdx = lapRange.first.coerceIn(0, screenPoints.lastIndex)
                            val endIdx = lapRange.last.coerceIn(startIdx, screenPoints.lastIndex)
                            val f1Stroke = 4.6.dp.toPx()
                            for (i in startIdx until endIdx) {
                                val secIdx = activeF1Lap.pointSectorMap[i]
                                    ?: f1SectorAnalysis.allPointsSectorIndex.getOrElse(i) { 0 }
                                val secStatus = activeF1Lap.sectors.getOrNull(secIdx)?.status
                                    ?: F1SectorStatus.YELLOW
                                drawLine(
                                    color = f1StatusColor(secStatus),
                                    start = screenPoints[i],
                                    end = screenPoints[i + 1],
                                    strokeWidth = f1Stroke,
                                    cap = StrokeCap.Round
                                )
                            }

                            // Draw S1, S2, S3 timing gate markers and crisp pill labels on the circuit
                            val badgeW = 24.dp.toPx()
                            val badgeH = 15.dp.toPx()
                            val badgeRadius = 6.dp.toPx()
                            f1SectorAnalysis.gates.forEachIndexed { idx, gate ->
                                val gp = project(gate.latitude, gate.longitude)
                                val secStatus = activeF1Lap.sectors.getOrNull(idx)?.status
                                    ?: F1SectorStatus.YELLOW
                                val gateColor = f1StatusColor(secStatus)

                                // Gate anchor dot
                                drawCircle(color = gateColor.copy(alpha = 0.28f), radius = 8.dp.toPx(), center = gp)
                                drawCircle(color = markerRingColor, radius = 5.dp.toPx(), center = gp)
                                drawCircle(color = gateColor, radius = 3.2.dp.toPx(), center = gp)

                                // Offset pill label slightly outward from track center to reduce overlap
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                val dirX = if (gp.x >= cx) 1f else -1f
                                val dirY = if (gp.y >= cy) 1f else -1f
                                val labelCenterX = (gp.x + dirX * 15.dp.toPx())
                                    .coerceIn(badgeW / 2f + 4f, size.width - badgeW / 2f - 4f)
                                val labelCenterY = (gp.y + dirY * 13.dp.toPx())
                                    .coerceIn(badgeH / 2f + 4f, size.height - badgeH / 2f - 26.dp.toPx())

                                drawRoundRect(
                                    color = gateColor,
                                    topLeft = Offset(labelCenterX - badgeW / 2f, labelCenterY - badgeH / 2f),
                                    size = Size(badgeW, badgeH),
                                    cornerRadius = CornerRadius(badgeRadius, badgeRadius)
                                )
                                val textBaseline = labelCenterY - (gateTextPaint.descent() + gateTextPaint.ascent()) / 2f
                                drawContext.canvas.nativeCanvas.drawText(
                                    gate.label,
                                    labelCenterX,
                                    textBaseline,
                                    gateTextPaint
                                )
                            }
                        } else {
                            val metrics: List<Double?> = if (mode == ActivityMode.KARTING) {
                                points.map { it.measuredSpeedMps?.let(speedUnit::convertFromMps)?.toDouble() }
                            } else {
                                points.map { it.altitudeMeters ?: 0.0 }
                            }
                            val knownMetrics = metrics.filterNotNull()
                            val minMetric = knownMetrics.minOrNull() ?: 0.0
                            val maxMetric = knownMetrics.maxOrNull() ?: 1.0
                            val metricSpan = (maxMetric - minMetric).coerceAtLeast(1.0)

                            val strokePx = 3.5.dp.toPx()
                            for (i in 0 until screenPoints.lastIndex) {
                                if (points[i + 1].timingBreakBefore == true) continue
                                val from = metrics[i]
                                val to = metrics[i + 1]
                                if (from == null || to == null) {
                                    drawLine(cs.outline, screenPoints[i], screenPoints[i + 1], strokePx, StrokeCap.Round)
                                    continue
                                }
                                val ratio = (((from + to) / 2.0 - minMetric) / metricSpan)
                                    .toFloat()
                                    .coerceIn(0f, 1f)
                                val segColor = if (mode == ActivityMode.KARTING) {
                                    when {
                                        ratio < 0.33f -> lerp(lowColor, midColor, ratio / 0.33f)
                                        ratio < 0.66f -> lerp(midColor, highColor, (ratio - 0.33f) / 0.33f)
                                        else -> lerp(highColor, peakColor, (ratio - 0.66f) / 0.34f)
                                    }
                                } else {
                                    if (ratio < 0.5f) {
                                        lerp(lowColor, midColor, ratio / 0.5f)
                                    } else {
                                        lerp(midColor, highColor, (ratio - 0.5f) / 0.5f)
                                    }
                                }
                                drawLine(
                                    color = segColor,
                                    start = screenPoints[i],
                                    end = screenPoints[i + 1],
                                    strokeWidth = strokePx,
                                    cap = StrokeCap.Round
                                )
                            }

                            if (showRoughSegments) {
                                roughSegments.forEach { range ->
                                    val path = Path().apply {
                                        moveTo(screenPoints[range.first].x, screenPoints[range.first].y)
                                        for (i in range.first + 1..range.last) {
                                            lineTo(screenPoints[i].x, screenPoints[i].y)
                                        }
                                    }
                                    drawPath(path, color = markerRingColor, style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
                                    drawPath(path, color = roughSegmentColor, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
                                }
                            }

                            val startPt = screenPoints.first()
                            drawCircle(color = midColor.copy(alpha = 0.28f), radius = 9.dp.toPx(), center = startPt)
                            drawCircle(color = midColor, radius = 5.dp.toPx(), center = startPt)

                            val endPt = screenPoints.last()
                            drawCircle(color = peakColor.copy(alpha = 0.28f), radius = 8.dp.toPx(), center = endPt)
                            drawCircle(color = peakColor, radius = 4.5.dp.toPx(), center = endPt)
                        }

                        // Live scrubbed cursor marker synced with the profile chart below
                        if (highlightedIndex != null && highlightedIndex in screenPoints.indices) {
                            val focus = screenPoints[highlightedIndex]
                            drawCircle(color = lowColor.copy(alpha = 0.28f), radius = 12.dp.toPx(), center = focus)
                            drawCircle(color = markerRingColor, radius = 7.dp.toPx(), center = focus)
                            drawCircle(color = lowColor, radius = 4.5.dp.toPx(), center = focus)
                        }
                    }
                }

                // Bottom-start overlay legend pill
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(cs.surfaceContainerLow.copy(alpha = 0.90f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (canShowF1Sectors && showF1Sectors && activeF1Lap != null) {
                        if (f1SectorAnalysis?.isMultiLap == true) {
                            Text(
                                text = "L${activeF1Lap.lapNumber}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                ),
                                color = cs.onSurface
                            )
                        }
                        MapLegendDot(label = stringResource(R.string.kart_f1_legend_purple), color = F1PurpleColor)
                        MapLegendDot(label = stringResource(R.string.kart_f1_legend_green), color = F1GreenColor)
                        MapLegendDot(label = stringResource(R.string.kart_f1_legend_yellow), color = F1YellowColor)
                    } else {
                        MapLegendDot(label = "起点", color = midColor)
                        MapLegendDot(label = "终点", color = peakColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun MapModePill(
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) cs.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 11.sp
            ),
            color = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant
        )
    }
}

@Composable
private fun MapLegendDot(
    label: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private data class StackedBandSpec(
    val title: String,
    val unit: String,
    val color: Color,
    val values: List<Float?>,
    val formatValue: (Float) -> String
)

/**
 * Synchronized X-axis 3-chart vertically stacked telemetry profile used across ALL modes:
 * - Hiking & Off-road Crossing: Elevation -> Speed -> Heart Rate
 * - Karting: Speed -> Total G-Force -> Heart Rate (or Lateral G / Elevation fallback)
 *
 * Finger press or horizontal drag draws a unified vertical crosshair across all 3 stacked bands
 * and updates the synced marker on the vector track map above.
 */
@Composable
fun TelemetryProfileChartCard(
    points: List<TrackPointEntity>,
    mode: ActivityMode,
    speedUnit: SpeedUnit,
    selectedPointIndex: Int? = null,
    onSelectPointIndex: (Int?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val tc = LocalTelemetryColors.current
    val isKart = mode == ActivityMode.KARTING

    val hasAltitude = remember(points) { points.count { it.altitudeMeters != null } >= 2 }
    val hasHeartRate = remember(points) { points.count { it.heartRateBpm != null } >= 2 }
    val hasGForce = remember(points) { points.any { it.totalG >= 0.05f } }
    val hasLateralG = remember(points) { points.any { abs(it.lateralG) >= 0.05f } }

    val elevationLabel = stringResource(R.string.detail_chart_elevation)
    val speedLabel = stringResource(R.string.detail_chart_speed)
    val hrLabel = stringResource(R.string.detail_chart_hr)
    val totalGLabel = stringResource(R.string.detail_chart_total_g)
    val latGLabel = stringResource(R.string.detail_chart_lat_g)
    val lonGLabel = stringResource(R.string.detail_chart_lon_g)

    val bands = remember(
        points,
        mode,
        speedUnit,
        hasAltitude,
        hasHeartRate,
        hasGForce,
        hasLateralG,
        cs.secondary,
        cs.primary,
        cs.tertiary,
        tc.heart
    ) {
        buildList {
            if (isKart) {
                // Band 1: Speed
                add(
                    StackedBandSpec(
                        title = speedLabel,
                        unit = speedUnit.label,
                        color = cs.primary,
                        values = points.map { it.measuredSpeedMps?.let(speedUnit::convertFromMps) },
                        formatValue = { v -> String.format(Locale.US, "%.1f %s", v, speedUnit.label) }
                    )
                )
                // Band 2: Total G-Force (fallback to Elevation only if no G-force sensor samples exist)
                if (hasGForce || !hasAltitude) {
                    add(
                        StackedBandSpec(
                            title = totalGLabel,
                            unit = "G",
                            color = cs.tertiary,
                            values = points.map { it.totalG },
                            formatValue = { v -> String.format(Locale.US, "%.2f G", v) }
                        )
                    )
                } else {
                    add(
                        StackedBandSpec(
                            title = elevationLabel,
                            unit = "m",
                            color = cs.secondary,
                            values = points.map { it.altitudeMeters?.toFloat() },
                            formatValue = { v -> String.format(Locale.US, "%.1f m", v) }
                        )
                    )
                }
                // Band 3: Heart Rate -> Lateral G -> Elevation -> Longitudinal G (always guarantees 3 charts)
                when {
                    hasHeartRate -> add(
                        StackedBandSpec(
                            title = hrLabel,
                            unit = "bpm",
                            color = tc.heart,
                            values = points.map { it.heartRateBpm?.toFloat() },
                            formatValue = { v -> "${v.toInt()} bpm" }
                        )
                    )
                    hasLateralG -> add(
                        StackedBandSpec(
                            title = latGLabel,
                            unit = "G",
                            color = cs.secondary,
                            values = points.map { abs(it.lateralG) },
                            formatValue = { v -> String.format(Locale.US, "%.2f G", v) }
                        )
                    )
                    hasAltitude -> add(
                        StackedBandSpec(
                            title = elevationLabel,
                            unit = "m",
                            color = cs.secondary,
                            values = points.map { it.altitudeMeters?.toFloat() },
                            formatValue = { v -> String.format(Locale.US, "%.1f m", v) }
                        )
                    )
                    else -> add(
                        StackedBandSpec(
                            title = lonGLabel,
                            unit = "G",
                            color = cs.secondary,
                            values = points.map { it.longitudinalG },
                            formatValue = { v -> String.format(Locale.US, "%.2f G", v) }
                        )
                    )
                }
            } else {
                if (hasAltitude) {
                    add(
                        StackedBandSpec(
                            title = elevationLabel,
                            unit = "m",
                            color = cs.secondary,
                            values = points.map { it.altitudeMeters?.toFloat() },
                            formatValue = { v -> String.format(Locale.US, "%.1f m", v) }
                        )
                    )
                }
                add(
                    StackedBandSpec(
                        title = speedLabel,
                        unit = speedUnit.label,
                        color = cs.primary,
                        values = points.map { it.measuredSpeedMps?.let(speedUnit::convertFromMps) },
                        formatValue = { v -> String.format(Locale.US, "%.1f %s", v, speedUnit.label) }
                    )
                )
                if (hasHeartRate) {
                    add(
                        StackedBandSpec(
                            title = hrLabel,
                            unit = "bpm",
                            color = tc.heart,
                            values = points.map { it.heartRateBpm?.toFloat() },
                            formatValue = { v -> "${v.toInt()} bpm" }
                        )
                    )
                }
            }
        }
    }

    val activeElapsedMs = remember(points) { computeActiveElapsedMs(points) }
    val totalDurationMs = activeElapsedMs.lastOrNull() ?: 0L
    val selectedPoint = selectedPointIndex?.let { points.getOrNull(it) }
    val selectedFraction = selectedPointIndex?.let { idx ->
        if (points.size <= 1) 0f
        else if (totalDurationMs <= 0L) idx.coerceIn(0, points.lastIndex).toFloat() / points.lastIndex.toFloat()
        else (activeElapsedMs[idx.coerceIn(0, points.lastIndex)].toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(if (isKart) "karting_stacked_profile_card" else "hiking_stacked_profile_card"),
        shape = RoundedCornerShape(24.dp),
        color = cs.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.detail_chart_stacked_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = cs.onSurface
                )
                if (selectedPoint != null) {
                    Surface(
                        onClick = { onSelectPointIndex(null) },
                        shape = RoundedCornerShape(50),
                        color = cs.surfaceContainerHigh,
                        modifier = Modifier.testTag("btn_clear_chart_scrub")
                    ) {
                        Text(
                            text = stringResource(R.string.detail_chart_clear_scrub),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = cs.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Live Synchronized Inspector Strip
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("chart_scrub_readout"),
                shape = RoundedCornerShape(16.dp),
                color = if (selectedPoint != null) cs.primaryContainer.copy(alpha = 0.55f) else cs.surfaceContainer
            ) {
                if (selectedPoint == null) {
                    Text(
                        text = stringResource(
                            if (isKart) R.string.detail_chart_scrub_hint_karting
                            else R.string.detail_chart_scrub_hint
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                } else {
                    val elapsedMs = selectedPointIndex?.let { activeElapsedMs.getOrNull(it) } ?: 0L
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ScrubMetricChip(
                            label = stringResource(R.string.detail_chart_time),
                            value = TrackFormatters.formatDuration(elapsedMs),
                            accent = cs.onSurface
                        )
                        if (isKart) {
                            ScrubMetricChip(
                                label = speedLabel,
                                value = TrackFormatters.formatSpeed(selectedPoint.measuredSpeedMps, speedUnit),
                                accent = cs.primary
                            )
                            ScrubMetricChip(
                                label = totalGLabel,
                                value = String.format(Locale.US, "%.2f G", selectedPoint.totalG),
                                accent = cs.tertiary
                            )
                            if (hasHeartRate) {
                                ScrubMetricChip(
                                    label = hrLabel,
                                    value = selectedPoint.heartRateBpm?.let { "$it bpm" } ?: "--",
                                    accent = tc.heart
                                )
                            } else {
                                ScrubMetricChip(
                                    label = latGLabel,
                                    value = String.format(Locale.US, "%.2f G", abs(selectedPoint.lateralG)),
                                    accent = cs.secondary
                                )
                            }
                        } else {
                            if (hasAltitude) {
                                ScrubMetricChip(
                                    label = elevationLabel,
                                    value = selectedPoint.altitudeMeters?.let {
                                        String.format(Locale.US, "%.1f m", it)
                                    } ?: "--",
                                    accent = cs.secondary
                                )
                            }
                            ScrubMetricChip(
                                label = speedLabel,
                                value = TrackFormatters.formatSpeed(selectedPoint.measuredSpeedMps, speedUnit),
                                accent = cs.primary
                            )
                            if (hasHeartRate) {
                                ScrubMetricChip(
                                    label = hrLabel,
                                    value = selectedPoint.heartRateBpm?.let { "$it bpm" } ?: "--",
                                    accent = tc.heart
                                )
                            }
                        }
                    }
                }
            }

            if (points.size < 2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(cs.surfaceContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "--", style = MaterialTheme.typography.bodyMedium, color = cs.outline)
                }
            } else {
                val crosshairColor = cs.onSurface.copy(alpha = 0.72f)
                val gridColor = cs.outlineVariant.copy(alpha = 0.6f)
                val surfaceDotRing = cs.surface

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(cs.surfaceContainer)
                        .pointerInput(points) {
                            detectTapGestures(
                                onPress = { offset ->
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    val frac = (offset.x / w).coerceIn(0f, 1f)
                                    onSelectPointIndex(findNearestPointIndexByTimeFraction(points, frac))
                                }
                            )
                        }
                        .pointerInput(points) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    val frac = (offset.x / w).coerceIn(0f, 1f)
                                    onSelectPointIndex(findNearestPointIndexByTimeFraction(points, frac))
                                },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    val frac = (change.position.x / w).coerceIn(0f, 1f)
                                    onSelectPointIndex(findNearestPointIndexByTimeFraction(points, frac))
                                }
                            )
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag(if (isKart) "karting_stacked_chart_canvas_group" else "hiking_stacked_chart_canvas_group"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    bands.forEachIndexed { bandIdx, band ->
                        val nonNullValues = band.values.filterNotNull()
                        val minVal = nonNullValues.minOrNull() ?: 0f
                        val maxVal = nonNullValues.maxOrNull() ?: 1f
                        val range = (maxVal - minVal).coerceAtLeast(0.1f)

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(band.color)
                                    )
                                    Text(
                                        text = "${band.title} (${band.unit})",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = cs.onSurface
                                    )
                                }
                                val currentBandVal = selectedPointIndex?.let { band.values.getOrNull(it) }
                                Text(
                                    text = if (currentBandVal != null) {
                                        band.formatValue(currentBandVal)
                                    } else if (selectedPointIndex != null || nonNullValues.isEmpty()) {
                                        "--"
                                    } else {
                                        "${band.formatValue(minVal)} – ${band.formatValue(maxVal)}"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = if (currentBandVal != null) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = 11.sp
                                    ),
                                    color = if (currentBandVal != null) band.color else cs.onSurfaceVariant
                                )
                            }

                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(78.dp)
                            ) {
                                val dash = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                                drawLine(gridColor, Offset(0f, 0f), Offset(size.width, 0f), 1f, pathEffect = dash)
                                drawLine(
                                    gridColor,
                                    Offset(0f, size.height / 2f),
                                    Offset(size.width, size.height / 2f),
                                    1f,
                                    pathEffect = dash
                                )
                                drawLine(
                                    gridColor,
                                    Offset(0f, size.height),
                                    Offset(size.width, size.height),
                                    1f,
                                    pathEffect = dash
                                )

                                val linePath = Path()
                                val fillPath = Path()
                                var started = false
                                var lastX = 0f
                                val padY = 5.dp.toPx()
                                val plotH = (size.height - padY * 2f).coerceAtLeast(1f)

                                fun closeFillSegment() {
                                    if (!started) return
                                    fillPath.lineTo(lastX, size.height)
                                    fillPath.close()
                                    started = false
                                }
                                for (i in points.indices) {
                                    if (points[i].timingBreakBefore == true) closeFillSegment()
                                    val v = band.values[i]
                                    if (v == null) {
                                        closeFillSegment()
                                        continue
                                    }
                                    val fracX = if (totalDurationMs > 0L) {
                                        (activeElapsedMs[i].toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                                    } else {
                                        i.toFloat() / points.lastIndex.coerceAtLeast(1).toFloat()
                                    }
                                    val x = size.width * fracX
                                    val normY = ((v - minVal) / range).coerceIn(0f, 1f)
                                    val y = size.height - padY - normY * plotH
                                    if (!started) {
                                        linePath.moveTo(x, y)
                                        fillPath.moveTo(x, size.height)
                                        fillPath.lineTo(x, y)
                                        started = true
                                    } else {
                                        linePath.lineTo(x, y)
                                        fillPath.lineTo(x, y)
                                    }
                                    lastX = x
                                }
                                closeFillSegment()
                                if (nonNullValues.isNotEmpty()) {
                                    drawPath(
                                        path = fillPath,
                                        brush = Brush.verticalGradient(
                                            colors = listOf(band.color.copy(alpha = 0.25f), Color.Transparent)
                                        )
                                    )
                                    drawPath(
                                        path = linePath,
                                        color = band.color,
                                        style = Stroke(
                                            width = 2.2.dp.toPx(),
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }

                                // Aligned vertical crosshair line & glowing value marker
                                if (selectedFraction != null && selectedPointIndex != null) {
                                    val cx = size.width * selectedFraction
                                    drawLine(
                                        color = crosshairColor,
                                        start = Offset(cx, 0f),
                                        end = Offset(cx, size.height),
                                        strokeWidth = 1.4.dp.toPx()
                                    )
                                    val v = band.values.getOrNull(selectedPointIndex)
                                    if (v != null) {
                                        val normY = ((v - minVal) / range).coerceIn(0f, 1f)
                                        val cy = size.height - padY - normY * plotH
                                        drawCircle(
                                            color = band.color.copy(alpha = 0.28f),
                                            radius = 9.dp.toPx(),
                                            center = Offset(cx, cy)
                                        )
                                        drawCircle(
                                            color = surfaceDotRing,
                                            radius = 5.5.dp.toPx(),
                                            center = Offset(cx, cy)
                                        )
                                        drawCircle(
                                            color = band.color,
                                            radius = 3.8.dp.toPx(),
                                            center = Offset(cx, cy)
                                        )
                                    }
                                }
                            }
                        }

                        if (bandIdx < bands.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(cs.outlineVariant.copy(alpha = 0.35f))
                            )
                        }
                    }

                    // Shared X-Axis time labels aligned across all stacked panels
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "00:00",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 10.sp
                            ),
                            color = cs.onSurfaceVariant
                        )
                        Text(
                            text = TrackFormatters.formatDuration(totalDurationMs / 2L),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 10.sp
                            ),
                            color = cs.onSurfaceVariant
                        )
                        Text(
                            text = TrackFormatters.formatDuration(totalDurationMs),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 10.sp
                            ),
                            color = cs.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScrubMetricChip(
    label: String,
    value: String,
    accent: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            ),
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
