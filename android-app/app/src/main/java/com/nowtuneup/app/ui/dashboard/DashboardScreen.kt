package com.nowtuneup.app.ui.dashboard

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import com.nowtuneup.app.ui.components.NtuPanel as Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nowtuneup.app.domain.model.AlertSeverity
import com.nowtuneup.app.domain.model.ConnectionState
import com.nowtuneup.app.domain.model.DashboardAlert
import com.nowtuneup.app.domain.model.DashboardConfig
import com.nowtuneup.app.domain.model.DashboardPreferences
import com.nowtuneup.app.domain.model.DataFreshness
import com.nowtuneup.app.domain.model.ReadingStats
import com.nowtuneup.app.domain.model.VehicleReading
import com.nowtuneup.app.ui.adaptive.AdaptiveLayoutResolver
import com.nowtuneup.app.ui.adaptive.ResolvedDeviceLayout
import com.nowtuneup.app.ui.dashboard.components.DrivingDashboardWidget
import com.nowtuneup.app.ui.hud.HudDashboard
import com.nowtuneup.app.util.DisplayReadingAdapter
import kotlinx.coroutines.delay

@Composable
fun DashboardScreen(
    config: DashboardConfig,
    readings: List<VehicleReading>,
    readingStats: Map<Int, ReadingStats>,
    preferences: DashboardPreferences,
    connectionState: ConnectionState,
    dtcCount: Int,
    activeAlerts: List<DashboardAlert>,
    pageIndex: Int,
    pageCount: Int,
    controlsVisible: Boolean,
    onConnectionAction: () -> Unit,
    onEdit: () -> Unit,
    onEnterFocus: () -> Unit,
    onExitFocus: () -> Unit,
    onExitHud: () -> Unit,
    onToggleHudMirror: () -> Unit,
    onToggleControls: () -> Unit,
    onToggleTouchLock: () -> Unit,
    onResetStats: () -> Unit,
) {
    if (preferences.hudMode) {
        HudDashboard(
            readings = readings,
            preferences = preferences,
            connectionState = connectionState,
            activeAlerts = activeAlerts,
            onExitHud = onExitHud,
            onToggleMirror = onToggleHudMirror,
            onToggleTouchLock = onToggleTouchLock,
        )
        return
    }

    val device = LocalConfiguration.current
    val landscape = device.screenWidthDp > device.screenHeightDp
    val deviceLayout = AdaptiveLayoutResolver.resolve(
        requested = preferences.adaptiveLayoutProfile,
        screenWidthDp = device.screenWidthDp,
        screenHeightDp = device.screenHeightDp,
        smallestWidthDp = device.smallestScreenWidthDp,
    )
    val layout = if (landscape) config.landscape else config.portrait
    val columns = AdaptiveLayoutResolver.dashboardColumns(layout.columns, deviceLayout, landscape)
    val headUnitImmersive = deviceLayout == ResolvedDeviceLayout.HEAD_UNIT && preferences.headUnitImmersive
    val immersiveDashboard = preferences.focusMode || headUnitImmersive
    val compact = landscape || deviceLayout != ResolvedDeviceLayout.PHONE
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(preferences.focusMode, preferences.touchLock) {
                detectTapGestures(
                    onTap = {
                        if (preferences.focusMode && !preferences.touchLock) onToggleControls()
                    },
                    onLongPress = {
                        if (preferences.focusMode) onToggleTouchLock()
                    },
                )
            },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!immersiveDashboard) {
                ConnectionSetupCard(connectionState, onConnectionAction, compact = compact)
                DashboardHeader(
                    config = config,
                    landscape = landscape,
                    columns = columns,
                    deviceLayout = deviceLayout,
                    drivingMode = preferences.drivingMode,
                    onEdit = onEdit,
                    onEnterFocus = onEnterFocus,
                )
            }

            activeAlerts.firstOrNull()?.let { alert ->
                AlertBanner(alert, compact = immersiveDashboard || compact)
            }

            if (layout.widgets.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("This orientation has no widgets. Open the editor or copy the other layout.")
                }
            } else {
                val spacing = when (deviceLayout) {
                    ResolvedDeviceLayout.PHONE -> if (immersiveDashboard || landscape) 6.dp else 10.dp
                    ResolvedDeviceLayout.TABLET -> 10.dp
                    ResolvedDeviceLayout.HEAD_UNIT -> 12.dp
                }
                val padding = when (deviceLayout) {
                    ResolvedDeviceLayout.PHONE -> if (immersiveDashboard || landscape) 6.dp else 12.dp
                    ResolvedDeviceLayout.TABLET -> 14.dp
                    ResolvedDeviceLayout.HEAD_UNIT -> 16.dp
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(padding),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    items(
                        items = layout.widgets,
                        key = { it.id },
                        span = { widget -> GridItemSpan(widget.columnSpan.coerceIn(1, columns)) },
                    ) { widget ->
                        val nativeReading = readings.firstOrNull { it.pid == widget.pid }
                        val displayReading = DisplayReadingAdapter.reading(nativeReading, widget.unit)
                        val displayStats = DisplayReadingAdapter.stats(
                            stats = readingStats[widget.pid],
                            sourceUnit = nativeReading?.unit.orEmpty(),
                            target = widget.unit,
                        )
                        DrivingDashboardWidget(
                            config = widget,
                            reading = displayReading,
                            stats = displayStats,
                            freshness = readingFreshness(nativeReading, connectionState, now, preferences),
                            reduceMotion = preferences.reduceMotion || preferences.drivingMode,
                            dtcCount = dtcCount,
                            showPeakHold = preferences.showPeakHold,
                            showMinMax = preferences.showMinMax,
                        )
                    }
                }
            }
        }

        if (immersiveDashboard) {
            CompactConnectionIndicator(
                state = connectionState,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            )
            PageIndicator(
                pageIndex = pageIndex,
                pageCount = pageCount,
                modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
            )
        }
        if (preferences.focusMode && controlsVisible) {
            FocusControls(
                touchLocked = preferences.touchLock,
                onToggleTouchLock = onToggleTouchLock,
                onResetStats = onResetStats,
                onExitFocus = onExitFocus,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun DashboardHeader(
    config: DashboardConfig,
    landscape: Boolean,
    columns: Int,
    deviceLayout: ResolvedDeviceLayout,
    drivingMode: Boolean,
    onEdit: () -> Unit,
    onEnterFocus: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = if (landscape) 2.dp else 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                config.name,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = if (landscape) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "${deviceLayout.label()} · $columns คอลัมน์",
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onEnterFocus) { Text("โฟกัส") }
            if (!drivingMode) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "แก้ไขหน้าปัด")
                }
            }
        }
    }
}

@Composable
private fun FocusControls(
    touchLocked: Boolean,
    onToggleTouchLock: () -> Unit,
    onResetStats: () -> Unit,
    onExitFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.78f),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onToggleTouchLock) { Text(if (touchLocked) "ปลดล็อก" else "ล็อก", color = Color.White) }
            TextButton(onClick = onResetStats) { Text("รีเซ็ต Min/Max", color = Color.White) }
            TextButton(onClick = onExitFocus) { Text("ออก", color = Color.White) }
        }
    }
}

@Composable
private fun CompactConnectionIndicator(state: ConnectionState, modifier: Modifier = Modifier) {
    val color = when (state) {
        ConnectionState.CONNECTED -> Color(0xFF4CAF50)
        ConnectionState.ERROR -> Color(0xFFFF5252)
        ConnectionState.DISCONNECTED -> Color(0xFF90A4AE)
        else -> Color(0xFFFFC107)
    }
    Surface(modifier = modifier, color = Color.Black.copy(alpha = 0.62f), shape = CircleShape) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Text("  ${state.shortLabel()}", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun PageIndicator(pageIndex: Int, pageCount: Int, modifier: Modifier = Modifier) {
    if (pageCount <= 1) return
    Surface(modifier = modifier, color = Color.Black.copy(alpha = 0.55f), shape = CircleShape) {
        Text(
            text = "${pageIndex + 1} / $pageCount",
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun AlertBanner(alert: DashboardAlert, compact: Boolean) {
    val color = if (alert.severity == AlertSeverity.CRITICAL) Color(0xFFD50000) else Color(0xFFFFA000)
    Surface(color = color, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "${alert.severity.name}: ${alert.title} ${"%.1f".format(alert.value)} ${alert.unit.name.lowercase()}",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = if (compact) 5.dp else 9.dp),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun readingFreshness(
    reading: VehicleReading?,
    connectionState: ConnectionState,
    now: Long,
    preferences: DashboardPreferences,
): DataFreshness {
    if (connectionState != ConnectionState.CONNECTED) {
        return if (connectionState in setOf(
                ConnectionState.CONNECTING,
                ConnectionState.INITIALIZING,
                ConnectionState.DEVICE_DETECTED,
            )
        ) {
            DataFreshness.RECONNECTING
        } else {
            DataFreshness.NO_DATA
        }
    }
    if (reading == null) return DataFreshness.NO_DATA
    if (!reading.supported) return DataFreshness.UNSUPPORTED
    if (reading.value == null) return DataFreshness.NO_DATA
    val age = (now - reading.updatedAt).coerceAtLeast(0L)
    return when {
        age <= preferences.delayedAfterMillis -> DataFreshness.LIVE
        age <= preferences.staleAfterMillis -> DataFreshness.DELAYED
        else -> DataFreshness.STALE
    }
}

@Composable
private fun ConnectionSetupCard(state: ConnectionState, onAction: () -> Unit, compact: Boolean) {
    val connected = state == ConnectionState.CONNECTED
    val busy = state in setOf(
        ConnectionState.DEVICE_DETECTED,
        ConnectionState.REQUESTING_PERMISSION,
        ConnectionState.CONNECTING,
        ConnectionState.INITIALIZING,
    )
    val title = when (state) {
        ConnectionState.DISCONNECTED -> "เชื่อมต่อรถของคุณ"
        ConnectionState.DEVICE_DETECTED -> "พบอะแดปเตอร์แล้ว"
        ConnectionState.REQUESTING_PERMISSION -> "อนุญาตการเข้าถึงอุปกรณ์"
        ConnectionState.CONNECTING -> "กำลังเชื่อมต่อ ELM327"
        ConnectionState.INITIALIZING -> "กำลังเตรียมข้อมูลรถ"
        ConnectionState.CONNECTED -> "เชื่อมต่อรถแล้ว"
        ConnectionState.ERROR -> "ตรวจสอบการเชื่อมต่อ"
    }
    val detail = when (state) {
        ConnectionState.DISCONNECTED -> "เปิดสวิตช์กุญแจ เลือก Bluetooth หรือ USB แล้วกดเชื่อมต่อ"
        ConnectionState.DEVICE_DETECTED -> "พบอุปกรณ์ที่พร้อมให้เชื่อมต่อ"
        ConnectionState.REQUESTING_PERMISSION -> "อนุญาตการเข้าถึงอุปกรณ์ในหน้าต่างของ Android"
        ConnectionState.CONNECTING -> "กำลังเปิดการเชื่อมต่อกับอะแดปเตอร์"
        ConnectionState.INITIALIZING -> "กำลังเตรียมอะแดปเตอร์และตรวจสอบ ECU"
        ConnectionState.CONNECTED -> "พร้อมอ่านข้อมูลสดที่รถรองรับ"
        ConnectionState.ERROR -> "ตรวจสอบอะแดปเตอร์ Bluetooth หรือ USB และสวิตช์กุญแจ แล้วลองอีกครั้ง"
    }
    val accent = when (state) {
        ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary
        ConnectionState.ERROR -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.secondary
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = if (compact) 4.dp else 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 10.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    busy -> CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    connected -> Icon(Icons.Default.CheckCircle, null, tint = accent)
                    state == ConnectionState.ERROR -> Icon(Icons.Default.Warning, null, tint = accent)
                    else -> Icon(Icons.Default.Usb, null, tint = accent)
                }
                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (!compact || state != ConnectionState.CONNECTED) {
                        Text(detail, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (!busy) {
                    Button(onClick = onAction) {
                        Text(if (connected) "ตัดการเชื่อมต่อ" else if (state == ConnectionState.ERROR) "ลองอีกครั้ง" else "เชื่อมต่อ")
                    }
                }
            }
            if (!compact) ConnectionSteps(state)
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
                    modifier = Modifier.size(22.dp).background(
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
                        color = when { completed -> MaterialTheme.colorScheme.onPrimary; active -> MaterialTheme.colorScheme.onSecondary; else -> MaterialTheme.colorScheme.onSurfaceVariant },
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun ResolvedDeviceLayout.label(): String = when (this) {
    ResolvedDeviceLayout.PHONE -> "มือถือ"
    ResolvedDeviceLayout.TABLET -> "แท็บเล็ต"
    ResolvedDeviceLayout.HEAD_UNIT -> "จอรถ"
}

private fun ConnectionState.shortLabel(): String = when (this) {
    ConnectionState.DISCONNECTED -> "Disconnected"
    ConnectionState.DEVICE_DETECTED -> "USB detected"
    ConnectionState.REQUESTING_PERMISSION -> "Permission"
    ConnectionState.CONNECTING -> "Connecting"
    ConnectionState.INITIALIZING -> "Initializing"
    ConnectionState.CONNECTED -> "Connected"
    ConnectionState.ERROR -> "Error"
}
