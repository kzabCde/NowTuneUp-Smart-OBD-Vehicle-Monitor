package com.nowtuneup.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.nowtuneup.app.domain.model.AlertSeverity
import com.nowtuneup.app.domain.model.ConnectionState
import com.nowtuneup.app.domain.model.DashboardAlert
import com.nowtuneup.app.domain.model.DashboardConfig
import com.nowtuneup.app.domain.model.DashboardLayout
import com.nowtuneup.app.domain.model.DashboardPreferences
import com.nowtuneup.app.domain.model.DataFreshness
import com.nowtuneup.app.domain.model.ReadingStats
import com.nowtuneup.app.domain.model.VehicleReading
import com.nowtuneup.app.ui.adaptive.AdaptiveLayoutResolver
import com.nowtuneup.app.ui.adaptive.ResolvedDeviceLayout
import com.nowtuneup.app.ui.dashboard.components.DrivingDashboardWidget
import com.nowtuneup.app.util.DisplayReadingAdapter
import kotlinx.coroutines.delay

/**
 * Runtime renderer for the v2 Custom HUD.
 *
 * There is no separate hard-coded HUD anymore. The same user-created profile is rendered in normal,
 * focus and mirrored projection modes, so Speed/RPM/Turbo are never forced into a fixed layout.
 */
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
    val device = LocalConfiguration.current
    val landscape = device.screenWidthDp > device.screenHeightDp
    val deviceLayout = AdaptiveLayoutResolver.resolve(
        requested = preferences.adaptiveLayoutProfile,
        screenWidthDp = device.screenWidthDp,
        screenHeightDp = device.screenHeightDp,
        smallestWidthDp = device.smallestScreenWidthDp,
    )
    val layout = if (landscape) config.landscape else config.portrait
    val headUnitImmersive = deviceLayout == ResolvedDeviceLayout.HEAD_UNIT && preferences.headUnitImmersive
    val immersive = preferences.focusMode || preferences.hudMode || headUnitImmersive
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
            .background(if (preferences.hudMode) Color.Black else MaterialTheme.colorScheme.background)
            .pointerInput(preferences.focusMode, preferences.hudMode, preferences.touchLock) {
                detectTapGestures(
                    onTap = {
                        if ((preferences.focusMode || preferences.hudMode) && !preferences.touchLock) {
                            onToggleControls()
                        }
                    },
                    onLongPress = {
                        if (preferences.focusMode || preferences.hudMode) onToggleTouchLock()
                    },
                )
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            if (!immersive) {
                ConnectionSetupCard(connectionState, onConnectionAction, compact)
                DashboardHeader(
                    config = config,
                    landscape = landscape,
                    deviceLayout = deviceLayout,
                    drivingMode = preferences.drivingMode,
                    onEdit = onEdit,
                    onEnterFocus = onEnterFocus,
                )
            }

            activeAlerts.firstOrNull()?.let { alert ->
                AlertBanner(alert, compact = immersive || compact)
            }

            CustomHudCanvas(
                layout = layout,
                readings = readings,
                readingStats = readingStats,
                preferences = preferences,
                connectionState = connectionState,
                dtcCount = dtcCount,
                now = now,
                modifier = Modifier.weight(1f),
            )
        }

        if (immersive) {
            ConnectionBadge(
                state = connectionState,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            )
            PageIndicator(
                pageIndex = pageIndex,
                pageCount = pageCount,
                modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
            )
        }

        if ((preferences.focusMode || preferences.hudMode) && controlsVisible) {
            ImmersiveControls(
                projectionMode = preferences.hudMode,
                mirrored = preferences.hudMirror,
                touchLocked = preferences.touchLock,
                onToggleMirror = onToggleHudMirror,
                onToggleTouchLock = onToggleTouchLock,
                onResetStats = onResetStats,
                onExit = if (preferences.hudMode) onExitHud else onExitFocus,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun CustomHudCanvas(
    layout: DashboardLayout,
    readings: List<VehicleReading>,
    readingStats: Map<Int, ReadingStats>,
    preferences: DashboardPreferences,
    connectionState: ConnectionState,
    dtcCount: Int,
    now: Long,
    modifier: Modifier = Modifier,
) {
    val background = layout.background?.let(::Color) ?: Color.Transparent
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .background(background)
            .graphicsLayer { scaleX = if (preferences.hudMode && preferences.hudMirror) -1f else 1f },
    ) {
        if (layout.widgets.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Custom HUD ยังว่างอยู่ · เปิดตัวแก้ไขเพื่อเพิ่ม Widget",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@BoxWithConstraints
        }

        val cellWidth = maxWidth / layout.columns.coerceAtLeast(1)
        val cellHeight = maxHeight / layout.rows.coerceAtLeast(1)

        layout.widgets
            .sortedWith(compareBy({ it.zIndex }, { it.id }))
            .forEach { widget ->
                val x = widget.canvasX.coerceIn(0, (layout.columns - 1).coerceAtLeast(0))
                val y = widget.canvasY.coerceIn(0, (layout.rows - 1).coerceAtLeast(0))
                val width = widget.canvasWidth.coerceIn(1, (layout.columns - x).coerceAtLeast(1))
                val height = widget.canvasHeight.coerceIn(1, (layout.rows - y).coerceAtLeast(1))

                val nativeReading = readings.firstOrNull { it.pid == widget.pid }
                val displayReading = DisplayReadingAdapter.reading(nativeReading, widget.unit)
                val displayStats = DisplayReadingAdapter.stats(
                    stats = readingStats[widget.pid],
                    sourceUnit = nativeReading?.unit.orEmpty(),
                    target = widget.unit,
                )

                Box(
                    modifier = Modifier
                        .offset(x = cellWidth * x, y = cellHeight * y)
                        .size(width = cellWidth * width, height = cellHeight * height)
                        .padding(3.dp)
                        .zIndex(widget.zIndex.toFloat()),
                ) {
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

@Composable
private fun DashboardHeader(
    config: DashboardConfig,
    landscape: Boolean,
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = if (landscape) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Custom HUD · ${deviceLayout.label()} · 12×24 canvas",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onEnterFocus) { Text("เต็มจอ") }
            if (!drivingMode) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "แก้ไข Custom HUD")
                }
            }
        }
    }
}

@Composable
private fun ImmersiveControls(
    projectionMode: Boolean,
    mirrored: Boolean,
    touchLocked: Boolean,
    onToggleMirror: () -> Unit,
    onToggleTouchLock: () -> Unit,
    onResetStats: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.82f),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (projectionMode) {
                TextButton(onClick = onToggleMirror) {
                    Text(if (mirrored) "ภาพปกติ" else "กลับภาพ", color = Color.White)
                }
            }
            TextButton(onClick = onToggleTouchLock) {
                Text(if (touchLocked) "ปลดล็อก" else "ล็อก", color = Color.White)
            }
            TextButton(onClick = onResetStats) { Text("รีเซ็ต Min/Max", color = Color.White) }
            TextButton(onClick = onExit) { Text("ออก", color = Color.White) }
        }
    }
}

@Composable
private fun ConnectionBadge(state: ConnectionState, modifier: Modifier = Modifier) {
    val color = when (state) {
        ConnectionState.CONNECTED -> Color(0xFF4CAF50)
        ConnectionState.ERROR -> Color(0xFFFF5252)
        ConnectionState.DISCONNECTED -> Color(0xFF90A4AE)
        else -> Color(0xFFFFC107)
    }
    Surface(modifier = modifier, color = Color.Black.copy(alpha = 0.68f), shape = MaterialTheme.shapes.large) {
        Text(
            state.shortLabel(),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun PageIndicator(pageIndex: Int, pageCount: Int, modifier: Modifier = Modifier) {
    if (pageCount <= 1) return
    Surface(modifier = modifier, color = Color.Black.copy(alpha = 0.62f), shape = MaterialTheme.shapes.large) {
        Text(
            "${pageIndex + 1} / $pageCount",
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            color = Color.White,
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
private fun ConnectionSetupCard(
    state: ConnectionState,
    onAction: () -> Unit,
    compact: Boolean,
) {
    val connected = state == ConnectionState.CONNECTED
    val busy = state in setOf(
        ConnectionState.DEVICE_DETECTED,
        ConnectionState.REQUESTING_PERMISSION,
        ConnectionState.CONNECTING,
        ConnectionState.INITIALIZING,
    )
    val title = when (state) {
        ConnectionState.DISCONNECTED -> "เชื่อมต่อรถ"
        ConnectionState.DEVICE_DETECTED -> "พบอะแดปเตอร์"
        ConnectionState.REQUESTING_PERMISSION -> "รอสิทธิ์อุปกรณ์"
        ConnectionState.CONNECTING -> "กำลังเชื่อมต่อ"
        ConnectionState.INITIALIZING -> "กำลังเตรียม ECU"
        ConnectionState.CONNECTED -> "OBD เชื่อมต่อแล้ว"
        ConnectionState.ERROR -> "การเชื่อมต่อมีปัญหา"
    }
    val accent = when (state) {
        ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary
        ConnectionState.ERROR -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.secondary
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = if (compact) 4.dp else 8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = if (compact) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                busy -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                connected -> Icon(Icons.Default.CheckCircle, null, tint = accent)
                state == ConnectionState.ERROR -> Icon(Icons.Default.Warning, null, tint = accent)
                else -> Icon(Icons.Default.Usb, null, tint = accent)
            }
            Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            if (!busy) {
                Button(onClick = onAction) {
                    Text(if (connected) "ตัด" else "เชื่อมต่อ")
                }
            }
        }
    }
}

private fun ResolvedDeviceLayout.label(): String = when (this) {
    ResolvedDeviceLayout.PHONE -> "Phone"
    ResolvedDeviceLayout.TABLET -> "Tablet"
    ResolvedDeviceLayout.HEAD_UNIT -> "Head unit"
}

private fun ConnectionState.shortLabel(): String = when (this) {
    ConnectionState.DISCONNECTED -> "ยังไม่เชื่อมต่อ"
    ConnectionState.DEVICE_DETECTED -> "พบอุปกรณ์"
    ConnectionState.REQUESTING_PERMISSION -> "รออนุญาต"
    ConnectionState.CONNECTING -> "กำลังเชื่อมต่อ"
    ConnectionState.INITIALIZING -> "กำลังเตรียมระบบ"
    ConnectionState.CONNECTED -> "LIVE"
    ConnectionState.ERROR -> "ERROR"
}
