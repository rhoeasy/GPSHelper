package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.ui.theme.GpsHudTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReportAndExportUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun exportMenuSharesTheSocialActionRowAndRoutesEachFormat() {
        val actions = mutableListOf<String>()
        composeRule.setContent {
            GpsHudTheme {
                Box(Modifier.width(280.dp)) {
                    TrackDetailActions(
                        onMakeSocialCard = { actions.add("social") },
                        onExportJson = { actions.add("json") },
                        onExportGpx = { actions.add("gpx") }
                    )
                }
            }
        }

        val social = composeRule.onNodeWithTag("btn_make_social_card").assertIsDisplayed()
        val export = composeRule.onNodeWithTag("btn_export_track").assertIsDisplayed()
        val socialBounds = social.getUnclippedBoundsInRoot()
        val exportBounds = export.getUnclippedBoundsInRoot()
        assertEquals(socialBounds.top + socialBounds.bottom, exportBounds.top + exportBounds.bottom)
        composeRule.onNodeWithTag("btn_export_json").assertDoesNotExist()
        export.performClick()
        composeRule.onNodeWithTag("btn_export_json").assertIsDisplayed().performClick()
        export.performClick()
        composeRule.onNodeWithTag("btn_export_gpx").assertIsDisplayed().performClick()
        social.performClick()
        composeRule.runOnIdle { assertEquals(listOf("json", "gpx", "social"), actions) }
    }
}
