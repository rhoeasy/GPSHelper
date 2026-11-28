package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.sensor.GForceData
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.util.Locale
import kotlin.math.hypot

@Composable
fun GForceTelemetryCard(
    gForceData: GForceData,
    showPeakOnDial: Boolean,
    peakHoldSeconds: Int,
    onCalibrateZero: () -> Unit,
    onResetPeak: () -> Unit,
    explanationExpanded: Boolean,
    onExplanationExpandedChange: (Boolean) -> Unit,
    isLandscape: Boolean = false,
    radarSize: Dp = if (isLandscape) 148.dp else 92.dp,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme

    val gAccent = when {
        gForceData.totalG >= 1.0f -> cs.error
        gForceData.totalG >= 0.5f -> cs.tertiary
        else -> cs.primary
    }

    val sensorBadgeText = when {
        !gForceData.isAvailable -> stringResource(R.string.gforce_badge_unavailable)
        gForceData.hasGyroscope -> stringResource(R.string.gforce_badge_gyro_fused)
        else -> stringResource(R.string.gforce_badge_accel_only)
    }

    val holdLabel = when {
        peakHoldSeconds <= 0 -> stringResource(R.string.peak_hold_forever)
        peakHoldSeconds < 60 -> "${peakHoldSeconds}s"
        else -> "${peakHoldSeconds / 60}m"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("gforce_telemetry_card"),
        shape = RoundedCornerShape(if (isLandscape) 28.dp else 24.dp),
        color = cs.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (isLandscape) 18.dp else 18.dp,
                    vertical = if (isLandscape) 16.dp else 18.dp
                ),
            verticalArrangement = if (isLandscape) {
                Arrangement.SpaceBetween
            } else {
                Arrangement.spacedBy(14.dp)
            }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClickLabel = stringResource(R.string.gforce_explanation_title)) { onExplanationExpandedChange(true) }
                        .padding(vertical = 4.dp)
                        .testTag("gforce_explanation_open"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Sensors,
                        contentDescription = null,
                        tint = cs.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.gforce_card_title),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = if (isLandscape) FontWeight.SemiBold else FontWeight.Medium
                        ),
                        color = cs.onSurface,
                        maxLines = 1
                    )
                    Icon(Icons.AutoMirrored.Rounded.HelpOutline, null, Modifier.size(17.dp), tint = cs.onSurfaceVariant)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (gForceData.isLandscape || isLandscape) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = cs.secondaryContainer,
                            modifier = Modifier.testTag("gforce_landscape_badge")
                        ) {
                            Text(
                                text = stringResource(R.string.gforce_badge_landscape),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.5.sp
                                ),
                                color = cs.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (!isLandscape) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (gForceData.hasGyroscope && gForceData.isAvailable) {
                                cs.primaryContainer
                            } else {
                                cs.surfaceContainer
                            }
                        ) {
                            Text(
                                text = sensorBadgeText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp
                                ),
                                color = if (gForceData.hasGyroscope && gForceData.isAvailable) {
                                    cs.onPrimaryContainer
                                } else {
                                    cs.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            if (explanationExpanded) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(cs.surfaceContainer)
                        .padding(12.dp).testTag("gforce_explanation"),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(stringResource(R.string.gforce_explanation_title), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.gforce_explanation_chart), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.gforce_explanation_axes), style = MaterialTheme.typography.bodySmall)
                    Text(
                        if (gForceData.isAvailable) {
                            stringResource(R.string.gforce_explanation_live, gForceData.totalG, gForceData.lateralG, gForceData.longitudinalG)
                        } else {
                            stringResource(R.string.gforce_badge_unavailable)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.primary
                    )
                    TextButton(
                        onClick = { onExplanationExpandedChange(false) },
                        modifier = Modifier.align(Alignment.End).testTag("gforce_explanation_close")
                    ) { Text(stringResource(R.string.gforce_explanation_close)) }
                }
            }

            if (isLandscape) {
                // Maximized Landscape Cockpit Layout: Enlarged G-Force Radar + High-Contrast Metrics & Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ComposeGForceRadar(
                        gForceData = gForceData,
                        dotColor = gAccent,
                        showPeakOnDial = showPeakOnDial,
                        showScaleLabels = true,
                        modifier = Modifier
                            .size(radarSize)
                            .clickable(onClickLabel = stringResource(R.string.gforce_explanation_title)) { onExplanationExpandedChange(true) }
                            .testTag("gforce_radar_canvas")
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            GForceMetricItem(
                                label = stringResource(R.string.gforce_label_total),
                                value = String.format(Locale.US, "%.2f G", gForceData.totalG),
                                accent = gAccent,
                                valueFontSize = 24.sp,
                                modifier = Modifier.weight(1f)
                            )
                            GForceMetricItem(
                                label = "${stringResource(R.string.gforce_label_peak)} ($holdLabel)",
                                value = String.format(Locale.US, "%.2f G", gForceData.peakG),
                                accent = cs.tertiary,
                                valueFontSize = 22.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(cs.surfaceContainer)
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            GForceAxisReadout(
                                label = stringResource(R.string.gforce_label_lateral),
                                value = String.format(Locale.US, "%+.2f G", gForceData.lateralG),
                                valueFontSize = 14.sp
                            )
                            GForceAxisReadout(
                                label = stringResource(R.string.gforce_label_longitudinal),
                                value = String.format(Locale.US, "%+.2f G", gForceData.longitudinalG),
                                valueFontSize = 14.sp
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = onCalibrateZero,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("btn_gforce_calibrate"),
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = cs.surfaceContainerHigh,
                                    contentColor = cs.onSurface
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CenterFocusStrong,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.gforce_action_calibrate),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            FilledTonalButton(
                                onClick = onResetPeak,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("btn_gforce_reset_peak"),
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = cs.surfaceContainerHigh,
                                    contentColor = cs.onSurface
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.gforce_action_reset_peak),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Live G-Force circular radar powered by Accelerometer + Gyroscope fusion
                    ComposeGForceRadar(
                        gForceData = gForceData,
                        dotColor = gAccent,
                        showPeakOnDial = showPeakOnDial,
                        showScaleLabels = false,
                        modifier = Modifier
                            .size(radarSize)
                            .clickable(onClickLabel = stringResource(R.string.gforce_explanation_title)) { onExplanationExpandedChange(true) }
                            .testTag("gforce_radar_canvas")
                    )

                    // Instantaneous numerical readouts
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            GForceMetricItem(
                                label = stringResource(R.string.gforce_label_total),
                                value = String.format(Locale.US, "%.2f G", gForceData.totalG),
                                accent = gAccent,
                                modifier = Modifier.weight(1f)
                            )
                            GForceMetricItem(
                                label = "${stringResource(R.string.gforce_label_peak)} ($holdLabel)",
                                value = String.format(Locale.US, "%.2f G", gForceData.peakG),
                                accent = cs.tertiary,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(cs.surfaceContainer)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            GForceAxisReadout(
                                label = stringResource(R.string.gforce_label_lateral),
                                value = String.format(Locale.US, "%+.2f", gForceData.lateralG)
                            )
                            GForceAxisReadout(
                                label = stringResource(R.string.gforce_label_longitudinal),
                                value = String.format(Locale.US, "%+.2f", gForceData.longitudinalG)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = onCalibrateZero,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("btn_gforce_calibrate"),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = cs.surfaceContainerHigh,
                            contentColor = cs.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CenterFocusStrong,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.gforce_action_calibrate),
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.5.sp)
                        )
                    }

                    FilledTonalButton(
                        onClick = onResetPeak,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("btn_gforce_reset_peak"),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = cs.surfaceContainerHigh,
                            contentColor = cs.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.gforce_action_reset_peak),
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.5.sp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposeGForceRadar(
    gForceData: GForceData,
    dotColor: Color,
    showPeakOnDial: Boolean,
    showScaleLabels: Boolean = false,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val dialBg = cs.surfaceContainer
    val ringOuter = cs.outline.copy(alpha = 0.48f)
    val ringInner = cs.outline.copy(alpha = 0.30f)
    val crosshair = cs.outlineVariant.copy(alpha = 0.55f)
    val labelColor = cs.onSurfaceVariant.copy(alpha = 0.78f)
    val peakColor = cs.tertiary

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val edgeInset = if (showScaleLabels) 4.dp.toPx() else 3.dp.toPx()
            val radius = (size.minDimension / 2f) - edgeInset
            if (radius <= 0f) return@Canvas

            drawCircle(color = dialBg, radius = radius, center = Offset(cx, cy))
            drawCircle(
                color = ringOuter,
                radius = radius,
                center = Offset(cx, cy),
                style = Stroke(width = (if (showScaleLabels) 1.6f else 1.4f).dp.toPx())
            )
            if (showScaleLabels) {
                // 0.5G & 1.0G dashed concentric rings for 1.5G full-scale precision
                listOf(1f / 3f, 2f / 3f).forEach { frac ->
                    drawCircle(
                        color = ringInner,
                        radius = radius * frac,
                        center = Offset(cx, cy),
                        style = Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                        )
                    )
                }
            } else {
                drawCircle(
                    color = ringInner,
                    radius = radius * 0.5f,
                    center = Offset(cx, cy),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )
                )
            }
            drawLine(
                color = crosshair,
                start = Offset(cx - radius, cy),
                end = Offset(cx + radius, cy),
                strokeWidth = 0.9.dp.toPx()
            )
            drawLine(
                color = crosshair,
                start = Offset(cx, cy - radius),
                end = Offset(cx, cy + radius),
                strokeWidth = 0.9.dp.toPx()
            )

            if (showScaleLabels) {
                val scalePaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                    textSize = 9.5.sp.toPx()
                    color = labelColor.toArgb()
                    isFakeBoldText = true
                }
                // Subtle ring scale & directional cues inside the expanded dial
                drawContext.canvas.nativeCanvas.drawText(
                    "1.0G",
                    cx + radius * (2f / 3f) - 10.dp.toPx(),
                    cy - 3.dp.toPx(),
                    scalePaint
                )
                drawContext.canvas.nativeCanvas.drawText("加", cx, cy - radius + 11.dp.toPx(), scalePaint)
                drawContext.canvas.nativeCanvas.drawText("刹", cx, cy + radius - 4.dp.toPx(), scalePaint)
                drawContext.canvas.nativeCanvas.drawText("左", cx - radius + 9.dp.toPx(), cy + 3.5.dp.toPx(), scalePaint)
                drawContext.canvas.nativeCanvas.drawText("右", cx + radius - 9.dp.toPx(), cy + 3.5.dp.toPx(), scalePaint)
            }

            val maxScaleG = 1.5f
            val maxTravel = radius - 4.dp.toPx()

            fun mapPoint(latG: Float, longG: Float): Offset {
                val nx = latG / maxScaleG
                val ny = -longG / maxScaleG
                val mag = hypot(nx, ny)
                val scale = if (mag > 1f) 1f / mag else 1f
                return Offset(cx + nx * scale * maxTravel, cy + ny * scale * maxTravel)
            }

            if (showPeakOnDial && gForceData.peakG >= 0.05f) {
                drawGForcePeakEnvelope(
                    gForceData = gForceData,
                    cx = cx,
                    cy = cy,
                    maxTravel = maxTravel,
                    maxScaleG = maxScaleG,
                    peakColor = peakColor,
                    strokeWidthPx = (if (showScaleLabels) 2.0f else 1.6f).dp.toPx(),
                    peakDotRadiusPx = (if (showScaleLabels) 4.4f else 3.8f).dp.toPx()
                )
            }

            val trail = gForceData.trail
            if (trail.size > 1) {
                trail.forEachIndexed { idx, sample ->
                    val frac = (idx + 1f) / trail.size.toFloat()
                    drawCircle(
                        color = dotColor.copy(alpha = frac * 0.42f),
                        radius = (if (showScaleLabels) (1.8f + 1.8f * frac) else (1.5f + 1.5f * frac)).dp.toPx(),
                        center = mapPoint(sample.lateralG, sample.longitudinalG)
                    )
                }
            }

            val dotCenter = mapPoint(gForceData.lateralG, gForceData.longitudinalG)
            drawCircle(
                color = dotColor.copy(alpha = 0.28f),
                radius = (if (showScaleLabels) 10.dp else 8.dp).toPx(),
                center = dotCenter
            )
            drawCircle(
                color = dotColor,
                radius = (if (showScaleLabels) 5.6.dp else 4.6.dp).toPx(),
                center = dotCenter
            )
        }
    }
}

@Composable
private fun GForceMetricItem(
    label: String,
    value: String,
    accent: Color,
    valueFontSize: TextUnit = 18.sp,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = valueFontSize
            ),
            color = accent,
            maxLines = 1
        )
    }
}

@Composable
private fun GForceAxisReadout(
    label: String,
    value: String,
    valueFontSize: TextUnit = 12.sp
) {
    val cs = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = valueFontSize
            ),
            color = cs.onSurface,
            maxLines = 1
        )
    }
}
