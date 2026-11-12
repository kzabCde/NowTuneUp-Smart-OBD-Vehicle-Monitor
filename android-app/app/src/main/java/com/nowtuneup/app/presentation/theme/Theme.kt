package com.nowtuneup.app.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nowtuneup.app.domain.model.ThemeConfig
import com.nowtuneup.app.ui.motion.LocalNtuReduceMotion

/** App chrome palettes are independent of the user's saved gauge colors. */
val GraphiteTheme = ThemeConfig(
    name = "Graphite", primary = 0xFF4F8EF7, secondary = 0xFF9AA5B5,
    accent = 0xFF4F8EF7, background = 0xFF0B0D10, card = 0xFF171A20,
    text = 0xFFF3F5F7, gaugeNeedle = 0xFF4F8EF7, gaugeTick = 0xFF9AA5B5, border = 0xFF343B47,
)
val DaylightTheme = ThemeConfig(
    name = "Light", primary = 0xFF245FBA, secondary = 0xFF586474,
    accent = 0xFF4F8EF7, background = 0xFFF5F6F8, card = 0xFFFFFFFF,
    text = 0xFF17202D, gaugeNeedle = 0xFF245FBA, gaugeTick = 0xFF586474,
    warning = 0xFF855300, critical = 0xFFB42332, success = 0xFF24734C, border = 0xFF7B8490,
)

private val legacyGraphite = ThemeConfig(
    name = "Graphite", primary = 0xFF52FF8A, secondary = 0xFFFFC66D,
    accent = 0xFF52FF8A, background = 0xFF080B0E, card = 0xFF12191E,
    text = 0xFFF1F6F4, gaugeNeedle = 0xFF52FF8A, border = 0xFF334139,
)
private val legacyDaylight = ThemeConfig(
    name = "Light", primary = 0xFF006D38, secondary = 0xFF855300,
    accent = 0xFF006D38, background = 0xFFF4F7F5, card = 0xFFFFFFFF,
    text = 0xFF15231C, gaugeNeedle = 0xFF006D38, gaugeTick = 0xFF40574A,
    warning = 0xFF855300, critical = 0xFFB42332, success = 0xFF006D38, border = 0xFF78897F,
)

/** Match complete stock palettes so a customized legacy theme keeps all of its colors. */
internal fun effectiveAppTheme(config: ThemeConfig): ThemeConfig = when (config) {
    legacyGraphite -> GraphiteTheme
    legacyDaylight -> DaylightTheme
    else -> config
}

internal fun contentColorFor(color: Color): Color =
    if (color.luminance() > 0.179f) Color.Black else Color.White

private val NtuTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 22.sp),
)

@Composable
fun NtuTheme(config: ThemeConfig = ThemeConfig(), reduceMotion: Boolean = false, content: @Composable () -> Unit) {
    // Upgrade only the old stock chrome; preserve deliberately customized palettes and gauge colors.
    val chrome = effectiveAppTheme(config)
    // Derive every Material surface: the default purple containers must never leak into custom themes.
    val background = Color(chrome.background)
    val surface = Color(chrome.card)
    val foreground = Color(chrome.text)
    val primary = Color(chrome.primary)
    val secondary = Color(chrome.secondary)
    val light = background.luminance() > 0.5f
    val base = if (light) lightColorScheme() else darkColorScheme()
    val container = lerp(surface, foreground, if (light) 0.04f else 0.055f)
    val primaryContainer = lerp(surface, primary, if (light) 0.12f else 0.16f)
    val secondaryContainer = lerp(surface, secondary, 0.14f)
    val error = if (light) Color(0xFFB42332) else Color(0xFFFF8A91)
    val errorContainer = lerp(surface, error, 0.14f)
    val scheme = base.copy(
        primary = primary, onPrimary = contentColorFor(primary),
        primaryContainer = primaryContainer, onPrimaryContainer = foreground,
        secondary = secondary, onSecondary = contentColorFor(secondary),
        secondaryContainer = secondaryContainer, onSecondaryContainer = foreground,
        tertiary = primary, onTertiary = contentColorFor(primary),
        tertiaryContainer = primaryContainer, onTertiaryContainer = foreground,
        background = background, onBackground = foreground,
        surface = surface, onSurface = foreground, surfaceTint = primary,
        surfaceVariant = container, onSurfaceVariant = lerp(foreground, surface, 0.22f),
        surfaceDim = background, surfaceBright = lerp(surface, foreground, 0.10f),
        surfaceContainerLowest = background, surfaceContainerLow = surface,
        surfaceContainer = container, surfaceContainerHigh = lerp(surface, foreground, 0.08f),
        surfaceContainerHighest = lerp(surface, foreground, 0.12f),
        outline = lerp(surface, foreground, 0.48f), outlineVariant = lerp(surface, foreground, 0.16f),
        error = error, onError = contentColorFor(error),
        errorContainer = errorContainer, onErrorContainer = foreground,
        inverseSurface = foreground, inverseOnSurface = surface,
        inversePrimary = if (light) GraphiteTheme.primary.let(::Color) else DaylightTheme.primary.let(::Color),
    )
    CompositionLocalProvider(LocalNtuReduceMotion provides reduceMotion) {
        MaterialTheme(
            colorScheme = scheme,
            typography = NtuTypography,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(8.dp),
                medium = RoundedCornerShape(10.dp), large = RoundedCornerShape(12.dp),
                extraLarge = RoundedCornerShape(16.dp),
            ),
            content = content,
        )
    }
}
