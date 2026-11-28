package com.example.overlay

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.example.ble.HeartRateState
import com.example.gps.GpsData
import com.example.gps.OverlayDisplayConfig
import com.example.gps.SpeedUnit
import com.example.sensor.GForceData
import com.example.ui.theme.AppThemePalette
import com.example.ui.theme.ThemeMode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OverlayTranslucencyTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun lowOpacityOverlayRetainsDifferentBackdropColorsAcrossItsInterior() {
        composeRule.setContent {
            Box(Modifier.width(180.dp)) {
                Canvas(Modifier.matchParentSize()) {
                    drawRect(Color.Blue, size = Size(size.width / 2f, size.height))
                    drawRect(Color.Red, topLeft = Offset(size.width / 2f, 0f), size = Size(size.width / 2f, size.height))
                }
                OverlayWindow(
                    GForceData(), GpsData(), SpeedUnit.KMH, HeartRateState(),
                    OverlayDisplayConfig(showSpeed = false, showAltitude = false, showGForce = false, backgroundAlpha = 0.5f),
                    themeMode = ThemeMode.LIGHT,
                    themePalette = AppThemePalette.PURE_WHITE,
                    isMiniMode = true,
                    onToggleMiniMode = {},
                    previewRideElapsedMs = 0L,
                    modifier = Modifier.width(180.dp)
                )
            }
        }

        val bounds = composeRule.onNodeWithTag("overlay_window_root").getUnclippedBoundsInRoot()
        val (left, right) = composeRule.runOnIdle {
            val content = composeRule.activity.findViewById<View>(android.R.id.content)
            val bitmap = Bitmap.createBitmap(content.width, content.height, Bitmap.Config.ARGB_8888)
            try {
                content.draw(AndroidCanvas(bitmap))
                val density = content.resources.displayMetrics.density
                val y = ((bounds.top.value + (bounds.bottom.value - bounds.top.value) * (2f / 3f)) * density).toInt()
                fun pixel(fraction: Float): Color {
                    val x = ((bounds.left.value + (bounds.right.value - bounds.left.value) * fraction) * density).toInt()
                    return Color(bitmap.getPixel(x, y))
                }
                pixel(0.4f) to pixel(0.8f)
            } finally {
                bitmap.recycle()
            }
        }
        assertTrue("Left backdrop lost under overlay: $left", left.blue > left.red + 0.2f)
        assertTrue("Right backdrop lost under overlay: $right", right.red > right.blue + 0.2f)
    }
}
