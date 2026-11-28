package com.example.overlay

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ble.HeartRateState
import com.example.gps.GpsData
import com.example.gps.GpsFixStatus
import com.example.gps.OverlayDisplayConfig
import com.example.gps.SpeedUnit
import com.example.sensor.GForceData
import com.example.ui.theme.ThemeMode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OverlayWindowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun compactModeKeepsGpsAndRideTimingWithoutAddingACloseButton() {
        val mini = mutableStateOf(false)
        val gps = mutableStateOf(GpsData(hasFix = true, fixStatus = GpsFixStatus.LOCKED, accuracyMeters = 6f))
        composeRule.setContent {
            OverlayWindow(
                gForceData = GForceData(),
                gpsData = gps.value,
                speedUnit = SpeedUnit.KMH,
                heartRateState = HeartRateState(),
                config = OverlayDisplayConfig(),
                themeMode = ThemeMode.LIGHT,
                isMiniMode = mini.value,
                onToggleMiniMode = { mini.value = !mini.value },
                previewRideElapsedMs = 605_000L
            )
        }

        composeRule.onNodeWithText("GPS \u5f3a", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("00:10:05", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("overlay_close_button", useUnmergedTree = true).assertDoesNotExist()

        composeRule.onNodeWithTag("overlay_window_root").performClick()
        composeRule.runOnIdle {
            assertTrue(mini.value)
            gps.value = GpsData()
        }

        composeRule.onNodeWithText("GPS \u641c\u661f", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("00:10:05", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("\u7d2f\u8ba1 2 \u4e2a\u65f6\u95f4\u70b9", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("overlay_close_button", useUnmergedTree = true).assertDoesNotExist()
    }
}
