package com.example.track

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.gps.SpeedUnit
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SocialCardLayoutTest {
    private val hike = SampleTrackSeeder.buildSampleHikingTrack(1_750_000_000_000L)

    @Test
    fun everyLayoutPaintsOverTheOriginalPhotoWithoutResizingOrCroppingIt() {
        for ((width, height) in listOf(640 to 320, 320 to 640, 760 to 130)) {
            val photo = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            photo.eraseColor(Color.CYAN)
            photo.setPixel(0, 0, Color.RED)
            photo.setPixel(width - 1, 0, Color.YELLOW)
            photo.setPixel(0, height - 1, Color.GREEN)
            photo.setPixel(width - 1, height - 1, Color.MAGENTA)
            for (layout in SocialCardLayout.entries) {
                val config = SocialCardConfig(
                    useImportedPhoto = true, layout = layout, overlayBackdrop = SocialOverlayBackdrop.FROSTED, showSteps = true
                )
                val output = SocialCardBitmapRenderer.renderBitmap(hike, SpeedUnit.KMH, config, photo, targetWidth = 480)
                assertEquals("${layout.name} width", width, output.width)
                assertEquals("${layout.name} height", height, output.height)
                assertEquals(Color.RED, output.getPixel(0, 0))
                assertEquals(Color.YELLOW, output.getPixel(width - 1, 0))
                assertEquals(Color.GREEN, output.getPixel(0, height - 1))
                assertEquals(Color.MAGENTA, output.getPixel(width - 1, height - 1))
                val overlay = changedArea(photo, output)
                assertTrue("No data was drawn for ${layout.name}", !overlay.isEmpty)
                output.recycle()
            }
            photo.recycle()
        }
    }

    @Test
    fun exportedPngUsesTheOriginalResolutionRatherThanThePreviewSize() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File.createTempFile("social-source", ".png", context.cacheDir)
        val exportedFile = File.createTempFile("social-export", ".png", context.cacheDir)
        try {
            val original = Bitmap.createBitmap(1907, 1081, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }
            file.outputStream().use { assertTrue(original.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            original.recycle()
            val sourceUri = Uri.fromFile(file)
            val preview = requireNotNull(SocialCardBitmapRenderer.decodeSampledPhoto(context, sourceUri, 600))
            assertEquals(600, preview.width)
            preview.recycle()

            val config = SocialCardConfig(useImportedPhoto = true, layout = SocialCardLayout.SIDE_BY_SIDE)
            val rendered = SocialCardBitmapRenderer.renderExportBitmap(context, hike, SpeedUnit.KMH, config, sourceUri)
            exportedFile.outputStream().use { assertTrue(rendered.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            rendered.recycle()
            val output = requireNotNull(BitmapFactory.decodeFile(exportedFile.path))
            assertEquals(1907, output.width)
            assertEquals(1081, output.height)
            assertEquals(Color.CYAN, output.getPixel(0, 0))
            output.recycle()
        } finally {
            file.delete()
            exportedFile.delete()
        }
    }

    @Test
    fun selectedMetricsAreNotDroppedAfterTheSixthAndStepsCanBeToggled() {
        val selected = SocialCardConfig(showSteps = true)
        val stats = SocialCardBitmapRenderer.buildSelectedStats(hike, SpeedUnit.KMH, selected)
        assertTrue(stats.size > 6)
        assertTrue(stats.any { it.label == "\u6b65\u6570" })
        assertTrue(SocialCardBitmapRenderer.buildSelectedStats(hike, SpeedUnit.KMH, selected.copy(showSteps = false))
            .none { it.label == "\u6b65\u6570" })
    }

    @Test
    fun enablingEveryMetricDoesNotShrinkOrMoveRouteArtwork() {
        val compact = SocialCardConfig(showTitle = false, showDistance = false, showDuration = false, showElevation = false,
            showSpeed = false, showHeartRate = false, showPeakG = false, showMiniProfile = false)
        val dense = SocialCardConfig(showClimbDifficulty = true, showSurfaceComplexity = true, showBodyPerformance = true, showSteps = true)
        for (preset in SocialCardLayout.entries) {
            val compactConfig = compact.copy(layout = preset)
            val denseConfig = dense.copy(layout = preset)
            val minimal = SocialCardBitmapRenderer.renderPhotoPosterLayers(hike, SpeedUnit.KMH, compactConfig)
            val detailed = SocialCardBitmapRenderer.renderPhotoPosterLayers(hike, SpeedUnit.KMH, denseConfig)
            try {
                val route = minimal.getValue(SocialPosterLayer.ROUTE)
                val detailedRoute = detailed.getValue(SocialPosterLayer.ROUTE)
                assertTrue(route.sameAs(detailedRoute))
                assertTrue(SocialPosterLayer.DATA in detailed)
                assertEquals(
                    socialPosterFrame(720f, 1280f, route.width, route.height, SocialPosterLayer.ROUTE, compactConfig),
                    socialPosterFrame(720f, 1280f, route.width, route.height, SocialPosterLayer.ROUTE, denseConfig)
                )
            } finally {
                (minimal.values + detailed.values).forEach(Bitmap::recycle)
            }
        }
    }

    @Test
    fun exportedRouteMovesAndScalesWithoutMovingOtherLayers() {
        val photo = Bitmap.createBitmap(720, 900, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.DKGRAY) }
        val config = SocialCardConfig(useImportedPhoto = true, showTitle = false, showDistance = false, showDuration = false,
            showElevation = false, showSpeed = false, showHeartRate = false, showPeakG = false, showMiniProfile = false)
        val initialConfig = config.copy(posterEdits = SocialPosterEdits(route = SocialLayerPlacement(0f, 0f, 0.5f)))
        val enlargedConfig = config.copy(posterEdits = SocialPosterEdits(route = SocialLayerPlacement(1f, 1f, 1f)))
        val first = SocialCardBitmapRenderer.renderBitmap(hike, SpeedUnit.KMH, initialConfig, photo)
        val second = SocialCardBitmapRenderer.renderBitmap(hike, SpeedUnit.KMH, enlargedConfig, photo)
        try {
            val initial = changedArea(photo, first)
            val enlarged = changedArea(photo, second)
            assertTrue(enlarged.width() >= initial.width() * 1.8f)
            assertTrue(enlarged.centerX() > initial.centerX())
            assertTrue(enlarged.centerY() > initial.centerY())
            val title = SocialPosterLayer.TITLE
            assertEquals(socialPosterFrame(720f, 900f, 1000, 194, title, initialConfig),
                socialPosterFrame(720f, 900f, 1000, 194, title, enlargedConfig))
        } finally {
            first.recycle()
            second.recycle()
            photo.recycle()
        }
    }

    @Test
    fun defaultPresetsGiveTitleRouteAndDataSeparateSpaceInEitherOrientation() {
        for (preset in SocialCardLayout.entries) {
            val config = SocialCardConfig(layout = preset, showSteps = true, showClimbDifficulty = true,
                showBodyPerformance = true, showSurfaceComplexity = true)
            val artwork = SocialCardBitmapRenderer.renderPhotoPosterLayers(hike, SpeedUnit.KMH, config)
            try {
                for ((width, height) in listOf(720f to 1280f, 1280f to 720f, 1900f to 310f)) {
                    val frames = artwork.mapValues { (layer, bitmap) ->
                        val frame = socialPosterFrame(width, height, bitmap.width, bitmap.height, layer, config)
                        RectF(frame.left, frame.top, frame.left + frame.width, frame.top + frame.height)
                    }
                    val route = frames.getValue(SocialPosterLayer.ROUTE)
                    val title = frames.getValue(SocialPosterLayer.TITLE)
                    val data = frames.getValue(SocialPosterLayer.DATA)
                    assertTrue(!RectF.intersects(route, title))
                    assertTrue(!RectF.intersects(route, data))
                    assertTrue(!RectF.intersects(title, data))
                    assertTrue(route.width() > 0.35f * width || route.height() > 0.48f * height)
                }
            } finally {
                artwork.values.forEach(Bitmap::recycle)
            }
        }
    }

    private fun changedArea(original: Bitmap, rendered: Bitmap): Rect {
        var left = original.width
        var right = -1
        var top = original.height
        var bottom = -1
        for (y in 0 until original.height) {
            val source = original.rowPixels(y)
            rendered.rowPixels(y).forEachIndexed { x, pixel ->
                if (pixel != source[x]) {
                    left = minOf(left, x)
                    right = maxOf(right, x)
                    top = minOf(top, y)
                    bottom = maxOf(bottom, y)
                }
            }
        }
        return Rect(left, top, right + 1, bottom + 1)
    }

    private fun Bitmap.rowPixels(y: Int): IntArray = IntArray(width).also {
        getPixels(it, 0, width, 0, y, width, 1)
    }

}
