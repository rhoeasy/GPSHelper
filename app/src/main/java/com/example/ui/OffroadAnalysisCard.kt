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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.track.ClimbDifficultyLevel
import com.example.track.OffroadCrossingAnalysis
import com.example.track.SurfaceComplexityLevel
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.util.Locale

/**
 * Material 3 card displayed on Off-road Crossing track detail screen presenting:
 * 1) Climb Difficulty Rating (S1-S4, score, max grade, avg ascent grade, climb density, steep ratio)
 * 2) Surface / Terrain Complexity Analysis (T1-T4, 3-tier terrain breakdown bar, turns/km, hairpins, vibration, speed variability)
 */
@Composable
fun OffroadAnalysisCard(
    analysis: OffroadCrossingAnalysis,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val climb = analysis.climb
    val surface = analysis.surface

    val (climbBadgeBg, climbBadgeFg, climbAccent) = when (climb.level) {
        ClimbDifficultyLevel.EASY -> Triple(cs.secondaryContainer, cs.onSecondaryContainer, cs.secondary)
        ClimbDifficultyLevel.MODERATE -> Triple(cs.primaryContainer, cs.onPrimaryContainer, cs.primary)
        ClimbDifficultyLevel.CHALLENGING -> Triple(cs.tertiaryContainer, cs.onTertiaryContainer, cs.tertiary)
        ClimbDifficultyLevel.EXTREME -> Triple(cs.errorContainer, cs.onErrorContainer, cs.error)
    }

    val (surfaceBadgeBg, surfaceBadgeFg, surfaceAccent) = when (surface.level) {
        SurfaceComplexityLevel.SMOOTH -> Triple(cs.secondaryContainer, cs.onSecondaryContainer, cs.secondary)
        SurfaceComplexityLevel.TWISTY -> Triple(cs.primaryContainer, cs.onPrimaryContainer, cs.primary)
        SurfaceComplexityLevel.ROUGH -> Triple(cs.tertiaryContainer, cs.onTertiaryContainer, cs.tertiary)
        SurfaceComplexityLevel.EXTREME -> Triple(cs.errorContainer, cs.onErrorContainer, cs.error)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("offroad_analysis_card"),
        shape = RoundedCornerShape(24.dp),
        color = cs.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Climb Difficulty Rating
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                .background(climbBadgeBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Terrain,
                                contentDescription = null,
                                tint = climbBadgeFg,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.offroad_climb_title),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = cs.onSurface
                            )
                            Text(
                                text = stringResource(R.string.offroad_score_format, climb.score),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily
                                ),
                                color = cs.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = climbBadgeBg,
                        modifier = Modifier.testTag("offroad_climb_badge")
                    ) {
                        Text(
                            text = climb.level.fullLabel,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = climbBadgeFg,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }

                LinearProgressIndicator(
                    progress = { (climb.score / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = climbAccent,
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
                    AnalysisMiniStat(
                        label = stringResource(R.string.offroad_metric_max_grade),
                        value = String.format(Locale.US, "%.1f%%", climb.maxGradePct),
                        accent = climbAccent,
                        modifier = Modifier.weight(1f)
                    )
                    AnalysisMiniStat(
                        label = stringResource(R.string.offroad_metric_avg_climb_grade),
                        value = String.format(Locale.US, "%.1f%%", climb.avgClimbGradePct),
                        accent = cs.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    AnalysisMiniStat(
                        label = stringResource(R.string.offroad_metric_gain_density),
                        value = String.format(Locale.US, "%.0f m/km", climb.gainPerKmMeters),
                        accent = cs.primary,
                        modifier = Modifier.weight(1f)
                    )
                    AnalysisMiniStat(
                        label = stringResource(R.string.offroad_metric_steep_ratio),
                        value = "${climb.steepDistanceRatioPct}%",
                        accent = cs.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    text = climb.summaryZh,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }

            HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.6f))

            // Section 2: Surface / Terrain Complexity Analysis
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                .background(surfaceBadgeBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AltRoute,
                                contentDescription = null,
                                tint = surfaceBadgeFg,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.offroad_surface_title),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = cs.onSurface
                            )
                            Text(
                                text = stringResource(R.string.offroad_score_format, surface.score),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily
                                ),
                                color = cs.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = surfaceBadgeBg,
                        modifier = Modifier.testTag("offroad_surface_badge")
                    ) {
                        Text(
                            text = surface.level.fullLabel,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = surfaceBadgeFg,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }

                // 3-tier segmented terrain distribution bar
                TerrainBreakdownBar(
                    smoothPct = surface.smoothPct,
                    twistyPct = surface.twistyPct,
                    technicalPct = surface.technicalPct
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cs.surfaceContainer)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    AnalysisMiniStat(
                        label = stringResource(R.string.offroad_metric_turns_per_km),
                        value = String.format(Locale.US, "%.1f/km", surface.turnsPerKm),
                        accent = cs.primary,
                        modifier = Modifier.weight(1f)
                    )
                    AnalysisMiniStat(
                        label = stringResource(R.string.offroad_metric_hairpins),
                        value = "${surface.hairpinCount} 处",
                        accent = surfaceAccent,
                        modifier = Modifier.weight(1f)
                    )
                    AnalysisMiniStat(
                        label = stringResource(R.string.offroad_metric_vibration),
                        value = String.format(Locale.US, "%.2f G", surface.vibrationIndexG),
                        accent = cs.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                    AnalysisMiniStat(
                        label = stringResource(R.string.offroad_metric_speed_cv),
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
    }
}

@Composable
private fun TerrainBreakdownBar(
    smoothPct: Int,
    twistyPct: Int,
    technicalPct: Int
) {
    val cs = MaterialTheme.colorScheme
    val smoothColor = cs.secondary
    val twistyColor = cs.primary
    val technicalColor = cs.tertiary

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(cs.surfaceContainerHighest),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (smoothPct > 0) {
                Box(
                    modifier = Modifier
                        .weight(smoothPct.toFloat())
                        .fillMaxHeight()
                        .background(smoothColor)
                )
            }
            if (twistyPct > 0) {
                Box(
                    modifier = Modifier
                        .weight(twistyPct.toFloat())
                        .fillMaxHeight()
                        .background(twistyColor)
                )
            }
            if (technicalPct > 0) {
                Box(
                    modifier = Modifier
                        .weight(technicalPct.toFloat())
                        .fillMaxHeight()
                        .background(technicalColor)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TerrainLegendItem(
                color = smoothColor,
                label = "${stringResource(R.string.offroad_terrain_smooth)} $smoothPct%"
            )
            TerrainLegendItem(
                color = twistyColor,
                label = "${stringResource(R.string.offroad_terrain_twisty)} $twistyPct%"
            )
            TerrainLegendItem(
                color = technicalColor,
                label = "${stringResource(R.string.offroad_terrain_technical)} $technicalPct%"
            )
        }
    }
}

@Composable
private fun TerrainLegendItem(
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
private fun AnalysisMiniStat(
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
