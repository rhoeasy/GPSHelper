package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
    onCalibrateZero: () -> Unit,
    onResetPeak: () -> Unit,
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

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("gforce_telemetry_card"),
        shape = RoundedCornerShape(24.dp),
        color = cs.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
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
                        imageVector = Icons.Rounded.Sensors,
                        contentDescription = null,
                        tint = cs.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.gforce_card_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onSurface
                    )
                }

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
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Live G-Force circular radar powered by Accelerometer + Gyroscope fusion
                ComposeGForceRadar(
                    gForceData = gForceData,
                    dotColor = gAccent,
                    modifier = Modifier.size(92.dp)
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
                            label = stringResource(R.string.gforce_label_peak),
                            value = String.format(Locale.US, "%.2f G", gForceData.peakG),
                            accent = cs.onSurface,
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

@Composable
private fun ComposeGForceRadar(
    gForceData: GForceData,
    dotColor: Color,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val dialBg = cs.surfaceContainer
    val ringOuter = cs.outline.copy(alpha = 0.45f)
    val ringInner = cs.outline.copy(alpha = 0.28f)
    val crosshair = cs.outlineVariant.copy(alpha = 0.5f)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = (size.minDimension / 2f) - 3.dp.toPx()
            if (radius <= 0f) return@Canvas

            drawCircle(color = dialBg, radius = radius, center = Offset(cx, cy))
            drawCircle(
                color = ringOuter,
                radius = radius,
                center = Offset(cx, cy),
                style = Stroke(width = 1.4.dp.toPx())
            )
            drawCircle(
                color = ringInner,
                radius = radius * 0.5f,
                center = Offset(cx, cy),
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                )
            )
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

            val maxScaleG = 1.5f
            val maxTravel = radius - 4.dp.toPx()

            fun mapPoint(latG: Float, longG: Float): Offset {
                val nx = latG / maxScaleG
                val ny = -longG / maxScaleG
                val mag = hypot(nx, ny)
                val scale = if (mag > 1f) 1f / mag else 1f
                return Offset(cx + nx * scale * maxTravel, cy + ny * scale * maxTravel)
            }

            val trail = gForceData.trail
            if (trail.size > 1) {
                trail.forEachIndexed { idx, sample ->
                    val frac = (idx + 1f) / trail.size.toFloat()
                    drawCircle(
                        color = dotColor.copy(alpha = frac * 0.38f),
                        radius = (1.5f + 1.5f * frac).dp.toPx(),
                        center = mapPoint(sample.lateralG, sample.longitudinalG)
                    )
                }
            }

            val dotCenter = mapPoint(gForceData.lateralG, gForceData.longitudinalG)
            drawCircle(
                color = dotColor.copy(alpha = 0.25f),
                radius = 8.dp.toPx(),
                center = dotCenter
            )
            drawCircle(
                color = dotColor,
                radius = 4.6.dp.toPx(),
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
            color = cs.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            ),
            color = accent
        )
    }
}

@Composable
private fun GForceAxisReadout(
    label: String,
    value: String
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
                fontWeight = FontWeight.SemiBold
            ),
            color = cs.onSurface
        )
    }
}
