package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.R
import com.example.ai.AiConnectionState
import com.example.ai.AiGatewayConfig
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
import com.example.ui.theme.AppThemePalette
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ThemeMode
import kotlin.math.roundToInt

/**
 * Second-level Settings destinations for clean two-level hierarchy navigation.
 */
enum class SettingsSubPage {
    APPEARANCE,
    OVERLAY,
    SAFETY,
    AI_GATEWAY,
    ABOUT
}

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
    themePalette: AppThemePalette,
    keepScreenOn: Boolean,
    autoFocusOnLaunch: Boolean,
    aiGatewayConfig: AiGatewayConfig,
    aiFetchedModels: List<String>,
    aiConnectionState: AiConnectionState,
    aiHistoryCount: Int,
    selectedSubPage: SettingsSubPage?,
    onSelectSubPage: (SettingsSubPage?) -> Unit,
    onUpdateOverlayConfig: ((OverlayDisplayConfig) -> OverlayDisplayConfig) -> Unit,
    onResetOverlayConfig: () -> Unit,
    onUpdateSafetyAlertConfig: ((SafetyAlertConfig) -> SafetyAlertConfig) -> Unit,
    onSelectSpeedUnit: (SpeedUnit) -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onSelectThemePalette: (AppThemePalette) -> Unit,
    onToggleKeepScreenOn: (Boolean) -> Unit,
    onToggleAutoFocusOnLaunch: (Boolean) -> Unit,
    onTestAndFetchAiModels: (baseUrl: String, apiKey: String, currentModelId: String) -> Unit,
    onCancelAiModelTest: () -> Unit,
    onSaveAiGatewayConfig: (baseUrl: String, apiKey: String, modelId: String) -> Unit,
    onClearAiGatewayConfig: () -> Unit,
    onOpenAiHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme

    BackHandler(enabled = selectedSubPage != null) {
        onSelectSubPage(null)
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        if (selectedSubPage == null) {
            SettingsRootMenu(
                overlayConfig = overlayConfig,
                safetyAlertConfig = safetyAlertConfig,
                speedUnit = speedUnit,
                themeMode = themeMode,
                themePalette = themePalette,
                aiGatewayConfig = aiGatewayConfig,
                aiHistoryCount = aiHistoryCount,
                onSelectSubPage = onSelectSubPage
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Level-2 Sub-Page Top Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = { onSelectSubPage(null) },
                            modifier = Modifier.testTag("btn_settings_subpage_back")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.settings_back_cd),
                                tint = cs.onSurface
                            )
                        }
                        Text(
                            text = stringResource(
                                when (selectedSubPage) {
                                    SettingsSubPage.APPEARANCE -> R.string.settings_cat_appearance_title
                                    SettingsSubPage.OVERLAY -> R.string.settings_cat_overlay_title
                                    SettingsSubPage.SAFETY -> R.string.settings_cat_safety_title
                                    SettingsSubPage.AI_GATEWAY -> R.string.settings_cat_ai_title
                                    SettingsSubPage.ABOUT -> R.string.settings_cat_about_title
                                }
                            ),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 20.sp
                            ),
                            color = cs.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (selectedSubPage == SettingsSubPage.OVERLAY) {
                        TextButton(
                            onClick = onResetOverlayConfig,
                            modifier = Modifier.testTag("btn_reset_overlay_config")
                        ) {
                            Text(
                                text = stringResource(R.string.settings_reset_default),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (selectedSubPage) {
                        SettingsSubPage.APPEARANCE -> {
                            SettingsAppearanceContent(
                                themePalette = themePalette,
                                themeMode = themeMode,
                                speedUnit = speedUnit,
                                keepScreenOn = keepScreenOn,
                                autoFocusOnLaunch = autoFocusOnLaunch,
                                onSelectThemePalette = onSelectThemePalette,
                                onSelectThemeMode = onSelectThemeMode,
                                onSelectSpeedUnit = onSelectSpeedUnit,
                                onToggleKeepScreenOn = onToggleKeepScreenOn,
                                onToggleAutoFocusOnLaunch = onToggleAutoFocusOnLaunch
                            )
                        }

                        SettingsSubPage.OVERLAY -> {
                            SettingsOverlaySubContent(
                                overlayConfig = overlayConfig,
                                alertState = alertState,
                                gpsData = gpsData,
                                gForceData = gForceData,
                                heartRateState = heartRateState,
                                speedUnit = speedUnit,
                                themeMode = themeMode,
                                themePalette = themePalette,
                                onUpdateOverlayConfig = onUpdateOverlayConfig
                            )
                        }

                        SettingsSubPage.SAFETY -> {
                            SettingsSafetySubContent(
                                safetyAlertConfig = safetyAlertConfig,
                                speedUnit = speedUnit,
                                onUpdateSafetyAlertConfig = onUpdateSafetyAlertConfig
                            )
                        }

                        SettingsSubPage.AI_GATEWAY -> {
                            AiGatewaySettingsCard(
                                config = aiGatewayConfig,
                                fetchedModels = aiFetchedModels,
                                connectionState = aiConnectionState,
                                aiHistoryCount = aiHistoryCount,
                                onTestAndFetchModels = onTestAndFetchAiModels,
                                onCancelTest = onCancelAiModelTest,
                                onSaveConfig = onSaveAiGatewayConfig,
                                onClearConfig = onClearAiGatewayConfig,
                                onOpenAiHistory = onOpenAiHistory
                            )
                        }

                        SettingsSubPage.ABOUT -> {
                            SettingsAboutSubContent()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRootMenu(
    overlayConfig: OverlayDisplayConfig,
    safetyAlertConfig: SafetyAlertConfig,
    speedUnit: SpeedUnit,
    themeMode: ThemeMode,
    themePalette: AppThemePalette,
    aiGatewayConfig: AiGatewayConfig,
    aiHistoryCount: Int,
    onSelectSubPage: (SettingsSubPage) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val themeModeLabel = stringResource(
        when (themeMode) {
            ThemeMode.AUTO -> R.string.theme_mode_auto
            ThemeMode.LIGHT -> R.string.theme_mode_light
            ThemeMode.DARK -> R.string.theme_mode_dark
            ThemeMode.AMBIENT -> R.string.theme_mode_ambient
        }
    )

    val overlaySummary = stringResource(
        R.string.settings_summary_overlay_count,
        overlayConfig.enabledCount,
        overlayConfig.scale.labelZh
    )

    val safetySummary = remember(safetyAlertConfig, speedUnit) {
        val parts = mutableListOf<String>()
        if (safetyAlertConfig.speedAlertEnabled) {
            val spd = if (speedUnit == SpeedUnit.MPH) {
                "${(safetyAlertConfig.speedLimitKmh * 0.621371f).roundToInt()} mph"
            } else {
                "${safetyAlertConfig.speedLimitKmh} km/h"
            }
            parts += "超速 $spd"
        }
        if (safetyAlertConfig.hrAlertEnabled) {
            parts += "心率 ${safetyAlertConfig.hrLimitBpm} bpm"
        }
        parts.joinToString(" · ")
    }.ifBlank { stringResource(R.string.settings_summary_safety_off) }

    val aiSummaryBase = if (aiGatewayConfig.isConfigured) {
        stringResource(R.string.settings_summary_ai_ready, aiGatewayConfig.modelId)
    } else {
        stringResource(R.string.settings_summary_ai_unconfigured)
    }
    val aiSummary = if (aiHistoryCount > 0) "$aiSummaryBase · $aiHistoryCount 条历史" else aiSummaryBase

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

        // Primary Settings Group: Appearance & Theme, Overlay HUD, Safety Alerts
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = cs.surfaceContainerLow
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SettingsMenuEntryRow(
                    icon = Icons.Rounded.Palette,
                    iconContainerColor = cs.primaryContainer,
                    iconTint = cs.onPrimaryContainer,
                    title = stringResource(R.string.settings_cat_appearance_title),
                    subtitle = "${themePalette.labelZh} · $themeModeLabel · ${speedUnit.label}",
                    testTag = "settings_menu_appearance",
                    trailingBadge = {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            Color(themePalette.swatchTopArgb),
                                            Color(themePalette.swatchBottomArgb)
                                        )
                                    )
                                )
                                .border(1.dp, cs.outlineVariant, CircleShape)
                        )
                    },
                    onClick = { onSelectSubPage(SettingsSubPage.APPEARANCE) }
                )

                SettingsRowDivider()

                SettingsMenuEntryRow(
                    icon = Icons.Rounded.Layers,
                    iconContainerColor = cs.secondaryContainer,
                    iconTint = cs.onSecondaryContainer,
                    title = stringResource(R.string.settings_cat_overlay_title),
                    subtitle = overlaySummary,
                    testTag = "settings_menu_overlay",
                    onClick = { onSelectSubPage(SettingsSubPage.OVERLAY) }
                )

                SettingsRowDivider()

                SettingsMenuEntryRow(
                    icon = Icons.Rounded.WarningAmber,
                    iconContainerColor = cs.tertiaryContainer,
                    iconTint = cs.onTertiaryContainer,
                    title = stringResource(R.string.settings_cat_safety_title),
                    subtitle = safetySummary,
                    testTag = "settings_menu_safety",
                    onClick = { onSelectSubPage(SettingsSubPage.SAFETY) }
                )
            }
        }

        // Secondary Settings Group: AI Coach Gateway & About
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = cs.surfaceContainerLow
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SettingsMenuEntryRow(
                    icon = Icons.Rounded.AutoAwesome,
                    iconContainerColor = cs.primaryContainer,
                    iconTint = cs.onPrimaryContainer,
                    title = stringResource(R.string.settings_cat_ai_title),
                    subtitle = aiSummary,
                    testTag = "settings_menu_ai",
                    onClick = { onSelectSubPage(SettingsSubPage.AI_GATEWAY) }
                )

                SettingsRowDivider()

                SettingsMenuEntryRow(
                    icon = Icons.Rounded.Info,
                    iconContainerColor = cs.surfaceContainerHighest,
                    iconTint = cs.onSurfaceVariant,
                    title = stringResource(R.string.settings_cat_about_title),
                    subtitle = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    testTag = "settings_menu_about",
                    onClick = { onSelectSubPage(SettingsSubPage.ABOUT) }
                )
            }
        }

        // Version footer (dynamically populated from BuildConfig / GitHub Actions release version)
        Text(
            text = stringResource(
                R.string.settings_version_footer,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE
            ),
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant.copy(alpha = 0.72f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 18.dp)
                .testTag("settings_version_footer")
        )
    }
}

@Composable
private fun SettingsMenuEntryRow(
    icon: ImageVector,
    iconContainerColor: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    testTag: String,
    trailingBadge: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(iconContainerColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                ),
                color = cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (trailingBadge != null) {
            trailingBadge()
        }

        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = cs.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingsOverlaySubContent(
    overlayConfig: OverlayDisplayConfig,
    alertState: SafetyAlertState,
    gpsData: GpsData,
    gForceData: GForceData,
    heartRateState: HeartRateState,
    speedUnit: SpeedUnit,
    themeMode: ThemeMode,
    themePalette: AppThemePalette,
    onUpdateOverlayConfig: ((OverlayDisplayConfig) -> OverlayDisplayConfig) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var previewMiniMode by remember { mutableStateOf(false) }

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

    val previewGForce = remember(gForceData) {
        if (gForceData.peakG >= 0.08f && gForceData.peakEnvelope.isNotEmpty()) {
            gForceData
        } else {
            val sampleEnvelope = listOf(
                1.05f, 0.98f, 0.88f, 0.78f, 0.72f, 0.74f, 0.82f, 0.92f, 1.02f,
                1.12f, 1.04f, 0.90f, 0.76f, 0.70f, 0.75f, 0.86f, 0.96f, 1.00f,
                0.98f, 0.92f, 0.80f, 0.66f, 0.56f, 0.50f, 0.48f, 0.50f, 0.54f,
                0.58f, 0.54f, 0.50f, 0.54f, 0.62f, 0.72f, 0.84f, 0.95f, 1.02f
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

    // Live Interactive Preview Card
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
                    themePalette = themePalette,
                    isMiniMode = previewMiniMode,
                    onToggleMiniMode = { previewMiniMode = !previewMiniMode },
                    previewRideElapsedMs = overlayConfig.rideMarkerIntervalMinutes * 120_000L + 15_000L
                )
            }

            Text(
                text = stringResource(R.string.settings_preview_hint),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
        }
    }

    // Overlay Content Toggles
    SettingsSectionHeader(text = stringResource(R.string.settings_section_overlay_content))

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = cs.surfaceContainerLow
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SettingsToggleRow(
                icon = Icons.Rounded.Speed,
                title = stringResource(R.string.settings_item_speed_title),
                subtitle = stringResource(R.string.settings_item_speed_desc),
                checked = overlayConfig.showSpeed,
                testTag = "switch_overlay_speed",
                onCheckedChange = { checked ->
                    onUpdateOverlayConfig { it.copy(showSpeed = checked) }
                }
            )
            SettingsRowDivider()
            SettingsToggleRow(
                icon = Icons.Rounded.Sensors,
                title = stringResource(R.string.settings_item_gforce_title),
                subtitle = stringResource(R.string.settings_item_gforce_desc),
                checked = overlayConfig.showGForce,
                testTag = "switch_overlay_gforce",
                onCheckedChange = { checked ->
                    onUpdateOverlayConfig { it.copy(showGForce = checked) }
                }
            )
            SettingsRowDivider()
            SettingsToggleRow(
                icon = Icons.Rounded.Terrain,
                title = stringResource(R.string.settings_item_altitude_title),
                subtitle = stringResource(R.string.settings_item_altitude_desc),
                checked = overlayConfig.showAltitude,
                testTag = "switch_overlay_altitude",
                onCheckedChange = { checked ->
                    onUpdateOverlayConfig { it.copy(showAltitude = checked) }
                }
            )
            SettingsRowDivider()
            SettingsToggleRow(
                icon = Icons.Rounded.Favorite,
                title = stringResource(R.string.settings_item_hr_title),
                subtitle = stringResource(R.string.settings_item_hr_desc),
                checked = overlayConfig.showHeartRate,
                testTag = "switch_overlay_hr",
                onCheckedChange = { checked ->
                    onUpdateOverlayConfig { it.copy(showHeartRate = checked) }
                }
            )
            SettingsRowDivider()
            SettingsToggleRow(
                icon = Icons.Rounded.Explore,
                title = stringResource(R.string.settings_item_bearing_title),
                subtitle = stringResource(R.string.settings_item_bearing_desc),
                checked = overlayConfig.showBearing,
                testTag = "switch_overlay_bearing",
                onCheckedChange = { checked ->
                    onUpdateOverlayConfig { it.copy(showBearing = checked) }
                }
            )
            SettingsRowDivider()
            SettingsToggleRow(
                icon = Icons.Rounded.GpsFixed,
                title = stringResource(R.string.settings_item_accuracy_title),
                subtitle = stringResource(R.string.settings_item_accuracy_desc),
                checked = overlayConfig.showAccuracy,
                testTag = "switch_overlay_accuracy",
                onCheckedChange = { checked ->
                    onUpdateOverlayConfig { it.copy(showAccuracy = checked) }
                }
            )
            SettingsRowDivider()
            SettingsToggleRow(
                icon = Icons.Rounded.MyLocation,
                title = stringResource(R.string.settings_item_coords_title),
                subtitle = stringResource(R.string.settings_item_coords_desc),
                checked = overlayConfig.showCoordinates,
                testTag = "switch_overlay_coords",
                onCheckedChange = { checked ->
                    onUpdateOverlayConfig { it.copy(showCoordinates = checked) }
                }
            )
            SettingsRowDivider()
            SettingsToggleRow(
                icon = Icons.Rounded.Timer,
                title = stringResource(R.string.settings_overlay_ride_timer_title),
                subtitle = stringResource(R.string.settings_overlay_ride_timer_desc),
                checked = overlayConfig.showRideTimer,
                testTag = "switch_overlay_ride_timer",
                onCheckedChange = { checked -> onUpdateOverlayConfig { it.copy(showRideTimer = checked) } }
            )
            if (overlayConfig.showRideTimer) {
                SettingsSliderRow(
                    icon = Icons.Rounded.Timer,
                    title = stringResource(R.string.settings_overlay_ride_marker_interval),
                    valueLabel = stringResource(R.string.settings_overlay_ride_marker_minutes, overlayConfig.rideMarkerIntervalMinutes),
                    value = overlayConfig.rideMarkerIntervalMinutes.toFloat(),
                    valueRange = 1f..60f,
                    steps = 58,
                    testTag = "slider_overlay_ride_marker_interval",
                    onValueChange = { raw ->
                        val minutes = raw.roundToInt()
                        if (minutes != overlayConfig.rideMarkerIntervalMinutes) {
                            onUpdateOverlayConfig { it.copy(rideMarkerIntervalMinutes = minutes) }
                        }
                    }
                )
            }
        }
    }

    // Overlay Scale, Font Size & Transparency
    SettingsSectionHeader(text = stringResource(R.string.settings_section_overlay_style))

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = cs.surfaceContainerLow
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
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

            SettingsRowDivider()

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

            SettingsRowDivider()

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

    // G-Force Peak Radar Marker & Hold Duration
    SettingsSectionHeader(text = stringResource(R.string.settings_section_gforce))

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = cs.surfaceContainerLow
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SettingsToggleRow(
                icon = Icons.Rounded.Radar,
                title = stringResource(R.string.settings_gforce_peak_dial_title),
                subtitle = stringResource(R.string.settings_gforce_peak_dial_desc),
                checked = overlayConfig.showGForcePeakOnDial,
                testTag = "switch_gforce_peak_dial",
                onCheckedChange = { checked ->
                    onUpdateOverlayConfig { it.copy(showGForcePeakOnDial = checked) }
                }
            )

            SettingsRowDivider()

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
}

@Composable
private fun SettingsSafetySubContent(
    safetyAlertConfig: SafetyAlertConfig,
    speedUnit: SpeedUnit,
    onUpdateSafetyAlertConfig: ((SafetyAlertConfig) -> SafetyAlertConfig) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = cs.surfaceContainerLow
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SettingsToggleRow(
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

            SettingsRowDivider()

            SettingsToggleRow(
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

            SettingsRowDivider()

            SettingsToggleRow(
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
}

@Composable
private fun SettingsAboutSubContent() {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = cs.surfaceContainerLow,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(cs.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Speed,
                    contentDescription = null,
                    tint = cs.onPrimaryContainer,
                    modifier = Modifier.size(30.dp)
                )
            }
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = cs.onSurface
            )
            Surface(
                shape = RoundedCornerShape(50),
                color = cs.secondaryContainer
            ) {
                Text(
                    text = "v${BuildConfig.VERSION_NAME} · Build ${BuildConfig.VERSION_CODE}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = cs.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = cs.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(20.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.settings_about_privacy_title),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = cs.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_about_privacy_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.45f))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = cs.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.settings_about_sensors_title),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = cs.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_about_sensors_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
internal fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
internal fun SettingsRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    )
}

@Composable
internal fun SettingsToggleRow(
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
