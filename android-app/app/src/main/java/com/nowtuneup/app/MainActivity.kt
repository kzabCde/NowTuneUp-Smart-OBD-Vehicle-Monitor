package com.nowtuneup.app

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nowtuneup.app.data.dashboard.DashboardDefaults
import com.nowtuneup.app.domain.model.ConnectionState
import com.nowtuneup.app.domain.model.DashboardPreferences
import com.nowtuneup.app.domain.model.RefreshRate
import com.nowtuneup.app.presentation.dashboard.MainViewModel
import com.nowtuneup.app.presentation.theme.NtuTheme
import com.nowtuneup.app.ui.dashboard.DashboardScreen
import com.nowtuneup.app.ui.dashboard.editor.DashboardEditor
import dagger.hilt.android.AndroidEntryPoint
import java.util.Date
import kotlinx.coroutines.delay

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
    val context = LocalContext.current
    val view = LocalView.current
    val focusActive = selectedDestination == 0 && preferences.focusMode

    DisposableEffect(preferences.keepScreenOn, connectionState) {
        val previous = view.keepScreenOn
        view.keepScreenOn = preferences.keepScreenOn && connectionState == ConnectionState.CONNECTED
        onDispose { view.keepScreenOn = previous }
    }

    LaunchedEffect(connectionState, preferences.autoFocusOnConnect) {
        if (connectionState == ConnectionState.CONNECTED && preferences.autoFocusOnConnect) {
            selectedDestination = 0
            viewModel.setFocusMode(true)
        }
    }

    LaunchedEffect(preferences.alertSound, preferences.alertVibration, preferences.muteAlerts) {
        viewModel.alertEvents.collect { alert ->
            if (preferences.muteAlerts) return@collect
            if (preferences.alertSound) {
                val tone = ToneGenerator(AudioManager.STREAM_ALARM, 80)
                tone.startTone(
                    if (alert.severity.name == "CRITICAL") ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD
                    else ToneGenerator.TONE_PROP_BEEP,
                    420,
                )
                delay(450)
                tone.release()
            }
            if (preferences.alertVibration) vibrateAlert(context, alert.severity.name == "CRITICAL")
        }
    }

    NtuTheme(preferences.theme) {
        Scaffold(
            topBar = {
                if (!focusActive) {
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
                                        imageVector = if (connectionState == ConnectionState.CONNECTED) Icons.Default.CheckCircle else Icons.Default.Usb,
                                        contentDescription = "OBD connection",
                                    )
                                },
                            )
                        },
                    )
                }
            },
            bottomBar = {
                if (!focusActive) {
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
                confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("OK") } },
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
    val stats by viewModel.readingStats.collectAsState()
    val alerts by viewModel.activeAlerts.collectAsState()
    val pages = dashboards.ifEmpty { DashboardDefaults.presets }
    val initialPage = pages.indexOfFirst { it.id == preferences.selectedDashboardId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { pages.size })
    var editing by remember { mutableStateOf(false) }
    var controlsVisible by remember(preferences.focusMode) { mutableStateOf(!preferences.focusMode) }

    LaunchedEffect(preferences.selectedDashboardId, pages.size) {
        val target = pages.indexOfFirst { it.id == preferences.selectedDashboardId }.coerceAtLeast(0)
        if (target != pagerState.currentPage) pagerState.scrollToPage(target)
    }
    LaunchedEffect(pagerState.currentPage, pages.size) {
        pages.getOrNull(pagerState.currentPage)?.let { page ->
            if (page.id != preferences.selectedDashboardId) viewModel.selectDashboard(page.id)
        }
    }
    LaunchedEffect(preferences.focusMode, controlsVisible, preferences.controlsAutoHideSeconds) {
        if (preferences.focusMode && controlsVisible && !preferences.touchLock) {
            delay(preferences.controlsAutoHideSeconds * 1_000L)
            controlsVisible = false
        }
    }

    val selected = pages.getOrNull(pagerState.currentPage) ?: pages.first()
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
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = preferences.swipePages && !preferences.touchLock,
            key = { pages[it].id },
        ) { page ->
            DashboardScreen(
                config = pages[page],
                readings = readings,
                readingStats = stats,
                preferences = preferences,
                connectionState = connectionState,
                dtcCount = dtcs.size,
                activeAlerts = alerts,
                pageIndex = page,
                pageCount = pages.size,
                controlsVisible = controlsVisible,
                onConnectionAction = viewModel::toggleConnection,
                onEdit = { editing = true },
                onEnterFocus = {
                    controlsVisible = true
                    viewModel.setFocusMode(true)
                },
                onExitFocus = {
                    controlsVisible = true
                    viewModel.setFocusMode(false)
                },
                onToggleControls = { controlsVisible = !controlsVisible },
                onToggleTouchLock = {
                    controlsVisible = true
                    viewModel.setTouchLock(!preferences.touchLock)
                },
                onResetStats = viewModel::resetReadingStats,
            )
        }
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
            Button(onClick = viewModel::pause, enabled = connectionState == ConnectionState.CONNECTED) { Text("Pause") }
            TextButton(onClick = viewModel::resume, enabled = connectionState == ConnectionState.CONNECTED) { Text("Resume") }
        }

        if (connectionState != ConnectionState.CONNECTED) {
            MessageCard("Vehicle is not connected", "Connect the USB OBD-II adapter before reading live parameters.", "Connect", viewModel::toggleConnection)
        } else if (filtered.isEmpty()) {
            MessageCard(
                if (query.isBlank()) "Waiting for ECU data" else "No matching parameter",
                if (query.isBlank()) "Keep the ignition on while NTU checks supported OBD-II PIDs." else "Try a different search term.",
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
                                    else -> "Updated ${System.currentTimeMillis() - reading.updatedAt} ms ago"
                                },
                            )
                        },
                        trailingContent = { Text(reading.value?.let { "%.1f ${reading.unit}".format(it) } ?: "—") },
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
            Button(onClick = viewModel::scan, enabled = connectionState == ConnectionState.CONNECTED, modifier = Modifier.padding(vertical = 12.dp)) {
                Text("Scan stored DTCs")
            }
        }
        if (connectionState != ConnectionState.CONNECTED) {
            item { MessageCard("Connect before scanning", "Turn the ignition on and establish an OBD-II connection first.", "Connect", viewModel::toggleConnection) }
        } else if (dtcs.isEmpty()) {
            item { MessageCard("No scan results yet", "Run a read-only scan to check stored diagnostic trouble codes.") }
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
            Text("Record local vehicle readings for later review. Peak and Min/Max reset when a new trip starts.")
            Button(onClick = viewModel::toggleTrip, modifier = Modifier.padding(vertical = 12.dp)) { Text("Start / stop recording") }
        }
        if (trips.isEmpty()) {
            item { MessageCard("No recorded trips", "Start recording after connecting to the vehicle.") }
        } else {
            items(trips, key = { it.id }) { trip ->
                ListItem(headlineContent = { Text("Trip #${trip.id}") }, supportingContent = { Text(Date(trip.startTime).toString()) })
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text("Settings", style = MaterialTheme.typography.headlineSmall) }
        item { SettingsHeading("Dashboard pages", "Swipe between saved dashboards while Focus Mode is active.") }
        item {
            ScrollableChips {
                dashboards.forEach { dashboard ->
                    FilterChip(
                        selected = preferences.selectedDashboardId == dashboard.id,
                        onClick = { viewModel.selectDashboard(dashboard.id) },
                        label = { Text(dashboard.name) },
                    )
                }
            }
        }
        item { ToggleSetting("Swipe dashboard pages", "Disable to lock the current page while driving.", preferences.swipePages, viewModel::setSwipePages) }

        item { SettingsHeading("Focus Mode", "Show only gauges and reveal controls with a tap.") }
        item { ToggleSetting("Gauge Focus Mode", "Hides app bars and expands the configured gauges.", preferences.focusMode, viewModel::setFocusMode) }
        item { ToggleSetting("Auto focus after connection", "Enter Focus Mode when ECU initialization succeeds.", preferences.autoFocusOnConnect, viewModel::setAutoFocusOnConnect) }
        item { ToggleSetting("Resume Focus Mode", "Restore the previous driving view after reopening the app.", preferences.resumeFocusMode, viewModel::setResumeFocusMode) }
        item { ToggleSetting("Keep screen on", "Keeps the display awake only while OBD-II is connected.", preferences.keepScreenOn, viewModel::setKeepScreenOn) }
        item { ToggleSetting("Touch lock", "Long-press the dashboard to lock or unlock touches.", preferences.touchLock, viewModel::setTouchLock) }
        item {
            ChoiceSetting("Controls auto-hide", listOf(3, 4, 5, 8), preferences.controlsAutoHideSeconds, { "$it s" }, viewModel::setControlsAutoHideSeconds)
        }

        item { SettingsHeading("Peak and Min/Max", "Statistics reset when a trip starts or Reset peak is tapped.") }
        item { ToggleSetting("Peak hold", "Shows the highest value reached for each PID.", preferences.showPeakHold, viewModel::setShowPeakHold) }
        item { ToggleSetting("Minimum and maximum", "Shows the observed range for each PID.", preferences.showMinMax, viewModel::setShowMinMax) }
        item { Button(onClick = viewModel::resetReadingStats, modifier = Modifier.fillMaxWidth()) { Text("Reset peak and Min/Max") } }

        item { SettingsHeading("Warnings", "Threshold alerts use hysteresis and cooldown to avoid repeated triggers.") }
        item { ToggleSetting("Alert sound", "Plays an audible warning for new alert events.", preferences.alertSound, viewModel::setAlertSound) }
        item { ToggleSetting("Alert vibration", "Vibrates for warning and critical events.", preferences.alertVibration, viewModel::setAlertVibration) }
        item { ToggleSetting("Mute alerts", "Keeps visual warnings but suppresses sound and vibration.", preferences.muteAlerts, viewModel::setMuteAlerts) }
        item { ChoiceSetting("Alert cooldown", listOf(10, 15, 30, 60), preferences.alertCooldownSeconds, { "$it s" }, viewModel::setAlertCooldownSeconds) }
        item { ChoiceSetting("Hysteresis", listOf(1, 2, 3, 5), preferences.hysteresis.toInt(), { "$it units" }) { viewModel.setHysteresis(it.toDouble()) } }

        item { SettingsHeading("Data freshness", "Stale values are replaced with -- instead of being shown as current.") }
        item {
            ChoiceSetting("Mark data stale after", listOf(2, 3, 5, 10), (preferences.staleAfterMillis / 1_000L).toInt(), { "$it s" }) {
                viewModel.setStaleAfterMillis(it * 1_000L)
            }
        }

        item { SettingsHeading("Auto reconnect", "Retries the last USB ELM327 connection after an unexpected disconnect.") }
        item { ToggleSetting("Auto reconnect", "Manual Disconnect never starts a reconnect loop.", preferences.autoReconnect, viewModel::setAutoReconnect) }
        item { ChoiceSetting("Retry interval", listOf(2, 3, 5, 10), preferences.reconnectIntervalSeconds, { "$it s" }, viewModel::setReconnectIntervalSeconds) }
        item { ChoiceSetting("Retry attempts", listOf(3, 5, 10, 15), preferences.reconnectAttempts, { "$it" }, viewModel::setReconnectAttempts) }

        item { SettingsHeading("Appearance", "Theme colors apply immediately without disconnecting OBD-II.") }
        item {
            ScrollableChips {
                DashboardDefaults.themes.forEach { theme ->
                    FilterChip(
                        selected = preferences.theme.name == theme.name,
                        onClick = { viewModel.selectTheme(theme) },
                        label = { Text(theme.name) },
                    )
                }
            }
        }
        item { ToggleSetting("Reduce motion", "Limits gauge animation for comfort and performance.", preferences.reduceMotion, viewModel::setReduceMotion) }
        item { ToggleSetting("Driving mode", "Larger essentials and locks dashboard editing.", preferences.drivingMode, viewModel::setDrivingMode) }

        item { SettingsHeading("Connection", "${connectionState.shortLabel()} · USB ELM327") }
        item {
            Button(onClick = viewModel::toggleConnection, modifier = Modifier.fillMaxWidth()) {
                Text(if (connectionState == ConnectionState.CONNECTED) "Disconnect OBD-II" else "Connect OBD-II")
            }
        }
        item { ChoiceSetting("Refresh rate", RefreshRate.entries, preferences.refreshRate, { it.name.lowercase().replaceFirstChar(Char::uppercase) }, viewModel::setRefreshRate) }
        item {
            Text(
                "NTU 1.3.1 • Android 8+ • Local-first • Read-only OBD-II",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun ToggleSetting(title: String, detail: String, value: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(detail) },
        trailingContent = { Switch(checked = value, onCheckedChange = onChange) },
    )
}

@Composable
private fun <T> ChoiceSetting(title: String, choices: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        ScrollableChips {
            choices.forEach { choice ->
                FilterChip(selected = choice == selected, onClick = { onSelect(choice) }, label = { Text(label(choice)) })
            }
        }
    }
}

@Composable
private fun ScrollableChips(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun SettingsHeading(title: String, detail: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(detail, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MessageCard(title: String, message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
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

private fun vibrateAlert(context: Context, critical: Boolean) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    } ?: return
    val pattern = if (critical) longArrayOf(0, 220, 100, 220) else longArrayOf(0, 180)
    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
}
