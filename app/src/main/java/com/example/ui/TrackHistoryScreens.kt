package com.example.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.example.ai.AiAnalysisState
import com.example.ai.AiGatewayConfig
import com.example.ble.HeartRateState
import com.example.gps.SpeedUnit
import com.example.track.ActivityMode
import com.example.track.KartingSectorEngine
import com.example.track.MergeJunctionQuality
import com.example.track.OffroadAnalysisEngine
import com.example.track.RecordingSnapshot
import com.example.track.RecordingStatus
import com.example.track.TrackEndpoints
import com.example.track.TrackFormatters
import com.example.track.TrackLapEntity
import com.example.track.TrackMergeEngine
import com.example.track.TrackSessionDetail
import com.example.track.TrackSessionEntity
import com.example.track.TrackTelemetryAnalyzer
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalTelemetryColors
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackHistoryScreen(
    sessions: List<TrackSessionEntity>,
    speedUnit: SpeedUnit,
    aiHistoryCount: Int,
    onSelectSession: (Long) -> Unit,
    onOpenAiHistory: () -> Unit,
    recordingSnapshot: RecordingSnapshot = RecordingSnapshot(),
    heartRateState: HeartRateState = HeartRateState(),
    showRecorderSheet: Boolean = false,
    onShowRecorderSheetChange: (Boolean) -> Unit = {},
    onOpenBlePicker: () -> Unit = {},
    onSelectActivityMode: (ActivityMode) -> Unit = {},
    onStartRecording: () -> Unit = {},
    onPauseRecording: () -> Unit = {},
    onResumeRecording: () -> Unit = {},
    onFinishRecording: () -> Unit = {},
    onSetStartGate: () -> Unit = {},
    sessionEndpoints: Map<Long, TrackEndpoints> = emptyMap(),
    mergeCompletedSignal: Int = 0,
    onEnterMergeMode: () -> Unit = {},
    onRequestMergePreview: (List<Long>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    var filterMode by remember { mutableStateOf<ActivityMode?>(null) }
    var isMergeMode by rememberSaveable { mutableStateOf(false) }
    var selectedMergeIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val isRecordingActive = recordingSnapshot.status != RecordingStatus.IDLE

    LaunchedEffect(mergeCompletedSignal) {
        if (mergeCompletedSignal > 0) {
            isMergeMode = false
            selectedMergeIds = emptySet()
        }
    }

    LaunchedEffect(sessions, isMergeMode) {
        if (isMergeMode) {
            val validIds = sessions.mapTo(HashSet(sessions.size)) { it.id }
            val pruned = selectedMergeIds.filterTo(HashSet()) { it in validIds }
            if (pruned.size != selectedMergeIds.size) {
                selectedMergeIds = pruned
            }
        }
    }

    BackHandler(enabled = isMergeMode) {
        isMergeMode = false
        selectedMergeIds = emptySet()
    }

    val filtered = remember(sessions, filterMode, isMergeMode) {
        if (isMergeMode) {
            sessions.filter { session ->
                TrackMergeEngine.isModeMergeable(session.activityMode) &&
                    (filterMode == null || session.activityMode == filterMode)
            }
        } else {
            if (filterMode == null) sessions else sessions.filter { it.activityMode == filterMode }
        }
    }

    val selectedSessions = remember(sessions, selectedMergeIds) {
        if (selectedMergeIds.isEmpty()) emptyList() else sessions.filter { it.id in selectedMergeIds }
    }
    val lockedMode = selectedSessions.firstOrNull()?.activityMode

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(if (isMergeMode) R.string.merge_mode_title else R.string.tab_tracks),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = if (isMergeMode) 19.sp else 22.sp
                    ),
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (isMergeMode) {
                    TextButton(
                        onClick = {
                            isMergeMode = false
                            selectedMergeIds = emptySet()
                        },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("btn_cancel_merge_mode")
                    ) {
                        Text(
                            text = stringResource(R.string.detail_cancel),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                isMergeMode = true
                                selectedMergeIds = emptySet()
                                if (filterMode == ActivityMode.KARTING) {
                                    filterMode = null
                                }
                                onEnterMergeMode()
                            },
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = cs.secondaryContainer,
                                contentColor = cs.onSecondaryContainer
                            ),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("btn_enter_merge_mode")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CallMerge,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = stringResource(R.string.merge_entry_btn),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1
                            )
                        }

                        FilledTonalButton(
                            onClick = onOpenAiHistory,
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("btn_open_ai_history")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (aiHistoryCount > 0) {
                                    "${stringResource(R.string.ai_history_entry)} ($aiHistoryCount)"
                                } else {
                                    stringResource(R.string.ai_history_entry)
                                },
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            if (isMergeMode) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = cs.secondaryContainer.copy(alpha = 0.65f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CallMerge,
                            contentDescription = null,
                            tint = cs.onSecondaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.merge_mode_banner),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSecondaryContainer
                        )
                    }
                }
            } else if (isRecordingActive) {
                ActiveRecordingBannerInTracks(
                    snapshot = recordingSnapshot,
                    onPause = onPauseRecording,
                    onResume = onResumeRecording,
                    onOpenRecorder = { onShowRecorderSheetChange(true) }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterMode == null,
                    onClick = { filterMode = null },
                    label = {
                        Text(
                            stringResource(
                                if (isMergeMode) R.string.merge_filter_all_eligible else R.string.filter_all
                            )
                        )
                    },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.testTag("filter_chip_all")
                )
                if (!isMergeMode) {
                    FilterChip(
                        selected = filterMode == ActivityMode.KARTING,
                        onClick = { filterMode = ActivityMode.KARTING },
                        label = { Text(stringResource(R.string.mode_karting)) },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.testTag("filter_chip_karting")
                    )
                }
                FilterChip(
                    selected = filterMode == ActivityMode.HIKING,
                    onClick = { filterMode = ActivityMode.HIKING },
                    label = { Text(stringResource(R.string.mode_hiking)) },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.testTag("filter_chip_hiking")
                )
                FilterChip(
                    selected = filterMode == ActivityMode.OFFROAD_CROSSING,
                    onClick = { filterMode = ActivityMode.OFFROAD_CROSSING },
                    label = { Text(stringResource(R.string.mode_offroad)) },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.testTag("filter_chip_offroad")
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
                                imageVector = if (isMergeMode) Icons.Rounded.CallMerge else Icons.Rounded.Route,
                                contentDescription = null,
                                tint = cs.onPrimaryContainer,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Text(
                            text = stringResource(
                                if (isMergeMode) R.string.merge_empty_eligible else R.string.tracks_empty_title
                            ),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = cs.onSurface,
                            textAlign = TextAlign.Center
                        )
                        if (!isMergeMode) {
                            Text(
                                text = stringResource(R.string.tracks_empty_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = if (isMergeMode) 16.dp else 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filtered, key = { it.id }) { session ->
                        val isSelected = session.id in selectedMergeIds
                        val isEligible = !isMergeMode || lockedMode == null || session.activityMode == lockedMode
                        val connectionGapM = if (isMergeMode && !isSelected && isEligible && selectedSessions.isNotEmpty()) {
                            TrackMergeEngine.bestChainConnectionMeters(
                                selectedOrdered = selectedSessions,
                                candidate = session,
                                endpointsById = sessionEndpoints
                            )
                        } else {
                            null
                        }

                        TrackSessionCard(
                            session = session,
                            speedUnit = speedUnit,
                            isMergeMode = isMergeMode,
                            isSelectedForMerge = isSelected,
                            isEligibleForMerge = isEligible,
                            connectionGapMeters = connectionGapM,
                            onClick = {
                                if (isMergeMode) {
                                    if (isEligible) {
                                        selectedMergeIds = if (isSelected) {
                                            selectedMergeIds - session.id
                                        } else {
                                            selectedMergeIds + session.id
                                        }
                                    }
                                } else {
                                    onSelectSession(session.id)
                                }
                            }
                        )
                    }
                }
            }

            if (isMergeMode) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("merge_bottom_action_bar"),
                    shape = RoundedCornerShape(20.dp),
                    color = cs.surfaceContainerHigh,
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(R.string.merge_selected_count_format, selectedMergeIds.size),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = cs.onSurface
                            )
                            Text(
                                text = lockedMode?.labelZh ?: stringResource(R.string.merge_filter_all_eligible),
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = { onRequestMergePreview(selectedMergeIds.toList()) },
                            enabled = selectedMergeIds.size >= 2,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("btn_preview_merge_tracks")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CallMerge,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.merge_preview_btn),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }

        // '+' Floating Action Button to launch / control Track Recording right from the Tracks tab
        if (!isMergeMode) {
            FloatingActionButton(
                onClick = { onShowRecorderSheetChange(true) },
                containerColor = if (isRecordingActive) cs.errorContainer else cs.primaryContainer,
                contentColor = if (isRecordingActive) cs.onErrorContainer else cs.onPrimaryContainer,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .testTag("fab_open_track_recorder")
            ) {
                Icon(
                    imageVector = if (isRecordingActive) Icons.Rounded.FiberManualRecord else Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.tracks_add_record_cd),
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }

    if (showRecorderSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { onShowRecorderSheetChange(false) },
            sheetState = sheetState,
            containerColor = cs.surfaceContainerLowest,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.tracks_recorder_sheet_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = cs.onSurface,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                TrackRecorderCard(
                    snapshot = recordingSnapshot,
                    heartRateState = heartRateState,
                    onOpenBlePicker = onOpenBlePicker,
                    onSelectMode = onSelectActivityMode,
                    onStartRecording = onStartRecording,
                    onPauseRecording = onPauseRecording,
                    onResumeRecording = onResumeRecording,
                    onFinishRecording = {
                        onFinishRecording()
                        onShowRecorderSheetChange(false)
                    },
                    onSetStartGate = onSetStartGate,
                    speedUnit = speedUnit
                )
            }
        }
    }
}

@Composable
private fun ActiveRecordingBannerInTracks(
    snapshot: RecordingSnapshot,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onOpenRecorder: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val isPaused = snapshot.status == RecordingStatus.PAUSED
    val blink = rememberInfiniteTransition(label = "tracks_rec_blink")
    val dotAlpha by blink.animateFloat(
        initialValue = 0.28f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(680, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tracks_rec_dot"
    )

    Surface(
        onClick = onOpenRecorder,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tracks_active_recording_banner"),
        shape = RoundedCornerShape(18.dp),
        color = if (isPaused) cs.secondaryContainer else cs.errorContainer,
        border = BorderStroke(1.dp, if (isPaused) cs.outlineVariant else cs.error.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            (if (isPaused) cs.secondary else cs.error).copy(
                                alpha = if (isPaused) 1f else dotAlpha
                            )
                        )
                )
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = "${snapshot.mode.labelZh} · ${
                            stringResource(
                                if (isPaused) R.string.record_status_paused else R.string.tracks_recording_active_label
                            )
                        }",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isPaused) cs.onSecondaryContainer else cs.onErrorContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${TrackFormatters.formatDuration(snapshot.elapsedMs)} · ${
                            TrackFormatters.formatDistance(snapshot.distanceMeters)
                        }",
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = (if (isPaused) cs.onSecondaryContainer else cs.onErrorContainer).copy(alpha = 0.85f)
                    )
                }
            }

            IconButton(
                onClick = { if (isPaused) onResume() else onPause() },
                modifier = Modifier.testTag("btn_banner_toggle_pause")
            ) {
                Icon(
                    imageVector = if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    contentDescription = stringResource(
                        if (isPaused) R.string.record_resume else R.string.record_pause
                    ),
                    tint = if (isPaused) cs.onSecondaryContainer else cs.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun TrackSessionCard(
    session: TrackSessionEntity,
    speedUnit: SpeedUnit,
    isMergeMode: Boolean = false,
    isSelectedForMerge: Boolean = false,
    isEligibleForMerge: Boolean = true,
    connectionGapMeters: Double? = null,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val tc = LocalTelemetryColors.current
    val isKart = session.activityMode == ActivityMode.KARTING
    val (badgeBg, badgeFg) = when (session.activityMode) {
        ActivityMode.KARTING -> cs.tertiaryContainer to cs.onTertiaryContainer
        ActivityMode.HIKING -> cs.secondaryContainer to cs.onSecondaryContainer
        ActivityMode.OFFROAD_CROSSING -> cs.primaryContainer to cs.onPrimaryContainer
    }

    val cardColor = when {
        isMergeMode && isSelectedForMerge -> cs.primaryContainer.copy(alpha = 0.35f)
        else -> cs.surfaceContainerLow
    }
    val cardBorder = if (isMergeMode && isSelectedForMerge) {
        BorderStroke(1.5.dp, cs.primary)
    } else {
        null
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isMergeMode && !isEligibleForMerge) 0.48f else 1f)
            .clip(RoundedCornerShape(22.dp))
            .clickable(enabled = !isMergeMode || isEligibleForMerge, onClick = onClick)
            .testTag("track_session_card_${session.id}"),
        shape = RoundedCornerShape(22.dp),
        color = cardColor,
        border = cardBorder
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
                                imageVector = activityModeIcon(session.activityMode),
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

                if (isMergeMode) {
                    Checkbox(
                        checked = isSelectedForMerge,
                        onCheckedChange = { if (isEligibleForMerge) onClick() },
                        enabled = isEligibleForMerge,
                        modifier = Modifier.testTag("merge_checkbox_${session.id}")
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (isMergeMode && (!isEligibleForMerge || connectionGapMeters != null)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!isEligibleForMerge) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = cs.surfaceContainerHighest
                        ) {
                            Text(
                                text = stringResource(R.string.merge_same_type_only_tag),
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    } else if (connectionGapMeters != null) {
                        val quality = TrackMergeEngine.classifyJunction(connectionGapMeters)
                        val (pillBg, pillFg, pillText) = when (quality) {
                            MergeJunctionQuality.SEAMLESS -> Triple(
                                cs.secondaryContainer,
                                cs.onSecondaryContainer,
                                stringResource(
                                    R.string.merge_connection_seamless_format,
                                    formatGapMeters(connectionGapMeters)
                                )
                            )
                            MergeJunctionQuality.NEARBY -> Triple(
                                cs.primaryContainer,
                                cs.onPrimaryContainer,
                                stringResource(
                                    R.string.merge_connection_nearby_format,
                                    formatGapMeters(connectionGapMeters)
                                )
                            )
                            MergeJunctionQuality.FAR -> Triple(
                                cs.surfaceContainerHighest,
                                cs.onSurfaceVariant,
                                stringResource(
                                    R.string.merge_connection_far_format,
                                    formatGapMeters(connectionGapMeters)
                                )
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = pillBg
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Link,
                                    contentDescription = null,
                                    tint = pillFg,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = pillText,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = JetBrainsMonoFontFamily
                                    ),
                                    color = pillFg
                                )
                            }
                        }
                    }
                }
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
    aiGatewayConfig: AiGatewayConfig,
    aiAnalysisState: AiAnalysisState,
    onExitFocusLock: () -> Unit,
    onBack: () -> Unit,
    onCopyJson: (includeAiPrompt: Boolean) -> Unit,
    onExportJson: (includeAiPrompt: Boolean) -> Unit,
    onExportGpx: () -> Unit,
    onShareSocialImage: (Uri) -> Unit,
    onDeleteSession: () -> Unit,
    onStartAiAnalysis: () -> Unit,
    onStopAiAnalysis: () -> Unit,
    onCopyAiReport: (String) -> Unit,
    onOpenAiSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val tc = LocalTelemetryColors.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showJsonExportDialog by remember { mutableStateOf(false) }
    var includeAiPrompt by rememberSaveable { mutableStateOf(true) }
    var showSocialStudio by rememberSaveable(detail.session.id) { mutableStateOf(false) }
    var selectedPointIndex by remember(detail.session.id) { mutableStateOf<Int?>(null) }

    if (showSocialStudio) {
        SocialShareCardScreen(
            detail = detail,
            speedUnit = speedUnit,
            onBack = { showSocialStudio = false },
            onSharePngUri = onShareSocialImage,
            modifier = Modifier
                .fillMaxSize()
                .background(cs.background)
        )
        return
    }

    BackHandler(onBack = onBack)

    val session = detail.session
    val isKart = session.activityMode == ActivityMode.KARTING
    val altitudeSourceLabel = remember(detail.points) {
        val hasBarometer = detail.points.any { it.altitudeFromBarometer == true }
        val hasGps = detail.points.any { it.altitudeMeters != null && it.altitudeFromBarometer != true }
        when {
            hasBarometer && hasGps -> R.string.detail_altitude_source_mixed
            hasBarometer -> R.string.detail_altitude_source_barometer
            detail.points.any { it.altitudeFromBarometer == false } -> R.string.detail_altitude_source_gps
            else -> null
        }
    }
    val kartingF1Analysis = remember(detail.session.id, detail.points, detail.laps) {
        if (isKart) KartingSectorEngine.analyze(detail.points, detail.laps) else null
    }
    var selectedF1LapNumber by remember(detail.session.id, kartingF1Analysis?.bestLapNumber) {
        mutableStateOf(kartingF1Analysis?.bestLapNumber)
    }

    if (showJsonExportDialog) {
        AlertDialog(
            onDismissRequest = { showJsonExportDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.json_export_dialog_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        onClick = { includeAiPrompt = !includeAiPrompt },
                        shape = RoundedCornerShape(16.dp),
                        color = cs.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("row_toggle_ai_prompt")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = includeAiPrompt,
                                onCheckedChange = { includeAiPrompt = it },
                                modifier = Modifier.testTag("checkbox_ai_prompt")
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(top = 2.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.json_export_ai_prompt_title),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = cs.onSurface
                                )
                                Text(
                                    text = stringResource(
                                        when (session.activityMode) {
                                            ActivityMode.KARTING -> R.string.json_export_ai_prompt_desc_karting
                                            ActivityMode.HIKING -> R.string.json_export_ai_prompt_desc_hiking
                                            ActivityMode.OFFROAD_CROSSING -> R.string.json_export_ai_prompt_desc_offroad
                                        }
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                showJsonExportDialog = false
                                onCopyJson(includeAiPrompt)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("btn_copy_json_clipboard"),
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.json_export_copy_clipboard),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        FilledTonalButton(
                            onClick = {
                                showJsonExportDialog = false
                                onExportJson(includeAiPrompt)
                            },
                            modifier = Modifier
                                .height(46.dp)
                                .testTag("btn_share_json_file"),
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.IosShare,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.json_export_share_file),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showJsonExportDialog = false }) {
                    Text(stringResource(R.string.detail_cancel))
                }
            }
        )
    }

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

    val headerBar: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
        }
    }

    val actionButtonsSection: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { showSocialStudio = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_make_social_card"),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = cs.primary,
                    contentColor = cs.onPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.detail_make_social_card),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = { showJsonExportDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_export_json"),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = cs.primaryContainer,
                        contentColor = cs.onPrimaryContainer
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
        }
    }

    val summaryMetricsCard: @Composable () -> Unit = {
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
                    val isOffroad = session.activityMode == ActivityMode.OFFROAD_CROSSING
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
                        if (isOffroad) {
                            DetailMetricCell(
                                label = stringResource(R.string.metric_avg_speed),
                                value = TrackFormatters.formatSpeed(session.avgSpeedMps, speedUnit),
                                accent = cs.tertiary,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
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
                        if (isOffroad) {
                            DetailMetricCell(
                                label = stringResource(R.string.metric_peak_lat_g),
                                value = if (session.peakLateralG > 0.05f) {
                                    String.format(Locale.US, "%.2f G", session.peakLateralG)
                                } else {
                                    "--"
                                },
                                accent = cs.primary,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            DetailMetricCell(
                                label = stringResource(R.string.metric_avg_speed),
                                value = TrackFormatters.formatSpeed(session.avgSpeedMps, speedUnit),
                                accent = cs.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    if (altitudeSourceLabel != null) {
                        Text(
                            text = stringResource(altitudeSourceLabel),
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant
                        )
                    }
                }

                if (session.activityMode == ActivityMode.HIKING && session.stepCount != null) {
                    val cadence = TrackFormatters.averageStepsPerMinute(session.stepCount, session.durationMs)
                    HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.6f))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        DetailMetricCell(
                            label = stringResource(R.string.metric_step_count),
                            value = stringResource(R.string.metric_step_count_value, session.stepCount),
                            accent = cs.primary,
                            modifier = Modifier.weight(1f)
                        )
                        DetailMetricCell(
                            label = stringResource(R.string.metric_avg_step_cadence),
                            value = cadence?.let { stringResource(R.string.metric_step_cadence_value, it) } ?: "--",
                            accent = cs.secondary,
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
    }

    val analysisAndChartsSection: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            TrackAiCoachCard(
                sessionId = session.id,
                gatewayConfig = aiGatewayConfig,
                analysisState = aiAnalysisState,
                onStartAnalysis = onStartAiAnalysis,
                onStopAnalysis = onStopAiAnalysis,
                onCopyReport = onCopyAiReport,
                onOpenAiSettings = onOpenAiSettings
            )

            when (session.activityMode) {
                ActivityMode.OFFROAD_CROSSING -> {
                    val offroadAnalysis = remember(detail.session.id, detail.points) {
                        OffroadAnalysisEngine.analyze(session, detail.points)
                    }
                    OffroadAnalysisCard(analysis = offroadAnalysis)
                }
                ActivityMode.HIKING -> {
                    val hikingAnalysis = remember(detail.session.id, detail.points) {
                        TrackTelemetryAnalyzer.analyzeHiking(session, detail.points)
                    }
                    HikingAnalysisCard(analysis = hikingAnalysis)
                }
                ActivityMode.KARTING -> {
                    if (kartingF1Analysis != null) {
                        KartingF1SectorCard(
                            analysis = kartingF1Analysis,
                            selectedLapNumber = selectedF1LapNumber,
                            onSelectLap = { selectedF1LapNumber = it },
                            speedUnit = speedUnit
                        )
                    }
                    val kartingAnalysis = remember(detail.session.id, detail.points, detail.laps) {
                        TrackTelemetryAnalyzer.analyzeKarting(session, detail.points, detail.laps)
                    }
                    KartingAnalysisCard(analysis = kartingAnalysis)
                }
            }

            TelemetryProfileChartCard(
                points = detail.points,
                mode = session.activityMode,
                speedUnit = speedUnit,
                selectedPointIndex = selectedPointIndex,
                onSelectPointIndex = { selectedPointIndex = it }
            )

            if (isKart) {
                KartingLapSplitsCard(
                    laps = detail.laps,
                    bestLapMs = session.bestLapMs,
                    speedUnit = speedUnit
                )
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(cs.background)
            .displayCutoutPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        val isLandscape = maxWidth > maxHeight && maxWidth >= 540.dp
        if (isLandscape) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("track_detail_landscape_layout"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                headerBar()

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Left Pane: Vector Route Map + Metrics + Share/Export Actions
                    Column(
                        modifier = Modifier
                            .weight(0.95f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        VectorTrackCanvasCard(
                            points = detail.points,
                            mode = session.activityMode,
                            speedUnit = speedUnit,
                            highlightedIndex = selectedPointIndex,
                            f1SectorAnalysis = kartingF1Analysis,
                            selectedLapNumber = selectedF1LapNumber
                        )
                        summaryMetricsCard()
                        actionButtonsSection()
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Right Pane: Telemetry Analysis + Synchronized Profile Charts + Laps (supports native long screenshot)
                    SystemLongScreenshotScrollContainer(
                        modifier = Modifier
                            .weight(1.15f)
                            .fillMaxHeight()
                            .testTag("track_detail_landscape_right_long_scroll")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            analysisAndChartsSection()
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
        } else {
            SystemLongScreenshotScrollContainer(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("track_detail_long_scroll")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .widthIn(max = 600.dp)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        headerBar()
                        actionButtonsSection()
                        VectorTrackCanvasCard(
                            points = detail.points,
                            mode = session.activityMode,
                            speedUnit = speedUnit,
                            highlightedIndex = selectedPointIndex,
                            f1SectorAnalysis = kartingF1Analysis,
                            selectedLapNumber = selectedF1LapNumber
                        )
                        summaryMetricsCard()
                        analysisAndChartsSection()
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
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
