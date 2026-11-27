package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class ThemeMode {
    AUTO,
    LIGHT,
    DARK
}

/**
 * Global Material 3 color theme palettes inspired by the Social Share Card background styles.
 */
enum class AppThemePalette(
    val labelZh: String,
    val subtitleZh: String,
    val swatchTopArgb: Long,
    val swatchBottomArgb: Long
) {
    GOOGLE_BLUE(
        labelZh = "谷歌蓝",
        subtitleZh = "经典清朗科技蓝",
        swatchTopArgb = 0xFF1A5CC8L,
        swatchBottomArgb = 0xFF0B275CL
    ),
    OBSIDIAN(
        labelZh = "极夜黑",
        subtitleZh = "暗夜碳纤钛灰质感",
        swatchTopArgb = 0xFF0E131AL,
        swatchBottomArgb = 0xFF1A2230L
    ),
    FOREST_GREEN(
        labelZh = "山野绿",
        subtitleZh = "徒步松林自然青绿",
        swatchTopArgb = 0xFF184A32L,
        swatchBottomArgb = 0xFF0B2419L
    ),
    TRACK_CRIMSON(
        labelZh = "赛道红",
        subtitleZh = "卡丁车竞速炽焰红",
        swatchTopArgb = 0xFF8C1D18L,
        swatchBottomArgb = 0xFF2B0A0DL
    ),
    SUNSET_AMBER(
        labelZh = "日落橙",
        subtitleZh = "越野穿越荒野暖橙",
        swatchTopArgb = 0xFFB8550AL,
        swatchBottomArgb = 0xFF3E1B05L
    ),
    PURE_WHITE(
        labelZh = "纯净白",
        subtitleZh = "北欧极简冰川霜白",
        swatchTopArgb = 0xFFFFFFFFL,
        swatchBottomArgb = 0xFFEEF3FAL
    );

    fun colorScheme(isDark: Boolean): ColorScheme = when (this) {
        GOOGLE_BLUE -> if (isDark) GoogleBlueDarkScheme else GoogleBlueLightScheme
        OBSIDIAN -> if (isDark) ObsidianDarkScheme else ObsidianLightScheme
        FOREST_GREEN -> if (isDark) ForestGreenDarkScheme else ForestGreenLightScheme
        TRACK_CRIMSON -> if (isDark) TrackCrimsonDarkScheme else TrackCrimsonLightScheme
        SUNSET_AMBER -> if (isDark) SunsetAmberDarkScheme else SunsetAmberLightScheme
        PURE_WHITE -> if (isDark) PureWhiteDarkScheme else PureWhiteLightScheme
    }
}

data class SemanticTelemetryColors(
    val heart: Color,
    val heartContainer: Color
)

val LocalTelemetryColors = staticCompositionLocalOf {
    SemanticTelemetryColors(
        heart = HeartRoseLight,
        heartContainer = HeartContainerLight
    )
}

private val GoogleShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun GpsHudTheme(
    themeMode: ThemeMode = ThemeMode.AUTO,
    themePalette: AppThemePalette = AppThemePalette.GOOGLE_BLUE,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.AUTO -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = themePalette.colorScheme(isDark)
    val telemetryColors = if (isDark) {
        SemanticTelemetryColors(heart = HeartRoseDark, heartContainer = HeartContainerDark)
    } else {
        SemanticTelemetryColors(heart = HeartRoseLight, heartContainer = HeartContainerLight)
    }

    CompositionLocalProvider(LocalTelemetryColors provides telemetryColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = HudTypography,
            shapes = GoogleShapes,
            content = content
        )
    }
}
