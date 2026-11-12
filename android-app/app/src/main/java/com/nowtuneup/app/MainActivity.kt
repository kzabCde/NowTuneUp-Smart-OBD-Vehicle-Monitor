package com.nowtuneup.app

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DashboardCustomize
import com.nowtuneup.app.ui.navigation.*
import com.nowtuneup.app.ui.vehicle.GarageScreen
import com.nowtuneup.app.ui.dashboard.WorkspaceManagerScreen
import com.nowtuneup.app.feature.timeslip.PerformanceHistoryScreen
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import com.nowtuneup.app.ui.components.NtuEmptyState
import com.nowtuneup.app.ui.motion.NowTuneUpLogoMark
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import com.nowtuneup.app.ui.components.NtuPanel as Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.nowtuneup.app.feature.timeslip.TimeSlipScreen
import com.nowtuneup.app.presentation.dashboard.MainViewModel
import com.nowtuneup.app.presentation.theme.NtuTheme
import com.nowtuneup.app.ui.adaptive.AdaptiveLayoutResolver
import com.nowtuneup.app.ui.adaptive.ResolvedDeviceLayout
import com.nowtuneup.app.ui.connection.ConnectionScreen
import com.nowtuneup.app.ui.dashboard.DashboardScreen
import com.nowtuneup.app.ui.dashboard.editor.DashboardEditor
import com.nowtuneup.app.ui.motion.NtuMotion
import com.nowtuneup.app.ui.motion.NowTuneUpSplash
import com.nowtuneup.app.ui.screens.DiagnosticsScreen
import com.nowtuneup.app.ui.screens.LiveDataScreen
import com.nowtuneup.app.ui.screens.SettingsScreen
import com.nowtuneup.app.ui.vehicle.VehicleProfilesScreen
import com.nowtuneup.app.ui.vehicle.rememberVehicleProfileRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { NtuApp() }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NtuApp(viewModel: MainViewModel = hiltViewModel()) {
    var selectedKey by rememberSaveable(key = "primary-destination-v2") { mutableStateOf(AppDestination.HOME.key) }
    val selectedDestination = AppDestination.fromKey(selectedKey)
    var secondaryPage by rememberSaveable { mutableStateOf<String?>(null) }
    var performanceActive by remember { mutableStateOf(false) }
    var openWorkspace by remember { mutableStateOf(false) }
    fun select(destination: AppDestination) {
        if (performanceActive) return
        selectedKey = destination.key
        secondaryPage = null
    }
    fun openGarage(page: String) {
        if (performanceActive) return
        selectedKey = AppDestination.GARAGE.key
        secondaryPage = page
    }
    val connectionState by viewModel.connection.collectAsState()
    val errorMessage by viewModel.error.collectAsState()
    val preferences by viewModel.dashboardPreferences.collectAsState()
    val vehicleRepository = rememberVehicleProfileRepository()
    val vehicleProfiles by vehicleRepository.profiles.collectAsState()
    val activeVehicle = vehicleProfiles.firstOrNull { it.isActive }
    var splashVisible by rememberSaveable { mutableStateOf(true) }
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
    val chromeHidden = selectedDestination == AppDestination.HOME && (preferences.focusMode || preferences.hudMode || headUnitImmersive)
    val useNavigationRail = deviceLayout != ResolvedDeviceLayout.PHONE || configuration.screenWidthDp > configuration.screenHeightDp
    val activity = context as? Activity

    LaunchedEffect(preferences.reduceMotion) {
        if (splashVisible) {
            if (!preferences.reduceMotion) delay(NtuMotion.Splash.toLong())
            splashVisible = false
        }
    }

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
        if (connectionState == ConnectionState.CONNECTED && preferences.autoFocusOnConnect && !performanceActive) {
            selectedKey = AppDestination.HOME.key
            secondaryPage = null
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

    BackHandler(enabled = !splashVisible && (secondaryPage != null || selectedDestination != AppDestination.HOME || chromeHidden)) {
        when {
            secondaryPage != null -> secondaryPage = null
            chromeHidden -> { viewModel.setFocusMode(false); viewModel.setHudMode(false); viewModel.setHeadUnitImmersive(false) }
            else -> select(AppDestination.HOME)
        }
    }

    NtuTheme(preferences.theme, reduceMotion = preferences.reduceMotion) {
        val lightBars = MaterialTheme.colorScheme.background.luminance() > 0.5f
        val legacyNavigationColor = MaterialTheme.colorScheme.surface.toArgb()
        DisposableEffect(lightBars, legacyNavigationColor) {
            activity?.window?.let { window ->
                // Before API 29, edge-to-edge uses an opaque navigation scrim.
                // Match the app palette when its theme differs from Android's theme.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    @Suppress("DEPRECATION")
                    window.navigationBarColor = legacyNavigationColor
                }
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = lightBars
                    isAppearanceLightNavigationBars = lightBars
                }
            }
            onDispose { }
        }
        when {
            splashVisible -> NowTuneUpSplash(reduceMotion = preferences.reduceMotion)

            else -> Scaffold(
                topBar = {
                    if (!chromeHidden) {
                        TopAppBar(
                            navigationIcon = {
                                if (secondaryPage != null) IconButton(onClick = { secondaryPage = null }, enabled = !performanceActive) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "กลับ")
                                } else NowTuneUpLogoMark(Modifier.padding(start = 12.dp, end = 6.dp).size(28.dp),
                                    foreground = MaterialTheme.colorScheme.onSurface,
                                    accent = MaterialTheme.colorScheme.primary,
                                    background = MaterialTheme.colorScheme.surface)
                            },
                            title = {
                                Column {
                                    Text(secondaryPage?.let { when(it) { "connection" -> "การเชื่อมต่อ"; "diagnostics" -> "ตรวจปัญหา"; "live" -> "ข้อมูลสด"; else -> "รถของฉัน" } } ?: selectedDestination.title, style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${activeVehicle?.displayName ?: "NowTuneUp"} · ${connectionState.shortLabel()}", style = MaterialTheme.typography.labelSmall,
                                        color = if (connectionState == ConnectionState.ERROR) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            },
                            actions = {
                                IconButton(onClick = { openGarage("connection") }, enabled = !performanceActive) {
                                    Icon(if (connectionState == ConnectionState.CONNECTED) Icons.Default.CheckCircle else Icons.Default.Bluetooth,
                                        contentDescription = "การเชื่อมต่อ: ${connectionState.shortLabel()}",
                                        tint = if (connectionState == ConnectionState.CONNECTED) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { openGarage("vehicles") }, enabled = !performanceActive) {
                                    Icon(Icons.Default.DirectionsCar, contentDescription = "จัดการโปรไฟล์รถ")
                                }
                            },
                        )
                    }
                },
                bottomBar = {
                    if (!chromeHidden && !useNavigationRail) {
                        AppBottomNavigation(selectedDestination, ::select, enabled = !performanceActive)
                    }
                },
            ) { innerPadding ->
                Row(modifier = Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding)) {
                    if (!chromeHidden && useNavigationRail) {
                        AppNavigationRail(selectedDestination, ::select, enabled = !performanceActive)
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        AnimatedContent(
                            targetState = selectedDestination to secondaryPage,
                            transitionSpec = {
                                val duration = if (preferences.reduceMotion) 0 else 100
                                fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))
                            },
                            label = "NowTuneUp destination",
                        ) { route ->
                            val destination = route.first
                            when (route.second) {
                                "connection" -> ConnectionScreen(viewModel)
                                "diagnostics" -> DiagnosticsScreen(viewModel)
                                "live" -> LiveDataScreen(viewModel)
                                "vehicles" -> VehicleProfilesScreen(repository = vehicleRepository,
                                    onContinue = if (activeVehicle != null) ({ secondaryPage = null }) else null,
                                    showBackAction = true, onBack = { secondaryPage = null })
                                else -> when (destination) {
                                    AppDestination.HOME -> Dashboard(viewModel,
                                        onLiveData = { secondaryPage = "live" },
                                        initiallyManage = openWorkspace,
                                        onManagerOpened = { openWorkspace = false })
                                    AppDestination.PERFORMANCE -> TimeSlipScreen(viewModel,
                                        onOpenHistory = { select(AppDestination.HISTORY) },
                                        onSessionActiveChange = { performanceActive = it })
                                    AppDestination.HISTORY -> PerformanceHistoryScreen(viewModel, onStartPerformance = { select(AppDestination.PERFORMANCE) })
                                    AppDestination.GARAGE -> GarageScreen(viewModel,
                                        onManageVehicles = { secondaryPage = "vehicles" },
                                        onConnection = { secondaryPage = "connection" },
                                        onDiagnostics = { secondaryPage = "diagnostics" },
                                        onLiveData = { secondaryPage = "live" })
                                    AppDestination.SETTINGS -> SettingsScreen(viewModel, onManageDashboards = {
                                        openWorkspace = true; select(AppDestination.HOME)
                                    })
                                }
                            }
                        }
                    }
                }
            }
        }

        errorMessage?.let { message ->
            AlertDialog(
                onDismissRequest = viewModel::dismissError,
                confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("ตกลง") } },
                title = { Text("ดำเนินการไม่สำเร็จ") },
                text = { Text(message) },
            )
        }
    }
}

@Composable
fun Dashboard(viewModel: MainViewModel, onLiveData: () -> Unit = {}, initiallyManage: Boolean = false, onManagerOpened: () -> Unit = {}) {
    var managing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initiallyManage) { if (initiallyManage) { managing = true; onManagerOpened() } }
    val storageIssues by viewModel.dashboardStorageIssues.collectAsState()
    val workspaceReady by viewModel.dashboardWorkspaceReady.collectAsState()
    val dashboards by viewModel.dashboards.collectAsState()
    val preferences by viewModel.dashboardPreferences.collectAsState()
    var editingProfile by remember { mutableStateOf<DashboardConfig?>(null) }

    val profileBeingEdited = editingProfile
    when {
        !workspaceReady -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        managing && profileBeingEdited == null -> WorkspaceManagerScreen(viewModel,
            onCreate = { editingProfile = DashboardDefaults.newProfile() },
            onEdit = { editingProfile = it }, onClose = { managing = false })
        profileBeingEdited != null -> DashboardEditor(
            config = profileBeingEdited,
            drivingMode = preferences.drivingMode,
            onSave = { saved ->
                viewModel.saveDashboard(saved) { editingProfile = null }
            },
            onCancel = { editingProfile = null },
        )

        dashboards.isEmpty() -> DashboardProfileEmptyState(
            onCreate = { editingProfile = DashboardDefaults.newProfile() },
        )

        else -> Column(Modifier.fillMaxSize()) {
            if (!preferences.focusMode && !preferences.hudMode) Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("พื้นที่หน้าปัด", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onLiveData) { Text("ข้อมูลสด") }
                IconButton(onClick = { managing = true }) { Icon(Icons.Default.DashboardCustomize, contentDescription = "จัดการหน้าปัด") }
            }
            Box(Modifier.weight(1f)) { DashboardProfilesPager(
                viewModel = viewModel, pages = dashboards,
                onEdit = { profile ->
                    if (storageIssues.any { it.id == profile.id && it.unreadable }) managing = true
                    else editingProfile = profile
                }) }
        }
    }
}

@Composable
private fun DashboardProfilesPager(
    viewModel: MainViewModel,
    pages: List<DashboardConfig>,
    onEdit: (DashboardConfig) -> Unit,
) {
    val readings by viewModel.readings.collectAsState()
    val preferences by viewModel.dashboardPreferences.collectAsState()
    val connectionState by viewModel.connection.collectAsState()
    val connectionUi by viewModel.connectionUiState.collectAsState()
    val diagnosticOverview by viewModel.diagnosticOverview.collectAsState()
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
                dtcCount = diagnosticOverview?.allDtcs?.size,
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
                simulated = connectionUi.transportType == com.nowtuneup.app.domain.model.ObdTransportType.MOCK,
            )
        }

        val display = LocalConfiguration.current
        if (display.screenWidthDp >= 900 && preferences.headUnitImmersive && !preferences.focusMode && !preferences.hudMode) {
            TextButton(onClick = { viewModel.setHeadUnitImmersive(false) }, modifier = Modifier.align(Alignment.TopEnd)) { Text("ออกจากเต็มจอ") }
        }
    }
}

@Composable
private fun DashboardProfileEmptyState(onCreate: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), contentAlignment = Alignment.Center) {
        NtuEmptyState(
            title = "หน้าปัดในแบบของคุณ",
            detail = "เริ่มจากหน้าปัดว่าง เลือกค่าที่อยากดู แล้วจัดวางเกจ สี และขนาดให้เหมาะกับรถของคุณ",
            icon = Icons.Default.Speed, action = "สร้างโปรไฟล์หน้าปัดแรก", onAction = onCreate,
        )
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
