package com.example.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SportsScore
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ble.BleConnectionStatus
import com.example.ble.HeartRateState
import com.example.gps.SpeedUnit
import com.example.track.ActivityMode
import com.example.track.RecordingSnapshot
import com.example.track.RecordingStatus
import com.example.track.TrackFormatters
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalTelemetryColors
import java.util.Locale

@Composable
fun TrackRecorderCard(
    snapshot: RecordingSnapshot,
    heartRateState: HeartRateState,
    onOpenBlePicker: () -> Unit,
    onSelectMode: (ActivityMode) -> Unit,
    onStartRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onFinishRecording: () -> Unit,
    onSetStartGate: () -> Unit,
    speedUnit: SpeedUnit = SpeedUnit.KMH,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val recTransition = rememberInfiniteTransition(label = "rec_blink")
    val recAlpha by recTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rec_dot_alpha"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("track_recorder_card"),
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (snapshot.isActive) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(
                                    if (snapshot.status == RecordingStatus.RECORDING) {
                                        cs.error.copy(alpha = recAlpha)
                                    } else {
                                        cs.tertiary
                                    }
                                )
                        )
                        Text(
                            text = stringResource(
                                when (snapshot.status) {
                                    RecordingStatus.RECORDING -> R.string.record_status_recording
                                    RecordingStatus.SAVING -> R.string.record_status_saving
                                    else -> R.string.record_status_paused
                                }
                            ),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (snapshot.status == RecordingStatus.RECORDING) cs.error else cs.tertiary
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.record_card_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = cs.onSurface
                        )
                    }
                }

                if (snapshot.isActive) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = cs.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = activityModeIcon(snapshot.mode),
                                contentDescription = null,
                                tint = cs.onPrimaryContainer,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = snapshot.mode.labelZh,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = cs.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            if (!snapshot.isActive) {
                ActivityModeSelector(
                    selectedMode = snapshot.mode,
                    enabled = true,
                    onSelectMode = onSelectMode
                )

                Text(
                    text = stringResource(
                        when (snapshot.mode) {
                            ActivityMode.KARTING -> R.string.record_hint_karting
                            ActivityMode.HIKING -> R.string.record_hint_hiking
                            ActivityMode.OFFROAD_CROSSING -> R.string.record_hint_offroad
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp),
                    color = cs.onSurfaceVariant
                )

                HeartRateFusionRow(
                    heartRateState = heartRateState,
                    peakTotalG = null,
                    onOpenBlePicker = onOpenBlePicker
                )

                Button(
                    onClick = onStartRecording,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_start_recording"),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = cs.primary,
                        contentColor = cs.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FiberManualRecord,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${stringResource(R.string.record_start)} · ${snapshot.mode.labelZh}",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            } else {
                // Live primary metrics strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cs.surfaceContainer)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    LiveStatPill(
                        label = stringResource(R.string.metric_duration),
                        value = TrackFormatters.formatDuration(snapshot.elapsedMs),
                        accent = cs.onSurface
                    )
                    LiveStatPill(
                        label = stringResource(R.string.metric_distance),
                        value = TrackFormatters.formatDistance(snapshot.distanceMeters),
                        accent = cs.primary
                    )
                    if (snapshot.mode == ActivityMode.KARTING) {
                        LiveStatPill(
                            label = stringResource(R.string.metric_best_lap),
                            value = TrackFormatters.formatLapTime(snapshot.bestLapMs),
                            accent = cs.secondary
                        )
                    } else {
                        LiveStatPill(
                            label = stringResource(R.string.metric_elevation_gain),
                            value = TrackFormatters.formatElevationGain(snapshot.elevationGainMeters),
                            accent = cs.secondary
                        )
                    }
                }

                if (snapshot.mode == ActivityMode.HIKING && snapshot.stepCount != null) {
                    val cadence = TrackFormatters.averageStepsPerMinute(snapshot.stepCount, snapshot.elapsedMs)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(cs.surfaceContainer)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(32.dp)
                    ) {
                        LiveStatPill(
                            label = stringResource(R.string.metric_step_count),
                            value = stringResource(R.string.metric_step_count_value, snapshot.stepCount),
                            accent = cs.primary
                        )
                        LiveStatPill(
                            label = stringResource(R.string.metric_avg_step_cadence),
                            value = cadence?.let { stringResource(R.string.metric_step_cadence_value, it) } ?: "--",
                            accent = cs.secondary
                        )
                    }
                }

                // Mode-specific secondary strip
                if (snapshot.mode == ActivityMode.KARTING) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(cs.tertiaryContainer.copy(alpha = 0.55f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "${stringResource(R.string.metric_current_lap)} L${snapshot.currentLapNumber}",
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onTertiaryContainer
                            )
                            Text(
                                text = TrackFormatters.formatLapTime(snapshot.currentLapElapsedMs),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = cs.onSurface
                            )
                        }

                        FilledTonalButton(
                            onClick = onSetStartGate,
                            modifier = Modifier.testTag("btn_set_start_gate"),
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = cs.surfaceContainerLow,
                                contentColor = cs.onTertiaryContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Flag,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.record_set_gate),
                                style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp)
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(cs.secondaryContainer.copy(alpha = 0.45f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        LiveStatPill(
                            label = stringResource(R.string.metric_elevation_loss),
                            value = TrackFormatters.formatElevationLoss(snapshot.elevationLossMeters),
                            accent = cs.onSurface
                        )
                        LiveStatPill(
                            label = stringResource(R.string.metric_max_altitude),
                            value = TrackFormatters.formatAltitude(snapshot.maxAltitudeMeters),
                            accent = cs.primary
                        )
                        if (snapshot.mode == ActivityMode.OFFROAD_CROSSING) {
                            LiveStatPill(
                                label = stringResource(R.string.metric_avg_speed),
                                value = TrackFormatters.formatSpeed(snapshot.avgSpeedMps, speedUnit),
                                accent = cs.onSecondaryContainer
                            )
                        } else {
                            LiveStatPill(
                                label = stringResource(R.string.metric_avg_pace),
                                value = TrackFormatters.formatPaceMinPerKm(
                                    snapshot.distanceMeters,
                                    snapshot.elapsedMs
                                ),
                                accent = cs.onSecondaryContainer
                            )
                        }
                    }
                }

                HeartRateFusionRow(
                    heartRateState = heartRateState,
                    peakTotalG = snapshot.peakTotalG.takeIf { it > 0.05f },
                    onOpenBlePicker = onOpenBlePicker
                )

                // Pause / Resume + Finish & Save
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val isPaused = snapshot.status == RecordingStatus.PAUSED
                    FilledTonalButton(
                        onClick = { if (isPaused) onResumeRecording() else onPauseRecording() },
                        enabled = snapshot.status != RecordingStatus.SAVING,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_pause_resume_recording"),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = cs.surfaceContainerHigh,
                            contentColor = cs.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(if (isPaused) R.string.record_resume else R.string.record_pause),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    Button(
                        onClick = onFinishRecording,
                        enabled = snapshot.status != RecordingStatus.SAVING,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_finish_recording"),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = cs.error,
                            contentColor = cs.onError
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.record_finish),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

internal fun activityModeIcon(mode: ActivityMode) = when (mode) {
    ActivityMode.KARTING -> Icons.Rounded.SportsScore
    ActivityMode.HIKING -> Icons.Rounded.Hiking
    ActivityMode.OFFROAD_CROSSING -> Icons.Rounded.TwoWheeler
}

@Composable
private fun ActivityModeSelector(
    selectedMode: ActivityMode,
    enabled: Boolean,
    onSelectMode: (ActivityMode) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(cs.surfaceContainer)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ActivityMode.entries.forEach { mode ->
            val selected = mode == selectedMode
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) cs.primaryContainer else Color.Transparent)
                    .clickable(enabled = enabled) { onSelectMode(mode) }
                    .padding(horizontal = 8.dp, vertical = 9.dp)
                    .testTag("mode_chip_${mode.name.lowercase()}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
            ) {
                Icon(
                    imageVector = activityModeIcon(mode),
                    contentDescription = null,
                    tint = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = mode.labelZh,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        fontSize = 12.sp
                    ),
                    color = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun LiveStatPill(
    label: String,
    value: String,
    accent: Color
) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            ),
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HeartRateFusionRow(
    heartRateState: HeartRateState,
    peakTotalG: Float?,
    onOpenBlePicker: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val tc = LocalTelemetryColors.current
    val isConnected = heartRateState.isConnected
    val isConnecting = heartRateState.status == BleConnectionStatus.CONNECTING

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isConnected) tc.heartContainer.copy(alpha = 0.7f)
                else cs.surfaceContainer
            )
            .clickable(enabled = !isConnected && !isConnecting, onClick = onOpenBlePicker)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("recorder_hr_fusion_row"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = if (isConnected) Icons.Rounded.Favorite else Icons.Rounded.BluetoothSearching,
                contentDescription = null,
                tint = if (isConnected) tc.heart else cs.onSurfaceVariant,
                modifier = Modifier.size(15.dp)
            )
            val labelText = when {
                isConnected -> {
                    val bpmStr = heartRateState.bpm?.toString() ?: "--"
                    "$bpmStr bpm · ${stringResource(R.string.record_hr_fused)}"
                }
                isConnecting -> stringResource(R.string.record_hr_connecting)
                else -> stringResource(R.string.record_hr_not_connected)
            }
            Text(
                text = labelText,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = if (isConnected) JetBrainsMonoFontFamily else MaterialTheme.typography.labelMedium.fontFamily,
                    fontWeight = if (isConnected) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = 12.sp
                ),
                color = if (isConnected) tc.heart else cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (peakTotalG != null) {
            Text(
                text = "${stringResource(R.string.metric_peak_g)} ${String.format(Locale.US, "%.2f", peakTotalG)} G",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.SemiBold
                ),
                color = cs.primary
            )
        }
    }
}
