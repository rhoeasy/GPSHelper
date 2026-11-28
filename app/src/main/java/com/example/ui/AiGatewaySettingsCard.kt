package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ai.AiConnectionState
import com.example.ai.AiGatewayConfig
import com.example.ai.AiGatewayWork
import com.example.ai.GENERATION_WAIT_SECONDS
import com.example.ai.GatewayTaskStatus
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun AiGatewaySettingsCard(
    config: AiGatewayConfig,
    fetchedModels: List<String>,
    connectionState: AiConnectionState,
    aiHistoryCount: Int,
    onTestAndFetchModels: (baseUrl: String, apiKey: String, currentModelId: String) -> Unit,
    onCancelTest: () -> Unit,
    onSaveConfig: (baseUrl: String, apiKey: String, modelId: String) -> Unit,
    onClearConfig: () -> Unit,
    onOpenAiHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme

    var baseUrlDraft by rememberSaveable { mutableStateOf(config.baseUrl) }
    var apiKeyDraft by rememberSaveable { mutableStateOf(config.apiKey) }
    var modelIdDraft by rememberSaveable { mutableStateOf(config.modelId) }
    var showApiKey by remember { mutableStateOf(false) }
    var showAllModelsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(config.baseUrl, config.apiKey, config.modelId) {
        baseUrlDraft = config.baseUrl
        apiKeyDraft = config.apiKey
        modelIdDraft = config.modelId
    }

    if (showAllModelsDialog) {
        AiModelPickerDialog(
            models = fetchedModels,
            selectedModel = modelIdDraft,
            onSelectModel = { picked ->
                modelIdDraft = picked
                onSaveConfig(baseUrlDraft, apiKeyDraft, picked)
                showAllModelsDialog = false
            },
            onDismiss = { showAllModelsDialog = false }
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = cs.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header + Status Badge
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
                        text = stringResource(R.string.ai_section_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = cs.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (config.isConfigured) cs.primaryContainer else cs.surfaceContainerHigh
                ) {
                    Text(
                        text = stringResource(
                            if (config.isConfigured) R.string.ai_status_configured else R.string.ai_status_offline
                        ),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (config.isConfigured) cs.onPrimaryContainer else cs.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = stringResource(R.string.ai_gateway_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )

            // Base URL Input
            OutlinedTextField(
                value = baseUrlDraft,
                onValueChange = { baseUrlDraft = it },
                label = { Text(stringResource(R.string.ai_base_url_label)) },
                placeholder = {
                    Text(
                        text = stringResource(R.string.ai_base_url_placeholder),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Link,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 13.5.sp
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_ai_base_url")
            )

            // API Key Input (Masked)
            OutlinedTextField(
                value = apiKeyDraft,
                onValueChange = { apiKeyDraft = it },
                label = { Text(stringResource(R.string.ai_api_key_label)) },
                placeholder = {
                    Text(
                        text = stringResource(R.string.ai_api_key_placeholder),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Key,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    IconButton(
                        onClick = { showApiKey = !showApiKey },
                        modifier = Modifier.testTag("btn_toggle_ai_key_visibility")
                    ) {
                        Icon(
                            imageVector = if (showApiKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = stringResource(
                                if (showApiKey) R.string.ai_api_key_toggle_hide else R.string.ai_api_key_toggle_show
                            )
                        )
                    }
                },
                visualTransformation = if (showApiKey) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 13.5.sp
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_ai_api_key")
            )

            // Test Connection & Fetch Models Action
            val isTesting = connectionState == AiConnectionState.Testing || connectionState == AiConnectionState.Queued
            FilledTonalButton(
                onClick = {
                    if (isTesting) onCancelTest() else onTestAndFetchModels(baseUrlDraft, apiKeyDraft, modelIdDraft)
                },
                enabled = isTesting || baseUrlDraft.isNotBlank(),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_ai_test_fetch")
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.ai_cancel_test))
                } else {
                    Icon(
                        imageVector = Icons.Rounded.CloudDone,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.ai_action_test_fetch))
                }
            }

            // Connection Test Feedback Banner
            when (connectionState) {
                is AiConnectionState.Success -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = cs.secondaryContainer.copy(alpha = 0.72f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = cs.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.ai_test_success_format, connectionState.modelCount),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = cs.onSecondaryContainer
                            )
                        }
                    }
                }

                is AiConnectionState.Error -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = cs.errorContainer.copy(alpha = 0.8f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = cs.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = connectionState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onErrorContainer
                            )
                        }
                    }
                }

                AiConnectionState.Queued -> Text(stringResource(R.string.ai_stage_queued), style = MaterialTheme.typography.bodySmall)
                AiConnectionState.Testing -> Text(stringResource(R.string.ai_action_testing), style = MaterialTheme.typography.bodySmall)
                AiConnectionState.Idle -> Unit
            }

            // Model ID Manual Input (always available as fallback)
            OutlinedTextField(
                value = modelIdDraft,
                onValueChange = { modelIdDraft = it },
                label = { Text(stringResource(R.string.ai_model_id_label)) },
                placeholder = {
                    Text(
                        text = stringResource(R.string.ai_model_id_placeholder),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Memory,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 13.5.sp
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_ai_model_id")
            )

            // Quick Model Selection Chips when models were fetched
            if (fetchedModels.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.ai_models_picker_label),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color = cs.onSurfaceVariant
                        )
                        if (fetchedModels.size > 6) {
                            TextButton(
                                onClick = { showAllModelsDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("btn_ai_open_model_dialog")
                            ) {
                                Text(
                                    text = stringResource(R.string.ai_models_more_button, fetchedModels.size),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val previewModels = remember(fetchedModels, modelIdDraft) {
                            buildList {
                                if (modelIdDraft.isNotBlank() && fetchedModels.contains(modelIdDraft)) {
                                    add(modelIdDraft)
                                }
                                fetchedModels.forEach { m ->
                                    if (size < 10 && m != modelIdDraft) add(m)
                                }
                            }
                        }
                        previewModels.forEach { model ->
                            val selected = model == modelIdDraft.trim()
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    modelIdDraft = model
                                    onSaveConfig(baseUrlDraft, apiKeyDraft, model)
                                },
                                label = {
                                    Text(
                                        text = model,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                leadingIcon = if (selected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else {
                                    null
                                }
                            )
                        }
                    }
                }
            }

            // Save & Clear Actions
            GatewayGenerationControls(aiHistoryCount)

            val hasDraftContent = baseUrlDraft.isNotBlank() || apiKeyDraft.isNotBlank() || modelIdDraft.isNotBlank()
            val hasSavedContent = config.hasBaseUrl || config.apiKey.isNotBlank() || config.modelId.isNotBlank()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { onSaveConfig(baseUrlDraft, apiKeyDraft, modelIdDraft) },
                    enabled = baseUrlDraft.isNotBlank() && modelIdDraft.isNotBlank(),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_ai_save_config")
                ) {
                    Text(stringResource(R.string.ai_action_save))
                }

                if (hasDraftContent || hasSavedContent) {
                    OutlinedButton(
                        onClick = {
                            baseUrlDraft = ""
                            apiKeyDraft = ""
                            modelIdDraft = ""
                            onClearConfig()
                        },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("btn_ai_clear_config")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.ai_action_clear))
                    }
                }
            }

            // Quick entry to AI Analysis History
            OutlinedButton(
                onClick = onOpenAiHistory,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_settings_open_ai_history")
            ) {
                Icon(
                    imageVector = Icons.Rounded.History,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (aiHistoryCount > 0) {
                        "${stringResource(R.string.ai_history_entry)} ($aiHistoryCount)"
                    } else {
                        stringResource(R.string.ai_history_entry)
                    }
                )
            }

            // Security Footnote
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = cs.onSurfaceVariant.copy(alpha = 0.78f),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = stringResource(R.string.ai_security_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun GatewayGenerationControls(historyCount: Int) {
    val context = LocalContext.current
    val gateway = remember(context.applicationContext) { AiGatewayWork.get(context.applicationContext) }
    val config by gateway.config.collectAsStateWithLifecycle()
    val status by gateway.memoryStatus.collectAsStateWithLifecycle(initialValue = null)
    val result by gateway.memoryResult.collectAsStateWithLifecycle()
    var seconds by rememberSaveable(config.effectiveWaitSeconds) { mutableStateOf(config.effectiveWaitSeconds.toString()) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    val wait = seconds.toIntOrNull()?.takeIf { it in GENERATION_WAIT_SECONDS }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = seconds, onValueChange = { seconds = it.filter(Char::isDigit).take(3) },
            label = { Text("每段等待（秒）") }, singleLine = true,
            isError = seconds.isNotEmpty() && wait == null,
            supportingText = { Text("25–300 秒") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f).testTag("ai_generation_wait")
        )
        TextButton(
            onClick = { wait?.let(gateway::setGenerationWait) },
            enabled = wait != null && wait != config.effectiveWaitSeconds
        ) { Text("应用") }
    }
    when (val task = status) {
        is GatewayTaskStatus.Active -> {
            Text(stringResource(gatewayStageLabel(task.stage)))
            TextButton(onClick = gateway::cancelOrganizeMemory) { Text("取消整理") }
        }
        else -> {
            OutlinedButton(
                onClick = { confirm = true }, enabled = config.isConfigured && historyCount > 0,
                modifier = Modifier.fillMaxWidth().testTag("ai_organize_memory")
            ) { Icon(Icons.Rounded.Memory, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("整理记忆") }
            if (task is GatewayTaskStatus.Failed) Text(task.message, color = MaterialTheme.colorScheme.error)
            else result?.let { Text(if (it == 0) "没有新的可用记忆" else "已新增 $it 条本地记忆") }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("整理记忆") },
        text = { Text("将最近最多 6 份 AI 教练报告的片段发送给当前网关。生成的短条目只保存到本地记忆。") },
        confirmButton = { TextButton(onClick = { confirm = false; gateway.organizeMemory() }) { Text("发送并整理") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("取消") } }
    )
}

@Composable
private fun AiModelPickerDialog(
    models: List<String>,
    selectedModel: String,
    onSelectModel: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(models, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) models else models.filter { it.lowercase().contains(q) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.ai_models_dialog_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.ai_models_search_placeholder)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_ai_model_search")
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filtered, key = { it }) { modelId ->
                        val isSelected = modelId == selectedModel.trim()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) cs.primaryContainer else cs.surfaceContainer
                                )
                                .clickable { onSelectModel(modelId) }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = modelId,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                color = if (isSelected) cs.onPrimaryContainer else cs.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = cs.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.detail_cancel))
            }
        }
    )
}
