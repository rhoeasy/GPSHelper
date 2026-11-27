package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.alert.SafetyAlertState
import com.example.ble.HeartRateState
import com.example.gps.GpsData
import com.example.gps.GpsFixStatus
import com.example.gps.OverlayDisplayConfig
import com.example.gps.OverlayScale
import com.example.gps.SafetyAlertConfig
import com.example.gps.SpeedUnit
import com.example.overlay.OverlayWindow
import com.example.sensor.GForceData
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ThemeMode
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    overlayConfig: OverlayDisplayConfig,
    safetyAlertConfig: SafetyAlertConfig,
    alertState: SafetyAlertState,
    gpsData: GpsData,
    gForceData: GForceData,
    heartRateState: HeartRateState,
    speedUnit: SpeedUnit,
    themeMode: ThemeMode,
    keepScreenOn: Boolean,
    autoFocusOnLaunch: Boolean,
    onUpdateOverlayConfig: ((OverlayDisplayConfig) -> OverlayDisplayConfig) -> Unit,
    onResetOverlayConfig: () -> Unit,
    onUpdateSafetyAlertConfig: ((SafetyAlertConfig) -> SafetyAlertConfig) -> Unit,
    onSelectSpeedUnit: (SpeedUnit) -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onToggleKeepScreenOn: (Boolean) -> Unit,
    onToggleAutoFocusOnLaunch: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    var previewMiniMode by remember { mutableStateOf(false) }

    // If GPS has no fix yet indoors, feed realistic preview values ONLY inside the Settings preview card
    val previewGps = remember(gpsData) {
        if (gpsData.hasFix) {
            gpsData
        } else {
            GpsData(
                hasFix = true,
                latitude = 39.904200,
                longitude = 116.407396,
                speedMps = 18.5f,
                altitudeMeters = 45.2,
                accuracyMeters = 3.2f,
                bearingDegrees = 128f,
                satellitesUsed = 14,
                satellitesVisible = 26,
                hasSatelliteInfo = true,
                fixStatus = GpsFixStatus.LOCKED
            )
        }
    }

    // Show a realistic asymmetric max-G envelope preview while stationary indoors so users see shape toggles live
    val previewGForce = remember(gForceData) {
        if (gForceData.peakG >= 0.08f && gForceData.peakEnvelope.isNotEmpty()) {
            gForceData
        } else {
            val sampleEnvelope = listOf(
                1.05f, 0.98f, 0.88f, 0.78f, 0.72f, 0.74f, 0.82f, 0.92f, 1.02f, // right -> braking
                1.12f, 1.04f, 0.90f, 0.76f, 0.70f, 0.75f, 0.86f, 0.96f, 1.00f, // braking -> left
                0.98f, 0.92f, 0.80f, 0.66f, 0.56f, 0.50f, 0.48f, 0.50f, 0.54f, // left -> accel
                0.58f, 0.54f, 0.50f, 0.54f, 0.62f, 0.72f, 0.84f, 0.95f, 1.02f  // accel -> right
            )
            gForceData.copy(
                lateralG = 0.38f,
                longitudinalG = -0.26f,
                totalG = 0.46f,
                peakG = 1.12f,
                peakLateralG = 0.0f,
                peakLongitudinalG = -1.12f,
                peakEnvelope = sampleEnvelope
            )
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 22.sp
                ),
                color = cs.onSurface
            )

            // Live Interactive Preview Card of the actual Compose OverlayWindow
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_overlay_preview_card"),
                shape = RoundedCornerShape(24.dp),
                color = cs.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.settings_preview_title),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = cs.onSurface
                        )

                        PreviewSizeToggle(
                            isMini = previewMiniMode,
                            onSelectMini = { previewMiniMode = it }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(cs.surfaceContainer)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        OverlayWindow(
                            gForceData = previewGForce,
                            gpsData = previewGps,
                            speedUnit = speedUnit,
                            heartRateState = heartRateState,
                            config = overlayConfig,
                            alertState = alertState,
                            themeMode = themeMode,
                            isMiniMode = previewMiniMode,
                            onToggleMiniMode = { previewMiniMode = !previewMiniMode },
                            onClose = {},
                            showCloseButton = true
                        )
                    }

                    Text(
                        text = stringResource(R.string.settings_preview_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }

            // Section 1: Overlay Content Toggles
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.settings_section_overlay_content),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = cs.primary
                )
                TextButton(
                    onClick = onResetOverlayConfig,
                    modifier = Modifier.testTag("btn_reset_overlay_config")
                ) {
                    Text(
                        text = stringResource(R.string.settings_reset_default),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = cs.surfaceContainerLow
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingsSwitchRow(
                        icon = Icons.Rounded.Speed,
                        title = stringResource(R.string.settings_item_speed_title),
                        subtitle = stringResource(R.string.settings_item_speed_desc),
                        checked = overlayConfig.showSpeed,
                        testTag = "switch_overlay_speed",
                        onCheckedChange = { checked ->
                            onUpdateOverlayConfig { it.copy(showSpeed = checked) }
                        }
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        icon = Icons.Rounded.Sensors,
                        title = stringResource(R.string.settings_item_gforce_title),
                        subtitle = stringResource(R.string.settings_item_gforce_desc),
                        checked = overlayConfig.showGForce,
                        testTag = "switch_overlay_gforce",
                        onCheckedChange = { checked ->
                            onUpdateOverlayConfig { it.copy(showGForce = checked) }
                        }
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        icon = Icons.Rounded.Terrain,
                        title = stringResource(R.string.settings_item_altitude_title),
                        subtitle = stringResource(R.string.settings_item_altitude_desc),
                        checked = overlayConfig.showAltitude,
                        testTag = "switch_overlay_altitude",
                        onCheckedChange = { checked ->
                            onUpdateOverlayConfig { it.copy(showAltitude = checked) }
                        }
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        icon = Icons.Rounded.Favorite,
                        title = stringResource(R.string.settings_item_hr_title),
                        subtitle = stringResource(R.string.settings_item_hr_desc),
                        checked = overlayConfig.showHeartRate,
                        testTag = "switch_overlay_hr",
                        onCheckedChange = { checked ->
                            onUpdateOverlayConfig { it.copy(showHeartRate = checked) }
                        }
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        icon = Icons.Rounded.Explore,
                        title = stringResource(R.string.settings_item_bearing_title),
                        subtitle = stringResource(R.string.settings_item_bearing_desc),
                        checked = overlayConfig.showBearing,
                        testTag = "switch_overlay_bearing",
                        onCheckedChange = { checked ->
                            onUpdateOverlayConfig { it.copy(showBearing = checked) }
                        }
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        icon = Icons.Rounded.GpsFixed,
                        title = stringResource(R.string.settings_item_accuracy_title),
                        subtitle = stringResource(R.string.settings_item_accuracy_desc),
                        checked = overlayConfig.showAccuracy,
                        testTag = "switch_overlay_accuracy",
                        onCheckedChange = { checked ->
                            onUpdateOverlayConfig { it.copy(showAccuracy = checked) }
                        }
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        icon = Icons.Rounded.MyLocation,
                        title = stringResource(R.string.settings_item_coords_title),
                        subtitle = stringResource(R.string.settings_item_coords_desc),
                        checked = overlayConfig.showCoordinates,
                        testTag = "switch_overlay_coords",
                        onCheckedChange = { checked ->
                            onUpdateOverlayConfig { it.copy(showCoordinates = checked) }
                        }
                    )
                }
            }

            // Section 2: Overlay Appearance, Scale, Riding Font Size & Transparency
            SectionHeaderText(text = stringResource(R.string.settings_section_overlay_style))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = cs.surfaceContainerLow
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Overall Scale presets (小 / 中 / 大 / 特大)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AspectRatio,
                                contentDescription = null,
                                tint = cs.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.settings_scale_title),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                color = cs.onSurface
                            )
                        }

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(cs.surfaceContainer)
                                .padding(3.dp)
                        ) {
                            OverlayScale.entries.forEach { preset ->
                                val selected = preset == overlayConfig.scale
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(if (selected) cs.primaryContainer else Color.Transparent)
                                        .clickable {
                                            onUpdateOverlayConfig { it.copy(scale = preset) }
                                        }
                                        .padding(horizontal = 11.dp, vertical = 6.dp)
                                        .testTag("settings_scale_${preset.name.lowercase()}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = preset.labelZh,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                        ),
                                        color = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    SettingsDivider()

                    // Riding Font Size Slider (85% .. 160%)
                    SettingsSliderRow(
                        icon = Icons.Rounded.FormatSize,
                        title = stringResource(R.string.settings_font_size_title),
                        valueLabel = "${(overlayConfig.fontScale * 100f).roundToInt()}%",
                        value = overlayConfig.fontScale,
                        valueRange = 0.85f..1.60f,
                        steps = 14,
                        testTag = "slider_overlay_font_scale",
                        onValueChange = { v ->
                            val snapped = (v * 20f).roundToInt() / 20f
                            onUpdateOverlayConfig { it.copy(fontScale = snapped.coerceIn(0.85f, 1.60f)) }
                        }
                    )

                    SettingsDivider()

                    // Background Opacity Slider (45% .. 98%)
                    SettingsSliderRow(
                        icon = Icons.Rounded.Opacity,
                        title = stringResource(R.string.settings_alpha_title),
                        valueLabel = "${(overlayConfig.backgroundAlpha * 100f).roundToInt()}%",
                        value = overlayConfig.backgroundAlpha,
                        valueRange = 0.45f..0.98f,
                        steps = 0,
                        testTag = "slider_overlay_alpha",
                        onValueChange = { v ->
                            val snapped = (v * 100f).roundToInt() / 100f
                            onUpdateOverlayConfig { it.copy(backgroundAlpha = snapped.coerceIn(0.45f, 0.98f)) }
                        }
                    )

                    Text(
                        text = stringResource(R.string.settings_position_memory_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                    )
                }
            }

            // Section 3: G-Force Peak Radar Marker & Configurable Hold Duration
            SectionHeaderText(text = stringResource(R.string.settings_section_gforce))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = cs.surfaceContainerLow
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingsSwitchRow(
                        icon = Icons.Rounded.Radar,
                        title = stringResource(R.string.settings_gforce_peak_dial_title),
                        subtitle = stringResource(R.string.settings_gforce_peak_dial_desc),
                        checked = overlayConfig.showGForcePeakOnDial,
                        testTag = "switch_gforce_peak_dial",
                        onCheckedChange = { checked ->
                            onUpdateOverlayConfig { it.copy(showGForcePeakOnDial = checked) }
                        }
                    )

                    SettingsDivider()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Timer,
                                contentDescription = null,
                                tint = cs.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = stringResource(R.string.settings_gforce_peak_hold_title),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 15.sp
                                    ),
                                    color = cs.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.settings_gforce_peak_hold_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant
                                )
                            }
                        }

                        val holdOptions = listOf(
                            10 to R.string.peak_hold_10s,
                            30 to R.string.peak_hold_30s,
                            60 to R.string.peak_hold_60s,
                            180 to R.string.peak_hold_180s,
                            0 to R.string.peak_hold_forever
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(50))
                                .background(cs.surfaceContainer)
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            holdOptions.forEach { (sec, labelRes) ->
                                val selected = overlayConfig.gForcePeakHoldSeconds == sec
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(50))
                                        .background(if (selected) cs.primaryContainer else Color.Transparent)
                                        .clickable {
                                            onUpdateOverlayConfig { it.copy(gForcePeakHoldSeconds = sec) }
                                        }
                                        .padding(vertical = 7.dp)
                                        .testTag("settings_peak_hold_$sec"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(labelRes),
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                            fontSize = 12.sp
                                        ),
                                        color = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section 4: Safety Alerts (Overspeed & High Heart Rate)
            SectionHeaderText(text = stringResource(R.string.settings_section_safety))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = cs.surfaceContainerLow
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingsSwitchRow(
                        icon = Icons.Rounded.WarningAmber,
                        title = stringResource(R.string.settings_alert_speed_title),
                        subtitle = stringResource(R.string.settings_alert_speed_desc),
                        checked = safetyAlertConfig.speedAlertEnabled,
                        testTag = "switch_alert_speed",
                        onCheckedChange = { checked ->
                            onUpdateSafetyAlertConfig { it.copy(speedAlertEnabled = checked) }
                        }
                    )

                    if (safetyAlertConfig.speedAlertEnabled) {
                        val displaySpeedLimit = if (speedUnit == SpeedUnit.MPH) {
                            "${(safetyAlertConfig.speedLimitKmh * 0.621371f).roundToInt()} mph"
                        } else {
                            "${safetyAlertConfig.speedLimitKmh} km/h"
                        }
                        SettingsSliderRow(
                            icon = Icons.Rounded.Speed,
                            title = stringResource(R.string.settings_alert_speed_limit_label),
                            valueLabel = displaySpeedLimit,
                            value = safetyAlertConfig.speedLimitKmh.toFloat(),
                            valueRange = 20f..180f,
                            steps = 31,
                            testTag = "slider_alert_speed_limit",
                            onValueChange = { v ->
                                val rounded = (v / 5f).roundToInt() * 5
                                onUpdateSafetyAlertConfig {
                                    it.copy(speedLimitKmh = rounded.coerceIn(20, 180))
                                }
                            }
                        )
                    }

                    SettingsDivider()

                    SettingsSwitchRow(
                        icon = Icons.Rounded.Favorite,
                        title = stringResource(R.string.settings_alert_hr_title),
                        subtitle = stringResource(R.string.settings_alert_hr_desc),
                        checked = safetyAlertConfig.hrAlertEnabled,
                        testTag = "switch_alert_hr",
                        onCheckedChange = { checked ->
                            onUpdateSafetyAlertConfig { it.copy(hrAlertEnabled = checked) }
                        }
                    )

                    if (safetyAlertConfig.hrAlertEnabled) {
                        SettingsSliderRow(
                            icon = Icons.Rounded.Favorite,
                            title = stringResource(R.string.settings_alert_hr_limit_label),
                            valueLabel = "${safetyAlertConfig.hrLimitBpm} bpm",
                            value = safetyAlertConfig.hrLimitBpm.toFloat(),
                            valueRange = 120f..210f,
                            steps = 17,
                            testTag = "slider_alert_hr_limit",
                            onValueChange = { v ->
                                val rounded = (v / 5f).roundToInt() * 5
                                onUpdateSafetyAlertConfig {
                                    it.copy(hrLimitBpm = rounded.coerceIn(120, 210))
                                }
                            }
                        )
                    }

                    SettingsDivider()

                    SettingsSwitchRow(
                        icon = Icons.Rounded.Vibration,
                        title = stringResource(R.string.settings_alert_vibrate_title),
                        subtitle = stringResource(R.string.settings_alert_vibrate_desc),
                        checked = safetyAlertConfig.vibrateOnAlert,
                        testTag = "switch_alert_vibrate",
                        onCheckedChange = { checked ->
                            onUpdateSafetyAlertConfig { it.copy(vibrateOnAlert = checked) }
                        }
                    )
                }
            }

            // Section 5: General & Display
            SectionHeaderText(text = stringResource(R.string.settings_section_general))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = cs.surfaceContainerLow
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Speed Unit selector row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Speed,
                                contentDescription = null,
                                tint = cs.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.settings_speed_unit_title),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                color = cs.onSurface
                            )
                        }

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(cs.surfaceContainer)
                                .padding(3.dp)
                        ) {
                            SpeedUnit.entries.forEach { unit ->
                                val selected = unit == speedUnit
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(if (selected) cs.primaryContainer else Color.Transparent)
                                        .clickable { onSelectSpeedUnit(unit) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                        .testTag("settings_unit_${unit.name.lowercase()}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = unit.label,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                        ),
                                        color = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    SettingsDivider()

                    // Theme mode selector row (Auto / Light / Dark)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = when (themeMode) {
                                    ThemeMode.AUTO -> Icons.Rounded.BrightnessAuto
                                    ThemeMode.LIGHT -> Icons.Rounded.LightMode
                                    ThemeMode.DARK -> Icons.Rounded.DarkMode
                                },
                                contentDescription = null,
                                tint = cs.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.theme_mode_label),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                color = cs.onSurface
                            )
                        }

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(cs.surfaceContainer)
                                .padding(3.dp)
                        ) {
                            listOf(
                                ThemeMode.AUTO to R.string.theme_mode_auto,
                                ThemeMode.LIGHT to R.string.theme_mode_light,
                                ThemeMode.DARK to R.string.theme_mode_dark
                            ).forEach { (mode, labelRes) ->
                                val selected = mode == themeMode
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(if (selected) cs.primaryContainer else Color.Transparent)
                                        .clickable { onSelectThemeMode(mode) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                        .testTag("settings_theme_${mode.name.lowercase()}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(labelRes),
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                        ),
                                        color = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    SettingsDivider()

                    SettingsSwitchRow(
                        icon = Icons.Rounded.WbSunny,
                        title = stringResource(R.string.keep_screen_on),
                        subtitle = null,
                        checked = keepScreenOn,
                        testTag = "settings_switch_keep_screen_on",
                        onCheckedChange = onToggleKeepScreenOn
                    )

                    SettingsDivider()

                    SettingsSwitchRow(
                        icon = Icons.Rounded.Shield,
                        title = stringResource(R.string.focus_mode_auto_launch),
                        subtitle = stringResource(R.string.focus_mode_auto_launch_desc),
                        checked = autoFocusOnLaunch,
                        testTag = "settings_switch_auto_focus",
                        onCheckedChange = onToggleAutoFocusOnLaunch
                    )
                }
            }

            // Version footer
            Text(
                text = stringResource(R.string.settings_version_footer),
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant.copy(alpha = 0.72f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 18.dp)
            )
        }
    }
}

@Composable
private fun SectionHeaderText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
private fun SettingsSliderRow(
    icon: ImageVector,
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    testTag: String,
    onValueChange: (Float) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp
                    ),
                    color = cs.onSurface
                )
            }
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.SemiBold
                ),
                color = cs.primary
            )
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
        )
    }
}

@Composable
private fun PreviewSizeToggle(
    isMini: Boolean,
    onSelectMini: (Boolean) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(cs.surfaceContainer)
            .padding(3.dp)
    ) {
        listOf(
            false to R.string.settings_preview_standard,
            true to R.string.settings_preview_mini
        ).forEach { (mini, labelRes) ->
            val selected = mini == isMini
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) cs.primaryContainer else Color.Transparent)
                    .clickable { onSelectMini(mini) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (checked) cs.primary else cs.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp
                ),
                color = cs.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    )
}
