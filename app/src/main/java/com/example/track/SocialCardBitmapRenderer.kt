package com.example.track

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.gps.SpeedUnit
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class SocialBgPreset(
    val labelZh: String,
    val isTransparent: Boolean,
    val topColorArgb: Int,
    val bottomColorArgb: Int,
    val defaultLightInk: Boolean
) {
    TRANSPARENT("透明", true, Color.TRANSPARENT, Color.TRANSPARENT, true),
    OBSIDIAN("极夜黑", false, 0xFF0E131A.toInt(), 0xFF1A2230.toInt(), true),
    PURE_WHITE("纯净白", false, 0xFFFFFFFF.toInt(), 0xFFEEF3FA.toInt(), false),
    GOOGLE_BLUE("谷歌蓝", false, 0xFF1A5CC8.toInt(), 0xFF0B275C.toInt(), true),
    FOREST_GREEN("山野绿", false, 0xFF184A32.toInt(), 0xFF0B2419.toInt(), true),
    TRACK_CRIMSON("赛道红", false, 0xFF8C1D18.toInt(), 0xFF2B0A0D.toInt(), true),
    SUNSET_AMBER("日落橙", false, 0xFFB8550A.toInt(), 0xFF3E1B05.toInt(), true)
}

enum class SocialOverlayBackdrop(val labelZh: String) {
    CLEAR("透明悬浮"),
    SCRIM("渐变遮罩"),
    FROSTED("磨砂底板")
}

data class SocialCardConfig(
    val useImportedPhoto: Boolean = false,
    val bgPreset: SocialBgPreset = SocialBgPreset.OBSIDIAN,
    val useLightInk: Boolean = true,
    val overlayBackdrop: SocialOverlayBackdrop = SocialOverlayBackdrop.SCRIM,
    val showRoute: Boolean = true,
    val showTitle: Boolean = true,
    val showDistance: Boolean = true,
    val showDuration: Boolean = true,
    val showElevation: Boolean = true,
    val showLaps: Boolean = true,
    val showSpeed: Boolean = true,
    val showHeartRate: Boolean = true,
    val showPeakG: Boolean = true,
    val showMiniProfile: Boolean = true
)

/**
 * Pure Android Canvas renderer producing crisp ARGB_8888 social share posters & transparent PNG
 * telemetry stickers for both Hiking and Karting track records.
 */
object SocialCardBitmapRenderer {

    data class StatBadge(
        val label: String,
        val value: String,
        val accentArgb: Int
    )

    fun buildSelectedStats(
        detail: TrackSessionDetail,
        speedUnit: SpeedUnit,
        config: SocialCardConfig
    ): List<StatBadge> {
        val session = detail.session
        val isKart = session.activityMode == ActivityMode.KARTING
        val list = mutableListOf<StatBadge>()

        val primaryAccent = if (config.useLightInk) 0xFF8AB4F8.toInt() else 0xFF1A73E8.toInt()
        val greenAccent = if (config.useLightInk) 0xFF81C995.toInt() else 0xFF137333.toInt()
        val amberAccent = if (config.useLightInk) 0xFFFDD663.toInt() else 0xFFC56000.toInt()
        val heartAccent = if (config.useLightInk) 0xFFF28B82.toInt() else 0xFFD93025.toInt()
        val neutralAccent = if (config.useLightInk) Color.WHITE else 0xFF1F242D.toInt()

        if (config.showDistance) {
            list.add(StatBadge("总里程", TrackFormatters.formatDistance(session.distanceMeters), primaryAccent))
        }
        if (config.showDuration) {
            list.add(StatBadge("用时", TrackFormatters.formatDuration(session.durationMs), neutralAccent))
        }
        if (isKart && config.showLaps) {
            if (session.bestLapMs != null) {
                list.add(StatBadge("最佳圈速", TrackFormatters.formatLapTime(session.bestLapMs), greenAccent))
            }
            if (session.lapCount > 0) {
                list.add(StatBadge("完成圈数", "${session.lapCount} 圈", amberAccent))
            }
        }
        if (!isKart && config.showElevation) {
            list.add(StatBadge("累计爬升", TrackFormatters.formatElevationGain(session.elevationGainMeters), greenAccent))
            session.maxAltitudeMeters?.let {
                list.add(StatBadge("最高海拔", TrackFormatters.formatAltitude(it), primaryAccent))
            }
        }
        if (config.showSpeed) {
            if (isKart || session.activityMode == ActivityMode.OFFROAD_CROSSING) {
                list.add(StatBadge("最高时速", TrackFormatters.formatSpeed(session.maxSpeedMps, speedUnit), amberAccent))
            } else {
                list.add(
                    StatBadge(
                        "平均配速",
                        TrackFormatters.formatPaceMinPerKm(session.distanceMeters, session.durationMs),
                        amberAccent
                    )
                )
            }
        }
        if (config.showHeartRate && session.avgHeartRate != null) {
            list.add(StatBadge("平均心率", "${session.avgHeartRate} bpm", heartAccent))
        }
        if (config.showPeakG && session.peakTotalG > 0.05f) {
            list.add(StatBadge("峰值 G 力", String.format(Locale.US, "%.2f G", session.peakTotalG), primaryAccent))
        }
        return list.take(6)
    }

    fun renderBitmap(
        detail: TrackSessionDetail,
        speedUnit: SpeedUnit,
        config: SocialCardConfig,
        photoBitmap: Bitmap? = null,
        targetWidth: Int = 1080
    ): Bitmap {
        val activePhoto = if (config.useImportedPhoto && photoBitmap != null && !photoBitmap.isRecycled) {
            photoBitmap
        } else {
            null
        }
        val hasPhoto = activePhoto != null
        val aspectRatio = if (activePhoto != null) {
            (activePhoto.height.toFloat() / activePhoto.width.toFloat()).coerceIn(0.75f, 1.38f)
        } else {
            1.25f // Clean 4:5 portrait social poster
        }
        val width = targetWidth.coerceIn(480, 1440)
        val height = (width * aspectRatio).roundToInt().coerceAtLeast(480)

        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val s = width / 1080f

        val inkPrimary = if (config.useLightInk) Color.WHITE else 0xFF171C24.toInt()
        val inkSecondary = if (config.useLightInk) 0xCCFFFFFF.toInt() else 0xB83C4554.toInt()

        // 1. Background layer (Photo / Transparent PNG / Solid-Gradient color)
        if (activePhoto != null) {
            drawCenterCropPhoto(canvas, activePhoto, width, height)
            if (config.overlayBackdrop == SocialOverlayBackdrop.SCRIM) {
                val topScrim = Paint().apply {
                    shader = LinearGradient(
                        0f, 0f, 0f, height * 0.28f,
                        0x99000000.toInt(), Color.TRANSPARENT,
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, width.toFloat(), height * 0.28f, topScrim)

                val bottomScrim = Paint().apply {
                    shader = LinearGradient(
                        0f, height * 0.48f, 0f, height.toFloat(),
                        Color.TRANSPARENT, 0xCC000000.toInt(),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, height * 0.48f, width.toFloat(), height.toFloat(), bottomScrim)
            }
        } else if (!config.bgPreset.isTransparent) {
            out.eraseColor(config.bgPreset.topColorArgb)
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    config.bgPreset.topColorArgb,
                    config.bgPreset.bottomColorArgb,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        }

        val padX = 64f * s
        var topCursorY = 64f * s

        // 2. Title & date header
        val session = detail.session
        if (config.showTitle) {
            val modePillBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (config.useLightInk) 0x38FFFFFF else 0x1F1A73E8
            }
            val pillTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = inkPrimary
                textSize = 26f * s
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val modeText = session.activityMode.labelZh
            val modeTextW = pillTextPaint.measureText(modeText)
            val pillRect = RectF(
                padX,
                topCursorY,
                padX + modeTextW + 38f * s,
                topCursorY + 48f * s
            )
            canvas.drawRoundRect(pillRect, 24f * s, 24f * s, modePillBg)
            canvas.drawText(
                modeText,
                pillRect.left + 19f * s,
                pillRect.top + 33f * s,
                pillTextPaint
            )

            val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = inkSecondary
                textSize = 26f * s
                typeface = Typeface.MONOSPACE
            }
            val dateStr = TrackFormatters.formatDateTime(session.startTimeMs)
            canvas.drawText(
                dateStr,
                pillRect.right + 20f * s,
                pillRect.top + 33f * s,
                datePaint
            )

            topCursorY = pillRect.bottom + 24f * s

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = inkPrimary
                textSize = 46f * s
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                if (hasPhoto && config.useLightInk) {
                    setShadowLayer(8f * s, 0f, 2f * s, 0x80000000.toInt())
                }
            }
            val displayTitle = ellipsize(session.name, titlePaint, width - padX * 2f)
            canvas.drawText(displayTitle, padX, topCursorY + 38f * s, titlePaint)
            topCursorY += 66f * s
        }

        // Compute bottom block height for stats + optional mini profile curve
        val stats = buildSelectedStats(detail, speedUnit, config)
        val hasMiniProfile = config.showMiniProfile && detail.points.size >= 2
        val statRows = if (stats.isEmpty()) 0 else ((stats.size + 2) / 3)
        val statsBlockHeight = if (statRows > 0) statRows * (112f * s) + 28f * s else 0f
        val profileBlockHeight = if (hasMiniProfile) 128f * s else 0f
        val bottomPad = 56f * s
        val bottomPanelHeight = statsBlockHeight + profileBlockHeight +
            if (statsBlockHeight > 0f && profileBlockHeight > 0f) 24f * s else 0f
        val bottomPanelTop = (height - bottomPad - bottomPanelHeight).coerceAtLeast(topCursorY + 80f * s)

        // 3. Vector Route Overlay in the visual hero zone
        if (config.showRoute && detail.points.size >= 2) {
            val routeTop = topCursorY + 18f * s
            val routeBottom = (bottomPanelTop - 28f * s).coerceAtLeast(routeTop + 140f * s)
            val routeRect = RectF(padX, routeTop, width - padX, routeBottom)
            drawRouteOverlay(
                canvas = canvas,
                rect = routeRect,
                points = detail.points,
                mode = session.activityMode,
                speedUnit = speedUnit,
                scale = s,
                lightInk = config.useLightInk,
                addPhotoGlow = hasPhoto || config.bgPreset.isTransparent
            )
        }

        // 4. Bottom Stats & Mini Profile Panel
        if (bottomPanelHeight > 0f) {
            val panelRect = RectF(
                padX - 16f * s,
                bottomPanelTop - 20f * s,
                width - padX + 16f * s,
                height - bottomPad + 16f * s
            )

            if (config.overlayBackdrop == SocialOverlayBackdrop.FROSTED) {
                val frostedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (config.useLightInk) 0xA6101620.toInt() else 0xD9FFFFFF.toInt()
                }
                canvas.drawRoundRect(panelRect, 36f * s, 36f * s, frostedPaint)
            }

            var cursorY = bottomPanelTop
            if (stats.isNotEmpty()) {
                drawStatsGrid(
                    canvas = canvas,
                    stats = stats,
                    left = padX,
                    top = cursorY,
                    width = width - padX * 2f,
                    scale = s,
                    inkSecondary = inkSecondary,
                    addShadow = hasPhoto && config.overlayBackdrop != SocialOverlayBackdrop.FROSTED && config.useLightInk
                )
                cursorY += statsBlockHeight
            }

            if (hasMiniProfile) {
                if (stats.isNotEmpty()) cursorY += 16f * s
                val profileRect = RectF(padX, cursorY, width - padX, cursorY + 112f * s)
                drawMiniProfileCurve(
                    canvas = canvas,
                    rect = profileRect,
                    points = detail.points,
                    mode = session.activityMode,
                    speedUnit = speedUnit,
                    scale = s,
                    lightInk = config.useLightInk,
                    inkSecondary = inkSecondary
                )
            }
        }

        return out
    }

    private fun drawCenterCropPhoto(canvas: Canvas, photo: Bitmap, dstW: Int, dstH: Int) {
        val srcW = photo.width.toFloat()
        val srcH = photo.height.toFloat()
        val scale = max(dstW / srcW, dstH / srcH)
        val cropW = (dstW / scale).roundToInt().coerceIn(1, photo.width)
        val cropH = (dstH / scale).roundToInt().coerceIn(1, photo.height)
        val left = ((photo.width - cropW) / 2).coerceAtLeast(0)
        val top = ((photo.height - cropH) / 2).coerceAtLeast(0)
        val srcRect = Rect(left, top, left + cropW, top + cropH)
        val dstRect = Rect(0, 0, dstW, dstH)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(photo, srcRect, dstRect, paint)
    }

    private fun drawRouteOverlay(
        canvas: Canvas,
        rect: RectF,
        points: List<TrackPointEntity>,
        mode: ActivityMode,
        speedUnit: SpeedUnit,
        scale: Float,
        lightInk: Boolean,
        addPhotoGlow: Boolean
    ) {
        val avgLat = points.map { it.latitude }.average()
        val lonScale = cos(Math.toRadians(avgLat)).coerceIn(0.2, 1.0)
        val xs = points.map { it.longitude * lonScale }
        val ys = points.map { it.latitude }
        val minX = xs.min()
        val maxX = xs.max()
        val minY = ys.min()
        val maxY = ys.max()

        val spanX = (maxX - minX).coerceAtLeast(1e-5)
        val spanY = (maxY - minY).coerceAtLeast(1e-5)

        val pad = 34f * scale
        val drawW = (rect.width() - pad * 2f).coerceAtLeast(1f)
        val drawH = (rect.height() - pad * 2f).coerceAtLeast(1f)
        val fitScale = min(drawW / spanX.toFloat(), drawH / spanY.toFloat())
        val offsetX = rect.left + pad + (drawW - spanX.toFloat() * fitScale) / 2f
        val offsetY = rect.top + pad + (drawH - spanY.toFloat() * fitScale) / 2f

        val sx = FloatArray(points.size)
        val sy = FloatArray(points.size)
        for (i in points.indices) {
            sx[i] = offsetX + ((xs[i] - minX).toFloat() * fitScale)
            sy[i] = rect.bottom - (offsetY - rect.top + ((ys[i] - minY).toFloat() * fitScale))
        }

        val metrics = if (mode == ActivityMode.KARTING) {
            points.map { speedUnit.convertFromMps(it.speedMps).toDouble() }
        } else {
            points.map { it.altitudeMeters ?: 0.0 }
        }
        val minM = metrics.minOrNull() ?: 0.0
        val maxM = metrics.maxOrNull() ?: 1.0
        val spanM = (maxM - minM).coerceAtLeast(1.0)

        // Outer soft halo/shadow stroke so route stays ultra-legible on photos & transparent PNGs
        if (addPhotoGlow) {
            val outlinePath = Path().apply {
                moveTo(sx[0], sy[0])
                for (i in 1 until points.size) lineTo(sx[i], sy[i])
            }
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 18f * scale
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = if (lightInk) 0x66000000 else 0x55FFFFFF
            }
            canvas.drawPath(outlinePath, glowPaint)
        }

        val lowCol = if (lightInk) 0xFF66B2FF.toInt() else 0xFF1A73E8.toInt()
        val midCol = if (lightInk) 0xFF6DD58C.toInt() else 0xFF188038.toInt()
        val highCol = if (lightInk) 0xFFFDD663.toInt() else 0xFFE37400.toInt()
        val peakCol = if (lightInk) 0xFFFF6B6B.toInt() else 0xFFD93025.toInt()

        val segPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 11f * scale
            strokeCap = Paint.Cap.ROUND
        }

        for (i in 0 until points.lastIndex) {
            val ratio = (((metrics[i] + metrics[i + 1]) / 2.0 - minM) / spanM)
                .toFloat()
                .coerceIn(0f, 1f)
            segPaint.color = if (mode == ActivityMode.KARTING) {
                when {
                    ratio < 0.33f -> lerpArgb(lowCol, midCol, ratio / 0.33f)
                    ratio < 0.66f -> lerpArgb(midCol, highCol, (ratio - 0.33f) / 0.33f)
                    else -> lerpArgb(highCol, peakCol, (ratio - 0.66f) / 0.34f)
                }
            } else {
                if (ratio < 0.5f) lerpArgb(lowCol, midCol, ratio / 0.5f)
                else lerpArgb(midCol, highCol, (ratio - 0.5f) / 0.5f)
            }
            canvas.drawLine(sx[i], sy[i], sx[i + 1], sy[i + 1], segPaint)
        }

        // Start & finish circles
        val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        markerPaint.color = Color.WHITE
        canvas.drawCircle(sx[0], sy[0], 14f * scale, markerPaint)
        markerPaint.color = midCol
        canvas.drawCircle(sx[0], sy[0], 9.5f * scale, markerPaint)

        val last = points.lastIndex
        markerPaint.color = Color.WHITE
        canvas.drawCircle(sx[last], sy[last], 13f * scale, markerPaint)
        markerPaint.color = peakCol
        canvas.drawCircle(sx[last], sy[last], 8.5f * scale, markerPaint)
    }

    private fun drawStatsGrid(
        canvas: Canvas,
        stats: List<StatBadge>,
        left: Float,
        top: Float,
        width: Float,
        scale: Float,
        inkSecondary: Int,
        addShadow: Boolean
    ) {
        val cols = min(3, stats.size)
        val colWidth = width / cols.toFloat()

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkSecondary
            textSize = 25f * scale
            if (addShadow) setShadowLayer(4f * scale, 0f, 1.5f * scale, 0x80000000.toInt())
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 44f * scale
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            if (addShadow) setShadowLayer(6f * scale, 0f, 2f * scale, 0x8C000000.toInt())
        }

        stats.forEachIndexed { idx, stat ->
            val row = idx / 3
            val col = idx % 3
            val x = left + col * colWidth
            val y = top + row * (112f * scale)
            canvas.drawText(stat.label, x, y + 26f * scale, labelPaint)
            valuePaint.color = stat.accentArgb
            val fitValue = ellipsize(stat.value, valuePaint, colWidth - 16f * scale)
            canvas.drawText(fitValue, x, y + 80f * scale, valuePaint)
        }
    }

    private fun drawMiniProfileCurve(
        canvas: Canvas,
        rect: RectF,
        points: List<TrackPointEntity>,
        mode: ActivityMode,
        speedUnit: SpeedUnit,
        scale: Float,
        lightInk: Boolean,
        inkSecondary: Int
    ) {
        val useElevationProfile = mode.isElevationTrail && points.any { it.altitudeMeters != null }
        val values = if (useElevationProfile) {
            points.mapNotNull { it.altitudeMeters?.toFloat() }
        } else {
            points.map { speedUnit.convertFromMps(it.speedMps) }
        }
        if (values.size < 2) return

        val title = if (useElevationProfile) "海拔剖面" else "速度剖面"
        val unit = if (useElevationProfile) "m" else speedUnit.label
        val lineColor = if (useElevationProfile) {
            if (lightInk) 0xFF81C995.toInt() else 0xFF137333.toInt()
        } else {
            if (lightInk) 0xFF8AB4F8.toInt() else 0xFF1A73E8.toInt()
        }

        val minV = values.min()
        val maxV = values.max()
        val span = (maxV - minV).coerceAtLeast(1f)

        val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkSecondary
            textSize = 22f * scale
        }
        canvas.drawText(title, rect.left, rect.top + 20f * scale, capPaint)

        val rangeStr = String.format(Locale.US, "%.0f – %.0f %s", minV, maxV, unit)
        val rangePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = lineColor
            textSize = 22f * scale
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(rangeStr, rect.right, rect.top + 20f * scale, rangePaint)

        val chartTop = rect.top + 34f * scale
        val chartBottom = rect.bottom
        val chartH = (chartBottom - chartTop).coerceAtLeast(10f)

        val linePath = Path()
        val fillPath = Path()
        val step = (values.size / 120).coerceAtLeast(1)
        val sampled = values.filterIndexed { i, _ -> i % step == 0 || i == values.lastIndex }

        sampled.forEachIndexed { idx, v ->
            val x = rect.left + rect.width() * idx / (sampled.size - 1).coerceAtLeast(1)
            val norm = ((v - minV) / span).coerceIn(0f, 1f)
            val y = chartBottom - norm * (chartH - 6f * scale) - 3f * scale
            if (idx == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, chartBottom)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
            if (idx == sampled.lastIndex) {
                fillPath.lineTo(x, chartBottom)
                fillPath.close()
            }
        }

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, chartTop, 0f, chartBottom,
                (lineColor and 0x00FFFFFF) or 0x55000000,
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(fillPath, fillPaint)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = lineColor
            strokeWidth = 4.5f * scale
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(linePath, strokePaint)
    }

    fun decodeSampledPhoto(context: Context, uri: Uri, maxDim: Int = 1440): Bitmap? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val w = info.size.width
                    val h = info.size.height
                    val longest = max(w, h)
                    if (longest > maxDim && longest > 0) {
                        val ratio = maxDim.toFloat() / longest.toFloat()
                        decoder.setTargetSize(
                            (w * ratio).roundToInt().coerceAtLeast(1),
                            (h * ratio).roundToInt().coerceAtLeast(1)
                        )
                    }
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                var sampleSize = 1
                while (max(bounds.outWidth, bounds.outHeight) / sampleSize > maxDim) {
                    sampleSize *= 2
                }
                val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
            }
        }.getOrNull()
    }

    fun savePngToGallery(context: Context, bitmap: Bitmap, fileNamePrefix: String): Boolean {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val displayName = "${fileNamePrefix}_${stamp}.png"

        return runCatching {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/GPS悬浮仪表"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false
            resolver.openOutputStream(uri)?.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    return false
                }
            } ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            true
        }.getOrDefault(false)
    }

    fun exportPngToShareUri(context: Context, bitmap: Bitmap, fileNamePrefix: String): Uri {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "${fileNamePrefix}_${stamp}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    private fun lerpArgb(start: Int, end: Int, fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        val a = (Color.alpha(start) + (Color.alpha(end) - Color.alpha(start)) * f).roundToInt()
        val r = (Color.red(start) + (Color.red(end) - Color.red(start)) * f).roundToInt()
        val g = (Color.green(start) + (Color.green(end) - Color.green(start)) * f).roundToInt()
        val b = (Color.blue(start) + (Color.blue(end) - Color.blue(start)) * f).roundToInt()
        return Color.argb(a, r, g, b)
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) {
            end--
        }
        return text.substring(0, end) + "…"
    }
}
