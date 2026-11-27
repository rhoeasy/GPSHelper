package com.example

import android.Manifest
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.alert.SafetyAlertMonitor
import com.example.ble.HeartRateRepository
import com.example.gps.GpsRepository
import com.example.overlay.FloatingGpsService
import com.example.sensor.HikingStepSensor
import com.example.sensor.MotionSensorManager
import com.example.track.ActivityMode
import com.example.ui.BleDevicePickerDialog
import com.example.ui.FocusModeDialog
import com.example.ui.GpsDashboardViewModel
import com.example.ui.GpsHudAppShell
import com.example.ui.MainTab
import com.example.ui.MergeTracksConfirmDialog
import com.example.ui.SettingsSubPage
import com.example.ui.TelemetryDetailPage
import com.example.ui.theme.GpsHudTheme
import com.example.ui.theme.isHudDarkTheme

class MainActivity : ComponentActivity() {

    private val viewModel: GpsDashboardViewModel by viewModels()
    private var previousInterruptionFilter: Int? = null
    private var focusLockActiveState by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        val launchWithFocusLock = savedInstanceState == null && viewModel.autoFocusOnLaunch.value

        setContent {
            LaunchedEffect(Unit) {
                if (launchWithFocusLock) {
                    window.decorView.post {
                        if (!isFinishing && !isDestroyed) enterFocusLockMode(showToast = false)
                    }
                }
            }

            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val themePalette by viewModel.themePalette.collectAsStateWithLifecycle()
            val isDarkTheme = isHudDarkTheme(themeMode)

            LaunchedEffect(isDarkTheme) {
                val barStyle = if (isDarkTheme) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(
                    statusBarStyle = barStyle,
                    navigationBarStyle = barStyle
                )
            }

            GpsHudTheme(themeMode = themeMode, themePalette = themePalette, darkTheme = isDarkTheme) {
                val gpsData by viewModel.gpsData.collectAsStateWithLifecycle()
                val satellites by viewModel.satellites.collectAsStateWithLifecycle()
                val altitudeTelemetry by viewModel.altitudeTelemetry.collectAsStateWithLifecycle()
                val accuracyTelemetry by viewModel.accuracyTelemetry.collectAsStateWithLifecycle()
                val environmentTelemetry by viewModel.environmentTelemetry.collectAsStateWithLifecycle()
                val gForceData by viewModel.gForceData.collectAsStateWithLifecycle()
                val speedUnit by viewModel.speedUnit.collectAsStateWithLifecycle()
                val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle()
                val autoFocusOnLaunch by viewModel.autoFocusOnLaunch.collectAsStateWithLifecycle()
                val overlayConfig by viewModel.overlayConfig.collectAsStateWithLifecycle()
                val safetyAlertConfig by viewModel.safetyAlertConfig.collectAsStateWithLifecycle()
                val hasDndPolicyAccess by viewModel.hasDndPolicyAccess.collectAsStateWithLifecycle()
                val isOverlayRunning by viewModel.isOverlayRunning.collectAsStateWithLifecycle()
                val permissionStatus by viewModel.permissionStatus.collectAsStateWithLifecycle()

                val heartRateState by viewModel.heartRateState.collectAsStateWithLifecycle()
                val localBleDevices by viewModel.localBleDevices.collectAsStateWithLifecycle()
                val scannedBleDevices by viewModel.scannedBleDevices.collectAsStateWithLifecycle()
                val isBleScanning by viewModel.isBleScanning.collectAsStateWithLifecycle()
                val lastSavedBleDevice by viewModel.lastSavedBleDevice.collectAsStateWithLifecycle()
                val autoReconnectBle by viewModel.autoReconnectBle.collectAsStateWithLifecycle()

                val recordingSnapshot by viewModel.recordingSnapshot.collectAsStateWithLifecycle()
                val savedSessions by viewModel.savedSessions.collectAsStateWithLifecycle()
                val selectedSessionDetail by viewModel.selectedSessionDetail.collectAsStateWithLifecycle()
                val sessionEndpoints by viewModel.sessionEndpoints.collectAsStateWithLifecycle()
                val mergePreview by viewModel.mergePreview.collectAsStateWithLifecycle()
                val isMergingTracks by viewModel.isMergingTracks.collectAsStateWithLifecycle()
                val aiGatewayConfig by viewModel.aiGatewayConfig.collectAsStateWithLifecycle()
                val aiFetchedModels by viewModel.aiFetchedModels.collectAsStateWithLifecycle()
                val aiConnectionState by viewModel.aiConnectionState.collectAsStateWithLifecycle()
                val aiAnalysisState by viewModel.aiAnalysisState.collectAsStateWithLifecycle()
                val aiAnalysisHistory by viewModel.aiAnalysisHistory.collectAsStateWithLifecycle()

                val alertState = SafetyAlertMonitor.evaluate(
                    gpsData = gpsData,
                    heartRateState = heartRateState,
                    config = safetyAlertConfig
                )

                LaunchedEffect(
                    gpsData.lastUpdateElapsedRealtimeMs,
                    heartRateState.lastUpdatedMs,
                    safetyAlertConfig
                ) {
                    SafetyAlertMonitor.maybeVibrate(
                        context = this@MainActivity,
                        alertState = alertState,
                        config = safetyAlertConfig
                    )
                }

                var activeTab by rememberSaveable { mutableStateOf(MainTab.DASHBOARD) }
                var selectedTelemetryDetail by rememberSaveable { mutableStateOf<TelemetryDetailPage?>(null) }
                var selectedSettingsSubPage by rememberSaveable { mutableStateOf<SettingsSubPage?>(null) }
                var showTrackRecorderSheet by rememberSaveable { mutableStateOf(false) }
                var requestedStepPermission by rememberSaveable { mutableStateOf(false) }
                var showAiAnalysisHistory by rememberSaveable { mutableStateOf(false) }
                var showBleDialog by rememberSaveable { mutableStateOf(false) }
                var reconnectBleAfterPermission by rememberSaveable { mutableStateOf(false) }
                var showFocusDialog by rememberSaveable { mutableStateOf(false) }
                var mergeCompletedSignal by rememberSaveable { mutableStateOf(0) }
                var isActivityStarted by rememberSaveable { mutableStateOf(true) }

                LifecycleEventEffect(Lifecycle.Event.ON_START) {
                    isActivityStarted = true
                }
                LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
                    isActivityStarted = false
                }

                // Register hardware compass & barometer only while Altitude or Compass detail screen is visible
                DisposableEffect(selectedTelemetryDetail, isActivityStarted) {
                    val needsEnvSensors = isActivityStarted && (
                        selectedTelemetryDetail == TelemetryDetailPage.ALTITUDE ||
                            selectedTelemetryDetail == TelemetryDetailPage.COMPASS
                        )
                    if (needsEnvSensors) {
                        viewModel.startEnvironmentSensors()
                    } else {
                        viewModel.stopEnvironmentSensors()
                    }
                    onDispose {
                        viewModel.stopEnvironmentSensors()
                    }
                }

                // Block accidental hardware/gesture Back exits while Focus Lock is active
                BackHandler(
                    enabled = focusLockActiveState &&
                        selectedSessionDetail == null &&
                        selectedTelemetryDetail == null &&
                        !showAiAnalysisHistory
                ) {
                    Toast.makeText(
                        this,
                        getString(R.string.toast_focus_locked_back),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                // Apply keep-screen-on window flag reactively (always on during Focus Lock)
                val effectiveKeepScreenOn = keepScreenOn || focusLockActiveState
                DisposableEffect(effectiveKeepScreenOn) {
                    if (effectiveKeepScreenOn) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                    onDispose {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                val locationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    viewModel.refreshPermissions()
                    if (GpsRepository.hasLocationPermission(this)) {
                        GpsRepository.startTracking(applicationContext, ACTIVITY_CLIENT_ID)
                    }
                }

                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) {
                    viewModel.refreshPermissions()
                }

                val stepPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) {
                    if (GpsRepository.hasLocationPermission(this)) viewModel.startTrackRecording()
                }

                fun continueBleAction(reconnectLast: Boolean) {
                    if (!HeartRateRepository.isBluetoothEnabled(this)) {
                        Toast.makeText(
                            this,
                            getString(R.string.toast_bluetooth_disabled),
                            Toast.LENGTH_SHORT
                        ).show()
                        runCatching { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                    } else if (!reconnectLast || !viewModel.quickReconnectLastBle()) {
                        showBleDialog = true
                        viewModel.startBleScan()
                    }
                }

                val blePermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    val reconnectLast = reconnectBleAfterPermission
                    reconnectBleAfterPermission = false
                    if (HeartRateRepository.hasBlePermissions(this)) {
                        continueBleAction(reconnectLast)
                    } else {
                        Toast.makeText(
                            this,
                            getString(R.string.toast_need_bluetooth_permission),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                fun requestBleAction(reconnectLast: Boolean) {
                    if (HeartRateRepository.hasBlePermissions(this)) {
                        continueBleAction(reconnectLast)
                        return
                    }
                    reconnectBleAfterPermission = reconnectLast
                    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                    } else {
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                    blePermissionLauncher.launch(permissions)
                }

                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    viewModel.refreshPermissions()
                    GpsRepository.startTracking(applicationContext, ACTIVITY_CLIENT_ID)
                }

                GpsHudAppShell(
                    activeTab = activeTab,
                    onSelectTab = { nextTab ->
                        activeTab = nextTab
                        if (nextTab != MainTab.SETTINGS) {
                            selectedSettingsSubPage = null
                        }
                    },
                    gpsData = gpsData,
                    gForceData = gForceData,
                    speedUnit = speedUnit,
                    keepScreenOn = keepScreenOn,
                    themeMode = themeMode,
                    themePalette = themePalette,
                    autoFocusOnLaunch = autoFocusOnLaunch,
                    overlayConfig = overlayConfig,
                    safetyAlertConfig = safetyAlertConfig,
                    alertState = alertState,
                    isFocusLockActive = focusLockActiveState,
                    isOverlayRunning = isOverlayRunning,
                    permissionStatus = permissionStatus,
                    heartRateState = heartRateState,
                    lastSavedBleDevice = lastSavedBleDevice,
                    recordingSnapshot = recordingSnapshot,
                    showTrackRecorderSheet = showTrackRecorderSheet,
                    onShowTrackRecorderSheetChange = { showTrackRecorderSheet = it },
                    savedSessions = savedSessions,
                    selectedSessionDetail = selectedSessionDetail,
                    selectedTelemetryDetail = selectedTelemetryDetail,
                    selectedSettingsSubPage = selectedSettingsSubPage,
                    onSelectSettingsSubPage = { selectedSettingsSubPage = it },
                    satellites = satellites,
                    altitudeTelemetry = altitudeTelemetry,
                    accuracyTelemetry = accuracyTelemetry,
                    environmentTelemetry = environmentTelemetry,
                    sessionEndpoints = sessionEndpoints,
                    mergeCompletedSignal = mergeCompletedSignal,
                    onSelectSpeedUnit = { unit -> viewModel.setSpeedUnit(unit) },
                    onToggleKeepScreenOn = { enabled -> viewModel.setKeepScreenOn(enabled) },
                    onSelectThemeMode = { mode -> viewModel.setThemeMode(mode) },
                    onSelectThemePalette = { palette -> viewModel.setThemePalette(palette) },
                    onToggleAutoFocusOnLaunch = { enabled -> viewModel.setAutoFocusOnLaunch(enabled) },
                    onUpdateOverlayConfig = { transform ->
                        val applied = viewModel.updateOverlayConfig(transform)
                        if (!applied) {
                            Toast.makeText(
                                this,
                                getString(R.string.toast_overlay_min_one_item),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onResetOverlayConfig = {
                        viewModel.resetOverlayConfig()
                        Toast.makeText(
                            this,
                            getString(R.string.toast_overlay_config_reset),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onUpdateSafetyAlertConfig = { transform ->
                        viewModel.updateSafetyAlertConfig(transform)
                    },
                    onOpenFocusDialog = { showFocusDialog = true },
                    onExitFocusLock = { exitFocusLockMode(showToast = true) },
                    onCalibrateGForceZero = {
                        viewModel.calibrateGForceZero()
                        Toast.makeText(
                            this,
                            getString(R.string.toast_gforce_calibrated),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onResetGForcePeak = {
                        viewModel.resetGForcePeak()
                    },
                    onOpenTelemetryDetail = { page ->
                        selectedTelemetryDetail = page
                    },
                    onCloseTelemetryDetail = {
                        selectedTelemetryDetail = null
                    },
                    onResetAltitudeSession = {
                        viewModel.resetAltitudeSession()
                        Toast.makeText(
                            this,
                            getString(R.string.toast_altitude_session_reset),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onResetAccuracySession = {
                        viewModel.resetAccuracySession()
                        Toast.makeText(
                            this,
                            getString(R.string.toast_accuracy_session_reset),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onCalibrateBarometer = {
                        val calibrated = viewModel.calibrateBarometerToGps()
                        Toast.makeText(
                            this,
                            getString(
                                if (calibrated) R.string.toast_barometer_calibrated else R.string.toast_barometer_need_gps
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onResetBarometerQnh = {
                        viewModel.resetBarometerQnh()
                    },
                    onCopyCoordinates = {
                        val coords = gpsData.formattedCoordinates()
                        if (coords != null) {
                            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("GPS Coordinates", coords))
                            Toast.makeText(
                                this,
                                getString(R.string.toast_coordinates_copied),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                this,
                                getString(R.string.toast_coordinates_unavailable),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onRequestLocationPermission = {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    onRequestOverlayPermission = {
                        openOverlayPermissionSettings()
                    },
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onOpenLocationSettings = {
                        runCatching {
                            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        }
                    },
                    onToggleOverlay = {
                        if (isOverlayRunning) {
                            FloatingGpsService.stop(this)
                        } else {
                            handleStartOverlay(
                                requestLocation = {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            )
                        }
                    },
                    onOpenBlePicker = { requestBleAction(reconnectLast = false) },
                    onQuickReconnectBle = { requestBleAction(reconnectLast = true) },
                    onDisconnectBle = {
                        viewModel.disconnectBleDevice()
                    },
                    onSelectActivityMode = { mode ->
                        viewModel.selectActivityMode(mode)
                    },
                    onStartRecording = {
                        if (!GpsRepository.hasLocationPermission(this)) {
                            Toast.makeText(
                                this,
                                getString(R.string.toast_need_location_for_record),
                                Toast.LENGTH_SHORT
                            ).show()
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        } else if (recordingSnapshot.mode == ActivityMode.HIKING &&
                            !requestedStepPermission && HikingStepSensor.hasSensor(this) &&
                            !HikingStepSensor.hasPermission(this)
                        ) {
                            requestedStepPermission = true
                            stepPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                        } else {
                            viewModel.startTrackRecording()
                        }
                    },
                    onPauseRecording = { viewModel.pauseTrackRecording() },
                    onResumeRecording = { viewModel.resumeTrackRecording() },
                    onFinishRecording = {
                        viewModel.finishAndSaveRecording { savedId ->
                            if (savedId != null) {
                                activeTab = MainTab.TRACKS
                                Toast.makeText(
                                    this,
                                    getString(R.string.toast_track_saved),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    this,
                                    getString(R.string.toast_track_too_short),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    onSetStartGate = {
                        val updated = viewModel.setKartingStartGate()
                        Toast.makeText(
                            this,
                            getString(
                                if (updated) R.string.toast_gate_updated else R.string.toast_gate_waiting_gps
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onSelectSession = { sessionId ->
                        viewModel.loadSessionDetail(sessionId)
                    },
                    onCloseSessionDetail = {
                        viewModel.closeSessionDetail()
                    },
                    onEnterMergeMode = {
                        viewModel.loadMergeableEndpoints()
                    },
                    onRequestMergePreview = { sessionIds ->
                        viewModel.prepareMergePreview(sessionIds) { errorMessage ->
                            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onCopyJson = { detail, includeAiPrompt ->
                        viewModel.buildJsonClipboardPayload(detail, includeAiPrompt) { payload ->
                            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText(detail.session.name, payload))
                            Toast.makeText(
                                this,
                                getString(
                                    if (includeAiPrompt) {
                                        R.string.toast_json_copied_with_prompt
                                    } else {
                                        R.string.toast_json_copied_plain
                                    }
                                ),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onExportJson = { detail, includeAiPrompt ->
                        viewModel.exportDetailToJson(detail, includeAiPrompt) { uri ->
                            if (uri != null) {
                                shareJsonUri(uri, detail.session.name, includeAiPrompt)
                            } else {
                                Toast.makeText(
                                    this,
                                    getString(R.string.toast_json_export_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    onExportGpx = { detail ->
                        viewModel.exportDetailToGpx(detail) { uri ->
                            if (uri != null) {
                                shareGpxUri(uri, detail.session.name)
                            } else {
                                Toast.makeText(
                                    this,
                                    getString(R.string.toast_gpx_export_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    onShareSocialImage = { uri, sessionName ->
                        shareSocialImageUri(uri, sessionName)
                    },
                    onDeleteSession = { sessionId ->
                        viewModel.deleteSession(sessionId)
                    },
                    aiGatewayConfig = aiGatewayConfig,
                    aiFetchedModels = aiFetchedModels,
                    aiConnectionState = aiConnectionState,
                    aiAnalysisState = aiAnalysisState,
                    aiAnalysisHistory = aiAnalysisHistory,
                    showAiAnalysisHistory = showAiAnalysisHistory,
                    onTestAndFetchAiModels = { baseUrl, apiKey, modelId ->
                        viewModel.testAndFetchAiModels(baseUrl, apiKey, modelId)
                    },
                    onSaveAiGatewayConfig = { baseUrl, apiKey, modelId ->
                        val result = viewModel.saveAiGatewayConfig(baseUrl, apiKey, modelId)
                        if (result.isSuccess) {
                            Toast.makeText(
                                this,
                                getString(R.string.toast_ai_config_saved),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onClearAiGatewayConfig = {
                        viewModel.clearAiGatewayConfig()
                        Toast.makeText(
                            this,
                            getString(R.string.toast_ai_config_cleared),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onStartAiAnalysis = { detail ->
                        viewModel.generateAiCoachReport(detail)
                    },
                    onStopAiAnalysis = {
                        viewModel.stopAiCoachGeneration()
                    },
                    onCopyAiReport = { reportText ->
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("AI Coach Report", reportText))
                        Toast.makeText(
                            this,
                            getString(R.string.toast_ai_report_copied),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onOpenAiSettingsFromDetail = {
                        viewModel.closeSessionDetail()
                        showAiAnalysisHistory = false
                        activeTab = MainTab.SETTINGS
                        selectedSettingsSubPage = SettingsSubPage.AI_GATEWAY
                    },
                    onOpenAiHistory = {
                        showAiAnalysisHistory = true
                    },
                    onCloseAiHistory = {
                        showAiAnalysisHistory = false
                    },
                    onOpenSessionFromAiHistory = { sessionId ->
                        showAiAnalysisHistory = false
                        activeTab = MainTab.TRACKS
                        viewModel.loadSessionDetail(sessionId)
                    },
                    onDeleteAiHistoryRecord = { recordId ->
                        viewModel.deleteAiAnalysisRecord(recordId)
                        Toast.makeText(
                            this,
                            getString(R.string.toast_ai_history_deleted),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onClearAiHistory = {
                        viewModel.clearAllAiAnalysisHistory()
                        Toast.makeText(
                            this,
                            getString(R.string.toast_ai_history_cleared),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )

                if (showBleDialog) {
                    BleDevicePickerDialog(
                        hrState = heartRateState,
                        isScanning = isBleScanning,
                        localDevices = localBleDevices,
                        scannedDevices = scannedBleDevices,
                        lastSavedDevice = lastSavedBleDevice,
                        autoReconnectEnabled = autoReconnectBle,
                        onToggleAutoReconnect = { enabled ->
                            viewModel.setAutoReconnectBle(enabled)
                        },
                        onForgetLastDevice = {
                            viewModel.clearLastSavedBleDevice()
                        },
                        onStartScan = { viewModel.startBleScan() },
                        onStopScan = { viewModel.stopBleScan() },
                        onSelectDevice = viewModel::connectBleDevice,
                        onDismiss = {
                            viewModel.stopBleScan()
                            showBleDialog = false
                        }
                    )
                }

                if (showFocusDialog) {
                    FocusModeDialog(
                        isFocusLockActive = focusLockActiveState,
                        autoFocusOnLaunch = autoFocusOnLaunch,
                        hasDndPolicyAccess = hasDndPolicyAccess,
                        onToggleAutoFocusOnLaunch = { enabled ->
                            viewModel.setAutoFocusOnLaunch(enabled)
                        },
                        onOpenDndPolicySettings = {
                            openDndPolicySettings()
                        },
                        onToggleFocusLock = {
                            if (focusLockActiveState) {
                                exitFocusLockMode(showToast = true)
                            } else {
                                enterFocusLockMode(showToast = true)
                            }
                        },
                        onDismiss = { showFocusDialog = false }
                    )
                }

                mergePreview?.let { preview ->
                    MergeTracksConfirmDialog(
                        preview = preview,
                        isMerging = isMergingTracks,
                        onConfirm = { mergedName, keepOriginals ->
                            viewModel.confirmMergeTracks(
                                mergedName = mergedName,
                                keepOriginalSegments = keepOriginals
                            ) { mergedCount, newSessionId ->
                                if (newSessionId != null) {
                                    mergeCompletedSignal += 1
                                    Toast.makeText(
                                        this,
                                        getString(R.string.toast_merge_success, mergedCount),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        onDismiss = { viewModel.dismissMergePreview() }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.refreshPermissions()
        GpsRepository.startTracking(applicationContext, ACTIVITY_CLIENT_ID)
        viewModel.startMotionSensors()
        MotionSensorManager.refreshDisplayRotation(this)
        viewModel.tryAutoReconnectBle()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        MotionSensorManager.refreshDisplayRotation(this)
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopMotionSensors()
        GpsRepository.stopTracking(ACTIVITY_CLIENT_ID)
    }

    override fun onDestroy() {
        restoreDndFilterIfSaved()
        viewModel.stopMotionSensors()
        GpsRepository.stopTracking(ACTIVITY_CLIENT_ID)
        super.onDestroy()
    }

    private fun enterFocusLockMode(showToast: Boolean) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (nm != null && runCatching { nm.isNotificationPolicyAccessGranted }.getOrDefault(false)) {
            if (previousInterruptionFilter == null) {
                previousInterruptionFilter = nm.currentInterruptionFilter
            }
            runCatching {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
            }
        }

        // Engage Android Screen Pinning to prevent other apps/popups/home switches
        runCatching { startLockTask() }

        // Immersive fullscreen hides notification shade & navigation bar from accidental touches
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())

        focusLockActiveState = true
        if (showToast) {
            Toast.makeText(this, getString(R.string.toast_focus_entered), Toast.LENGTH_SHORT).show()
        }
    }

    private fun exitFocusLockMode(showToast: Boolean) {
        restoreDndFilterIfSaved()
        runCatching { stopLockTask() }

        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())

        focusLockActiveState = false
        if (showToast) {
            Toast.makeText(this, getString(R.string.toast_focus_exited), Toast.LENGTH_SHORT).show()
        }
    }

    private fun restoreDndFilterIfSaved() {
        val saved = previousInterruptionFilter ?: return
        previousInterruptionFilter = null
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (nm != null && runCatching { nm.isNotificationPolicyAccessGranted }.getOrDefault(false)) {
            runCatching { nm.setInterruptionFilter(saved) }
        }
    }

    private fun openDndPolicySettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
    }

    private fun handleStartOverlay(requestLocation: () -> Unit) {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                getString(R.string.toast_need_overlay_permission),
                Toast.LENGTH_SHORT
            ).show()
            openOverlayPermissionSettings()
            return
        }
        if (!GpsRepository.hasLocationPermission(this)) {
            Toast.makeText(
                this,
                getString(R.string.toast_need_location_for_overlay),
                Toast.LENGTH_SHORT
            ).show()
            requestLocation()
            return
        }
        FloatingGpsService.start(this)
    }

    private fun openOverlayPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        runCatching { startActivity(intent) }
            .onFailure {
                runCatching {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                }
            }
    }

    private fun shareJsonUri(uri: Uri, sessionName: String, isPromptText: Boolean = false) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = if (isPromptText) "text/plain" else "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, sessionName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_json_chooser)))
        }.onFailure {
            Toast.makeText(this, getString(R.string.toast_json_export_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareGpxUri(uri: Uri, sessionName: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, sessionName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_gpx_chooser)))
        }.onFailure {
            Toast.makeText(this, getString(R.string.toast_gpx_export_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareSocialImageUri(uri: Uri, sessionName: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, sessionName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_social_image_chooser)))
        }.onFailure {
            Toast.makeText(this, getString(R.string.toast_social_save_failed), Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val ACTIVITY_CLIENT_ID = "main_activity"
    }
}
