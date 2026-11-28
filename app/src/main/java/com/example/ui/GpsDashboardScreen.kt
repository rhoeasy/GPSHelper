package com.example.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ai.AiAnalysisState
import com.example.ai.AiConnectionState
import com.example.ai.AiGatewayConfig
import com.example.alert.SafetyAlertState
import com.example.ble.HeartRateState
import com.example.ble.SavedBleDevice
import com.example.gps.AccuracyTelemetry
import com.example.gps.DashboardSpeedStyle
import com.example.gps.AltitudeTelemetry
import com.example.gps.GnssSatelliteInfo
import com.example.gps.GpsData
import com.example.gps.OverlayDisplayConfig
import com.example.gps.SafetyAlertConfig
import com.example.gps.SpeedUnit
import com.example.sensor.EnvironmentTelemetry
import com.example.sensor.GForceData
import com.example.track.ActivityMode
import com.example.track.AiAnalysisRecordEntity
import com.example.track.KartingTimingCourse
import com.example.track.RecordingSnapshot
import com.example.track.RecordingStatus
import com.example.track.TrackEndpoints
import com.example.track.TrackFormatters
import com.example.track.TrackSessionDetail
import com.example.track.TrackSessionEntity
import com.example.ui.theme.AppThemePalette
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ThemeMode

enum class MainTab {
    DASHBOARD,
    TRACKS,
    SETTINGS
}

enum class TelemetryDetailPage {
    ALTITUDE,
    ACCURACY,
    COMPASS,
    SATELLITES,
    COORDINATES
}

@Composable
fun GpsHudAppShell(
    activeTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    gpsData: GpsData,
    gForceData: GForceData,
    speedUnit: SpeedUnit,
    keepScreenOn: Boolean,
    themeMode: ThemeMode,
    themePalette: AppThemePalette,
    autoFocusOnLaunch: Boolean,
    overlayConfig: OverlayDisplayConfig,
    safetyAlertConfig: SafetyAlertConfig,
    alertState: SafetyAlertState,
    isFocusLockActive: Boolean,
    isOverlayRunning: Boolean,
    permissionStatus: PermissionStatus,
    heartRateState: HeartRateState,
    lastSavedBleDevice: SavedBleDevice?,
    recordingSnapshot: RecordingSnapshot,
    showTrackRecorderSheet: Boolean,
    onShowTrackRecorderSheetChange: (Boolean) -> Unit,
    savedSessions: List<TrackSessionEntity>,
    selectedSessionDetail: TrackSessionDetail?,
    selectedTelemetryDetail: TelemetryDetailPage?,
    selectedSettingsSubPage: SettingsSubPage?,
    onSelectSettingsSubPage: (SettingsSubPage?) -> Unit,
    satellites: List<GnssSatelliteInfo>,
    altitudeTelemetry: AltitudeTelemetry,
    accuracyTelemetry: AccuracyTelemetry,
    environmentTelemetry: EnvironmentTelemetry,
    sessionEndpoints: Map<Long, TrackEndpoints>,
    mergeCompletedSignal: Int,
    onSelectSpeedUnit: (SpeedUnit) -> Unit,
    dashboardSpeedStyle: DashboardSpeedStyle,
    onSelectDashboardSpeedStyle: (DashboardSpeedStyle) -> Unit,
    onToggleKeepScreenOn: (Boolean) -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onSelectThemePalette: (AppThemePalette) -> Unit,
    onToggleAutoFocusOnLaunch: (Boolean) -> Unit,
    onUpdateOverlayConfig: ((OverlayDisplayConfig) -> OverlayDisplayConfig) -> Unit,
    onResetOverlayConfig: () -> Unit,
    onUpdateSafetyAlertConfig: ((SafetyAlertConfig) -> SafetyAlertConfig) -> Unit,
    onOpenFocusDialog: () -> Unit,
    onExitFocusLock: () -> Unit,
    onCalibrateGForceZero: () -> Unit,
    onZeroMotorcycleLean: () -> Unit,
    onResetGForcePeak: () -> Unit,
    onOpenTelemetryDetail: (TelemetryDetailPage) -> Unit,
    onCloseTelemetryDetail: () -> Unit,
    onResetAltitudeSession: () -> Unit,
    onResetAccuracySession: () -> Unit,
    onCalibrateBarometer: () -> Unit,
    onResetBarometerQnh: () -> Unit,
    onCopyCoordinates: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onToggleOverlay: () -> Unit,
    onOpenBlePicker: () -> Unit,
    onQuickReconnectBle: () -> Unit,
    onDisconnectBle: () -> Unit,
    onSelectActivityMode: (ActivityMode) -> Unit,
    onStartRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onFinishRecording: () -> Unit,
    onSetStartGate: () -> Unit,
    onRecalculateKarting: (Long, KartingTimingCourse, (Boolean) -> Unit) -> Unit,
    onSelectSession: (Long) -> Unit,
    onCloseSessionDetail: () -> Unit,
    onEnterMergeMode: () -> Unit,
    onRequestMergePreview: (List<Long>) -> Unit,
    onCopyJson: (TrackSessionDetail, Boolean) -> Unit,
    onExportJson: (TrackSessionDetail, Boolean) -> Unit,
    onExportGpx: (TrackSessionDetail) -> Unit,
    onShareSocialImage: (Uri, String) -> Unit,
    onDeleteSession: (Long) -> Unit,
    aiGatewayConfig: AiGatewayConfig,
    aiFetchedModels: List<String>,
    aiConnectionState: AiConnectionState,
    aiAnalysisState: AiAnalysisState,
    aiAnalysisHistory: List<AiAnalysisRecordEntity>,
    showAiAnalysisHistory: Boolean,
    onTestAndFetchAiModels: (baseUrl: String, apiKey: String, currentModelId: String) -> Unit,
    onCancelAiModelTest: () -> Unit,
    onSaveAiGatewayConfig: (baseUrl: String, apiKey: String, modelId: String) -> Unit,
    onClearAiGatewayConfig: () -> Unit,
    onStartAiAnalysis: (TrackSessionDetail) -> Unit,
    onStopAiAnalysis: () -> Unit,
    onCopyAiReport: (String) -> Unit,
    onOpenAiSettingsFromDetail: () -> Unit,
    onOpenAiHistory: () -> Unit,
    onCloseAiHistory: () -> Unit,
    onOpenSessionFromAiHistory: (Long) -> Unit,
    onDeleteAiHistoryRecord: (Long) -> Unit,
    onClearAiHistory: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var gForceExplanationExpanded by rememberSaveable { mutableStateOf(false) }

    if (selectedSessionDetail != null) {
        TrackDetailScreen(
            detail = selectedSessionDetail,
            speedUnit = speedUnit,
            isFocusLockActive = isFocusLockActive,
            aiGatewayConfig = aiGatewayConfig,
            aiAnalysisState = aiAnalysisState,
            onExitFocusLock = onExitFocusLock,
            onBack = onCloseSessionDetail,
            onCopyJson = { includePrompt -> onCopyJson(selectedSessionDetail, includePrompt) },
            onExportJson = { includePrompt -> onExportJson(selectedSessionDetail, includePrompt) },
            onExportGpx = { onExportGpx(selectedSessionDetail) },
            onShareSocialImage = { uri -> onShareSocialImage(uri, selectedSessionDetail.session.name) },
            onDeleteSession = { onDeleteSession(selectedSessionDetail.session.id) },
            onRecalculateKarting = { course, completed ->
                onRecalculateKarting(selectedSessionDetail.session.id, course, completed)
            },
            onStartAiAnalysis = { onStartAiAnalysis(selectedSessionDetail) },
            onStopAiAnalysis = onStopAiAnalysis,
            onCopyAiReport = onCopyAiReport,
            onOpenAiSettings = onOpenAiSettingsFromDetail,
            modifier = Modifier
                .fillMaxSize()
                .background(cs.background)
                .statusBarsPadding()
                .navigationBarsPadding()
        )
        return
    }

    if (showAiAnalysisHistory) {
        val existingSessionIds = remember(savedSessions) {
            savedSessions.mapTo(HashSet(savedSessions.size)) { it.id }
        }
        AiAnalysisHistoryScreen(
            records = aiAnalysisHistory,
            existingSessionIds = existingSessionIds,
            isFocusLockActive = isFocusLockActive,
            onExitFocusLock = onExitFocusLock,
            onBack = onCloseAiHistory,
            onCopyReport = onCopyAiReport,
            onOpenLinkedSession = onOpenSessionFromAiHistory,
            onDeleteRecord = onDeleteAiHistoryRecord,
            onClearAllRecords = onClearAiHistory,
            modifier = Modifier
                .fillMaxSize()
                .background(cs.background)
                .statusBarsPadding()
                .navigationBarsPadding()
        )
        return
    }

    if (selectedTelemetryDetail != null) {
        when (selectedTelemetryDetail) {
            TelemetryDetailPage.ALTITUDE -> {
                AltitudeDetailScreen(
                    gpsData = gpsData,
                    altitudeTelemetry = altitudeTelemetry,
                    envTelemetry = environmentTelemetry,
                    onBack = onCloseTelemetryDetail,
                    onResetAltitudeSession = onResetAltitudeSession,
                    onCalibrateBarometer = onCalibrateBarometer,
                    onResetBarometerQnh = onResetBarometerQnh,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(cs.background)
                )
            }

            TelemetryDetailPage.ACCURACY -> {
                AccuracyDetailScreen(
                    gpsData = gpsData,
                    accuracyTelemetry = accuracyTelemetry,
                    satellites = satellites,
                    speedUnit = speedUnit,
                    onBack = onCloseTelemetryDetail,
                    onResetAccuracySession = onResetAccuracySession,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(cs.background)
                )
            }

            TelemetryDetailPage.COMPASS -> {
                CompassDetailScreen(
                    gpsData = gpsData,
                    envTelemetry = environmentTelemetry,
                    onBack = onCloseTelemetryDetail,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(cs.background)
                )
            }

            TelemetryDetailPage.SATELLITES -> {
                SatelliteDetailScreen(
                    gpsData = gpsData,
                    satellites = satellites,
                    onBack = onCloseTelemetryDetail,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(cs.background)
                )
            }

            TelemetryDetailPage.COORDINATES -> {
                val shareTitle = stringResource(R.string.coordinate_diagram_title)
                CoordinateDetailScreen(
                    gpsData = gpsData,
                    gateway = aiGatewayConfig,
                    onBack = onCloseTelemetryDetail,
                    onCopy = onCopyCoordinates,
                    onRequestLocation = onRequestLocationPermission,
                    onOpenLocationSettings = onOpenLocationSettings,
                    onConfigureGateway = {
                        onCloseTelemetryDetail()
                        onSelectTab(MainTab.SETTINGS)
                        onSelectSettingsSubPage(SettingsSubPage.AI_GATEWAY)
                    },
                    onShareImage = { uri -> onShareSocialImage(uri, shareTitle) }
                )
            }
        }
        return
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
    ) {
        val isLandscapeWide = maxWidth > maxHeight && maxWidth >= 540.dp
        val heroRadarSize = (maxHeight * 0.43f).coerceIn(132.dp, 172.dp)
        val heroMinHeight = (maxHeight - 88.dp).coerceIn(216.dp, 292.dp)

        Row(modifier = Modifier.fillMaxSize()) {
            if (isLandscapeWide) {
                NavigationRail(
                    modifier = Modifier
                        .fillMaxHeight()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .displayCutoutPadding(),
                    containerColor = cs.surfaceContainerLow
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    NavigationRailItem(
                        selected = activeTab == MainTab.DASHBOARD,
                        onClick = { onSelectTab(MainTab.DASHBOARD) },
                        modifier = Modifier.testTag("nav_tab_dashboard"),
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Speed,
                                contentDescription = stringResource(R.string.tab_dashboard)
                            )
                        },
                        label = { Text(stringResource(R.string.tab_dashboard)) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = cs.onPrimaryContainer,
                            selectedTextColor = cs.onSurface,
                            indicatorColor = cs.primaryContainer,
                            unselectedIconColor = cs.onSurfaceVariant,
                            unselectedTextColor = cs.onSurfaceVariant
                        )
                    )
                    NavigationRailItem(
                        selected = activeTab == MainTab.TRACKS,
                        onClick = { onSelectTab(MainTab.TRACKS) },
                        modifier = Modifier.testTag("nav_tab_tracks"),
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Route,
                                contentDescription = stringResource(R.string.tab_tracks)
                            )
                        },
                        label = { Text(stringResource(R.string.tab_tracks)) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = cs.onPrimaryContainer,
                            selectedTextColor = cs.onSurface,
                            indicatorColor = cs.primaryContainer,
                            unselectedIconColor = cs.onSurfaceVariant,
                            unselectedTextColor = cs.onSurfaceVariant
                        )
                    )
                    NavigationRailItem(
                        selected = activeTab == MainTab.SETTINGS,
                        onClick = { onSelectTab(MainTab.SETTINGS) },
                        modifier = Modifier.testTag("nav_tab_settings"),
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = stringResource(R.string.tab_settings)
                            )
                        },
                        label = { Text(stringResource(R.string.tab_settings)) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = cs.onPrimaryContainer,
                            selectedTextColor = cs.onSurface,
                            indicatorColor = cs.primaryContainer,
                            unselectedIconColor = cs.onSurfaceVariant,
                            unselectedTextColor = cs.onSurfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }
            }

            Scaffold(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                containerColor = cs.background,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                floatingActionButton = {
                    if (activeTab == MainTab.DASHBOARD) {
                        ExtendedFloatingActionButton(
                            onClick = onToggleOverlay,
                            modifier = Modifier
                                .then(if (isLandscapeWide) Modifier.navigationBarsPadding() else Modifier)
                                .testTag("toggle_overlay_button"),
                            shape = RoundedCornerShape(18.dp),
                            containerColor = if (isOverlayRunning) cs.errorContainer else cs.primaryContainer,
                            contentColor = if (isOverlayRunning) cs.onErrorContainer else cs.onPrimaryContainer,
                            icon = {
                                Icon(
                                    imageVector = if (isOverlayRunning) Icons.Rounded.Close else Icons.Rounded.Layers,
                                    contentDescription = null
                                )
                            },
                            text = {
                                Text(
                                    text = stringResource(
                                        if (isOverlayRunning) R.string.action_stop_overlay else R.string.action_start_overlay
                                    ),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        )
                    }
                },
                bottomBar = {
                    if (!isLandscapeWide) {
                        NavigationBar(
                            containerColor = cs.surfaceContainerLow,
                            tonalElevation = 0.dp,
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            NavigationBarItem(
                                selected = activeTab == MainTab.DASHBOARD,
                                onClick = { onSelectTab(MainTab.DASHBOARD) },
                                modifier = Modifier.testTag("nav_tab_dashboard"),
                                icon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Speed,
                                        contentDescription = stringResource(R.string.tab_dashboard)
                                    )
                                },
                                label = { Text(stringResource(R.string.tab_dashboard)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = cs.onPrimaryContainer,
                                    selectedTextColor = cs.onSurface,
                                    indicatorColor = cs.primaryContainer,
                                    unselectedIconColor = cs.onSurfaceVariant,
                                    unselectedTextColor = cs.onSurfaceVariant
                                )
                            )
                            NavigationBarItem(
                                selected = activeTab == MainTab.TRACKS,
                                onClick = { onSelectTab(MainTab.TRACKS) },
                                modifier = Modifier.testTag("nav_tab_tracks"),
                                icon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Route,
                                        contentDescription = stringResource(R.string.tab_tracks)
                                    )
                                },
                                label = { Text(stringResource(R.string.tab_tracks)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = cs.onPrimaryContainer,
                                    selectedTextColor = cs.onSurface,
                                    indicatorColor = cs.primaryContainer,
                                    unselectedIconColor = cs.onSurfaceVariant,
                                    unselectedTextColor = cs.onSurfaceVariant
                                )
                            )
                            NavigationBarItem(
                                selected = activeTab == MainTab.SETTINGS,
                                onClick = { onSelectTab(MainTab.SETTINGS) },
                                modifier = Modifier.testTag("nav_tab_settings"),
                                icon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Tune,
                                        contentDescription = stringResource(R.string.tab_settings)
                                    )
                                },
                                label = { Text(stringResource(R.string.tab_settings)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = cs.onPrimaryContainer,
                                    selectedTextColor = cs.onSurface,
                                    indicatorColor = cs.primaryContainer,
                                    unselectedIconColor = cs.onSurfaceVariant,
                                    unselectedTextColor = cs.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .statusBarsPadding()
                        .then(if (isLandscapeWide) Modifier.navigationBarsPadding() else Modifier)
                        .mainTabSwipe(activeTab, selectedSettingsSubPage == null && !showTrackRecorderSheet, onSelectTab),
                    contentAlignment = Alignment.TopCenter
                ) {
                    when (activeTab) {
                        MainTab.DASHBOARD -> {
                            if (isLandscapeWide) {
                                // Maximized Landscape Cockpit: Compact Header + Full-Viewport Speed & G-Force Hero Row
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .displayCutoutPadding()
                                        .verticalScroll(rememberScrollState())
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    AnimatedVisibility(
                                        visible = isFocusLockActive,
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically()
                                    ) {
                                        FocusActiveBanner(onExitFocusLock = onExitFocusLock)
                                    }

                                    GoogleTopHeader(
                                        gpsData = gpsData,
                                        themeMode = themeMode,
                                        isFocusLockActive = isFocusLockActive,
                                        compactLandscape = true,
                                        onSelectThemeMode = onSelectThemeMode,
                                        onOpenFocusDialog = onOpenFocusDialog
                                    )

                                    AnimatedVisibility(
                                        visible = permissionStatus.hasMissingPermissions,
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically()
                                    ) {
                                        PermissionBannerCard(
                                            status = permissionStatus,
                                            onRequestLocation = onRequestLocationPermission,
                                            onRequestOverlay = onRequestOverlayPermission,
                                            onRequestNotification = onRequestNotificationPermission
                                        )
                                    }

                                    AnimatedVisibility(
                                        visible = recordingSnapshot.status != RecordingStatus.IDLE,
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically()
                                    ) {
                                        DashboardActiveRecordingPill(
                                            snapshot = recordingSnapshot,
                                            onClick = {
                                                onSelectTab(MainTab.TRACKS)
                                                onShowTrackRecorderSheetChange(true)
                                            }
                                        )
                                    }

                                    MotorcycleLeanRow(recordingSnapshot, onZeroMotorcycleLean)

                                    // Hero Instrument Cluster: Maximized Speedometer (left) + Enlarged G-Force Radar (right)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("landscape_hero_cockpit_row"),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        PrimarySpeedCard(
                                            gpsData = gpsData,
                                            style = dashboardSpeedStyle,
                                            speedUnit = speedUnit,
                                            onSelectUnit = onSelectSpeedUnit,
                                            onOpenLocationSettings = onOpenLocationSettings,
                                            isAlertActive = alertState.isOverspeed,
                                            isLandscape = true,
                                            modifier = Modifier
                                                .weight(1.02f)
                                                .heightIn(min = heroMinHeight)
                                        )

                                        GForceTelemetryCard(
                                            gForceData = gForceData,
                                            explanationExpanded = gForceExplanationExpanded,
                                            onExplanationExpandedChange = { gForceExplanationExpanded = it },
                                            showPeakOnDial = overlayConfig.showGForcePeakOnDial,
                                            peakHoldSeconds = overlayConfig.gForcePeakHoldSeconds,
                                            onCalibrateZero = onCalibrateGForceZero,
                                            onResetPeak = onResetGForcePeak,
                                            isLandscape = true,
                                            radarSize = heroRadarSize,
                                            modifier = Modifier
                                                .weight(1.18f)
                                                .heightIn(min = heroMinHeight)
                                        )
                                    }

                                    // Secondary Telemetry Grid below the Hero Instrument Cluster (balanced 2-column)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            TelemetryGrid(
                                                gpsData = gpsData,
                                                onOpenAltitudeDetail = { onOpenTelemetryDetail(TelemetryDetailPage.ALTITUDE) },
                                                onOpenAccuracyDetail = { onOpenTelemetryDetail(TelemetryDetailPage.ACCURACY) },
                                                onOpenCompassDetail = { onOpenTelemetryDetail(TelemetryDetailPage.COMPASS) },
                                                onOpenSatelliteDetail = { onOpenTelemetryDetail(TelemetryDetailPage.SATELLITES) }
                                            )
                                        }

                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            HeartRateStripCard(
                                                hrState = heartRateState,
                                                lastSavedDevice = lastSavedBleDevice,
                                                onOpenDevicePicker = onOpenBlePicker,
                                                onQuickReconnect = onQuickReconnectBle,
                                                onDisconnect = onDisconnectBle,
                                                isAlertActive = alertState.isHighHeartRate
                                            )

                                            CoordinatesCard(
                                                gpsData = gpsData,
                                                onOpenDetails = { onOpenTelemetryDetail(TelemetryDetailPage.COORDINATES) },
                                                onCopyCoordinates = onCopyCoordinates
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(68.dp))
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .widthIn(max = 600.dp)
                                        .verticalScroll(rememberScrollState())
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    AnimatedVisibility(
                                        visible = isFocusLockActive,
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically()
                                    ) {
                                        FocusActiveBanner(onExitFocusLock = onExitFocusLock)
                                    }

                                    GoogleTopHeader(
                                        gpsData = gpsData,
                                        themeMode = themeMode,
                                        isFocusLockActive = isFocusLockActive,
                                        onSelectThemeMode = onSelectThemeMode,
                                        onOpenFocusDialog = onOpenFocusDialog
                                    )

                                    AnimatedVisibility(
                                        visible = permissionStatus.hasMissingPermissions,
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically()
                                    ) {
                                        PermissionBannerCard(
                                            status = permissionStatus,
                                            onRequestLocation = onRequestLocationPermission,
                                            onRequestOverlay = onRequestOverlayPermission,
                                            onRequestNotification = onRequestNotificationPermission
                                        )
                                    }

                                    AnimatedVisibility(
                                        visible = recordingSnapshot.status != RecordingStatus.IDLE,
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically()
                                    ) {
                                        DashboardActiveRecordingPill(
                                            snapshot = recordingSnapshot,
                                            onClick = {
                                                onSelectTab(MainTab.TRACKS)
                                                onShowTrackRecorderSheetChange(true)
                                            }
                                        )
                                    }

                                    MotorcycleLeanRow(recordingSnapshot, onZeroMotorcycleLean)

                                    PrimarySpeedCard(
                                        gpsData = gpsData,
                                        style = dashboardSpeedStyle,
                                        speedUnit = speedUnit,
                                        onSelectUnit = onSelectSpeedUnit,
                                        onOpenLocationSettings = onOpenLocationSettings,
                                        isAlertActive = alertState.isOverspeed
                                    )

                                    TelemetryGrid(
                                        gpsData = gpsData,
                                        onOpenAltitudeDetail = { onOpenTelemetryDetail(TelemetryDetailPage.ALTITUDE) },
                                        onOpenAccuracyDetail = { onOpenTelemetryDetail(TelemetryDetailPage.ACCURACY) },
                                        onOpenCompassDetail = { onOpenTelemetryDetail(TelemetryDetailPage.COMPASS) },
                                        onOpenSatelliteDetail = { onOpenTelemetryDetail(TelemetryDetailPage.SATELLITES) }
                                    )

                                    GForceTelemetryCard(
                                        gForceData = gForceData,
                                        explanationExpanded = gForceExplanationExpanded,
                                        onExplanationExpandedChange = { gForceExplanationExpanded = it },
                                        showPeakOnDial = overlayConfig.showGForcePeakOnDial,
                                        peakHoldSeconds = overlayConfig.gForcePeakHoldSeconds,
                                        onCalibrateZero = onCalibrateGForceZero,
                                        onResetPeak = onResetGForcePeak
                                    )

                                    CoordinatesCard(
                                        gpsData = gpsData,
                                        onOpenDetails = { onOpenTelemetryDetail(TelemetryDetailPage.COORDINATES) },
                                        onCopyCoordinates = onCopyCoordinates
                                    )

                                    HeartRateStripCard(
                                        hrState = heartRateState,
                                        lastSavedDevice = lastSavedBleDevice,
                                        onOpenDevicePicker = onOpenBlePicker,
                                        onQuickReconnect = onQuickReconnectBle,
                                        onDisconnect = onDisconnectBle,
                                        isAlertActive = alertState.isHighHeartRate
                                    )

                                    // Generous clearance above Extended FAB
                                    Spacer(modifier = Modifier.height(78.dp))
                                }
                            }
                        }

                        MainTab.TRACKS -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .widthIn(max = if (isLandscapeWide) 900.dp else 600.dp)
                            ) {
                                if (isFocusLockActive) {
                                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                        FocusActiveBanner(onExitFocusLock = onExitFocusLock)
                                    }
                                }
                                TrackHistoryScreen(
                                    sessions = savedSessions,
                                    speedUnit = speedUnit,
                                    aiHistoryCount = aiAnalysisHistory.size,
                                    onSelectSession = onSelectSession,
                                    onDeleteSession = onDeleteSession,
                                    onOpenAiHistory = onOpenAiHistory,
                                    recordingSnapshot = recordingSnapshot,
                                    heartRateState = heartRateState,
                                    showRecorderSheet = showTrackRecorderSheet,
                                    onShowRecorderSheetChange = onShowTrackRecorderSheetChange,
                                    onOpenBlePicker = onOpenBlePicker,
                                    onSelectActivityMode = onSelectActivityMode,
                                    onStartRecording = onStartRecording,
                                    onPauseRecording = onPauseRecording,
                                    onResumeRecording = onResumeRecording,
                                    onFinishRecording = onFinishRecording,
                                    onSetStartGate = onSetStartGate,
                                    onZeroMotorcycleLean = onZeroMotorcycleLean,
                                    sessionEndpoints = sessionEndpoints,
                                    mergeCompletedSignal = mergeCompletedSignal,
                                    onEnterMergeMode = onEnterMergeMode,
                                    onRequestMergePreview = onRequestMergePreview,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        MainTab.SETTINGS -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .widthIn(max = if (isLandscapeWide) 840.dp else 600.dp)
                            ) {
                                if (isFocusLockActive) {
                                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                        FocusActiveBanner(onExitFocusLock = onExitFocusLock)
                                    }
                                }
                                SettingsScreen(
                                    overlayConfig = overlayConfig,
                                    safetyAlertConfig = safetyAlertConfig,
                                    alertState = alertState,
                                    gpsData = gpsData,
                                    gForceData = gForceData,
                                    heartRateState = heartRateState,
                                    speedUnit = speedUnit,
                                    themeMode = themeMode,
                                    themePalette = themePalette,
                                    keepScreenOn = keepScreenOn,
                                    autoFocusOnLaunch = autoFocusOnLaunch,
                                    aiGatewayConfig = aiGatewayConfig,
                                    aiFetchedModels = aiFetchedModels,
                                    aiConnectionState = aiConnectionState,
                                    aiHistoryCount = aiAnalysisHistory.size,
                                    selectedSubPage = selectedSettingsSubPage,
                                    onSelectSubPage = onSelectSettingsSubPage,
                                    onUpdateOverlayConfig = onUpdateOverlayConfig,
                                    onResetOverlayConfig = onResetOverlayConfig,
                                    onUpdateSafetyAlertConfig = onUpdateSafetyAlertConfig,
                                    onSelectSpeedUnit = onSelectSpeedUnit,
                                    dashboardSpeedStyle = dashboardSpeedStyle,
                                    onSelectDashboardSpeedStyle = onSelectDashboardSpeedStyle,
                                    onSelectThemeMode = onSelectThemeMode,
                                    onSelectThemePalette = onSelectThemePalette,
                                    onToggleKeepScreenOn = onToggleKeepScreenOn,
                                    onToggleAutoFocusOnLaunch = onToggleAutoFocusOnLaunch,
                                    onTestAndFetchAiModels = onTestAndFetchAiModels,
                                    onCancelAiModelTest = onCancelAiModelTest,
                                    onSaveAiGatewayConfig = onSaveAiGatewayConfig,
                                    onClearAiGatewayConfig = onClearAiGatewayConfig,
                                    onOpenAiHistory = onOpenAiHistory,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardActiveRecordingPill(
    snapshot: RecordingSnapshot,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val isPaused = snapshot.status == RecordingStatus.PAUSED
    val statusLabel = if (snapshot.status == RecordingStatus.SAVING) {
        R.string.record_status_saving
    } else {
        R.string.dashboard_recording_active_pill
    }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_active_recording_pill"),
        shape = RoundedCornerShape(16.dp),
        color = if (isPaused) cs.secondaryContainer else cs.errorContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.FiberManualRecord,
                    contentDescription = null,
                    tint = if (isPaused) cs.onSecondaryContainer else cs.error,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = "${snapshot.mode.labelZh} · ${stringResource(statusLabel)}",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isPaused) cs.onSecondaryContainer else cs.onErrorContainer
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = TrackFormatters.formatDuration(snapshot.elapsedMs),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = if (isPaused) cs.onSecondaryContainer else cs.onErrorContainer
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = if (isPaused) cs.onSecondaryContainer else cs.onErrorContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun GoogleTopHeader(
    gpsData: GpsData,
    themeMode: ThemeMode,
    isFocusLockActive: Boolean,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onOpenFocusDialog: () -> Unit,
    compactLandscape: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    var themeMenuExpanded by remember { mutableStateOf(false) }

    val (themeIcon, themeLabelRes) = when (themeMode) {
        ThemeMode.AUTO -> Icons.Rounded.BrightnessAuto to R.string.theme_mode_auto
        ThemeMode.LIGHT -> Icons.Rounded.LightMode to R.string.theme_mode_light
        ThemeMode.DARK -> Icons.Rounded.DarkMode to R.string.theme_mode_dark
        ThemeMode.AMBIENT -> Icons.Rounded.BrightnessAuto to R.string.theme_mode_ambient
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (compactLandscape) 2.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (compactLandscape) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    ),
                    color = cs.onSurface,
                    maxLines = 1
                )
                GpsStatusBadge(fixStatus = gpsData.fixStatus)
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 21.sp
                    ),
                    color = cs.onSurface,
                    maxLines = 1
                )
                GpsStatusBadge(fixStatus = gpsData.fixStatus)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Focus / Anti-Interruption lock pill
            Surface(
                onClick = onOpenFocusDialog,
                modifier = Modifier.testTag("focus_mode_button"),
                shape = RoundedCornerShape(50),
                color = if (isFocusLockActive) cs.errorContainer else cs.surfaceContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isFocusLockActive) Icons.Rounded.Lock else Icons.Rounded.Shield,
                        contentDescription = stringResource(R.string.focus_mode_title),
                        tint = if (isFocusLockActive) cs.onErrorContainer else cs.onSurface,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = stringResource(R.string.focus_pill_label),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.5.sp
                        ),
                        color = if (isFocusLockActive) cs.onErrorContainer else cs.onSurface
                    )
                }
            }

            // Day / Night / Auto theme selector pill with Material 3 dropdown
            Box {
                Surface(
                    onClick = { themeMenuExpanded = true },
                    modifier = Modifier.testTag("theme_mode_button"),
                    shape = RoundedCornerShape(50),
                    color = cs.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = themeIcon,
                            contentDescription = stringResource(R.string.theme_mode_label),
                            tint = cs.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(themeLabelRes),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.5.sp
                            ),
                            color = cs.onSurface
                        )
                    }
                }

                DropdownMenu(
                    expanded = themeMenuExpanded,
                    onDismissRequest = { themeMenuExpanded = false },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = cs.surfaceContainerLow
                ) {
                    ThemeOptionItem(
                        icon = Icons.Rounded.BrightnessAuto,
                        label = stringResource(R.string.theme_mode_auto_full),
                        selected = themeMode == ThemeMode.AUTO,
                        tag = "theme_option_auto",
                        onClick = {
                            onSelectThemeMode(ThemeMode.AUTO)
                            themeMenuExpanded = false
                        }
                    )
                    ThemeOptionItem(
                        icon = Icons.Rounded.LightMode,
                        label = stringResource(R.string.theme_mode_light_full),
                        selected = themeMode == ThemeMode.LIGHT,
                        tag = "theme_option_light",
                        onClick = {
                            onSelectThemeMode(ThemeMode.LIGHT)
                            themeMenuExpanded = false
                        }
                    )
                    ThemeOptionItem(
                        icon = Icons.Rounded.DarkMode,
                        label = stringResource(R.string.theme_mode_dark_full),
                        selected = themeMode == ThemeMode.DARK,
                        tag = "theme_option_dark",
                        onClick = {
                            onSelectThemeMode(ThemeMode.DARK)
                            themeMenuExpanded = false
                        }
                    )
                    ThemeOptionItem(
                        icon = Icons.Rounded.BrightnessAuto,
                        label = stringResource(R.string.theme_mode_ambient_full),
                        selected = themeMode == ThemeMode.AMBIENT,
                        tag = "theme_option_ambient",
                        onClick = {
                            onSelectThemeMode(ThemeMode.AMBIENT)
                            themeMenuExpanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeOptionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    DropdownMenuItem(
        modifier = Modifier.testTag(tag),
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) cs.primary else cs.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        },
        text = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = if (selected) cs.primary else cs.onSurface
            )
        },
        trailingIcon = if (selected) {
            {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            null
        },
        onClick = onClick
    )
}
