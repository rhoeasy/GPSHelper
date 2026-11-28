package com.example.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.gps.DashboardSpeedStyle
import com.example.gps.GpsData
import com.example.gps.SpeedUnit
import com.example.sensor.GForceData
import com.example.ui.theme.GpsHudTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DashboardInstrumentTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun dialKeepsMissingSpeedDifferentFromARealZeroAndSupportsAWalkingScale() {
        val gps = mutableStateOf(GpsData(hasFix = true))
        val style = mutableStateOf(DashboardSpeedStyle.LOW_SPEED_DIAL)
        composeRule.setContent {
            GpsHudTheme {
                PrimarySpeedCard(gps.value, SpeedUnit.KMH, {}, {}, style = style.value)
            }
        }

        assertEquals(20, speedDialMaximum(DashboardSpeedStyle.LOW_SPEED_DIAL, SpeedUnit.KMH))
        assertEquals(10, speedDialMaximum(DashboardSpeedStyle.LOW_SPEED_DIAL, SpeedUnit.MPH))
        composeRule.onNodeWithTag("dashboard_speed_dial_no_needle").assertIsDisplayed()
        composeRule.onNodeWithText("--").assertIsDisplayed()
        composeRule.runOnIdle { gps.value = gps.value.copy(speedMps = 0f) }
        composeRule.onNodeWithTag("dashboard_speed_dial_needle").assertIsDisplayed()
        composeRule.onNodeWithText("0.0").assertIsDisplayed()

        composeRule.runOnIdle { style.value = DashboardSpeedStyle.NUMERIC }
        composeRule.onNodeWithTag("dashboard_speed_dial").assertDoesNotExist()
        composeRule.onNodeWithText("0.0").assertIsDisplayed()
    }

    @Test
    fun defaultDialSwapsRangesWithHysteresisAndDoesNotTreatMissingGpsAsSlowingDown() {
        val gps = mutableStateOf(GpsData())
        val unit = mutableStateOf(SpeedUnit.KMH)
        composeRule.setContent {
            GpsHudTheme { PrimarySpeedCard(gps.value, unit.value, {}, {}) }
        }
        fun assertRange(maximum: Int, label: String) {
            composeRule.onNodeWithTag("dashboard_speed_dial").assert(
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "量程 0 至 $maximum $label")
            )
        }
        fun speed(mps: Float) = composeRule.runOnIdle { gps.value = GpsData(hasFix = true, speedMps = mps) }

        assertRange(20, "km/h")
        speed(5.55f)
        assertRange(20, "km/h")
        speed(5.7f)
        assertRange(160, "km/h")
        speed(5.4f)
        assertRange(160, "km/h")
        composeRule.runOnIdle { gps.value = GpsData() }
        assertRange(160, "km/h")
        speed(5f)
        assertRange(20, "km/h")

        composeRule.runOnIdle { unit.value = SpeedUnit.MPH }
        assertRange(100, "mph")
        speed(4.2f)
        assertRange(100, "mph")
        speed(4f)
        assertRange(10, "mph")
    }

    @Test
    fun forceExplanationStaysOpenUntilExplicitlyClosed() {
        val expanded = mutableStateOf(false)
        val landscape = mutableStateOf(false)
        composeRule.setContent {
            GpsHudTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    GForceTelemetryCard(
                        gForceData = GForceData(totalG = 0.32f, lateralG = 0.25f, longitudinalG = -0.20f),
                        showPeakOnDial = true, peakHoldSeconds = 60, onCalibrateZero = {}, onResetPeak = {},
                        explanationExpanded = expanded.value, onExplanationExpandedChange = { expanded.value = it },
                        isLandscape = landscape.value
                    )
                }
            }
        }

        composeRule.onNodeWithTag("gforce_explanation").assertDoesNotExist()
        composeRule.onNodeWithTag("gforce_radar_canvas").performClick()
        composeRule.onNodeWithTag("gforce_explanation").assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(15_000)
        composeRule.runOnIdle { landscape.value = true }
        composeRule.onNodeWithTag("gforce_explanation").assertIsDisplayed()
        composeRule.onNodeWithTag("gforce_explanation_close").performScrollTo().performClick()
        composeRule.onNodeWithTag("gforce_explanation").assertDoesNotExist()
    }
}
