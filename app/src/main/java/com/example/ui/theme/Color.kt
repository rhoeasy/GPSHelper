package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Google Material 3 Light Palette (Google Blue tonal system, clean & serene)
val LightPrimary = Color(0xFF1A73E8)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFD3E3FD)
val LightOnPrimaryContainer = Color(0xFF041E49)

val LightSecondary = Color(0xFF1E8E3E)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFC4EED0)
val LightOnSecondaryContainer = Color(0xFF072711)

val LightTertiary = Color(0xFFB06000)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFFFEFC3)
val LightOnTertiaryContainer = Color(0xFF3E2200)

val LightError = Color(0xFFD93025)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFCE8E6)
val LightOnErrorContainer = Color(0xFF410E0B)

val LightBackground = Color(0xFFF6F8FC)
val LightOnBackground = Color(0xFF1F1F1F)
val LightSurface = Color(0xFFF6F8FC)
val LightOnSurface = Color(0xFF1F1F1F)
val LightSurfaceVariant = Color(0xFFE9EEF6)
val LightOnSurfaceVariant = Color(0xFF444746)
val LightOutline = Color(0xFF747775)
val LightOutlineVariant = Color(0xFFDDE3EA)

val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFFFFFFF)
val LightSurfaceContainer = Color(0xFFF0F4F9)
val LightSurfaceContainerHigh = Color(0xFFE7EDF5)
val LightSurfaceContainerHighest = Color(0xFFDDE3EC)

// Google Material 3 Dark Palette (Pixel / Google Maps night surfaces)
val DarkPrimary = Color(0xFFA8C7FA)
val DarkOnPrimary = Color(0xFF062E6F)
val DarkPrimaryContainer = Color(0xFF0842A0)
val DarkOnPrimaryContainer = Color(0xFFD3E3FD)

val DarkSecondary = Color(0xFF6DD58C)
val DarkOnSecondary = Color(0xFF0A3818)
val DarkSecondaryContainer = Color(0xFF0F5223)
val DarkOnSecondaryContainer = Color(0xFFC4EED0)

val DarkTertiary = Color(0xFFFDD663)
val DarkOnTertiary = Color(0xFF3F2E00)
val DarkTertiaryContainer = Color(0xFF5A4300)
val DarkOnTertiaryContainer = Color(0xFFFFEFC3)

val DarkError = Color(0xFFF28B82)
val DarkOnError = Color(0xFF601410)
val DarkErrorContainer = Color(0xFF8C1D18)
val DarkOnErrorContainer = Color(0xFFFCE8E6)

val DarkBackground = Color(0xFF111318)
val DarkOnBackground = Color(0xFFE3E3E3)
val DarkSurface = Color(0xFF111318)
val DarkOnSurface = Color(0xFFE3E3E3)
val DarkSurfaceVariant = Color(0xFF282C34)
val DarkOnSurfaceVariant = Color(0xFFC4C7C5)
val DarkOutline = Color(0xFF8E918F)
val DarkOutlineVariant = Color(0xFF2E323B)

val DarkSurfaceContainerLowest = Color(0xFF0C0E13)
val DarkSurfaceContainerLow = Color(0xFF1A1D24)
val DarkSurfaceContainer = Color(0xFF20242C)
val DarkSurfaceContainerHigh = Color(0xFF2A2F3A)
val DarkSurfaceContainerHighest = Color(0xFF343A47)

// Semantic telemetry accents (balanced for both light and dark surfaces)
val HeartRoseLight = Color(0xFFD9304F)
val HeartRoseDark = Color(0xFFFF758F)
val HeartContainerLight = Color(0xFFFCE4EC)
val HeartContainerDark = Color(0xFF4B1525)

// Curated Material 3 ColorSchemes inspired by Social Share Card background presets

internal val GoogleBlueLightScheme: ColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest
)

internal val GoogleBlueDarkScheme: ColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest
)

// 2. Obsidian (极夜黑 — Deep Carbon Slate & Titanium Ice Accent)
internal val ObsidianLightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF283548),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9E2EF),
    onPrimaryContainer = Color(0xFF0E1622),
    secondary = Color(0xFF1F7A5A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDECE0),
    onSecondaryContainer = Color(0xFF082E20),
    tertiary = Color(0xFF5B6B82),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE2E8F2),
    onTertiaryContainer = Color(0xFF182230),
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    background = Color(0xFFF3F5F8),
    onBackground = Color(0xFF151921),
    surface = Color(0xFFF3F5F8),
    onSurface = Color(0xFF151921),
    surfaceVariant = Color(0xFFE4E8EF),
    onSurfaceVariant = Color(0xFF444C59),
    outline = Color(0xFF727A88),
    outlineVariant = Color(0xFFD2D8E2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFBFD),
    surfaceContainer = Color(0xFFECEFF4),
    surfaceContainerHigh = Color(0xFFE3E7EE),
    surfaceContainerHighest = Color(0xFFD9DFE9)
)

internal val ObsidianDarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFB8CCE8),
    onPrimary = Color(0xFF111C2D),
    primaryContainer = Color(0xFF243247),
    onPrimaryContainer = Color(0xFFDDE8F7),
    secondary = Color(0xFF6DD5A8),
    onSecondary = Color(0xFF083525),
    secondaryContainer = Color(0xFF134E39),
    onSecondaryContainer = Color(0xFFC5F2DE),
    tertiary = Color(0xFF9FB4D1),
    onTertiary = Color(0xFF162436),
    tertiaryContainer = Color(0xFF29394F),
    onTertiaryContainer = Color(0xFFD9E6F7),
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    background = Color(0xFF0B0F15),
    onBackground = Color(0xFFE6EAF0),
    surface = Color(0xFF0B0F15),
    onSurface = Color(0xFFE6EAF0),
    surfaceVariant = Color(0xFF1D2430),
    onSurfaceVariant = Color(0xFFB8C0CE),
    outline = Color(0xFF7D8696),
    outlineVariant = Color(0xFF262F3F),
    surfaceContainerLowest = Color(0xFF070A0E),
    surfaceContainerLow = Color(0xFF121822),
    surfaceContainer = Color(0xFF171F2C),
    surfaceContainerHigh = Color(0xFF1F2939),
    surfaceContainerHighest = Color(0xFF283447)
)

// 3. Forest Green (山野绿 — Alpine Pine & Meadow Sage)
internal val ForestGreenLightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF1B6B44),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC4F0D6),
    onPrimaryContainer = Color(0xFF052917),
    secondary = Color(0xFF3E6A52),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD3ECE0),
    onSecondaryContainer = Color(0xFF0C2A1C),
    tertiary = Color(0xFF9E6408),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFEBC2),
    onTertiaryContainer = Color(0xFF361E00),
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    background = Color(0xFFF3F8F5),
    onBackground = Color(0xFF15201A),
    surface = Color(0xFFF3F8F5),
    onSurface = Color(0xFF15201A),
    surfaceVariant = Color(0xFFE2ECE6),
    onSurfaceVariant = Color(0xFF3F4E45),
    outline = Color(0xFF6D7E74),
    outlineVariant = Color(0xFFCDE0D5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFDFA),
    surfaceContainer = Color(0xFFEBF3EE),
    surfaceContainerHigh = Color(0xFFE1ECE5),
    surfaceContainerHighest = Color(0xFFD6E4DC)
)

internal val ForestGreenDarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF7ADBA5),
    onPrimary = Color(0xFF04381E),
    primaryContainer = Color(0xFF135232),
    onPrimaryContainer = Color(0xFFC4F0D6),
    secondary = Color(0xFF9CD4B6),
    onSecondary = Color(0xFF0B3723),
    secondaryContainer = Color(0xFF1F4E37),
    onSecondaryContainer = Color(0xFFD0F0DE),
    tertiary = Color(0xFFF4C96B),
    onTertiary = Color(0xFF3D2A00),
    tertiaryContainer = Color(0xFF573E05),
    onTertiaryContainer = Color(0xFFFFEBC2),
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    background = Color(0xFF0B1610),
    onBackground = Color(0xFFE0ECE5),
    surface = Color(0xFF0B1610),
    onSurface = Color(0xFFE0ECE5),
    surfaceVariant = Color(0xFF1C2D23),
    onSurfaceVariant = Color(0xFFB5C9BD),
    outline = Color(0xFF7B9184),
    outlineVariant = Color(0xFF243A2D),
    surfaceContainerLowest = Color(0xFF07100B),
    surfaceContainerLow = Color(0xFF112018),
    surfaceContainer = Color(0xFF16281E),
    surfaceContainerHigh = Color(0xFF1E3427),
    surfaceContainerHighest = Color(0xFF274132)
)

// 4. Track Crimson (赛道红 — Motorsport Racing Red & Warm Asphalt)
internal val TrackCrimsonLightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFFB3261E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF410002),
    secondary = Color(0xFF1E7E46),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC7EED4),
    onSecondaryContainer = Color(0xFF062914),
    tertiary = Color(0xFFB25E00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE9C4),
    onTertiaryContainer = Color(0xFF3A1C00),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFCF6F6),
    onBackground = Color(0xFF221919),
    surface = Color(0xFFFCF6F6),
    onSurface = Color(0xFF221919),
    surfaceVariant = Color(0xFFF3E4E3),
    onSurfaceVariant = Color(0xFF534342),
    outline = Color(0xFF857371),
    outlineVariant = Color(0xFFE4CFCB),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFBFB),
    surfaceContainer = Color(0xFFF8ECEB),
    surfaceContainerHigh = Color(0xFFF2E2E0),
    surfaceContainerHighest = Color(0xFFEBD7D5)
)

internal val TrackCrimsonDarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFFF8A80),
    onPrimary = Color(0xFF5C0A09),
    primaryContainer = Color(0xFF8C1D18),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFF72D69A),
    onSecondary = Color(0xFF08391E),
    secondaryContainer = Color(0xFF12522D),
    onSecondaryContainer = Color(0xFFC7EED4),
    tertiary = Color(0xFFFFCA75),
    onTertiary = Color(0xFF422600),
    tertiaryContainer = Color(0xFF603900),
    onTertiaryContainer = Color(0xFFFFE9C4),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF180C0E),
    onBackground = Color(0xFFF0E0E0),
    surface = Color(0xFF180C0E),
    onSurface = Color(0xFFF0E0E0),
    surfaceVariant = Color(0xFF331D20),
    onSurfaceVariant = Color(0xFFD6BEBE),
    outline = Color(0xFF9B8383),
    outlineVariant = Color(0xFF432529),
    surfaceContainerLowest = Color(0xFF12080A),
    surfaceContainerLow = Color(0xFF221114),
    surfaceContainer = Color(0xFF2A161A),
    surfaceContainerHigh = Color(0xFF361D22),
    surfaceContainerHighest = Color(0xFF42252B)
)

// 5. Sunset Amber (日落橙 — Overland Desert Sunset & Warm Copper)
internal val SunsetAmberLightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFFC25E0A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE1C2),
    onPrimaryContainer = Color(0xFF3C1900),
    secondary = Color(0xFF257A4B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC8EED6),
    onSecondaryContainer = Color(0xFF062916),
    tertiary = Color(0xFF8C5319),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFEE5CC),
    onTertiaryContainer = Color(0xFF331900),
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    background = Color(0xFFFCF7F2),
    onBackground = Color(0xFF221A14),
    surface = Color(0xFFFCF7F2),
    onSurface = Color(0xFF221A14),
    surfaceVariant = Color(0xFFF3E6DA),
    onSurfaceVariant = Color(0xFF524438),
    outline = Color(0xFF847466),
    outlineVariant = Color(0xFFE4D3C3),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFBF8),
    surfaceContainer = Color(0xFFF8EEE5),
    surfaceContainerHigh = Color(0xFFF2E4D8),
    surfaceContainerHighest = Color(0xFFEBDBCC)
)

internal val SunsetAmberDarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFFFB46E),
    onPrimary = Color(0xFF4D2200),
    primaryContainer = Color(0xFF7D3A04),
    onPrimaryContainer = Color(0xFFFFE1C2),
    secondary = Color(0xFF74D69D),
    onSecondary = Color(0xFF07381D),
    secondaryContainer = Color(0xFF13522F),
    onSecondaryContainer = Color(0xFFC8EED6),
    tertiary = Color(0xFFF3C48A),
    onTertiary = Color(0xFF422706),
    tertiaryContainer = Color(0xFF5D3A10),
    onTertiaryContainer = Color(0xFFFEE5CC),
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    background = Color(0xFF191009),
    onBackground = Color(0xFFF1E4D8),
    surface = Color(0xFF191009),
    onSurface = Color(0xFFF1E4D8),
    surfaceVariant = Color(0xFF332317),
    onSurfaceVariant = Color(0xFFD5C1B0),
    outline = Color(0xFF9C8776),
    outlineVariant = Color(0xFF443020),
    surfaceContainerLowest = Color(0xFF120B06),
    surfaceContainerLow = Color(0xFF23170E),
    surfaceContainer = Color(0xFF2C1D12),
    surfaceContainerHigh = Color(0xFF382618),
    surfaceContainerHighest = Color(0xFF452F1F)
)

// 6. Pure White / Frost Minimal (纯净白 — Crisp Nordic Ice Minimalism)
internal val PureWhiteLightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF2F5DA8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3ECFA),
    onPrimaryContainer = Color(0xFF0E2549),
    secondary = Color(0xFF1E7F53),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD7F2E3),
    onSecondaryContainer = Color(0xFF072B1A),
    tertiary = Color(0xFF5D6A7E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE8EDF5),
    onTertiaryContainer = Color(0xFF1D2633),
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF161A20),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF161A20),
    surfaceVariant = Color(0xFFF0F3F8),
    onSurfaceVariant = Color(0xFF4B5360),
    outline = Color(0xFF7B8391),
    outlineVariant = Color(0xFFE0E5EE),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAFD),
    surfaceContainer = Color(0xFFF1F4F9),
    surfaceContainerHigh = Color(0xFFE9EEF5),
    surfaceContainerHighest = Color(0xFFE0E6F0)
)

internal val PureWhiteDarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFD6E2F5),
    onPrimary = Color(0xFF162438),
    primaryContainer = Color(0xFF2D3C52),
    onPrimaryContainer = Color(0xFFECF3FC),
    secondary = Color(0xFF7FD9AE),
    onSecondary = Color(0xFF083823),
    secondaryContainer = Color(0xFF165237),
    onSecondaryContainer = Color(0xFFD2F5E3),
    tertiary = Color(0xFFC5D0E0),
    onTertiary = Color(0xFF1E2734),
    tertiaryContainer = Color(0xFF323D4E),
    onTertiaryContainer = Color(0xFFE7EDF6),
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    background = Color(0xFF121417),
    onBackground = Color(0xFFECEFF3),
    surface = Color(0xFF121417),
    onSurface = Color(0xFFECEFF3),
    surfaceVariant = Color(0xFF252930),
    onSurfaceVariant = Color(0xFFC4CAD4),
    outline = Color(0xFF8B929E),
    outlineVariant = Color(0xFF323741),
    surfaceContainerLowest = Color(0xFF0D0F12),
    surfaceContainerLow = Color(0xFF191C21),
    surfaceContainer = Color(0xFF20242A),
    surfaceContainerHigh = Color(0xFF292E36),
    surfaceContainerHighest = Color(0xFF333942)
)
