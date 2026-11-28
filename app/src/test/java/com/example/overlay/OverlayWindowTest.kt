package com.example.overlay

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
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
import com.example.track.ActivityMode
import com.example.track.RecordingSnapshot
import com.example.track.RecordingStatus
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
        val config = mutableStateOf(OverlayDisplayConfig())
        composeRule.setContent {
            OverlayWindow(
                gForceData = GForceData(),
                gpsData = gps.value,
                speedUnit = SpeedUnit.KMH,
                heartRateState = HeartRateState(),
                config = config.value,
                themeMode = ThemeMode.LIGHT,
                isMiniMode = mini.value,
                onToggleMiniMode = { mini.value = !mini.value },
                previewRideElapsedMs = 605_000L
            )
        }

        composeRule.onNodeWithText("GPS", useUnmergedTree = true).assertIsDisplayed()
        assertGpsStatus("GPS \u5f3a")
        composeRule.onNodeWithText("00:10:05", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("overlay_close_button", useUnmergedTree = true).assertDoesNotExist()

        composeRule.onNodeWithTag("overlay_window_root").performClick()
        composeRule.runOnIdle {
            assertTrue(mini.value)
            gps.value = GpsData(hasFix = true, fixStatus = GpsFixStatus.FUSED_FALLBACK)
        }

        composeRule.onNodeWithText("GPS", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("GPS \u878d\u5408\u5b9a\u4f4d", useUnmergedTree = true).assertDoesNotExist()
        assertGpsStatus("GPS \u878d\u5408\u5b9a\u4f4d")
        composeRule.onNodeWithText("00:10:05", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("\u7d2f\u8ba1 2 \u4e2a\u65f6\u95f4\u70b9", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("overlay_close_button", useUnmergedTree = true).assertDoesNotExist()

        composeRule.runOnIdle { config.value = config.value.copy(showRideElapsedTime = true) }
        composeRule.onNodeWithText("00:10:05", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("\u7d2f\u8ba1 2 \u4e2a\u65f6\u95f4\u70b9", useUnmergedTree = true).assertIsDisplayed()

        composeRule.runOnIdle { config.value = config.value.copy(showRideElapsedTime = false) }
        composeRule.onNodeWithText("00:10:05", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun sixthMarkReplacesTheWholeDotRowWithElapsedTime() {
        val elapsed = mutableStateOf(1_799_999L)
        composeRule.setContent {
            OverlayWindow(
                gForceData = GForceData(), gpsData = GpsData(), speedUnit = SpeedUnit.KMH,
                heartRateState = HeartRateState(), config = OverlayDisplayConfig(showRideElapsedTime = false),
                themeMode = ThemeMode.LIGHT, isMiniMode = true, onToggleMiniMode = {}, previewRideElapsedMs = elapsed.value
            )
        }

        composeRule.onNodeWithContentDescription("\u7d2f\u8ba1 5 \u4e2a\u65f6\u95f4\u70b9", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("00:29:59", useUnmergedTree = true).assertDoesNotExist()
        composeRule.runOnIdle { elapsed.value = 1_800_000L }
        composeRule.onNodeWithText("00:30:00", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("\u7d2f\u8ba1 6 \u4e2a\u65f6\u95f4\u70b9", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("+1", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun leanReadoutFollowsTheActiveOffroadRecording() {
        val recording = mutableStateOf(RecordingSnapshot(status = RecordingStatus.RECORDING, mode = ActivityMode.HIKING))
        composeRule.setContent {
            OverlayWindow(
                gForceData = GForceData(), gpsData = GpsData(), speedUnit = SpeedUnit.KMH,
                heartRateState = HeartRateState(), config = OverlayDisplayConfig(showRideTimer = false),
                themeMode = ThemeMode.LIGHT, isMiniMode = true, onToggleMiniMode = {},
                showMotorcycleLean = recording.value.showMotorcycleLean, motorcycleLeanDegrees = 28f
            )
        }
        val lean = composeRule.onNodeWithTag("overlay_motorcycle_lean", useUnmergedTree = true)
        lean.assertDoesNotExist()
        composeRule.runOnIdle { recording.value = recording.value.copy(mode = ActivityMode.OFFROAD_CROSSING) }
        lean.assertIsDisplayed()
        composeRule.onNodeWithText("28°", substring = true, useUnmergedTree = true).assertIsDisplayed()
        composeRule.runOnIdle { recording.value = recording.value.copy(status = RecordingStatus.PAUSED) }
        lean.assertDoesNotExist()
    }

    private fun assertGpsStatus(description: String) {
        composeRule.onNodeWithTag("overlay_gps_status", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, description))
    }
}
