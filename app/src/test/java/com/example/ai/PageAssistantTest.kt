package com.example.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PageAssistantTest {
    @Test
    fun organizedReportMemoriesAreDeduplicatedAndSavedAsSeparateEntries() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val path = File(context.cacheDir, "organized-memory-test.json").apply { delete() }
        try {
            val store = AssistantMemoryStore(path)
            val result = parseOrganizedMemories("```json\n[\"先看 GPS 断流\",\"先看 GPS 断流\",42,\"注意下坡配速\"]\n```")
            assertEquals(2, store.addOrganized(result, 1))
            assertEquals(0, store.addOrganized(result, 2))
            assertEquals(result, AssistantMemoryStore(path).apply { load() }.entries.value.map { it.text })
        } finally {
            path.delete()
        }
    }

    @Test
    fun generationWaitPersistsAndIsClampedLocally() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = AiGatewaySecureStorage(context)
        storage.clearAll()
        assertEquals(90, storage.loadConfig().effectiveWaitSeconds)
        storage.saveConfig(AiGatewayConfig(generationWaitSeconds = 135))
        assertEquals(135, AiGatewaySecureStorage(context).loadConfig().effectiveWaitSeconds)
        storage.saveConfig(AiGatewayConfig(generationWaitSeconds = 900))
        assertEquals(300, storage.loadConfig().effectiveWaitSeconds)
        storage.clearAll()
    }
    @Test
    fun memoryReloadsAndEvictsTheLeastRecentlyUsedUnpinnedEntry() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val path = File.createTempFile("assistant-test", ".json", context.cacheDir)
        path.delete()
        try {
            val memory = AssistantMemoryStore(path)
            memory.save(null, "置顶测试", null, true, 0)
            memory.save(null, "总是先解释定位精度", "ACCURACY", false, 1)
            memory.save(null, "应该被淘汰", null, false, 2)
            for (i in 3..99) memory.save(null, "偏好 $i", null, false, i.toLong())
            assertEquals(1, memory.recall("定位精度怎么看", "ACCURACY", 101).count { it.pageId == "ACCURACY" })
            memory.save(null, "最后写入", null, false, 102)

            val restored = AssistantMemoryStore(path).apply { load() }
            assertEquals(100, restored.entries.value.size)
            assertTrue(restored.entries.value.any { it.pinned })
            assertTrue(restored.entries.value.any { it.pageId == "ACCURACY" })
            assertFalse(restored.entries.value.any { it.text == "应该被淘汰" })
            assertTrue(path.length() <= 128 * 1024)
            val entry = restored.entries.value.first { it.pinned }
            restored.save(entry.id, "编辑后内容", "ACCURACY", false, 103)
            assertTrue(restored.entries.value.any { it.id == entry.id && it.text == "编辑后内容" && !it.pinned })
            restored.delete(entry.id)
            assertFalse(AssistantMemoryStore(path).apply { load() }.entries.value.any { it.id == entry.id })
        } finally {
            path.delete()
        }
    }

    @Test
    fun recallRespectsPageScopeAndSendingWithoutPageOmitsTheSnapshot() {
        val entries = listOf(
            AssistantMemory("1", "先说海拔", "ALTITUDE", false, 1),
            AssistantMemory("2", "海拔问题只看定位误差", "ACCURACY", true, 2),
            AssistantMemory("3", "海拔用米", null, false, 3)
        )
        assertEquals(setOf("1", "3"), selectAssistantMemories(entries, "海拔是多少", "ALTITUDE").map { it.id }.toSet())
        val withoutPage = JSONObject(assistantPrompt("海拔是什么", null, emptyList(), emptyList()))
        assertFalse(withoutPage.has("current_page"))
        val withPage = JSONObject(assistantPrompt(
            "海拔是什么", AssistantPage("ALTITUDE", "海拔", "字".repeat(4_000)), emptyList(), entries
        ))
        assertEquals(ASSISTANT_PAGE_LIMIT, withPage.getJSONObject("current_page").getString("summary").length)
        assertEquals("海拔是什么", withPage.getString("question"))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun assistantWaitsForTheSharedQueueAndReceivesTheSubmittedPageSnapshot() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val queue = AiGatewayQueue(backgroundScope)
        var request: JSONObject? = null
        val agent = GpsPageAssistant(context, backgroundScope, queue, { AiGatewayConfig("https://example.com", "", "model") }) {
            _, _, prompt, _, _ ->
            request = JSONObject(prompt)
            "定位误差单位是米"
        }
        queue.submit(GatewayTaskKey.Models) { awaitCancellation() }
        assertTrue(agent.ask("这个值是什么意思", AssistantPage("ACCURACY", "定位精度", "水平精度 6 m"), useMemory = false))
        runCurrent()
        assertEquals(GatewayStage.QUEUED, (queue.status.value[GatewayTaskKey.PageAssistant] as GatewayTaskStatus.Active).stage)
        queue.cancel(GatewayTaskKey.Models)
        runCurrent()
        assertEquals("水平精度 6 m", request?.getJSONObject("current_page")?.getString("summary"))
        assertEquals("定位误差单位是米", agent.exchanges.value.single().answer)
    }
}
