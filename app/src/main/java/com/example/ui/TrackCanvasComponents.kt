package com.example.ui

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.gps.SpeedUnit
import com.example.track.ActivityMode
import com.example.track.TrackFormatters
import com.example.track.TrackPointEntity
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalTelemetryColors
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos

/**
 * Pure helper mapping a normalized horizontal time fraction [0f..1f] to the nearest track point index
 * by timestamp so stacked profile charts share an exact X-axis alignment.
 */
fun findNearestPointIndexByTimeFraction(
    points: List<TrackPointEntity>,
    fraction: Float
): Int {
    if (points.size <= 1) return 0
    val clamped = fraction.coerceIn(0f, 1f)
    val startMs = points.first().timestampMs
    val endMs = points.last().timestampMs
    val spanMs = (endMs - startMs)
    if (spanMs <= 0L) {
        return ((points.lastIndex) * clamped).toInt().coerceIn(0, points.lastIndex)
    }
    val targetMs = startMs + (spanMs * clamped).toLong()
    var bestIdx = 0
    var bestDiff = Long.MAX_VALUE
    for (i in points.indices) {
        val diff = abs(points[i].timestampMs - targetMs)
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
    val startMs = points.first().timestampMs
    val spanMs = (points.last().timestampMs - startMs)
    return if (spanMs <= 0L) {
        clampedIdx.toFloat() / points.lastIndex.toFloat()
    } else {
        ((points[clampedIdx].timestampMs - startMs).toFloat() / spanMs.toFloat()).coerceIn(0f, 1f)
    }
}

@Composable
fun VectorTrackCanvasCard(
    points: List<TrackPointEntity>,
    mode: ActivityMode,
    speedUnit: SpeedUnit,
    highlightedIndex: Int? = null,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val lowColor = cs.primary
    val midColor = cs.secondary
    val highColor = cs.tertiary
    val peakColor = cs.error
    val markerRingColor = cs.surface

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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
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

                        val pad = 22.dp.toPx()
                        val drawW = (size.width - pad * 2f).coerceAtLeast(1f)
                        val drawH = (size.height - pad * 2f).coerceAtLeast(1f)
                        val scale = minOf(drawW / spanX.toFloat(), drawH / spanY.toFloat())
                        val offsetX = pad + (drawW - spanX.toFloat() * scale) / 2f
                        val offsetY = pad + (drawH - spanY.toFloat() * scale) / 2f

                        val screenPoints = points.indices.map { i ->
                            val sx = offsetX + ((xs[i] - minX).toFloat() * scale)
                            val sy = size.height - (offsetY + ((ys[i] - minY).toFloat() * scale))
                            Offset(sx, sy)
                        }

                        val metrics: List<Double> = if (mode == ActivityMode.KARTING) {
                            points.map { speedUnit.convertFromMps(it.speedMps).toDouble() }
                        } else {
                            points.map { it.altitudeMeters ?: 0.0 }
                        }
                        val minMetric = metrics.minOrNull() ?: 0.0
                        val maxMetric = metrics.maxOrNull() ?: 1.0
                        val metricSpan = (maxMetric - minMetric).coerceAtLeast(1.0)

                        val strokePx = 3.5.dp.toPx()
                        for (i in 0 until screenPoints.lastIndex) {
                            val ratio = (((metrics[i] + metrics[i + 1]) / 2.0 - minMetric) / metricSpan)
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

                        val startPt = screenPoints.first()
                        drawCircle(color = midColor.copy(alpha = 0.28f), radius = 9.dp.toPx(), center = startPt)
                        drawCircle(color = midColor, radius = 5.dp.toPx(), center = startPt)

                        val endPt = screenPoints.last()
                        drawCircle(color = peakColor.copy(alpha = 0.28f), radius = 8.dp.toPx(), center = endPt)
                        drawCircle(color = peakColor, radius = 4.5.dp.toPx(), center = endPt)

                        // Live scrubbed cursor marker synced with the profile chart below
                        if (highlightedIndex != null && highlightedIndex in screenPoints.indices) {
                            val focus = screenPoints[highlightedIndex]
                            drawCircle(color = lowColor.copy(alpha = 0.28f), radius = 12.dp.toPx(), center = focus)
                            drawCircle(color = markerRingColor, radius = 7.dp.toPx(), center = focus)
                            drawCircle(color = lowColor, radius = 4.5.dp.toPx(), center = focus)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(cs.surfaceContainerLow.copy(alpha = 0.88f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(midColor)
                        )
                        Text(
                            text = "起点",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = cs.onSurfaceVariant
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(peakColor)
                        )
                        Text(
                            text = "终点",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = cs.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private enum class ProfileMetric {
    ELEVATION,
    SPEED,
    HEART_RATE
}

private data class StackedBandSpec(
    val title: String,
    val unit: String,
    val color: Color,
    val values: List<Float?>,
    val formatValue: (Float) -> String
)

@Composable
fun TelemetryProfileChartCard(
    points: List<TrackPointEntity>,
    mode: ActivityMode,
    speedUnit: SpeedUnit,
    selectedPointIndex: Int? = null,
    onSelectPointIndex: (Int?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (mode.isElevationTrail) {
        HikingStackedProfileCard(
            points = points,
            speedUnit = speedUnit,
            selectedPointIndex = selectedPointIndex,
            onSelectPointIndex = onSelectPointIndex,
            modifier = modifier
        )
    } else {
        SingleInteractiveProfileCard(
            points = points,
            mode = mode,
            speedUnit = speedUnit,
            selectedPointIndex = selectedPointIndex,
            onSelectPointIndex = onSelectPointIndex,
            modifier = modifier
        )
    }
}

/**
 * Synchronized X-axis stacked telemetry bands for Hiking:
 * Elevation -> Speed -> Heart Rate laid out vertically from top to bottom (never overlapping),
 * with finger press/drag scrubbing that draws a unified vertical crosshair across all bands
 * and shows exact point-in-time metrics.
 */
@Composable
private fun HikingStackedProfileCard(
    points: List<TrackPointEntity>,
    speedUnit: SpeedUnit,
    selectedPointIndex: Int?,
    onSelectPointIndex: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val tc = LocalTelemetryColors.current

    val hasAltitude = remember(points) { points.count { it.altitudeMeters != null } >= 2 }
    val hasHeartRate = remember(points) { points.count { it.heartRateBpm != null } >= 2 }

    val elevationLabel = stringResource(R.string.detail_chart_elevation)
    val speedLabel = stringResource(R.string.detail_chart_speed)
    val hrLabel = stringResource(R.string.detail_chart_hr)

    val bands = remember(points, speedUnit, hasAltitude, hasHeartRate, cs.secondary, cs.primary, tc.heart) {
        buildList {
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
                    values = points.map { speedUnit.convertFromMps(it.speedMps) },
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

    val startMs = points.firstOrNull()?.timestampMs ?: 0L
    val endMs = points.lastOrNull()?.timestampMs ?: 0L
    val totalDurationMs = (endMs - startMs).coerceAtLeast(0L)
    val selectedPoint = selectedPointIndex?.let { points.getOrNull(it) }
    val selectedFraction = selectedPointIndex?.let { normalizedTimeFraction(points, it) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("hiking_stacked_profile_card"),
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

            // Live Synchronized Inspector Strip (always reserves clean height so scrubbing never jumps layout)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("chart_scrub_readout"),
                shape = RoundedCornerShape(16.dp),
                color = if (selectedPoint != null) cs.primaryContainer.copy(alpha = 0.55f) else cs.surfaceContainer
            ) {
                if (selectedPoint == null) {
                    Text(
                        text = stringResource(R.string.detail_chart_scrub_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                } else {
                    val elapsedMs = (selectedPoint.timestampMs - startMs).coerceAtLeast(0L)
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
                            value = TrackFormatters.formatSpeed(selectedPoint.speedMps, speedUnit),
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
                // Unified touch-capture container so finger press/drag anywhere smoothly scrubs all stacked bands
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
                        .testTag("hiking_stacked_chart_canvas_group"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    bands.forEachIndexed { bandIdx, band ->
                        val nonNullValues = band.values.filterNotNull()
                        val minVal = nonNullValues.minOrNull() ?: 0f
                        val maxVal = nonNullValues.maxOrNull() ?: 1f
                        val range = (maxVal - minVal).coerceAtLeast(1f)

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

                                for (i in points.indices) {
                                    val v = band.values[i] ?: continue
                                    val fracX = normalizedTimeFraction(points, i)
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
                                if (started) {
                                    fillPath.lineTo(lastX, size.height)
                                    fillPath.close()
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

@Composable
private fun SingleInteractiveProfileCard(
    points: List<TrackPointEntity>,
    mode: ActivityMode,
    speedUnit: SpeedUnit,
    selectedPointIndex: Int?,
    onSelectPointIndex: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val tc = LocalTelemetryColors.current

    val hasAltitude = remember(points) { points.any { it.altitudeMeters != null } }
    val hasHeartRate = remember(points) { points.any { it.heartRateBpm != null } }

    var selectedMetric by remember(mode, hasAltitude) {
        mutableStateOf(
            when {
                mode.isElevationTrail && hasAltitude -> ProfileMetric.ELEVATION
                else -> ProfileMetric.SPEED
            }
        )
    }

    val valuesByPoint: List<Float?> = remember(points, selectedMetric, speedUnit) {
        when (selectedMetric) {
            ProfileMetric.ELEVATION -> points.map { it.altitudeMeters?.toFloat() }
            ProfileMetric.SPEED -> points.map { speedUnit.convertFromMps(it.speedMps) }
            ProfileMetric.HEART_RATE -> points.map { it.heartRateBpm?.toFloat() }
        }
    }
    val nonNullSeries = remember(valuesByPoint) { valuesByPoint.filterNotNull() }

    val (lineColor, unitLabel) = when (selectedMetric) {
        ProfileMetric.ELEVATION -> cs.secondary to "m"
        ProfileMetric.SPEED -> cs.primary to speedUnit.label
        ProfileMetric.HEART_RATE -> tc.heart to "bpm"
    }

    val startMs = points.firstOrNull()?.timestampMs ?: 0L
    val selectedPoint = selectedPointIndex?.let { points.getOrNull(it) }
    val selectedFraction = selectedPointIndex?.let { normalizedTimeFraction(points, it) }

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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.detail_chart_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = cs.onSurface
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(cs.surfaceContainer)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (hasAltitude) {
                        ChartTabChip(
                            label = stringResource(R.string.detail_chart_elevation),
                            selected = selectedMetric == ProfileMetric.ELEVATION,
                            onClick = { selectedMetric = ProfileMetric.ELEVATION }
                        )
                    }
                    ChartTabChip(
                        label = stringResource(R.string.detail_chart_speed),
                        selected = selectedMetric == ProfileMetric.SPEED,
                        onClick = { selectedMetric = ProfileMetric.SPEED }
                    )
                    if (hasHeartRate) {
                        ChartTabChip(
                            label = stringResource(R.string.detail_chart_hr),
                            selected = selectedMetric == ProfileMetric.HEART_RATE,
                            onClick = { selectedMetric = ProfileMetric.HEART_RATE }
                        )
                    }
                }
            }

            if (selectedPoint != null) {
                val elapsedMs = (selectedPoint.timestampMs - startMs).coerceAtLeast(0L)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = cs.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ScrubMetricChip(
                            label = stringResource(R.string.detail_chart_time),
                            value = TrackFormatters.formatDuration(elapsedMs),
                            accent = cs.onSurface
                        )
                        ScrubMetricChip(
                            label = stringResource(R.string.detail_chart_speed),
                            value = TrackFormatters.formatSpeed(selectedPoint.speedMps, speedUnit),
                            accent = cs.primary
                        )
                        if (hasHeartRate) {
                            ScrubMetricChip(
                                label = stringResource(R.string.detail_chart_hr),
                                value = selectedPoint.heartRateBpm?.let { "$it bpm" } ?: "--",
                                accent = tc.heart
                            )
                        }
                        ScrubMetricChip(
                            label = "G 力",
                            value = String.format(Locale.US, "%.2fG", selectedPoint.totalG),
                            accent = cs.tertiary
                        )
                    }
                }
            }

            if (nonNullSeries.size < 2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(cs.surfaceContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "--",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.outline
                    )
                }
            } else {
                val minVal = nonNullSeries.min()
                val maxVal = nonNullSeries.max()
                val range = (maxVal - minVal).coerceAtLeast(1f)
                val gridColor = cs.outlineVariant.copy(alpha = 0.65f)
                val crosshairColor = cs.onSurface.copy(alpha = 0.7f)
                val surfaceDotRing = cs.surface

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(145.dp)
                        .clip(RoundedCornerShape(16.dp))
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
                        .padding(12.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
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

                        for (i in points.indices) {
                            val value = valuesByPoint[i] ?: continue
                            val x = size.width * normalizedTimeFraction(points, i)
                            val normalizedY = ((value - minVal) / range).coerceIn(0f, 1f)
                            val y = size.height - (normalizedY * (size.height - 8.dp.toPx()) + 4.dp.toPx())
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
                        if (started) {
                            fillPath.lineTo(lastX, size.height)
                            fillPath.close()
                            drawPath(
                                path = fillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        lineColor.copy(alpha = 0.26f),
                                        Color.Transparent
                                    )
                                )
                            )
                            drawPath(
                                path = linePath,
                                color = lineColor,
                                style = Stroke(
                                    width = 2.4.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }

                        if (selectedFraction != null && selectedPointIndex != null) {
                            val cx = size.width * selectedFraction
                            drawLine(
                                color = crosshairColor,
                                start = Offset(cx, 0f),
                                end = Offset(cx, size.height),
                                strokeWidth = 1.4.dp.toPx()
                            )
                            val v = valuesByPoint.getOrNull(selectedPointIndex)
                            if (v != null) {
                                val normalizedY = ((v - minVal) / range).coerceIn(0f, 1f)
                                val cy = size.height - (normalizedY * (size.height - 8.dp.toPx()) + 4.dp.toPx())
                                drawCircle(lineColor.copy(alpha = 0.28f), 9.dp.toPx(), Offset(cx, cy))
                                drawCircle(surfaceDotRing, 5.5.dp.toPx(), Offset(cx, cy))
                                drawCircle(lineColor, 3.8.dp.toPx(), Offset(cx, cy))
                            }
                        }
                    }

                    Text(
                        text = String.format(Locale.US, "%.1f %s", maxVal, unitLabel),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp
                        ),
                        color = lineColor,
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f %s", minVal, unitLabel),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp
                        ),
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartTabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) cs.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant
        )
    }
}
