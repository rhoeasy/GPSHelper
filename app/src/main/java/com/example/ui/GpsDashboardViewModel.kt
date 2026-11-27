package com.example.ui

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiAnalysisState
import com.example.ai.AiConnectionState
import com.example.ai.AiGatewayConfig
import com.example.ai.AiGatewaySecureStorage
import com.example.ai.OpenAiGatewayClient
import com.example.ble.BleHrDevice
import com.example.ble.HeartRateRepository
import com.example.ble.HeartRateState
import com.example.ble.SavedBleDevice
import com.example.gps.AccuracyTelemetry
import com.example.gps.AltitudeTelemetry
import com.example.gps.GnssSatelliteInfo
import com.example.gps.GpsData
import com.example.gps.GpsRepository
import com.example.gps.OverlayDisplayConfig
import com.example.gps.SafetyAlertConfig
import com.example.gps.SpeedUnit
import com.example.sensor.EnvironmentSensorManager
import com.example.sensor.EnvironmentTelemetry
import com.example.sensor.GForceData
import com.example.sensor.MotionSensorManager
import com.example.track.ActivityMode
import com.example.track.AiAnalysisRecordEntity
import com.example.track.GpxExporter
import com.example.track.RecordingSnapshot
import com.example.track.TelemetryJsonExporter
import com.example.track.TrackAiReportEntity
import com.example.track.TrackEndpoints
import com.example.track.TrackFormatters
import com.example.track.TrackMergeEngine
import com.example.track.TrackMergePreview
import com.example.track.TrackRecorder
import com.example.track.TrackRepository
import com.example.track.TrackSessionDetail
import com.example.track.TrackSessionEntity
import com.example.ui.theme.AppThemePalette
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PermissionStatus(
    val hasFineLocation: Boolean = false,
    val hasAnyLocation: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val hasNotificationPermission: Boolean = true,
    val requiresNotificationPermission: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
) {
    val hasMissingPermissions: Boolean
        get() = !hasFineLocation || !hasOverlayPermission || (requiresNotificationPermission && !hasNotificationPermission)
}

class GpsDashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val trackRepository = TrackRepository(application)
    private val aiGatewayStorage = AiGatewaySecureStorage(application)

    val gpsData: StateFlow<GpsData> = GpsRepository.gpsData
    val satellites: StateFlow<List<GnssSatelliteInfo>> = GpsRepository.satellites
    val altitudeTelemetry: StateFlow<AltitudeTelemetry> = GpsRepository.altitudeTelemetry
    val accuracyTelemetry: StateFlow<AccuracyTelemetry> = GpsRepository.accuracyTelemetry
    val environmentTelemetry: StateFlow<EnvironmentTelemetry> = EnvironmentSensorManager.telemetry
    val gForceData: StateFlow<GForceData> = MotionSensorManager.gForceData
    val speedUnit: StateFlow<SpeedUnit> = GpsRepository.speedUnit
    val keepScreenOn: StateFlow<Boolean> = GpsRepository.keepScreenOn
    val themeMode: StateFlow<ThemeMode> = GpsRepository.themeMode
    val themePalette: StateFlow<AppThemePalette> = GpsRepository.themePalette
    val autoFocusOnLaunch: StateFlow<Boolean> = GpsRepository.autoFocusOnLaunch
    val overlayConfig: StateFlow<OverlayDisplayConfig> = GpsRepository.overlayConfig
    val safetyAlertConfig: StateFlow<SafetyAlertConfig> = GpsRepository.safetyAlertConfig
    val isOverlayRunning: StateFlow<Boolean> = GpsRepository.isOverlayRunning

    val heartRateState: StateFlow<HeartRateState> = HeartRateRepository.heartRateState
    val localBleDevices: StateFlow<List<BleHrDevice>> = HeartRateRepository.localDevices
    val scannedBleDevices: StateFlow<List<BleHrDevice>> = HeartRateRepository.scannedDevices
    val isBleScanning: StateFlow<Boolean> = HeartRateRepository.isScanning
    val lastSavedBleDevice: StateFlow<SavedBleDevice?> = HeartRateRepository.lastSavedDevice
    val autoReconnectBle: StateFlow<Boolean> = HeartRateRepository.autoReconnectEnabled

    val recordingSnapshot: StateFlow<RecordingSnapshot> = TrackRecorder.snapshot

    val savedSessions: StateFlow<List<TrackSessionEntity>> = trackRepository.allSessions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    val aiAnalysisHistory: StateFlow<List<AiAnalysisRecordEntity>> = trackRepository.allAiAnalysisHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    private val _selectedSessionDetail = MutableStateFlow<TrackSessionDetail?>(null)
    val selectedSessionDetail: StateFlow<TrackSessionDetail?> = _selectedSessionDetail.asStateFlow()

    private val _sessionEndpoints = MutableStateFlow<Map<Long, TrackEndpoints>>(emptyMap())
    val sessionEndpoints: StateFlow<Map<Long, TrackEndpoints>> = _sessionEndpoints.asStateFlow()

    private val _mergePreview = MutableStateFlow<TrackMergePreview?>(null)
    val mergePreview: StateFlow<TrackMergePreview?> = _mergePreview.asStateFlow()

    private val _isMergingTracks = MutableStateFlow(false)
    val isMergingTracks: StateFlow<Boolean> = _isMergingTracks.asStateFlow()

    private val _aiGatewayConfig = MutableStateFlow(aiGatewayStorage.loadConfig())
    val aiGatewayConfig: StateFlow<AiGatewayConfig> = _aiGatewayConfig.asStateFlow()

    private val _aiFetchedModels = MutableStateFlow(aiGatewayStorage.loadFetchedModels())
    val aiFetchedModels: StateFlow<List<String>> = _aiFetchedModels.asStateFlow()

    private val _aiConnectionState = MutableStateFlow<AiConnectionState>(AiConnectionState.Idle)
    val aiConnectionState: StateFlow<AiConnectionState> = _aiConnectionState.asStateFlow()

    private val _aiAnalysisState = MutableStateFlow<AiAnalysisState>(AiAnalysisState.Idle)
    val aiAnalysisState: StateFlow<AiAnalysisState> = _aiAnalysisState.asStateFlow()

    private var aiAnalysisJob: Job? = null
    private var aiTestJob: Job? = null

    private val _permissionStatus = MutableStateFlow(readPermissions())
    val permissionStatus: StateFlow<PermissionStatus> = _permissionStatus.asStateFlow()

    private val _hasDndPolicyAccess = MutableStateFlow(readDndPolicyAccess())
    val hasDndPolicyAccess: StateFlow<Boolean> = _hasDndPolicyAccess.asStateFlow()

    init {
        GpsRepository.ensurePrefsLoaded(application)
        HeartRateRepository.loadLastDevice(application)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { trackRepository.ensureSampleTracksSeeded() }
        }
    }

    fun refreshPermissions() {
        _permissionStatus.value = readPermissions()
        _hasDndPolicyAccess.value = readDndPolicyAccess()
        GpsRepository.refreshTrackingState(getApplication())
    }

    fun setSpeedUnit(unit: SpeedUnit) {
        GpsRepository.setSpeedUnit(getApplication(), unit)
    }

    fun setKeepScreenOn(enabled: Boolean) {
        GpsRepository.setKeepScreenOn(getApplication(), enabled)
    }

    fun setThemeMode(mode: ThemeMode) {
        GpsRepository.setThemeMode(getApplication(), mode)
    }

    fun setThemePalette(palette: AppThemePalette) {
        GpsRepository.setThemePalette(getApplication(), palette)
    }

    fun setAutoFocusOnLaunch(enabled: Boolean) {
        GpsRepository.setAutoFocusOnLaunch(getApplication(), enabled)
    }

    fun updateOverlayConfig(transform: (OverlayDisplayConfig) -> OverlayDisplayConfig): Boolean {
        return GpsRepository.updateOverlayConfig(getApplication(), transform)
    }

    fun resetOverlayConfig() {
        GpsRepository.resetOverlayConfig(getApplication())
    }

    fun updateSafetyAlertConfig(transform: (SafetyAlertConfig) -> SafetyAlertConfig) {
        GpsRepository.updateSafetyAlertConfig(getApplication(), transform)
    }

    // IMU (Accelerometer + Gyroscope) G-Force controls
    fun startMotionSensors() {
        MotionSensorManager.startListening(getApplication(), VIEWMODEL_SENSOR_CLIENT_ID)
    }

    fun stopMotionSensors() {
        MotionSensorManager.stopListening(VIEWMODEL_SENSOR_CLIENT_ID)
    }

    fun calibrateGForceZero() {
        MotionSensorManager.calibrateZero()
    }

    fun resetGForcePeak() {
        MotionSensorManager.resetPeak()
    }

    // Hardware Compass & Barometer (only active while Altitude or Compass detail screen is open)
    fun startEnvironmentSensors() {
        EnvironmentSensorManager.startListening(getApplication(), VIEWMODEL_ENV_CLIENT_ID)
    }

    fun stopEnvironmentSensors() {
        EnvironmentSensorManager.stopListening(VIEWMODEL_ENV_CLIENT_ID)
    }

    fun resetAltitudeSession() {
        GpsRepository.resetAltitudeSession()
    }

    fun resetAccuracySession() {
        GpsRepository.resetAccuracySession()
    }

    fun calibrateBarometerToGps(): Boolean {
        val gpsAlt = altitudeTelemetry.value.currentAltitudeM ?: gpsData.value.altitudeMeters ?: return false
        return EnvironmentSensorManager.calibrateBarometerToAltitude(gpsAlt)
    }

    fun resetBarometerQnh() {
        EnvironmentSensorManager.resetBarometerQnh()
    }

    override fun onCleared() {
        aiAnalysisJob?.cancel()
        aiTestJob?.cancel()
        MotionSensorManager.stopListening(VIEWMODEL_SENSOR_CLIENT_ID)
        EnvironmentSensorManager.stopListening(VIEWMODEL_ENV_CLIENT_ID)
        super.onCleared()
    }

    // BLE Heart Rate controls
    fun refreshLocalBleDevices() {
        HeartRateRepository.refreshLocalDevices(getApplication())
    }

    fun startBleScan() {
        HeartRateRepository.startScan(getApplication())
    }

    fun stopBleScan() {
        HeartRateRepository.stopScan()
    }

    fun connectBleDevice(name: String, address: String) {
        HeartRateRepository.connect(getApplication(), address = address, deviceName = name)
    }

    fun quickReconnectLastBle(): Boolean {
        val saved = lastSavedBleDevice.value ?: return false
        HeartRateRepository.connect(getApplication(), address = saved.address, deviceName = saved.name)
        return true
    }

    fun tryAutoReconnectBle() {
        HeartRateRepository.tryAutoReconnectIfEnabled(getApplication())
    }

    fun setAutoReconnectBle(enabled: Boolean) {
        HeartRateRepository.setAutoReconnectEnabled(getApplication(), enabled)
    }

    fun clearLastSavedBleDevice() {
        HeartRateRepository.clearLastSavedDevice(getApplication())
    }

    fun disconnectBleDevice() {
        HeartRateRepository.disconnect()
    }

    // Track recording controls
    fun selectActivityMode(mode: ActivityMode) {
        TrackRecorder.selectMode(mode)
    }

    fun startTrackRecording() {
        TrackRecorder.startRecording(getApplication())
    }

    fun pauseTrackRecording() {
        TrackRecorder.pauseRecording()
    }

    fun resumeTrackRecording() {
        TrackRecorder.resumeRecording()
    }

    fun setKartingStartGate(): Boolean {
        return TrackRecorder.setStartFinishGateToCurrentPosition()
    }

    fun finishAndSaveRecording(onCompleted: (savedSessionId: Long?) -> Unit) {
        viewModelScope.launch {
            val savedId = TrackRecorder.stopAndSave(getApplication())
            if (savedId != null) {
                loadSessionDetail(savedId)
            }
            onCompleted(savedId)
        }
    }

    // Track history & detail controls
    fun loadSessionDetail(sessionId: Long) {
        aiAnalysisJob?.cancel()
        _aiAnalysisState.value = AiAnalysisState.Idle
        viewModelScope.launch {
            val (detail, cachedReport) = withContext(Dispatchers.IO) {
                val loadedDetail = trackRepository.loadSessionDetail(sessionId)
                val cached = if (loadedDetail != null) {
                    trackRepository.getAiReport(sessionId)
                } else {
                    null
                }
                loadedDetail to cached
            }
            _selectedSessionDetail.value = detail
            if (detail != null && cachedReport != null) {
                _aiAnalysisState.value = AiAnalysisState.Success(
                    sessionId = cachedReport.sessionId,
                    modelId = cachedReport.modelId,
                    reportText = cachedReport.reportText,
                    generatedAtMs = cachedReport.generatedAtMs,
                    isFromCache = true
                )
            }
        }
    }

    fun closeSessionDetail() {
        aiAnalysisJob?.cancel()
        _aiAnalysisState.value = AiAnalysisState.Idle
        _selectedSessionDetail.value = null
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                trackRepository.deleteSession(sessionId)
            }
            if (_selectedSessionDetail.value?.session?.id == sessionId) {
                aiAnalysisJob?.cancel()
                _aiAnalysisState.value = AiAnalysisState.Idle
                _selectedSessionDetail.value = null
            }
        }
    }

    // Track Merge (Hiking & Off-road Crossing only)
    fun loadMergeableEndpoints() {
        val eligibleIds = savedSessions.value
            .filter { TrackMergeEngine.isModeMergeable(it.activityMode) }
            .map { it.id }
        if (eligibleIds.isEmpty()) {
            _sessionEndpoints.value = emptyMap()
            return
        }
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching { trackRepository.loadSessionEndpoints(eligibleIds) }
                    .getOrDefault(emptyMap())
            }
            _sessionEndpoints.value = loaded
        }
    }

    fun prepareMergePreview(
        sessionIds: List<Long>,
        onError: (String) -> Unit
    ) {
        if (sessionIds.size < 2) {
            onError("请选择至少 2 条同类型的徒步或越野穿越轨迹")
            return
        }
        viewModelScope.launch {
            val previewResult = withContext(Dispatchers.IO) {
                val details = sessionIds.mapNotNull { id ->
                    trackRepository.loadSessionDetail(id)
                }
                TrackMergeEngine.buildPreview(details)
            }
            previewResult.fold(
                onSuccess = { preview ->
                    _mergePreview.value = preview
                },
                onFailure = { err ->
                    onError(err.message ?: "无法预览合并，请确认已选择同类型轨迹")
                }
            )
        }
    }

    fun dismissMergePreview() {
        if (!_isMergingTracks.value) {
            _mergePreview.value = null
        }
    }

    fun confirmMergeTracks(
        mergedName: String,
        keepOriginalSegments: Boolean,
        onCompleted: (mergedCount: Int, newSessionId: Long?) -> Unit
    ) {
        val preview = _mergePreview.value ?: return
        if (_isMergingTracks.value) return
        _isMergingTracks.value = true

        viewModelScope.launch {
            val savedId = withContext(Dispatchers.IO) {
                val mergedResult = TrackMergeEngine.merge(
                    details = preview.orderedDetails,
                    customName = mergedName
                )
                val mergedDetail = mergedResult.getOrNull() ?: return@withContext null
                val deleteIds = if (keepOriginalSegments) {
                    emptyList()
                } else {
                    preview.orderedSessions.map { it.id }
                }
                runCatching {
                    trackRepository.saveMergedSession(
                        mergedDetail = mergedDetail,
                        deleteSourceIds = deleteIds
                    )
                }.getOrNull()
            }
            _isMergingTracks.value = false
            _mergePreview.value = null
            if (savedId != null) {
                loadSessionDetail(savedId)
            }
            onCompleted(preview.orderedSessions.size, savedId)
        }
    }

    // OpenAI-Compatible AI Gateway & Track Coach Analysis
    fun saveAiGatewayConfig(baseUrl: String, apiKey: String, modelId: String): Result<AiGatewayConfig> {
        val trimmedUrl = baseUrl.trim()
        val normalizedUrl = if (trimmedUrl.isNotEmpty()) {
            OpenAiGatewayClient.normalizeAndValidateBaseUrl(trimmedUrl)
                .getOrElse { err ->
                    _aiConnectionState.value = AiConnectionState.Error(err.message ?: "Base URL 无效")
                    return Result.failure(err)
                }
        } else {
            ""
        }
        val updated = AiGatewayConfig(
            baseUrl = normalizedUrl,
            apiKey = apiKey.trim(),
            modelId = modelId.trim()
        )
        aiGatewayStorage.saveConfig(updated)
        _aiGatewayConfig.value = updated
        if (_aiConnectionState.value is AiConnectionState.Error) {
            _aiConnectionState.value = AiConnectionState.Idle
        }
        return Result.success(updated)
    }

    fun clearAiGatewayConfig() {
        aiTestJob?.cancel()
        aiAnalysisJob?.cancel()
        aiGatewayStorage.clearAll()
        _aiGatewayConfig.value = AiGatewayConfig()
        _aiFetchedModels.value = emptyList()
        _aiConnectionState.value = AiConnectionState.Idle
    }

    fun testAndFetchAiModels(baseUrl: String, apiKey: String, currentModelId: String) {
        aiTestJob?.cancel()
        val validatedUrl = OpenAiGatewayClient.normalizeAndValidateBaseUrl(baseUrl)
            .getOrElse { err ->
                _aiConnectionState.value = AiConnectionState.Error(err.message ?: "Base URL 格式无效")
                return
            }

        val draftConfig = AiGatewayConfig(
            baseUrl = validatedUrl,
            apiKey = apiKey.trim(),
            modelId = currentModelId.trim()
        )
        aiGatewayStorage.saveConfig(draftConfig)
        _aiGatewayConfig.value = draftConfig
        _aiConnectionState.value = AiConnectionState.Testing

        aiTestJob = viewModelScope.launch {
            val result = OpenAiGatewayClient.fetchModels(draftConfig)
            result.fold(
                onSuccess = { models ->
                    aiGatewayStorage.saveFetchedModels(models)
                    _aiFetchedModels.value = models
                    val resolvedModel = draftConfig.modelId.ifBlank { models.firstOrNull().orEmpty() }
                    val finalConfig = draftConfig.copy(modelId = resolvedModel)
                    aiGatewayStorage.saveConfig(finalConfig)
                    _aiGatewayConfig.value = finalConfig
                    _aiConnectionState.value = AiConnectionState.Success(models.size)
                },
                onFailure = { err ->
                    _aiConnectionState.value = AiConnectionState.Error(
                        err.message ?: "无法连接 AI 网关，请检查地址与密钥"
                    )
                }
            )
        }
    }

    fun generateAiCoachReport(detail: TrackSessionDetail) {
        val config = _aiGatewayConfig.value
        val sessionId = detail.session.id
        if (!config.isConfigured) {
            _aiAnalysisState.value = AiAnalysisState.Error(
                sessionId = sessionId,
                message = "请先在「设置 → AI 网关」填写 Base URL 与模型 ID"
            )
            return
        }

        aiAnalysisJob?.cancel()
        val activeModel = config.modelId.trim()
        _aiAnalysisState.value = AiAnalysisState.Streaming(
            sessionId = sessionId,
            modelId = activeModel,
            partialText = ""
        )

        aiAnalysisJob = viewModelScope.launch {
            val (systemPrompt, userPrompt) = withContext(Dispatchers.Default) {
                val modeRole = when (detail.session.activityMode) {
                    ActivityMode.KARTING -> "你是一位专业的卡丁车赛道遥测数据分析教练，擅长用清晰、结构化的中文给出专业且可落地的复盘建议。"
                    ActivityMode.OFFROAD_CROSSING -> "你是一位专业的摩托车越野穿越与山路骑行遥测分析教练，擅长用清晰、结构化的中文给出控车、配速与安全建议。"
                    ActivityMode.HIKING -> "你是一位专业的户外徒步与运动生理分析教练，擅长用清晰、结构化的中文分析爬升节奏、心肺负荷与体能分配。"
                }
                val coachPrompt = TelemetryJsonExporter.buildAiAnalysisPrompt(detail.session, detail.laps)
                val telemetryJson = TelemetryJsonExporter.buildStructuredJson(
                    session = detail.session,
                    points = detail.points,
                    laps = detail.laps,
                    maxSamples = AI_COACH_MAX_SAMPLES
                )
                modeRole to "$coachPrompt\n\n```json\n$telemetryJson\n```"
            }

            val accumulated = StringBuilder()
            try {
                OpenAiGatewayClient.streamChatCompletion(
                    config = config,
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt
                ).collect { chunk ->
                    accumulated.append(chunk)
                    _aiAnalysisState.value = AiAnalysisState.Streaming(
                        sessionId = sessionId,
                        modelId = activeModel,
                        partialText = accumulated.toString()
                    )
                }

                val finalText = accumulated.toString().trim()
                if (finalText.isEmpty()) {
                    _aiAnalysisState.value = AiAnalysisState.Error(
                        sessionId = sessionId,
                        message = "模型未返回有效复盘内容，请重试"
                    )
                    return@launch
                }

                val generatedAtMs = System.currentTimeMillis()
                val summaryMetrics = buildAiHistorySummaryMetrics(detail.session, speedUnit.value)
                withContext(Dispatchers.IO) {
                    trackRepository.saveAiReport(
                        TrackAiReportEntity(
                            sessionId = sessionId,
                            modelId = activeModel,
                            reportText = finalText,
                            generatedAtMs = generatedAtMs
                        )
                    )
                    trackRepository.saveAiAnalysisRecord(
                        AiAnalysisRecordEntity(
                            sessionId = sessionId,
                            sessionName = detail.session.name,
                            activityMode = detail.session.mode,
                            modelId = activeModel,
                            reportText = finalText,
                            summaryMetrics = summaryMetrics,
                            generatedAtMs = generatedAtMs
                        )
                    )
                }
                _aiAnalysisState.value = AiAnalysisState.Success(
                    sessionId = sessionId,
                    modelId = activeModel,
                    reportText = finalText,
                    generatedAtMs = generatedAtMs,
                    isFromCache = false
                )
            } catch (e: CancellationException) {
                val partial = accumulated.toString().trim()
                if (partial.isNotEmpty()) {
                    _aiAnalysisState.value = AiAnalysisState.Success(
                        sessionId = sessionId,
                        modelId = activeModel,
                        reportText = partial,
                        generatedAtMs = System.currentTimeMillis(),
                        isFromCache = false
                    )
                } else {
                    val cached = withContext(Dispatchers.IO) {
                        trackRepository.getAiReport(sessionId)
                    }
                    _aiAnalysisState.value = if (cached != null) {
                        AiAnalysisState.Success(
                            sessionId = cached.sessionId,
                            modelId = cached.modelId,
                            reportText = cached.reportText,
                            generatedAtMs = cached.generatedAtMs,
                            isFromCache = true
                        )
                    } else {
                        AiAnalysisState.Idle
                    }
                }
                throw e
            } catch (e: Exception) {
                _aiAnalysisState.value = AiAnalysisState.Error(
                    sessionId = sessionId,
                    message = e.message?.takeIf { it.isNotBlank() } ?: "AI 复盘生成失败，请检查网关配置或网络状态"
                )
            }
        }
    }

    fun stopAiCoachGeneration() {
        aiAnalysisJob?.cancel()
    }

    fun deleteAiAnalysisRecord(recordId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            trackRepository.deleteAiAnalysisRecord(recordId)
        }
    }

    fun clearAllAiAnalysisHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            trackRepository.clearAllAiAnalysisHistory()
        }
    }

    private fun buildAiHistorySummaryMetrics(
        session: TrackSessionEntity,
        unit: SpeedUnit
    ): String {
        val parts = mutableListOf<String>()
        parts += TrackFormatters.formatDistance(session.distanceMeters)
        if (session.activityMode == ActivityMode.KARTING) {
            if (session.lapCount > 0) {
                parts += "${session.lapCount} 圈"
            }
            session.bestLapMs?.let { best ->
                parts += "最佳 ${TrackFormatters.formatLapTime(best)}"
            }
        } else {
            parts += "爬升 ${TrackFormatters.formatElevationGain(session.elevationGainMeters)}"
        }
        parts += "极速 ${TrackFormatters.formatSpeed(session.maxSpeedMps, unit)}"
        return parts.joinToString(" · ")
    }

    fun exportDetailToGpx(detail: TrackSessionDetail, onResult: (Uri?) -> Unit) {
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) {
                runCatching {
                    GpxExporter.exportToShareUri(
                        context = getApplication(),
                        session = detail.session,
                        points = detail.points
                    )
                }.getOrNull()
            }
            onResult(uri)
        }
    }

    fun buildJsonClipboardPayload(
        detail: TrackSessionDetail,
        includeAiPrompt: Boolean,
        onResult: (String) -> Unit
    ) {
        viewModelScope.launch {
            val payload = withContext(Dispatchers.Default) {
                TelemetryJsonExporter.buildClipboardPayload(
                    session = detail.session,
                    points = detail.points,
                    laps = detail.laps,
                    includeAiPrompt = includeAiPrompt
                )
            }
            onResult(payload)
        }
    }

    fun exportDetailToJson(
        detail: TrackSessionDetail,
        includeAiPrompt: Boolean = false,
        onResult: (Uri?) -> Unit
    ) {
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) {
                runCatching {
                    TelemetryJsonExporter.exportToShareUri(
                        context = getApplication(),
                        session = detail.session,
                        points = detail.points,
                        laps = detail.laps,
                        includeAiPrompt = includeAiPrompt
                    )
                }.getOrNull()
            }
            onResult(uri)
        }
    }

    private fun readDndPolicyAccess(): Boolean {
        val ctx = getApplication<Application>()
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        return runCatching { nm?.isNotificationPolicyAccessGranted == true }.getOrDefault(false)
    }

    private fun readPermissions(): PermissionStatus {
        val ctx = getApplication<Application>()
        val fineGranted = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val overlayGranted = Settings.canDrawOverlays(ctx)
        val notifRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val notifGranted = if (notifRequired) {
            ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return PermissionStatus(
            hasFineLocation = fineGranted,
            hasAnyLocation = fineGranted || coarseGranted,
            hasOverlayPermission = overlayGranted,
            hasNotificationPermission = notifGranted,
            requiresNotificationPermission = notifRequired
        )
    }

    companion object {
        private const val VIEWMODEL_SENSOR_CLIENT_ID = "gps_dashboard_viewmodel"
        private const val VIEWMODEL_ENV_CLIENT_ID = "gps_dashboard_env_viewmodel"
        private const val AI_COACH_MAX_SAMPLES = 220
    }
}
