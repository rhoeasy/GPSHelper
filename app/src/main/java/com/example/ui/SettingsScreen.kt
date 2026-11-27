package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ble.HeartRateState
import com.example.gps.GpsData
import com.example.gps.GpsFixStatus
import com.example.gps.OverlayDisplayConfig
import com.example.gps.SpeedUnit
import com.example.overlay.OverlayWindow
import com.example.sensor.GForceData
import com.example.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
    overlayConfig: OverlayDisplayConfig,
    gpsData: GpsData,
    gForceData: GForceData,
    heartRateState: HeartRateState,
    speedUnit: SpeedUnit,
    themeMode: ThemeMode,
    keepScreenOn: Boolean,
    autoFocusOnLaunch: Boolean,
    onUpdateOverlayConfig: ((OverlayDisplayConfig) -> OverlayDisplayConfig) -> Unit,
    onResetOverlayConfig: () -> Unit,
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
                            .padding(horizontal = 12.dp, vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        OverlayWindow(
                            gForceData = gForceData,
                            gpsData = previewGps,
                            speedUnit = speedUnit,
                            heartRateState = heartRateState,
                            config = overlayConfig,
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

            // Section 2: General & Appearance
            Text(
                text = stringResource(R.string.settings_section_general),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = cs.primary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

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

            Spacer(modifier = Modifier.height(16.dp))
        }
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
