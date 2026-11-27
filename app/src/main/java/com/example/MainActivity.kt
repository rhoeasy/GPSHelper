package com.example

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ble.HeartRateRepository
import com.example.gps.GpsRepository
import com.example.overlay.FloatingGpsService
import com.example.ui.BleDevicePickerDialog
import com.example.ui.GpsDashboardViewModel
import com.example.ui.GpsHudAppShell
import com.example.ui.MainTab
import com.example.ui.theme.GpsHudTheme
import com.example.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {

    private val viewModel: GpsDashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val isDarkTheme = when (themeMode) {
                ThemeMode.AUTO -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

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

            GpsHudTheme(themeMode = themeMode) {
                val gpsData by viewModel.gpsData.collectAsStateWithLifecycle()
                val speedUnit by viewModel.speedUnit.collectAsStateWithLifecycle()
                val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle()
                val isOverlayRunning by viewModel.isOverlayRunning.collectAsStateWithLifecycle()
                val permissionStatus by viewModel.permissionStatus.collectAsStateWithLifecycle()

                val heartRateState by viewModel.heartRateState.collectAsStateWithLifecycle()
                val scannedBleDevices by viewModel.scannedBleDevices.collectAsStateWithLifecycle()
                val isBleScanning by viewModel.isBleScanning.collectAsStateWithLifecycle()
                val lastSavedBleDevice by viewModel.lastSavedBleDevice.collectAsStateWithLifecycle()

                val recordingSnapshot by viewModel.recordingSnapshot.collectAsStateWithLifecycle()
                val savedSessions by viewModel.savedSessions.collectAsStateWithLifecycle()
                val selectedSessionDetail by viewModel.selectedSessionDetail.collectAsStateWithLifecycle()

                var activeTab by rememberSaveable { mutableStateOf(MainTab.DASHBOARD) }
                var showBleDialog by rememberSaveable { mutableStateOf(false) }

                // Apply keep-screen-on window flag reactively
                DisposableEffect(keepScreenOn) {
                    if (keepScreenOn) {
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

                val blePermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    if (HeartRateRepository.hasBlePermissions(this)) {
                        if (!HeartRateRepository.isBluetoothEnabled(this)) {
                            Toast.makeText(
                                this,
                                getString(R.string.toast_bluetooth_disabled),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            showBleDialog = true
                            viewModel.startBleScan()
                        }
                    } else {
                        Toast.makeText(
                            this,
                            getString(R.string.toast_need_bluetooth_permission),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    viewModel.refreshPermissions()
                    GpsRepository.startTracking(applicationContext, ACTIVITY_CLIENT_ID)
                }

                GpsHudAppShell(
                    activeTab = activeTab,
                    onSelectTab = { activeTab = it },
                    gpsData = gpsData,
                    speedUnit = speedUnit,
                    keepScreenOn = keepScreenOn,
                    themeMode = themeMode,
                    isOverlayRunning = isOverlayRunning,
                    permissionStatus = permissionStatus,
                    heartRateState = heartRateState,
                    recordingSnapshot = recordingSnapshot,
                    savedSessions = savedSessions,
                    selectedSessionDetail = selectedSessionDetail,
                    onSelectSpeedUnit = { unit -> viewModel.setSpeedUnit(unit) },
                    onToggleKeepScreenOn = { enabled -> viewModel.setKeepScreenOn(enabled) },
                    onSelectThemeMode = { mode -> viewModel.setThemeMode(mode) },
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
                    onOpenBlePicker = {
                        if (!HeartRateRepository.hasBlePermissions(this)) {
                            val blePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                arrayOf(
                                    Manifest.permission.BLUETOOTH_SCAN,
                                    Manifest.permission.BLUETOOTH_CONNECT
                                )
                            } else {
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                            blePermissionLauncher.launch(blePermissions)
                        } else if (!HeartRateRepository.isBluetoothEnabled(this)) {
                            Toast.makeText(
                                this,
                                getString(R.string.toast_bluetooth_disabled),
                                Toast.LENGTH_SHORT
                            ).show()
                            runCatching { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                        } else {
                            showBleDialog = true
                            viewModel.startBleScan()
                        }
                    },
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
                    onDeleteSession = { sessionId ->
                        viewModel.deleteSession(sessionId)
                    }
                )

                if (showBleDialog) {
                    BleDevicePickerDialog(
                        hrState = heartRateState,
                        isScanning = isBleScanning,
                        devices = scannedBleDevices,
                        lastSavedDevice = lastSavedBleDevice,
                        onStartScan = { viewModel.startBleScan() },
                        onStopScan = { viewModel.stopBleScan() },
                        onSelectDevice = { name, addr ->
                            viewModel.connectBleDevice(name, addr)
                            showBleDialog = false
                        },
                        onDismiss = {
                            viewModel.stopBleScan()
                            showBleDialog = false
                        }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.refreshPermissions()
        GpsRepository.startTracking(applicationContext, ACTIVITY_CLIENT_ID)
    }

    override fun onStop() {
        super.onStop()
        GpsRepository.stopTracking(ACTIVITY_CLIENT_ID)
    }

    override fun onDestroy() {
        GpsRepository.stopTracking(ACTIVITY_CLIENT_ID)
        super.onDestroy()
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

    companion object {
        private const val ACTIVITY_CLIENT_ID = "main_activity"
    }
}
