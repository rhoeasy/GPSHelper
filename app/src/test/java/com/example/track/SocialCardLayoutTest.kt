package com.example.track

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
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
                when (layout) {
                    SocialCardLayout.TOP -> assertTrue(overlay.centerY() < height / 2)
                    SocialCardLayout.BOTTOM -> assertTrue(overlay.centerY() > height / 2)
                    SocialCardLayout.LEFT -> assertTrue(overlay.centerX() < width / 2)
                    SocialCardLayout.PHOTO_SIDE -> assertTrue(overlay.centerX() > width / 2)
                    SocialCardLayout.COVER -> assertTrue(overlay.contains(width / 2, height / 2))
                }
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

            val config = SocialCardConfig(useImportedPhoto = true, layout = SocialCardLayout.PHOTO_SIDE)
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
