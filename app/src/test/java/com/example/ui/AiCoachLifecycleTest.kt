package com.example.ui

import android.app.Application
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.core.app.ApplicationProvider
import com.example.ai.AiAnalysisState
import com.example.track.SampleTrackSeeder
import com.example.track.TrackDatabase
import com.example.track.TrackRepository
import com.example.ui.theme.GpsHudTheme
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AiCoachLifecycleTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun coachResultSurvivesLeavingTheTrackAndDisappearsWhenItsHistoryIsDeleted() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val store = ViewModelStore()
        val viewModel = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory.getInstance(app))[GpsDashboardViewModel::class.java]
        val tracks = TrackRepository(app)
        val sample = SampleTrackSeeder.buildSampleHikingTrack(1_750_000_000_000L)
        val id = runBlocking { TrackDatabase.getInstance(app).trackDao().saveCompleteSession(sample.session, sample.points, sample.laps) }
        val requested = CountDownLatch(1)
        val release = CountDownLatch(1)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/chat/completions") { exchange ->
            exchange.requestBody.use { it.readBytes() }
            requested.countDown()
            release.await(10L, TimeUnit.SECONDS)
            val bytes = """{"choices":[{"message":{"content":"## Trail summary"}}]}""".toByteArray()
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            composeRule.setContent {
                GpsHudTheme {
                    val selected by viewModel.selectedSessionDetail.collectAsStateWithLifecycle()
                    val state by viewModel.aiAnalysisState.collectAsStateWithLifecycle()
                    val config by viewModel.aiGatewayConfig.collectAsStateWithLifecycle()
                    selected?.let { detail ->
                        TrackAiCoachCard(id, config, state, { viewModel.generateAiCoachReport(detail) }, viewModel::stopAiCoachGeneration, {}, {})
                    }
                }
            }
            composeRule.runOnIdle {
                assertTrue(viewModel.saveAiGatewayConfig("http://127.0.0.1:${server.address.port}/v1", "", "test-coach").isSuccess)
                viewModel.loadSessionDetail(id)
            }
            await { viewModel.selectedSessionDetail.value != null }
            composeRule.runOnIdle { viewModel.generateAiCoachReport(requireNotNull(viewModel.selectedSessionDetail.value)) }
            await { requested.count == 0L }
            composeRule.runOnIdle { viewModel.closeSessionDetail() }
            release.countDown()

            await { runBlocking { tracks.observeAiReport(id).first() != null } }
            composeRule.runOnIdle { viewModel.loadSessionDetail(id) }
            await { viewModel.aiAnalysisState.value is AiAnalysisState.Success }
            composeRule.onNodeWithText("Trail summary").assertDoesNotExist()
            composeRule.onNodeWithTag("toggle_ai_report").performClick()
            composeRule.onNodeWithText("Trail summary").assertIsDisplayed()
            composeRule.onNodeWithTag("toggle_ai_report").performClick()
            composeRule.onNodeWithText("Trail summary").assertDoesNotExist()

            val history = runBlocking { tracks.allAiAnalysisHistory.first() }.single { it.sessionId == id }
            assertEquals("## Trail summary", history.reportText)
            composeRule.runOnIdle { viewModel.deleteAiAnalysisRecord(history.id) }
            await { viewModel.aiAnalysisState.value == AiAnalysisState.Idle }
            composeRule.onNodeWithTag("toggle_ai_report").assertDoesNotExist()
        } finally {
            release.countDown()
            server.stop(0)
            composeRule.runOnIdle {
                viewModel.clearAiGatewayConfig()
                store.clear()
            }
        }
    }

    private fun await(condition: () -> Boolean) = composeRule.waitUntil(5_000L) {
        shadowOf(Looper.getMainLooper()).idle()
        condition()
    }
}
