package com.example.ui

import android.graphics.Bitmap
import android.graphics.Canvas as BitmapCanvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ai.AiGatewayConfig
import com.example.ai.AiGatewayWork
import com.example.ai.GatewayTaskStatus
import com.example.gps.CoordinateRelation
import com.example.gps.GpsData
import com.example.gps.GpsFixStatus
import com.example.gps.MapCoordinate
import com.example.gps.compareCoordinates
import com.example.gps.currentCoordinate
import com.example.gps.parseMapCoordinate
import com.example.track.SocialCardBitmapRenderer
import com.example.track.TrackFormatters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CoordinateDetailScreen(
    gpsData: GpsData,
    gateway: AiGatewayConfig,
    onBack: () -> Unit,
    onCopy: () -> Unit,
    onRequestLocation: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onConfigureGateway: () -> Unit,
    onShareImage: (Uri) -> Unit,
    gatewayWork: AiGatewayWork? = null
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val work = gatewayWork ?: remember(context) { AiGatewayWork.get(context) }
    val answer by work.coordinateAnswer.collectAsStateWithLifecycle()
    val task by work.coordinateStatus.collectAsStateWithLifecycle(initialValue = null)
    val active = task as? GatewayTaskStatus.Active
    val generating = active != null
    var targetInput by rememberSaveable { mutableStateOf(work.coordinateAnswer.value?.target?.formatted().orEmpty()) }

    val current = gpsData.currentCoordinate()
    val target = remember(targetInput) { parseMapCoordinate(targetInput) }
    val invalidTarget = targetInput.isNotBlank() && target == null
    val relation = remember(current, target) {
        if (current != null && target != null) compareCoordinates(current, target) else null
    }
    val relationLabel = relation?.let { value ->
        val bearing = value.bearingDegrees?.let {
            stringResource(R.string.coordinate_bearing, GpsData.compassDirectionZh(it), it.roundToInt() % 360)
        } ?: stringResource(R.string.coordinate_same_position)
        stringResource(R.string.coordinate_direct_distance, TrackFormatters.formatDistance(value.distanceMeters.toDouble()), bearing)
    }
    ProvideAssistantSubpage {
        AssistantPageKind.COORDINATES.snapshot(
            if (current == null) "暂无定位" else "当前有定位；未附上精确经纬度",
            relationLabel.orEmpty(),
            if (invalidTarget) "输入的目标坐标格式无效" else ""
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("coordinate_detail_screen"),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.coordinate_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.detail_back_dashboard_cd))
                    }
                }
            )
        }
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier.widthIn(max = 660.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.coordinate_current), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                        Text(current?.formatted() ?: stringResource(R.string.coordinate_waiting), style = MaterialTheme.typography.titleMedium)
                    }
                    if (current != null) {
                        IconButton(onClick = onCopy) {
                            Icon(Icons.Rounded.ContentCopy, stringResource(R.string.action_copy_coordinates))
                        }
                    } else if (gpsData.fixStatus == GpsFixStatus.DISABLED) {
                        TextButton(onClick = onOpenLocationSettings) { Text(stringResource(R.string.coordinate_enable_location)) }
                    } else if (gpsData.fixStatus == GpsFixStatus.NO_PERMISSION) {
                        TextButton(onClick = onRequestLocation) { Text(stringResource(R.string.coordinate_enable_location)) }
                    }
                }

                OutlinedTextField(
                    value = targetInput,
                    onValueChange = { targetInput = it.take(120) },
                    modifier = Modifier.fillMaxWidth().testTag("coordinate_target_input"),
                    label = { Text(stringResource(R.string.coordinate_target_label)) },
                    placeholder = { Text("39.9042, 116.4074") },
                    supportingText = if (invalidTarget) {
                        { Text(stringResource(R.string.coordinate_invalid_target)) }
                    } else null,
                    isError = invalidTarget,
                    enabled = !generating,
                    maxLines = 2
                )

                if (current != null && !invalidTarget) {
                    CoordinateDiagram(current, target, relation, relationLabel, onShareImage)
                }

                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.coordinate_ai_title), style = MaterialTheme.typography.titleMedium)
                    if (!gateway.isConfigured) {
                        TextButton(onClick = onConfigureGateway) { Text(stringResource(R.string.coordinate_configure_gateway)) }
                    }
                }
                if (gateway.isConfigured) {
                    Button(
                        onClick = {
                            if (generating) {
                                work.cancelCoordinate()
                            } else if (current != null) {
                                work.queryCoordinate(gateway, current, target, relation)
                            }
                        },
                        enabled = generating || (current != null && !invalidTarget),
                        modifier = Modifier.fillMaxWidth().testTag("coordinate_ask_gateway")
                    ) {
                        Text(stringResource(when {
                            generating -> R.string.coordinate_stop_analysis
                            target != null -> R.string.coordinate_ask_target
                            else -> R.string.coordinate_ask_nearby
                        }))
                    }
                    Text(stringResource(R.string.coordinate_gateway_disclosure), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                if (active != null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(gatewayStageLabel(active.stage)), color = cs.onSurfaceVariant)
                }
                val lastRequest = answer
                if (lastRequest != null && !invalidTarget && lastRequest.target == target) {
                    (task as? GatewayTaskStatus.Failed)?.let { Text(it.message, color = cs.error, style = MaterialTheme.typography.bodyMedium) }
                    val result = active?.partialText ?: lastRequest.text
                    if (result.isNotBlank()) {
                        val source = lastRequest.current.formatted()
                        val destination = lastRequest.target?.formatted()
                        Text(if (destination == null) source else "$source → $destination", color = cs.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                        FormattedCoachReportText(rawMarkdown = result)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CoordinateDiagram(
    current: MapCoordinate,
    target: MapCoordinate?,
    relation: CoordinateRelation?,
    relationLabel: String?,
    onShareImage: (Uri) -> Unit
) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(8.dp)
    val diagramTitle = stringResource(R.string.coordinate_diagram_title)
    val northLabel = stringResource(R.string.coordinate_north)
    val currentLabel = "${stringResource(R.string.coordinate_current)}  ${current.formatted()}"
    val targetLabel = target?.let { "${stringResource(R.string.coordinate_target_point)}  ${it.formatted()}" }
    var zoom by remember(target) { mutableStateOf(1f) }
    var pan by remember(target) { mutableStateOf(Offset.Zero) }
    var plotSize by remember { mutableStateOf(IntSize.Zero) }
    var exporting by remember { mutableStateOf(false) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        zoom = (zoom * zoomChange).coerceIn(0.5f, 5f)
        if (plotSize.width > 0 && plotSize.height > 0) {
            pan += Offset(panChange.x / plotSize.width, panChange.y / plotSize.height)
        }
    }
    val rangeMeters = (relation?.distanceMeters?.times(1.35f)?.coerceAtLeast(100f) ?: 1000f) / zoom
    val scaleLabel = stringResource(R.string.coordinate_diagram_scale, TrackFormatters.formatDistance(rangeMeters.toDouble()))
    val plot = CoordinatePlot(relation, rangeMeters, pan, cs.outlineVariant.toArgb(), cs.primary.toArgb(), cs.tertiary.toArgb())

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(
            Modifier.fillMaxWidth().clip(shape).background(cs.surfaceContainerLow).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(diagramTitle, style = MaterialTheme.typography.titleSmall)
                Text(northLabel, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            }
            Canvas(
                Modifier.fillMaxWidth().aspectRatio(1.6f).clip(shape).background(cs.surfaceContainer)
                    .onSizeChanged { plotSize = it }
                    .transformable(state = transformState, canPan = { zoom != 1f })
            ) {
                plot.draw(drawContext.canvas.nativeCanvas, size.width, size.height, 1.dp.toPx())
            }
            relationLabel?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = cs.primary, modifier = Modifier.testTag("coordinate_relation")) }
            Text(scaleLabel, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            Text(currentLabel, style = MaterialTheme.typography.labelMedium, color = cs.primary)
            targetLabel?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = cs.tertiary) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row {
                IconButton(onClick = { zoom = (zoom / 1.35f).coerceAtLeast(0.5f) }, enabled = zoom > 0.5f) {
                    Icon(Icons.Rounded.Remove, stringResource(R.string.coordinate_zoom_out))
                }
                IconButton(onClick = { zoom = 1f; pan = Offset.Zero }) {
                    Icon(Icons.Rounded.MyLocation, stringResource(R.string.coordinate_recenter))
                }
                IconButton(onClick = { zoom = (zoom * 1.35f).coerceAtMost(5f) }, enabled = zoom < 5f) {
                    Icon(Icons.Rounded.Add, stringResource(R.string.coordinate_zoom_in))
                }
            }
            TextButton(
                enabled = !exporting,
                modifier = Modifier.testTag("coordinate_share_diagram"),
                onClick = {
                    exporting = true
                    scope.launch {
                        try {
                            val uri = withContext(Dispatchers.IO) {
                                val bitmap = plot.render(
                                    title = "$diagramTitle     $northLabel",
                                    lines = listOfNotNull(relationLabel, scaleLabel, currentLabel, targetLabel),
                                    background = cs.surfaceContainerLow.toArgb(),
                                    plotBackground = cs.surfaceContainer.toArgb(),
                                    ink = cs.onSurface.toArgb()
                                )
                                try {
                                    SocialCardBitmapRenderer.exportPngToShareUri(context, bitmap, "GPSHelper_Position")
                                } finally {
                                    bitmap.recycle()
                                }
                            }
                            onShareImage(uri)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w("CoordinateDiagram", "Could not export diagram", e)
                            Toast.makeText(context, R.string.toast_social_save_failed, Toast.LENGTH_SHORT).show()
                        } finally {
                            exporting = false
                        }
                    }
                }
            ) {
                Icon(Icons.Rounded.Share, null, Modifier.size(16.dp))
                Text(stringResource(R.string.coordinate_share_image), Modifier.padding(start = 6.dp))
            }
        }
    }
}

private data class CoordinatePlot(
    val relation: CoordinateRelation?,
    val rangeMeters: Float,
    val pan: Offset,
    val gridInk: Int,
    val originInk: Int,
    val targetInk: Int
) {
    fun draw(canvas: BitmapCanvas, width: Float, height: Float, unit: Float) {
        val x = width * (0.5f + pan.x)
        val y = height * (0.5f + pan.y)
        val radius = minOf(width, height) * 0.43f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = gridInk
            strokeWidth = unit
        }
        for (ring in 1..3) canvas.drawCircle(x, y, radius * ring / 3, paint)
        canvas.drawLine(x, 0f, x, height, paint)
        canvas.drawLine(0f, y, width, y, paint)
        if (relation?.bearingDegrees != null) {
            val angle = Math.toRadians(relation.bearingDegrees.toDouble())
            val distance = radius * relation.distanceMeters / rangeMeters
            val toX = x + sin(angle).toFloat() * distance
            val toY = y - cos(angle).toFloat() * distance
            paint.color = targetInk
            paint.strokeWidth = unit * 2f
            canvas.drawLine(x, y, toX, toY, paint)
            paint.style = Paint.Style.FILL
            canvas.drawCircle(toX, toY, 6f * unit, paint)
        }
        paint.style = Paint.Style.FILL
        paint.color = originInk
        canvas.drawCircle(x, y, 6f * unit, paint)
    }

    fun render(title: String, lines: List<String>, background: Int, plotBackground: Int, ink: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(1080, 752 + lines.size * 42, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(background)
        val canvas = BitmapCanvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = plotBackground }
        canvas.save()
        canvas.translate(36f, 88f)
        canvas.clipRect(0f, 0f, 1008f, 630f)
        canvas.drawRect(0f, 0f, 1008f, 630f, paint)
        draw(canvas, 1008f, 630f, 3f)
        canvas.restore()
        paint.color = ink
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = 40f
        canvas.drawText(title, 36f, 61f, paint)
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 30f
        lines.forEachIndexed { index, text -> canvas.drawText(text, 36f, 755f + index * 42f, paint) }
        return bitmap
    }
}
