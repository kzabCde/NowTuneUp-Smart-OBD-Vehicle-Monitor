package com.nowtuneup.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nowtuneup.app.data.dashboard.DashboardDefaults
import com.nowtuneup.app.domain.model.ConnectionState
import com.nowtuneup.app.domain.model.RefreshRate
import com.nowtuneup.app.presentation.dashboard.MainViewModel
import com.nowtuneup.app.presentation.theme.NtuTheme
import com.nowtuneup.app.ui.dashboard.DashboardScreen
import com.nowtuneup.app.ui.dashboard.editor.DashboardEditor
import dagger.hilt.android.AndroidEntryPoint
import java.util.Date

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NtuApp() }
    }
}

data class Destination(val title: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NtuApp(viewModel: MainViewModel = hiltViewModel()) {
    val destinations = remember {
        listOf(
            Destination("Dashboard", Icons.Default.Speed),
            Destination("Live Data", Icons.Default.List),
            Destination("Diagnostics", Icons.Default.Warning),
            Destination("Trips", Icons.Default.Route),
            Destination("Settings", Icons.Default.Settings),
        )
    }
    var selectedDestination by remember { mutableIntStateOf(0) }
    val connectionState by viewModel.connection.collectAsState()
    val errorMessage by viewModel.error.collectAsState()
    val preferences by viewModel.dashboardPreferences.collectAsState()

    NtuTheme(preferences.theme) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("NTU", fontWeight = FontWeight.Black)
                            Text("Vehicle monitor", fontSize = 11.sp)
                        }
                    },
                    actions = {
                        AssistChip(
                            onClick = viewModel::toggleConnection,
                            label = { Text(connectionState.shortLabel()) },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (connectionState == ConnectionState.CONNECTED) {
                                        Icons.Default.CheckCircle
                                    } else {
                                        Icons.Default.Usb
                                    },
                                    contentDescription = "OBD connection",
                                )
                            },
                        )
                    },
                )
            },
            bottomBar = {
                NavigationBar {
                    destinations.forEachIndexed { index, destination ->
                        NavigationBarItem(
                            selected = selectedDestination == index,
                            onClick = { selectedDestination = index },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.title, fontSize = 10.sp) },
                        )
                    }
                }
            },
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                when (selectedDestination) {
                    0 -> Dashboard(viewModel)
                    1 -> LiveData(viewModel)
                    2 -> Diagnostics(viewModel)
                    3 -> Trips(viewModel)
                    else -> Settings(viewModel)
                }
            }
        }

        errorMessage?.let { message ->
            AlertDialog(
                onDismissRequest = viewModel::dismissError,
                confirmButton = {
                    TextButton(onClick = viewModel::dismissError) { Text("OK") }
                },
                title = { Text("Communication problem") },
                text = { Text(message) },
            )
        }
    }
}

@Composable
fun Dashboard(viewModel: MainViewModel) {
    val readings by viewModel.readings.collectAsState()
    val dashboards by viewModel.dashboards.collectAsState()
    val preferences by viewModel.dashboardPreferences.collectAsState()
    val connectionState by viewModel.connection.collectAsState()
    val dtcs by viewModel.dtcs.collectAsState()
    val selected = dashboards.firstOrNull { it.id == preferences.selectedDashboardId }
        ?: dashboards.firstOrNull()
        ?: DashboardDefaults.presets.first()
    var editing by remember { mutableStateOf(false) }

    if (editing) {
        DashboardEditor(
            config = selected,
            drivingMode = preferences.drivingMode,
            onSave = {
                viewModel.saveDashboard(it)
                editing = false
            },
            onCancel = { editing = false },
        )
    } else {
        DashboardScreen(
            config = selected,
            readings = readings,
            preferences = preferences,
            connectionState = connectionState,
            dtcCount = dtcs.size,
            onConnectionAction = viewModel::toggleConnection,
            onEdit = { editing = true },
        )
    }
}

@Composable
fun LiveData(viewModel: MainViewModel) {
    val readings by viewModel.readings.collectAsState()
    val connectionState by viewModel.connection.collectAsState()
    var query by remember { mutableStateOf("") }
    val filtered = readings.filter { it.name.contains(query, ignoreCase = true) }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            label = { Text("Search vehicle data") },
            singleLine = true,
        )
        Row(modifier = Modifier.padding(horizontal = 12.dp)) {
            Button(
                onClick = viewModel::pause,
                enabled = connectionState == ConnectionState.CONNECTED,
            ) { Text("Pause") }
            TextButton(
                onClick = viewModel::resume,
                enabled = connectionState == ConnectionState.CONNECTED,
            ) { Text("Resume") }
        }

        if (connectionState != ConnectionState.CONNECTED) {
            MessageCard(
                title = "Vehicle is not connected",
                message = "Connect the USB OBD-II adapter before reading live parameters.",
                actionLabel = "Connect",
                onAction = viewModel::toggleConnection,
            )
        } else if (filtered.isEmpty()) {
            MessageCard(
                title = if (query.isBlank()) "Waiting for ECU data" else "No matching parameter",
                message = if (query.isBlank()) {
                    "Keep the ignition on while NTU checks the supported OBD-II PIDs."
                } else {
                    "Try a different search term."
                },
            )
        } else {
            LazyColumn {
                items(filtered, key = { it.pid }) { reading ->
                    ListItem(
                        headlineContent = { Text(reading.name) },
                        overlineContent = { Text("PID 01%02X".format(reading.pid)) },
                        supportingContent = {
                            Text(
                                when {
                                    !reading.supported -> "Not supported by this vehicle"
                                    reading.value == null -> "Waiting for a valid response"
                                    else -> "Range ${reading.minimum ?: "—"}–${reading.maximum ?: "—"}"
                                },
                            )
                        },
                        trailingContent = {
                            Text(reading.value?.let { "%.1f ${reading.unit}".format(it) } ?: "—")
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun Diagnostics(viewModel: MainViewModel) {
    val dtcs by viewModel.dtcs.collectAsState()
    val connectionState by viewModel.connection.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Stored diagnostic trouble codes", style = MaterialTheme.typography.headlineSmall)
            Text("Read-only scan. NTU never clears codes or changes the ECU.")
            Button(
                onClick = viewModel::scan,
                enabled = connectionState == ConnectionState.CONNECTED,
                modifier = Modifier.padding(vertical = 12.dp),
            ) { Text("Scan stored DTCs") }
        }
        if (connectionState != ConnectionState.CONNECTED) {
            item {
                MessageCard(
                    title = "Connect before scanning",
                    message = "Turn the ignition on and establish an OBD-II connection first.",
                    actionLabel = "Connect",
                    onAction = viewModel::toggleConnection,
                )
            }
        } else if (dtcs.isEmpty()) {
            item {
                MessageCard(
                    title = "No scan results yet",
                    message = "Run a read-only scan to check stored diagnostic trouble codes.",
                )
            }
        } else {
            items(dtcs, key = { it.code }) { dtc ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(dtc.code, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text(dtc.description ?: "Manufacturer-specific description unavailable")
                        Text(dtc.status, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun Trips(viewModel: MainViewModel) {
    val trips by viewModel.trips.collectAsState(initial = emptyList())
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Trip history", style = MaterialTheme.typography.headlineSmall)
            Text("Record local vehicle readings for later review.")
            Button(
                onClick = viewModel::toggleTrip,
                modifier = Modifier.padding(vertical = 12.dp),
            ) { Text("Start / stop recording") }
        }
        if (trips.isEmpty()) {
            item { MessageCard("No recorded trips", "Start recording after connecting to the vehicle.") }
        } else {
            items(trips, key = { it.id }) { trip ->
                ListItem(
                    headlineContent = { Text("Trip #${trip.id}") },
                    supportingContent = { Text(Date(trip.startTime).toString()) },
                )
            }
        }
    }
}

@Composable
fun Settings(viewModel: MainViewModel) {
    val preferences by viewModel.dashboardPreferences.collectAsState()
    val dashboards by viewModel.dashboards.collectAsState()
    val connectionState by viewModel.connection.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text("Settings", style = MaterialTheme.typography.headlineSmall) }
        item { SettingsHeading("Dashboard", "Choose the information layout used while driving.") }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                dashboards.forEach { dashboard ->
                    FilterChip(
                        selected = preferences.selectedDashboardId == dashboard.id,
                        onClick = { viewModel.selectDashboard(dashboard.id) },
                        label = { Text(dashboard.name) },
                    )
                }
            }
        }

        item { SettingsHeading("Appearance", "Theme colors apply immediately without disconnecting OBD-II.") }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DashboardDefaults.themes.forEach { theme ->
                    FilterChip(
                        selected = preferences.theme.name == theme.name,
                        onClick = { viewModel.selectTheme(theme) },
                        label = { Text(theme.name) },
                    )
                }
            }
        }
        item {
            ListItem(
                headlineContent = { Text("Reduce motion") },
                supportingContent = { Text("Limits gauge animation for comfort and performance") },
                trailingContent = {
                    Switch(preferences.reduceMotion, viewModel::setReduceMotion)
                },
            )
        }
        item {
            ListItem(
                headlineContent = { Text("Driving mode") },
                supportingContent = { Text("Larger essentials and locks dashboard editing") },
                trailingContent = {
                    Switch(preferences.drivingMode, viewModel::setDrivingMode)
                },
            )
        }

        item { SettingsHeading("Connection", "${connectionState.shortLabel()} · USB ELM327") }
        item {
            Button(onClick = viewModel::toggleConnection, modifier = Modifier.fillMaxWidth()) {
                Text(if (connectionState == ConnectionState.CONNECTED) "Disconnect OBD-II" else "Connect OBD-II")
            }
        }
        item { Text("Refresh rate", style = MaterialTheme.typography.titleMedium) }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RefreshRate.entries.forEach { rate ->
                    FilterChip(
                        selected = preferences.refreshRate == rate,
                        onClick = { viewModel.setRefreshRate(rate) },
                        label = { Text(rate.name.lowercase().replaceFirstChar(Char::uppercase)) },
                    )
                }
            }
        }
        item {
            Text(
                "NTU 1.2.2 • Android 8+ • Local-first • Read-only OBD-II",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun SettingsHeading(title: String, detail: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(detail, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MessageCard(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodyMedium)
            if (actionLabel != null && onAction != null) {
                Row {
                    Button(onClick = onAction) { Text(actionLabel) }
                    Spacer(Modifier.width(8.dp))
                }
            }
        }
    }
}

private fun ConnectionState.shortLabel(): String = when (this) {
    ConnectionState.DISCONNECTED -> "Disconnected"
    ConnectionState.DEVICE_DETECTED -> "USB detected"
    ConnectionState.REQUESTING_PERMISSION -> "USB permission"
    ConnectionState.CONNECTING -> "Connecting"
    ConnectionState.INITIALIZING -> "Initializing"
    ConnectionState.CONNECTED -> "Connected"
    ConnectionState.ERROR -> "Connection error"
}
