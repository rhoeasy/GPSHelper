package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import com.example.gps.SpeedUnit
import com.example.track.SampleTrackSeeder
import com.example.ui.theme.GpsHudTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrackGestureTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun bothSwipesOnTrackRequireConfirmationAndDoNotSwitchTabs() {
        val track = SampleTrackSeeder.buildSampleHikingTrack(1_750_000_000_000L).session.copy(id = 12)
        var deleted: Long? = null
        var tabChanges = 0
        composeRule.setContent {
            GpsHudTheme {
                Box(Modifier.fillMaxSize().mainTabSwipe(MainTab.TRACKS, true) { tabChanges++ }) {
                    TrackHistoryScreen(
                        sessions = listOf(track), speedUnit = SpeedUnit.KMH, aiHistoryCount = 3,
                        onSelectSession = {}, onOpenAiHistory = {}, onDeleteSession = { deleted = it }
                    )
                }
            }
        }
        composeRule.onNodeWithTag("tracks_more_actions").performClick()
        composeRule.onNodeWithTag("btn_open_ai_history").assertIsDisplayed().performClick()

        val row = composeRule.onNodeWithTag("swipe_track_12")
        row.performTouchInput { swipeLeft() }
        composeRule.onNodeWithText("删除轨迹记录").assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()
        row.performTouchInput { swipeRight() }
        composeRule.onNodeWithText("删除轨迹记录").assertIsDisplayed()
        composeRule.onNodeWithText("删除").performClick()
        composeRule.runOnIdle {
            assertEquals(12L, deleted)
            assertEquals(0, tabChanges)
        }
    }

    @Test
    fun leftAndRightSwipesSwitchToAdjacentMainTabs() {
        val active = mutableStateOf(MainTab.DASHBOARD)
        composeRule.setContent {
            Box(Modifier.fillMaxWidth().height(160.dp).mainTabSwipe(active.value, true) { active.value = it }
                .testTag("main_swipe_surface")) { Text(active.value.name) }
        }
        val target = composeRule.onNodeWithTag("main_swipe_surface")
        target.performTouchInput { swipeLeft() }
        composeRule.onNodeWithText("TRACKS").assertIsDisplayed()
        target.performTouchInput { swipeLeft() }
        composeRule.onNodeWithText("SETTINGS").assertIsDisplayed()
        target.performTouchInput { swipeRight() }
        composeRule.onNodeWithText("TRACKS").assertIsDisplayed()
    }
}
