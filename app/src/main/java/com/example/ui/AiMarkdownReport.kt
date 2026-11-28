package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlInline
import org.commonmark.node.Node
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text as MarkdownText
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.Parser

private val reportMarkdownParser = Parser.builder()
    .extensions(listOf(TablesExtension.builder().maxCells(1_000).build()))
    .includeSourceSpans(IncludeSourceSpans.BLOCKS)
    .build()

private sealed interface MarkdownSection {
    data class Prose(val source: String) : MarkdownSection
    data class ReportHeading(val text: AnnotatedString) : MarkdownSection
    data class Table(val rows: List<List<ReportCell>>) : MarkdownSection
    data object Divider : MarkdownSection
}

private data class ReportCell(val text: AnnotatedString, val alignment: TextAlign)

private fun Node.children(): Sequence<Node> = generateSequence(firstChild) { it.next }

private fun Node.inlineText(): AnnotatedString = buildAnnotatedString {
    fun appendNode(node: Node) {
        when (node) {
            is MarkdownText -> append(node.literal)
            is Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(node.literal) }
            is StrongEmphasis -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { node.children().forEach(::appendNode) }
            is Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { node.children().forEach(::appendNode) }
            is SoftLineBreak, is HardLineBreak -> append('\n')
            is HtmlInline -> append(node.literal)
            else -> node.children().forEach(::appendNode)
        }
    }
    children().forEach(::appendNode)
}

private fun markdownSections(markdown: String): List<MarkdownSection> {
    val document = runCatching { reportMarkdownParser.parse(markdown) }.getOrNull()
        ?: return listOf(MarkdownSection.Prose(markdown))
    val lines = markdown.lines()
    return buildList {
        var cursor = 0
        fun addProse(end: Int) {
            val text = lines.subList(cursor, end).joinToString("\n").trim()
            if (text.isNotEmpty()) add(MarkdownSection.Prose(text))
        }
        for (node in document.children()) {
            val section = when (node) {
                is ThematicBreak -> MarkdownSection.Divider
                is Heading -> MarkdownSection.ReportHeading(node.inlineText())
                is TableBlock -> MarkdownSection.Table(node.children().flatMap { it.children().filterIsInstance<TableRow>() }
                    .map { row ->
                        row.children().filterIsInstance<TableCell>().map { cell ->
                            val alignment = when (cell.alignment) {
                                TableCell.Alignment.CENTER -> TextAlign.Center
                                TableCell.Alignment.RIGHT -> TextAlign.Right
                                else -> TextAlign.Left
                            }
                            ReportCell(cell.inlineText(), alignment)
                        }.toList()
                    }.toList())
                else -> continue
            }
            val start = node.sourceSpans.firstOrNull()?.lineIndex ?: continue
            val end = node.sourceSpans.last().lineIndex + 1
            if (start < cursor || end > lines.size) continue
            addProse(start)
            add(section)
            cursor = end
        }
        addProse(lines.size)
    }
}

@Composable
internal fun AiMarkdownReport(markdown: String) {
    val colors = MaterialTheme.colorScheme
    val sections = remember(markdown) { markdownSections(markdown) }
    for (section in sections) {
        when (section) {
            is MarkdownSection.Prose -> {
                val text = remember(section, colors.primary, colors.onSurface) {
                    buildCoachAnnotatedString(section.source, colors.primary, colors.onSurface)
                }
                Text(text, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp))
            }
            is MarkdownSection.ReportHeading -> Text(
                section.text,
                style = MaterialTheme.typography.titleSmall,
                color = colors.primary,
                modifier = Modifier.semantics { heading() }
            )
            is MarkdownSection.Table -> ReportTable(section.rows)
            MarkdownSection.Divider -> HorizontalDivider(Modifier.testTag("ai_markdown_divider"))
        }
    }
}

@Composable
private fun ReportTable(rows: List<List<ReportCell>>) {
    val columnCount = rows.firstOrNull()?.size ?: return
    if (columnCount == 0) return
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("ai_markdown_table")) {
        val cellWidth = (maxWidth / columnCount).coerceIn(96.dp, 240.dp)
        Column(Modifier.horizontalScroll(rememberScrollState()).width(cellWidth * columnCount)) {
            rows.forEachIndexed { index, cells ->
                val header = index == 0
                val rowColor = when {
                    header -> colors.surfaceContainerHighest
                    index % 2 == 0 -> colors.surfaceContainerLow
                    else -> colors.surfaceContainer
                }
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(rowColor)) {
                    cells.forEach { cell ->
                        Box(Modifier.width(cellWidth).fillMaxHeight()) {
                            Text(
                                cell.text,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                                textAlign = cell.alignment
                            )
                        }
                    }
                }
                HorizontalDivider(color = colors.outlineVariant)
            }
        }
    }
}
