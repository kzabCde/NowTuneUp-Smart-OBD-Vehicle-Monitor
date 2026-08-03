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
                "เชื่อมต่อ USB หรือ Bluetooth ELM327 และเปิดสวิตช์กุญแจก่อนอ่านข้อมูลสด",
                "เชื่อมต่อ",
                viewModel::connectSelected,
            )
        } else if (filtered.isEmpty()) {
            MessageCard(
                if (query.isBlank()) "กำลังรอข้อมูลจาก ECU" else "ไม่พบข้อมูลที่ค้นหา",
                if (query.isBlank()) "ระบบจะแสดงเฉพาะข้อมูลที่รถรองรับ" else "ลองใช้คำค้นหาอื่น",
            )
        } else {
            LazyColumn {
                items(filtered, key = { it.pid }) { reading ->
                    ListItem(
                        headlineContent = { Text(reading.name) },
                        overlineContent = {
                            Text(if (reading.pid == DerivedPids.TURBO_PRESSURE) "คำนวณจาก MAP − BARO" else "PID 01%02X".format(reading.pid))
                        },
                        supportingContent = {
                            Text(
                                when {
                                    !reading.supported -> if (reading.pid == DerivedPids.TURBO_PRESSURE) {
                                        "รถต้องรองรับค่าความดันท่อร่วมและความดันอากาศ"
                                    } else {
                                        "ECU ของรถไม่รองรับข้อมูลนี้"
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
            title = { Text("ยืนยันการลบรหัสปัญหา") },
            text = {
                Text(
                    "การส่งคำสั่ง Mode 04 อาจลบรหัสความผิดปกติ ข้อมูล Freeze-frame และสถานะตรวจมลพิษ " +
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
            Text("ตรวจรหัสปัญหารถ", style = MaterialTheme.typography.headlineSmall)
            Text("แอปจะอ่านข้อมูลก่อนเสมอและไม่แก้ไข ECU โดยอัตโนมัติ")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 12.dp)) {
                Button(
                    onClick = viewModel::scan,
                    enabled = connectionState == ConnectionState.CONNECTED && connectionUi.initialization.ecuConnected,
                ) { Text("อ่านรหัสที่บันทึกไว้") }
                TextButton(
                    onClick = { confirmClear = true },
                    enabled = connectionState == ConnectionState.CONNECTED && dtcs.isNotEmpty(),
                ) { Text("ลบรหัส…") }
            }
        }
        if (connectionState != ConnectionState.CONNECTED) {
            item { MessageCard("เชื่อมต่อก่อนตรวจ", "เปิดสวิตช์กุญแจและเชื่อมต่อ ELM327 ก่อน", "เชื่อมต่อ", viewModel::connectSelected) }
        } else if (dtcs.isEmpty()) {
            item { MessageCard("ยังไม่มีผลตรวจ", "กดอ่านรหัสที่บันทึกไว้เพื่อตรวจข้อมูลจาก ECU") }
        } else {
            items(dtcs, key = { it.code }) { dtc ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(dtc.code, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text(dtc.description ?: "ไม่มีคำอธิบายมาตรฐานหรือเป็นรหัสเฉพาะผู้ผลิต")
                        Text("ระบบ ${dtc.category} · ${dtc.status}", style = MaterialTheme.typography.labelMedium)
                        Text("ข้อมูลดิบ: ${dtc.raw.take(240)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
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
        item { ToggleSetting("เชื่อมต่ออะแดปเตอร์ล่าสุดอัตโนมัติ", "ใช้กับ Bluetooth ที่เคยเชื่อมต่อสำเร็จ", preferences.autoConnectLastAdapter, viewModel::setAutoConnectLastAdapter) }
        item { ToggleSetting("อ่านข้อมูลต่อเมื่อออกจากแอป", "เปิดเฉพาะเมื่อจำเป็น เพราะใช้พลังงานมากขึ้น", preferences.continuousMonitoring, viewModel::setContinuousMonitoring) }
        item { ToggleSetting("เชื่อมต่อใหม่อัตโนมัติ", "หยุดทำงานเมื่อผู้ใช้กดตัดการเชื่อมต่อเอง", preferences.autoReconnect, viewModel::setAutoReconnect) }
        item { ChoiceSetting("รอก่อนลองเชื่อมใหม่", listOf(2, 3, 5, 10), preferences.reconnectIntervalSeconds, { "$it วินาที" }, viewModel::setReconnectIntervalSeconds) }
        item { ChoiceSetting("จำนวนครั้งที่ลอง", listOf(3, 5, 10, 15), preferences.reconnectAttempts, { "$it ครั้ง" }, viewModel::setReconnectAttempts) }
        item { ChoiceSetting("เวลารอสูงสุด", listOf(15, 30, 60, 120), preferences.reconnectMaxDelaySeconds, { "$it วินาที" }, viewModel::setReconnectMaxDelaySeconds) }

        item { SettingsHeading("โปรไฟล์หน้าปัด", "สร้างและแก้ไขโปรไฟล์จากหน้า หน้าปัด") }
        if (dashboards.isEmpty()) {
            item { Text("ยังไม่มีโปรไฟล์ ไปที่หน้า หน้าปัด แล้วกด “สร้างโปรไฟล์แรก”") }
        } else {
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
        }
        item { ToggleSetting("ปัดเปลี่ยนโปรไฟล์", "ปิดเพื่อล็อกหน้าปัดขณะขับรถ", preferences.swipePages, viewModel::setSwipePages) }

        item { SettingsHeading("โหมดเต็มหน้าจอ", "ซ่อนเมนูเพื่อให้เห็นมาตรวัดชัดขึ้น") }
        item { ToggleSetting("แสดงหน้าปัดเต็มจอ", "แตะหน้าจอเพื่อแสดงหรือซ่อนปุ่มควบคุม", preferences.focusMode, viewModel::setFocusMode) }
        item { ToggleSetting("เข้าเต็มจอหลังเชื่อมต่อ", "เปิดหน้าปัดเมื่อ ECU พร้อมใช้งาน", preferences.autoFocusOnConnect, viewModel::setAutoFocusOnConnect) }
        item { ToggleSetting("จำโหมดเต็มจอ", "กลับไปยังมุมมองเดิมเมื่อเปิดแอป", preferences.resumeFocusMode, viewModel::setResumeFocusMode) }
        item { ToggleSetting("เปิดหน้าจอค้าง", "ทำงานเฉพาะขณะเชื่อมต่อ OBD-II", preferences.keepScreenOn, viewModel::setKeepScreenOn) }
        item { ToggleSetting("ล็อกการแตะ", "กดค้างเพื่อปลดล็อกหรือเปิดล็อก", preferences.touchLock, viewModel::setTouchLock) }
        item { ChoiceSetting("ซ่อนปุ่มควบคุมหลัง", listOf(3, 4, 5, 8), preferences.controlsAutoHideSeconds, { "$it วินาที" }, viewModel::setControlsAutoHideSeconds) }

        item { SettingsHeading("ค่าสูงสุดและต่ำสุด", "รีเซ็ตได้ด้วยปุ่มด้านล่าง") }
        item { ToggleSetting("แสดงค่าสูงสุด", "แสดงค่าสูงสุดที่พบของแต่ละข้อมูล", preferences.showPeakHold, viewModel::setShowPeakHold) }
        item { ToggleSetting("แสดงช่วงต่ำสุด–สูงสุด", "แสดงช่วงค่าที่ตรวจพบตั้งแต่เปิดแอปหรือรีเซ็ต", preferences.showMinMax, viewModel::setShowMinMax) }
        item { Button(onClick = viewModel::resetReadingStats, modifier = Modifier.fillMaxWidth()) { Text("เริ่มนับค่าสูงสุดและต่ำสุดใหม่") } }

        item { SettingsHeading("การเตือน", "ปรับช่วงพักเพื่อลดการเตือนซ้ำ") }
        item { ToggleSetting("เสียงเตือน", "เล่นเสียงเมื่อพบค่าที่ควรระวังหรืออันตราย", preferences.alertSound, viewModel::setAlertSound) }
        item { ToggleSetting("สั่นเตือน", "สั่นเมื่อเกิดการเตือน", preferences.alertVibration, viewModel::setAlertVibration) }
        item { ToggleSetting("ปิดเสียงทั้งหมด", "ยังแสดงสีและข้อความเตือน", preferences.muteAlerts, viewModel::setMuteAlerts) }
        item { ChoiceSetting("เว้นช่วงการเตือน", listOf(10, 15, 30, 60), preferences.alertCooldownSeconds, { "$it วินาที" }, viewModel::setAlertCooldownSeconds) }
        item { ChoiceSetting("ระยะเผื่อก่อนเปลี่ยนสถานะ", listOf(1, 2, 3, 5), preferences.hysteresis.toInt(), { "$it หน่วย" }) { viewModel.setHysteresis(it.toDouble()) } }
        item { ChoiceSetting("ถือว่าข้อมูลเก่าหลัง", listOf(2, 3, 5, 10), (preferences.staleAfterMillis / 1_000).toInt(), { "$it วินาที" }) { viewModel.setStaleAfterMillis(it * 1_000L) } }

        item { SettingsHeading("โหมด HUD", "แสดงข้อมูลสำคัญสำหรับสะท้อนกระจกหน้า") }
        item { ToggleSetting("เปิด HUD", "ใช้พื้นหลังสีดำและซ่อนแถบระบบ", preferences.hudMode, viewModel::setHudMode) }
        item { ToggleSetting("กลับภาพซ้าย–ขวา", "ใช้เมื่อสะท้อนภาพบนกระจกหน้า", preferences.hudMirror, viewModel::setHudMirror) }
        item { ToggleSetting("ป้องกันภาพค้าง", "ขยับตำแหน่งเล็กน้อยเป็นระยะ", preferences.hudBurnInProtection, viewModel::setHudBurnInProtection) }
        item { ChoiceSetting("ความสว่าง HUD", listOf(40, 60, 80, 100), preferences.hudBrightnessPercent, { "$it%" }, viewModel::setHudBrightnessPercent) }
        item { ChoiceSetting("สี HUD", HudColorPreset.entries, preferences.hudColorPreset, { it.thaiLabel() }, viewModel::setHudColorPreset) }

        item { SettingsHeading("ขนาดหน้าจอ", "ให้แอปเลือกอัตโนมัติ หรือกำหนดตามอุปกรณ์") }
        item { ChoiceSetting("รูปแบบหน้าจอ", AdaptiveLayoutProfile.entries, preferences.adaptiveLayoutProfile, { it.thaiLabel() }, viewModel::setAdaptiveLayoutProfile) }
        item { ToggleSetting("เต็มจอบนจอรถ", "ซ่อนแถบระบบและเพิ่มพื้นที่หน้าปัด", preferences.headUnitImmersive, viewModel::setHeadUnitImmersive) }

        item { SettingsHeading("รูปลักษณ์", "เปลี่ยนโทนสีได้ทันทีโดยไม่ตัดการเชื่อมต่อ") }
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
        item { ToggleSetting("ลดการเคลื่อนไหว", "ลดการขยับของเข็มและเอฟเฟกต์", preferences.reduceMotion, viewModel::setReduceMotion) }
        item { ToggleSetting("โหมดขับรถ", "ขยายข้อมูลสำคัญและปิดการแก้ไขหน้าปัด", preferences.drivingMode, viewModel::setDrivingMode) }
        item { ChoiceSetting("ความถี่การอ่านข้อมูล", RefreshRate.entries, preferences.refreshRate, { it.thaiLabel() }, viewModel::setRefreshRate) }

        item { SettingsHeading("บันทึกช่วยตรวจปัญหา", "เก็บขั้นตอนการเชื่อมต่อ คำสั่ง และเวลาที่รอ") }
        item { ToggleSetting("เก็บบันทึกระบบ", "ข้อมูลดิบแสดงเฉพาะรุ่นทดสอบและซ่อน MAC ในรุ่นใช้งานจริง", preferences.diagnosticLogging, viewModel::setDiagnosticLogging) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showLogs = !showLogs }) { Text(if (showLogs) "ซ่อนบันทึก" else "ดูบันทึก (${logs.size})") }
                TextButton(onClick = {
                    val text = viewModel.exportDiagnosticLogs()
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "NowTuneUp diagnostic logs")
                                putExtra(Intent.EXTRA_TEXT, text)
                            },
                            "ส่งออกบันทึกระบบ",
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
                "NTU 1.6.2 • Android 8+ • USB + Bluetooth Classic SPP • อ่านข้อมูลเป็นค่าเริ่มต้น",
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
    ConnectionState.DISCONNECTED -> "ยังไม่เชื่อมต่อ"
    ConnectionState.DEVICE_DETECTED -> "พบอุปกรณ์"
    ConnectionState.REQUESTING_PERMISSION -> "รออนุญาต"
    ConnectionState.CONNECTING -> "กำลังเชื่อมต่อ"
    ConnectionState.INITIALIZING -> "กำลังเตรียมระบบ"
    ConnectionState.CONNECTED -> "เชื่อมต่อแล้ว"
    ConnectionState.ERROR -> "มีปัญหา"
}

private fun ObdTransportType.displayName(): String = when (this) {
    ObdTransportType.USB -> "สาย USB OTG"
    ObdTransportType.BLUETOOTH_CLASSIC -> "Bluetooth ELM327"
    ObdTransportType.BLE_EXPERIMENTAL -> "Bluetooth BLE ทดลอง"
    ObdTransportType.MOCK -> "ข้อมูลจำลอง"
}

private fun HudColorPreset.thaiLabel(): String = when (this) {
    HudColorPreset.GREEN -> "เขียว"
    HudColorPreset.AMBER -> "ส้มอำพัน"
    HudColorPreset.CYAN -> "ฟ้า"
    HudColorPreset.WHITE -> "ขาว"
    HudColorPreset.RED -> "แดง"
}

private fun AdaptiveLayoutProfile.thaiLabel(): String = when (this) {
    AdaptiveLayoutProfile.AUTO -> "เลือกอัตโนมัติ"
    AdaptiveLayoutProfile.PHONE -> "โทรศัพท์"
    AdaptiveLayoutProfile.TABLET -> "แท็บเล็ต"
    AdaptiveLayoutProfile.HEAD_UNIT -> "จอรถ"
}

private fun RefreshRate.thaiLabel(): String = when (this) {
    RefreshRate.LOW -> "ประหยัดพลังงาน"
    RefreshRate.BALANCED -> "สมดุล"
    RefreshRate.FAST -> "ตอบสนองไว"
}
