package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.SportsScore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.gps.SpeedUnit
import com.example.track.ActivityMode
import com.example.track.TrackFormatters
import com.example.track.TrackLapEntity
import com.example.track.TrackSessionDetail
import com.example.track.TrackSessionEntity
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalTelemetryColors
import java.util.Locale

@Composable
fun TrackHistoryScreen(
    sessions: List<TrackSessionEntity>,
    speedUnit: SpeedUnit,
    onSelectSession: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    var filterMode by remember { mutableStateOf<ActivityMode?>(null) }
    val filtered = remember(sessions, filterMode) {
        if (filterMode == null) sessions else sessions.filter { it.activityMode == filterMode }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.tab_tracks),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 22.sp
                ),
                color = cs.onSurface
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filterMode == null,
                    onClick = { filterMode = null },
                    label = { Text(stringResource(R.string.filter_all)) },
                    shape = RoundedCornerShape(50)
                )
                FilterChip(
                    selected = filterMode == ActivityMode.KARTING,
                    onClick = { filterMode = ActivityMode.KARTING },
                    label = { Text(stringResource(R.string.mode_karting)) },
                    shape = RoundedCornerShape(50)
                )
                FilterChip(
                    selected = filterMode == ActivityMode.HIKING,
                    onClick = { filterMode = ActivityMode.HIKING },
                    label = { Text(stringResource(R.string.mode_hiking)) },
                    shape = RoundedCornerShape(50)
                )
            }

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(horizontal = 28.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(cs.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Route,
                                contentDescription = null,
                                tint = cs.onPrimaryContainer,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.tracks_empty_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = cs.onSurface
                        )
                        Text(
                            text = stringResource(R.string.tracks_empty_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filtered, key = { it.id }) { session ->
                        TrackSessionCard(
                            session = session,
                            speedUnit = speedUnit,
                            onClick = { onSelectSession(session.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackSessionCard(
    session: TrackSessionEntity,
    speedUnit: SpeedUnit,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val tc = LocalTelemetryColors.current
    val isKart = session.activityMode == ActivityMode.KARTING
    val badgeBg = if (isKart) cs.tertiaryContainer else cs.secondaryContainer
    val badgeFg = if (isKart) cs.onTertiaryContainer else cs.onSecondaryContainer

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .testTag("track_session_card_${session.id}"),
        shape = RoundedCornerShape(22.dp),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = badgeBg
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isKart) Icons.Rounded.SportsScore else Icons.Rounded.Hiking,
                                contentDescription = null,
                                tint = badgeFg,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = session.activityMode.labelZh,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = badgeFg
                            )
                        }
                    }

                    Text(
                        text = session.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = cs.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SessionStatItem(
                    label = stringResource(R.string.metric_distance),
                    value = TrackFormatters.formatDistance(session.distanceMeters)
                )
                SessionStatItem(
                    label = stringResource(R.string.metric_duration),
                    value = TrackFormatters.formatDuration(session.durationMs)
                )
                if (isKart) {
                    SessionStatItem(
                        label = stringResource(R.string.metric_best_lap),
                        value = TrackFormatters.formatLapTime(session.bestLapMs),
                        accent = cs.tertiary
                    )
                } else {
                    SessionStatItem(
                        label = stringResource(R.string.metric_elevation_gain),
                        value = TrackFormatters.formatElevationGain(session.elevationGainMeters),
                        accent = cs.secondary
                    )
                }
                SessionStatItem(
                    label = stringResource(R.string.metric_max_speed),
                    value = TrackFormatters.formatSpeed(session.maxSpeedMps, speedUnit),
                    accent = cs.primary
                )
            }

            if (session.avgHeartRate != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Favorite,
                        contentDescription = null,
                        tint = tc.heart,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "${stringResource(R.string.metric_avg_hr)} ${session.avgHeartRate} bpm" +
                            (session.maxHeartRate?.let { "  ·  ${stringResource(R.string.metric_max_hr)} $it bpm" } ?: ""),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        color = cs.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionStatItem(
    label: String,
    value: String,
    accent: Color? = null
) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.5.sp
            ),
            color = accent ?: cs.onSurface
        )
    }
}

@Composable
fun TrackDetailScreen(
    detail: TrackSessionDetail,
    speedUnit: SpeedUnit,
    isFocusLockActive: Boolean,
    onExitFocusLock: () -> Unit,
    onBack: () -> Unit,
    onExportJson: () -> Unit,
    onExportGpx: () -> Unit,
    onDeleteSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val cs = MaterialTheme.colorScheme
    val tc = LocalTelemetryColors.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val session = detail.session
    val isKart = session.activityMode == ActivityMode.KARTING

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(text = stringResource(R.string.detail_delete_title)) },
            text = { Text(text = stringResource(R.string.detail_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteSession()
                    }
                ) {
                    Text(stringResource(R.string.detail_delete), color = cs.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.detail_cancel))
                }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(cs.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (isFocusLockActive) {
                FocusActiveBanner(onExitFocusLock = onExitFocusLock)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("btn_detail_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.detail_back_cd),
                        tint = cs.onSurface
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = cs.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = TrackFormatters.formatDateTime(session.startTimeMs),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        color = cs.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.testTag("btn_delete_session")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.detail_delete),
                        tint = cs.error
                    )
                }
            }

            // Dual export bar: Structured JSON for AI Agents + GPX for maps
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onExportJson,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_export_json"),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = cs.primary,
                        contentColor = cs.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DataObject,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.detail_export_json),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                FilledTonalButton(
                    onClick = onExportGpx,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_export_gpx"),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = cs.secondaryContainer,
                        contentColor = cs.onSecondaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.IosShare,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.detail_export_gpx),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            VectorTrackCanvasCard(
                points = detail.points,
                mode = session.activityMode,
                speedUnit = speedUnit
            )

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
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DetailMetricCell(
                            label = stringResource(R.string.metric_distance),
                            value = TrackFormatters.formatDistance(session.distanceMeters),
                            accent = cs.primary,
                            modifier = Modifier.weight(1f)
                        )
                        DetailMetricCell(
                            label = stringResource(R.string.metric_duration),
                            value = TrackFormatters.formatDuration(session.durationMs),
                            accent = cs.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        DetailMetricCell(
                            label = stringResource(R.string.metric_max_speed),
                            value = TrackFormatters.formatSpeed(session.maxSpeedMps, speedUnit),
                            accent = cs.tertiary,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.6f))

                    if (isKart) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            DetailMetricCell(
                                label = stringResource(R.string.metric_lap_count),
                                value = "${session.lapCount} 圈",
                                accent = cs.tertiary,
                                modifier = Modifier.weight(1f)
                            )
                            DetailMetricCell(
                                label = stringResource(R.string.metric_best_lap),
                                value = TrackFormatters.formatLapTime(session.bestLapMs),
                                accent = cs.secondary,
                                modifier = Modifier.weight(1f)
                            )
                            DetailMetricCell(
                                label = stringResource(R.string.metric_avg_speed),
                                value = TrackFormatters.formatSpeed(session.avgSpeedMps, speedUnit),
                                accent = cs.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            DetailMetricCell(
                                label = stringResource(R.string.metric_elevation_gain),
                                value = TrackFormatters.formatElevationGain(session.elevationGainMeters),
                                accent = cs.secondary,
                                modifier = Modifier.weight(1f)
                            )
                            DetailMetricCell(
                                label = stringResource(R.string.metric_elevation_loss),
                                value = TrackFormatters.formatElevationLoss(session.elevationLossMeters),
                                accent = cs.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            DetailMetricCell(
                                label = stringResource(R.string.metric_avg_pace),
                                value = TrackFormatters.formatPaceMinPerKm(
                                    session.distanceMeters,
                                    session.durationMs
                                ),
                                accent = cs.tertiary,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.6f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            DetailMetricCell(
                                label = stringResource(R.string.metric_max_altitude),
                                value = TrackFormatters.formatAltitude(session.maxAltitudeMeters),
                                accent = cs.primary,
                                modifier = Modifier.weight(1f)
                            )
                            DetailMetricCell(
                                label = stringResource(R.string.metric_min_altitude),
                                value = TrackFormatters.formatAltitude(session.minAltitudeMeters),
                                accent = cs.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            DetailMetricCell(
                                label = stringResource(R.string.metric_avg_speed),
                                value = TrackFormatters.formatSpeed(session.avgSpeedMps, speedUnit),
                                accent = cs.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (session.avgHeartRate != null || session.maxHeartRate != null || session.peakTotalG > 0.05f) {
                        HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.6f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            DetailMetricCell(
                                label = stringResource(R.string.metric_avg_hr),
                                value = session.avgHeartRate?.let { "$it bpm" } ?: "--",
                                accent = tc.heart,
                                modifier = Modifier.weight(1f)
                            )
                            DetailMetricCell(
                                label = stringResource(R.string.metric_max_hr),
                                value = session.maxHeartRate?.let { "$it bpm" } ?: "--",
                                accent = tc.heart,
                                modifier = Modifier.weight(1f)
                            )
                            DetailMetricCell(
                                label = stringResource(R.string.metric_peak_g),
                                value = if (session.peakTotalG > 0.05f) {
                                    String.format(Locale.US, "%.2f G", session.peakTotalG)
                                } else {
                                    "--"
                                },
                                accent = cs.primary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            TelemetryProfileChartCard(
                points = detail.points,
                mode = session.activityMode,
                speedUnit = speedUnit
            )

            if (isKart) {
                KartingLapSplitsCard(
                    laps = detail.laps,
                    bestLapMs = session.bestLapMs,
                    speedUnit = speedUnit
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun DetailMetricCell(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp)
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
                fontSize = 15.sp
            ),
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun KartingLapSplitsCard(
    laps: List<TrackLapEntity>,
    bestLapMs: Long?,
    speedUnit: SpeedUnit
) {
    val cs = MaterialTheme.colorScheme
    val tc = LocalTelemetryColors.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = cs.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.detail_laps_title),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = cs.onSurface
            )

            if (laps.isEmpty()) {
                Text(
                    text = stringResource(R.string.detail_no_laps),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
            } else {
                val referenceBest = bestLapMs ?: laps.minOf { it.lapDurationMs }
                laps.forEach { lap ->
                    val isBest = lap.lapDurationMs <= referenceBest + 10L
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isBest) cs.secondaryContainer.copy(alpha = 0.65f)
                                else cs.surfaceContainer
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "L${lap.lapNumber}",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = if (isBest) cs.onSecondaryContainer else cs.primary
                            )
                            Text(
                                text = TrackFormatters.formatLapTime(lap.lapDurationMs),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = cs.onSurface
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = TrackFormatters.formatSpeed(lap.maxSpeedMps, speedUnit),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily
                                ),
                                color = cs.onSurfaceVariant
                            )
                            if (lap.avgHeartRate != null) {
                                Text(
                                    text = "♥${lap.avgHeartRate}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily
                                    ),
                                    color = tc.heart
                                )
                            }
                            if (lap.peakG > 0.05f) {
                                Text(
                                    text = String.format(Locale.US, "%.1fG", lap.peakG),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily
                                    ),
                                    color = cs.primary
                                )
                            }
                            Text(
                                text = TrackFormatters.formatLapDelta(lap.lapDurationMs, referenceBest),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = if (isBest) cs.onSecondaryContainer else cs.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
