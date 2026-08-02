package com.nowtuneup.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nowtuneup.app.data.dashboard.DashboardDefaults
import com.nowtuneup.app.data.obd.pid.DerivedPids
import com.nowtuneup.app.domain.model.AdaptiveLayoutProfile
import com.nowtuneup.app.domain.model.ConnectionState
import com.nowtuneup.app.domain.model.HudColorPreset
import com.nowtuneup.app.domain.model.ObdTransportType
import com.nowtuneup.app.domain.model.RefreshRate
import com.nowtuneup.app.presentation.dashboard.MainViewModel
import java.util.Date

@Composable
fun LiveDataScreen(viewModel: MainViewModel) {
    val readings by viewModel.readings.collectAsState()
    val connectionState by viewModel.connection.collectAsState()
    val connectionUi by viewModel.connectionUiState.collectAsState()
    var query by remember { mutableStateOf("") }
    val filtered = readings.filter { it.name.contains(query, ignoreCase = true) }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            label = { Text("ค้นหาข้อมูลรถ") },
            singleLine = true,
        )
        Row(modifier = Modifier.padding(horizontal = 12.dp)) {
            Button(onClick = viewModel::pause, enabled = connectionState == ConnectionState.CONNECTED) { Text("หยุดชั่วคราว") }
            TextButton(onClick = viewModel::resume, enabled = connectionState == ConnectionState.CONNECTED) { Text("อ่านต่อ") }
        }

        if (connectionState != ConnectionState.CONNECTED || !connectionUi.initialization.ecuConnected) {
            MessageCard(
                "ยังไม่ได้เชื่อมต่อ ECU",
                "เชื่อมต่อ USB หรือ Bluetooth ELM327 และเปิดสวิตช์กุญแจก่อนอ่าน Live Data",
                "เชื่อมต่อ",
                viewModel::connectSelected,
            )
        } else if (filtered.isEmpty()) {
            MessageCard(
                if (query.isBlank()) "กำลังรอข้อมูลจาก ECU" else "ไม่พบข้อมูลที่ค้นหา",
                if (query.isBlank()) "ระบบจะแสดงเฉพาะ PID ที่รถรองรับ" else "ลองใช้คำค้นหาอื่น",
            )
        } else {
            LazyColumn {
                items(filtered, key = { it.pid }) { reading ->
                    ListItem(
                        headlineContent = { Text(reading.name) },
                        overlineContent = {
                            Text(if (reading.pid == DerivedPids.TURBO_PRESSURE) "DERIVED · MAP − BARO" else "PID 01%02X".format(reading.pid))
                        },
                        supportingContent = {
                            Text(
                                when {
                                    !reading.supported -> if (reading.pid == DerivedPids.TURBO_PRESSURE) {
                                        "รถต้องรองรับ MAP 0x0B และ Barometric 0x33"
                                    } else {
                                        "ECU ของรถไม่รองรับ PID นี้"
                                    }
                                    reading.value == null -> "ยังไม่มีข้อมูลล่าสุด"
                                    else -> "อัปเดตเมื่อ ${System.currentTimeMillis() - reading.updatedAt} ms ที่แล้ว"
                                },
                            )
                        },
                        trailingContent = {
                            Text(reading.value?.let { "%.1f ${reading.unit}".format(it) } ?: "--")
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun DiagnosticsScreen(viewModel: MainViewModel) {
    val dtcs by viewModel.dtcs.collectAsState()
    val connectionState by viewModel.connection.collectAsState()
    val connectionUi by viewModel.connectionUiState.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("ยืนยันการลบรหัส DTC") },
            text = {
                Text(
                    "การส่ง Mode 04 อาจลบรหัสความผิดปกติ, Freeze-frame และข้อมูล Emissions readiness " +
                        "ควรบันทึกและวิเคราะห์รหัสก่อนดำเนินการ แอปจะไม่ลบโดยอัตโนมัติ",
                )
            },
            confirmButton = {
                Button(onClick = {
                    confirmClear = false
                    viewModel.clearDtcsConfirmed()
                }) { Text("ยืนยันลบรหัส") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("ยกเลิก") } },
        )
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("รหัสวินิจฉัยความผิดปกติ", style = MaterialTheme.typography.headlineSmall)
            Text("ค่าเริ่มต้นเป็นการอ่านข้อมูลเท่านั้น ไม่แก้ไข ECU")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 12.dp)) {
                Button(
                    onClick = viewModel::scan,
                    enabled = connectionState == ConnectionState.CONNECTED && connectionUi.initialization.ecuConnected,
                ) { Text("อ่าน Stored DTC") }
                TextButton(
                    onClick = { confirmClear = true },
                    enabled = connectionState == ConnectionState.CONNECTED && dtcs.isNotEmpty(),
                ) { Text("ลบรหัส…") }
            }
        }
        if (connectionState != ConnectionState.CONNECTED) {
            item { MessageCard("เชื่อมต่อก่อนสแกน", "เปิดสวิตช์กุญแจและเชื่อมต่อ ELM327 ก่อน", "เชื่อมต่อ", viewModel::connectSelected) }
        } else if (dtcs.isEmpty()) {
            item { MessageCard("ยังไม่มีผลสแกน", "กดอ่าน Stored DTC เพื่อตรวจรหัสที่ ECU บันทึกไว้") }
        } else {
            items(dtcs, key = { it.code }) { dtc ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(dtc.code, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text(dtc.description ?: "ไม่มีคำอธิบายมาตรฐานหรือเป็นรหัสเฉพาะผู้ผลิต")
                        Text("ระบบ ${dtc.category} · ${dtc.status}", style = MaterialTheme.typography.labelMedium)
                        Text("Raw: ${dtc.raw.take(240)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun TripsScreen(viewModel: MainViewModel) {
    val trips by viewModel.trips.collectAsState(initial = emptyList())
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("ประวัติการเดินทาง", style = MaterialTheme.typography.headlineSmall)
            Text("บันทึกข้อมูลรถไว้ในเครื่อง ค่า Peak และ Min/Max จะเริ่มใหม่เมื่อเริ่ม Trip")
            Button(onClick = viewModel::toggleTrip, modifier = Modifier.padding(vertical = 12.dp)) {
                Text("เริ่ม / หยุดบันทึก")
            }
        }
        if (trips.isEmpty()) {
            item { MessageCard("ยังไม่มี Trip", "เชื่อมต่อรถและเริ่มบันทึกเพื่อสร้างประวัติ") }
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
fun SettingsScreen(viewModel: MainViewModel) {
    val preferences by viewModel.dashboardPreferences.collectAsState()
    val dashboards by viewModel.dashboards.collectAsState()
    val connectionState by viewModel.connection.collectAsState()
    val connectionUi by viewModel.connectionUiState.collectAsState()
    val logs by viewModel.diagnosticLogs.collectAsState()
    val context = LocalContext.current
    var showLogs by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text("การตั้งค่า", style = MaterialTheme.typography.headlineSmall) }

        item { SettingsHeading("การเชื่อมต่อ", "${connectionState.shortLabel()} · ${connectionUi.transportType.displayName()}") }
        item {
            ScrollableChips {
                listOf(ObdTransportType.USB, ObdTransportType.BLUETOOTH_CLASSIC).forEach { type ->
                    FilterChip(
                        selected = preferences.preferredTransport == type,
                        onClick = { viewModel.selectTransport(type) },
                        label = { Text(type.displayName()) },
                    )
                }
            }
        }
        item { ToggleSetting("เชื่อมต่ออะแดปเตอร์ล่าสุดอัตโนมัติ", "ใช้กับอุปกรณ์ Bluetooth ที่เคยเชื่อมต่อสำเร็จ", preferences.autoConnectLastAdapter, viewModel::setAutoConnectLastAdapter) }
        item { ToggleSetting("ทำงานต่อในพื้นหลัง", "เปิดเฉพาะเมื่อจำเป็น เพราะใช้พลังงานและต้องมี foreground service", preferences.continuousMonitoring, viewModel::setContinuousMonitoring) }
        item { ToggleSetting("เชื่อมต่อใหม่อัตโนมัติ", "ไม่ทำงานหลังผู้ใช้กด Disconnect เอง", preferences.autoReconnect, viewModel::setAutoReconnect) }
        item { ChoiceSetting("Retry เริ่มต้น", listOf(2, 3, 5, 10), preferences.reconnectIntervalSeconds, { "$it s" }, viewModel::setReconnectIntervalSeconds) }
        item { ChoiceSetting("จำนวน Retry", listOf(3, 5, 10, 15), preferences.reconnectAttempts, { "$it" }, viewModel::setReconnectAttempts) }
        item { ChoiceSetting("Retry สูงสุด", listOf(15, 30, 60, 120), preferences.reconnectMaxDelaySeconds, { "$it s" }, viewModel::setReconnectMaxDelaySeconds) }

        item { SettingsHeading("หน้า Dashboard", "ปัดระหว่าง Dashboard ที่บันทึกไว้ใน Focus Mode") }
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
        item { ToggleSetting("ปัดเปลี่ยน Dashboard", "ปิดเพื่อล็อกหน้าในขณะขับรถ", preferences.swipePages, viewModel::setSwipePages) }

        item { SettingsHeading("Focus Mode", "ซ่อนเมนูและแสดงเฉพาะ Gauge") }
        item { ToggleSetting("Gauge Focus Mode", "แตะหน้าจอเพื่อแสดงหรือซ่อนชุดควบคุม", preferences.focusMode, viewModel::setFocusMode) }
        item { ToggleSetting("เข้า Focus หลังเชื่อมต่อ", "เปิด Dashboard เมื่อ ECU พร้อม", preferences.autoFocusOnConnect, viewModel::setAutoFocusOnConnect) }
        item { ToggleSetting("จำ Focus Mode", "คืนมุมมองเดิมเมื่อเปิดแอป", preferences.resumeFocusMode, viewModel::setResumeFocusMode) }
        item { ToggleSetting("เปิดหน้าจอค้าง", "ทำงานเฉพาะขณะเชื่อมต่อ OBD-II", preferences.keepScreenOn, viewModel::setKeepScreenOn) }
        item { ToggleSetting("Touch lock", "กดค้างเพื่อ Lock หรือ Unlock", preferences.touchLock, viewModel::setTouchLock) }
        item { ChoiceSetting("ซ่อน Controls", listOf(3, 4, 5, 8), preferences.controlsAutoHideSeconds, { "$it s" }, viewModel::setControlsAutoHideSeconds) }

        item { SettingsHeading("Peak และ Min/Max", "รีเซ็ตเมื่อเริ่ม Trip หรือกด Reset") }
        item { ToggleSetting("Peak hold", "แสดงค่าสูงสุดของแต่ละ PID", preferences.showPeakHold, viewModel::setShowPeakHold) }
        item { ToggleSetting("Minimum และ Maximum", "แสดงช่วงค่าที่ตรวจพบ", preferences.showMinMax, viewModel::setShowMinMax) }
        item { Button(onClick = viewModel::resetReadingStats, modifier = Modifier.fillMaxWidth()) { Text("รีเซ็ต Peak และ Min/Max") } }

        item { SettingsHeading("การเตือน", "ใช้ Hysteresis และ Cooldown ลดการเตือนซ้ำ") }
        item { ToggleSetting("เสียงเตือน", "เล่นเสียงเมื่อเกิด Warning/Critical ใหม่", preferences.alertSound, viewModel::setAlertSound) }
        item { ToggleSetting("การสั่น", "สั่นเมื่อเกิดการเตือน", preferences.alertVibration, viewModel::setAlertVibration) }
        item { ToggleSetting("ปิดเสียงเตือน", "ยังแสดงสีและข้อความเตือน", preferences.muteAlerts, viewModel::setMuteAlerts) }
        item { ChoiceSetting("Alert cooldown", listOf(10, 15, 30, 60), preferences.alertCooldownSeconds, { "$it s" }, viewModel::setAlertCooldownSeconds) }
        item { ChoiceSetting("Hysteresis", listOf(1, 2, 3, 5), preferences.hysteresis.toInt(), { "$it units" }) { viewModel.setHysteresis(it.toDouble()) } }
        item { ChoiceSetting("ข้อมูลถือว่า Stale หลัง", listOf(2, 3, 5, 10), (preferences.staleAfterMillis / 1_000).toInt(), { "$it s" }) { viewModel.setStaleAfterMillis(it * 1_000L) } }

        item { SettingsHeading("HUD Mode", "แสดง Speed, RPM และ Turbo สำหรับสะท้อนกระจกหน้า") }
        item { ToggleSetting("HUD Mode", "เปิดพื้นดำและซ่อน System bars", preferences.hudMode, viewModel::setHudMode) }
        item { ToggleSetting("Mirror horizontal", "กลับภาพสำหรับการสะท้อนกระจก", preferences.hudMirror, viewModel::setHudMirror) }
        item { ToggleSetting("Burn-in protection", "ขยับตำแหน่งเล็กน้อยทุกนาที", preferences.hudBurnInProtection, viewModel::setHudBurnInProtection) }
        item { ChoiceSetting("ความสว่าง HUD", listOf(40, 60, 80, 100), preferences.hudBrightnessPercent, { "$it%" }, viewModel::setHudBrightnessPercent) }
        item { ChoiceSetting("สี HUD", HudColorPreset.entries, preferences.hudColorPreset, { it.name }, viewModel::setHudColorPreset) }

        item { SettingsHeading("หน้าจอขนาดใหญ่", "Auto, Phone, Tablet และ Android Head Unit") }
        item { ChoiceSetting("Layout profile", AdaptiveLayoutProfile.entries, preferences.adaptiveLayoutProfile, { it.name.replace('_', ' ') }, viewModel::setAdaptiveLayoutProfile) }
        item { ToggleSetting("Head Unit immersive", "ซ่อนแถบระบบและเพิ่มพื้นที่ Gauge", preferences.headUnitImmersive, viewModel::setHeadUnitImmersive) }

        item { SettingsHeading("รูปลักษณ์", "Theme ใช้งานทันทีโดยไม่ตัดการเชื่อมต่อ") }
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
        item { ToggleSetting("ลด Animation", "ลดการเคลื่อนไหวของ Gauge", preferences.reduceMotion, viewModel::setReduceMotion) }
        item { ToggleSetting("Driving mode", "ขยายข้อมูลสำคัญและล็อก Editor", preferences.drivingMode, viewModel::setDrivingMode) }
        item { ChoiceSetting("Refresh rate", RefreshRate.entries, preferences.refreshRate, { it.name }, viewModel::setRefreshRate) }

        item { SettingsHeading("Diagnostic logs", "เก็บวงจรการเชื่อมต่อ คำสั่ง และ timeout เพื่อแก้ปัญหา") }
        item { ToggleSetting("Structured logging", "Raw responses แสดงเฉพาะ Debug build และซ่อน MAC ใน Production", preferences.diagnosticLogging, viewModel::setDiagnosticLogging) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showLogs = !showLogs }) { Text(if (showLogs) "ซ่อน Logs" else "ดู Logs (${logs.size})") }
                TextButton(onClick = {
                    val text = viewModel.exportDiagnosticLogs()
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "NowTuneUp diagnostic logs")
                                putExtra(Intent.EXTRA_TEXT, text)
                            },
                            "ส่งออก Diagnostic logs",
                        ),
                    )
                }) { Text("ส่งออก") }
                TextButton(onClick = viewModel::clearDiagnosticLogs) { Text("ล้าง") }
            }
        }
        if (showLogs) {
            items(logs.takeLast(80).reversed(), key = { "${it.timestamp}-${it.category}-${it.message.hashCode()}" }) { entry ->
                Text(
                    "${entry.level.name} ${entry.category}: ${entry.message}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                )
            }
        }

        item {
            Text(
                "NTU 1.5.0 • Android 8+ • USB + Bluetooth Classic SPP • Read-only by default",
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
private fun <T> ChoiceSetting(
    title: String,
    choices: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        ScrollableChips {
            choices.forEach { choice ->
                FilterChip(
                    selected = choice == selected,
                    onClick = { onSelect(choice) },
                    label = { Text(label(choice)) },
                )
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
private fun MessageCard(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Any)? = null,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodyMedium)
            if (actionLabel != null && onAction != null) {
                Row {
                    Button(onClick = { onAction() }) { Text(actionLabel) }
                    Spacer(Modifier.width(8.dp))
                }
            }
        }
    }
}

private fun ConnectionState.shortLabel(): String = when (this) {
    ConnectionState.DISCONNECTED -> "Disconnected"
    ConnectionState.DEVICE_DETECTED -> "Device detected"
    ConnectionState.REQUESTING_PERMISSION -> "Permission"
    ConnectionState.CONNECTING -> "Connecting"
    ConnectionState.INITIALIZING -> "Initializing"
    ConnectionState.CONNECTED -> "Connected"
    ConnectionState.ERROR -> "Error"
}

private fun ObdTransportType.displayName(): String = when (this) {
    ObdTransportType.USB -> "USB OTG"
    ObdTransportType.BLUETOOTH_CLASSIC -> "Bluetooth Classic SPP"
    ObdTransportType.BLE_EXPERIMENTAL -> "BLE experimental"
    ObdTransportType.MOCK -> "Mock"
}
