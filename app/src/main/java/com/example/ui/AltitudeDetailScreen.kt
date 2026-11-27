package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.gps.AltitudeSample
import com.example.gps.AltitudeTelemetry
import com.example.gps.GpsData
import com.example.sensor.EnvironmentTelemetry
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.util.Locale

private const val METERS_TO_FEET = 3.28084

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AltitudeDetailScreen(
    gpsData: GpsData,
    altitudeTelemetry: AltitudeTelemetry,
    envTelemetry: EnvironmentTelemetry,
    onBack: () -> Unit,
    onResetAltitudeSession: () -> Unit,
    onCalibrateBarometer: () -> Unit,
    onResetBarometerQnh: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val cs = MaterialTheme.colorScheme
    var useFeet by rememberSaveable { mutableStateOf(false) }

    val unitSuffix = if (useFeet) "ft" else "m"
    val factor = if (useFeet) METERS_TO_FEET else 1.0
    val currentAltM = altitudeTelemetry.currentAltitudeM ?: gpsData.altitudeMeters

    fun fmtAlt(meters: Double?): String {
        if (meters == null) return "--"
        return String.format(Locale.US, "%.1f", meters * factor)
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("altitude_detail_screen"),
        containerColor = cs.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.alt_detail_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("btn_back_from_altitude")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.detail_back_dashboard_cd)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = cs.background,
                    titleContentColor = cs.onSurface,
                    navigationIconContentColor = cs.onSurface
                )
            )
        }
    ) { innerPadding ->
        val heroAltitudeCard: @Composable (Boolean) -> Unit = { isLandscape ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = cs.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = if (isLandscape) 20.dp else 22.dp,
                            vertical = if (isLandscape) 16.dp else 20.dp
                        ),
                    verticalArrangement = Arrangement.spacedBy(if (isLandscape) 12.dp else 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Terrain,
                                contentDescription = null,
                                tint = cs.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.telemetry_altitude),
                                style = MaterialTheme.typography.titleSmall,
                                color = cs.onSurfaceVariant
                            )
                        }

                        // m / ft pill toggle
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(cs.surfaceContainer)
                                .padding(3.dp)
                        ) {
                            listOf(false to "m", true to "ft").forEach { (ft, label) ->
                                val selected = useFeet == ft
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(if (selected) cs.primaryContainer else Color.Transparent)
                                        .clickable { useFeet = ft }
                                        .padding(horizontal = 12.dp, vertical = 5.dp)
                                        .testTag("alt_unit_$label"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                        ),
                                        color = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = fmtAlt(currentAltM),
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = if (isLandscape) 66.sp else 62.sp,
                                lineHeight = if (isLandscape) 70.sp else 66.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-2).sp
                            ),
                            color = if (currentAltM != null) cs.onSurface else cs.outline
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = unitSuffix,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Medium
                            ),
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }

                    // Sub-metrics row: vertical speed, grade, vertical accuracy
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(cs.surfaceContainer)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val vSpeed = altitudeTelemetry.verticalSpeedMps
                        val vSpeedStr = if (currentAltM == null) {
                            "--"
                        } else {
                            val sign = if (vSpeed > 0f) "+" else ""
                            String.format(Locale.US, "%s%.1f m/s", sign, vSpeed)
                        }
                        AltInlineMetric(
                            label = stringResource(R.string.alt_vertical_speed),
                            value = vSpeedStr,
                            valueColor = when {
                                vSpeed > 0.2f -> cs.secondary
                                vSpeed < -0.2f -> cs.tertiary
                                else -> cs.onSurface
                            }
                        )

                        val gradeStr = altitudeTelemetry.gradePercent?.let {
                            String.format(Locale.US, "%+.1f%%", it)
                        } ?: "--"
                        AltInlineMetric(
                            label = stringResource(R.string.alt_grade_label),
                            value = gradeStr,
                            valueColor = cs.onSurface
                        )

                        val vAccStr = altitudeTelemetry.verticalAccuracyM?.let {
                            String.format(Locale.US, "±%.1f m", it)
                        } ?: "--"
                        AltInlineMetric(
                            label = stringResource(R.string.alt_vertical_accuracy),
                            value = vAccStr,
                            valueColor = cs.onSurface
                        )
                    }
                }
            }
        }

        val elevationCurveCard: @Composable (Dp) -> Unit = { chartHeight ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = cs.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.alt_chart_title),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = cs.onSurface
                        )
                        TextButton(
                            onClick = onResetAltitudeSession,
                            modifier = Modifier.testTag("btn_reset_altitude_session")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.alt_reset_session),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    LiveAltitudeCurveChart(
                        samples = altitudeTelemetry.samples,
                        factor = factor,
                        unitSuffix = unitSuffix,
                        chartHeight = chartHeight
                    )
                }
            }
        }

        val elevationStatsGrid: @Composable () -> Unit = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AltStatTile(
                        icon = Icons.AutoMirrored.Rounded.TrendingUp,
                        iconBg = cs.secondaryContainer,
                        iconTint = cs.onSecondaryContainer,
                        label = stringResource(R.string.metric_elevation_gain),
                        value = "+${String.format(Locale.US, "%.0f", altitudeTelemetry.elevationGainM * factor)} $unitSuffix",
                        modifier = Modifier.weight(1f)
                    )
                    AltStatTile(
                        icon = Icons.AutoMirrored.Rounded.TrendingDown,
                        iconBg = cs.tertiaryContainer,
                        iconTint = cs.onTertiaryContainer,
                        label = stringResource(R.string.metric_elevation_loss),
                        value = "-${String.format(Locale.US, "%.0f", altitudeTelemetry.elevationLossM * factor)} $unitSuffix",
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AltStatTile(
                        icon = Icons.Rounded.VerticalAlignTop,
                        iconBg = cs.primaryContainer,
                        iconTint = cs.onPrimaryContainer,
                        label = stringResource(R.string.metric_max_altitude),
                        value = altitudeTelemetry.maxAltitudeM?.let { "${fmtAlt(it)} $unitSuffix" } ?: "--",
                        modifier = Modifier.weight(1f)
                    )
                    AltStatTile(
                        icon = Icons.Rounded.VerticalAlignBottom,
                        iconBg = cs.surfaceContainerHigh,
                        iconTint = cs.onSurfaceVariant,
                        label = stringResource(R.string.metric_min_altitude),
                        value = altitudeTelemetry.minAltitudeM?.let { "${fmtAlt(it)} $unitSuffix" } ?: "--",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        val barometerCard: @Composable () -> Unit = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = cs.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(cs.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Compress,
                                contentDescription = null,
                                tint = cs.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.alt_barometer_title),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = cs.onSurface
                        )
                    }

                    if (envTelemetry.hasBarometer) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            AltInlineMetric(
                                label = stringResource(R.string.alt_barometer_pressure),
                                value = envTelemetry.pressureHpa?.let {
                                    String.format(Locale.US, "%.2f hPa", it)
                                } ?: "--",
                                valueColor = cs.primary
                            )
                            AltInlineMetric(
                                label = stringResource(R.string.alt_barometer_altitude),
                                value = envTelemetry.barometricAltitudeM?.let {
                                    "${String.format(Locale.US, "%.1f", it * factor)} $unitSuffix"
                                } ?: "--",
                                valueColor = cs.onSurface
                            )
                        }

                        Text(
                            text = "${stringResource(R.string.alt_barometer_qnh)}: ${String.format(Locale.US, "%.2f hPa", envTelemetry.seaLevelPressureHpa)}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = cs.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilledTonalButton(
                                onClick = onCalibrateBarometer,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("btn_calibrate_barometer"),
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = cs.primaryContainer,
                                    contentColor = cs.onPrimaryContainer
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Height,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.alt_barometer_calibrate_gps),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            FilledTonalButton(
                                onClick = onResetBarometerQnh,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("btn_reset_qnh"),
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = cs.surfaceContainerHigh,
                                    contentColor = cs.onSurfaceVariant
                                )
                            ) {
                                Text(
                                    text = stringResource(R.string.alt_barometer_reset_qnh),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.alt_barometer_unavailable),
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant
                        )
                    }
                }
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .displayCutoutPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            val isLandscape = maxWidth > maxHeight && maxWidth >= 540.dp
            if (isLandscape) {
                val expandedChartHeight = (maxHeight * 0.46f).coerceIn(160.dp, 224.dp)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("altitude_landscape_layout"),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier
                            .weight(0.95f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        heroAltitudeCard(true)
                        elevationStatsGrid()
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Column(
                        modifier = Modifier
                            .weight(1.15f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        elevationCurveCard(expandedChartHeight)
                        barometerCard()
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 640.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    heroAltitudeCard(false)
                    elevationCurveCard(148.dp)
                    elevationStatsGrid()
                    barometerCard()
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun LiveAltitudeCurveChart(
    samples: List<AltitudeSample>,
    factor: Double,
    unitSuffix: String,
    chartHeight: Dp = 148.dp
) {
    val cs = MaterialTheme.colorScheme
    if (samples.size < 2) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .clip(RoundedCornerShape(16.dp))
                .background(cs.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.alt_chart_empty),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        return
    }

    val values = samples.map { (it.altitudeMeters * factor).toFloat() }
    val minV = values.minOrNull() ?: 0f
    val maxV = values.maxOrNull() ?: 1f
    val span = (maxV - minV).coerceAtLeast(4f)
    val lineColor = cs.primary
    val gridColor = cs.outlineVariant.copy(alpha = 0.5f)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .clip(RoundedCornerShape(16.dp))
                .background(cs.surfaceContainer)
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f) return@Canvas

                // Subtle horizontal reference lines
                listOf(0.2f, 0.5f, 0.8f).forEach { frac ->
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, h * frac),
                        end = Offset(w, h * frac),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                val stepX = w / (values.size - 1).coerceAtLeast(1)
                val linePath = Path()
                val fillPath = Path()

                values.forEachIndexed { idx, v ->
                    val x = idx * stepX
                    val norm = ((v - minV) / span).coerceIn(0f, 1f)
                    val y = h - (0.12f + norm * 0.76f) * h
                    if (idx == 0) {
                        linePath.moveTo(x, y)
                        fillPath.moveTo(x, h)
                        fillPath.lineTo(x, y)
                    } else {
                        linePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                    if (idx == values.lastIndex) {
                        fillPath.lineTo(x, h)
                        fillPath.close()
                    }
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            lineColor.copy(alpha = 0.28f),
                            lineColor.copy(alpha = 0.02f)
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

                val lastNorm = ((values.last() - minV) / span).coerceIn(0f, 1f)
                val lastY = h - (0.12f + lastNorm * 0.76f) * h
                drawCircle(color = lineColor.copy(alpha = 0.25f), radius = 7.dp.toPx(), center = Offset(w, lastY))
                drawCircle(color = lineColor, radius = 3.8.dp.toPx(), center = Offset(w, lastY))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "最低 ${String.format(Locale.US, "%.1f %s", minV, unitSuffix)}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                color = cs.onSurfaceVariant
            )
            Text(
                text = "最高 ${String.format(Locale.US, "%.1f %s", maxV, unitSuffix)}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                color = cs.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AltInlineMetric(
    label: String,
    value: String,
    valueColor: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.SemiBold
            ),
            color = valueColor
        )
    }
}

@Composable
private fun AltStatTile(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = cs.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = cs.onSurfaceVariant
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp
                ),
                color = cs.onSurface
            )
        }
    }
}
