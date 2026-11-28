package com.example

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.AiGatewayConfig
import com.example.ai.OpenAiGatewayClient
import com.example.track.ActivityMode
import com.example.track.AiAnalysisRecordEntity
import com.example.track.TrackAiReportEntity
import com.example.track.TrackDatabase
import com.example.track.TrackSessionEntity
import com.example.ui.buildCoachAnnotatedString
import com.example.ui.ReportFormat
import com.example.ui.formatReportBlocks
import com.example.track.SampleTrackSeeder
import com.example.ui.buildPlainPreviewSnippet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AiGatewayClientTest {

    @Test
    fun `normalizeAndValidateBaseUrl normalizes paths and enforces https on public hosts`() {
        // Auto-append /v1 when path is empty
        assertEquals(
            "https://api.deepseek.com/v1",
            OpenAiGatewayClient.normalizeAndValidateBaseUrl("https://api.deepseek.com").getOrThrow()
        )
        assertEquals(
            "https://api.deepseek.com/v1",
            OpenAiGatewayClient.normalizeAndValidateBaseUrl("https://api.deepseek.com/").getOrThrow()
        )

        // Preserve custom subpath like OpenRouter /api/v1
        assertEquals(
            "https://openrouter.ai/api/v1",
            OpenAiGatewayClient.normalizeAndValidateBaseUrl("https://openrouter.ai/api/v1/").getOrThrow()
        )

        // Strip accidentally pasted /chat/completions or /models suffix
        assertEquals(
            "https://api.siliconflow.cn/v1",
            OpenAiGatewayClient.normalizeAndValidateBaseUrl("https://api.siliconflow.cn/v1/chat/completions").getOrThrow()
        )
        assertEquals(
            "https://oneapi.example.com/v1",
            OpenAiGatewayClient.normalizeAndValidateBaseUrl("https://oneapi.example.com/v1/models").getOrThrow()
        )

        // Allow cleartext HTTP ONLY on local loopback or private RFC1918 LAN addresses (e.g. Ollama)
        assertEquals(
            "http://192.168.1.25:11434/v1",
            OpenAiGatewayClient.normalizeAndValidateBaseUrl("http://192.168.1.25:11434").getOrThrow()
        )
        assertEquals(
            "http://localhost:11434/v1",
            OpenAiGatewayClient.normalizeAndValidateBaseUrl("http://localhost:11434/v1").getOrThrow()
        )
        assertEquals(
            "http://10.0.0.8:8080/v1",
            OpenAiGatewayClient.normalizeAndValidateBaseUrl("http://10.0.0.8:8080/v1").getOrThrow()
        )

        // Reject cleartext HTTP to public internet hosts
        assertTrue(OpenAiGatewayClient.normalizeAndValidateBaseUrl("http://api.openai.com/v1").isFailure)
        assertTrue(OpenAiGatewayClient.normalizeAndValidateBaseUrl("http://api.deepseek.com/v1").isFailure)

        // Reject link-local cloud metadata IP
        assertTrue(OpenAiGatewayClient.normalizeAndValidateBaseUrl("http://169.254.169.254/v1").isFailure)

        // Reject embedded credentials in URL
        assertTrue(OpenAiGatewayClient.normalizeAndValidateBaseUrl("https://user:pass@api.deepseek.com/v1").isFailure)

        // Reject blank or invalid URL
        assertTrue(OpenAiGatewayClient.normalizeAndValidateBaseUrl("   ").isFailure)
        assertTrue(OpenAiGatewayClient.normalizeAndValidateBaseUrl("not-a-url").isFailure)
    }

    @Test
    fun `parseModelsResponse parses standard OpenAI and Ollama payloads`() {
        val openaiJson = """
            {
              "object": "list",
              "data": [
                {"id": "gpt-4o-mini", "object": "model"},
                {"id": "deepseek-chat", "object": "model"},
                {"id": "gpt-4o-mini", "object": "model"}
              ]
            }
        """.trimIndent()
        assertEquals(
            listOf("deepseek-chat", "gpt-4o-mini"),
            OpenAiGatewayClient.parseModelsResponse(openaiJson)
        )

        val ollamaFallbackJson = """
            {
              "models": [
                {"name": "qwen2.5:7b"},
                {"name": "llama3.1:8b"}
              ]
            }
        """.trimIndent()
        assertEquals(
            listOf("llama3.1:8b", "qwen2.5:7b"),
            OpenAiGatewayClient.parseModelsResponse(ollamaFallbackJson)
        )
    }

    @Test
    fun `parseSseChunkDelta and parseNonStreamingCompletion extract assistant text cleanly`() {
        val sseDelta = """{"id":"chatcmpl-1","choices":[{"index":0,"delta":{"content":"过弯速度"},"finish_reason":null}]}"""
        assertEquals("过弯速度", OpenAiGatewayClient.parseSseChunkDelta(sseDelta))

        val emptyRoleChunk = """{"id":"chatcmpl-1","choices":[{"index":0,"delta":{"role":"assistant"},"finish_reason":null}]}"""
        assertNull(OpenAiGatewayClient.parseSseChunkDelta(emptyRoleChunk))

        val fullJson = """{"id":"chatcmpl-2","choices":[{"index":0,"message":{"role":"assistant","content":"整体节奏稳定。"}}]}"""
        assertEquals("整体节奏稳定。", OpenAiGatewayClient.parseNonStreamingCompletion(fullJson))
    }

    @Test
    fun `AiGatewayConfig reflects configuration readiness and masks api keys`() {
        assertFalse(AiGatewayConfig().isConfigured)
        assertFalse(AiGatewayConfig(baseUrl = "https://api.deepseek.com/v1").isConfigured)
        val ready = AiGatewayConfig(
            baseUrl = "https://api.deepseek.com/v1",
            apiKey = "sk-1234567890abcdef",
            modelId = "deepseek-chat"
        )
        assertTrue(ready.isConfigured)
        assertEquals("sk-1••••cdef", ready.maskedApiKeyPreview)
    }

    @Test
    fun `room track_ai_reports caches report and cascades delete when track session is removed`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, TrackDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.trackDao()
            val sessionId = dao.insertSession(
                TrackSessionEntity(
                    name = "测试赛道记录",
                    mode = ActivityMode.KARTING.name,
                    startTimeMs = 1_750_000_000_000L,
                    endTimeMs = 1_750_000_300_000L,
                    durationMs = 300_000L,
                    distanceMeters = 2400.0,
                    maxSpeedMps = 19.2f,
                    avgSpeedMps = 14.5f,
                    elevationGainMeters = 6.0,
                    elevationLossMeters = 6.0,
                    minAltitudeMeters = 40.0,
                    maxAltitudeMeters = 46.0,
                    lapCount = 3,
                    bestLapMs = 54_100L,
                    avgHeartRate = 152,
                    maxHeartRate = 171,
                    pointCount = 120
                )
            )

            assertNull(dao.getAiReport(sessionId))

            val report = TrackAiReportEntity(
                sessionId = sessionId,
                modelId = "deepseek-chat",
                reportText = "### 总体评价\n- **第 2 圈**表现最佳",
                generatedAtMs = 1_750_000_350_000L
            )
            dao.upsertAiReport(report)

            val loaded = dao.getAiReport(sessionId)
            assertNotNull(loaded)
            assertEquals("deepseek-chat", loaded!!.modelId)
            assertTrue(loaded.reportText.contains("第 2 圈"))

            // Deleting the parent session must cascade-delete the cached AI report
            dao.deleteSessionById(sessionId)
            assertNull(dao.getAiReport(sessionId))
        } finally {
            db.close()
        }
    }

    @Test
    fun `buildCoachAnnotatedString formats headings bullets and bold markers cleanly`() {
        val formatted = buildCoachAnnotatedString(
            markdown = "### 总体表现\n- **峰值横向 G 力** 达 1.42G\n普通说明",
            headingColor = Color.Blue,
            bodyColor = Color.Black
        )
        val plain = formatted.text
        assertTrue(plain.contains("总体表现"))
        assertTrue(plain.contains("• 峰值横向 G 力 达 1.42G"))
        assertFalse(plain.contains("###"))
        assertFalse(plain.contains("**"))

        val snippet = buildPlainPreviewSnippet("### 爬升节奏\n- **陡坡配速** 建议控制在 3.8 km/h\n- 注意补水")
        assertTrue(snippet.contains("爬升节奏"))
        assertTrue(snippet.contains("陡坡配速"))
        assertFalse(snippet.contains("###"))
        assertFalse(snippet.contains("**"))
    }

    @Test
    fun `report formats raw JSON YAML and mixed Markdown without changing the YAML values`() {
        val json = formatReportBlocks("""{"steps":[1,2]}""").single()
        assertEquals(ReportFormat.JSON, json.format)
        assertTrue(json.text.contains("\n  \"steps\": ["))

        val yaml = "stats:\n  - steps: 100\n  - notes: |\n      line one\n      line two"
        assertEquals(ReportFormat.YAML, formatReportBlocks(yaml).single().format)
        val mixed = formatReportBlocks("## Summary\n\n```yaml\n$yaml\n```\n\n```json\n[1,2]\n```")
        assertEquals(listOf(ReportFormat.MARKDOWN, ReportFormat.YAML, ReportFormat.JSON), mixed.map { it.format })
        assertEquals(yaml, mixed[1].text)
    }

    @Test
    fun `deleting history clears only the linked track result and does not restore another version`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, TrackDatabase::class.java).build()
        try {
            val dao = database.trackDao()
            val sample = SampleTrackSeeder.buildSampleHikingTrack(1_750_000_000_000L).session
            val firstTrack = dao.insertSession(sample)
            val anotherTrack = dao.insertSession(sample.copy(name = "Other trail"))
            fun record(sessionId: Long, text: String) = AiAnalysisRecordEntity(
                sessionId = sessionId,
                sessionName = sample.name,
                activityMode = sample.mode,
                modelId = "coach-test",
                reportText = text,
                summaryMetrics = "",
                generatedAtMs = 1_750_000_000_000L
            )
            dao.saveAiCoachResult(record(firstTrack, "Older report"))
            dao.saveAiCoachResult(record(firstTrack, "Current report"))
            dao.saveAiCoachResult(record(anotherTrack, "Unrelated report"))
            val history = dao.observeAllAiAnalysisHistory().first().associateBy { it.reportText }

            dao.deleteAiAnalysisWithReport(history.getValue("Older report").id)
            assertEquals("Current report", dao.observeAiReport(firstTrack).first()?.reportText)
            dao.saveAiCoachResult(record(firstTrack, "Replacement report"))
            val replacement = dao.observeAllAiAnalysisHistory().first().first { it.reportText == "Replacement report" }
            dao.deleteAiAnalysisWithReport(replacement.id)
            assertNull(dao.observeAiReport(firstTrack).first())
            assertTrue(dao.observeAllAiAnalysisHistory().first().any { it.reportText == "Current report" })
            assertEquals("Unrelated report", dao.observeAiReport(anotherTrack).first()?.reportText)

            dao.clearAiHistoryWithReports()
            assertTrue(dao.observeAllAiAnalysisHistory().first().isEmpty())
            assertNull(dao.observeAiReport(anotherTrack).first())
        } finally {
            database.close()
        }
    }

    @Test
    fun `room ai_analysis_history stores multiple reports in reverse chronological order and survives track deletion`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, TrackDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.trackDao()
            val sessionId = dao.insertSession(
                TrackSessionEntity(
                    name = "妙峰山越野穿越",
                    mode = ActivityMode.OFFROAD_CROSSING.name,
                    startTimeMs = 1_750_000_000_000L,
                    endTimeMs = 1_750_003_600_000L,
                    durationMs = 3_600_000L,
                    distanceMeters = 18_500.0,
                    maxSpeedMps = 18.0f,
                    avgSpeedMps = 11.2f,
                    elevationGainMeters = 740.0,
                    elevationLossMeters = 720.0,
                    minAltitudeMeters = 280.0,
                    maxAltitudeMeters = 1020.0,
                    lapCount = 0,
                    bestLapMs = null,
                    avgHeartRate = 138,
                    maxHeartRate = 165,
                    pointCount = 300
                )
            )

            val firstId = dao.insertAiAnalysisRecord(
                AiAnalysisRecordEntity(
                    sessionId = sessionId,
                    sessionName = "妙峰山越野穿越",
                    activityMode = ActivityMode.OFFROAD_CROSSING.name,
                    modelId = "deepseek-chat",
                    reportText = "### 首次复盘\n下坡控速稳定。",
                    summaryMetrics = "18.50 km · 爬升 +740m · 极速 64.8 km/h",
                    generatedAtMs = 1_750_004_000_000L
                )
            )
            val secondId = dao.insertAiAnalysisRecord(
                AiAnalysisRecordEntity(
                    sessionId = sessionId,
                    sessionName = "妙峰山越野穿越",
                    activityMode = ActivityMode.OFFROAD_CROSSING.name,
                    modelId = "gpt-4o-mini",
                    reportText = "### 二次复盘\n发卡弯入弯重心前移可更果断。",
                    summaryMetrics = "18.50 km · 爬升 +740m · 极速 64.8 km/h",
                    generatedAtMs = 1_750_005_000_000L
                )
            )

            val history = dao.observeAllAiAnalysisHistory().first()
            assertEquals(2, history.size)
            // Most recent generated report appears first
            assertEquals(secondId, history[0].id)
            assertEquals("gpt-4o-mini", history[0].modelId)
            assertEquals(firstId, history[1].id)

            // Deleting the parent track keeps historical coaching reports intact
            dao.deleteSessionById(sessionId)
            val afterTrackDelete = dao.observeAllAiAnalysisHistory().first()
            assertEquals(2, afterTrackDelete.size)

            // Deleting a single record removes only that item
            dao.deleteAiAnalysisRecord(firstId)
            val afterSingleDelete = dao.observeAllAiAnalysisHistory().first()
            assertEquals(1, afterSingleDelete.size)
            assertEquals(secondId, afterSingleDelete[0].id)

            // Clear removes all history cleanly
            dao.clearAllAiAnalysisHistory()
            assertTrue(dao.observeAllAiAnalysisHistory().first().isEmpty())
        } finally {
            db.close()
        }
    }
}
