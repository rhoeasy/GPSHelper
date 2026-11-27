package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ble.HeartRateState
import com.example.gps.GpsData
import com.example.gps.SpeedUnit
import com.example.track.ActivityMode
import com.example.track.RecordingSnapshot
import com.example.track.TrackSessionDetail
import com.example.track.TrackSessionEntity
import com.example.ui.theme.ThemeMode

enum class MainTab {
    DASHBOARD,
    TRACKS
}

@Composable
fun GpsHudAppShell(
    activeTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    gpsData: GpsData,
    speedUnit: SpeedUnit,
    keepScreenOn: Boolean,
    themeMode: ThemeMode,
    isFocusLockActive: Boolean,
    isOverlayRunning: Boolean,
    permissionStatus: PermissionStatus,
    heartRateState: HeartRateState,
    recordingSnapshot: RecordingSnapshot,
    savedSessions: List<TrackSessionEntity>,
    selectedSessionDetail: TrackSessionDetail?,
    onSelectSpeedUnit: (SpeedUnit) -> Unit,
    onToggleKeepScreenOn: (Boolean) -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onOpenFocusDialog: () -> Unit,
    onExitFocusLock: () -> Unit,
    onCopyCoordinates: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onToggleOverlay: () -> Unit,
    onOpenBlePicker: () -> Unit,
    onDisconnectBle: () -> Unit,
    onSelectActivityMode: (ActivityMode) -> Unit,
    onStartRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onFinishRecording: () -> Unit,
    onSetStartGate: () -> Unit,
    onSelectSession: (Long) -> Unit,
    onCloseSessionDetail: () -> Unit,
    onExportJson: (TrackSessionDetail) -> Unit,
    onExportGpx: (TrackSessionDetail) -> Unit,
    onDeleteSession: (Long) -> Unit
) {
    val cs = MaterialTheme.colorScheme

    if (selectedSessionDetail != null) {
        TrackDetailScreen(
            detail = selectedSessionDetail,
            speedUnit = speedUnit,
            isFocusLockActive = isFocusLockActive,
            onExitFocusLock = onExitFocusLock,
            onBack = onCloseSessionDetail,
            onExportJson = { onExportJson(selectedSessionDetail) },
            onExportGpx = { onExportGpx(selectedSessionDetail) },
            onDeleteSession = { onDeleteSession(selectedSessionDetail.session.id) },
            modifier = Modifier
                .fillMaxSize()
                .background(cs.background)
                .statusBarsPadding()
                .navigationBarsPadding()
        )
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = cs.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (activeTab == MainTab.DASHBOARD) {
                ExtendedFloatingActionButton(
                    onClick = onToggleOverlay,
                    modifier = Modifier.testTag("toggle_overlay_button"),
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
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            when (activeTab) {
                MainTab.DASHBOARD -> {
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
                            keepScreenOn = keepScreenOn,
                            isFocusLockActive = isFocusLockActive,
                            onSelectThemeMode = onSelectThemeMode,
                            onToggleKeepScreenOn = onToggleKeepScreenOn,
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

                        PrimarySpeedCard(
                            gpsData = gpsData,
                            speedUnit = speedUnit,
                            onSelectUnit = onSelectSpeedUnit,
                            onOpenLocationSettings = onOpenLocationSettings
                        )

                        TelemetryGrid(gpsData = gpsData)

                        CoordinatesCard(
                            gpsData = gpsData,
                            onCopyCoordinates = onCopyCoordinates
                        )

                        TrackRecorderCard(
                            snapshot = recordingSnapshot,
                            heartRateState = heartRateState,
                            onOpenBlePicker = onOpenBlePicker,
                            onSelectMode = onSelectActivityMode,
                            onStartRecording = onStartRecording,
                            onPauseRecording = onPauseRecording,
                            onResumeRecording = onResumeRecording,
                            onFinishRecording = onFinishRecording,
                            onSetStartGate = onSetStartGate
                        )

                        HeartRateStripCard(
                            hrState = heartRateState,
                            onOpenDevicePicker = onOpenBlePicker,
                            onDisconnect = onDisconnectBle
                        )

                        // Generous clearance above Extended FAB
                        Spacer(modifier = Modifier.height(78.dp))
                    }
                }

                MainTab.TRACKS -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 600.dp)
                    ) {
                        if (isFocusLockActive) {
                            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                FocusActiveBanner(onExitFocusLock = onExitFocusLock)
                            }
                        }
                        TrackHistoryScreen(
                            sessions = savedSessions,
                            speedUnit = speedUnit,
                            onSelectSession = onSelectSession,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GoogleTopHeader(
    gpsData: GpsData,
    themeMode: ThemeMode,
    keepScreenOn: Boolean,
    isFocusLockActive: Boolean,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onToggleKeepScreenOn: (Boolean) -> Unit,
    onOpenFocusDialog: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var themeMenuExpanded by remember { mutableStateOf(false) }

    val (themeIcon, themeLabelRes) = when (themeMode) {
        ThemeMode.AUTO -> Icons.Rounded.BrightnessAuto to R.string.theme_mode_auto
        ThemeMode.LIGHT -> Icons.Rounded.LightMode to R.string.theme_mode_light
        ThemeMode.DARK -> Icons.Rounded.DarkMode to R.string.theme_mode_dark
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
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

            // Keep screen on compact circular icon pill
            Surface(
                onClick = { onToggleKeepScreenOn(!keepScreenOn) },
                modifier = Modifier
                    .size(36.dp)
                    .testTag("keep_screen_on_switch"),
                shape = RoundedCornerShape(50),
                color = if (keepScreenOn) cs.primaryContainer else cs.surfaceContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.WbSunny,
                        contentDescription = stringResource(R.string.keep_screen_on),
                        tint = if (keepScreenOn) cs.onPrimaryContainer else cs.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
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
