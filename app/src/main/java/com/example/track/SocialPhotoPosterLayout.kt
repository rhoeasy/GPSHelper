package com.example.track

import kotlin.math.min

enum class SocialCardLayout { ROUTE_FOCUS, SIDE_BY_SIDE, DIAGONAL }

enum class SocialPosterLayer { ROUTE, TITLE, DATA }

data class SocialLayerPlacement(val x: Float, val y: Float, val scale: Float = 1f)

data class SocialPosterEdits(
    val route: SocialLayerPlacement? = null,
    val title: SocialLayerPlacement? = null,
    val data: SocialLayerPlacement? = null
) {
    operator fun get(layer: SocialPosterLayer) = when (layer) {
        SocialPosterLayer.ROUTE -> route
        SocialPosterLayer.TITLE -> title
        SocialPosterLayer.DATA -> data
    }

    fun place(layer: SocialPosterLayer, placement: SocialLayerPlacement?) = when (layer) {
        SocialPosterLayer.ROUTE -> copy(route = placement)
        SocialPosterLayer.TITLE -> copy(title = placement)
        SocialPosterLayer.DATA -> copy(data = placement)
    }
}

internal const val MIN_POSTER_LAYER_SCALE = 0.45f
internal const val MAX_POSTER_LAYER_SCALE = 2f

internal data class SocialPosterFrame(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val travelX: Float,
    val travelY: Float,
    val maxScale: Float,
    val placement: SocialLayerPlacement
)

private data class PosterSlot(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val alignX: Float = 0.5f,
    val alignY: Float = 0.5f
)

internal fun socialPosterFrame(
    photoWidth: Float,
    photoHeight: Float,
    layerWidth: Int,
    layerHeight: Int,
    layer: SocialPosterLayer,
    config: SocialCardConfig
): SocialPosterFrame {
    val inset = min(photoWidth, photoHeight) * 0.042f
    val innerWidth = (photoWidth - 2f * inset).coerceAtLeast(0f)
    val innerHeight = (photoHeight - 2f * inset).coerceAtLeast(0f)
    val slot = when (config.layout) {
        SocialCardLayout.ROUTE_FOCUS -> when (layer) {
            SocialPosterLayer.TITLE -> PosterSlot(0f, 0f, 0.83f, 0.17f, 0f, 0f)
            SocialPosterLayer.ROUTE -> PosterSlot(0.035f, 0.18f, 0.93f, 0.53f)
            SocialPosterLayer.DATA -> PosterSlot(0.025f, 0.73f, 0.95f, 0.27f, 0.5f, 1f)
        }
        SocialCardLayout.SIDE_BY_SIDE -> when (layer) {
            SocialPosterLayer.TITLE -> PosterSlot(0f, 0f, 0.75f, 0.18f, 0f, 0f)
            SocialPosterLayer.ROUTE -> PosterSlot(0f, 0.20f, 0.55f, 0.79f)
            SocialPosterLayer.DATA -> PosterSlot(0.58f, 0.20f, 0.42f, 0.79f, 1f, 0.5f)
        }
        SocialCardLayout.DIAGONAL -> when (layer) {
            SocialPosterLayer.TITLE -> PosterSlot(0f, 0f, 0.72f, 0.16f, 0f, 0f)
            SocialPosterLayer.ROUTE -> PosterSlot(0.18f, 0.17f, 0.82f, 0.53f, 1f, 0.5f)
            SocialPosterLayer.DATA -> PosterSlot(0f, 0.72f, 0.70f, 0.28f, 0f, 1f)
        }
    }
    val preferred = min(innerWidth * slot.width / layerWidth, innerHeight * slot.height / layerHeight)
    val limit = min(innerWidth / layerWidth, innerHeight / layerHeight)
    val maxScale = if (preferred > 0f) (limit / preferred).coerceIn(1f, MAX_POSTER_LAYER_SCALE) else 1f
    val baseWidth = layerWidth * preferred
    val baseHeight = layerHeight * preferred
    val placement = config.posterEdits[layer] ?: SocialLayerPlacement(
        (innerWidth * slot.left + (innerWidth * slot.width - baseWidth) * slot.alignX) /
            (innerWidth - baseWidth).coerceAtLeast(0.001f),
        (innerHeight * slot.top + (innerHeight * slot.height - baseHeight) * slot.alignY) /
            (innerHeight - baseHeight).coerceAtLeast(0.001f)
    )
    val adjusted = SocialLayerPlacement(
        placement.x.coerceIn(0f, 1f), placement.y.coerceIn(0f, 1f),
        placement.scale.coerceIn(MIN_POSTER_LAYER_SCALE, maxScale)
    )
    val zoom = preferred * adjusted.scale
    val width = layerWidth * zoom
    val height = layerHeight * zoom
    val travelX = (innerWidth - width).coerceAtLeast(0f)
    val travelY = (innerHeight - height).coerceAtLeast(0f)
    return SocialPosterFrame(
        inset + adjusted.x * travelX,
        inset + adjusted.y * travelY,
        width, height, travelX, travelY, maxScale, adjusted
    )
}
