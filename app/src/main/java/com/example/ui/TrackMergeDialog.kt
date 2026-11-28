package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.South
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.track.MergeJunctionQuality
import com.example.track.TrackFormatters
import com.example.track.TrackMergePreview
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.util.Locale

@Composable
fun MergeTracksConfirmDialog(
    preview: TrackMergePreview,
    isMerging: Boolean,
    onConfirm: (mergedName: String, keepOriginalSegments: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var mergedName by remember(preview) { mutableStateOf(preview.defaultMergedName) }
    var keepOriginals by remember(preview) { mutableStateOf(true) }
    ProvideAssistantSubpage {
        AssistantPageKind.MERGE.snapshot(
            "合并模式：${preview.mode.labelZh}；轨迹数：${preview.orderedSessions.size}；保留原始记录：$keepOriginals"
        )
    }

    AlertDialog(
        onDismissRequest = { if (!isMerging) onDismiss() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CallMerge,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = stringResource(
                        R.string.merge_dialog_title,
                        preview.mode.labelZh,
                        preview.orderedSessions.size
                    ),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f)
                )
                PageAssistantIcon()
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = mergedName,
                    onValueChange = { mergedName = it },
                    label = { Text(stringResource(R.string.merge_name_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_merged_track_name")
                )

                // Merged totals preview strip
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cs.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MergeSummaryStat(
                            label = stringResource(R.string.metric_distance),
                            value = TrackFormatters.formatDistance(preview.mergedDistanceMeters)
                        )
                        MergeSummaryStat(
                            label = stringResource(R.string.merge_active_duration),
                            value = TrackFormatters.formatDuration(preview.mergedDurationMs)
                        )
                        MergeSummaryStat(
                            label = stringResource(R.string.metric_elevation_gain),
                            value = TrackFormatters.formatElevationGain(preview.mergedElevationGainMeters)
                        )
                    }
                }

                // Chronological chain + end-to-start junction diagnostics
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.merge_chain_section_title),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = cs.onSurfaceVariant
                    )

                    preview.orderedSessions.forEachIndexed { idx, session ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = cs.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "${idx + 1}. ${session.name}",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = cs.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${TrackFormatters.formatDateTime(session.startTimeMs)} · ${TrackFormatters.formatDistance(session.distanceMeters)} · ${TrackFormatters.formatDuration(session.durationMs)}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily
                                    ),
                                    color = cs.onSurfaceVariant
                                )
                            }
                        }

                        if (idx < preview.junctions.size) {
                            val j = preview.junctions[idx]
                            val (jBg, jFg, jIcon) = when (j.quality) {
                                MergeJunctionQuality.SEAMLESS -> Triple(
                                    cs.secondaryContainer.copy(alpha = 0.7f),
                                    cs.onSecondaryContainer,
                                    Icons.Rounded.CheckCircle
                                )
                                MergeJunctionQuality.NEARBY -> Triple(
                                    cs.primaryContainer.copy(alpha = 0.7f),
                                    cs.onPrimaryContainer,
                                    Icons.Rounded.South
                                )
                                MergeJunctionQuality.FAR -> Triple(
                                    cs.errorContainer.copy(alpha = 0.7f),
                                    cs.onErrorContainer,
                                    Icons.Rounded.WarningAmber
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(jBg)
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = jIcon,
                                    contentDescription = null,
                                    tint = jFg,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "${j.quality.labelZh} · 终点→起点 ${formatGapMeters(j.gapMeters)}" +
                                        if (j.gapDurationMs >= 60_000L) {
                                            " · 间隔 ${formatGapDurationZh(j.gapDurationMs)}"
                                        } else {
                                            ""
                                        },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 11.sp
                                    ),
                                    color = jFg
                                )
                            }
                        }
                    }
                }

                if (preview.hasFarJunction) {
                    Text(
                        text = stringResource(R.string.merge_far_junction_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.error
                    )
                }

                // Non-destructive option: keep original track segments (default checked)
                Surface(
                    onClick = { keepOriginals = !keepOriginals },
                    shape = RoundedCornerShape(14.dp),
                    color = cs.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("row_keep_original_tracks")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Checkbox(
                            checked = keepOriginals,
                            onCheckedChange = { keepOriginals = it },
                            modifier = Modifier.testTag("checkbox_keep_original_tracks")
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.merge_keep_originals_title),
                                style = MaterialTheme.typography.labelLarge,
                                color = cs.onSurface
                            )
                            Text(
                                text = stringResource(R.string.merge_keep_originals_desc),
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(mergedName, keepOriginals) },
                enabled = !isMerging,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("btn_confirm_merge_tracks")
            ) {
                Text(
                    text = stringResource(
                        if (isMerging) R.string.merge_confirming else R.string.merge_confirm_action
                    ),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isMerging
            ) {
                Text(stringResource(R.string.detail_cancel))
            }
        }
    )
}

@Composable
private fun MergeSummaryStat(
    label: String,
    value: String
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
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

fun formatGapMeters(meters: Double): String {
    return if (meters >= 1000.0) {
        String.format(Locale.US, "%.2f km", meters / 1000.0)
    } else {
        String.format(Locale.US, "%.0f m", meters)
    }
}

private fun formatGapDurationZh(durationMs: Long): String {
    val totalMin = (durationMs / 60_000L).coerceAtLeast(1L)
    val hours = totalMin / 60L
    val mins = totalMin % 60L
    return when {
        hours >= 24L -> "${hours / 24L}天${hours % 24L}小时"
        hours > 0L -> "${hours}小时${mins}分"
        else -> "${mins}分钟"
    }
}
