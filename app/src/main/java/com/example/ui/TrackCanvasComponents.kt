package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.gps.SpeedUnit
import com.example.track.ActivityMode
import com.example.track.TrackPointEntity
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalTelemetryColors
import java.util.Locale
import kotlin.math.cos

@Composable
fun VectorTrackCanvasCard(
    points: List<TrackPointEntity>,
    mode: ActivityMode,
    speedUnit: SpeedUnit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val lowColor = cs.primary
    val midColor = cs.secondary
    val highColor = cs.tertiary
    val peakColor = cs.error

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

@Composable
fun TelemetryProfileChartCard(
    points: List<TrackPointEntity>,
    mode: ActivityMode,
    speedUnit: SpeedUnit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val tc = LocalTelemetryColors.current

    val hasAltitude = remember(points) { points.any { it.altitudeMeters != null } }
    val hasHeartRate = remember(points) { points.any { it.heartRateBpm != null } }

    var selectedMetric by remember(mode, hasAltitude) {
        mutableStateOf(
            when {
                mode == ActivityMode.HIKING && hasAltitude -> ProfileMetric.ELEVATION
                else -> ProfileMetric.SPEED
            }
        )
    }

    val series: List<Float> = remember(points, selectedMetric, speedUnit) {
        when (selectedMetric) {
            ProfileMetric.ELEVATION -> points.mapNotNull { it.altitudeMeters?.toFloat() }
            ProfileMetric.SPEED -> points.map { speedUnit.convertFromMps(it.speedMps) }
            ProfileMetric.HEART_RATE -> points.mapNotNull { it.heartRateBpm?.toFloat() }
        }
    }

    val (lineColor, unitLabel) = when (selectedMetric) {
        ProfileMetric.ELEVATION -> cs.secondary to "m"
        ProfileMetric.SPEED -> cs.primary to speedUnit.label
        ProfileMetric.HEART_RATE -> tc.heart to "bpm"
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

            if (series.size < 2) {
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
                val minVal = series.min()
                val maxVal = series.max()
                val range = (maxVal - minVal).coerceAtLeast(1f)
                val gridColor = cs.outlineVariant.copy(alpha = 0.65f)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(145.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(cs.surfaceContainer)
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

                        val step = (series.size / 140).coerceAtLeast(1)
                        val sampled = series.filterIndexed { index, _ ->
                            index % step == 0 || index == series.lastIndex
                        }

                        val linePath = Path()
                        val fillPath = Path()

                        sampled.forEachIndexed { idx, value ->
                            val x = if (sampled.size == 1) 0f else size.width * idx / (sampled.size - 1)
                            val normalizedY = ((value - minVal) / range).coerceIn(0f, 1f)
                            val y = size.height - (normalizedY * (size.height - 8.dp.toPx()) + 4.dp.toPx())
                            if (idx == 0) {
                                linePath.moveTo(x, y)
                                fillPath.moveTo(x, size.height)
                                fillPath.lineTo(x, y)
                            } else {
                                linePath.lineTo(x, y)
                                fillPath.lineTo(x, y)
                            }
                            if (idx == sampled.lastIndex) {
                                fillPath.lineTo(x, size.height)
                                fillPath.close()
                            }
                        }

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
