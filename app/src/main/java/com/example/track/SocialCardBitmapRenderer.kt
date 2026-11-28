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
    val showClimbDifficulty: Boolean = false,
    val showSurfaceComplexity: Boolean = false,
    val showBodyPerformance: Boolean = false,
    val showF1Sectors: Boolean = true,
    val showMiniProfile: Boolean = true,
    val layout: SocialCardLayout = SocialCardLayout.ROUTE_FOCUS,
    val showSteps: Boolean = false,
    val posterEdits: SocialPosterEdits = SocialPosterEdits()
)

/**
 * Pure Android Canvas renderer producing crisp ARGB_8888 social share posters & transparent PNG
 * telemetry stickers for Hiking, Karting, and Off-road Crossing track records.
 */
object SocialCardBitmapRenderer {

    data class StatBadge(
        val label: String,
        val value: String,
        val accentArgb: Int
    )

    data class OffroadOverlayTag(
        val category: String,
        val headline: String,
        val detailLine: String,
        val accentArgb: Int
    )

    fun buildOffroadOverlayTags(
        detail: TrackSessionDetail,
        config: SocialCardConfig
    ): List<OffroadOverlayTag> {
        if (detail.session.activityMode != ActivityMode.OFFROAD_CROSSING) return emptyList()
        return buildAnalysisOverlayTags(detail, config)
    }

    fun buildAnalysisOverlayTags(
        detail: TrackSessionDetail,
        config: SocialCardConfig
    ): List<OffroadOverlayTag> {
        if (!config.showClimbDifficulty && !config.showSurfaceComplexity && !config.showBodyPerformance) {
            return emptyList()
        }

        val green = if (config.useLightInk) 0xFF81C995.toInt() else 0xFF137333.toInt()
        val blue = if (config.useLightInk) 0xFF8AB4F8.toInt() else 0xFF1A73E8.toInt()
        val amber = if (config.useLightInk) 0xFFFDD663.toInt() else 0xFFC56000.toInt()
        val red = if (config.useLightInk) 0xFFF28B82.toInt() else 0xFFD93025.toInt()

        val tags = mutableListOf<OffroadOverlayTag>()

        when (detail.session.activityMode) {
            ActivityMode.OFFROAD_CROSSING -> {
                val analysis = OffroadAnalysisEngine.analyze(detail.session, detail.points)
                if (config.showClimbDifficulty) {
                    val c = analysis.climb
                    val accent = when (c.level) {
                        ClimbDifficultyLevel.EASY -> green
                        ClimbDifficultyLevel.MODERATE -> blue
                        ClimbDifficultyLevel.CHALLENGING -> amber
                        ClimbDifficultyLevel.EXTREME -> red
                    }
                    tags.add(
                        OffroadOverlayTag(
                            category = "爬升难度",
                            headline = c.level.fullLabel,
                            detailLine = String.format(
                                Locale.US,
                                "指数 %d · 最大坡度 %.1f%% · %.0fm/km",
                                c.score,
                                c.maxGradePct,
                                c.gainPerKmMeters
                            ),
                            accentArgb = accent
                        )
                    )
                }
                if (config.showSurfaceComplexity) {
                    val sf = analysis.surface
                    val accent = when (sf.level) {
                        SurfaceComplexityLevel.SMOOTH -> green
                        SurfaceComplexityLevel.TWISTY -> blue
                        SurfaceComplexityLevel.ROUGH -> amber
                        SurfaceComplexityLevel.EXTREME -> red
                    }
                    tags.add(
                        OffroadOverlayTag(
                            category = "路面复杂度",
                            headline = sf.level.fullLabel,
                            detailLine = String.format(
                                Locale.US,
                                "指数 %d · 非铺装 %d%% · %.1f弯/km",
                                sf.score,
                                sf.technicalPct,
                                sf.turnsPerKm
                            ),
                            accentArgb = accent
                        )
                    )
                }
            }

            ActivityMode.HIKING -> {
                val analysis = TrackTelemetryAnalyzer.analyzeHiking(detail.session, detail.points)
                val compact = config.showClimbDifficulty && config.showSurfaceComplexity && config.showBodyPerformance
                if (config.showClimbDifficulty) {
                    val d = analysis.difficulty
                    val accent = when (d.level) {
                        HikeDifficultyLevel.EASY -> green
                        HikeDifficultyLevel.MODERATE -> blue
                        HikeDifficultyLevel.CHALLENGING -> amber
                        HikeDifficultyLevel.EXTREME -> red
                    }
                    tags.add(
                        OffroadOverlayTag(
                            category = "难度评价",
                            headline = d.level.fullLabel,
                            detailLine = if (compact) {
                                String.format(Locale.US, "指数 %d · 坡度 %.1f%%", d.score, d.maxGradePct)
                            } else {
                                String.format(Locale.US, "指数 %d · 最大坡度 %.1f%% · %.0fm/km", d.score, d.maxGradePct, d.gainPerKmMeters)
                            },
                            accentArgb = accent
                        )
                    )
                }
                if (config.showSurfaceComplexity) {
                    val sf = analysis.surface
                    val accent = when (sf.level) {
                        SurfaceComplexityLevel.SMOOTH -> green
                        SurfaceComplexityLevel.TWISTY -> blue
                        SurfaceComplexityLevel.ROUGH -> amber
                        SurfaceComplexityLevel.EXTREME -> red
                    }
                    tags.add(
                        OffroadOverlayTag(
                            category = "路面复杂度",
                            headline = sf.level.fullLabel,
                            detailLine = if (compact) {
                                String.format(Locale.US, "指数 %d · 复杂 %d%%", sf.score, sf.technicalPct)
                            } else {
                                String.format(Locale.US, "指数 %d · 复杂路 %d%% · %.1f弯/km", sf.score, sf.technicalPct, sf.turnsPerKm)
                            },
                            accentArgb = accent
                        )
                    )
                }
                if (config.showBodyPerformance) {
                    val bp = analysis.body
                    val accent = when (bp.level) {
                        BodyPerformanceLevel.RECOVERY -> green
                        BodyPerformanceLevel.AEROBIC -> blue
                        BodyPerformanceLevel.INTENSE -> amber
                        BodyPerformanceLevel.PEAK -> red
                    }
                    val detailText = if (bp.avgHeartRate != null) {
                        if (compact) {
                            "均心率 ${bp.avgHeartRate} · ${bp.estimatedCaloriesKcal}kcal"
                        } else {
                            "均心率 ${bp.avgHeartRate} bpm · ${bp.estimatedCaloriesKcal} kcal"
                        }
                    } else {
                        "指数 ${bp.score} · ${bp.estimatedCaloriesKcal} kcal"
                    }
                    tags.add(
                        OffroadOverlayTag(
                            category = "身体表现",
                            headline = bp.level.fullLabel,
                            detailLine = detailText,
                            accentArgb = accent
                        )
                    )
                }
            }

            ActivityMode.KARTING -> {
                val analysis = TrackTelemetryAnalyzer.analyzeKarting(detail.session, detail.points, detail.laps)
                if (config.showClimbDifficulty) {
                    val d = analysis.difficulty
                    val accent = when (d.level) {
                        KartDifficultyLevel.EASY -> green
                        KartDifficultyLevel.MODERATE -> blue
                        KartDifficultyLevel.CHALLENGING -> amber
                        KartDifficultyLevel.EXTREME -> red
                    }
                    tags.add(
                        OffroadOverlayTag(
                            category = "赛道难度",
                            headline = d.level.fullLabel,
                            detailLine = String.format(
                                Locale.US,
                                "指数 %d · 横向 %.2fG · 急弯 %d处",
                                d.score,
                                d.peakLateralG,
                                d.hairpinCount
                            ),
                            accentArgb = accent
                        )
                    )
                }
                if (config.showBodyPerformance) {
                    val bp = analysis.body
                    val accent = when (bp.level) {
                        BodyPerformanceLevel.RECOVERY -> green
                        BodyPerformanceLevel.AEROBIC -> blue
                        BodyPerformanceLevel.INTENSE -> amber
                        BodyPerformanceLevel.PEAK -> red
                    }
                    val detailText = if (bp.avgHeartRate != null) {
                        "均心率 ${bp.avgHeartRate} bpm · ${bp.stabilityLabelZh} ${bp.stabilityPct}%"
                    } else {
                        "${bp.stabilityLabelZh} ${bp.stabilityPct}% · ${bp.estimatedCaloriesKcal} kcal"
                    }
                    tags.add(
                        OffroadOverlayTag(
                            category = "身体表现",
                            headline = bp.level.fullLabel,
                            detailLine = detailText,
                            accentArgb = accent
                        )
                    )
                }
            }
        }
        return tags
    }

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
        if (config.showSteps && session.activityMode == ActivityMode.HIKING) {
            session.stepCount?.let { list.add(StatBadge("步数", "$it 步", greenAccent)) }
        }
        return list
    }

    fun renderBitmap(
        detail: TrackSessionDetail,
        speedUnit: SpeedUnit,
        config: SocialCardConfig,
        photoBitmap: Bitmap? = null,
        targetWidth: Int = 1080
    ): Bitmap {
        if (config.useImportedPhoto && photoBitmap != null && !photoBitmap.isRecycled) {
            val output = checkNotNull(photoBitmap.copy(Bitmap.Config.ARGB_8888, true))
            drawPhotoPoster(output, detail, speedUnit, config)
            return output
        }

        val width = targetWidth.coerceIn(480, 1440)
        val height = (width * 1.25f).roundToInt()
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        if (!config.bgPreset.isTransparent) {
            output.eraseColor(config.bgPreset.topColorArgb)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    config.bgPreset.topColorArgb, config.bgPreset.bottomColorArgb, Shader.TileMode.CLAMP
                )
            }
            Canvas(output).drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
        drawStandaloneTelemetry(output, detail, speedUnit, config)
        return output
    }

    fun renderExportBitmap(
        context: Context,
        detail: TrackSessionDetail,
        speedUnit: SpeedUnit,
        config: SocialCardConfig,
        photoUri: Uri?
    ): Bitmap {
        if (!config.useImportedPhoto) return renderBitmap(detail, speedUnit, config)
        val photo = checkNotNull(decodePhoto(context, checkNotNull(photoUri), maxDim = null)) {
            "Could not decode the original photo"
        }
        // The decoded original belongs to this export, so no second full-size bitmap is needed.
        try {
            drawPhotoPoster(photo, detail, speedUnit, config)
            return photo
        } catch (e: Throwable) {
            photo.recycle()
            throw e
        }
    }

    private data class PosterArtwork(
        val layer: SocialPosterLayer,
        val width: Int,
        val height: Int,
        val paint: (Canvas) -> Unit
    )

    // Render only when the artwork changes. Moving or scaling a layer reuses its preview bitmap.
    internal fun renderPhotoPosterLayers(
        detail: TrackSessionDetail,
        speedUnit: SpeedUnit,
        config: SocialCardConfig
    ): Map<SocialPosterLayer, Bitmap> = photoPosterArtwork(detail, speedUnit, config).associate { art ->
        art.layer to Bitmap.createBitmap(art.width, art.height, Bitmap.Config.ARGB_8888).also {
            art.paint(Canvas(it))
        }
    }

    private fun drawPhotoPoster(
        bitmap: Bitmap,
        detail: TrackSessionDetail,
        speedUnit: SpeedUnit,
        config: SocialCardConfig
    ) {
        val canvas = Canvas(bitmap)
        for (art in photoPosterArtwork(detail, speedUnit, config)) {
            val frame = socialPosterFrame(
                bitmap.width.toFloat(), bitmap.height.toFloat(), art.width, art.height, art.layer, config
            )
            val save = canvas.save()
            canvas.translate(frame.left, frame.top)
            val zoom = frame.width / art.width
            canvas.scale(zoom, zoom)
            canvas.clipRect(0f, 0f, art.width.toFloat(), art.height.toFloat())
            art.paint(canvas)
            canvas.restoreToCount(save)
        }
    }

    private fun photoPosterArtwork(
        detail: TrackSessionDetail,
        speedUnit: SpeedUnit,
        config: SocialCardConfig
    ): List<PosterArtwork> = buildList {
        val session = detail.session
        val mode = session.activityMode
        val hasTrack = detail.points.size >= 2
        val primary = if (config.useLightInk) Color.WHITE else 0xFF171C24.toInt()
        val secondary = if (config.useLightInk) 0xEAFFFFFF.toInt() else 0xFF3C4554.toInt()
        val shadow = config.overlayBackdrop != SocialOverlayBackdrop.FROSTED && config.useLightInk
        val f1 = if (mode == ActivityMode.KARTING && config.showF1Sectors) {
            KartingSectorEngine.analyze(detail.points, detail.laps)
        } else null
        val bestLap = f1?.timingForLap(f1.bestLapNumber)

        if (config.showRoute && hasTrack) {
            val points = detail.points
            val xSpan = (points.maxOf { it.longitude } - points.minOf { it.longitude }) *
                cos(Math.toRadians(points.map { it.latitude }.average())).coerceIn(0.2, 1.0)
            val ySpan = points.maxOf { it.latitude } - points.minOf { it.latitude }
            val aspect = (xSpan.coerceAtLeast(1e-5) / ySpan.coerceAtLeast(1e-5)).coerceIn(0.24, 4.0)
            val width = if (aspect >= 1) 1000 else (1000 * aspect).roundToInt()
            val height = if (aspect >= 1) (1000 / aspect).roundToInt() else 1000
            add(PosterArtwork(SocialPosterLayer.ROUTE, width, height) { canvas ->
                drawRouteOverlay(
                    canvas, RectF(0f, 0f, width.toFloat(), height.toFloat()), points, mode, speedUnit,
                    scale = 0.64f, lightInk = config.useLightInk, addPhotoGlow = true,
                    kartingF1 = f1, f1LapTiming = bestLap, posterInk = true
                )
            })
        }

        if (config.showTitle) {
            add(PosterArtwork(SocialPosterLayer.TITLE, 1000, 194) { canvas ->
                val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = primary
                    textSize = 86f
                    typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
                    setShadowLayer(10f, 0f, 2f, if (config.useLightInk) 0xA8000000.toInt() else 0x9AFFFFFF.toInt())
                }
                val kicker = Paint(title).apply {
                    textSize = 30f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                val date = SimpleDateFormat("yyyy.MM.dd", Locale.US).format(Date(session.startTimeMs))
                canvas.drawText("${mode.labelZh}   /   $date", 20f, 54f, kicker)
                val name = session.name
                val width = title.measureText(name)
                if (width > 960f) title.textSize *= (960f / width).coerceAtLeast(0.71f)
                canvas.drawText(ellipsize(name, title, 960f), 18f, 157f, title)
            })
        }

        val stats = buildSelectedStats(detail, speedUnit, config)
        val tags = buildAnalysisOverlayTags(detail, config)
        val showProfile = config.showMiniProfile && hasTrack
        if (stats.isNotEmpty() || tags.isNotEmpty() || bestLap != null || showProfile) {
            val isSide = config.layout == SocialCardLayout.SIDE_BY_SIDE
            val width = if (isSide) 770 else 1140
            val columns = if (isSide) 2 else 3
            val groups = listOfNotNull(
                if (stats.isNotEmpty()) ((stats.size + columns - 1) / columns) * 88 else null,
                if (bestLap != null) 142 else null,
                if (tags.isNotEmpty()) 114 else null,
                if (showProfile) 88 else null
            )
            val height = 44 + groups.sum() + (groups.size - 1) * 14
            add(PosterArtwork(SocialPosterLayer.DATA, width, height) { canvas ->
                val panelWidth = width - 48f
                val isFrosted = config.overlayBackdrop == SocialOverlayBackdrop.FROSTED
                drawPhotoBackdrop(canvas, width.toFloat(), height.toFloat(), config)
                var top = 22f
                if (stats.isNotEmpty()) {
                    drawStatsGrid(
                        canvas, stats, 24f, top, panelWidth, 1f, secondary, shadow,
                        columns = columns, compact = true
                    )
                    top += ((stats.size + columns - 1) / columns) * 88f + 14f
                }
                if (f1 != null && bestLap != null) {
                    drawF1SectorStrip(canvas, f1, bestLap, 24f, top, panelWidth, 142f, 1f,
                        config.useLightInk, primary, secondary, isFrosted)
                    top += 156f
                }
                if (tags.isNotEmpty()) {
                    drawAnalysisRatingPills(canvas, tags, 24f, top, panelWidth, 114f, 1f,
                        config.useLightInk, secondary, isFrosted)
                    top += 128f
                }
                if (showProfile) {
                    drawMiniProfileCurve(canvas, RectF(24f, top, width - 24f, top + 88f), detail.points, mode,
                        speedUnit, 1f, config.useLightInk, secondary)
                }
            })
        }
    }

    private fun drawStandaloneTelemetry(
        bitmap: Bitmap,
        detail: TrackSessionDetail,
        speedUnit: SpeedUnit,
        config: SocialCardConfig
    ) {
        val session = detail.session
        val showRoute = config.showRoute && detail.points.size >= 2
        val isFrosted = config.overlayBackdrop == SocialOverlayBackdrop.FROSTED
        val kartingF1 = if (session.activityMode == ActivityMode.KARTING && config.showF1Sectors) {
            KartingSectorEngine.analyze(detail.points, detail.laps)
        } else {
            null
        }
        val f1BestLapTiming = kartingF1?.timingForLap(kartingF1.bestLapNumber)
        val f1BlockHeight = if (f1BestLapTiming != null) 142f else 0f

        val analysisTags = buildAnalysisOverlayTags(detail, config)
        val stats = buildSelectedStats(detail, speedUnit, config)
        val hasMiniProfile = config.showMiniProfile && detail.points.size >= 2
        val analysisBlockHeight = if (analysisTags.isNotEmpty()) 114f else 0f
        val statRows = if (stats.isEmpty()) 0 else ((stats.size + 2) / 3)
        val statsBlockHeight = if (statRows > 0) statRows * 112f + 28f else 0f
        val profileBlockHeight = if (hasMiniProfile) 128f else 0f
        val bottomPad = 56f
        var bottomPanelHeight = f1BlockHeight + analysisBlockHeight + statsBlockHeight + profileBlockHeight
        if (f1BlockHeight > 0f && (analysisBlockHeight > 0f || statsBlockHeight > 0f || profileBlockHeight > 0f)) {
            bottomPanelHeight += 18f
        }
        if (analysisBlockHeight > 0f && (statsBlockHeight > 0f || profileBlockHeight > 0f)) {
            bottomPanelHeight += 20f
        }
        if (statsBlockHeight > 0f && profileBlockHeight > 0f) {
            bottomPanelHeight += 24f
        }
        if (!config.showTitle && !showRoute && bottomPanelHeight == 0f) return

        val titleRoom = if (config.showTitle) 138f else 0f
        val routeRoom = if (showRoute) 196f else 0f
        val minHeight = 64f + titleRoom + routeRoom + bottomPanelHeight + bottomPad
        val fit = min(bitmap.width / 1080f, bitmap.height / minHeight)
        val width = (bitmap.width / fit).coerceAtMost(1600f)
        val height = bitmap.height / fit
        val canvas = Canvas(bitmap)
        canvas.translate((bitmap.width - width * fit) / 2f, 0f)
        canvas.scale(fit, fit)
        canvas.clipRect(0f, 0f, width, height)
        val inkPrimary = if (config.useLightInk) Color.WHITE else 0xFF171C24.toInt()
        val inkSecondary = if (config.useLightInk) 0xCCFFFFFF.toInt() else 0xB83C4554.toInt()

        val padX = 64f
        var topCursorY = 64f

        // Title and date
        if (config.showTitle) {
            val modePillBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (config.useLightInk) 0x38FFFFFF else 0x1F1A73E8
            }
            val pillTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = inkPrimary
                textSize = 26f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val modeText = session.activityMode.labelZh
            val modeTextW = pillTextPaint.measureText(modeText)
            val pillRect = RectF(
                padX,
                topCursorY,
                padX + modeTextW + 38f,
                topCursorY + 48f
            )
            canvas.drawRoundRect(pillRect, 24f, 24f, modePillBg)
            canvas.drawText(
                modeText,
                pillRect.left + 19f,
                pillRect.top + 33f,
                pillTextPaint
            )

            val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = inkSecondary
                textSize = 26f
                typeface = Typeface.MONOSPACE
            }
            val dateStr = TrackFormatters.formatDateTime(session.startTimeMs)
            canvas.drawText(
                dateStr,
                pillRect.right + 20f,
                pillRect.top + 33f,
                datePaint
            )

            topCursorY = pillRect.bottom + 24f

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = inkPrimary
                textSize = 46f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val displayTitle = ellipsize(session.name, titlePaint, width - padX * 2f)
            canvas.drawText(displayTitle, padX, topCursorY + 38f, titlePaint)
            topCursorY += 66f
        }
        val bottomPanelTop = height - bottomPad - bottomPanelHeight

        // Route between the header and the metrics
        if (showRoute) {
            val routeTop = topCursorY + 18f
            val routeBottom = (bottomPanelTop - 28f).coerceAtLeast(routeTop + 140f)
            val routeRect = RectF(padX, routeTop, width - padX, routeBottom)
            drawRouteOverlay(
                canvas = canvas,
                rect = routeRect,
                points = detail.points,
                mode = session.activityMode,
                speedUnit = speedUnit,
                scale = 1f,
                lightInk = config.useLightInk,
                addPhotoGlow = config.bgPreset.isTransparent,
                kartingF1 = kartingF1,
                f1LapTiming = f1BestLapTiming
            )
        }

        // F1 sectors, analysis badges, selected metrics, and profile
        if (bottomPanelHeight > 0f) {
            val panelRect = RectF(
                padX - 16f,
                bottomPanelTop - 20f,
                width - padX + 16f,
                height - bottomPad + 16f
            )

            if (isFrosted) {
                val frostedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (config.useLightInk) 0xA6101620.toInt() else 0xD9FFFFFF.toInt()
                }
                canvas.drawRoundRect(panelRect, 36f, 36f, frostedPaint)
            }

            var cursorY = bottomPanelTop
            if (kartingF1 != null && f1BestLapTiming != null) {
                drawF1SectorStrip(
                    canvas = canvas,
                    analysis = kartingF1,
                    lapTiming = f1BestLapTiming,
                    left = padX,
                    top = cursorY,
                    width = width - padX * 2f,
                    height = f1BlockHeight,
                    scale = 1f,
                    lightInk = config.useLightInk,
                    inkPrimary = inkPrimary,
                    inkSecondary = inkSecondary,
                    isFrosted = isFrosted
                )
                cursorY += f1BlockHeight + if (analysisTags.isNotEmpty() || stats.isNotEmpty() || hasMiniProfile) 18f else 0f
            }

            if (analysisTags.isNotEmpty()) {
                drawAnalysisRatingPills(
                    canvas = canvas,
                    tags = analysisTags,
                    left = padX,
                    top = cursorY,
                    width = width - padX * 2f,
                    height = analysisBlockHeight,
                    scale = 1f,
                    lightInk = config.useLightInk,
                    inkSecondary = inkSecondary,
                    isFrosted = isFrosted
                )
                cursorY += analysisBlockHeight + if (stats.isNotEmpty() || hasMiniProfile) 20f else 0f
            }

            if (stats.isNotEmpty()) {
                drawStatsGrid(
                    canvas = canvas,
                    stats = stats,
                    left = padX,
                    top = cursorY,
                    width = width - padX * 2f,
                    scale = 1f,
                    inkSecondary = inkSecondary,
                    addShadow = false
                )
                cursorY += statsBlockHeight
            }

            if (hasMiniProfile) {
                if (stats.isNotEmpty()) cursorY += 16f
                val profileRect = RectF(padX, cursorY, width - padX, cursorY + 112f)
                drawMiniProfileCurve(
                    canvas = canvas,
                    rect = profileRect,
                    points = detail.points,
                    mode = session.activityMode,
                    speedUnit = speedUnit,
                    scale = 1f,
                    lightInk = config.useLightInk,
                    inkSecondary = inkSecondary
                )
            }
        }
    }

    private fun drawPhotoBackdrop(canvas: Canvas, width: Float, height: Float, config: SocialCardConfig) {
        if (config.overlayBackdrop == SocialOverlayBackdrop.CLEAR) return
        val dark = config.useLightInk
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            if (config.overlayBackdrop == SocialOverlayBackdrop.SCRIM) {
                shader = LinearGradient(
                    0f, 0f, 0f, height,
                    if (dark) 0x5010171E else 0x90FFFFFF.toInt(),
                    if (dark) 0xBA10171E.toInt() else 0xD9FFFFFF.toInt(),
                    Shader.TileMode.CLAMP
                )
            } else {
                color = if (dark) 0xBD101620.toInt() else 0xE6FFFFFF.toInt()
            }
        }
        canvas.drawRoundRect(0f, 0f, width, height, 32f, 32f, paint)
    }

    private fun f1StatusColorArgb(status: F1SectorStatus, lightInk: Boolean): Int {
        return when (status) {
            F1SectorStatus.PURPLE -> if (lightInk) 0xFFC864FF.toInt() else 0xFF8E24AA.toInt()
            F1SectorStatus.GREEN -> if (lightInk) 0xFF2EE56F.toInt() else 0xFF15803D.toInt()
            F1SectorStatus.YELLOW -> if (lightInk) 0xFFFACC15.toInt() else 0xFFCA8A04.toInt()
        }
    }

    private fun drawF1SectorStrip(
        canvas: Canvas,
        analysis: KartingF1SectorAnalysis,
        lapTiming: KartingLapSectorTiming,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        scale: Float,
        lightInk: Boolean,
        inkPrimary: Int,
        inkSecondary: Int,
        isFrosted: Boolean
    ) {
        val headerH = 34f * scale
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkPrimary
            textSize = 22f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val titleStr = if (analysis.isMultiLap) {
            "F1 分段计时 · L${lapTiming.lapNumber} 最佳圈"
        } else {
            "F1 分段计时 · 自动 3 段"
        }
        canvas.drawText(titleStr, left, top + 22f * scale, headerPaint)

        val rightMetaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = f1StatusColorArgb(F1SectorStatus.PURPLE, lightInk)
            textSize = 21f * scale
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val rightMetaStr = if (analysis.isMultiLap) {
            "理论最佳 ${TrackFormatters.formatLapTime(analysis.theoreticalBestLapMs)}"
        } else {
            "S1 / S2 / S3"
        }
        canvas.drawText(rightMetaStr, left + width, top + 22f * scale, rightMetaPaint)

        val pillsTop = top + headerH
        val pillH = (height - headerH).coerceAtLeast(64f * scale)
        val gap = 14f * scale
        val count = lapTiming.sectors.size.coerceAtLeast(1)
        val cellW = (width - gap * (count - 1)) / count.toFloat()

        val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when {
                isFrosted && lightInk -> 0x2BFFFFFF
                isFrosted && !lightInk -> 0x12000000
                lightInk -> 0x590D131C
                else -> 0xD6FFFFFF.toInt()
            }
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * scale
        }
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        val sectorLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 22f * scale
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val statusBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 18f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkPrimary
            textSize = 31f * scale
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val deltaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkSecondary
            textSize = 18f * scale
            typeface = Typeface.MONOSPACE
        }

        lapTiming.sectors.forEachIndexed { idx, split ->
            val x = left + idx * (cellW + gap)
            val rect = RectF(x, pillsTop, x + cellW, pillsTop + pillH)
            val statusColor = f1StatusColorArgb(split.status, lightInk)

            canvas.drawRoundRect(rect, 20f * scale, 20f * scale, cardBgPaint)
            borderPaint.color = (statusColor and 0x00FFFFFF) or 0x99000000.toInt()
            canvas.drawRoundRect(rect, 20f * scale, 20f * scale, borderPaint)

            // Top F1 status color accent strip inside the sector pill
            barPaint.color = statusColor
            val barRect = RectF(
                x + 14f * scale,
                pillsTop + 7f * scale,
                x + cellW - 14f * scale,
                pillsTop + 13f * scale
            )
            canvas.drawRoundRect(barRect, 4f * scale, 4f * scale, barPaint)

            val pad = 16f * scale
            sectorLabelPaint.color = statusColor
            canvas.drawText(split.label, x + pad, pillsTop + 39f * scale, sectorLabelPaint)

            statusBadgePaint.color = statusColor
            canvas.drawText(split.status.labelZh, x + cellW - pad, pillsTop + 38f * scale, statusBadgePaint)

            val secTimeStr = String.format(Locale.US, "%.2fs", split.durationMs / 1000.0)
            canvas.drawText(secTimeStr, x + pad, pillsTop + 73f * scale, timePaint)

            val deltaText = when {
                split.status == F1SectorStatus.PURPLE || split.deltaToBestMs <= 15L -> "全场最快"
                split.status == F1SectorStatus.GREEN -> String.format(Locale.US, "+%.2fs · PB", split.deltaToBestMs / 1000.0)
                else -> String.format(Locale.US, "+%.2fs", split.deltaToBestMs / 1000.0)
            }
            val fitDelta = ellipsize(deltaText, deltaPaint, cellW - pad * 2f)
            canvas.drawText(fitDelta, x + pad, pillsTop + 98f * scale, deltaPaint)
        }
    }

    private fun drawAnalysisRatingPills(
        canvas: Canvas,
        tags: List<OffroadOverlayTag>,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        scale: Float,
        lightInk: Boolean,
        inkSecondary: Int,
        isFrosted: Boolean
    ) {
        val count = tags.size.coerceAtLeast(1)
        val isCompactThree = count >= 3
        val gap = (if (isCompactThree) 12f else 16f) * scale
        val cellW = (width - gap * (count - 1)) / count.toFloat()

        val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when {
                isFrosted && lightInk -> 0x26FFFFFF
                isFrosted && !lightInk -> 0x12000000
                lightInk -> 0x4D0D131C
                else -> 0xCCFFFFFF.toInt()
            }
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f * scale
        }
        val catPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkSecondary
            textSize = (if (isCompactThree) 19f else 21f) * scale
        }
        val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = (if (isCompactThree) 26f else 31f) * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkSecondary
            textSize = (if (isCompactThree) 18f else 20f) * scale
            typeface = Typeface.MONOSPACE
        }

        tags.forEachIndexed { idx, tag ->
            val x = left + idx * (cellW + gap)
            val rect = RectF(x, top, x + cellW, top + height)
            canvas.drawRoundRect(rect, 22f * scale, 22f * scale, cardBgPaint)

            borderPaint.color = (tag.accentArgb and 0x00FFFFFF) or 0x66000000
            canvas.drawRoundRect(rect, 22f * scale, 22f * scale, borderPaint)

            val innerPad = (if (isCompactThree) 14f else 18f) * scale
            val maxTextW = (cellW - innerPad * 2f).coerceAtLeast(20f)
            canvas.drawText(tag.category, x + innerPad, top + 28f * scale, catPaint)

            headPaint.color = tag.accentArgb
            val fitHead = ellipsize(tag.headline, headPaint, maxTextW)
            canvas.drawText(fitHead, x + innerPad, top + 65f * scale, headPaint)

            val fitSub = ellipsize(tag.detailLine, subPaint, maxTextW)
            canvas.drawText(fitSub, x + innerPad, top + 95f * scale, subPaint)
        }
    }

    private fun drawRouteOverlay(
        canvas: Canvas,
        rect: RectF,
        points: List<TrackPointEntity>,
        mode: ActivityMode,
        speedUnit: SpeedUnit,
        scale: Float,
        lightInk: Boolean,
        addPhotoGlow: Boolean,
        kartingF1: KartingF1SectorAnalysis? = null,
        f1LapTiming: KartingLapSectorTiming? = null,
        posterInk: Boolean = false
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
                strokeWidth = (if (posterInk) 25f else 18f) * scale
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = if (posterInk) {
                    if (lightInk) 0xA8000000.toInt() else 0xB8FFFFFF.toInt()
                } else if (lightInk) 0x66000000 else 0x55FFFFFF
            }
            canvas.drawPath(outlinePath, glowPaint)
        }

        val lowCol = if (lightInk) 0xFF66B2FF.toInt() else 0xFF1A73E8.toInt()
        val midCol = if (lightInk) 0xFF6DD58C.toInt() else 0xFF188038.toInt()
        val highCol = if (lightInk) 0xFFFDD663.toInt() else 0xFFE37400.toInt()
        val peakCol = if (lightInk) 0xFFFF6B6B.toInt() else 0xFFD93025.toInt()

        val segPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (if (posterInk) 16f else 11f) * scale
            strokeCap = Paint.Cap.ROUND
        }

        if (mode == ActivityMode.KARTING && kartingF1 != null && f1LapTiming != null) {
            // Draw ghost track base first, then crisp F1 Purple/Green/Yellow best-lap sectors on top
            val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 10f * scale
                strokeCap = Paint.Cap.ROUND
                color = if (lightInk) 0x38FFFFFF else 0x2A1F2937
            }
            for (i in 0 until points.lastIndex) {
                canvas.drawLine(sx[i], sy[i], sx[i + 1], sy[i + 1], ghostPaint)
            }

            segPaint.strokeWidth = 13f * scale
            val startIdx = f1LapTiming.pointIndices.first.coerceIn(0, points.lastIndex)
            val endIdx = f1LapTiming.pointIndices.last.coerceIn(startIdx, points.lastIndex)
            for (i in startIdx until endIdx) {
                val secIdx = (f1LapTiming.pointSectorMap[i] ?: kartingF1.allPointsSectorIndex[i]).coerceIn(0, 2)
                val status = f1LapTiming.sectors.getOrNull(secIdx)?.status ?: F1SectorStatus.YELLOW
                segPaint.color = f1StatusColorArgb(status, lightInk)
                canvas.drawLine(sx[i], sy[i], sx[i + 1], sy[i + 1], segPaint)
            }

            // Draw S1 / S2 / S3 timing gate pill markers directly on the track
            val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = if (lightInk) 0xEB111622.toInt() else 0xF2FFFFFF.toInt()
            }
            val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.5f * scale
            }
            val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 20f * scale
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            kartingF1.gates.forEachIndexed { gIdx, gate ->
                val pIdx = gate.referencePointIndex.coerceIn(0, points.lastIndex)
                val gx = sx[pIdx]
                val gy = sy[pIdx]
                val status = f1LapTiming.sectors.getOrNull(gIdx)?.status ?: F1SectorStatus.PURPLE
                val col = f1StatusColorArgb(status, lightInk)
                val pillW = 48f * scale
                val pillH = 30f * scale
                val pillRect = RectF(gx - pillW / 2f, gy - pillH / 2f, gx + pillW / 2f, gy + pillH / 2f)
                canvas.drawRoundRect(pillRect, 15f * scale, 15f * scale, badgeBgPaint)
                badgeBorderPaint.color = col
                canvas.drawRoundRect(pillRect, 15f * scale, 15f * scale, badgeBorderPaint)
                badgeTextPaint.color = col
                canvas.drawText(gate.label, gx, gy + 7f * scale, badgeTextPaint)
            }
        } else {
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
    }

    private fun drawStatsGrid(
        canvas: Canvas,
        stats: List<StatBadge>,
        left: Float,
        top: Float,
        width: Float,
        scale: Float,
        inkSecondary: Int,
        addShadow: Boolean,
        columns: Int = 3,
        compact: Boolean = false
    ) {
        val cols = min(columns, stats.size)
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
            val row = idx / cols
            val col = idx % cols
            val x = left + col * colWidth
            val y = top + row * ((if (compact) 88f else 112f) * scale)
            val availableWidth = colWidth - 16f * scale
            canvas.drawText(ellipsize(stat.label, labelPaint, availableWidth), x, y + 26f * scale, labelPaint)
            valuePaint.color = if (compact && idx > 0) inkSecondary else stat.accentArgb
            valuePaint.textSize = (if (compact && idx == 0) 51f else 44f) * scale
            val valueWidth = valuePaint.measureText(stat.value)
            if (valueWidth > availableWidth) {
                valuePaint.textSize *= (availableWidth / valueWidth).coerceAtLeast(0.62f)
            }
            val fitValue = ellipsize(stat.value, valuePaint, availableWidth)
            canvas.drawText(fitValue, x, y + (if (compact) 76f else 80f) * scale, valuePaint)
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

    fun decodeSampledPhoto(context: Context, uri: Uri, maxDim: Int = 1440): Bitmap? = decodePhoto(context, uri, maxDim)

    private fun decodePhoto(context: Context, uri: Uri, maxDim: Int?): Bitmap? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = maxDim == null
                    val w = info.size.width
                    val h = info.size.height
                    val longest = max(w, h)
                    if (maxDim != null && longest > maxDim) {
                        val ratio = maxDim.toFloat() / longest.toFloat()
                        decoder.setTargetSize(
                            (w * ratio).roundToInt().coerceAtLeast(1),
                            (h * ratio).roundToInt().coerceAtLeast(1)
                        )
                    }
                }
            } else {
                var sampleSize = 1
                if (maxDim != null) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, bounds)
                    }
                    while (max(bounds.outWidth, bounds.outHeight) / sampleSize > maxDim) {
                        sampleSize *= 2
                    }
                }
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inMutable = maxDim == null
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
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
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "Could not encode PNG" }
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
