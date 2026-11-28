package com.example.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.gps.SpeedUnit
import com.example.track.MIN_POSTER_LAYER_SCALE
import com.example.track.SocialCardBitmapRenderer
import com.example.track.SocialCardConfig
import com.example.track.SocialCardLayout
import com.example.track.SocialLayerPlacement
import com.example.track.SocialPosterEdits
import com.example.track.SocialPosterLayer
import com.example.track.TrackSessionDetail
import com.example.track.socialPosterFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SocialPhotoPosterEditor(
    photo: Bitmap,
    detail: TrackSessionDetail,
    speedUnit: SpeedUnit,
    config: SocialCardConfig,
    onConfigChange: (SocialCardConfig) -> Unit
) {
    val artworkConfig = config.copy(posterEdits = SocialPosterEdits())
    val artwork by produceState<Map<SocialPosterLayer, Bitmap>?>(null, detail, speedUnit, artworkConfig) {
        value = withContext(Dispatchers.Default) {
            SocialCardBitmapRenderer.renderPhotoPosterLayers(detail, speedUnit, artworkConfig)
        }
    }
    var selectedLayer by remember { mutableStateOf(SocialPosterLayer.ROUTE) }
    val layers = artwork.orEmpty()
    val activeLayer = selectedLayer.takeIf { it in layers } ?: layers.keys.firstOrNull()
    val place: (SocialPosterLayer, SocialLayerPlacement?) -> Unit = { layer, placement ->
        onConfigChange(config.copy(posterEdits = config.posterEdits.place(layer, placement)))
    }

    SocialPhotoPosterPreview(photo, layers, config, activeLayer, { selectedLayer = it }, place)
    if (artwork == null) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.social_layout_title), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((preset, label) in listOf(
                SocialCardLayout.ROUTE_FOCUS to R.string.social_poster_route_focus,
                SocialCardLayout.SIDE_BY_SIDE to R.string.social_poster_side,
                SocialCardLayout.DIAGONAL to R.string.social_poster_diagonal
            )) {
                FilterChip(
                    selected = config.layout == preset,
                    onClick = {
                        if (config.layout != preset) {
                            onConfigChange(config.copy(layout = preset, posterEdits = SocialPosterEdits()))
                        }
                    },
                    label = { Text(stringResource(label)) },
                    modifier = Modifier.testTag("social_layout_${preset.name.lowercase()}")
                )
            }
        }
        if (activeLayer != null) {
            Text(stringResource(R.string.social_poster_layers), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (layer in layers.keys) {
                    FilterChip(
                        selected = layer == activeLayer,
                        onClick = { selectedLayer = layer },
                        label = { Text(stringResource(posterLayerLabel(layer))) },
                        modifier = Modifier.testTag("social_layer_${layer.name.lowercase()}")
                    )
                }
            }

            val source = layers.getValue(activeLayer)
            val frame = socialPosterFrame(photo.width.toFloat(), photo.height.toFloat(), source.width, source.height, activeLayer, config)
            val placement = frame.placement
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.social_poster_layer_scale, (placement.scale * 100).roundToInt()))
                Slider(
                    value = placement.scale,
                    onValueChange = { place(activeLayer, placement.copy(scale = it)) },
                    valueRange = MIN_POSTER_LAYER_SCALE..frame.maxScale,
                    modifier = Modifier.weight(1f).testTag("social_layer_scale")
                )
                IconButton(onClick = { place(activeLayer, null) }, enabled = config.posterEdits[activeLayer] != null) {
                    Icon(Icons.Rounded.Refresh, stringResource(R.string.social_poster_reset_layer))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                for ((icon, label, delta) in listOf(
                    Triple(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, R.string.social_poster_move_left, -0.04f to 0f),
                    Triple(Icons.Rounded.KeyboardArrowUp, R.string.social_poster_move_up, 0f to -0.04f),
                    Triple(Icons.Rounded.KeyboardArrowDown, R.string.social_poster_move_down, 0f to 0.04f),
                    Triple(Icons.AutoMirrored.Rounded.KeyboardArrowRight, R.string.social_poster_move_right, 0.04f to 0f)
                )) {
                    val x = (placement.x + delta.first).coerceIn(0f, 1f)
                    val y = (placement.y + delta.second).coerceIn(0f, 1f)
                    IconButton(
                        onClick = { place(activeLayer, placement.copy(x = x, y = y)) },
                        enabled = (x != placement.x && frame.travelX > 0.01f) || (y != placement.y && frame.travelY > 0.01f)
                    ) {
                        Icon(icon, stringResource(label))
                    }
                }
            }
        }
    }
}

@Composable
internal fun SocialPhotoPosterPreview(
    photo: Bitmap,
    layers: Map<SocialPosterLayer, Bitmap>,
    config: SocialCardConfig,
    activeLayer: SocialPosterLayer?,
    onSelect: (SocialPosterLayer) -> Unit,
    onPlace: (SocialPosterLayer, SocialLayerPlacement) -> Unit
) {
    val photoAspect = photo.width / photo.height.toFloat()
    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier.fillMaxWidth().aspectRatio(photoAspect.coerceIn(0.6f, 2.4f))
            .testTag("social_preview_container"),
        contentAlignment = Alignment.Center
    ) {
        val fittedWidth = minOf(maxWidth, maxHeight * photoAspect)
        val fittedHeight = fittedWidth / photoAspect
        val widthPx = with(density) { fittedWidth.toPx() }
        val heightPx = with(density) { fittedHeight.toPx() }
        Box(Modifier.size(fittedWidth, fittedHeight).clip(RoundedCornerShape(6.dp))) {
            if (photo.hasAlpha()) TransparencyCheckerboard(Modifier.fillMaxSize())
            Image(
                bitmap = remember(photo) { photo.asImageBitmap() },
                contentDescription = stringResource(R.string.social_title),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
            for ((layer, bitmap) in layers) {
                val frame = socialPosterFrame(widthPx, heightPx, bitmap.width, bitmap.height, layer, config)
                val bounds = Modifier.absoluteOffset { IntOffset(frame.left.roundToInt(), frame.top.roundToInt()) }
                    .size(with(density) { frame.width.toDp() }, with(density) { frame.height.toDp() })
                Image(
                    bitmap = remember(bitmap) { bitmap.asImageBitmap() },
                    contentDescription = null,
                    modifier = if (layer == activeLayer) bounds else bounds.clickable { onSelect(layer) },
                    contentScale = ContentScale.FillBounds
                )
            }
            if (activeLayer != null) {
                val bitmap = layers.getValue(activeLayer)
                val frame = socialPosterFrame(widthPx, heightPx, bitmap.width, bitmap.height, activeLayer, config)
                val placement = frame.placement
                val applyPlacement by rememberUpdatedState(onPlace)
                val currentPlacement by rememberUpdatedState(placement)
                val currentFrame by rememberUpdatedState(frame)
                Box(
                    Modifier.absoluteOffset { IntOffset(frame.left.roundToInt(), frame.top.roundToInt()) }
                        .size(with(density) { frame.width.toDp() }, with(density) { frame.height.toDp() })
                        .border(1.dp, cs.primaryContainer, RoundedCornerShape(3.dp))
                        .testTag("social_poster_selected_layer")
                        .pointerInput(activeLayer) {
                            var x = 0f
                            var y = 0f
                            detectDragGestures(
                                onDragStart = {
                                    x = currentPlacement.x
                                    y = currentPlacement.y
                                }
                            ) { change, delta ->
                                change.consume()
                                val travel = currentFrame
                                if (travel.travelX > 0f) x = (x + delta.x / travel.travelX).coerceIn(0f, 1f)
                                if (travel.travelY > 0f) y = (y + delta.y / travel.travelY).coerceIn(0f, 1f)
                                applyPlacement(activeLayer, currentPlacement.copy(x = x, y = y))
                            }
                        }
                )
            }
        }
    }
}

private fun posterLayerLabel(layer: SocialPosterLayer) = when (layer) {
    SocialPosterLayer.ROUTE -> R.string.social_poster_layer_route
    SocialPosterLayer.TITLE -> R.string.social_poster_layer_title
    SocialPosterLayer.DATA -> R.string.social_poster_layer_data
}
