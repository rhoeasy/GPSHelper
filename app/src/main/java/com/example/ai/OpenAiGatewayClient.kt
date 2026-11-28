package com.example.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

internal sealed interface GatewayChatEvent {
    data object Headers : GatewayChatEvent
    data class Text(val text: String) : GatewayChatEvent
}

/**
 * Standard OpenAI-compatible HTTP client supporting OneAPI, NewAPI, DeepSeek,
 * OpenRouter, SiliconFlow, local Ollama, LM Studio, and custom gateways.
 *
 * Security invariants:
 * - Public internet hosts MUST use HTTPS; cleartext HTTP is only permitted for
 *   loopback and RFC1918 private LAN addresses (e.g. local Ollama).
 * - Link-local metadata addresses (169.254.0.0/16) are blocked.
 * - API keys and Authorization headers are never logged.
 */
object OpenAiGatewayClient {

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val modelsClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val streamClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Normalizes a user-entered Base URL into a clean OpenAI-compatible base endpoint
     * (without trailing slash) and validates transport security rules.
     */
    fun normalizeAndValidateBaseUrl(rawUrl: String): Result<String> {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("请先填写网关 Base URL"))
        }

        val parsed = trimmed.toHttpUrlOrNull()
            ?: return Result.failure(
                IllegalArgumentException("Base URL 格式不正确，请以 https:// 或局域网 http:// 开头")
            )

        if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) {
            return Result.failure(IllegalArgumentException("请勿在 Base URL 中嵌入账号或密码"))
        }

        val host = parsed.host.lowercase()
        if (isLinkLocalMetadataHost(host)) {
            return Result.failure(IllegalArgumentException("不允许访问链路本地保留地址"))
        }

        if (parsed.scheme == "http" && !isPrivateOrLocalHost(host)) {
            return Result.failure(
                IllegalArgumentException("公网网关必须使用 HTTPS 加密连接；仅本机或局域网服务（如 Ollama）支持 HTTP")
            )
        }

        var path = parsed.encodedPath.trimEnd('/')
        val redundantSuffixes = listOf(
            "/chat/completions",
            "/completions",
            "/models"
        )
        for (suffix in redundantSuffixes) {
            if (path.endsWith(suffix, ignoreCase = true)) {
                path = path.dropLast(suffix.length).trimEnd('/')
                break
            }
        }
        if (path.isEmpty()) {
            path = "/v1"
        }

        val defaultPort = (parsed.scheme == "https" && parsed.port == 443) ||
            (parsed.scheme == "http" && parsed.port == 80)
        val authority = if (defaultPort) {
            parsed.host
        } else {
            "${parsed.host}:${parsed.port}"
        }

        return Result.success("${parsed.scheme}://$authority$path")
    }

    internal fun isPrivateOrLocalHost(rawHost: String): Boolean {
        val host = rawHost.trim().lowercase().removePrefix("[").removeSuffix("]")
        if (host == "localhost" || host == "::1" || host.endsWith(".local") || host.endsWith(".localhost")) {
            return true
        }
        val parts = host.split('.')
        if (parts.size != 4) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false

        val a = octets[0]
        val b = octets[1]
        return when {
            a == 127 -> true
            a == 10 -> true
            a == 192 && b == 168 -> true
            a == 172 && b in 16..31 -> true
            else -> false
        }
    }

    private fun isLinkLocalMetadataHost(rawHost: String): Boolean {
        val host = rawHost.trim().lowercase().removePrefix("[").removeSuffix("]")
        val parts = host.split('.')
        if (parts.size == 4) {
            val a = parts[0].toIntOrNull()
            val b = parts[1].toIntOrNull()
            if (a == 169 && b == 254) return true
        }
        return host.startsWith("fe80:")
    }

    /**
     * Fetches the available model IDs from `GET {baseUrl}/models`.
     */
    suspend fun fetchModels(config: AiGatewayConfig): Result<List<String>> {
        val normalizedBase = normalizeAndValidateBaseUrl(config.baseUrl)
            .getOrElse { return Result.failure(it) }

        val requestBuilder = Request.Builder()
            .url("$normalizedBase/models")
            .get()
            .header("Accept", "application/json")

        val key = config.apiKey.trim()
        if (key.isNotEmpty()) {
            requestBuilder.header("Authorization", "Bearer $key")
        }

        return try {
            val models = responseFlow<List<String>>(modelsClient.newCall(requestBuilder.build())) { response ->
                if (!response.isSuccessful) {
                    throw IOException(mapHttpError(response.code, errorBodyIfNeeded(response), isModelsEndpoint = true))
                }
                val bodyText = response.body?.string().orEmpty()
                val models = parseModelsResponse(bodyText)
                if (models.isEmpty()) {
                    throw IOException("连接成功，但网关未返回模型列表，可手动输入模型 ID")
                }
                trySendBlocking(models)
            }.first()
            Result.success(models)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Streams assistant text chunks from `POST {baseUrl}/chat/completions`.
     * Supports both SSE streaming (`text/event-stream`) and standard JSON fallback.
     */
    internal fun streamChatCompletion(
        config: AiGatewayConfig,
        systemPrompt: String,
        userPrompt: String
    ): Flow<GatewayChatEvent> {
        val normalizedBase = normalizeAndValidateBaseUrl(config.baseUrl)
            .getOrThrow()
        val modelId = config.modelId.trim()
        if (modelId.isEmpty()) {
            throw IllegalArgumentException("请先在设置中选择或输入模型 ID")
        }

        val payload = JSONObject().apply {
            put("model", modelId)
            put("stream", true)
            put("temperature", 0.5)
            put(
                "messages",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt)
                        }
                    )
                    put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", userPrompt)
                        }
                    )
                }
            )
        }

        val requestBuilder = Request.Builder()
            .url("$normalizedBase/chat/completions")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "text/event-stream, application/json")

        val key = config.apiKey.trim()
        if (key.isNotEmpty()) {
            requestBuilder.header("Authorization", "Bearer $key")
        }

        return responseFlow(streamClient.newCall(requestBuilder.build())) { response ->
            val body = response.body
            if (!response.isSuccessful) {
                if (trySendBlocking(GatewayChatEvent.Headers).isFailure) return@responseFlow
                val errBody = errorBodyIfNeeded(response)
                throw IOException(mapHttpError(response.code, errBody, isModelsEndpoint = false))
            }
            if (body == null) {
                throw IOException("网关返回空响应，请稍后重试")
            }
            if (trySendBlocking(GatewayChatEvent.Headers).isFailure) return@responseFlow

            val contentType = response.header("Content-Type").orEmpty().lowercase()
            if (contentType.contains("application/json")) {
                val fullJson = body.string()
                val fullText = parseNonStreamingCompletion(fullJson)
                if (fullText.isNullOrEmpty()) {
                    throw IOException("模型返回内容为空，请检查模型 ID 或重试")
                }
                trySendBlocking(GatewayChatEvent.Text(fullText))
                return@responseFlow
            }

            val source = body.source()
            var emittedAny = false
            val nonSseBuffer = StringBuilder()
            var sawSsePrefix = false

            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith(":")) continue

                if (trimmed.startsWith("data:")) {
                    sawSsePrefix = true
                    val dataPayload = trimmed.removePrefix("data:").trim()
                    if (dataPayload == "[DONE]") {
                        break
                    }
                    val delta = parseSseChunkDelta(dataPayload)
                    if (!delta.isNullOrEmpty()) {
                        emittedAny = true
                        if (trySendBlocking(GatewayChatEvent.Text(delta)).isFailure) return@responseFlow
                    }
                } else if (!sawSsePrefix) {
                    nonSseBuffer.append(trimmed)
                }
            }

            // Fallback for gateways returning plain JSON without application/json Content-Type
            if (!emittedAny && nonSseBuffer.isNotEmpty()) {
                val fallbackText = parseNonStreamingCompletion(nonSseBuffer.toString())
                if (!fallbackText.isNullOrEmpty()) {
                    emittedAny = true
                    trySendBlocking(GatewayChatEvent.Text(fallbackText))
                }
            }

            if (!emittedAny) {
                throw IOException("未收到模型生成的文本内容，请确认所选模型支持对话补全")
            }
        }
    }

    private fun <T> responseFlow(call: Call, read: ProducerScope<T>.(Response) -> Unit): Flow<T> = callbackFlow {
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                close(IOException(mapNetworkException(e), e))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use { read(it) }
                    close()
                } catch (e: Exception) {
                    close(IOException(mapNetworkException(e), e))
                }
            }
        })
        awaitClose { call.cancel() }
    }

    internal fun parseModelsResponse(jsonText: String): List<String> {
        val trimmed = jsonText.trim()
        if (trimmed.isEmpty()) return emptyList()
        return runCatching {
            val root = JSONObject(trimmed)
            val result = LinkedHashSet<String>()

            val dataArr = root.optJSONArray("data")
            if (dataArr != null) {
                for (i in 0 until dataArr.length()) {
                    val item = dataArr.opt(i)
                    when (item) {
                        is JSONObject -> {
                            val id = item.optString("id").trim()
                                .ifEmpty { item.optString("name").trim() }
                            if (id.isNotEmpty()) result.add(id)
                        }
                        is String -> {
                            val id = item.trim()
                            if (id.isNotEmpty()) result.add(id)
                        }
                    }
                }
            }

            if (result.isEmpty()) {
                val modelsArr = root.optJSONArray("models")
                if (modelsArr != null) {
                    for (i in 0 until modelsArr.length()) {
                        val item = modelsArr.opt(i)
                        when (item) {
                            is JSONObject -> {
                                val id = item.optString("id").trim()
                                    .ifEmpty { item.optString("name").trim() }
                                if (id.isNotEmpty()) result.add(id)
                            }
                            is String -> {
                                val id = item.trim()
                                if (id.isNotEmpty()) result.add(id)
                            }
                        }
                    }
                }
            }

            result.sorted().take(500)
        }.getOrDefault(emptyList())
    }

    internal fun parseSseChunkDelta(dataPayload: String): String? {
        return runCatching {
            val obj = JSONObject(dataPayload)
            val choices = obj.optJSONArray("choices") ?: return@runCatching null
            if (choices.length() == 0) return@runCatching null
            val first = choices.optJSONObject(0) ?: return@runCatching null

            val delta = first.optJSONObject("delta")
            if (delta != null && !delta.isNull("content")) {
                val content = delta.optString("content", "")
                if (content.isNotEmpty()) return@runCatching content
            }

            val message = first.optJSONObject("message")
            if (message != null && !message.isNull("content")) {
                val content = message.optString("content", "")
                if (content.isNotEmpty()) return@runCatching content
            }

            null
        }.getOrNull()
    }

    internal fun parseNonStreamingCompletion(jsonText: String): String? {
        return runCatching {
            val obj = JSONObject(jsonText.trim())
            val choices = obj.optJSONArray("choices") ?: return@runCatching null
            if (choices.length() == 0) return@runCatching null
            val first = choices.optJSONObject(0) ?: return@runCatching null
            val message = first.optJSONObject("message")
            if (message != null && !message.isNull("content")) {
                val content = message.optString("content", "")
                if (content.isNotEmpty()) return@runCatching content
            }
            val text = first.optString("text", "")
            text.ifEmpty { null }
        }.getOrNull()
    }

    private fun extractGatewayErrorMessage(bodyText: String): String? {
        return runCatching {
            val obj = JSONObject(bodyText.trim())
            val errObj = obj.optJSONObject("error")
            val msg = errObj?.optString("message")?.trim()
                ?.ifEmpty { obj.optString("message").trim() }
                ?.ifEmpty { obj.optString("detail").trim() }
            msg?.take(120)?.ifEmpty { null }
        }.getOrNull()
    }

    private fun errorBodyIfNeeded(response: Response): String = when (response.code) {
        401, 403, 429, in 500..599 -> ""
        else -> response.peekBody(4_096L).string()
    }

    private fun mapHttpError(code: Int, bodyText: String, isModelsEndpoint: Boolean): String {
        val detail = extractGatewayErrorMessage(bodyText)
        val suffix = if (!detail.isNullOrBlank()) "（$detail）" else ""
        return when (code) {
            400 -> "请求参数不被网关支持$suffix"
            401, 403 -> "鉴权失败（HTTP $code）：请检查 API Key 是否正确或余额是否充足"
            404 -> if (isModelsEndpoint) {
                "网关未提供 /models 接口（HTTP 404），可直接在下方手动填写模型 ID"
            } else {
                "接口路径或模型不存在（HTTP 404），请检查 Base URL 与模型 ID$suffix"
            }
            429 -> "请求过于频繁或配额已用尽（HTTP 429），请稍后重试"
            in 500..599 -> "网关服务端异常（HTTP $code），请稍后重试"
            else -> "请求失败（HTTP $code）$suffix"
        }
    }

    private fun mapNetworkException(e: Exception): String {
        return when (e) {
            is UnknownHostException -> "无法解析网关域名，请检查网络连接或 Base URL"
            is ConnectException -> "无法连接到网关服务器，请确认地址、端口及局域网状态"
            is SocketTimeoutException -> "连接或响应超时，请稍后重试"
            is InterruptedIOException -> "网关请求超时，请稍后重试"
            is SSLException -> "HTTPS 安全证书校验失败，请检查网关证书配置"
            else -> e.message?.takeIf { it.isNotBlank() } ?: "网络请求异常，请检查网关配置后重试"
        }
    }
}
