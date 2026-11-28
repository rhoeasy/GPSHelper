package com.example.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.ai.AiGatewayConfig
import com.example.ai.AiGatewayWork
import com.example.ai.GatewayStage
import com.example.ai.GatewayTaskKey
import com.example.ai.GatewayTaskStatus
import com.example.gps.GpsData
import com.example.ui.theme.GpsHudTheme
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CoordinateDetailScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun changesFromNearbyToComparisonAndExportsTheZoomedDiagram() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val work = AiGatewayWork(context)
        val sharedUri = AtomicReference<Uri>()
        composeRule.setContent {
            GpsHudTheme {
                CoordinateDetailScreen(
                    gpsData = GpsData(hasFix = true, latitude = 0.0, longitude = 0.0),
                    gateway = AiGatewayConfig(baseUrl = "https://example.invalid/v1", modelId = "test"),
                    onBack = {}, onCopy = {}, onRequestLocation = {}, onOpenLocationSettings = {}, onConfigureGateway = {},
                    onShareImage = { sharedUri.set(it) },
                    gatewayWork = work
                )
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.coordinate_ask_nearby)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("coordinate_target_input").performScrollTo().performTextReplacement("0, 0.01")
        composeRule.onNodeWithTag("coordinate_relation").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.coordinate_ask_target)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.coordinate_zoom_in)).performScrollTo().performClick()
        composeRule.onNodeWithTag("coordinate_share_diagram").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) { sharedUri.get() != null }

        val bitmap = context.contentResolver.openInputStream(requireNotNull(sharedUri.get()))?.use { BitmapFactory.decodeStream(it) }
        requireNotNull(bitmap)
        assertTrue(bitmap.width > 100 && bitmap.height > 100)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertTrue(pixels.toSet().size > 5)
        bitmap.recycle()
    }

    @Test
    fun sendsOnlyRequestedCoordinatesAndUsesTheConfiguredGatewayModel() {
        val requests = ConcurrentLinkedQueue<JSONObject>()
        val allowResponse = CountDownLatch(1)
        val visible = mutableStateOf(true)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val work = AiGatewayWork(context)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/chat/completions") { exchange ->
            requests.add(JSONObject(exchange.requestBody.bufferedReader().use { it.readText() }))
            allowResponse.await(10, TimeUnit.SECONDS)
            val body = """{"choices":[{"message":{"content":"Gateway result"}}]}""".toByteArray()
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            composeRule.setContent {
                GpsHudTheme {
                    if (visible.value) CoordinateDetailScreen(
                        gpsData = GpsData(hasFix = true, latitude = 31.0, longitude = 121.0),
                        gateway = AiGatewayConfig(baseUrl = "http://127.0.0.1:${server.address.port}/v1", modelId = "position-test"),
                        onBack = {}, onCopy = {}, onRequestLocation = {}, onOpenLocationSettings = {}, onConfigureGateway = {},
                        onShareImage = {}, gatewayWork = work
                    )
                }
            }
            composeRule.waitForIdle()
            assertTrue(requests.isEmpty())
            composeRule.onNodeWithTag("coordinate_ask_gateway").performScrollTo().performClick()
            composeRule.waitUntil(5_000L) { requests.isNotEmpty() }
            val nearby = requireNotNull(requests.poll())
            assertEquals("position-test", nearby.getString("model"))
            val nearbyQuery = nearby.getJSONArray("messages").getJSONObject(1).getString("content")
            assertTrue(nearbyQuery.contains("31.000000, 121.000000"))
            assertFalse(nearbyQuery.contains("Target WGS84"))
            composeRule.runOnIdle { visible.value = false }
            composeRule.waitForIdle()
            allowResponse.countDown()
            composeRule.waitUntil(5_000L) {
                shadowOf(Looper.getMainLooper()).idle()
                work.status.value[GatewayTaskKey.Coordinates] !is GatewayTaskStatus.Active
            }
            assertEquals(work.status.value.toString(), "Gateway result", work.coordinateAnswer.value?.text)
            composeRule.runOnIdle { visible.value = true }
            composeRule.onNodeWithText("Gateway result").performScrollTo().assertIsDisplayed()

            composeRule.onNodeWithTag("coordinate_target_input").performScrollTo().performTextReplacement("31, 121.01")
            assertTrue(requests.isEmpty())
            composeRule.onNodeWithTag("coordinate_ask_gateway").performScrollTo().performClick()
            composeRule.waitUntil(5_000L) { requests.isNotEmpty() }
            val targetQuery = requests.remove().getJSONArray("messages").getJSONObject(1).getString("content")
            assertTrue(targetQuery.contains("Target WGS84 position: 31.000000, 121.010000"))
            composeRule.waitUntil(5_000L) {
                shadowOf(Looper.getMainLooper()).idle()
                work.status.value[GatewayTaskKey.Coordinates] !is GatewayTaskStatus.Active
            }
        } finally {
            allowResponse.countDown()
            server.stop(0)
        }
    }

    @Test
    fun cancellingAWaitingGatewayStreamLetsTheQueuedModelTestReportItsError() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val work = AiGatewayWork(context)
        val releaseStream = CountDownLatch(1)
        val executor = Executors.newCachedThreadPool()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = executor
        server.createContext("/v1/chat/completions") { exchange ->
            exchange.responseHeaders.set("Content-Type", "text/event-stream")
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { body ->
                body.write(": still waiting\n\n".toByteArray())
                body.flush()
                releaseStream.await(15, TimeUnit.SECONDS)
            }
        }
        server.createContext("/v1/models") { exchange ->
            val body = """{"error":{"message":"Rejected"}}""".toByteArray()
            exchange.sendResponseHeaders(401, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        val baseUrl = "http://127.0.0.1:${server.address.port}/v1"
        try {
            composeRule.setContent {
                GpsHudTheme {
                    CoordinateDetailScreen(
                        gpsData = GpsData(hasFix = true, latitude = 31.0, longitude = 121.0),
                        gateway = AiGatewayConfig(baseUrl = baseUrl, modelId = "position-test"),
                        onBack = {}, onCopy = {}, onRequestLocation = {}, onOpenLocationSettings = {}, onConfigureGateway = {},
                        onShareImage = {}, gatewayWork = work
                    )
                }
            }
            composeRule.onNodeWithTag("coordinate_ask_gateway").performScrollTo().performClick()
            composeRule.waitUntil(5_000L) {
                shadowOf(Looper.getMainLooper()).idle()
                val state = work.status.value[GatewayTaskKey.Coordinates]
                (state as? GatewayTaskStatus.Active)?.stage == GatewayStage.WAITING_FOR_OUTPUT || state is GatewayTaskStatus.Failed
            }
            assertEquals(GatewayStage.WAITING_FOR_OUTPUT, (work.status.value[GatewayTaskKey.Coordinates] as GatewayTaskStatus.Active).stage)
            composeRule.runOnIdle { work.testModels(baseUrl, "", "position-test") }
            assertEquals(GatewayStage.QUEUED, (work.status.value[GatewayTaskKey.Models] as GatewayTaskStatus.Active).stage)

            composeRule.onNodeWithTag("coordinate_ask_gateway").performScrollTo().performClick()
            composeRule.waitUntil(5_000L) {
                shadowOf(Looper.getMainLooper()).idle()
                work.status.value[GatewayTaskKey.Models] is GatewayTaskStatus.Failed
            }
            val failure = work.status.value[GatewayTaskKey.Models] as GatewayTaskStatus.Failed
            assertTrue(failure.message.contains("HTTP 401"))
            assertTrue(work.coordinateAnswer.value?.text.isNullOrEmpty())
            assertFalse(work.status.value.containsKey(GatewayTaskKey.Coordinates))
        } finally {
            composeRule.runOnIdle { work.clearConfig() }
            releaseStream.countDown()
            server.stop(0)
            executor.shutdownNow()
        }
    }
}
