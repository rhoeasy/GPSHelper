package com.example.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.track.ActivityMode
import com.example.track.AiAnalysisRecordEntity
import com.example.track.TrackFormatters
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun AiAnalysisHistoryScreen(
    records: List<AiAnalysisRecordEntity>,
    existingSessionIds: Set<Long>,
    isFocusLockActive: Boolean,
    onExitFocusLock: () -> Unit,
    onBack: () -> Unit,
    onCopyReport: (String) -> Unit,
    onOpenLinkedSession: (Long) -> Unit,
    onDeleteRecord: (Long) -> Unit,
    onClearAllRecords: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    var filterMode by rememberSaveable { mutableStateOf<ActivityMode?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedRecordId by rememberSaveable { mutableStateOf<Long?>(null) }
    var recordPendingDelete by remember { mutableStateOf<AiAnalysisRecordEntity?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val filteredRecords = remember(records, filterMode, searchQuery) {
        val q = searchQuery.trim().lowercase()
        records.filter { record ->
            val modeMatches = filterMode == null || record.mode == filterMode
            val queryMatches = q.isEmpty() ||
                record.sessionName.lowercase().contains(q) ||
                record.modelId.lowercase().contains(q) ||
                record.reportText.lowercase().contains(q)
            modeMatches && queryMatches
        }
    }

    val selectedRecord = remember(records, selectedRecordId) {
        selectedRecordId?.let { id -> records.firstOrNull { it.id == id } }
    }

    // Clear stale selection if record was deleted
    LaunchedEffect(selectedRecordId, records) {
        if (selectedRecordId != null && records.none { it.id == selectedRecordId }) {
            selectedRecordId = null
        }
    }

    recordPendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { recordPendingDelete = null },
            title = { Text(stringResource(R.string.ai_history_delete_title)) },
            text = { Text(stringResource(R.string.ai_history_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = target.id
                        recordPendingDelete = null
                        if (selectedRecordId == id) {
                            selectedRecordId = null
                        }
                        onDeleteRecord(id)
                    }
                ) {
                    Text(stringResource(R.string.detail_delete), color = cs.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { recordPendingDelete = null }) {
                    Text(stringResource(R.string.detail_cancel))
                }
            }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.ai_history_clear_title)) },
            text = { Text(stringResource(R.string.ai_history_clear_confirm, records.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        selectedRecordId = null
                        onClearAllRecords()
                    }
                ) {
                    Text(stringResource(R.string.ai_history_clear_all), color = cs.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.detail_cancel))
                }
            }
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(cs.background)
            .displayCutoutPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        val isLandscapeSplit = maxWidth > maxHeight && maxWidth >= 600.dp

        // Portrait detail mode: show dedicated full-screen detail view when a record is selected
        if (!isLandscapeSplit && selectedRecord != null) {
            BackHandler { selectedRecordId = null }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        onClick = { selectedRecordId = null },
                        modifier = Modifier.testTag("btn_ai_history_detail_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.ai_history_detail_back_cd),
                            tint = cs.onSurface
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedRecord.sessionName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = cs.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = TrackFormatters.formatDateTime(selectedRecord.generatedAtMs),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily
                            ),
                            color = cs.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { recordPendingDelete = selectedRecord },
                        modifier = Modifier.testTag("btn_ai_history_delete_single")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(R.string.ai_history_delete_record),
                            tint = cs.error
                        )
                    }
                }

                AiHistoryDetailContent(
                    record = selectedRecord,
                    hasLinkedSession = selectedRecord.sessionId != null &&
                        existingSessionIds.contains(selectedRecord.sessionId),
                    onCopyReport = { onCopyReport(selectedRecord.reportText) },
                    onOpenLinkedSession = {
                        selectedRecord.sessionId?.let(onOpenLinkedSession)
                    },
                    onRequestDelete = { recordPendingDelete = selectedRecord },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
            return@BoxWithConstraints
        }

        BackHandler(onBack = onBack)

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
                        modifier = Modifier.testTag("btn_ai_history_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.ai_history_back_cd),
                            tint = cs.onSurface
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.ai_history_title),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 20.sp
                            ),
                            color = cs.onSurface
                        )
                        if (records.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.ai_history_count_format, records.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant
                            )
                        }
                    }
                    if (records.isNotEmpty()) {
                        TextButton(
                            onClick = { showClearConfirm = true },
                            modifier = Modifier.testTag("btn_ai_history_clear_all")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = null,
                                tint = cs.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.ai_history_clear_all),
                                color = cs.error
                            )
                        }
                    }
                }
            }
        }

        val filtersSection: @Composable () -> Unit = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (records.size > 1) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.ai_history_search_placeholder),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.detail_cancel),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        } else {
                            null
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_ai_history_search")
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
                        label = { Text(stringResource(R.string.filter_all)) },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.testTag("ai_history_filter_all")
                    )
                    FilterChip(
                        selected = filterMode == ActivityMode.KARTING,
                        onClick = { filterMode = ActivityMode.KARTING },
                        label = { Text(stringResource(R.string.mode_karting)) },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.testTag("ai_history_filter_karting")
                    )
                    FilterChip(
                        selected = filterMode == ActivityMode.HIKING,
                        onClick = { filterMode = ActivityMode.HIKING },
                        label = { Text(stringResource(R.string.mode_hiking)) },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.testTag("ai_history_filter_hiking")
                    )
                    FilterChip(
                        selected = filterMode == ActivityMode.OFFROAD_CROSSING,
                        onClick = { filterMode = ActivityMode.OFFROAD_CROSSING },
                        label = { Text(stringResource(R.string.mode_offroad)) },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.testTag("ai_history_filter_offroad")
                    )
                }
            }
        }

        if (isLandscapeSplit) {
            // Canonical Expanded List-Detail Split Layout for Landscape / Tablets
            val activeDetail = selectedRecord ?: filteredRecords.firstOrNull()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("ai_history_landscape_layout"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                headerBar()

                if (records.isEmpty()) {
                    AiHistoryEmptyState(
                        isFilteredEmpty = false,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(0.95f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            filtersSection()
                            if (filteredRecords.isEmpty()) {
                                AiHistoryEmptyState(
                                    isFilteredEmpty = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    contentPadding = PaddingValues(bottom = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(filteredRecords, key = { it.id }) { record ->
                                        AiHistoryListCard(
                                            record = record,
                                            isSelected = activeDetail?.id == record.id,
                                            onClick = { selectedRecordId = record.id }
                                        )
                                    }
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1.25f)
                                .fillMaxHeight()
                        ) {
                            if (activeDetail != null) {
                                AiHistoryDetailContent(
                                    record = activeDetail,
                                    hasLinkedSession = activeDetail.sessionId != null &&
                                        existingSessionIds.contains(activeDetail.sessionId),
                                    onCopyReport = { onCopyReport(activeDetail.reportText) },
                                    onOpenLinkedSession = {
                                        activeDetail.sessionId?.let(onOpenLinkedSession)
                                    },
                                    onRequestDelete = { recordPendingDelete = activeDetail },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.ai_history_select_prompt),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = cs.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Portrait Single-Column History List
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                headerBar()

                if (records.isEmpty()) {
                    AiHistoryEmptyState(
                        isFilteredEmpty = false,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                } else {
                    filtersSection()

                    if (filteredRecords.isEmpty()) {
                        AiHistoryEmptyState(
                            isFilteredEmpty = true,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentPadding = PaddingValues(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filteredRecords, key = { it.id }) { record ->
                                AiHistoryListCard(
                                    record = record,
                                    isSelected = false,
                                    onClick = { selectedRecordId = record.id }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiHistoryListCard(
    record: AiAnalysisRecordEntity,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val (badgeBg, badgeFg) = when (record.mode) {
        ActivityMode.KARTING -> cs.tertiaryContainer to cs.onTertiaryContainer
        ActivityMode.HIKING -> cs.secondaryContainer to cs.onSecondaryContainer
        ActivityMode.OFFROAD_CROSSING -> cs.primaryContainer to cs.onPrimaryContainer
    }
    val previewSnippet = remember(record.reportText) {
        buildPlainPreviewSnippet(record.reportText)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .testTag("ai_history_card_${record.id}"),
        shape = RoundedCornerShape(22.dp),
        color = if (isSelected) cs.primaryContainer.copy(alpha = 0.28f) else cs.surfaceContainerLow,
        border = if (isSelected) BorderStroke(1.5.dp, cs.primary) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
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
                                imageVector = activityModeIcon(record.mode),
                                contentDescription = null,
                                tint = badgeFg,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = record.mode.labelZh,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = badgeFg
                            )
                        }
                    }

                    Text(
                        text = record.sessionName,
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

            if (record.summaryMetrics.isNotBlank()) {
                Text(
                    text = record.summaryMetrics,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Medium
                    ),
                    color = cs.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = previewSnippet,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                color = cs.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Memory,
                        contentDescription = null,
                        tint = cs.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = record.modelId,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = TrackFormatters.formatDateTime(record.generatedAtMs),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFontFamily
                    ),
                    color = cs.onSurfaceVariant.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun AiHistoryDetailContent(
    record: AiAnalysisRecordEntity,
    hasLinkedSession: Boolean,
    onCopyReport: () -> Unit,
    onOpenLinkedSession: () -> Unit,
    onRequestDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val (badgeBg, badgeFg) = when (record.mode) {
        ActivityMode.KARTING -> cs.tertiaryContainer to cs.onTertiaryContainer
        ActivityMode.HIKING -> cs.secondaryContainer to cs.onSecondaryContainer
        ActivityMode.OFFROAD_CROSSING -> cs.primaryContainer to cs.onPrimaryContainer
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Metadata & Actions Card
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = cs.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
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
                                imageVector = activityModeIcon(record.mode),
                                contentDescription = null,
                                tint = badgeFg,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = record.mode.labelZh,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = badgeFg
                            )
                        }
                    }

                    Text(
                        text = record.sessionName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = cs.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (record.summaryMetrics.isNotBlank()) {
                    Text(
                        text = record.summaryMetrics,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Medium
                        ),
                        color = cs.primary
                    )
                }

                Text(
                    text = "${stringResource(R.string.ai_coach_meta_model, record.modelId)}  ·  ${TrackFormatters.formatDateTime(record.generatedAtMs)}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFontFamily
                    ),
                    color = cs.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onCopyReport,
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_ai_history_copy")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.ai_coach_copy))
                    }

                    if (hasLinkedSession) {
                        FilledTonalButton(
                            onClick = onOpenLinkedSession,
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_ai_history_open_track")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Route,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.ai_history_open_track))
                        }
                    } else {
                        OutlinedButton(
                            onClick = onRequestDelete,
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("btn_ai_history_delete_inline")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = null,
                                tint = cs.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.detail_delete),
                                color = cs.error
                            )
                        }
                    }
                }

                if (!hasLinkedSession) {
                    Text(
                        text = stringResource(R.string.ai_history_track_deleted_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // Full Formatted AI Coach Report Card
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = cs.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = cs.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.ai_coach_title),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = cs.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cs.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SelectionContainer {
                        FormattedCoachReportText(
                            rawMarkdown = record.reportText,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun AiHistoryEmptyState(
    isFilteredEmpty: Boolean,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = modifier,
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
                    imageVector = Icons.Rounded.History,
                    contentDescription = null,
                    tint = cs.onPrimaryContainer,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                text = stringResource(
                    if (isFilteredEmpty) R.string.ai_history_filter_empty else R.string.ai_history_empty_title
                ),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = cs.onSurface,
                textAlign = TextAlign.Center
            )
            if (!isFilteredEmpty) {
                Text(
                    text = stringResource(R.string.ai_history_empty_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

internal fun buildPlainPreviewSnippet(markdown: String): String {
    return markdown
        .replace("\r\n", "\n")
        .lineSequence()
        .map { line ->
            line.trim()
                .trimStart('#', '-', '*', ' ')
                .replace("**", "")
                .trim()
        }
        .filter { it.isNotEmpty() }
        .take(3)
        .joinToString(" ")
        .take(140)
}
