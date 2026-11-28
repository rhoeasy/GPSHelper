package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.track.KartingTimingCoordinate
import com.example.track.KartingTimingCourse
import com.example.track.KartingTimingLine
import com.example.track.TrackPointEntity
import kotlin.math.cos

@Composable
internal fun KartingTimingDialog(
    points: List<TrackPointEntity>,
    saved: KartingTimingCourse?,
    hasGpsClock: Boolean,
    onSave: (KartingTimingCourse, (Boolean) -> Unit) -> Unit,
    onClose: () -> Unit
) {
    var finish by remember { mutableStateOf(saved?.finish) }
    var checkpoint by remember { mutableStateOf(saved?.checkpoint) }
    var left by remember { mutableStateOf<KartingTimingCoordinate?>(null) }
    var editingCheckpoint by remember { mutableStateOf(false) }
    var invalidLine by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val projection = remember(points) { TimingMapProjection(points) }
    val cs = MaterialTheme.colorScheme

    fun setLine(line: KartingTimingLine?) {
        if (editingCheckpoint) checkpoint = line else finish = line
        left = null
        invalidLine = false
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onClose() },
        title = { Text(stringResource(R.string.kart_edit_timing)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.kart_timing_select_ends))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = !editingCheckpoint,
                        onClick = { editingCheckpoint = false; left = null; invalidLine = false },
                        label = { Text(stringResource(R.string.kart_finish_line)) }
                    )
                    FilterChip(
                        selected = editingCheckpoint,
                        onClick = { editingCheckpoint = true; left = null; invalidLine = false },
                        label = { Text(stringResource(R.string.kart_checkpoint_line)) }
                    )
                }
                Canvas(
                    Modifier.fillMaxWidth().height(230.dp).background(cs.surfaceContainerHighest)
                        .testTag("karting_timing_map")
                        .pointerInput(projection, editingCheckpoint, saving) {
                            detectTapGestures { offset ->
                                if (saving) return@detectTapGestures
                                val p = projection.coordinate(offset, size.width.toFloat(), size.height.toFloat(), zoom, pan)
                                val first = left
                                if (first == null) {
                                    left = p
                                    invalidLine = false
                                } else {
                                    val line = KartingTimingLine(first, p)
                                    if (line.isUsable()) setLine(line) else invalidLine = true
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { centroid, delta, scale, _ ->
                                val newZoom = (zoom * scale).coerceIn(1f, 40f)
                                val center = Offset(size.width * 0.5f, size.height * 0.5f)
                                pan = if (newZoom == 1f) Offset.Zero else
                                    centroid - center - (centroid - center - pan) * (newZoom / zoom) + delta
                                zoom = newZoom
                            }
                        }
                ) {
                    fun screen(world: Offset) = projection.screen(world, size.width, size.height, zoom, pan)
                    for (index in 1 until projection.route.size) {
                        if (points[index].timingBreakBefore != true) {
                            drawLine(cs.outline, screen(projection.route[index - 1]), screen(projection.route[index]), 2.dp.toPx())
                        }
                    }
                    fun gate(line: KartingTimingLine?, color: Color) {
                        if (line == null) return
                        val a = screen(projection.world(line.left))
                        val b = screen(projection.world(line.right))
                        val vector = b - a
                        if (vector.getDistance() < 1f) return
                        val across = vector / vector.getDistance()
                        val forward = Offset(across.y, -across.x)
                        val center = (a + b) * 0.5f
                        val tip = center + forward * 20.dp.toPx()
                        val back = tip - forward * 6.dp.toPx()
                        val stroke = 3.dp.toPx()
                        drawLine(color, a, b, stroke, StrokeCap.Round)
                        drawLine(color, center, tip, stroke, StrokeCap.Round)
                        drawLine(color, tip, back + across * 4.dp.toPx(), stroke, StrokeCap.Round)
                        drawLine(color, tip, back - across * 4.dp.toPx(), stroke, StrokeCap.Round)
                    }
                    gate(finish, cs.primary)
                    gate(checkpoint, cs.tertiary)
                    left?.let { drawCircle(cs.error, 4.dp.toPx(), screen(projection.world(it))) }
                }
                val activeLine = if (editingCheckpoint) checkpoint else finish
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { setLine(activeLine?.reversed()) }, enabled = activeLine != null && !saving) {
                        Text(stringResource(R.string.kart_reverse_line))
                    }
                    TextButton(onClick = { setLine(null) }, enabled = (left != null || activeLine != null) && !saving) {
                        Text(stringResource(R.string.kart_clear_line))
                    }
                }
                val hint = when {
                    saveFailed -> R.string.kart_timing_save_failed
                    invalidLine -> R.string.kart_invalid_line
                    !hasGpsClock -> R.string.kart_estimated_clock
                    else -> null
                }
                hint?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = finish != null && left == null && !saving,
                onClick = {
                    val line = finish ?: return@TextButton
                    saving = true
                    saveFailed = false
                    onSave(KartingTimingCourse(line, checkpoint)) { ok ->
                        saving = false
                        if (ok) onClose() else saveFailed = true
                    }
                }
            ) { Text(stringResource(if (saving) R.string.kart_recalculating else R.string.kart_recalculate)) }
        },
        dismissButton = {
            TextButton(onClick = onClose, enabled = !saving) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}

private class TimingMapProjection(points: List<TrackPointEntity>) {
    private val origin = points.first()
    private val lonScale = cos(Math.toRadians(origin.latitude)).coerceAtLeast(0.01) * 111_320.0
    val route = points.map { world(KartingTimingCoordinate(it.latitude, it.longitude)) }
    private val west = route.minOf { it.x }
    private val east = route.maxOf { it.x }
    private val north = route.minOf { it.y }
    private val south = route.maxOf { it.y }
    private val center = Offset((west + east) * 0.5f, (north + south) * 0.5f)

    fun world(point: KartingTimingCoordinate) = Offset(
        (((point.longitude - origin.longitude + 540.0) % 360.0 - 180.0) * lonScale).toFloat(),
        ((origin.latitude - point.latitude) * 110_540.0).toFloat()
    )

    private fun scale(width: Float, height: Float) =
        0.84f * minOf(width / (east - west).coerceAtLeast(25f), height / (south - north).coerceAtLeast(25f))

    fun screen(point: Offset, width: Float, height: Float, zoom: Float, pan: Offset): Offset =
        Offset(width / 2f, height / 2f) + pan + (point - center) * (scale(width, height) * zoom)

    fun coordinate(offset: Offset, width: Float, height: Float, zoom: Float, pan: Offset): KartingTimingCoordinate {
        val world = center + (offset - Offset(width / 2f, height / 2f) - pan) / (scale(width, height) * zoom)
        return KartingTimingCoordinate(origin.latitude - world.y / 110_540.0, (origin.longitude + world.x / lonScale + 540.0) % 360.0 - 180.0)
    }
}
