package com.example.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SatelliteAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.gps.AccuracyQualityGrade
import com.example.gps.AccuracySample
import com.example.gps.AccuracyTelemetry
import com.example.gps.GnssSatelliteInfo
import com.example.gps.GpsData
import com.example.gps.GpsFixStatus
import com.example.gps.SpeedUnit
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccuracyDetailScreen(
    gpsData: GpsData,
    accuracyTelemetry: AccuracyTelemetry,
    satellites: List<GnssSatelliteInfo>,
    speedUnit: SpeedUnit,
    onBack: () -> Unit,
    onResetAccuracySession: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val cs = MaterialTheme.colorScheme
    val currentAccM = accuracyTelemetry.currentHorizontalAccuracyM ?: gpsData.accuracyMeters
    val grade = accuracyTelemetry.qualityGrade
    val (gradeBg, gradeFg, gradeAccent) = when (grade) {
        AccuracyQualityGrade.EXCELLENT -> Triple(cs.secondaryContainer, cs.onSecondaryContainer, cs.secondary)
        AccuracyQualityGrade.GOOD -> Triple(cs.primaryContainer, cs.onPrimaryContainer, cs.primary)
        AccuracyQualityGrade.FAIR -> Triple(cs.tertiaryContainer, cs.onTertiaryContainer, cs.tertiary)
        AccuracyQualityGrade.POOR -> Triple(cs.errorContainer, cs.onErrorContainer, cs.error)
        AccuracyQualityGrade.WAITING -> Triple(cs.surfaceContainerHigh, cs.onSurfaceVariant, cs.outline)
    }

    val usedSatellites = remember(satellites) { satellites.filter { it.usedInFix } }
    val avgUsedCn0 = remember(usedSatellites) {
        if (usedSatellites.isEmpty()) null else usedSatellites.map { it.cn0DbHz }.average().toFloat()
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("accuracy_detail_screen"),
        containerColor = cs.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.acc_detail_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("btn_back_from_accuracy")
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
        val heroBullseyeCard: @Composable (Boolean) -> Unit = { isLandscape ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = cs.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = if (isLandscape) 18.dp else 20.dp,
                            vertical = if (isLandscape) 16.dp else 18.dp
                        ),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header row + Quality Grade badge
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
                                imageVector = Icons.Rounded.GpsFixed,
                                contentDescription = null,
                                tint = gradeAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.acc_horizontal_label),
                                style = MaterialTheme.typography.titleSmall,
                                color = cs.onSurfaceVariant
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = gradeBg
                        ) {
                            Text(
                                text = grade.labelZh,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = gradeFg,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Primary Readout + CEP Bullseye Visualizer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = if (currentAccM != null && gpsData.hasFix) {
                                        String.format(Locale.US, "±%.1f", currentAccM)
                                    } else {
                                        "--"
                                    },
                                    style = MaterialTheme.typography.displayMedium.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = if (isLandscape) 46.sp else 50.sp,
                                        lineHeight = if (isLandscape) 50.sp else 54.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = (-1.5).sp
                                    ),
                                    color = if (currentAccM != null && gpsData.hasFix) cs.onSurface else cs.outline
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "m",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = cs.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }

                            Text(
                                text = grade.descZh,
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant
                            )
                        }

                        AccuracyBullseyeCanvas(
                            accuracyMeters = if (gpsData.hasFix) currentAccM else null,
                            accentColor = gradeAccent,
                            size = if (isLandscape) 128.dp else 136.dp
                        )
                    }

                    // Inline status strip: Provider | Update Rate | Satellites Used
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(cs.surfaceContainer)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val sourceLabel = when (gpsData.fixStatus) {
                            GpsFixStatus.LOCKED -> stringResource(R.string.acc_source_gnss)
                            GpsFixStatus.FUSED_FALLBACK -> stringResource(R.string.acc_source_fused)
                            else -> "--"
                        }
                        AccInlineMetric(
                            label = stringResource(R.string.acc_source_label),
                            value = sourceLabel,
                            valueColor = if (gpsData.fixStatus == GpsFixStatus.LOCKED) cs.secondary else cs.onSurface
                        )

                        val rateStr = accuracyTelemetry.fixIntervalMs?.let { ms ->
                            val hz = 1000.0 / ms.coerceAtLeast(100L)
                            String.format(Locale.US, "%.1f Hz", hz)
                        } ?: "--"
                        AccInlineMetric(
                            label = stringResource(R.string.acc_update_rate_label),
                            value = rateStr,
                            valueColor = cs.primary
                        )

                        AccInlineMetric(
                            label = stringResource(R.string.telemetry_satellites),
                            value = gpsData.formattedSatellites(),
                            valueColor = cs.onSurface
                        )
                    }
                }
            }
        }

        val accuracyCurveCard: @Composable (Dp) -> Unit = { chartHeight ->
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
                            text = stringResource(R.string.acc_chart_title),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = cs.onSurface
                        )
                        TextButton(
                            onClick = onResetAccuracySession,
                            modifier = Modifier.testTag("btn_reset_accuracy_session")
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

                    LiveAccuracyCurveChart(
                        samples = accuracyTelemetry.samples,
                        accentColor = gradeAccent,
                        chartHeight = chartHeight
                    )
                }
            }
        }

        val precisionBreakdownGrid: @Composable () -> Unit = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AccStatTile(
                        icon = Icons.Rounded.Height,
                        iconBg = cs.primaryContainer,
                        iconTint = cs.onPrimaryContainer,
                        label = stringResource(R.string.alt_vertical_accuracy),
                        value = accuracyTelemetry.verticalAccuracyM?.let {
                            String.format(Locale.US, "±%.1f m", it)
                        } ?: "--",
                        subtitle = stringResource(R.string.acc_vertical_sub),
                        modifier = Modifier.weight(1f)
                    )
                    AccStatTile(
                        icon = Icons.Rounded.Speed,
                        iconBg = cs.secondaryContainer,
                        iconTint = cs.onSecondaryContainer,
                        label = stringResource(R.string.acc_speed_accuracy),
                        value = accuracyTelemetry.speedAccuracyMps?.let { mps ->
                            String.format(Locale.US, "±%.1f %s", speedUnit.convertFromMps(mps), speedUnit.label)
                        } ?: "--",
                        subtitle = stringResource(R.string.acc_speed_sub),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AccStatTile(
                        icon = Icons.Rounded.Explore,
                        iconBg = cs.tertiaryContainer,
                        iconTint = cs.onTertiaryContainer,
                        label = stringResource(R.string.acc_bearing_accuracy),
                        value = accuracyTelemetry.bearingAccuracyDeg?.let {
                            String.format(Locale.US, "±%.1f°", it)
                        } ?: "--",
                        subtitle = stringResource(R.string.acc_bearing_sub),
                        modifier = Modifier.weight(1f)
                    )
                    AccStatTile(
                        icon = Icons.Rounded.Verified,
                        iconBg = cs.surfaceContainerHigh,
                        iconTint = cs.primary,
                        label = stringResource(R.string.acc_avg_stability),
                        value = accuracyTelemetry.avgAccuracyM?.let {
                            String.format(Locale.US, "±%.1f m", it)
                        } ?: "--",
                        subtitle = accuracyTelemetry.stabilityScore?.let { score ->
                            stringResource(R.string.acc_stability_score_format, score)
                        } ?: stringResource(R.string.acc_samples_waiting),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        val diagnosticsInfoCard: @Composable () -> Unit = {
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
                                .background(cs.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SatelliteAlt,
                                contentDescription = null,
                                tint = cs.onSecondaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.acc_diagnostics_title),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = cs.onSurface
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(cs.surfaceContainer)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        AccInlineMetric(
                            label = stringResource(R.string.sat_avg_snr_label),
                            value = avgUsedCn0?.let { String.format(Locale.US, "%.1f dB-Hz", it) } ?: "--",
                            valueColor = cs.primary
                        )
                        AccInlineMetric(
                            label = stringResource(R.string.acc_best_recorded),
                            value = accuracyTelemetry.bestAccuracyM?.let {
                                String.format(Locale.US, "±%.1f m", it)
                            } ?: "--",
                            valueColor = cs.secondary
                        )
                        AccInlineMetric(
                            label = stringResource(R.string.acc_sample_count),
                            value = "${accuracyTelemetry.sampleCount}",
                            valueColor = cs.onSurface
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(15.dp)
                        )
                        Text(
                            text = stringResource(R.string.acc_cep_explanation),
                            style = MaterialTheme.typography.bodySmall,
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
                val expandedChartHeight = (maxHeight * 0.44f).coerceIn(154.dp, 218.dp)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("accuracy_landscape_layout"),
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
                        heroBullseyeCard(true)
                        precisionBreakdownGrid()
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Column(
                        modifier = Modifier
                            .weight(1.15f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        accuracyCurveCard(expandedChartHeight)
                        diagnosticsInfoCard()
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
                    heroBullseyeCard(false)
                    accuracyCurveCard(148.dp)
                    precisionBreakdownGrid()
                    diagnosticsInfoCard()
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun AccuracyBullseyeCanvas(
    accuracyMeters: Float?,
    accentColor: Color,
    size: Dp
) {
    val cs = MaterialTheme.colorScheme
    val maxScaleMeters = when {
        accuracyMeters == null -> 30f
        accuracyMeters <= 15f -> 20f
        accuracyMeters <= 45f -> 50f
        else -> 100f
    }
    val targetFraction = if (accuracyMeters != null) {
        (accuracyMeters / maxScaleMeters).coerceIn(0.08f, 1.0f)
    } else {
        0f
    }
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 220f),
        label = "cep_radius"
    )
    val density = LocalDensity.current
    val labelPaint = remember(density, cs.onSurfaceVariant) {
        Paint().apply {
            isAntiAlias = true
            color = cs.onSurfaceVariant.copy(alpha = 0.75f).toArgb()
            textSize = with(density) { 8.5.sp.toPx() }
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
    }

    val ringColor = cs.outlineVariant.copy(alpha = 0.65f)
    val bgColor = cs.surfaceContainer
    val centerDotRing = cs.surface

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val maxR = (minOf(this.size.width, this.size.height) / 2f) - 8.dp.toPx()
            val dash = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)

            // Crosshair axes
            drawLine(ringColor, Offset(center.x - maxR, center.y), Offset(center.x + maxR, center.y), 1f, pathEffect = dash)
            drawLine(ringColor, Offset(center.x, center.y - maxR), Offset(center.x, center.y + maxR), 1f, pathEffect = dash)

            // Concentric scale rings (33%, 66%, 100%)
            val ringFractions = listOf(0.33f, 0.66f, 1.0f)
            ringFractions.forEach { f ->
                val r = maxR * f
                drawCircle(
                    color = ringColor,
                    radius = r,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // Outer ring scale label (top)
            drawContext.canvas.nativeCanvas.drawText(
                "${maxScaleMeters.toInt()}m",
                center.x,
                center.y - maxR + 9.dp.toPx(),
                labelPaint
            )

            // Real-time 68% CEP uncertainty circle
            if (animatedFraction > 0.01f) {
                val cepRadius = maxR * animatedFraction
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.34f),
                            accentColor.copy(alpha = 0.12f)
                        ),
                        center = center,
                        radius = cepRadius.coerceAtLeast(1f)
                    ),
                    radius = cepRadius,
                    center = center
                )
                drawCircle(
                    color = accentColor,
                    radius = cepRadius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // Center receiver dot
            drawCircle(color = centerDotRing, radius = 5.5.dp.toPx(), center = center)
            drawCircle(color = accentColor, radius = 3.5.dp.toPx(), center = center)
        }
    }
}

@Composable
private fun LiveAccuracyCurveChart(
    samples: List<AccuracySample>,
    accentColor: Color,
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
                text = stringResource(R.string.acc_chart_empty),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        return
    }

    val values = samples.map { it.horizontalAccuracyM }
    val minV = values.minOrNull() ?: 0f
    val maxV = values.maxOrNull() ?: 10f
    val chartTop = maxOf(maxV * 1.15f, 10f)
    val gridColor = cs.outlineVariant.copy(alpha = 0.5f)
    val goodThresholdColor = cs.secondary.copy(alpha = 0.5f)

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

                val dash = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                listOf(0.25f, 0.5f, 0.75f).forEach { frac ->
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, h * frac),
                        end = Offset(w, h * frac),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dash
                    )
                }

                // 5m high-precision threshold reference line
                val fiveMeterY = h - (5f / chartTop).coerceIn(0f, 1f) * (h * 0.85f) - h * 0.08f
                if (fiveMeterY in 0f..h) {
                    drawLine(
                        color = goodThresholdColor,
                        start = Offset(0f, fiveMeterY),
                        end = Offset(w, fiveMeterY),
                        strokeWidth = 1.2.dp.toPx(),
                        pathEffect = dash
                    )
                }

                val stepX = w / (values.size - 1).coerceAtLeast(1)
                val linePath = Path()
                val fillPath = Path()

                values.forEachIndexed { idx, v ->
                    val x = idx * stepX
                    val norm = (v / chartTop).coerceIn(0f, 1f)
                    val y = h - (0.08f + norm * 0.85f) * h
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
                            accentColor.copy(alpha = 0.28f),
                            accentColor.copy(alpha = 0.02f)
                        )
                    )
                )
                drawPath(
                    path = linePath,
                    color = accentColor,
                    style = Stroke(
                        width = 2.4.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                val lastNorm = (values.last() / chartTop).coerceIn(0f, 1f)
                val lastY = h - (0.08f + lastNorm * 0.85f) * h
                drawCircle(color = accentColor.copy(alpha = 0.25f), radius = 7.dp.toPx(), center = Offset(w, lastY))
                drawCircle(color = accentColor, radius = 3.8.dp.toPx(), center = Offset(w, lastY))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "最佳 ±${String.format(Locale.US, "%.1f m", minV)}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                color = cs.secondary
            )
            Text(
                text = "绿色虚线 = 5m 极佳线",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant
            )
            Text(
                text = "峰值 ±${String.format(Locale.US, "%.1f m", maxV)}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                color = cs.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AccInlineMetric(
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
private fun AccStatTile(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    label: String,
    value: String,
    subtitle: String,
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
                .padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    color = cs.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 19.sp
                ),
                color = cs.onSurface,
                maxLines = 1
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
