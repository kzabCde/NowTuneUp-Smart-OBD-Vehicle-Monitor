package com.nowtuneup.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.nowtuneup.app.data.obd.pid.DerivedPids
import com.nowtuneup.app.domain.model.ConnectionState
import com.nowtuneup.app.domain.model.ObdTransportType
import com.nowtuneup.app.domain.model.RefreshRate
import com.nowtuneup.app.presentation.dashboard.MainViewModel

@Composable
fun LiveDataScreen(viewModel: MainViewModel) {
    val readings by viewModel.readings.collectAsState()
    val connectionState by viewModel.connection.collectAsState()
    val connectionUi by viewModel.connectionUiState.collectAsState()
    var query by remember { mutableStateOf("") }

    val visibleReadings = readings
        .asSequence()
        .filter { it.supported }
        .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
        .sortedWith(compareByDescending<com.nowtuneup.app.domain.model.VehicleReading> { it.value != null }.thenBy { it.name })
        .toList()

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenIntro(
            title = "ข้อมูลสด",
            detail = if (connectionState == ConnectionState.CONNECTED && connectionUi.initialization.ecuConnected) {
                "แสดงเฉพาะค่าที่รถรองรับ ระบบอ่านต่อเนื่องอัตโนมัติ"
            } else {
                "เชื่อมต่อ ELM327 และเปิดสวิตช์กุญแจก่อน"
            },
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            label = { Text("ค้นหาค่าที่ต้องการ") },
            singleLine = true,
        )

        when {
            connectionState != ConnectionState.CONNECTED || !connectionUi.initialization.ecuConnected -> {
                MessageCard(
                    title = "ยังไม่ได้เชื่อมต่อ ECU",
                    message = "ไปที่หน้าเชื่อมต่อ เลือก ELM327 ที่จับคู่ไว้ แล้วกดเชื่อมต่อ",
                    actionLabel = "เชื่อมต่อ",
                    onAction = viewModel::connectSelected,
                )
            }

            visibleReadings.isEmpty() -> {
                MessageCard(
                    title = if (query.isBlank()) "กำลังรอข้อมูล" else "ไม่พบข้อมูลที่ค้นหา",
                    message = if (query.isBlank()) "ระบบกำลังตรวจว่ารถรองรับค่าใดบ้าง" else "ลองใช้คำค้นหาอื่น",
                )
            }

            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(visibleReadings, key = { it.pid }) { reading ->
                        val ageMillis = (System.currentTimeMillis() - reading.updatedAt).coerceAtLeast(0L)
                        ListItem(
                            headlineContent = { Text(reading.name, fontWeight = FontWeight.SemiBold) },
                            overlineContent = {
                                Text(
                                    if (reading.pid == DerivedPids.TURBO_PRESSURE) {
                                        "Turbo จาก MAP − BARO"
                                    } else {
                                        "PID 01%02X".format(reading.pid)
                                    },
                                )
                            },
                            supportingContent = {
                                Text(
                                    when {
                                        reading.value == null -> "รอข้อมูลล่าสุด"
                                        ageMillis <= 1_500L -> "ข้อมูลสด"
                                        ageMillis <= 4_000L -> "ข้อมูลล่าช้าเล็กน้อย"
                                        else -> "ข้อมูลเก่า ระบบกำลังปรับการเชื่อมต่อ"
                                    },
                                )
                            },
                            trailingContent = {
                                Text(
                                    reading.value?.let { "%.1f %s".format(it, reading.unit) } ?: "--",
                                    fontWeight = FontWeight.Bold,
                                )
                            },
                        )
                        HorizontalDivider()
                    }
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
                    "คำสั่ง Mode 04 อาจลบรหัสความผิดปกติและข้อมูล Freeze-frame " +
                        "ควรบันทึกผลตรวจไว้ก่อนดำเนินการ",
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenIntro("ตรวจปัญหา", "อ่านรหัสก่อนเสมอ แอปจะไม่ลบข้อมูล ECU โดยอัตโนมัติ") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = viewModel::scan,
                    enabled = connectionState == ConnectionState.CONNECTED && connectionUi.initialization.ecuConnected,
                ) { Text("อ่านรหัสปัญหา") }
                TextButton(
                    onClick = { confirmClear = true },
                    enabled = connectionState == ConnectionState.CONNECTED && dtcs.isNotEmpty(),
                ) { Text("ลบรหัส…") }
            }
        }

        when {
            connectionState != ConnectionState.CONNECTED -> {
                item { MessageCard("เชื่อมต่อก่อนตรวจ", "เปิดสวิตช์กุญแจและเชื่อมต่อ ELM327 ก่อน") }
            }

            dtcs.isEmpty() -> {
                item { MessageCard("ยังไม่มีผลตรวจ", "กด “อ่านรหัสปัญหา” เพื่ออ่านข้อมูลจาก ECU") }
            }

            else -> {
                items(dtcs, key = { it.code }) { dtc ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(dtc.code, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Text(dtc.description ?: "ไม่มีคำอธิบายมาตรฐานหรือเป็นรหัสเฉพาะผู้ผลิต")
                            Text("ระบบ ${dtc.category} · ${dtc.status}", style = MaterialTheme.typography.labelMedium)
                        }
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
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenIntro("การตั้งค่า", "ตั้งค่าเฉพาะสิ่งที่จำเป็น ค่าขั้นสูงใช้ค่าแนะนำอัตโนมัติ") }

        item {
            SettingsSection(
                title = "การเชื่อมต่อ",
                detail = "${connectionState.shortLabel()} · ${connectionUi.transportType.displayName()}",
            )
        }
        item {
            ChoiceChips {
                listOf(ObdTransportType.BLUETOOTH_CLASSIC, ObdTransportType.USB).forEach { type ->
                    FilterChip(
                        selected = preferences.preferredTransport == type,
                        onClick = { viewModel.selectTransport(type) },
                        label = { Text(type.displayName()) },
                    )
                }
            }
        }
        item {
            ToggleSetting(
                title = "เชื่อมต่ออะแดปเตอร์ล่าสุดอัตโนมัติ",
                detail = "เหมาะสำหรับ ELM327 Bluetooth ที่ใช้งานประจำ",
                value = preferences.autoConnectLastAdapter,
                onChange = viewModel::setAutoConnectLastAdapter,
            )
        }
        item {
            ToggleSetting(
                title = "เชื่อมต่อใหม่อัตโนมัติ",
                detail = "พยายามกลับมาอ่านข้อมูลเมื่อสัญญาณสะดุด",
                value = preferences.autoReconnect,
                onChange = viewModel::setAutoReconnect,
            )
        }

        item { SettingsSection("การอ่านข้อมูล", "เลือกสมดุลก่อน หากอะแดปเตอร์ราคาประหยัดไม่เสถียร") }
        item {
            ChoiceChips {
                RefreshRate.entries.forEach { rate ->
                    FilterChip(
                        selected = preferences.refreshRate == rate,
                        onClick = { viewModel.setRefreshRate(rate) },
                        label = { Text(rate.thaiLabel()) },
                    )
                }
            }
        }
        item {
            ToggleSetting(
                title = "อ่านต่อเมื่อออกจากแอป",
                detail = "ปิดไว้เพื่อความเสถียรและประหยัดพลังงาน เปิดเมื่อจำเป็นเท่านั้น",
                value = preferences.continuousMonitoring,
                onChange = viewModel::setContinuousMonitoring,
            )
        }

        item { SettingsSection("หน้าปัด", "เลือกโปรไฟล์และแสดงค่าต่ำสุด–สูงสุดของรอบปัจจุบัน") }
        if (dashboards.isEmpty()) {
            item { Text("ยังไม่มีโปรไฟล์ สร้างได้จากหน้า “หน้าปัด”") }
        } else {
            item {
                ChoiceChips {
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
        item {
            ToggleSetting(
                title = "แสดงค่าต่ำสุด–สูงสุด",
                detail = "Peak ถูกตัดออกเพราะซ้ำกับค่าสูงสุด",
                value = preferences.showMinMax,
                onChange = viewModel::setShowMinMax,
            )
        }
        item {
            Button(onClick = viewModel::resetReadingStats, modifier = Modifier.fillMaxWidth()) {
                Text("เริ่มนับค่าต่ำสุด–สูงสุดใหม่")
            }
        }
        item {
            ToggleSetting(
                title = "ลดการเคลื่อนไหว",
                detail = "ลดแอนิเมชันของเข็มเพื่อให้อ่านง่ายและใช้ทรัพยากรน้อยลง",
                value = preferences.reduceMotion,
                onChange = viewModel::setReduceMotion,
            )
        }
        item {
            ToggleSetting(
                title = "เปิดหน้าจอค้างขณะเชื่อมต่อ",
                detail = "เหมาะเมื่อวางโทรศัพท์เป็นหน้าปัดในรถ",
                value = preferences.keepScreenOn,
                onChange = viewModel::setKeepScreenOn,
            )
        }

        item { SettingsSection("การตรวจปัญหา", "เปิดเฉพาะเมื่อต้องส่งข้อมูลให้ผู้พัฒนา") }
        item {
            ToggleSetting(
                title = "เก็บบันทึกระบบ",
                detail = "บันทึกขั้นตอนการเชื่อมต่อและเวลาตอบสนองของ ELM327",
                value = preferences.diagnosticLogging,
                onChange = viewModel::setDiagnosticLogging,
            )
        }
        item {
            Button(
                onClick = {
                    val report = viewModel.exportDiagnosticLogs()
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "NowTuneUp diagnostic report")
                                putExtra(Intent.EXTRA_TEXT, report)
                            },
                            "ส่งรายงานระบบ",
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("ส่งรายงานระบบ") }
        }

        item {
            Text(
                "NowTuneUp 1.8.1 • Android 8+ • Bluetooth Classic + USB",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun ScreenIntro(title: String, detail: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(detail, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SettingsSection(title: String, detail: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(detail, style = MaterialTheme.typography.bodySmall)
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
private fun ChoiceChips(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
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
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(message, style = MaterialTheme.typography.bodyMedium)
            if (actionLabel != null && onAction != null) {
                Button(onClick = { onAction() }) { Text(actionLabel) }
            }
        }
    }
}

private fun ConnectionState.shortLabel(): String = when (this) {
    ConnectionState.DISCONNECTED -> "ยังไม่เชื่อมต่อ"
    ConnectionState.DEVICE_DETECTED -> "พบอุปกรณ์"
    ConnectionState.REQUESTING_PERMISSION -> "รออนุญาต"
    ConnectionState.CONNECTING -> "กำลังเชื่อมต่อ"
    ConnectionState.INITIALIZING -> "กำลังเตรียม ECU"
    ConnectionState.CONNECTED -> "เชื่อมต่อแล้ว"
    ConnectionState.ERROR -> "มีปัญหา"
}

private fun ObdTransportType.displayName(): String = when (this) {
    ObdTransportType.USB -> "USB OTG"
    ObdTransportType.BLUETOOTH_CLASSIC -> "Bluetooth ELM327"
    ObdTransportType.BLE_EXPERIMENTAL -> "BLE ทดลอง"
    ObdTransportType.MOCK -> "ข้อมูลจำลอง"
}

private fun RefreshRate.thaiLabel(): String = when (this) {
    RefreshRate.LOW -> "เสถียรที่สุด"
    RefreshRate.BALANCED -> "แนะนำ"
    RefreshRate.FAST -> "ตอบสนองไว"
}
