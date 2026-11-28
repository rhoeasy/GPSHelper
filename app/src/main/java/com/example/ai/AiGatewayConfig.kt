package com.example.ai

internal const val DEFAULT_GENERATION_WAIT_SECONDS = 90
internal val GENERATION_WAIT_SECONDS = 25..300

/**
 * Configuration for the user's OpenAI-compatible AI gateway.
 * When [baseUrl] or [modelId] is blank, the app performs zero network requests
 * and retains its fully offline experience.
 */
data class AiGatewayConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
    val modelId: String = "",
    val generationWaitSeconds: Int = DEFAULT_GENERATION_WAIT_SECONDS
) {
    internal val effectiveWaitSeconds: Int get() = generationWaitSeconds.coerceIn(GENERATION_WAIT_SECONDS)
    val hasBaseUrl: Boolean
        get() = baseUrl.isNotBlank()

    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && modelId.isNotBlank()

    val maskedApiKeyPreview: String
        get() {
            val trimmed = apiKey.trim()
            if (trimmed.isEmpty()) return ""
            return if (trimmed.length <= 8) {
                "••••••"
            } else {
                "${trimmed.take(4)}••••${trimmed.takeLast(4)}"
            }
        }
}

sealed interface AiConnectionState {
    data object Idle : AiConnectionState
    data object Queued : AiConnectionState
    data object Testing : AiConnectionState
    data class Success(val modelCount: Int) : AiConnectionState
    data class Error(val message: String) : AiConnectionState
}

sealed interface AiAnalysisState {
    data object Idle : AiAnalysisState
    data class Streaming(
        val sessionId: Long,
        val modelId: String,
        val partialText: String,
        val stage: GatewayStage = GatewayStage.RECEIVING
    ) : AiAnalysisState

    data class Success(
        val sessionId: Long,
        val modelId: String,
        val reportText: String,
        val generatedAtMs: Long,
        val isFromCache: Boolean
    ) : AiAnalysisState

    data class Error(
        val sessionId: Long,
        val message: String
    ) : AiAnalysisState
}
