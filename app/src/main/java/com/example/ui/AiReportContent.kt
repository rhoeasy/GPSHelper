package com.example.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ai.GatewayStage
import org.json.JSONArray
import org.json.JSONObject

internal fun gatewayStageLabel(stage: GatewayStage): Int = when (stage) {
    GatewayStage.QUEUED -> R.string.ai_stage_queued
    GatewayStage.CONNECTING -> R.string.ai_stage_connecting
    GatewayStage.WAITING_FOR_OUTPUT -> R.string.ai_stage_waiting
    GatewayStage.RECEIVING -> R.string.ai_stage_receiving
    GatewayStage.SAVING -> R.string.ai_stage_saving
}

@Composable
internal fun CollapsibleCoachReport(text: String, identity: Long) {
    var expanded by rememberSaveable(identity) { mutableStateOf(false) }
    TextButton(
        onClick = { expanded = !expanded },
        modifier = Modifier.testTag("toggle_ai_report")
    ) {
        Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
        Text(stringResource(if (expanded) R.string.ai_report_collapse else R.string.ai_report_expand))
    }
    if (expanded) {
        SelectionContainer {
            FormattedCoachReportText(text, Modifier.testTag("expanded_ai_report"))
        }
    }
}

internal enum class ReportFormat { MARKDOWN, JSON, YAML, CODE }

internal data class ReportBlock(val format: ReportFormat, val text: String)

private val fence = Regex("^\\s*(`{3,}|~{3,})\\s*([\\w+-]*)\\s*$")
private val yamlKey = Regex("^(?:-\\s+)?[\\p{L}\\w.-]+:(?:\\s|$)")
private val structuredKey = Regex("^(\\s*(?:-\\s+)?)(\"[^\"]+\"|[\\p{L}\\w.-]+)(:)(.*)$")
private val markdownHeading = Regex("^#{1,6} +(.+)$")
private val markdownInline = Regex("\\*\\*(.+?)\\*\\*|__(.+?)__|`([^`]+)`|(?<!\\*)\\*([^*]+)\\*(?!\\*)")

internal fun formatReportBlocks(raw: String): List<ReportBlock> {
    val text = raw.trim()
    if (text.isEmpty()) return emptyList()

    prettyJson(text)?.let { return listOf(ReportBlock(ReportFormat.JSON, it)) }
    val lines = text.lines()
    if (lines.none { fence.matches(it) }) {
        val keys = lines.count { yamlKey.containsMatchIn(it.trimStart()) }
        val isYaml = keys > 0 && (lines.first() == "---" || (keys >= 2 && yamlKey.containsMatchIn(lines.first())))
        return listOf(ReportBlock(if (isYaml) ReportFormat.YAML else ReportFormat.MARKDOWN, text))
    }

    return buildList {
        val buffer = StringBuilder()
        var open: String? = null
        var format = ReportFormat.MARKDOWN
        fun flush() {
            val body = buffer.toString().trimEnd('\n', '\r')
            if (body.isNotBlank()) add(ReportBlock(format, if (format == ReportFormat.JSON) prettyJson(body) ?: body else body))
            buffer.clear()
        }
        for (line in lines) {
            val match = fence.matchEntire(line)
            if (open == null && match != null) {
                flush()
                open = match.groupValues[1]
                format = when (match.groupValues[2].lowercase()) {
                    "json", "jsonc" -> ReportFormat.JSON
                    "yaml", "yml" -> ReportFormat.YAML
                    "md", "markdown" -> ReportFormat.MARKDOWN
                    else -> ReportFormat.CODE
                }
            } else if (open != null && match != null && match.groupValues[1].startsWith(open) && match.groupValues[2].isEmpty()) {
                flush()
                open = null
                format = ReportFormat.MARKDOWN
            } else {
                buffer.appendLine(line)
            }
        }
        flush()
    }
}

private fun prettyJson(text: String): String? = runCatching {
    when {
        text.startsWith('{') && text.endsWith('}') -> JSONObject(text).toString(2)
        text.startsWith('[') && text.endsWith(']') -> JSONArray(text).toString(2)
        else -> null
    }
}.getOrNull()

/** Render model text as native selectable content, without executing HTML, links, or YAML tags. */
@Composable
internal fun FormattedCoachReportText(rawMarkdown: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val blocks = remember(rawMarkdown) { formatReportBlocks(rawMarkdown) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for ((format, content) in blocks) {
            if (format == ReportFormat.MARKDOWN) {
                val text = remember(content, cs.primary, cs.onSurface) { buildCoachAnnotatedString(content, cs.primary, cs.onSurface) }
                Text(text, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp))
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = cs.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(format.name, style = MaterialTheme.typography.labelSmall, color = cs.primary)
                        val highlighted = remember(content, format, cs.primary) {
                            buildAnnotatedString {
                                content.lineSequence().forEachIndexed { index, line ->
                                    if (index > 0) append("\n")
                                    val match = if (format == ReportFormat.CODE) null else structuredKey.matchEntire(line)
                                    if (match != null) {
                                        append(match.groupValues[1])
                                        withStyle(SpanStyle(color = cs.primary, fontWeight = FontWeight.SemiBold)) {
                                            append(match.groupValues[2])
                                        }
                                        append(match.groupValues[3])
                                        append(match.groupValues[4])
                                    } else {
                                        append(line)
                                    }
                                }
                            }
                        }
                        Text(
                            highlighted,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            softWrap = false,
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        )
                    }
                }
            }
        }
    }
}

internal fun buildCoachAnnotatedString(
    markdown: String,
    headingColor: androidx.compose.ui.graphics.Color,
    bodyColor: androidx.compose.ui.graphics.Color
): AnnotatedString = buildAnnotatedString {
    val lines = markdown.replace("\r\n", "\n").lines()
    lines.forEachIndexed { index, rawLine ->
        val trimmed = rawLine.trimStart()
        val heading = markdownHeading.matchEntire(trimmed)
        when {
            heading != null -> {
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = headingColor
                    )
                ) {
                    appendInlineMarkdown(heading.groupValues[1])
                }
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                withStyle(SpanStyle(color = headingColor, fontWeight = FontWeight.Bold)) {
                    append("• ")
                }
                withStyle(SpanStyle(color = bodyColor)) {
                    appendInlineMarkdown(trimmed.drop(2))
                }
            }
            trimmed.startsWith("> ") -> withStyle(SpanStyle(color = bodyColor, fontStyle = FontStyle.Italic)) {
                append("│ ")
                appendInlineMarkdown(trimmed.drop(2))
            }
            trimmed.startsWith('|') && trimmed.endsWith('|') ->
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = bodyColor)) { append(trimmed) }
            else -> {
                withStyle(SpanStyle(color = bodyColor)) {
                    appendInlineMarkdown(rawLine)
                }
            }
        }
        if (index < lines.lastIndex) {
            append("\n")
        }
    }
}

private fun AnnotatedString.Builder.appendInlineMarkdown(text: String) {
    var cursor = 0
    for (match in markdownInline.findAll(text)) {
        append(text.substring(cursor, match.range.first))
        when {
            match.groups[1] != null || match.groups[2] != null -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                append(match.groups[1]?.value ?: match.groupValues[2])
            }
            match.groups[3] != null -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(match.groupValues[3]) }
            else -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(match.groupValues[4]) }
        }
        cursor = match.range.last + 1
    }
    append(text.substring(cursor))
}
