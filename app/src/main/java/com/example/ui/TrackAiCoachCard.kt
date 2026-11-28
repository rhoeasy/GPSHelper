package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ai.AiAnalysisState
import com.example.ai.AiGatewayConfig
import com.example.ai.GatewayStage
import com.example.track.TrackFormatters
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun TrackAiCoachCard(
    sessionId: Long,
    gatewayConfig: AiGatewayConfig,
    analysisState: AiAnalysisState,
    onStartAnalysis: () -> Unit,
    onStopAnalysis: () -> Unit,
    onCopyReport: (String) -> Unit,
    onOpenAiSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme

    // Guard against displaying another session's stale state during transitions
    val scopedState = when (analysisState) {
        is AiAnalysisState.Streaming -> if (analysisState.sessionId == sessionId) analysisState else AiAnalysisState.Idle
        is AiAnalysisState.Success -> if (analysisState.sessionId == sessionId) analysisState else AiAnalysisState.Idle
        is AiAnalysisState.Error -> if (analysisState.sessionId == sessionId) analysisState else AiAnalysisState.Idle
        AiAnalysisState.Idle -> AiAnalysisState.Idle
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = cs.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = cs.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.ai_coach_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = cs.onSurface
                    )
                }

                when (scopedState) {
                    is AiAnalysisState.Streaming -> {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = cs.primaryContainer
                        ) {
                            Text(
                                text = stringResource(gatewayStageLabel(scopedState.stage)),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = cs.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    is AiAnalysisState.Success -> {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (scopedState.isFromCache) cs.secondaryContainer else cs.primaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.OfflinePin,
                                    contentDescription = null,
                                    tint = if (scopedState.isFromCache) cs.onSecondaryContainer else cs.onPrimaryContainer,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = stringResource(R.string.ai_coach_badge_cached),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (scopedState.isFromCache) cs.onSecondaryContainer else cs.onPrimaryContainer
                                )
                            }
                        }
                    }

                    else -> {
                        if (gatewayConfig.isConfigured) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = cs.surfaceContainerHigh
                            ) {
                                Text(
                                    text = gatewayConfig.modelId,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily
                                    ),
                                    color = cs.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            when (scopedState) {
                AiAnalysisState.Idle -> {
                    if (!gatewayConfig.isConfigured) {
                        Text(
                            text = stringResource(R.string.ai_coach_unconfigured_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant
                        )
                        FilledTonalButton(
                            onClick = onOpenAiSettings,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_ai_coach_open_settings")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.ai_coach_go_settings))
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.ai_coach_ready_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant
                        )
                        Button(
                            onClick = onStartAnalysis,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_ai_coach_start")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.ai_coach_start))
                        }
                    }
                }

                is AiAnalysisState.Streaming -> {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                    )

                    OutlinedButton(
                        onClick = onStopAnalysis,
                        enabled = scopedState.stage != GatewayStage.SAVING,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_ai_coach_stop")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.ai_coach_stop))
                    }
                    if (scopedState.partialText.isNotBlank()) {
                        CollapsibleCoachReport(scopedState.partialText, scopedState.sessionId)
                    }
                }

                is AiAnalysisState.Success -> {
                    Text(
                        text = "${stringResource(R.string.ai_coach_meta_model, scopedState.modelId)}  ·  ${TrackFormatters.formatDateTime(scopedState.generatedAtMs)}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    CollapsibleCoachReport(scopedState.reportText, scopedState.generatedAtMs)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { onCopyReport(scopedState.reportText) },
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_ai_coach_copy")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.ai_coach_copy))
                        }

                        OutlinedButton(
                            onClick = {
                                if (gatewayConfig.isConfigured) {
                                    onStartAnalysis()
                                } else {
                                    onOpenAiSettings()
                                }
                            },
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_ai_coach_regenerate")
                        ) {
                            Icon(
                                imageVector = if (gatewayConfig.isConfigured) {
                                    Icons.Rounded.Refresh
                                } else {
                                    Icons.Rounded.Tune
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(
                                    if (gatewayConfig.isConfigured) {
                                        R.string.ai_coach_regenerate
                                    } else {
                                        R.string.ai_coach_go_settings
                                    }
                                )
                            )
                        }
                    }
                }

                is AiAnalysisState.Error -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = cs.errorContainer.copy(alpha = 0.8f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = cs.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = scopedState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onErrorContainer
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onStartAnalysis,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_ai_coach_retry")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.ai_coach_retry))
                        }

                        OutlinedButton(
                            onClick = onOpenAiSettings,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("btn_ai_coach_error_settings")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.tab_settings))
                        }
                    }
                }
            }
        }
    }
}
