package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.track.F1SectorStatus
import com.example.track.KartingF1SectorAnalysis
import com.example.track.KartingLapSectorTiming
import com.example.track.KartingSectorSplit
import com.example.track.TrackFormatters
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.util.Locale

/**
 * Authentic Formula 1 timing tower color palette:
 * - Purple (刷紫): Overall session fastest sector / lap
 * - Green (刷绿): Personal best improvement over prior laps
 * - Yellow (刷黄): Slower than benchmark
 */
val F1PurpleColor = Color(0xFFC026D3)
val F1GreenColor = Color(0xFF22C55E)
val F1YellowColor = Color(0xFFEAB308)

fun f1StatusColor(status: F1SectorStatus): Color = when (status) {
    F1SectorStatus.PURPLE -> F1PurpleColor
    F1SectorStatus.GREEN -> F1GreenColor
    F1SectorStatus.YELLOW -> F1YellowColor
}

@Composable
fun KartingF1SectorCard(
    analysis: KartingF1SectorAnalysis,
    selectedLapNumber: Int?,
    onSelectLap: (Int) -> Unit,
    speedUnit: SpeedUnit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val activeTiming = analysis.timingForLap(selectedLapNumber) ?: return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("karting_f1_sector_card"),
        shape = RoundedCornerShape(24.dp),
        color = cs.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card Header + F1 Color Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(F1PurpleColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Flag,
                            contentDescription = null,
                            tint = F1PurpleColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.kart_f1_sector_title),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = cs.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    F1LegendDot(label = stringResource(R.string.kart_f1_legend_purple), color = F1PurpleColor)
                    F1LegendDot(label = stringResource(R.string.kart_f1_legend_green), color = F1GreenColor)
                    F1LegendDot(label = stringResource(R.string.kart_f1_legend_yellow), color = F1YellowColor)
                }
            }

            // Multi-lap Best vs Theoretical Best Lap Strip
            if (analysis.isMultiLap) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cs.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        F1SummaryStat(
                            label = stringResource(R.string.kart_f1_best_lap, analysis.bestLapNumber),
                            value = TrackFormatters.formatLapTime(analysis.bestLapDurationMs),
                            accent = F1PurpleColor,
                            modifier = Modifier.weight(1f)
                        )
                        F1SummaryStat(
                            label = stringResource(R.string.kart_f1_theoretical_best),
                            value = TrackFormatters.formatLapTime(analysis.theoreticalBestLapMs),
                            accent = F1GreenColor,
                            modifier = Modifier.weight(1f)
                        )
                        val gainText = if (analysis.potentialGainMs >= 10L) {
                            String.format(Locale.US, "-%.2fs", analysis.potentialGainMs / 1000.0)
                        } else {
                            stringResource(R.string.kart_f1_optimal_hit)
                        }
                        F1SummaryStat(
                            label = stringResource(R.string.kart_f1_potential_gain),
                            value = gainText,
                            accent = if (analysis.potentialGainMs >= 10L) F1YellowColor else F1PurpleColor,
                            modifier = Modifier.weight(0.9f)
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.kart_f1_single_run_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }

            // F1 Timing Tower Matrix: Lap | S1 | S2 | S3 | Lap Time
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(cs.surfaceContainer)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.kart_f1_col_lap),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.width(34.dp)
                    )
                    listOf("S1", "S2", "S3").forEach { secLabel ->
                        Text(
                            text = secLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = cs.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        text = stringResource(R.string.kart_f1_col_lap_time),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = cs.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(66.dp)
                    )
                }

                HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.45f))

                analysis.lapTimings.forEach { lap ->
                    val isSelected = lap.lapNumber == activeTiming.lapNumber
                    F1LapTimingRow(
                        lap = lap,
                        isMultiLap = analysis.isMultiLap,
                        isSelected = isSelected,
                        onClick = { onSelectLap(lap.lapNumber) }
                    )
                }
            }

            // Selected Lap Sector Breakdown (S1, S2, S3 telemetry mini-cards)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (analysis.isMultiLap) {
                        stringResource(R.string.kart_f1_selected_lap_detail, activeTiming.lapNumber)
                    } else {
                        stringResource(R.string.kart_f1_single_detail_header)
                    },
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = cs.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    activeTiming.sectors.forEach { split ->
                        F1SectorDetailTile(
                            split = split,
                            speedUnit = speedUnit,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (analysis.isMultiLap) {
                Text(
                    text = stringResource(R.string.kart_f1_tap_lap_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun F1LapTimingRow(
    lap: KartingLapSectorTiming,
    isMultiLap: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val lapColor = f1StatusColor(lap.lapStatus)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) cs.surfaceContainerHigh else Color.Transparent,
        border = if (isSelected) BorderStroke(1.2.dp, lapColor.copy(alpha = 0.7f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("f1_sector_lap_row_${lap.lapNumber}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Lap Badge
            Row(
                modifier = Modifier.width(34.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(lapColor)
                )
                Text(
                    text = if (isMultiLap) "L${lap.lapNumber}" else "全",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp
                    ),
                    color = if (isSelected) cs.onSurface else cs.onSurfaceVariant
                )
            }

            // S1, S2, S3 Cells
            lap.sectors.forEach { sector ->
                val secColor = f1StatusColor(sector.status)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = formatSectorSeconds(sector.durationMs),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp
                        ),
                        color = secColor,
                        maxLines = 1
                    )
                    if (isMultiLap) {
                        val deltaLabel = if (sector.status == F1SectorStatus.PURPLE && sector.deltaToBestMs <= 15L) {
                            sector.status.labelZh
                        } else {
                            String.format(Locale.US, "+%.2f", sector.deltaToBestMs / 1000.0)
                        }
                        Text(
                            text = deltaLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 9.5.sp
                            ),
                            color = secColor.copy(alpha = 0.85f),
                            maxLines = 1
                        )
                    }
                    // Authentic F1 TV colored underline bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.76f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(secColor)
                    )
                }
            }

            // Total Lap Time
            Column(
                modifier = Modifier.width(66.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = TrackFormatters.formatLapTime(lap.lapDurationMs),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp
                    ),
                    color = if (lap.lapStatus == F1SectorStatus.PURPLE) F1PurpleColor else cs.onSurface,
                    maxLines = 1
                )
                if (isMultiLap) {
                    Text(
                        text = lap.lapStatus.labelZh,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = lapColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun F1SectorDetailTile(
    split: KartingSectorSplit,
    speedUnit: SpeedUnit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val statusColor = f1StatusColor(split.status)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = cs.surfaceContainer,
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = split.label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = statusColor
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = statusColor.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = split.status.labelZh,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }

            Text(
                text = formatSectorSeconds(split.durationMs),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp
                ),
                color = cs.onSurface,
                maxLines = 1
            )

            Text(
                text = String.format(Locale.US, "%.0f m", split.distanceMeters),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 10.sp
                ),
                color = cs.onSurfaceVariant,
                maxLines = 1
            )

            Text(
                text = "均 ${TrackFormatters.formatSpeed(split.avgSpeedMps, speedUnit)}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 10.sp
                ),
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (split.peakLateralG > 0.05f) {
                Text(
                    text = String.format(Locale.US, "侧G %.2f", split.peakLateralG),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 10.sp
                    ),
                    color = cs.primary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun F1LegendDot(
    label: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun F1SummaryStat(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp
            ),
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatSectorSeconds(durationMs: Long): String {
    return String.format(Locale.US, "%.2fs", durationMs / 1000.0)
}
