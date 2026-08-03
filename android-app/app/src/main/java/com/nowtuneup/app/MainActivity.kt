package com.nowtuneup.app

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nowtuneup.app.data.dashboard.DashboardDefaults
import com.nowtuneup.app.domain.model.ConnectionState
import com.nowtuneup.app.domain.model.DashboardConfig
import com.nowtuneup.app.presentation.dashboard.MainViewModel
import com.nowtuneup.app.presentation.theme.NtuTheme
import com.nowtuneup.app.ui.adaptive.AdaptiveLayoutResolver
import com.nowtuneup.app.ui.adaptive.ResolvedDeviceLayout
import com.nowtuneup.app.ui.connection.ConnectionScreen
import com.nowtuneup.app.ui.dashboard.DashboardScreen
import com.nowtuneup.app.ui.dashboard.editor.DashboardEditor
import com.nowtuneup.app.ui.screens.DiagnosticsScreen
import com.nowtuneup.app.ui.screens.LiveDataScreen
import com.nowtuneup.app.ui.screens.SettingsScreen
import dagger.hilt.android.AndroidEntryPoint
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
            Destination("หน้าปัด", Icons.Default.Speed),
            Destination("เชื่อมต่อ", Icons.Default.Bluetooth),
            Destination("ข้อมูลสด", Icons.AutoMirrored.Filled.List),
            Destination("ตรวจปัญหา", Icons.Default.Warning),
            Destination("ตั้งค่า", Icons.Default.Settings),
        )
    }
    var selectedDestination by remember { mutableIntStateOf(0) }
    val connectionState by viewModel.connection.collectAsState()
    val errorMessage by viewModel.error.collectAsState()
    val preferences by viewModel.dashboardPreferences.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    val deviceLayout = AdaptiveLayoutResolver.resolve(
        requested = preferences.adaptiveLayoutProfile,
        screenWidthDp = configuration.screenWidthDp,
        screenHeightDp = configuration.screenHeightDp,
        smallestWidthDp = configuration.smallestScreenWidthDp,
    )
    val headUnitImmersive = deviceLayout == ResolvedDeviceLayout.HEAD_UNIT && preferences.headUnitImmersive
    val chromeHidden = selectedDestination == 0 && (preferences.focusMode || preferences.hudMode || headUnitImmersive)
    val useNavigationRail = deviceLayout != ResolvedDeviceLayout.PHONE
    val activity = context as? Activity

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onAppForegrounded()
                Lifecycle.Event.ON_STOP -> viewModel.onAppBackgrounded()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(preferences.keepScreenOn, connectionState) {
        val previous = view.keepScreenOn
        view.keepScreenOn = preferences.keepScreenOn && connectionState == ConnectionState.CONNECTED
        onDispose { view.keepScreenOn = previous }
    }

    DisposableEffect(preferences.hudMode, preferences.hudBrightnessPercent) {
        val window = activity?.window
        val previous = window?.attributes?.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        if (preferences.hudMode && window != null) {
            val attributes = window.attributes
            attributes.screenBrightness = preferences.hudBrightnessPercent.coerceIn(20, 100) / 100f
            window.attributes = attributes
        }
        onDispose {
            if (window != null) {
                val attributes = window.attributes
                attributes.screenBrightness = previous
                window.attributes = attributes
            }
        }
    }

    DisposableEffect(chromeHidden) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        if (chromeHidden) controller?.hide(WindowInsetsCompat.Type.systemBars())
        else controller?.show(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
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
                if (!chromeHidden) {
                    TopAppBar(
                        title = {
                            Column {
                                Text("NTU", fontWeight = FontWeight.Black)
                                Text(
                                    when (deviceLayout) {
                                        ResolvedDeviceLayout.PHONE -> "ตัวช่วยดูข้อมูลรถ"
                                        ResolvedDeviceLayout.TABLET -> "หน้าปัดสำหรับแท็บเล็ต"
                                        ResolvedDeviceLayout.HEAD_UNIT -> "หน้าปัดสำหรับจอรถ"
                                    },
                                    fontSize = 11.sp,
                                )
                            }
                        },
                        actions = {
                            AssistChip(
                                onClick = { selectedDestination = 1 },
                                label = { Text(connectionState.shortLabel()) },
                                leadingIcon = {
                                    Icon(
                                        if (connectionState == ConnectionState.CONNECTED) Icons.Default.CheckCircle else Icons.Default.Bluetooth,
                                        contentDescription = "เปิดหน้าการเชื่อมต่อ OBD-II",
                                    )
                                },
                            )
                        },
                    )
                }
            },
            bottomBar = {
                if (!chromeHidden && !useNavigationRail) {
                    NavigationBar {
                        destinations.forEachIndexed { index, destination ->
                            NavigationBarItem(
                                selected = selectedDestination == index,
                                onClick = { selectedDestination = index },
                                icon = { Icon(destination.icon, contentDescription = destination.title) },
                                label = { Text(destination.title, fontSize = 9.sp) },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                if (!chromeHidden && useNavigationRail) {
                    NavigationRail {
                        destinations.forEachIndexed { index, destination ->
                            NavigationRailItem(
                                selected = selectedDestination == index,
                                onClick = { selectedDestination = index },
                                icon = { Icon(destination.icon, contentDescription = destination.title) },
                                label = { Text(destination.title, fontSize = 10.sp) },
                            )
                        }
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedDestination) {
                        0 -> Dashboard(viewModel)
                        1 -> ConnectionScreen(viewModel)
                        2 -> LiveDataScreen(viewModel)
                        3 -> DiagnosticsScreen(viewModel)
                        else -> SettingsScreen(viewModel)
                    }
                }
            }
        }

        errorMessage?.let { message ->
            AlertDialog(
                onDismissRequest = viewModel::dismissError,
                confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("ตกลง") } },
                title = { Text("ปัญหาการเชื่อมต่อ") },
                text = { Text(message) },
            )
        }
    }
}

@Composable
fun Dashboard(viewModel: MainViewModel) {
    val dashboards by viewModel.dashboards.collectAsState()
    val preferences by viewModel.dashboardPreferences.collectAsState()
    var editingProfile by remember { mutableStateOf<DashboardConfig?>(null) }

    val profileBeingEdited = editingProfile
    when {
        profileBeingEdited != null -> DashboardEditor(
            config = profileBeingEdited,
            drivingMode = preferences.drivingMode,
            onSave = { saved ->
                viewModel.saveDashboard(saved)
                editingProfile = null
            },
            onCancel = { editingProfile = null },
        )

        dashboards.isEmpty() -> DashboardProfileEmptyState(
            onCreate = { editingProfile = DashboardDefaults.newProfile() },
        )

        else -> DashboardProfilesPager(
            viewModel = viewModel,
            pages = dashboards,
            onEdit = { editingProfile = it },
            onCreate = { editingProfile = DashboardDefaults.newProfile() },
        )
    }
}

@Composable
private fun DashboardProfilesPager(
    viewModel: MainViewModel,
    pages: List<DashboardConfig>,
    onEdit: (DashboardConfig) -> Unit,
    onCreate: () -> Unit,
) {
    val readings by viewModel.readings.collectAsState()
    val preferences by viewModel.dashboardPreferences.collectAsState()
    val connectionState by viewModel.connection.collectAsState()
    val dtcs by viewModel.dtcs.collectAsState()
    val stats by viewModel.readingStats.collectAsState()
    val alerts by viewModel.activeAlerts.collectAsState()
    val initialPage = pages.indexOfFirst { it.id == preferences.selectedDashboardId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { pages.size })
    var controlsVisible by remember(preferences.focusMode) { mutableStateOf(!preferences.focusMode) }

    LaunchedEffect(preferences.selectedDashboardId, pages.size) {
        val target = pages.indexOfFirst { it.id == preferences.selectedDashboardId }.coerceAtLeast(0)
        if (target != pagerState.currentPage && target in pages.indices) pagerState.scrollToPage(target)
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

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = preferences.swipePages && !preferences.touchLock && !preferences.hudMode,
            key = { pages[it].id },
        ) { page ->
            val profile = pages[page]
            DashboardScreen(
                config = profile,
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
                onEdit = { onEdit(profile) },
                onEnterFocus = {
                    controlsVisible = true
                    viewModel.setFocusMode(true)
                },
                onExitFocus = {
                    controlsVisible = true
                    viewModel.setFocusMode(false)
                },
                onExitHud = { viewModel.setHudMode(false) },
                onToggleHudMirror = { viewModel.setHudMirror(!preferences.hudMirror) },
                onToggleControls = { controlsVisible = !controlsVisible },
                onToggleTouchLock = {
                    controlsVisible = true
                    viewModel.setTouchLock(!preferences.touchLock)
                },
                onResetStats = viewModel::resetReadingStats,
            )
        }

        if (!preferences.focusMode && !preferences.hudMode && !preferences.drivingMode) {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("โปรไฟล์ใหม่") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp),
            )
        }
    }
}

@Composable
private fun DashboardProfileEmptyState(onCreate: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Default.Speed, contentDescription = null)
                Text("ยังไม่มีโปรไฟล์หน้าปัด", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "สร้างหน้าปัดในแบบของคุณเอง แล้วเลือกข้อมูล รูปแบบ สี และตำแหน่งที่ต้องการ แอปจะบันทึกไว้ใช้ครั้งต่อไป",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("  สร้างโปรไฟล์แรก")
                }
            }
        }
    }
}

private fun ConnectionState.shortLabel(): String = when (this) {
    ConnectionState.DISCONNECTED -> "ยังไม่เชื่อมต่อ"
    ConnectionState.DEVICE_DETECTED -> "พบอุปกรณ์"
    ConnectionState.REQUESTING_PERMISSION -> "รออนุญาต"
    ConnectionState.CONNECTING -> "กำลังเชื่อมต่อ"
    ConnectionState.INITIALIZING -> "กำลังเตรียมระบบ"
    ConnectionState.CONNECTED -> "เชื่อมต่อแล้ว"
    ConnectionState.ERROR -> "เชื่อมต่อมีปัญหา"
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
