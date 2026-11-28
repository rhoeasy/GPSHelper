package com.example.ai

import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal const val ASSISTANT_MEMORY_TEXT_LIMIT = 300
internal const val ASSISTANT_MEMORY_COUNT_LIMIT = 100
private const val MEMORY_FILE_LIMIT = 128 * 1024

internal data class AssistantMemory(
    val id: String,
    val text: String,
    val pageId: String?,
    val pinned: Boolean,
    val usedAt: Long
)

/** Runs on the IO dispatcher. At most 128 KiB of memory data, including its index fields. */
internal class AssistantMemoryStore(path: File) {
    private val file = AtomicFile(path)
    private val _entries = MutableStateFlow<List<AssistantMemory>>(emptyList())
    val entries = _entries.asStateFlow()
    private var loaded = false

    @Synchronized
    fun load() {
        if (loaded) return
        val items = try {
            val buffer = ByteArray(MEMORY_FILE_LIMIT + 1)
            val size = file.openRead().use { reader ->
                var total = 0
                while (total < buffer.size) {
                    val n = reader.read(buffer, total, buffer.size - total)
                    if (n <= 0) break
                    total += n
                }
                total
            }
            require(size <= MEMORY_FILE_LIMIT)
            val json = JSONArray(buffer.decodeToString(endIndex = size))
            List(json.length()) { index ->
                val value = json.getJSONObject(index)
                AssistantMemory(
                    value.getString("id"), value.getString("text").take(ASSISTANT_MEMORY_TEXT_LIMIT),
                    value.optString("page").ifBlank { null }, value.optBoolean("pinned"), value.optLong("usedAt")
                )
            }.take(ASSISTANT_MEMORY_COUNT_LIMIT)
        } catch (_: FileNotFoundException) {
            emptyList()
        } catch (_: JSONException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
        _entries.value = items
        loaded = true
    }

    @Synchronized
    fun save(id: String?, text: String, pageId: String?, pinned: Boolean, now: Long) {
        load()
        val clean = text.trim()
        require(clean.isNotEmpty() && clean.length <= ASSISTANT_MEMORY_TEXT_LIMIT)
        val old = _entries.value
        val dedup = if (id == null) old.firstOrNull { it.pageId == pageId && it.text == clean } else null
        val entry = AssistantMemory(id ?: dedup?.id ?: UUID.randomUUID().toString(), clean, pageId, pinned || dedup?.pinned == true, now)
        val next = (old.filterNot { it.id == entry.id } + entry).toMutableList()
        while (next.size > ASSISTANT_MEMORY_COUNT_LIMIT || encode(next).size > MEMORY_FILE_LIMIT) {
            val oldest = next.filterNot { it.pinned || it.id == entry.id }.minByOrNull { it.usedAt }
                ?: error("记忆已满，请取消置顶或删除一条后再试")
            next.remove(oldest)
        }
        persist(next)
    }

    @Synchronized
    fun delete(id: String) {
        load()
        persist(_entries.value.filterNot { it.id == id })
    }

    @Synchronized
    fun addOrganized(texts: List<String>, now: Long): Int {
        load()
        val current = _entries.value
        val existing = current.mapTo(HashSet()) { it.text }
        val fresh = texts.filter { it.isNotBlank() && it.length <= ASSISTANT_MEMORY_TEXT_LIMIT && existing.add(it) }
            .take(8).map { AssistantMemory(UUID.randomUUID().toString(), it, null, false, now) }
        if (fresh.isEmpty()) return 0
        val next = (current + fresh).toMutableList()
        while (next.size > ASSISTANT_MEMORY_COUNT_LIMIT || encode(next).size > MEMORY_FILE_LIMIT) {
            val oldest = next.filterNot { it.pinned }.minByOrNull { it.usedAt } ?: break
            next.remove(oldest)
        }
        val added = fresh.count { it in next }
        if (added == 0) error("记忆已满，请取消置顶或删除一条后再试")
        persist(next)
        return added
    }

    @Synchronized
    fun recall(question: String, pageId: String?, now: Long): List<AssistantMemory> {
        load()
        val selected = selectAssistantMemories(_entries.value, question, pageId)
        val ids = selected.mapTo(HashSet()) { it.id }
        if (selected.isNotEmpty()) persist(_entries.value.map { if (it.id in ids) it.copy(usedAt = now) else it })
        return selected
    }

    private fun persist(items: List<AssistantMemory>) {
        val bytes = encode(items)
        require(bytes.size <= MEMORY_FILE_LIMIT)
        val out = file.startWrite()
        try {
            out.write(bytes)
            file.finishWrite(out)
            _entries.value = items
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
    }

    private fun encode(items: List<AssistantMemory>): ByteArray = JSONArray().apply {
        for (item in items) put(JSONObject().apply {
            put("id", item.id)
            put("text", item.text)
            item.pageId?.let { put("page", it) }
            put("pinned", item.pinned)
            put("usedAt", item.usedAt)
        })
    }.toString().encodeToByteArray()
}

private val memoryKeywords = Regex("(?=([\\p{IsHan}]{2}))|[a-z0-9]{2,}")

internal fun selectAssistantMemories(entries: List<AssistantMemory>, question: String, pageId: String?): List<AssistantMemory> {
    val keywords = memoryKeywords.findAll(question.lowercase())
        .map { it.groups[1]?.value ?: it.value }.toSet()
    val matches = entries.asSequence()
        .filter { it.pageId == null || it.pageId == pageId }
        .map { entry ->
            val text = entry.text.lowercase()
            val score = keywords.count(text::contains) + (if (entry.pinned) 3 else 0) +
                (if (pageId != null && entry.pageId == pageId) 2 else 0)
            entry to score
        }
        .filter { it.second > 0 }
        .sortedWith(compareByDescending<Pair<AssistantMemory, Int>> { it.second }.thenByDescending { it.first.usedAt })
        .take(4).map { it.first }.toList()
    if (matches.size < 4 && matches.none { it.pageId == null }) {
        val global = entries.filter { it.pageId == null }.maxByOrNull { it.usedAt }
        if (global != null) return matches + global
    }
    return matches
}
