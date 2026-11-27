package com.example.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.gps.GnssConstellation
import com.example.gps.GnssSatelliteInfo
import com.example.gps.GpsData
import com.example.ui.theme.GpsHudTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SatelliteDetailScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val satellite = GnssSatelliteInfo(
        svid = 12,
        constellation = GnssConstellation.GPS,
        cn0DbHz = 39f,
        elevationDegrees = 44f,
        azimuthDegrees = 120f,
        usedInFix = true
    )

    @Test
    fun displaysBothSignalsWithTheSameSatelliteId() {
        val satellites = mutableStateOf(emptyList<GnssSatelliteInfo>())
        composeRule.setContent {
            GpsHudTheme {
                SatelliteDetailScreen(
                    gpsData = GpsData(hasFix = satellites.value.isNotEmpty()),
                    satellites = satellites.value,
                    onBack = {}
                )
            }
        }

        composeRule.runOnIdle { satellites.value = listOf(satellite, satellite.copy(cn0DbHz = 24f)) }

        assertChannelTextDisplayed("39.0 dB-Hz")
        assertChannelTextDisplayed("24.0 dB-Hz")
    }

    @Test
    fun displaysPlaceholdersForNonFiniteAngles() {
        composeRule.setContent {
            GpsHudTheme {
                SatelliteDetailScreen(
                    gpsData = GpsData(hasFix = true),
                    satellites = listOf(satellite.copy(
                        elevationDegrees = Float.NaN,
                        azimuthDegrees = Float.POSITIVE_INFINITY
                    )),
                    onBack = {}
                )
            }
        }

        assertChannelTextDisplayed("\u4ef0\u89d2 -- \u00b7 \u65b9\u4f4d --")
    }

    private fun assertChannelTextDisplayed(text: String) {
        composeRule.onNodeWithTag("satellite_channel_list").performScrollToNode(hasText(text))
        composeRule.onNodeWithText(text).assertIsDisplayed()
    }
}
