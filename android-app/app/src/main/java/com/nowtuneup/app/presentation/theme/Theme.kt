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
    name = "Graphite", primary = 0xFF52FF8A, secondary = 0xFFFFC66D,
    accent = 0xFF52FF8A, background = 0xFF080B0E, card = 0xFF12191E,
    text = 0xFFF1F6F4, gaugeNeedle = 0xFF52FF8A, border = 0xFF334139,
)
val DaylightTheme = ThemeConfig(
    name = "Light", primary = 0xFF006D38, secondary = 0xFF855300,
    accent = 0xFF006D38, background = 0xFFF4F7F5, card = 0xFFFFFFFF,
    text = 0xFF15231C, gaugeNeedle = 0xFF006D38, gaugeTick = 0xFF40574A,
    warning = 0xFF855300, critical = 0xFFB42332, success = 0xFF006D38, border = 0xFF78897F,
)

internal fun contentColorFor(color: Color): Color =
    if (color.luminance() > 0.179f) Color.Black else Color.White

private val NtuTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 42.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 38.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 30.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 26.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 23.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 22.sp),
)

@Composable
fun NtuTheme(config: ThemeConfig = ThemeConfig(), reduceMotion: Boolean = false, content: @Composable () -> Unit) {
    // Derive every Material surface: the default purple containers must never leak into custom themes.
    val background = Color(config.background)
    val surface = Color(config.card)
    val foreground = Color(config.text)
    val primary = Color(config.primary)
    val secondary = Color(config.secondary)
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
                extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp),
                extraLarge = RoundedCornerShape(30.dp),
            ),
            content = content,
        )
    }
}
