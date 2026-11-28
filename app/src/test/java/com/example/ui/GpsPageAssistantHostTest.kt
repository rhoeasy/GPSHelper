package com.example.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ApplicationProvider
import com.example.ai.AiGatewayWork
import com.example.ui.theme.GpsHudTheme
import org.junit.Rule
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GpsPageAssistantHostTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun overlayCapturesTheVisibleSubpageBeforeOpeningAndReturnsToTheBasePage() {
        val subpage = mutableStateOf(true)
        val assistant = AiGatewayWork.get(ApplicationProvider.getApplicationContext()).assistant
        composeRule.setContent {
            GpsHudTheme {
                GpsPageAssistantHost(assistant, false, { AssistantPageKind.TRACK_DETAIL.snapshot("记录数值") }, {}) {
                    if (subpage.value) {
                        ProvideAssistantSubpage { AssistantPageKind.SOCIAL_POSTER.snapshot("轨迹、标题、数据图层") }
                    }
                    Text("GPSHelper 页面")
                }
            }
        }
        composeRule.onNodeWithTag("page_assistant_open").performClick()
        composeRule.onNodeWithText("制作社交图").assertIsDisplayed()
        composeRule.onNodeWithText("查看摘要").performClick()
        composeRule.onNodeWithText("轨迹、标题、数据图层").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("关闭助手").performClick()
        composeRule.runOnIdle { subpage.value = false }
        composeRule.onNodeWithTag("page_assistant_open").performClick()
        composeRule.onNodeWithText("记录详情").assertIsDisplayed()
    }

    @Test
    fun draggingAssistantMovesItWithoutOpeningTheChatAndTapStillWorks() {
        val assistant = AiGatewayWork.get(ApplicationProvider.getApplicationContext()).assistant
        composeRule.setContent {
            GpsHudTheme { GpsPageAssistantHost(assistant, false, { AssistantPageKind.DASHBOARD.snapshot() }, {}) {} }
        }
        val button = composeRule.onNodeWithTag("page_assistant_open")
        val before = button.fetchSemanticsNode().boundsInRoot
        button.performTouchInput { swipe(center, center + Offset(-90f, 55f)) }
        val after = button.fetchSemanticsNode().boundsInRoot
        assertTrue(after.left < before.left && after.top > before.top)
        composeRule.onNodeWithTag("page_assistant_dialog").assertDoesNotExist()
        button.performClick()
        composeRule.onNodeWithTag("page_assistant_dialog").assertIsDisplayed()
    }
}
