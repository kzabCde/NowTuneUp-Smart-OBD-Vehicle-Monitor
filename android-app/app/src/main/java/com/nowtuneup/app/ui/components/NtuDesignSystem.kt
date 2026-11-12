package com.nowtuneup.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Shared 4 dp spacing grid; UI state colors always come from the effective Material palette. */
object NtuSpacing {
    val xsmall = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val section = 24.dp
}

@Composable
fun NtuMetricValue(value: String, unit: String, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(NtuSpacing.small), verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
        Text(value, style = MaterialTheme.typography.headlineLarge, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
        Text(unit, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
    }
}

@Composable
fun NtuSectionTitle(title: String, detail: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(NtuSpacing.xsmall)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
