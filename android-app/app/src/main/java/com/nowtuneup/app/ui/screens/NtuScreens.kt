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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.nowtuneup.app.data.obd.session.TurboDataQuality
import com.nowtuneup.app.domain.model.ConnectionState
import com.nowtuneup.app.domain.model.ObdTransportType
import com.nowtuneup.app.domain.model.RefreshRate
import com.nowtuneup.app.presentation.dashboard.MainViewModel

@Composable
fun LiveDataScreen(viewModel: MainViewModel) {
    val readings by viewModel.readings.collectAsState()
    val connectionState by viewModel.connection.collectAsState()
    val connectionUi by viewModel.connectionUiState.collectAsState()
    val health by viewModel.adapterHealth.collectAsState()
    val turboQuality by viewModel.turboQuality.collectAsState()
    var query by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        viewModel.setLiveDataVisible(true)
        onDispose { viewModel.setLiveDataVisible(false) }
    }

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
                "OBD ${health.thaiLabel} • ${health.averageLatencyMillis} ms • ระบบปรับความถี่ให้อัตโนมัติ"
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
                        val turboWaiting = reading.pid == DerivedPids.TURBO_PRESSURE && turboQuality != TurboDataQuality.GOOD
                        ListItem(
                            headlineContent = { Text(reading.name, fontWeight = FontWeight.SemiBold) },
                            overlineContent = {
                                Text(
                                    if (reading.pid == DerivedPids.TURBO_PRESSURE) {
                                        "Turbo จาก MAP − BARO • ${turboQuality.name}"
                                    } else {
                                        "PID 01%02X".format(reading.pid)
                                    },
                                )
                            },
                            supportingContent = {
                                Text(
                                    when {
                                        turboWaiting -> "รอ MAP/BARO คู่ใหม่ที่เชื่อถือได้"
                                        reading.value == null -> "รอข้อมูลล่าสุด"
                                        ageMillis <= 1_500L -> "ข้อมูลสด"
                                        ageMillis <= 4_000L -> "ข้อมูลล่าช้าเล็กน้อย"
                                        else -> "ข้อมูลเก่า ระบบกำลังลดภาระ ELM327"
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
    val overview by viewModel.diagnosticOverview.collectAsState()
    val selfTest by viewModel.adapterSelfTest.collectAsState()
    val identity by viewModel.vehicleIdentity.collectAsState()
    val health by viewModel.adapterHealth.collectAsState()
    val connectionState by viewModel.connection.collectAsState()
    val connectionUi by viewModel.connectionUiState.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("ยืนยันการลบรหัสปัญหา") },
            text = {
                Text(
                    "คำสั่ง Mode 04 อาจลบรหัส Stored และ Freeze-frame บางส่วน " +
                        "Pending/Permanent DTC อาจยังคงอยู่ตามเงื่อนไขของ ECU",
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
        item { ScreenIntro("ตรวจสุขภาพรถ", "อ่านอย่างเดียวเป็นค่าเริ่มต้น: DTC, Readiness, Freeze-frame trigger และ VIN") }

        if (connectionState != ConnectionState.CONNECTED || !connectionUi.initialization.ecuConnected) {
            item { MessageCard("เชื่อมต่อก่อนตรวจ", "เปิดสวิตช์กุญแจและเชื่อมต่อ ELM327 ก่อน") }
        } else {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("การเชื่อมต่อ OBD: ${health.thaiLabel}", fontWeight = FontWeight.Bold)
                        Text("Latency ${health.averageLatencyMillis} ms • ${"%.1f".format(health.successRate * 100)}% success • ${health.recommendedMode.name}")
                        Text("VIN: ${identity.vin ?: overview?.vin ?: "ยังอ่านไม่ได้"}")
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = viewModel::scan) { Text("สแกนสุขภาพรถ") }
                    OutlinedButton(onClick = viewModel::runAdapterSelfTest) { Text("ทดสอบ ELM327") }
                }
            }
            if (identity.vin == null) {
                item { TextButton(onClick = viewModel::refreshVehicleIdentity) { Text("ลองอ่าน VIN อีกครั้ง") } }
            }
        }

        overview?.let { result ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Readiness", fontWeight = FontWeight.Bold)
                        val readiness = result.readiness
                        if (readiness == null) {
                            Text("รถไม่ส่งข้อมูล Readiness ในครั้งนี้")
                        } else {
                            Text("MIL: ${if (readiness.milOn) "ON" else "OFF"} • DTC count ${readiness.dtcCount}")
                            Text("Monitor bytes: ${readiness.rawMonitorBytes}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("Freeze-frame trigger: ${result.freezeFrame?.triggerDtc ?: "ไม่มี/ไม่รองรับ"}")
                    }
                }
            }
        }

        selfTest?.let { result ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("ผลทดสอบ ELM327", fontWeight = FontWeight.Bold)
                        Text("ผ่าน ${result.passedChecks}/${result.totalChecks} • ${result.averageLatencyMillis} ms • แนะนำ ${result.recommendedMode}")
                        result.voltage?.let { Text("แรงดันที่อะแดปเตอร์อ่านได้ ${"%.1f".format(it)} V") }
                        Text("Time Slip: ${if (result.timeSlipSupported) "รองรับ" else "ไม่รองรับ"} • Turbo: ${if (result.turboSupported) "รองรับ" else "ไม่รองรับ"}")
                        result.details.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }

        if (dtcs.isEmpty()) {
            item {
                MessageCard(
                    if (overview == null) "ยังไม่มีผลตรวจ" else "ไม่พบรหัสปัญหา",
                    if (overview == null) "กด “สแกนสุขภาพรถ” เพื่ออ่านข้อมูลจาก ECU" else "Stored, Pending และ Permanent DTC ไม่พบในครั้งนี้",
                )
            }
        } else {
            item {
                Text("รหัสปัญหา", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            items(dtcs, key = { "${it.status}-${it.code}" }) { dtc ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(dtc.code, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text(dtc.description ?: "ไม่มีคำอธิบายมาตรฐานหรือเป็นรหัสเฉพาะผู้ผลิต")
                        Text("${dtc.status} • ระบบ ${dtc.category}", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            item {
                TextButton(
                    onClick = { confirmClear = true },
                    enabled = dtcs.any { it.status == "Stored" },
                ) { Text("ลบ Stored DTC…") }
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
    val health by viewModel.adapterHealth.collectAsState()
    val identity by viewModel.vehicleIdentity.collectAsState()
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenIntro("การตั้งค่า", "ค่าขั้นสูงถูกปรับอัตโนมัติตามสุขภาพของ ELM327") }

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
                detail = "เหมาะสำหรับ ELM327 ที่ใช้กับรถคันเดิมเป็นประจำ",
                value = preferences.autoConnectLastAdapter,
                onChange = viewModel::setAutoConnectLastAdapter,
            )
        }
        item {
            ToggleSetting(
                title = "เชื่อมต่อใหม่อัตโนมัติ",
                detail = "พยายามกลับมาอ่านข้อมูลเมื่อ Bluetooth หรือ ECU สะดุด",
                value = preferences.autoReconnect,
                onChange = viewModel::setAutoReconnect,
            )
        }

        item {
            SettingsSection(
                "การอ่านข้อมูล",
                "สุขภาพ ${health.thaiLabel} • ${health.averageLatencyMillis} ms • ระบบแนะนำ ${health.recommendedMode.name}",
            )
        }
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
            Text(
                "หาก ELM327 เริ่ม timeout แอปจะลดความถี่ลงเอง แม้เลือกโหมดตอบสนองไว เพื่อป้องกันการหลุด",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item {
            ToggleSetting(
                title = "อ่านต่อเมื่อออกจากแอป",
                detail = "ปิดไว้เพื่อความเสถียรและประหยัดพลังงาน",
                value = preferences.continuousMonitoring,
                onChange = viewModel::setContinuousMonitoring,
            )
        }

        item { SettingsSection("รถคันนี้", "VIN ใช้จำข้อมูลที่รถรองรับและ profile ของ adapter") }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(identity.vin ?: "ยังอ่าน VIN ไม่ได้", fontWeight = FontWeight.Bold)
                    Text(connectionUi.initialization.adapterIdentity ?: "ยังไม่ทราบรุ่น ELM327")
                }
            }
        }

        item { SettingsSection("หน้าปัด", "อ่านเฉพาะ PID ที่ widget ปัจจุบันต้องใช้ เพื่อลดภาระ ELM327") }
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
                detail = "ใช้ Min/Max ของ session ปัจจุบัน โดยไม่มี Peak ซ้ำซ้อน",
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
                detail = "ลดแอนิเมชันของเข็มและใช้ทรัพยากรน้อยลง",
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

        item { SettingsSection("การตรวจปัญหา", "รายงานจะรวม latency, success rate, VIN และ recovery count") }
        item {
            ToggleSetting(
                title = "เก็บบันทึกระบบ",
                detail = "เปิดเมื่อกำลังตรวจปัญหา ELM327 หรือการหลุดของข้อมูลสด",
                value = preferences.diagnosticLogging,
                onChange = viewModel::setDiagnosticLogging,
            )
        }
        item {
            Button(onClick = viewModel::runAdapterSelfTest, modifier = Modifier.fillMaxWidth()) {
                Text("ทดสอบความเข้ากันได้ของ ELM327")
            }
        }
        item {
            Button(
                onClick = { shareText(context, "NowTuneUp diagnostic report", viewModel.exportDiagnosticLogs()) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("ส่งรายงานระบบปัจจุบัน") }
        }
        item {
            OutlinedButton(
                onClick = { shareText(context, "NowTuneUp last session report", viewModel.exportLastSessionReport()) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("ส่งรายงาน Session ล่าสุด") }
        }

        item {
            Text(
                "NowTuneUp 1.9.0 • Android 8+ • Bluetooth Classic + USB",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }
}

private fun shareText(context: android.content.Context, subject: String, text: String) {
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, text)
            },
            "ส่งรายงานระบบ",
        ),
    )
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
