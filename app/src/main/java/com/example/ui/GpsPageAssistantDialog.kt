package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.ASSISTANT_MEMORY_COUNT_LIMIT
import com.example.ai.ASSISTANT_MEMORY_TEXT_LIMIT
import com.example.ai.ASSISTANT_QUESTION_LIMIT
import com.example.ai.AssistantMemory
import com.example.ai.AssistantPage
import com.example.ai.GatewayStage
import com.example.ai.GatewayTaskStatus
import com.example.ai.GpsPageAssistant

@Composable
internal fun GpsPageAssistantHost(
    assistant: GpsPageAssistant,
    gatewayConfigured: Boolean,
    currentPage: () -> AssistantPage,
    openGatewaySettings: () -> Unit,
    content: @Composable () -> Unit
) {
    val pages = remember { AssistantPageRegistry() }
    var page by remember { mutableStateOf<AssistantPage?>(null) }
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalAssistantPages provides pages, LocalOpenPageAssistant provides {
            page = pages.active() ?: currentPage()
        }) {
            content()
        }
        SmallFloatingActionButton(
            onClick = { page = pages.active() ?: currentPage() },
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).testTag("page_assistant_open"),
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        ) {
            Icon(Icons.Rounded.ChatBubbleOutline, "问 GPSHelper")
        }
    }
    page?.let {
        PageAssistantDialog(it, assistant, gatewayConfigured, { page = null }, {
            page = null
            openGatewaySettings()
        })
    }
}

internal val LocalOpenPageAssistant = androidx.compose.runtime.staticCompositionLocalOf<(() -> Unit)?> { null }

@Composable
internal fun PageAssistantIcon() {
    val open = LocalOpenPageAssistant.current ?: return
    IconButton(onClick = open) { Icon(Icons.Rounded.ChatBubbleOutline, "问 GPSHelper") }
}

@Composable
private fun PageAssistantDialog(
    page: AssistantPage,
    assistant: GpsPageAssistant,
    configured: Boolean,
    close: () -> Unit,
    openSettings: () -> Unit
) {
    var memoryTab by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.imePadding().padding(horizontal = 12.dp).widthIn(max = 660.dp).fillMaxWidth()
                .fillMaxHeight(0.94f).testTag("page_assistant_dialog"),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("GPSHelper 助手", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = close) { Icon(Icons.Rounded.Close, "关闭助手") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !memoryTab, onClick = { memoryTab = false }, label = { Text("对话") })
                    FilterChip(selected = memoryTab, onClick = { memoryTab = true }, label = { Text("本地记忆") })
                }
                if (memoryTab) AssistantMemories(page, assistant) else AssistantConversation(page, assistant, configured, openSettings)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssistantConversation(page: AssistantPage, assistant: GpsPageAssistant, configured: Boolean, openSettings: () -> Unit) {
    val turns by assistant.exchanges.collectAsStateWithLifecycle()
    val status by assistant.status.collectAsStateWithLifecycle(initialValue = null)
    val running = status as? GatewayTaskStatus.Active
    val error = status as? GatewayTaskStatus.Failed
    var question by remember { mutableStateOf("") }
    var attachPage by remember { mutableStateOf(true) }
    var attachMemory by remember { mutableStateOf(true) }
    var showExcerpt by remember { mutableStateOf(false) }
    val scroll = rememberLazyListState()
    LaunchedEffect(turns.size, running?.stage, error) {
        if (turns.isNotEmpty()) scroll.scrollToItem(turns.lastIndex + if (error != null) 1 else 0)
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(page.title, Modifier.weight(1f).testTag("page_assistant_context"), style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = { showExcerpt = !showExcerpt }) { Text(if (showExcerpt) "收起摘要" else "查看摘要") }
            IconButton(onClick = assistant::clearConversation, enabled = turns.isNotEmpty() && running == null) {
                Icon(Icons.Rounded.DeleteOutline, "清空对话")
            }
        }
        if (showExcerpt) {
            Text(page.excerpt.ifBlank { "当前页没有可附带的数据" },
                Modifier.fillMaxWidth().heightIn(max = 112.dp).verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodySmall)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = scroll, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(turns.size) { index ->
                val turn = turns[index]
                Text(turn.question, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                turn.pageTitle?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                if (turn.answer != null) {
                    FormattedCoachReportText(turn.answer)
                } else if (index == turns.lastIndex && running != null) {
                    if (running.partialText.isNotBlank()) Text(running.partialText)
                    else Text(when (running.stage) {
                        GatewayStage.QUEUED -> "正在排队"
                        GatewayStage.CONNECTING -> "正在连接网关"
                        else -> "等待模型回答"
                    }, style = MaterialTheme.typography.bodySmall)
                }
            }
            error?.let { item { Text(it.message, color = MaterialTheme.colorScheme.error) } }
        }
        if (!configured) {
            TextButton(onClick = openSettings) { Text("配置 AI 网关") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = attachPage, onClick = { attachPage = !attachPage }, label = { Text("发送页面摘要") })
            FilterChip(selected = attachMemory, onClick = { attachMemory = !attachMemory }, label = { Text("使用本地记忆") })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = question, onValueChange = { question = it.take(ASSISTANT_QUESTION_LIMIT) },
                modifier = Modifier.weight(1f).testTag("page_assistant_question"), label = { Text("问 GPSHelper") },
                maxLines = 3
            )
            if (running != null) {
                IconButton(onClick = assistant::cancel) { Icon(Icons.Rounded.Stop, "取消回答") }
            } else {
                IconButton(enabled = configured && question.isNotBlank(), onClick = {
                    if (assistant.ask(question, page.takeIf { attachPage }, attachMemory)) question = ""
                }) { Icon(Icons.AutoMirrored.Rounded.Send, "发送给 AI 网关") }
            }
        }
    }
}

@Composable
private fun AssistantMemories(page: AssistantPage, assistant: GpsPageAssistant) {
    val entries by assistant.memories.collectAsStateWithLifecycle()
    val error by assistant.memoryError.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<AssistantMemory?>(null) }
    var text by remember { mutableStateOf("") }
    var pageId by remember { mutableStateOf<String?>(null) }
    var pinned by remember { mutableStateOf(false) }
    fun edit(entry: AssistantMemory?) {
        selected = entry
        text = entry?.text.orEmpty()
        pageId = entry?.pageId
        pinned = entry?.pinned ?: false
        editing = true
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${entries.size} / $ASSISTANT_MEMORY_COUNT_LIMIT 条", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            IconButton(onClick = { edit(null) }, enabled = !editing) { Icon(Icons.Rounded.Add, "添加记忆") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (editing) {
            OutlinedTextField(
                value = text, onValueChange = { text = it.take(ASSISTANT_MEMORY_TEXT_LIMIT) },
                modifier = Modifier.fillMaxWidth().testTag("page_assistant_memory_input"), label = { Text("记忆内容") }, maxLines = 3
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = pageId != null, onClick = { pageId = if (pageId != null) null else page.id },
                    label = { Text(if (pageId == null) "全局" else pageTitle(pageId)) })
                FilterChip(selected = pinned, onClick = { pinned = !pinned }, label = { Text("置顶") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = text.isNotBlank(), onClick = {
                    assistant.saveMemory(selected?.id, text, pageId, pinned)
                    editing = false
                }) { Text("保存记忆") }
                TextButton(onClick = { editing = false }) { Text("取消") }
            }
        }
        val ordered = remember(entries) { entries.sortedWith(compareByDescending<AssistantMemory> { it.pinned }.thenByDescending { it.usedAt }) }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(ordered, key = { it.id }) { entry ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.text)
                        Text(pageTitle(entry.pageId) + if (entry.pinned) " · 置顶" else "", style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = { edit(entry) }) { Icon(Icons.Rounded.Edit, "编辑记忆") }
                    IconButton(onClick = {
                        assistant.deleteMemory(entry.id)
                        if (selected?.id == entry.id) editing = false
                    }) { Icon(Icons.Rounded.DeleteOutline, "删除记忆") }
                }
                HorizontalDivider()
            }
        }
    }
}

private fun pageTitle(id: String?): String =
    AssistantPageKind.entries.firstOrNull { it.name == id }?.title ?: "全局"
