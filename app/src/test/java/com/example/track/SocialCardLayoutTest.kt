package com.example.track

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.example.gps.SpeedUnit
import kotlin.math.abs
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
    fun framedPhotosPreserveTheWholeImageAndKeepMetricsOffIt() {
        for ((width, height) in listOf(320 to 160, 160 to 320)) {
            val photo = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            photo.eraseColor(Color.CYAN)
            val square = IntArray(32 * 32) { Color.MAGENTA }
            photo.setPixels(square, 0, 32, width / 2 - 16, height / 2 - 16, 32, 32)
            for (layout in listOf(SocialCardLayout.PHOTO_TOP, SocialCardLayout.PHOTO_SIDE)) {
                val config = SocialCardConfig(useImportedPhoto = true, layout = layout)
                val output = SocialCardBitmapRenderer.renderBitmap(hike, SpeedUnit.KMH, config, photo, targetWidth = 480)
                val clean = SocialCardBitmapRenderer.renderBitmap(
                    hike, SpeedUnit.KMH, config.copy(showTitle = false, showRoute = false, showDistance = false), photo, targetWidth = 480
                )
                val photoBounds = pixelBounds(output, Color.CYAN)
                val squareBounds = pixelBounds(output, Color.MAGENTA)
                assertEquals(width.toFloat() / height, photoBounds.width().toFloat() / photoBounds.height(), 0.04f)
                assertTrue(squareBounds.width() > 0 && abs(squareBounds.width() - squareBounds.height()) <= 1)
                assertEquals(photoBounds, pixelBounds(clean, Color.CYAN))
                assertTrue(output.pixelsIn(photoBounds).contentEquals(clean.pixelsIn(photoBounds)))
                val fullImage = Rect(0, 0, output.width, output.height)
                assertTrue(!output.pixelsIn(fullImage).contentEquals(clean.pixelsIn(fullImage)))
                output.recycle()
                clean.recycle()
            }
            photo.recycle()
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

    private fun pixelBounds(bitmap: Bitmap, color: Int): Rect {
        var left = bitmap.width
        var right = -1
        var top = bitmap.height
        var bottom = -1
        for (y in 0 until bitmap.height) {
            bitmap.rowPixels(y).forEachIndexed { x, pixel ->
                if (pixel == color) {
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

    private fun Bitmap.pixelsIn(rect: Rect): IntArray = IntArray(rect.width() * rect.height()).also {
        getPixels(it, 0, rect.width(), rect.left, rect.top, rect.width(), rect.height())
    }
}
