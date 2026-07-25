package com.nowtuneup.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nowtuneup.app.domain.model.ConnectionState
import com.nowtuneup.app.domain.model.DashboardConfig
import com.nowtuneup.app.domain.model.DashboardPreferences
import com.nowtuneup.app.domain.model.VehicleReading
import com.nowtuneup.app.ui.dashboard.components.DashboardWidgetView

@Composable
fun DashboardScreen(
    config: DashboardConfig,
    readings: List<VehicleReading>,
    preferences: DashboardPreferences,
    connectionState: ConnectionState,
    dtcCount: Int,
    onConnectionAction: () -> Unit,
    onEdit: () -> Unit,
) {
    val device = LocalConfiguration.current
    val landscape = device.screenWidthDp > device.screenHeightDp
    val layout = if (landscape) config.landscape else config.portrait
    val columns = layout.columns.coerceIn(1, 6)

    Column(modifier = Modifier.fillMaxSize()) {
        ConnectionSetupCard(connectionState, onConnectionAction)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(config.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "${config.mode.name.lowercase().replaceFirstChar { it.uppercase() }} dashboard",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (!preferences.drivingMode) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit dashboard")
                }
            }
        }

        if (layout.widgets.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("This dashboard has no widgets. Open the editor to add one.")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(
                    items = layout.widgets,
                    key = { it.id },
                    span = { widget -> GridItemSpan(widget.columnSpan.coerceIn(1, columns)) },
                ) { widget ->
                    DashboardWidgetView(
                        config = widget,
                        reading = readings.firstOrNull { it.pid == widget.pid },
                        reduceMotion = preferences.reduceMotion || preferences.drivingMode,
                        dtcCount = dtcCount,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionSetupCard(state: ConnectionState, onAction: () -> Unit) {
    val connected = state == ConnectionState.CONNECTED
    val busy = state in setOf(
        ConnectionState.DEVICE_DETECTED,
        ConnectionState.REQUESTING_PERMISSION,
        ConnectionState.CONNECTING,
        ConnectionState.INITIALIZING,
    )
    val title = when (state) {
        ConnectionState.DISCONNECTED -> "Connect your vehicle"
        ConnectionState.DEVICE_DETECTED -> "USB adapter detected"
        ConnectionState.REQUESTING_PERMISSION -> "Allow USB access"
        ConnectionState.CONNECTING -> "Opening ELM327 connection"
        ConnectionState.INITIALIZING -> "Detecting OBD-II protocol"
        ConnectionState.CONNECTED -> "Vehicle connected"
        ConnectionState.ERROR -> "Connection needs attention"
    }
    val detail = when (state) {
        ConnectionState.DISCONNECTED -> "Plug in the USB OBD-II adapter, turn the ignition on, then tap Connect."
        ConnectionState.DEVICE_DETECTED -> "NTU found a compatible USB device and is preparing the serial port."
        ConnectionState.REQUESTING_PERMISSION -> "Approve the Android USB permission dialog to continue."
        ConnectionState.CONNECTING -> "Opening the serial connection to the ELM327 adapter."
        ConnectionState.INITIALIZING -> "Initializing the adapter and asking the ECU for supported data."
        ConnectionState.CONNECTED -> "Live vehicle data is available. Keep your attention on the road."
        ConnectionState.ERROR -> "Check USB OTG, adapter power and ignition, then try again."
    }
    val accent = when (state) {
        ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary
        ConnectionState.ERROR -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.secondary
    }

    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    busy -> CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    connected -> Icon(Icons.Default.CheckCircle, null, tint = accent)
                    state == ConnectionState.ERROR -> Icon(Icons.Default.Warning, null, tint = accent)
                    else -> Icon(Icons.Default.Usb, null, tint = accent)
                }
                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(detail, style = MaterialTheme.typography.bodySmall)
                }
                if (!busy) {
                    Button(onClick = onAction) {
                        Text(if (connected) "Disconnect" else if (state == ConnectionState.ERROR) "Retry" else "Connect")
                    }
                }
            }
            ConnectionSteps(state)
        }
    }
}

@Composable
private fun ConnectionSteps(state: ConnectionState) {
    val activeStep = when (state) {
        ConnectionState.DISCONNECTED -> 0
        ConnectionState.DEVICE_DETECTED, ConnectionState.REQUESTING_PERMISSION -> 1
        ConnectionState.CONNECTING -> 2
        ConnectionState.INITIALIZING -> 3
        ConnectionState.CONNECTED -> 4
        ConnectionState.ERROR -> 0
    }
    val labels = listOf("USB", "Permission", "Adapter", "ECU")

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.forEachIndexed { index, label ->
            val completed = activeStep > index
            val active = activeStep == index && state !in setOf(ConnectionState.DISCONNECTED, ConnectionState.ERROR)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(
                            color = when {
                                completed -> MaterialTheme.colorScheme.primary
                                active -> MaterialTheme.colorScheme.secondary
                                else -> Color.Transparent
                            },
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (completed) "✓" else "${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (completed || active) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
