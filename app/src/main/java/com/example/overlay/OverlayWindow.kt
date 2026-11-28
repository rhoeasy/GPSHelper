package com.example.overlay

import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.R
import com.example.alert.SafetyAlertMonitor
import com.example.alert.SafetyAlertState
import com.example.ble.HeartRateState
import com.example.gps.GpsData
import com.example.gps.GpsRepository
import com.example.gps.OverlayDisplayConfig
import com.example.gps.SafetyAlertConfig
import com.example.gps.SpeedUnit
import com.example.sensor.GForceData
import com.example.ui.drawGForcePeakEnvelope
import com.example.ui.theme.AppThemePalette
import com.example.ui.theme.GpsHudTheme
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalTelemetryColors
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Jetpack Compose floating HUD component optimized for outdoor riding & driving visibility.
 *
 * Dynamically renders user-selected modules (Speed, G-Force radar + peak marker, Altitude,
 * Heart Rate, Bearing, Accuracy, Coordinates) with adjustable scale, font size, background
 * transparency, Day/Night/Auto sync, and gentle visual safety alert highlighting.
 */
@Composable
fun OverlayWindow(
    gForceData: GForceData,
    gpsData: GpsData,
    speedUnit: SpeedUnit,
    heartRateState: HeartRateState,
    config: OverlayDisplayConfig,
    alertState: SafetyAlertState = SafetyAlertState(),
    themeMode: ThemeMode,
    themePalette: AppThemePalette = AppThemePalette.GOOGLE_BLUE,
    isMiniMode: Boolean,
    onToggleMiniMode: () -> Unit,
    previewRideElapsedMs: Long? = null,
    modifier: Modifier = Modifier
) {
    GpsHudTheme(themeMode = themeMode, themePalette = themePalette) {
        val cs = MaterialTheme.colorScheme
        val tc = LocalTelemetryColors.current

        val s = config.scale.factor
        val fs = config.fontScale * s
        val bgAlpha = config.backgroundAlpha.coerceIn(0.45f, 0.98f)

        val gAccent = when {
            gForceData.totalG >= 1.0f -> cs.error
            gForceData.totalG >= 0.5f -> cs.tertiary
            else -> cs.primary
        }
        val containerColor = if (alertState.isAnyAlert) {
            cs.errorContainer.copy(alpha = (bgAlpha + 0.04f).coerceAtMost(0.98f))
        } else {
            cs.surfaceContainerLow.copy(alpha = bgAlpha)
        }
        val borderColor = if (alertState.isAnyAlert) {
            cs.error.copy(alpha = 0.85f)
        } else {
            cs.outlineVariant.copy(alpha = 0.65f)
        }

        val speedValueStr = gpsData.formattedSpeedValue(speedUnit)
        val altText = gpsData.formattedAltitude()
        val liveGText = String.format(Locale.US, "%.2fG", gForceData.totalG)
        val peakGText = String.format(Locale.US, "%.2fG", gForceData.peakG)
        val hrText = heartRateState.bpm?.let { "$it bpm" } ?: "-- bpm"
        val bearingText = gpsData.formattedBearing()
        val accuracyText = gpsData.formattedAccuracy()
        val coordsText = gpsData.formattedCoordinates() ?: "--, --"

        val hasRightColumn = config.showSpeed ||
            config.showAltitude ||
            config.showHeartRate ||
            config.showBearing ||
            config.showAccuracy ||
            config.showCoordinates ||
            config.showGForce

        val shape = RoundedCornerShape((22f * s).dp)
        // Keep the translucent fill in one clipped layer over the app below it.
        Box(
            modifier = modifier
                .clip(shape)
                .background(containerColor)
                .border(BorderStroke(if (alertState.isAnyAlert) 1.6.dp else 1.dp, borderColor), shape)
                .clickable(onClick = onToggleMiniMode)
                .testTag("overlay_window_root")
        ) {
            Column(modifier = Modifier.padding(horizontal = (11f * s).dp, vertical = (8f * s).dp)) {
                OverlayStatusRow(gpsData, config, previewRideElapsedMs, s, fs.coerceAtMost(1.7f))
                Row(
                    modifier = Modifier.padding(top = if (hasRightColumn) (5f * s).dp else 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy((if (isMiniMode) 8f else 11f).times(s).dp)
                ) {
                    if (config.showGForce) {
                        OverlayGForceDial(
                            gForceData = gForceData,
                            dotColor = gAccent,
                            showPeakOnDial = config.showGForcePeakOnDial,
                            modifier = Modifier
                                .size((if (isMiniMode) 38f else 60f).times(s).dp)
                                .testTag("overlay_gforce_dial")
                        )
                    }

                    if (hasRightColumn) {
                        if (isMiniMode) {
                            // Ultra-compact single-line mini pill (scaled for riding readability)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy((6f * s).dp)
                            ) {
                                var emittedCount = 0
                                @Composable
                                fun sep() {
                                    if (emittedCount > 0) {
                                        Text(
                                            text = "·",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontSize = (13f * fs).sp
                                            ),
                                            color = cs.outline
                                        )
                                    }
                                    emittedCount++
                                }

                                if (config.showSpeed) {
                                    sep()
                                    Row(
                                        verticalAlignment = Alignment.Bottom,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = speedValueStr,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontFamily = JetBrainsMonoFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = (17f * fs).sp
                                            ),
                                            color = if (alertState.isOverspeed) cs.error else cs.onSurface,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = speedUnit.label,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = (10.5f * fs).sp
                                            ),
                                            color = if (alertState.isOverspeed) cs.error else cs.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                                if (config.showAltitude) {
                                    sep()
                                    Text(
                                        text = altText,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = (14.5f * fs).sp
                                        ),
                                        color = cs.onSurface,
                                        maxLines = 1
                                    )
                                }
                                if (config.showGForce) {
                                    sep()
                                    Text(
                                        text = liveGText,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = (14.5f * fs).sp
                                        ),
                                        color = gAccent,
                                        maxLines = 1
                                    )
                                }
                                if (config.showHeartRate) {
                                    sep()
                                    Text(
                                        text = "♥${heartRateState.bpm ?: "--"}",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = (14f * fs).sp
                                        ),
                                        color = if (alertState.isHighHeartRate) cs.error else tc.heart,
                                        maxLines = 1
                                    )
                                }
                                if (config.showBearing && emittedCount < 3) {
                                    sep()
                                    Text(
                                        text = bearingText,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = (13f * fs).sp
                                        ),
                                        color = cs.primary,
                                        maxLines = 1
                                    )
                                }
                                if (config.showAccuracy && emittedCount == 0) {
                                    sep()
                                    Text(
                                        text = accuracyText,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontSize = (13.5f * fs).sp
                                        ),
                                        color = cs.onSurface,
                                        maxLines = 1
                                    )
                                }
                                if (config.showCoordinates && emittedCount == 0) {
                                    sep()
                                    Text(
                                        text = coordsText,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontSize = (12f * fs).sp
                                        ),
                                        color = cs.onSurface,
                                        maxLines = 1
                                    )
                                }
                            }
                        } else {
                            // Standard compact multi-row HUD (riding-optimized typography)
                            Column(verticalArrangement = Arrangement.spacedBy((4f * s).dp)) {
                                // Row 1: Speed (hero readout when enabled) + optional Bearing
                                if (config.showSpeed) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy((6f * s).dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.Bottom,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Text(
                                                text = speedValueStr,
                                                style = MaterialTheme.typography.titleLarge.copy(
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = (23f * fs).sp,
                                                    lineHeight = (25f * fs).sp
                                                ),
                                                color = if (alertState.isOverspeed) cs.error else cs.onSurface,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = speedUnit.label,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = (11.5f * fs).sp,
                                                    fontWeight = FontWeight.Medium
                                                ),
                                                color = if (alertState.isOverspeed) cs.error else cs.onSurfaceVariant,
                                                modifier = Modifier.padding(bottom = 2.dp),
                                                maxLines = 1
                                            )
                                        }
                                        if (config.showBearing) {
                                            Text(
                                                text = bearingText,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = (12f * fs).sp
                                                ),
                                                color = cs.primary,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }

                                // Row 2: Altitude + Accuracy + Bearing (if Speed is off)
                                val showEnvRow = config.showAltitude ||
                                    config.showAccuracy ||
                                    (config.showBearing && !config.showSpeed)
                                if (showEnvRow) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy((6f * s).dp)
                                    ) {
                                        if (config.showAltitude) {
                                            Text(
                                                text = stringResource(R.string.telemetry_altitude),
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = (11.5f * fs).sp
                                                ),
                                                color = cs.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = altText,
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = (if (config.showSpeed) 14f else 16.5f).times(fs).sp
                                                ),
                                                color = cs.onSurface,
                                                maxLines = 1
                                            )
                                        }
                                        if (config.showBearing && !config.showSpeed) {
                                            Text(
                                                text = bearingText,
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = (13.5f * fs).sp
                                                ),
                                                color = cs.primary,
                                                maxLines = 1
                                            )
                                        }
                                        if (config.showAccuracy) {
                                            Text(
                                                text = accuracyText,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontSize = (12f * fs).sp
                                                ),
                                                color = cs.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }

                                // Row 3: G-Force (live + max) + Heart Rate
                                if (config.showGForce || config.showHeartRate) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy((8f * s).dp)
                                    ) {
                                        if (config.showGForce) {
                                            Text(
                                                text = liveGText,
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = (13.5f * fs).sp
                                                ),
                                                color = gAccent,
                                                maxLines = 1
                                            )
                                            if (config.showGForcePeakOnDial) {
                                                Text(
                                                    text = "MAX $peakGText",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontFamily = JetBrainsMonoFontFamily,
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = (12f * fs).sp
                                                    ),
                                                    color = cs.tertiary,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                        if (config.showHeartRate) {
                                            Text(
                                                text = "♥ $hrText",
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = (13.5f * fs).sp
                                                ),
                                                color = if (alertState.isHighHeartRate) cs.error else tc.heart,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }

                                // Row 4: Coordinates
                                if (config.showCoordinates) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy((6f * s).dp)
                                    ) {
                                        Text(
                                            text = coordsText,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = JetBrainsMonoFontFamily,
                                                fontSize = (11.5f * fs).sp
                                            ),
                                            color = cs.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

            }
        }
    }
}

@Composable
private fun OverlayStatusRow(
    gpsData: GpsData,
    config: OverlayDisplayConfig,
    previewRideElapsedMs: Long?,
    scale: Float,
    fontScale: Float
) {
    val cs = MaterialTheme.colorScheme
    val signal = overlayGpsSignal(gpsData)
    val statusLabel = when (signal) {
        OverlayGpsSignal.NO_PERMISSION -> R.string.overlay_gps_no_permission
        OverlayGpsSignal.DISABLED -> R.string.overlay_gps_disabled
        OverlayGpsSignal.SEARCHING -> R.string.overlay_gps_searching
        OverlayGpsSignal.FUSED -> R.string.overlay_gps_fused
        OverlayGpsSignal.LOCKED -> R.string.status_gps_locked
        OverlayGpsSignal.WEAK -> R.string.overlay_gps_weak
        OverlayGpsSignal.FAIR -> R.string.overlay_gps_fair
        OverlayGpsSignal.GOOD -> R.string.overlay_gps_good
    }
    val signalDescription = stringResource(statusLabel)
    val signalColor = when (signal) {
        OverlayGpsSignal.GOOD, OverlayGpsSignal.LOCKED -> cs.secondary
        OverlayGpsSignal.FAIR, OverlayGpsSignal.FUSED -> cs.primary
        OverlayGpsSignal.WEAK, OverlayGpsSignal.SEARCHING -> cs.tertiary
        OverlayGpsSignal.DISABLED, OverlayGpsSignal.NO_PERMISSION -> cs.error
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((10f * scale).dp)
    ) {
        Row(
            modifier = Modifier
                .testTag("overlay_gps_status")
                .semantics(mergeDescendants = true) { stateDescription = signalDescription },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((4f * scale).dp)
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                for (bar in 1..3) {
                    Box(
                        Modifier
                            .size(width = (2.5f * scale).dp, height = ((3 + 2 * bar) * scale).dp)
                            .background(if (bar <= signal.bars) signalColor else cs.outlineVariant)
                    )
                }
            }
            Text(
                text = stringResource(R.string.overlay_gps_label),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = (11f * fontScale).sp),
                color = signalColor,
                maxLines = 1
            )
        }
        if (config.showRideTimer) {
            OverlayRideTimer(config.rideMarkerIntervalMinutes, config.showRideElapsedTime, previewRideElapsedMs, scale, fontScale)
        }
    }
}

@Composable
private fun OverlayRideTimer(intervalMinutes: Int, showElapsedTime: Boolean, previewElapsedMs: Long?, scale: Float, fontScale: Float) {
    val elapsedMs = if (previewElapsedMs != null) {
        previewElapsedMs
    } else {
        val elapsed by produceState(appRideClock.elapsedMs(SystemClock.elapsedRealtime()), intervalMinutes, showElapsedTime) {
            while (true) {
                val now = appRideClock.elapsedMs(SystemClock.elapsedRealtime())
                value = now
                delay(rideTimerRefreshDelayMs(now, intervalMinutes, showElapsedTime))
            }
        }
        elapsed
    }
    val readout = rideTimerReadout(elapsedMs, intervalMinutes)
    val cs = MaterialTheme.colorScheme
    val markSummary = stringResource(R.string.overlay_ride_marks_description, readout.completedMarks)
    Row(
        modifier = Modifier.testTag("overlay_ride_timer"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((5f * scale).dp)
    ) {
        if (showElapsedTime || readout.showTimeOnly) {
            Text(
                text = readout.elapsedText,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (11.5f * fontScale).sp
                ),
                color = cs.onSurface,
                maxLines = 1
            )
        }
        if (!readout.showTimeOnly) {
            Row(
                modifier = Modifier.semantics { contentDescription = markSummary },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy((2f * scale).dp)
            ) {
                repeat(MAX_RIDE_MARKS) { index ->
                    Box(
                        Modifier
                            .size((4.5f * scale).dp)
                            .background(if (index < readout.visibleMarks) cs.primary else Color.Transparent, CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun OverlayGForceDial(
    gForceData: GForceData,
    dotColor: Color,
    showPeakOnDial: Boolean,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val dialBg = cs.surfaceContainerHighest.copy(alpha = 0.55f)
    val outerRing = cs.outline.copy(alpha = 0.55f)
    val innerRing = cs.outline.copy(alpha = 0.30f)
    val crosshair = cs.outlineVariant.copy(alpha = 0.55f)
    val peakColor = cs.tertiary

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = (size.minDimension / 2f) - 2.dp.toPx()
        if (radius <= 0f) return@Canvas

        drawCircle(color = dialBg, radius = radius, center = Offset(cx, cy))
        drawCircle(
            color = outerRing,
            radius = radius,
            center = Offset(cx, cy),
            style = Stroke(width = 1.3.dp.toPx())
        )
        drawCircle(
            color = innerRing,
            radius = radius * 0.5f,
            center = Offset(cx, cy),
            style = Stroke(
                width = 0.9.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
            )
        )
        drawLine(
            color = crosshair,
            start = Offset(cx - radius, cy),
            end = Offset(cx + radius, cy),
            strokeWidth = 0.8.dp.toPx()
        )
        drawLine(
            color = crosshair,
            start = Offset(cx, cy - radius),
            end = Offset(cx, cy + radius),
            strokeWidth = 0.8.dp.toPx()
        )

        val maxScaleG = 1.5f
        val maxTravel = radius - 3.2.dp.toPx()

        fun mapPoint(latG: Float, longG: Float): Offset {
            val nx = latG / maxScaleG
            val ny = -longG / maxScaleG
            val mag = hypot(nx, ny)
            val scale = if (mag > 1f) 1f / mag else 1f
            return Offset(cx + nx * scale * maxTravel, cy + ny * scale * maxTravel)
        }

        // Recorded 360° max G-Force envelope shape & peak vertex marker (when enabled)
        if (showPeakOnDial && gForceData.peakG >= 0.05f) {
            val peakDotRadius = if (size.minDimension < 46.dp.toPx()) 2.6.dp.toPx() else 3.4.dp.toPx()
            drawGForcePeakEnvelope(
                gForceData = gForceData,
                cx = cx,
                cy = cy,
                maxTravel = maxTravel,
                maxScaleG = maxScaleG,
                peakColor = peakColor,
                strokeWidthPx = 1.4.dp.toPx(),
                peakDotRadiusPx = peakDotRadius
            )
        }

        val trail = gForceData.trail
        if (trail.size > 1) {
            trail.forEachIndexed { index, sample ->
                val fraction = (index + 1f) / trail.size.toFloat()
                drawCircle(
                    color = dotColor.copy(alpha = fraction * 0.40f),
                    radius = (1.1f + 1.3f * fraction).dp.toPx(),
                    center = mapPoint(sample.lateralG, sample.longitudinalG)
                )
            }
        }

        val dotCenter = mapPoint(gForceData.lateralG, gForceData.longitudinalG)
        val coreRadius = if (size.minDimension < 46.dp.toPx()) 3.2.dp.toPx() else 4.2.dp.toPx()
        drawCircle(
            color = dotColor.copy(alpha = 0.28f),
            radius = coreRadius * 1.7f,
            center = dotCenter
        )
        drawCircle(
            color = dotColor,
            radius = coreRadius,
            center = dotCenter
        )
    }
}

/**
 * WindowManager host for the Jetpack Compose [OverlayWindow].
 *
 * Supplies LifecycleOwner, SavedStateRegistryOwner, and ViewModelStoreOwner so ComposeView
 * operates cleanly inside an Android Foreground Service using TYPE_APPLICATION_OVERLAY,
 * with jitter-free screen-space dragging, persistent screen-position memory, and live config sync.
 */
class OverlayWindowController(
    private val context: Context,
    private val gForceFlow: StateFlow<GForceData>,
    private val gpsFlow: StateFlow<GpsData>,
    private val speedUnitFlow: StateFlow<SpeedUnit>,
    private val heartRateFlow: StateFlow<HeartRateState>,
    private val overlayConfigFlow: StateFlow<OverlayDisplayConfig>,
    private val safetyAlertConfigFlow: StateFlow<SafetyAlertConfig>,
    private val themeModeFlow: StateFlow<ThemeMode>,
    private val themePaletteFlow: StateFlow<AppThemePalette> = GpsRepository.themePalette
) : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    private var hostContainer: DraggableOverlayContainer? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isMiniMode by mutableStateOf(false)
    private var isAttached = false

    fun attach(): Boolean {
        if (isAttached) return true

        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        val params = createOverlayLayoutParams(context)
        // Restore last saved drag coordinates if available
        GpsRepository.loadOverlayPosition(context)?.let { (savedX, savedY) ->
            params.x = savedX
            params.y = savedY
        }

        val container = DraggableOverlayContainer(
            context = context,
            onDragDelta = { dx, dy ->
                params.x += dx
                params.y += dy
                val root = hostContainer
                if (root != null) {
                    clampPosition(params, root)
                    runCatching { windowManager.updateViewLayout(root, params) }
                }
            },
            onDragFinished = {
                GpsRepository.saveOverlayPosition(context, params.x, params.y)
            }
        ).apply {
            background = null
            setViewTreeLifecycleOwner(this@OverlayWindowController)
            setViewTreeSavedStateRegistryOwner(this@OverlayWindowController)
            setViewTreeViewModelStoreOwner(this@OverlayWindowController)
        }

        val composeView = ComposeView(context).apply {
            background = null
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                val gForce by gForceFlow.collectAsStateWithLifecycle()
                val gps by gpsFlow.collectAsStateWithLifecycle()
                val speedUnit by speedUnitFlow.collectAsStateWithLifecycle()
                val heartRate by heartRateFlow.collectAsStateWithLifecycle()
                val overlayConfig by overlayConfigFlow.collectAsStateWithLifecycle()
                val safetyAlertConfig by safetyAlertConfigFlow.collectAsStateWithLifecycle()
                val themeMode by themeModeFlow.collectAsStateWithLifecycle()
                val themePalette by themePaletteFlow.collectAsStateWithLifecycle()

                val alertState = SafetyAlertMonitor.evaluate(gps, heartRate, safetyAlertConfig)

                // Re-clamp window bounds whenever displayed modules or scale change pill dimensions
                LaunchedEffect(overlayConfig, isMiniMode) {
                    hostContainer?.post {
                        val root = hostContainer ?: return@post
                        clampPosition(params, root)
                        runCatching { windowManager.updateViewLayout(root, params) }
                    }
                }

                OverlayWindow(
                    gForceData = gForce,
                    gpsData = gps,
                    speedUnit = speedUnit,
                    heartRateState = heartRate,
                    config = overlayConfig,
                    alertState = alertState,
                    themeMode = themeMode,
                    themePalette = themePalette,
                    isMiniMode = isMiniMode,
                    onToggleMiniMode = { isMiniMode = !isMiniMode }
                )
            }
        }

        container.addView(
            composeView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        return try {
            windowManager.addView(container, params)
            hostContainer = container
            layoutParams = params
            isAttached = true
            // Clamp restored coordinates after first layout pass
            container.post {
                clampPosition(params, container)
                runCatching { windowManager.updateViewLayout(container, params) }
            }
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach Compose OverlayWindow", e)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            store.clear()
            false
        }
    }

    fun onConfigurationChanged() {
        val root = hostContainer ?: return
        val params = layoutParams ?: return
        val isLand = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val density = context.resources.displayMetrics.density
        val saved = GpsRepository.loadOverlayPosition(context, isLandscape = isLand)
        if (saved != null) {
            params.x = saved.first
            params.y = saved.second
        } else {
            params.x = (16f * density + 0.5f).toInt()
            params.y = ((if (isLand) 16f else 96f) * density + 0.5f).toInt()
        }
        clampPosition(params, root)
        runCatching { windowManager.updateViewLayout(root, params) }
    }

    fun detach() {
        if (!isAttached) return
        isAttached = false

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)

        val root = hostContainer
        hostContainer = null
        layoutParams = null

        if (root != null && root.isAttachedToWindow) {
            runCatching { windowManager.removeViewImmediate(root) }
                .onFailure {
                    runCatching { windowManager.removeView(root) }
                }
        }

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }

    private fun clampPosition(params: WindowManager.LayoutParams, view: View) {
        val metrics = context.resources.displayMetrics
        val viewWidth = view.width.takeIf { it > 0 } ?: 220
        val viewHeight = view.height.takeIf { it > 0 } ?: 80
        val maxX = (metrics.widthPixels - viewWidth).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - viewHeight).coerceAtLeast(0)
        params.x = params.x.coerceIn(0, maxX)
        params.y = params.y.coerceIn(0, maxY)
    }

    private class DraggableOverlayContainer(
        context: Context,
        private val onDragDelta: (dx: Int, dy: Int) -> Unit,
        private val onDragFinished: () -> Unit
    ) : FrameLayout(context) {

        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private var downRawX = 0f
        private var downRawY = 0f
        private var lastRawX = 0f
        private var lastRawY = 0f
        private var dragging = false

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = ev.rawX
                    downRawY = ev.rawY
                    lastRawX = ev.rawX
                    lastRawY = ev.rawY
                    dragging = false
                    return false
                }

                MotionEvent.ACTION_MOVE -> {
                    val totalDx = ev.rawX - downRawX
                    val totalDy = ev.rawY - downRawY
                    if (!dragging && (abs(totalDx) > touchSlop || abs(totalDy) > touchSlop)) {
                        dragging = true
                        lastRawX = ev.rawX
                        lastRawY = ev.rawY
                        return true
                    }
                    return dragging
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    return false
                }
            }
            return false
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    if (dragging) {
                        val dx = (event.rawX - lastRawX).toInt()
                        val dy = (event.rawY - lastRawY).toInt()
                        if (dx != 0 || dy != 0) {
                            lastRawX = event.rawX
                            lastRawY = event.rawY
                            onDragDelta(dx, dy)
                        }
                        return true
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        dragging = false
                        onDragFinished()
                        return true
                    }
                }
            }
            return super.onTouchEvent(event)
        }
    }

    companion object {
        private const val TAG = "OverlayWindowController"

        fun createOverlayLayoutParams(context: Context): WindowManager.LayoutParams {
            val density = context.resources.displayMetrics.density
            val isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            return WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = (16f * density + 0.5f).toInt()
                y = ((if (isLandscape) 16f else 96f) * density + 0.5f).toInt()
            }
        }
    }
}
