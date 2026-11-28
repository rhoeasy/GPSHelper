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
import com.example.ai.AiGatewayWork
import com.example.ai.GatewayStage
import com.example.ai.GatewayTaskKey
import com.example.ai.GatewayTaskStatus
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
import com.example.track.TrackEndpoints
import com.example.track.TrackMergeEngine
import com.example.track.TrackMergePreview
import com.example.track.TrackRecorder
import com.example.track.TrackRepository
import com.example.track.TrackSessionDetail
import com.example.track.TrackSessionEntity
import com.example.ui.theme.AppThemePalette
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
    private val gatewayWork = AiGatewayWork.get(application)

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

    val aiGatewayConfig = gatewayWork.config
    val aiFetchedModels = gatewayWork.models
    val aiConnectionState = combine(gatewayWork.status, gatewayWork.modelTestResult) { jobs, result ->
        when (val job = jobs[GatewayTaskKey.Models]) {
            is GatewayTaskStatus.Active -> if (job.stage == GatewayStage.QUEUED) AiConnectionState.Queued else AiConnectionState.Testing
            is GatewayTaskStatus.Failed -> AiConnectionState.Error(job.message)
            null -> result
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), AiConnectionState.Idle)

    @OptIn(ExperimentalCoroutinesApi::class)
    val aiAnalysisState: StateFlow<AiAnalysisState> = _selectedSessionDetail.map { it?.session?.id }
        .distinctUntilChanged().flatMapLatest { sessionId ->
            if (sessionId == null) flowOf(AiAnalysisState.Idle) else {
                combine(gatewayWork.status, gatewayWork.config, trackRepository.observeAiReport(sessionId)) { jobs, config, report ->
                    when (val job = jobs[GatewayTaskKey.Coach(sessionId)]) {
                        is GatewayTaskStatus.Active -> AiAnalysisState.Streaming(sessionId, job.modelId ?: config.modelId, job.partialText, job.stage)
                        is GatewayTaskStatus.Failed -> AiAnalysisState.Error(sessionId, job.message)
                        null -> report?.let { AiAnalysisState.Success(sessionId, it.modelId, it.reportText, it.generatedAtMs, true) }
                            ?: AiAnalysisState.Idle
                    }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), AiAnalysisState.Idle)

    private var detailLoadJob: Job? = null

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
        MotionSensorManager.stopListening(VIEWMODEL_SENSOR_CLIENT_ID)
        EnvironmentSensorManager.stopListening(VIEWMODEL_ENV_CLIENT_ID)
        super.onCleared()
    }

    // BLE Heart Rate controls
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
        detailLoadJob?.cancel()
        detailLoadJob = viewModelScope.launch {
            _selectedSessionDetail.value = withContext(Dispatchers.IO) { trackRepository.loadSessionDetail(sessionId) }
        }
    }

    fun closeSessionDetail() {
        detailLoadJob?.cancel()
        _selectedSessionDetail.value = null
    }

    fun deleteSession(sessionId: Long) {
        gatewayWork.cancelTrack(sessionId)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                trackRepository.deleteSession(sessionId)
            }
            if (_selectedSessionDetail.value?.session?.id == sessionId) {
                closeSessionDetail()
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

    // AI commands enqueue work in the process-owned queue, independently of the selected page.
    fun saveAiGatewayConfig(baseUrl: String, apiKey: String, modelId: String): Result<AiGatewayConfig> =
        gatewayWork.saveConfig(baseUrl, apiKey, modelId)

    fun clearAiGatewayConfig() = gatewayWork.clearConfig()

    fun testAndFetchAiModels(baseUrl: String, apiKey: String, currentModelId: String) =
        gatewayWork.testModels(baseUrl, apiKey, currentModelId)

    fun cancelAiModelTest() = gatewayWork.cancelModelTest()

    fun generateAiCoachReport(detail: TrackSessionDetail) = gatewayWork.analyzeTrack(detail, speedUnit.value)

    fun stopAiCoachGeneration() {
        _selectedSessionDetail.value?.session?.id?.let(gatewayWork::cancelTrack)
    }

    fun deleteAiAnalysisRecord(recordId: Long) {
        viewModelScope.launch(Dispatchers.IO) { trackRepository.deleteAiAnalysisRecord(recordId) }
    }

    fun clearAllAiAnalysisHistory() {
        viewModelScope.launch(Dispatchers.IO) { trackRepository.clearAllAiAnalysisHistory() }
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
    }
}
