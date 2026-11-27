package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.gps.SpeedUnit
import com.example.ui.theme.AppThemePalette
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ThemeMode

/**
 * Level-2 Appearance & Theme Palette settings sub-screen.
 *
 * Lets users pick from 6 curated global Material 3 color palettes inspired by the Social Share
 * Card background styles, plus Light/Dark/Auto mode, speed unit, keep-screen-on, and auto-focus.
 */
@Composable
internal fun SettingsAppearanceContent(
    themePalette: AppThemePalette,
    themeMode: ThemeMode,
    speedUnit: SpeedUnit,
    keepScreenOn: Boolean,
    autoFocusOnLaunch: Boolean,
    onSelectThemePalette: (AppThemePalette) -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onSelectSpeedUnit: (SpeedUnit) -> Unit,
    onToggleKeepScreenOn: (Boolean) -> Unit,
    onToggleAutoFocusOnLaunch: (Boolean) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val systemDark = isSystemInDarkTheme()
    val previewDark = when (themeMode) {
        ThemeMode.AUTO -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // Section 1: Theme Color Palette Picker (inspired by Social Share Card background styles)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SettingsSectionHeader(text = stringResource(R.string.settings_theme_palette_section))
        Text(
            text = stringResource(R.string.settings_theme_palette_hint),
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }

    // 2-column visual palette selector grid
    val palettes = AppThemePalette.entries
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        palettes.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEach { palette ->
                    ThemePaletteOptionCard(
                        palette = palette,
                        selected = palette == themePalette,
                        isDarkPreview = previewDark,
                        onClick = { onSelectThemePalette(palette) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }

    // Live Mini Theme Preview Card
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_theme_live_preview"),
        shape = RoundedCornerShape(22.dp),
        color = cs.surfaceContainerLow,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = stringResource(R.string.settings_theme_preview_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "68.4",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = cs.primary
                    )
                    Text(
                        text = speedUnit.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = cs.primaryContainer
                ) {
                    Text(
                        text = themePalette.labelZh,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = cs.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = cs.secondaryContainer
                ) {
                    Text(
                        text = "0.84G",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = cs.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }

    // Section 2: General Display Preferences (Theme Mode, Speed Unit, Keep Screen On, Auto Focus)
    SettingsSectionHeader(text = stringResource(R.string.settings_section_general))

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = cs.surfaceContainerLow
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
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
                        tint = cs.primary,
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

            SettingsRowDivider()

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
                        tint = cs.primary,
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

            SettingsRowDivider()

            SettingsToggleRow(
                icon = Icons.Rounded.WbSunny,
                title = stringResource(R.string.keep_screen_on),
                subtitle = null,
                checked = keepScreenOn,
                testTag = "settings_switch_keep_screen_on",
                onCheckedChange = onToggleKeepScreenOn
            )

            SettingsRowDivider()

            SettingsToggleRow(
                icon = Icons.Rounded.Shield,
                title = stringResource(R.string.focus_mode_auto_launch),
                subtitle = stringResource(R.string.focus_mode_auto_launch_desc),
                checked = autoFocusOnLaunch,
                testTag = "settings_switch_auto_focus",
                onCheckedChange = onToggleAutoFocusOnLaunch
            )
        }
    }
}

@Composable
private fun ThemePaletteOptionCard(
    palette: AppThemePalette,
    selected: Boolean,
    isDarkPreview: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val scheme = palette.colorScheme(isDarkPreview)
    val topColor = Color(palette.swatchTopArgb)
    val bottomColor = Color(palette.swatchBottomArgb)
    val isPureWhite = palette == AppThemePalette.PURE_WHITE

    Surface(
        onClick = onClick,
        modifier = modifier.testTag("settings_palette_${palette.name.lowercase()}"),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) cs.primaryContainer.copy(alpha = 0.42f) else cs.surfaceContainerLow,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) cs.primary else cs.outlineVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Social-card-inspired gradient banner with M3 palette accent dots
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(topColor, bottomColor)))
                    .then(
                        if (isPureWhite) {
                            Modifier.border(
                                width = 1.dp,
                                color = cs.outlineVariant.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(14.dp)
                            )
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.align(Alignment.BottomStart),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(scheme.primary)
                            .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(scheme.secondary)
                            .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(scheme.tertiary)
                            .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                    )
                }

                if (selected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(cs.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = cs.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = palette.labelZh,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 14.sp
                    ),
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = palette.subtitleZh,
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
