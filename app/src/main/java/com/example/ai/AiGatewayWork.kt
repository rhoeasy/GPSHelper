package com.example.ai

import android.content.Context
import com.example.gps.COORDINATE_INSIGHT_SYSTEM_PROMPT
import com.example.gps.CoordinateRelation
import com.example.gps.MapCoordinate
import com.example.gps.SpeedUnit
import com.example.gps.coordinateInsightPrompt
import com.example.track.ActivityMode
import com.example.track.AiAnalysisRecordEntity
import com.example.track.TelemetryJsonExporter
import com.example.track.TrackFormatters
import com.example.track.TrackRepository
import com.example.track.TrackSessionDetail
import com.example.track.TrackSessionEntity
import java.io.IOException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.withContext

internal data class CoordinateAnswer(
    val current: MapCoordinate,
    val target: MapCoordinate?,
    val text: String = ""
)

/** Process-owned work and results. No request captures an Activity or a ViewModel. */
internal class AiGatewayWork(context: Context) {
    private val storage = AiGatewaySecureStorage(context.applicationContext)
    private val tracks = TrackRepository(context.applicationContext)
    private val queue = AiGatewayQueue(CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
    val status = queue.status

    private val _config = MutableStateFlow(storage.loadConfig())
    val config = _config.asStateFlow()
    private val _models = MutableStateFlow(storage.loadFetchedModels())
    val models = _models.asStateFlow()
    private val _modelTestResult = MutableStateFlow<AiConnectionState>(AiConnectionState.Idle)
    val modelTestResult = _modelTestResult.asStateFlow()
    private val _coordinateAnswer = MutableStateFlow<CoordinateAnswer?>(null)
    val coordinateAnswer = _coordinateAnswer.asStateFlow()
    val coordinateStatus = status.map { it[GatewayTaskKey.Coordinates] }.distinctUntilChanged()

    fun saveConfig(baseUrl: String, apiKey: String, modelId: String): Result<AiGatewayConfig> {
        val normalized = if (baseUrl.isBlank()) Result.success("") else OpenAiGatewayClient.normalizeAndValidateBaseUrl(baseUrl)
        return normalized.map { AiGatewayConfig(it, apiKey.trim(), modelId.trim()) }.onSuccess { updated ->
            if (_config.value != updated && status.value[GatewayTaskKey.Models] is GatewayTaskStatus.Active) cancelModelTest()
            storeConfig(updated)
            if (status.value[GatewayTaskKey.Models] is GatewayTaskStatus.Failed) cancelModelTest()
            _modelTestResult.value = AiConnectionState.Idle
        }.onFailure { err ->
            _modelTestResult.value = AiConnectionState.Error(err.message ?: "Base URL 无效")
        }
    }

    fun clearConfig() {
        queue.cancelAll()
        storage.clearAll()
        _config.value = AiGatewayConfig()
        _models.value = emptyList()
        _modelTestResult.value = AiConnectionState.Idle
        _coordinateAnswer.value = null
    }

    fun testModels(baseUrl: String, apiKey: String, modelId: String) {
        if (status.value[GatewayTaskKey.Models] is GatewayTaskStatus.Active) return
        val normalized = OpenAiGatewayClient.normalizeAndValidateBaseUrl(baseUrl).getOrElse { error ->
            queue.cancel(GatewayTaskKey.Models)
            _modelTestResult.value = AiConnectionState.Error(error.message ?: "Base URL 无效")
            return
        }
        val draft = AiGatewayConfig(normalized, apiKey.trim(), modelId.trim())
        _modelTestResult.value = AiConnectionState.Idle
        storeConfig(draft)
        queue.submit(GatewayTaskKey.Models) {
            val result = OpenAiGatewayClient.fetchModels(draft).getOrThrow()
            storage.saveFetchedModels(result)
            _models.value = result
            storeConfig(draft.copy(modelId = draft.modelId.ifBlank { result.firstOrNull().orEmpty() }))
            _modelTestResult.value = AiConnectionState.Success(result.size)
        }
    }

    fun cancelModelTest() = queue.cancel(GatewayTaskKey.Models)

    fun analyzeTrack(detail: TrackSessionDetail, speedUnit: SpeedUnit) {
        val config = _config.value
        val session = detail.session
        queue.submit(GatewayTaskKey.Coach(session.id), config.modelId) { progress ->
            check(config.isConfigured) { "请先在「设置 → AI 网关」填写 Base URL 与模型 ID" }
            val prompt = withContext(Dispatchers.Default) {
                val instructions = TelemetryJsonExporter.buildAiAnalysisPrompt(session, detail.laps)
                val payload = TelemetryJsonExporter.buildStructuredJson(session, detail.points, detail.laps, maxSamples = 220)
                "$instructions\n\n```json\n$payload\n```"
            }
            val coach = when (session.activityMode) {
                ActivityMode.KARTING -> "卡丁车赛道遥测数据分析教练"
                ActivityMode.OFFROAD_CROSSING -> "摩托车越野穿越与山路骑行遥测分析教练"
                ActivityMode.HIKING -> "户外徒步与运动生理分析教练"
            }
            val text = stream(config, "你是一位专业的${coach}，用清晰、结构化的简体中文回答。", prompt, progress)
            progress(GatewayStage.SAVING, "")
            tracks.saveAiCoachResult(AiAnalysisRecordEntity(
                sessionId = session.id,
                sessionName = session.name,
                activityMode = session.mode,
                modelId = config.modelId,
                reportText = text,
                summaryMetrics = summaryMetrics(session, speedUnit),
                generatedAtMs = System.currentTimeMillis()
            ))
        }
    }

    fun cancelTrack(sessionId: Long) = queue.cancel(GatewayTaskKey.Coach(sessionId))

    fun queryCoordinate(config: AiGatewayConfig, current: MapCoordinate, target: MapCoordinate?, relation: CoordinateRelation?) {
        if (status.value[GatewayTaskKey.Coordinates] is GatewayTaskStatus.Active) return
        val request = CoordinateAnswer(current, target)
        _coordinateAnswer.value = request
        queue.submit(GatewayTaskKey.Coordinates) { progress ->
            val text = stream(config, COORDINATE_INSIGHT_SYSTEM_PROMPT, coordinateInsightPrompt(current, target, relation), progress)
            _coordinateAnswer.value = request.copy(text = text)
        }
    }

    fun cancelCoordinate() = queue.cancel(GatewayTaskKey.Coordinates)

    @OptIn(FlowPreview::class)
    private suspend fun stream(
        config: AiGatewayConfig,
        system: String,
        prompt: String,
        progress: (GatewayStage, String) -> Unit
    ): String {
        val text = StringBuilder()
        var connected = false
        try {
            OpenAiGatewayClient.streamChatCompletion(config, system, prompt).timeout(25.seconds).collect { event ->
                when (event) {
                    GatewayChatEvent.Headers -> {
                        connected = true
                        progress(GatewayStage.WAITING_FOR_OUTPUT, "")
                    }
                    is GatewayChatEvent.Text -> {
                        text.append(event.text)
                        progress(GatewayStage.RECEIVING, text.toString())
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw IOException(when {
                !connected -> "网关未及时响应，请检查网络和 Base URL（25 秒）"
                text.isEmpty() -> "网关已响应，但模型 25 秒未输出内容，请检查模型或稍后重试"
                else -> "模型输出中断，25 秒未收到新内容，请重试"
            }, e)
        }
        return text.toString().trim().ifEmpty { throw IOException("模型未返回有效内容，请重试") }
    }

    private fun storeConfig(config: AiGatewayConfig) {
        storage.saveConfig(config)
        _config.value = config
    }

    private fun summaryMetrics(session: TrackSessionEntity, unit: SpeedUnit): String = buildList {
        add(TrackFormatters.formatDistance(session.distanceMeters))
        if (session.activityMode == ActivityMode.KARTING) {
            if (session.lapCount > 0) add("${session.lapCount} 圈")
            session.bestLapMs?.let { add("最佳 ${TrackFormatters.formatLapTime(it)}") }
        } else {
            add("爬升 ${TrackFormatters.formatElevationGain(session.elevationGainMeters)}")
        }
        add("极速 ${TrackFormatters.formatSpeed(session.maxSpeedMps, unit)}")
    }.joinToString(" · ")

    companion object {
        private var instance: AiGatewayWork? = null

        fun get(context: Context): AiGatewayWork = synchronized(this) {
            instance ?: AiGatewayWork(context.applicationContext).also { instance = it }
        }
    }
}
