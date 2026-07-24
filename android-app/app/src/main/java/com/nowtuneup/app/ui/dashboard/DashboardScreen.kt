package com.nowtuneup.app.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.nowtuneup.app.domain.model.*
import com.nowtuneup.app.ui.dashboard.components.DashboardWidgetView

@Composable
fun DashboardScreen(config: DashboardConfig, readings: List<VehicleReading>, preferences: DashboardPreferences, dtcCount: Int, onEdit: () -> Unit) {
    val device = LocalConfiguration.current
    val landscape = device.screenWidthDp > device.screenHeightDp
    val layout = if (landscape) config.landscape else config.portrait
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text(config.name, style = MaterialTheme.typography.titleLarge); Text(config.mode.name.lowercase().replaceFirstChar(Char::titlecase), style = MaterialTheme.typography.labelMedium) }
            if (!preferences.drivingMode) IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit dashboard") }
        }
        LazyVerticalGrid(columns = GridCells.Fixed(layout.columns.coerceIn(1, 6)), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(layout.widgets, key = { it.id }) { widget -> DashboardWidgetView(widget, readings.firstOrNull { it.pid == widget.pid }, preferences.reduceMotion || preferences.drivingMode, dtcCount) }
        }
    }
}
