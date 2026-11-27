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
import com.example.ble.BleHrDevice
import com.example.ble.HeartRateRepository
import com.example.ble.HeartRateState
import com.example.gps.GpsData
import com.example.gps.GpsRepository
import com.example.gps.SpeedUnit
import com.example.track.ActivityMode
import com.example.track.GpxExporter
import com.example.track.RecordingSnapshot
import com.example.track.TelemetryJsonExporter
import com.example.track.TrackRecorder
import com.example.track.TrackRepository
import com.example.track.TrackSessionDetail
import com.example.track.TrackSessionEntity
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
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

    val gpsData: StateFlow<GpsData> = GpsRepository.gpsData
    val speedUnit: StateFlow<SpeedUnit> = GpsRepository.speedUnit
    val keepScreenOn: StateFlow<Boolean> = GpsRepository.keepScreenOn
    val themeMode: StateFlow<ThemeMode> = GpsRepository.themeMode
    val autoFocusOnLaunch: StateFlow<Boolean> = GpsRepository.autoFocusOnLaunch
    val isOverlayRunning: StateFlow<Boolean> = GpsRepository.isOverlayRunning

    val heartRateState: StateFlow<HeartRateState> = HeartRateRepository.heartRateState
    val scannedBleDevices: StateFlow<List<BleHrDevice>> = HeartRateRepository.scannedDevices
    val isBleScanning: StateFlow<Boolean> = HeartRateRepository.isScanning
    val lastSavedBleDevice: StateFlow<Pair<String, String>?> = HeartRateRepository.lastSavedDevice

    val recordingSnapshot: StateFlow<RecordingSnapshot> = TrackRecorder.snapshot

    val savedSessions: StateFlow<List<TrackSessionEntity>> = trackRepository.allSessions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    private val _selectedSessionDetail = MutableStateFlow<TrackSessionDetail?>(null)
    val selectedSessionDetail: StateFlow<TrackSessionDetail?> = _selectedSessionDetail.asStateFlow()

    private val _permissionStatus = MutableStateFlow(readPermissions())
    val permissionStatus: StateFlow<PermissionStatus> = _permissionStatus.asStateFlow()

    private val _hasDndPolicyAccess = MutableStateFlow(readDndPolicyAccess())
    val hasDndPolicyAccess: StateFlow<Boolean> = _hasDndPolicyAccess.asStateFlow()

    init {
        GpsRepository.ensurePrefsLoaded(application)
        HeartRateRepository.loadLastDevice(application)
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

    fun setAutoFocusOnLaunch(enabled: Boolean) {
        GpsRepository.setAutoFocusOnLaunch(getApplication(), enabled)
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
        viewModelScope.launch {
            _selectedSessionDetail.value = withContext(Dispatchers.IO) {
                trackRepository.loadSessionDetail(sessionId)
            }
        }
    }

    fun closeSessionDetail() {
        _selectedSessionDetail.value = null
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                trackRepository.deleteSession(sessionId)
            }
            if (_selectedSessionDetail.value?.session?.id == sessionId) {
                _selectedSessionDetail.value = null
            }
        }
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

    fun exportDetailToJson(detail: TrackSessionDetail, onResult: (Uri?) -> Unit) {
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) {
                runCatching {
                    TelemetryJsonExporter.exportToShareUri(
                        context = getApplication(),
                        session = detail.session,
                        points = detail.points,
                        laps = detail.laps
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
}
