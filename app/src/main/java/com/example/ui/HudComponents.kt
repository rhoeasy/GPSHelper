package com.example.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.GpsNotFixed
import androidx.compose.material.icons.rounded.GpsOff
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.SatelliteAlt
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.gps.GpsData
import com.example.gps.DashboardSpeedStyle
import com.example.gps.GpsFixStatus
import com.example.gps.SpeedUnit
import com.example.ui.theme.CoordinatesMonoStyle
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.TelemetryValueStyle

@Composable
fun GpsStatusBadge(
    fixStatus: GpsFixStatus,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val infiniteTransition = rememberInfiniteTransition(label = "gps_status_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val (labelRes, dotColor, containerColor, contentColor, shouldPulse) = when (fixStatus) {
        GpsFixStatus.LOCKED -> StatusSpec(
            R.string.status_gps_locked, cs.secondary, cs.secondaryContainer, cs.onSecondaryContainer, false
        )
        GpsFixStatus.FUSED_FALLBACK -> StatusSpec(
            R.string.status_fused_fallback, cs.primary, cs.primaryContainer, cs.onPrimaryContainer, true
        )
        GpsFixStatus.SEARCHING -> StatusSpec(
            R.string.status_searching, cs.tertiary, cs.tertiaryContainer, cs.onTertiaryContainer, true
        )
        GpsFixStatus.DISABLED -> StatusSpec(
            R.string.status_gps_disabled, cs.error, cs.errorContainer, cs.onErrorContainer, false
        )
        GpsFixStatus.NO_PERMISSION -> StatusSpec(
            R.string.status_no_permission, cs.tertiary, cs.tertiaryContainer, cs.onTertiaryContainer, false
        )
    }

    val animatedDot by animateColorAsState(targetValue = dotColor, label = "badge_dot")
    val animatedBg by animateColorAsState(targetValue = containerColor, label = "badge_bg")
    val animatedFg by animateColorAsState(targetValue = contentColor, label = "badge_fg")

    Surface(
        modifier = modifier.testTag("gps_status_badge"),
        color = animatedBg,
        shape = RoundedCornerShape(50)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(animatedDot.copy(alpha = if (shouldPulse) pulseAlpha else 1f))
            )
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.5.sp
                ),
                color = animatedFg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private data class StatusSpec(
    val labelRes: Int,
    val dot: Color,
    val container: Color,
    val content: Color,
    val pulse: Boolean
)

@Composable
fun PermissionBannerCard(
    status: PermissionStatus,
    onRequestLocation: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestNotification: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("permission_banner"),
        shape = RoundedCornerShape(24.dp),
        color = cs.tertiaryContainer.copy(alpha = 0.72f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = cs.onTertiaryContainer,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.permission_banner_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = cs.onTertiaryContainer
                )
            }

            if (!status.hasFineLocation) {
                PermissionActionRow(
                    icon = Icons.Rounded.MyLocation,
                    title = stringResource(R.string.permission_location_title),
                    subtitle = stringResource(R.string.permission_location_desc),
                    actionLabel = stringResource(R.string.action_grant),
                    buttonTag = "btn_request_location",
                    isOptional = false,
                    onClick = onRequestLocation
                )
            }

            if (!status.hasOverlayPermission) {
                PermissionActionRow(
                    icon = Icons.Rounded.Layers,
                    title = stringResource(R.string.permission_overlay_title),
                    subtitle = stringResource(R.string.permission_overlay_desc),
                    actionLabel = stringResource(R.string.action_open_settings),
                    buttonTag = "btn_request_overlay",
                    isOptional = false,
                    onClick = onRequestOverlay
                )
            }

            if (status.requiresNotificationPermission && !status.hasNotificationPermission) {
                PermissionActionRow(
                    icon = Icons.Rounded.NotificationsActive,
                    title = stringResource(R.string.permission_notification_title),
                    subtitle = stringResource(R.string.permission_notification_desc),
                    actionLabel = stringResource(R.string.action_grant),
                    buttonTag = "btn_request_notification",
                    isOptional = true,
                    onClick = onRequestNotification
                )
            }
        }
    }
}

@Composable
private fun PermissionActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String,
    buttonTag: String,
    isOptional: Boolean,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cs.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isOptional) cs.onSurfaceVariant else cs.primary,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = cs.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = cs.onSurfaceVariant
            )
        }
        FilledTonalButton(
            onClick = onClick,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag(buttonTag),
            shape = RoundedCornerShape(50),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = if (isOptional) cs.surfaceContainerHigh else cs.primaryContainer,
                contentColor = if (isOptional) cs.onSurfaceVariant else cs.onPrimaryContainer
            )
        ) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp)
            )
        }
    }
}

@Composable
fun PrimarySpeedCard(
    gpsData: GpsData,
    speedUnit: SpeedUnit,
    onSelectUnit: (SpeedUnit) -> Unit,
    onOpenLocationSettings: () -> Unit,
    isAlertActive: Boolean = false,
    isLandscape: Boolean = false,
    style: DashboardSpeedStyle = DashboardSpeedStyle.NUMERIC,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val speedVal = gpsData.speedInUnit(speedUnit)?.takeIf { it.isFinite() }
    val cardBg by animateColorAsState(
        targetValue = if (isAlertActive) cs.errorContainer else cs.surfaceContainerLow,
        label = "speed_card_bg"
    )

    val providerText = when (gpsData.fixStatus) {
        GpsFixStatus.LOCKED -> stringResource(R.string.provider_gps_native)
        GpsFixStatus.FUSED_FALLBACK -> stringResource(R.string.provider_fused_fallback)
        GpsFixStatus.DISABLED -> stringResource(R.string.status_gps_disabled)
        GpsFixStatus.NO_PERMISSION -> stringResource(R.string.status_no_permission)
        GpsFixStatus.SEARCHING -> stringResource(R.string.provider_waiting)
    }

    val providerIcon = when (gpsData.fixStatus) {
        GpsFixStatus.LOCKED -> Icons.Rounded.GpsFixed
        GpsFixStatus.DISABLED, GpsFixStatus.NO_PERMISSION -> Icons.Rounded.GpsOff
        else -> Icons.Rounded.GpsNotFixed
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("primary_speed_card"),
        shape = RoundedCornerShape(28.dp),
        color = cardBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (isLandscape) 20.dp else 22.dp,
                    vertical = if (isLandscape) 16.dp else 20.dp
                ),
            verticalArrangement = if (isLandscape) {
                Arrangement.SpaceBetween
            } else {
                Arrangement.spacedBy(16.dp)
            }
        ) {
            // Top bar inside Hero Card
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
                        imageVector = if (isAlertActive) Icons.Rounded.WarningAmber else providerIcon,
                        contentDescription = null,
                        tint = when {
                            isAlertActive -> cs.error
                            gpsData.fixStatus == GpsFixStatus.LOCKED -> cs.secondary
                            else -> cs.primary
                        },
                        modifier = Modifier.size(if (isLandscape) 18.dp else 16.dp)
                    )
                    Text(
                        text = stringResource(R.string.speed_label),
                        style = if (isLandscape) {
                            MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        } else {
                            MaterialTheme.typography.titleSmall
                        },
                        color = if (isAlertActive) cs.onErrorContainer else cs.onSurfaceVariant
                    )
                    if (isAlertActive) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = cs.error,
                            modifier = Modifier.testTag("speed_overspeed_badge")
                        ) {
                            Text(
                                text = stringResource(R.string.alert_badge_overspeed),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.5.sp
                                ),
                                color = cs.onError,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                SpeedUnitToggleChip(
                    selectedUnit = speedUnit,
                    onSelectUnit = onSelectUnit
                )
            }

            if (style == DashboardSpeedStyle.NUMERIC) {
                // Giant clean tabular speed figure (maximized for wide landscape cockpit legibility)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = if (isLandscape) 8.dp else 4.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = gpsData.formattedSpeedValue(speedUnit),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = if (isLandscape) 92.sp else 76.sp,
                            lineHeight = if (isLandscape) 94.sp else 78.sp,
                            fontWeight = if (isLandscape) FontWeight.Bold else FontWeight.SemiBold,
                            letterSpacing = if (isLandscape) (-3.0).sp else (-2.5).sp
                        ),
                        color = when {
                            isAlertActive -> cs.error
                            gpsData.hasFix -> cs.onSurface
                            else -> cs.outline
                        },
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.testTag("speed_readout_text")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = speedUnit.label,
                        style = (if (isLandscape) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium).copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = if (isAlertActive) cs.onErrorContainer else cs.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = if (isLandscape) 15.dp else 12.dp)
                    )
                }
            } else {
                DashboardSpeedDial(
                    speedVal, gpsData.formattedSpeedValue(speedUnit), speedUnit, style,
                    accent = if (isAlertActive) cs.error else cs.primary
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(if (isLandscape) 10.dp else 12.dp)
            ) {
                if (style == DashboardSpeedStyle.NUMERIC) {
                    val maxScale = if (speedUnit == SpeedUnit.KMH) 160f else 100f
                    val animatedFraction by animateFloatAsState(
                        targetValue = ((speedVal ?: 0f) / maxScale).coerceIn(0f, 1f),
                        animationSpec = spring(dampingRatio = 0.85f, stiffness = 200f),
                        label = "speed_bar_progress"
                    )
                    // Rounded M3 speed bar (thicker & graduated with quarter markers in landscape)
                    val trackColor = if (isAlertActive) cs.error.copy(alpha = 0.22f) else cs.surfaceContainerHighest
                    val fillColor = if (isAlertActive) cs.error else cs.primary
                    val tickColor = cs.surfaceContainerLow.copy(alpha = 0.72f)
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isLandscape) 10.dp else 6.dp)
                    ) {
                        val radius = CornerRadius(size.height / 2f, size.height / 2f)
                        drawRoundRect(
                            color = trackColor,
                            size = size,
                            cornerRadius = radius
                        )
                        if (animatedFraction > 0.005f) {
                            drawRoundRect(
                                color = fillColor,
                                size = Size(size.width * animatedFraction, size.height),
                                cornerRadius = radius
                            )
                        }
                        if (isLandscape) {
                            val tickWidth = 2.dp.toPx()
                            listOf(0.25f, 0.5f, 0.75f).forEach { frac ->
                                val x = size.width * frac
                                drawLine(
                                    color = tickColor,
                                    start = androidx.compose.ui.geometry.Offset(x, 0f),
                                    end = androidx.compose.ui.geometry.Offset(x, size.height),
                                    strokeWidth = tickWidth
                                )
                            }
                        }
                    }
                }

                // Bottom caption / landscape quick cockpit telemetry strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = providerText,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = if (isAlertActive) cs.onErrorContainer else cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (gpsData.fixStatus == GpsFixStatus.DISABLED) {
                        Text(
                            text = stringResource(R.string.action_open_location_settings),
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                            color = cs.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onOpenLocationSettings)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("btn_open_location_settings")
                        )
                    } else if (isLandscape) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = gpsData.formattedBearing(),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = if (isAlertActive) cs.onErrorContainer else cs.onSurfaceVariant,
                                maxLines = 1
                            )
                            Text(
                                text = gpsData.formattedAccuracy(),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = if (isAlertActive) cs.onErrorContainer else cs.primary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedUnitToggleChip(
    selectedUnit: SpeedUnit,
    onSelectUnit: (SpeedUnit) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(cs.surfaceContainer)
            .padding(3.dp)
            .testTag("speed_unit_toggle"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SpeedUnit.entries.forEach { unit ->
            val selected = unit == selectedUnit
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) cs.primaryContainer else Color.Transparent)
                    .clickable { onSelectUnit(unit) }
                    .padding(horizontal = 12.dp, vertical = 5.dp)
                    .testTag("unit_chip_${unit.name.lowercase()}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = unit.label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CoordinatesCard(
    gpsData: GpsData,
    onOpenDetails: () -> Unit,
    onCopyCoordinates: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val coordsDisplay = gpsData.formattedCoordinates() ?: "-- , --"

    Surface(
        onClick = onOpenDetails,
        modifier = modifier
            .fillMaxWidth()
            .testTag("coordinates_card"),
        shape = RoundedCornerShape(22.dp),
        color = cs.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.coordinates_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant
                )
                Text(
                    text = coordsDisplay,
                    style = CoordinatesMonoStyle.copy(fontSize = 16.sp),
                    color = if (gpsData.hasFix) cs.onSurface else cs.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("coordinates_value_text")
                )
            }

            FilledTonalButton(
                onClick = onCopyCoordinates,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("copy_coordinates_button"),
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = cs.surfaceContainerHigh,
                    contentColor = cs.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.action_copy_coordinates),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp)
                )
            }
        }
    }
}

@Composable
fun TelemetryGrid(
    gpsData: GpsData,
    onOpenAltitudeDetail: () -> Unit = {},
    onOpenAccuracyDetail: () -> Unit = {},
    onOpenCompassDetail: () -> Unit = {},
    onOpenSatelliteDetail: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val bearingAngle = gpsData.bearingDegrees ?: 0f
    val animatedBearing by animateFloatAsState(
        targetValue = bearingAngle,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 200f),
        label = "compass_rotation"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TelemetryMetricCard(
                icon = Icons.Rounded.Terrain,
                iconTint = cs.onPrimaryContainer,
                iconBg = cs.primaryContainer,
                label = stringResource(R.string.telemetry_altitude),
                value = gpsData.formattedAltitude(),
                hasValue = gpsData.displayAltitudeMeters != null,
                onClick = onOpenAltitudeDetail,
                modifier = Modifier
                    .weight(1f)
                    .testTag("metric_altitude_card")
            )
            TelemetryMetricCard(
                icon = Icons.Rounded.GpsFixed,
                iconTint = cs.onSecondaryContainer,
                iconBg = cs.secondaryContainer,
                label = stringResource(R.string.telemetry_accuracy),
                value = gpsData.formattedAccuracy(),
                hasValue = gpsData.hasFix && gpsData.accuracyMeters != null,
                onClick = onOpenAccuracyDetail,
                modifier = Modifier
                    .weight(1f)
                    .testTag("metric_accuracy_card")
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TelemetryMetricCard(
                icon = Icons.Rounded.Navigation,
                iconTint = cs.onTertiaryContainer,
                iconBg = cs.tertiaryContainer,
                iconRotation = if (gpsData.hasFix && gpsData.bearingDegrees != null) animatedBearing else 0f,
                label = stringResource(R.string.telemetry_bearing),
                value = gpsData.formattedBearing(),
                hasValue = gpsData.hasFix && gpsData.bearingDegrees != null,
                onClick = onOpenCompassDetail,
                modifier = Modifier
                    .weight(1f)
                    .testTag("metric_bearing_card")
            )
            TelemetryMetricCard(
                icon = Icons.Rounded.SatelliteAlt,
                iconTint = cs.onPrimaryContainer,
                iconBg = cs.primaryContainer,
                label = stringResource(R.string.telemetry_satellites),
                value = gpsData.formattedSatellites(),
                subtitle = stringResource(R.string.telemetry_satellites_sub),
                hasValue = gpsData.hasSatelliteInfo || gpsData.hasFix,
                onClick = onOpenSatelliteDetail,
                modifier = Modifier
                    .weight(1f)
                    .testTag("metric_satellites_card")
            )
        }
    }
}

@Composable
private fun TelemetryMetricCard(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    label: String,
    value: String,
    hasValue: Boolean,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    iconRotation: Float = 0f,
    onClick: (() -> Unit)? = null
) {
    val cs = MaterialTheme.colorScheme
    val cardContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier
                            .size(16.dp)
                            .rotate(iconRotation)
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (onClick != null) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = cs.onSurfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = value,
                    style = TelemetryValueStyle.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = if (hasValue) cs.onSurface else cs.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(22.dp),
            color = cs.surfaceContainerLow,
            content = cardContent
        )
    } else {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(22.dp),
            color = cs.surfaceContainerLow,
            content = cardContent
        )
    }
}
