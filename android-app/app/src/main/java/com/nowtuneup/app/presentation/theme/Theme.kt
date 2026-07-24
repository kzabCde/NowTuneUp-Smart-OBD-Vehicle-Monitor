package com.nowtuneup.app.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NtuColorScheme = darkColorScheme(
    primary = Color(0xFF00E5FF),
    secondary = Color(0xFFFFB300),
    background = Color(0xFF090D12),
    surface = Color(0xFF121923),
    surfaceVariant = Color(0xFF1B2633),
    onPrimary = Color(0xFF001F24),
    onSecondary = Color(0xFF241A00),
    onBackground = Color(0xFFEAF7FA),
    onSurface = Color(0xFFEAF7FA),
)

@Composable
fun NtuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NtuColorScheme,
        typography = Typography(),
        content = content,
    )
}
