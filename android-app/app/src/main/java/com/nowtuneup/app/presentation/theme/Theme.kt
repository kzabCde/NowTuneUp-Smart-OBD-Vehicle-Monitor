package com.nowtuneup.app.presentation.theme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
private val colors=darkColorScheme(primary=Color(0xFF00E5FF),secondary=Color(0xFFFFB300),background=Color(0xFF090D12),surface=Color(0xFF121923),surfaceVariant=Color(0xFF1B2633),onBackground=Color(0xFFEAF7FA))
@Composable fun NtuTheme(content:@Composable()->Unit)=MaterialTheme(colorScheme=colors,typography=Typography(),content=content)
