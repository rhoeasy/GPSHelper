package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AltRoute
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.track.BodyPerformanceAnalysis
import com.example.track.BodyPerformanceLevel
import com.example.track.HikeDifficultyLevel
import com.example.track.HikingTrackAnalysis
import com.example.track.KartDifficultyLevel
import com.example.track.KartingTrackAnalysis
import com.example.track.SurfaceComplexityAnalysis
import com.example.track.SurfaceComplexityLevel
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalTelemetryColors
import java.util.Locale

/**
 * Material 3 analysis card for Hiking track detail screen:
 * 1) Hike Difficulty Rating (H1-H4)
 * 2) Trail Surface Complexity (T1-T4)
 * 3) Physiological Body Performance (P1-P4, HR zones / calories / pace stability)
 */
@Composable
fun HikingAnalysisCard(
    analysis: HikingTrackAnalysis,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val diff = analysis.difficulty

    val (diffBg, diffFg, diffAccent) = when (diff.level) {
        HikeDifficultyLevel.EASY -> Triple(cs.secondaryContainer, cs.onSecondaryContainer, cs.secondary)
        HikeDifficultyLevel.MODERATE -> Triple(cs.primaryContainer, cs.onPrimaryContainer, cs.primary)
        HikeDifficultyLevel.CHALLENGING -> Triple(cs.tertiaryContainer, cs.onTertiaryContainer, cs.tertiary)
        HikeDifficultyLevel.EXTREME -> Triple(cs.errorContainer, cs.onErrorContainer, cs.error)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("hiking_analysis_card"),
        shape = RoundedCornerShape(24.dp),
        color = cs.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Hiking Route Difficulty
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AnalysisSectionHeader(
                    icon = Icons.Rounded.Terrain,
                    title = stringResource(R.string.hike_difficulty_title),
                    score = diff.score,
                    badgeText = diff.level.fullLabel,
                    badgeBg = diffBg,
                    badgeFg = diffFg,
                    badgeTag = "hiking_difficulty_badge"
                )

                LinearProgressIndicator(
                    progress = { (diff.score / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = diffAccent,
                    trackColor = cs.surfaceContainerHighest,
                    strokeCap = StrokeCap.Round
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cs.surfaceContainer)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TrackAnalysisMiniStat(
                        label = stringResource(R.string.offroad_metric_max_grade),
                        value = String.format(Locale.US, "%.1f%%", diff.maxGradePct),
                        accent = diffAccent,
                        modifier = Modifier.weight(1f)
                    )
                    TrackAnalysisMiniStat(
                        label = stringResource(R.string.offroad_metric_avg_climb_grade),
                        value = String.format(Locale.US, "%.1f%%", diff.avgClimbGradePct),
                        accent = cs.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    TrackAnalysisMiniStat(
                        label = stringResource(R.string.offroad_metric_gain_density),
                        value = String.format(Locale.US, "%.0f m/km", diff.gainPerKmMeters),
                        accent = cs.primary,
                        modifier = Modifier.weight(1f)
                    )
                    TrackAnalysisMiniStat(
                        label = stringResource(R.string.offroad_metric_steep_ratio),
                        value = "${diff.steepDistanceRatioPct}%",
                        accent = cs.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    text = diff.summaryZh,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }

            HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.6f))

            // Section 2: Trail Surface Complexity
            HikingSurfaceComplexitySection(surface = analysis.surface)

            HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.6f))

            // Section 3: Body Performance Statistics
            BodyPerformanceSection(
                body = analysis.body,
                isKarting = false
            )
        }
    }
}

/**
 * Material 3 analysis card for Karting track detail screen:
 * 1) Track Difficulty Rating (R1-R4)
 * 2) Physiological Body Performance (P1-P4, HR zones / lap consistency / calories)
 */
@Composable
fun KartingAnalysisCard(
    analysis: KartingTrackAnalysis,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val diff = analysis.difficulty

    val (diffBg, diffFg, diffAccent) = when (diff.level) {
        KartDifficultyLevel.EASY -> Triple(cs.secondaryContainer, cs.onSecondaryContainer, cs.secondary)
        KartDifficultyLevel.MODERATE -> Triple(cs.primaryContainer, cs.onPrimaryContainer, cs.primary)
        KartDifficultyLevel.CHALLENGING -> Triple(cs.tertiaryContainer, cs.onTertiaryContainer, cs.tertiary)
        KartDifficultyLevel.EXTREME -> Triple(cs.errorContainer, cs.onErrorContainer, cs.error)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("karting_analysis_card"),
        shape = RoundedCornerShape(24.dp),
        color = cs.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Karting Track Difficulty
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AnalysisSectionHeader(
                    icon = Icons.Rounded.Speed,
                    title = stringResource(R.string.kart_difficulty_title),
                    score = diff.score,
                    badgeText = diff.level.fullLabel,
                    badgeBg = diffBg,
                    badgeFg = diffFg,
                    badgeTag = "karting_difficulty_badge"
                )

                LinearProgressIndicator(
                    progress = { (diff.score / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = diffAccent,
                    trackColor = cs.surfaceContainerHighest,
                    strokeCap = StrokeCap.Round
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cs.surfaceContainer)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TrackAnalysisMiniStat(
                        label = stringResource(R.string.metric_peak_lat_g),
                        value = String.format(Locale.US, "%.2f G", diff.peakLateralG),
                        accent = diffAccent,
                        modifier = Modifier.weight(1f)
                    )
                    TrackAnalysisMiniStat(
                        label = stringResource(R.string.kart_metric_peak_brake_g),
                        value = String.format(Locale.US, "%.2f G", diff.peakBrakingG),
                        accent = cs.error,
                        modifier = Modifier.weight(1f)
                    )
                    TrackAnalysisMiniStat(
                        label = stringResource(R.string.offroad_metric_turns_per_km),
                        value = String.format(Locale.US, "%.1f/km", diff.turnsPerKm),
                        accent = cs.primary,
                        modifier = Modifier.weight(1f)
                    )
                    TrackAnalysisMiniStat(
                        label = stringResource(R.string.offroad_metric_hairpins),
                        value = "${diff.hairpinCount} 处",
                        accent = cs.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    text = diff.summaryZh,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }

            HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.6f))

            // Section 2: Body Performance Statistics
            BodyPerformanceSection(
                body = analysis.body,
                isKarting = true
            )
        }
    }
}

@Composable
private fun HikingSurfaceComplexitySection(
    surface: SurfaceComplexityAnalysis
) {
    val cs = MaterialTheme.colorScheme
    val (surfaceBg, surfaceFg, surfaceAccent) = when (surface.level) {
        SurfaceComplexityLevel.SMOOTH -> Triple(cs.secondaryContainer, cs.onSecondaryContainer, cs.secondary)
        SurfaceComplexityLevel.TWISTY -> Triple(cs.primaryContainer, cs.onPrimaryContainer, cs.primary)
        SurfaceComplexityLevel.ROUGH -> Triple(cs.tertiaryContainer, cs.onTertiaryContainer, cs.tertiary)
        SurfaceComplexityLevel.EXTREME -> Triple(cs.errorContainer, cs.onErrorContainer, cs.error)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AnalysisSectionHeader(
            icon = Icons.Rounded.AltRoute,
            title = stringResource(R.string.offroad_surface_title),
            score = surface.score,
            badgeText = surface.level.fullLabel,
            badgeBg = surfaceBg,
            badgeFg = surfaceFg,
            badgeTag = "hiking_surface_badge"
        )

        SegmentedThreeTierBar(
            firstPct = surface.smoothPct,
            secondPct = surface.twistyPct,
            thirdPct = surface.technicalPct,
            firstColor = cs.secondary,
            secondColor = cs.primary,
            thirdColor = cs.tertiary,
            firstLabel = "${stringResource(R.string.hike_terrain_smooth)} ${surface.smoothPct}%",
            secondLabel = "${stringResource(R.string.hike_terrain_trail)} ${surface.twistyPct}%",
            thirdLabel = "${stringResource(R.string.hike_terrain_technical)} ${surface.technicalPct}%"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(cs.surfaceContainer)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TrackAnalysisMiniStat(
                label = stringResource(R.string.offroad_metric_turns_per_km),
                value = String.format(Locale.US, "%.1f/km", surface.turnsPerKm),
                accent = cs.primary,
                modifier = Modifier.weight(1f)
            )
            TrackAnalysisMiniStat(
                label = stringResource(R.string.hike_metric_switchbacks),
                value = "${surface.hairpinCount} 处",
                accent = surfaceAccent,
                modifier = Modifier.weight(1f)
            )
            TrackAnalysisMiniStat(
                label = stringResource(R.string.offroad_metric_vibration),
                value = String.format(Locale.US, "%.2f G", surface.vibrationIndexG),
                accent = cs.tertiary,
                modifier = Modifier.weight(1f)
            )
            TrackAnalysisMiniStat(
                label = stringResource(R.string.hike_metric_pace_cv),
                value = "${surface.speedVariabilityPct}%",
                accent = cs.onSurface,
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            text = surface.summaryZh,
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant
        )
    }
}

@Composable
private fun BodyPerformanceSection(
    body: BodyPerformanceAnalysis,
    isKarting: Boolean
) {
    val cs = MaterialTheme.colorScheme
    val tc = LocalTelemetryColors.current

    val (badgeBg, badgeFg, accent) = when (body.level) {
        BodyPerformanceLevel.RECOVERY -> Triple(cs.secondaryContainer, cs.onSecondaryContainer, cs.secondary)
        BodyPerformanceLevel.AEROBIC -> Triple(cs.primaryContainer, cs.onPrimaryContainer, cs.primary)
        BodyPerformanceLevel.INTENSE -> Triple(cs.tertiaryContainer, cs.onTertiaryContainer, cs.tertiary)
        BodyPerformanceLevel.PEAK -> Triple(tc.heartContainer, tc.heart, tc.heart)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AnalysisSectionHeader(
            icon = Icons.Rounded.Favorite,
            title = stringResource(R.string.body_perf_title),
            score = body.score,
            badgeText = body.level.fullLabel,
            badgeBg = badgeBg,
            badgeFg = badgeFg,
            badgeTag = if (isKarting) "karting_body_badge" else "hiking_body_badge"
        )

        if (body.hasHeartRateData) {
            SegmentedThreeTierBar(
                firstPct = body.easyZonePct,
                secondPct = body.aerobicZonePct,
                thirdPct = body.highZonePct,
                firstColor = cs.secondary,
                secondColor = cs.primary,
                thirdColor = tc.heart,
                firstLabel = "${stringResource(R.string.body_zone_easy)} ${body.easyZonePct}%",
                secondLabel = "${stringResource(R.string.body_zone_aerobic)} ${body.aerobicZonePct}%",
                thirdLabel = "${stringResource(R.string.body_zone_high)} ${body.highZonePct}%"
            )
        } else {
            LinearProgressIndicator(
                progress = { (body.score / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = accent,
                trackColor = cs.surfaceContainerHighest,
                strokeCap = StrokeCap.Round
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(cs.surfaceContainer)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TrackAnalysisMiniStat(
                label = stringResource(R.string.metric_avg_hr),
                value = body.avgHeartRate?.let { "$it bpm" } ?: "--",
                accent = tc.heart,
                modifier = Modifier.weight(1f)
            )
            TrackAnalysisMiniStat(
                label = stringResource(R.string.metric_max_hr),
                value = body.maxHeartRate?.let { "$it bpm" } ?: "--",
                accent = tc.heart,
                modifier = Modifier.weight(1f)
            )
            TrackAnalysisMiniStat(
                label = body.stabilityLabelZh,
                value = "${body.stabilityPct}%",
                accent = cs.primary,
                modifier = Modifier.weight(1f)
            )
            TrackAnalysisMiniStat(
                label = stringResource(R.string.body_metric_calories),
                value = "${body.estimatedCaloriesKcal} kcal",
                accent = cs.tertiary,
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            text = body.summaryZh,
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant
        )
    }
}

@Composable
private fun AnalysisSectionHeader(
    icon: ImageVector,
    title: String,
    score: Int,
    badgeText: String,
    badgeBg: Color,
    badgeFg: Color,
    badgeTag: String
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(badgeBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = badgeFg,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = cs.onSurface
                )
                Text(
                    text = stringResource(R.string.offroad_score_format, score),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFontFamily
                    ),
                    color = cs.onSurfaceVariant
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(50),
            color = badgeBg,
            modifier = Modifier.testTag(badgeTag)
        ) {
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = badgeFg,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun SegmentedThreeTierBar(
    firstPct: Int,
    secondPct: Int,
    thirdPct: Int,
    firstColor: Color,
    secondColor: Color,
    thirdColor: Color,
    firstLabel: String,
    secondLabel: String,
    thirdLabel: String
) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(cs.surfaceContainerHighest),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (firstPct > 0) {
                Box(
                    modifier = Modifier
                        .weight(firstPct.toFloat())
                        .fillMaxHeight()
                        .background(firstColor)
                )
            }
            if (secondPct > 0) {
                Box(
                    modifier = Modifier
                        .weight(secondPct.toFloat())
                        .fillMaxHeight()
                        .background(secondColor)
                )
            }
            if (thirdPct > 0) {
                Box(
                    modifier = Modifier
                        .weight(thirdPct.toFloat())
                        .fillMaxHeight()
                        .background(thirdColor)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TierLegendDot(color = firstColor, label = firstLabel)
            TierLegendDot(color = secondColor, label = secondLabel)
            TierLegendDot(color = thirdColor, label = thirdLabel)
        }
    }
}

@Composable
private fun TierLegendDot(
    color: Color,
    label: String
) {
    val cs = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 11.sp
            ),
            color = cs.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TrackAnalysisMiniStat(
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
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
            color = cs.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            ),
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
