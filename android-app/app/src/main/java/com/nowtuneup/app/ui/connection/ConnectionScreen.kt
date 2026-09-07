package com.nowtuneup.app.ui.connection

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nowtuneup.app.data.vehicle.ConnectionProfileRepository
import com.nowtuneup.app.domain.model.ConnectionPhase
import com.nowtuneup.app.domain.model.ObdTransportType
import com.nowtuneup.app.presentation.dashboard.MainViewModel
import com.nowtuneup.app.ui.motion.NtuMotion
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ConnectionProfileEntryPoint {
    fun connectionProfileRepository(): ConnectionProfileRepository
}

@Composable
fun ConnectionScreen(viewModel: MainViewModel) {
    val state by viewModel.connectionUiState.collectAsState()
    val health by viewModel.adapterHealth.collectAsState()
    val identity by viewModel.vehicleIdentity.collectAsState()
    val readings by viewModel.readings.collectAsState()
    val preferences by viewModel.dashboardPreferences.collectAsState()
    val context = LocalContext.current
    val profileRepository = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, ConnectionProfileEntryPoint::class.java)
            .connectionProfileRepository()
    }
    val connectionProfile by profileRepository.current.collectAsState()
    var showTechnicalDetails by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        viewModel.onBluetoothPermissionResult(result.values.all { it })
    }
    val enableBluetoothLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onBluetoothEnableResult()
    }

    LaunchedEffect(state.transportType, state.permissionGranted, state.bluetoothEnabled) {
        if (state.transportType == ObdTransportType.BLUETOOTH_CLASSIC && state.permissionGranted) viewModel.refreshBluetoothState()
    }
    LaunchedEffect(state.phase, health.updatedAtMillis, identity.vin, state.initialization.adapterIdentity) {
        if (state.phase == ConnectionPhase.CONNECTED && health.totalCommands > 0L) {
            profileRepository.observe(
                adapterIdentity = state.initialization.adapterIdentity,
                vin = identity.vin,
                supportedPidCount = readings.count { it.supported },
                health = health,
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("การเชื่อมต่อ OBD-II", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Adaptive Connection v2 จะจดจำสุขภาพของ adapter และแนะนำ FAST / BALANCED / STABLE ให้อัตโนมัติ")
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ประเภทการเชื่อมต่อ", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.transportType == ObdTransportType.USB,
                            onClick = { viewModel.selectTransport(ObdTransportType.USB) },
                            label = { Text("USB OTG") },
                            leadingIcon = { Icon(Icons.Default.Usb, null) },
                        )
                        FilterChip(
                            selected = state.transportType == ObdTransportType.BLUETOOTH_CLASSIC,
                            onClick = { viewModel.selectTransport(ObdTransportType.BLUETOOTH_CLASSIC) },
                            label = { Text("Bluetooth SPP") },
                            leadingIcon = { Icon(Icons.Default.Bluetooth, null) },
                        )
                    }
                    Text("BLE เตรียม interface ไว้แล้วแต่ยังปิดใช้งาน จนกว่าจะทราบ Service UUID และ Characteristic UUID ของอะแดปเตอร์จริง", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            ConnectionStatusCard(
                phase = state.phase,
                completed = state.initialization.completedSteps,
                total = state.initialization.totalSteps,
                reduceMotion = preferences.reduceMotion,
            )
        }

        if (state.transportType == ObdTransportType.BLUETOOTH_CLASSIC) {
            when {
                !state.bluetoothSupported -> item { ActionCard("อุปกรณ์ไม่รองรับ Bluetooth", "ยังสามารถใช้สาย USB OTG ได้") }
                !state.permissionGranted -> item {
                    ActionCard(
                        title = "ต้องอนุญาต Nearby devices",
                        detail = "Android 12 ขึ้นไปต้องใช้ BLUETOOTH_SCAN และ BLUETOOTH_CONNECT เพื่อแสดงและเชื่อมต่ออุปกรณ์ที่จับคู่ไว้",
                        actionLabel = "อนุญาตสิทธิ์",
                        onAction = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
                            } else viewModel.onBluetoothPermissionResult(true)
                        },
                        secondaryLabel = "เปิดการตั้งค่าแอป",
                        onSecondary = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) },
                    )
                }
                !state.bluetoothEnabled -> item {
                    ActionCard(
                        title = "Bluetooth ปิดอยู่",
                        detail = "เปิด Bluetooth ก่อนเลือก ELM327",
                        actionLabel = "เปิด Bluetooth",
                        onAction = { enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
                    )
                }
                else -> {
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("อุปกรณ์ที่จับคู่ไว้", style = MaterialTheme.typography.titleMedium)
                                Text("ELM327 V1.5 มักแสดงชื่อ OBDII, OBD2 หรือ ELM327", style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = viewModel::refreshBluetoothState) {
                                Icon(Icons.Default.Refresh, null)
                                Text("รีเฟรช")
                            }
                        }
                    }
                    if (state.pairedDevices.isEmpty()) {
                        item {
                            ActionCard(
                                title = "ไม่พบอุปกรณ์ที่จับคู่",
                                detail = "จับคู่ผ่านหน้าตั้งค่า Bluetooth ของ Android ก่อน แอปจะไม่ข้ามระบบ Pairing ของ Android รหัสที่พบบ่อยคือ 1234 หรือ 0000",
                                actionLabel = "เปิดการตั้งค่า Bluetooth",
                                onAction = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                            )
                        }
                    } else {
                        items(state.pairedDevices, key = { it.address }) { device ->
                            val selected = state.selectedDevice?.address == device.address
                            Card(modifier = Modifier.fillMaxWidth()) {
                                ListItem(
                                    headlineContent = { Text(device.name, fontWeight = FontWeight.SemiBold) },
                                    supportingContent = {
                                        Column {
                                            Text(device.address)
                                            Text("Bluetooth Classic · RFCOMM SPP", style = MaterialTheme.typography.bodySmall)
                                        }
                                    },
                                    leadingContent = { Icon(Icons.Default.Bluetooth, null) },
                                    trailingContent = {
                                        if (selected) AssistChip(onClick = {}, label = { Text("เลือกแล้ว") })
                                        else OutlinedButton(onClick = { viewModel.selectBluetoothDevice(device) }) { Text("เลือก") }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("การเริ่มต้น ELM327", style = MaterialTheme.typography.titleMedium)
                    val init = state.initialization
                    Text("Bluetooth/Transport: ${if (init.bluetoothConnected) "สำเร็จ" else "รอเชื่อมต่อ"}")
                    Text("ELM327: ${if (init.adapterInitialized) "พร้อมใช้งาน" else "ยังไม่พร้อม"}")
                    Text("ECU: ${if (init.ecuConnected) "ตอบกลับแล้ว" else "ยังไม่ยืนยัน"}")
                    init.currentCommand?.let { Text("กำลังส่งคำสั่ง $it") }
                    init.adapterIdentity?.let { Text("Adapter: $it", style = MaterialTheme.typography.bodySmall) }
                    if (init.totalSteps > 0 && init.completedSteps < init.totalSteps) {
                        LinearProgressIndicator(
                            progress = { init.completedSteps.toFloat() / init.totalSteps.toFloat() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        connectionProfile?.let { profile ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Adapter Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Stability ${profile.stabilityScore}/100 • ${profile.latestGrade} • แนะนำ ${profile.recommendedMode}")
                        Text("Latency ${profile.averageLatencyMillis} ms • ${"%.1f".format(profile.successRate * 100)}% success • ${profile.supportedPidCount} PIDs")
                        Text("Health history ${profile.healthHistory.size} จุด • ระบบจะเรียนรู้ profile เฉพาะ adapter/รถคันนี้", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val connectedOrBusy = state.phase in setOf(ConnectionPhase.CONNECTED, ConnectionPhase.CONNECTING, ConnectionPhase.INITIALIZING_ADAPTER)
                Button(
                    onClick = { if (connectedOrBusy) viewModel.disconnectManually() else viewModel.connectSelected() },
                    modifier = Modifier.weight(1f),
                    enabled = state.phase !in setOf(
                        ConnectionPhase.PERMISSION_REQUIRED,
                        ConnectionPhase.BLUETOOTH_DISABLED,
                        ConnectionPhase.BLUETOOTH_UNAVAILABLE,
                        ConnectionPhase.DEVICE_SELECTION,
                    ),
                ) { Text(if (connectedOrBusy) "ตัดการเชื่อมต่อ" else "เชื่อมต่อ") }
                if (state.phase == ConnectionPhase.RECONNECTING) OutlinedButton(onClick = viewModel::cancelReconnect) { Text("ยกเลิก") }
            }
        }

        state.lastErrorThai?.let { message ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                            Text("  $message", color = MaterialTheme.colorScheme.error)
                        }
                        if (!state.technicalError.isNullOrBlank()) {
                            TextButton(onClick = { showTechnicalDetails = !showTechnicalDetails }) {
                                Text(if (showTechnicalDetails) "ซ่อนรายละเอียดทางเทคนิค" else "ดูรายละเอียดทางเทคนิค")
                            }
                            if (showTechnicalDetails) Text(state.technicalError.orEmpty(), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        item {
            HorizontalDivider()
            Text("คำเตือนเพื่อความปลอดภัย", style = MaterialTheme.typography.titleMedium)
            Text("• ห้ามตั้งค่าหรือใช้งานแอประหว่างขับรถ\n• บันทึกรหัส DTC ก่อนลบ เพราะการลบอาจลบ Freeze-frame และสถานะ Emissions readiness\n• แอปเป็นเครื่องมือวินิจฉัยเบื้องต้น ไม่ทดแทนการตรวจรถโดยช่างผู้เชี่ยวชาญ\n• ค่าและฟังก์ชันบางรายการขึ้นอยู่กับ ECU ของรถ")
        }
    }
}

@Composable
private fun ConnectionStatusCard(
    phase: ConnectionPhase,
    completed: Int,
    total: Int,
    reduceMotion: Boolean,
) {
    val pulse = remember { Animatable(1f) }
    val busy = phase in setOf(
        ConnectionPhase.SCANNING,
        ConnectionPhase.CONNECTING,
        ConnectionPhase.INITIALIZING_ADAPTER,
        ConnectionPhase.RECONNECTING,
    )
    LaunchedEffect(busy, reduceMotion) {
        if (!busy || reduceMotion) {
            pulse.snapTo(1f)
        } else {
            while (true) {
                pulse.animateTo(0.68f, tween(520))
                pulse.animateTo(1f, tween(520))
            }
        }
    }

    val (title, detail) = when (phase) {
        ConnectionPhase.BLUETOOTH_UNAVAILABLE -> "Bluetooth unavailable" to "อุปกรณ์นี้ไม่มี Bluetooth"
        ConnectionPhase.BLUETOOTH_DISABLED -> "Bluetooth disabled" to "กรุณาเปิด Bluetooth"
        ConnectionPhase.PERMISSION_REQUIRED -> "Permission required" to "ต้องอนุญาต Nearby devices"
        ConnectionPhase.SCANNING -> "Scanning" to "กำลังตรวจอุปกรณ์"
        ConnectionPhase.DEVICE_SELECTION -> "Select adapter" to "เลือก ELM327 ที่จับคู่ไว้แล้ว"
        ConnectionPhase.CONNECTING -> "Connecting" to "กำลังเปิด RFCOMM socket"
        ConnectionPhase.INITIALIZING_ADAPTER -> "Initializing ELM327" to "ขั้นตอน $completed จาก $total"
        ConnectionPhase.CONNECTED -> "Connected" to "ELM327 และ ECU พร้อมอ่านข้อมูล"
        ConnectionPhase.RECONNECTING -> "Reconnecting" to "กำลังพยายามเชื่อมต่อใหม่"
        ConnectionPhase.DISCONNECTED -> "Disconnected" to "ยังไม่ได้เชื่อมต่อรถ"
        ConnectionPhase.CONNECTION_FAILED -> "Connection failed" to "ตรวจอะแดปเตอร์ ระยะสัญญาณ และสวิตช์กุญแจ"
        ConnectionPhase.UNSUPPORTED_ADAPTER -> "Unsupported" to "ยังไม่รองรับ transport หรืออะแดปเตอร์นี้"
    }
    val errorPhase = phase in setOf(ConnectionPhase.CONNECTION_FAILED, ConnectionPhase.UNSUPPORTED_ADAPTER, ConnectionPhase.BLUETOOTH_UNAVAILABLE)
    val containerColor = when {
        phase == ConnectionPhase.CONNECTED -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
        errorPhase -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.52f)
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val icon = when {
        phase == ConnectionPhase.CONNECTED -> Icons.Default.CheckCircle
        errorPhase -> Icons.Default.Error
        else -> Icons.Default.Bluetooth
    }
    val iconTint = when {
        phase == ConnectionPhase.CONNECTED -> MaterialTheme.colorScheme.primary
        errorPhase -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.secondary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        ListItem(
            colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = containerColor),
            headlineContent = {
                AnimatedContent(
                    targetState = title,
                    transitionSpec = {
                        if (reduceMotion) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                        else fadeIn(tween(NtuMotion.Standard)) togetherWith fadeOut(tween(NtuMotion.Quick))
                    },
                    label = "connection title",
                ) { value -> Text(value, fontWeight = FontWeight.Bold) }
            },
            supportingContent = {
                AnimatedContent(
                    targetState = detail,
                    transitionSpec = {
                        if (reduceMotion) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                        else fadeIn(tween(NtuMotion.Standard)) togetherWith fadeOut(tween(NtuMotion.Quick))
                    },
                    label = "connection detail",
                ) { value -> Text(value) }
            },
            leadingContent = {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.graphicsLayer {
                        alpha = pulse.value
                        val scale = 0.92f + pulse.value * 0.08f
                        scaleX = scale
                        scaleY = scale
                    },
                )
            },
        )
    }
}

@Composable
private fun ActionCard(
    title: String,
    detail: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(detail)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (actionLabel != null && onAction != null) Button(onClick = onAction) { Text(actionLabel) }
                if (secondaryLabel != null && onSecondary != null) OutlinedButton(onClick = onSecondary) {
                    Icon(Icons.Default.Settings, null)
                    Text(secondaryLabel)
                }
            }
        }
    }
}
