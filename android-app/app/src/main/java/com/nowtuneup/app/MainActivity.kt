package com.nowtuneup.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nowtuneup.app.data.dashboard.DashboardDefaults
import com.nowtuneup.app.domain.model.ConnectionState
import com.nowtuneup.app.domain.model.DashboardPreferences
import com.nowtuneup.app.domain.model.RefreshRate
import com.nowtuneup.app.domain.model.VehicleReading
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
        setContent {
            NtuApp()
        }
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
    val dashboardPreferences by viewModel.dashboardPreferences.collectAsState()

    NtuTheme(dashboardPreferences.theme) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(text = "NTU", fontWeight = FontWeight.Black)
                            Text(text = "Vehicle Monitoring", fontSize = 11.sp)
                        }
                    },
                    actions = {
                        AssistChip(
                            onClick = { viewModel.toggleConnection() },
                            label = { Text(connectionState.name.replace('_', ' ')) },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (connectionState == ConnectionState.CONNECTED) {
                                        Icons.Default.CheckCircle
                                    } else {
                                        Icons.Default.Usb
                                    },
                                    contentDescription = "Connection status",
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
                onDismissRequest = { viewModel.dismissError() },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissError() }) {
                        Text("OK")
                    }
                },
                title = { Text("Communication error") },
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
    val dtcs by viewModel.dtcs.collectAsState()
    val selected = dashboards.firstOrNull { it.id == preferences.selectedDashboardId }
        ?: dashboards.first()
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
            dtcCount = dtcs.size,
            onEdit = { editing = true },
        )
    }
}

@Composable
fun GaugeCard(reading: VehicleReading) {
    val isPrimaryReading = reading.pid == 0x0C || reading.pid == 0x0D
    Card(
        modifier = Modifier.height(if (isPrimaryReading) 190.dp else 130.dp),
        shape = RoundedCornerShape(20.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(
                    color = Color.DarkGray,
                    startAngle = 145f,
                    sweepAngle = 250f,
                    useCenter = false,
                    style = Stroke(width = 10f, cap = StrokeCap.Round),
                )
                val ratio = reading.value?.let { value ->
                    val minimum = reading.minimum ?: 0.0
                    val maximum = reading.maximum ?: 100.0
                    if (maximum <= minimum) {
                        0f
                    } else {
                        ((value - minimum) / (maximum - minimum)).toFloat().coerceIn(0f, 1f)
                    }
                } ?: 0f
                drawArc(
                    color = Color.Cyan,
                    startAngle = 145f,
                    sweepAngle = 250f * ratio,
                    useCenter = false,
                    style = Stroke(width = 10f, cap = StrokeCap.Round),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = reading.name, fontSize = 12.sp)
                Text(
                    text = reading.value?.let { "%.1f".format(it) } ?: "—",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (reading.supported) reading.unit else "Not supported",
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
fun LiveData(viewModel: MainViewModel) {
    val readings by viewModel.readings.collectAsState()
    var query by remember { mutableStateOf("") }

    Column {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            label = { Text("Search parameters") },
            singleLine = true,
        )
        Row {
            Button(
                onClick = { viewModel.pause() },
                modifier = Modifier.padding(start = 12.dp),
            ) {
                Text("Pause")
            }
            TextButton(onClick = { viewModel.resume() }) {
                Text("Resume")
            }
        }
        LazyColumn {
            items(
                items = readings.filter { it.name.contains(query, ignoreCase = true) },
                key = { it.pid },
            ) { reading ->
                ListItem(
                    headlineContent = { Text(reading.name) },
                    overlineContent = { Text("PID 01%02X".format(reading.pid)) },
                    supportingContent = {
                        Text(
                            if (reading.supported) {
                                "Range ${reading.minimum}–${reading.maximum}"
                            } else {
                                "Not supported"
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

@Composable
fun Diagnostics(viewModel: MainViewModel) {
    val dtcs by viewModel.dtcs.collectAsState()
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Stored diagnostic trouble codes", style = MaterialTheme.typography.headlineSmall)
        Text("Read-only scan. Descriptions may vary by manufacturer.")
        Button(
            onClick = { viewModel.scan() },
            modifier = Modifier.padding(vertical = 12.dp),
        ) {
            Text("Scan stored DTCs")
        }
        if (dtcs.isEmpty()) {
            Text("No scan results")
        } else {
            dtcs.forEach { dtc ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(dtc.code, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text(dtc.description ?: "Manufacturer-specific description unavailable")
                        Text(dtc.status)
                    }
                }
            }
        }
    }
}

@Composable
fun Trips(viewModel: MainViewModel) {
    val trips by viewModel.trips.collectAsState(initial = emptyList())
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Trip history", style = MaterialTheme.typography.headlineSmall)
        Button(
            onClick = { viewModel.toggleTrip() },
            modifier = Modifier.padding(vertical = 12.dp),
        ) {
            Text("Start / stop recording")
        }
        if (trips.isEmpty()) {
            Text("No recorded trips yet")
        } else {
            trips.forEach { trip ->
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

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Text("Dashboard preset", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            dashboards.take(3).forEach { dashboard ->
                FilterChip(
                    selected = preferences.selectedDashboardId == dashboard.id,
                    onClick = { viewModel.selectDashboard(dashboard.id) },
                    label = { Text(dashboard.name) },
                )
            }
        }

        Text(
            "Theme",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DashboardDefaults.themes.take(3).forEach { theme ->
                FilterChip(
                    selected = preferences.theme.name == theme.name,
                    onClick = { viewModel.selectTheme(theme) },
                    label = { Text(theme.name) },
                )
            }
        }

        ListItem(
            headlineContent = { Text("Reduce Motion") },
            supportingContent = { Text("Limits gauge and screen animation") },
            trailingContent = {
                Switch(
                    checked = preferences.reduceMotion,
                    onCheckedChange = viewModel::setReduceMotion,
                )
            },
        )
        ListItem(
            headlineContent = { Text("Driving Mode") },
            supportingContent = { Text("Larger essentials and locks dashboard editing") },
            trailingContent = {
                Switch(
                    checked = preferences.drivingMode,
                    onCheckedChange = viewModel::setDrivingMode,
                )
            },
        )

        Text("Refresh rate", style = MaterialTheme.typography.titleMedium)
        Row {
            RefreshRate.entries.forEach { rate ->
                FilterChip(
                    selected = preferences.refreshRate == rate,
                    onClick = { viewModel.setRefreshRate(rate) },
                    label = { Text(rate.name) },
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
        }

        Text(
            text = "NTU 1.1.0 • Local-first • Read-only OBD-II",
            modifier = Modifier.padding(16.dp),
        )
    }
}
