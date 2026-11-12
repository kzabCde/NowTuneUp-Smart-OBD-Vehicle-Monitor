package com.nowtuneup.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.ListItemDefaults
import com.nowtuneup.app.BuildConfig
import com.nowtuneup.app.ui.components.NtuScreenHeader
import com.nowtuneup.app.presentation.theme.GraphiteTheme
import com.nowtuneup.app.presentation.theme.DaylightTheme
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
import com.nowtuneup.app.ui.components.NtuPanel as Card
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay
import com.nowtuneup.app.ui.connection.ConnectionHealthPanel
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nowtuneup.app.data.diagnostics.DiagnosticHistoryRepository
import com.nowtuneup.app.data.obd.elm.Elm327Client
import com.nowtuneup.app.data.obd.pid.DerivedPids
import com.nowtuneup.app.data.obd.session.TurboDataQuality
import com.nowtuneup.app.domain.model.ConnectionState
import com.nowtuneup.app.domain.model.Mode06Summary
import com.nowtuneup.app.domain.model.ObdTransportType
import com.nowtuneup.app.domain.model.RefreshRate
import com.nowtuneup.app.feature.live.LiveSessionRepository
import com.nowtuneup.app.presentation.dashboard.MainViewModel
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NtuToolsEntryPoint {
    fun liveSessionRepository(): LiveSessionRepository
    fun diagnosticHistoryRepository(): DiagnosticHistoryRepository
    fun elm327Client(): Elm327Client
}

@Composable
fun LiveDataScreen(viewModel: MainViewModel) {
    val readings by viewModel.readings.collectAsState()
    val connectionState by viewModel.connection.collectAsState()
    val connectionUi by viewModel.connectionUiState.collectAsState()
    val health by viewModel.adapterHealth.collectAsState()
    val turboQuality by viewModel.turboQuality.collectAsState()
    val context = LocalContext.current
    val tools = remember(context) { EntryPointAccessors.fromApplication(context.applicationContext, NtuToolsEntryPoint::class.java) }
    val liveRepository = remember(tools) { tools.liveSessionRepository() }
    var query by rememberSaveable { mutableStateOf("") }
    val preferences by viewModel.dashboardPreferences.collectAsState()
    var showUnsupported by rememberSaveable { mutableStateOf(false) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1_000); nowMillis = System.currentTimeMillis() } }

    DisposableEffect(Unit) {
        viewModel.setLiveDataVisible(true)
        onDispose {
            viewModel.setLiveDataVisible(false)
        }
    }

    val telemetrySamples by liveRepository.samples.collectAsState()

    val visibleReadings = readings.asSequence()
        .filter { it.supported || showUnsupported }
        .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
        .sortedWith(compareByDescending<com.nowtuneup.app.domain.model.VehicleReading> { it.value != null }.thenBy { it.name })
        .toList()

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            NtuScreenHeader(
            modifier = Modifier,
            title = "ข้อมูลสด",
            detail = if (connectionState == ConnectionState.CONNECTED && connectionUi.initialization.ecuConnected) {
                if (health.totalCommands > 0) "OBD ${health.thaiLabel} · ${health.averageLatencyMillis} ms · ค่าจาก ECU" else "เชื่อมต่อแล้ว · รอข้อมูลคำสั่งจริง"
            } else "เชื่อมต่อ ELM327 และเปิดสวิตช์กุญแจก่อน",
        )
        }
        item { com.nowtuneup.app.feature.live.TelemetryPanel(liveRepository, telemetrySamples, connectionState == ConnectionState.CONNECTED, connectionUi.transportType.name) }
        item {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("ค้นหาค่าที่ต้องการ") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        )
        }
        item { FilterChip(selected = showUnsupported, onClick = { showUnsupported = !showUnsupported }, label = { Text("แสดง PID ที่ไม่รองรับ") }) }
        when {
            connectionState != ConnectionState.CONNECTED || !connectionUi.initialization.ecuConnected -> item { MessageCard(
                title = "ยังไม่ได้เชื่อมต่อ ECU",
                message = "ไปที่หน้าเชื่อมต่อ เลือก ELM327 ที่จับคู่ไว้ แล้วกดเชื่อมต่อ",
                actionLabel = "เชื่อมต่อ",
                onAction = viewModel::connectSelected,
            ) }
            visibleReadings.isEmpty() -> item { MessageCard(
                title = if (query.isBlank()) "กำลังรอข้อมูล" else "ไม่พบข้อมูลที่ค้นหา",
                message = if (query.isBlank()) "ระบบกำลังตรวจว่ารถรองรับค่าใดบ้าง" else "ลองใช้คำค้นหาอื่น",
            ) }
            else -> {
                items(visibleReadings, key = { it.pid }) { reading ->
                    val ageMillis = (nowMillis - reading.updatedAt).coerceAtLeast(0L)
                    val turboWaiting = reading.pid == DerivedPids.TURBO_PRESSURE && turboQuality != TurboDataQuality.GOOD
                    Card(Modifier.fillMaxWidth()) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        headlineContent = { Text(reading.name, fontWeight = FontWeight.SemiBold) },
                        overlineContent = { Text(if (reading.pid == DerivedPids.TURBO_PRESSURE) "Turbo จาก MAP − BARO • ${turboQuality.name}" else "PID 01%02X".format(reading.pid)) },
                        supportingContent = { Text(when {
                            !reading.supported -> "รถไม่รองรับ PID นี้"
                            turboWaiting -> "รอ MAP/BARO คู่ใหม่ที่เชื่อถือได้"
                            reading.value == null -> "รอข้อมูลล่าสุด"
                            ageMillis <= 1_500L -> "ข้อมูลสด"
                            ageMillis <= 4_000L -> "ข้อมูลล่าช้าเล็กน้อย"
                            else -> "ข้อมูลเก่า ระบบกำลังลดภาระ ELM327"
                        }) },
                        trailingContent = {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(reading.value?.takeIf { it.isFinite() && reading.supported && !turboWaiting && ageMillis <= preferences.staleAfterMillis }?.let { "%.1f".format(it) } ?: "—", fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace, color = if (reading.value != null && ageMillis <= 1_500L && !turboWaiting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(reading.unit, style = MaterialTheme.typography.labelSmall)
                            }
                        },
                    )
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
    val context = LocalContext.current
    val tools = remember(context) { EntryPointAccessors.fromApplication(context.applicationContext, NtuToolsEntryPoint::class.java) }
    val historyRepository = remember(tools) { tools.diagnosticHistoryRepository() }
    val diagnosticHistory by historyRepository.history.collectAsState()
    val scope = rememberCoroutineScope()
    var mode06 by remember { mutableStateOf<Mode06Summary?>(null) }
    var mode06Message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(overview?.readAtMillis, mode06?.readAtMillis) {
        overview?.let { historyRepository.save(it, mode06) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenIntro("ตรวจสุขภาพรถ", "รหัสปัญหา ความพร้อมของระบบ และประวัติการตรวจในที่เดียว") }
        if (connectionState != ConnectionState.CONNECTED || !connectionUi.initialization.ecuConnected) {
            item { MessageCard("เชื่อมต่อก่อนตรวจ", "เปิดสวิตช์กุญแจและเชื่อมต่อ ELM327 ก่อน") }
        } else {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("การเชื่อมต่อ OBD: ${health.thaiLabel}", fontWeight = FontWeight.Bold)
                        if (health.totalCommands > 0) Text("Latency ${health.averageLatencyMillis} ms • ${"%.1f".format(health.successRate * 100)}% success • ${health.recommendedMode.name}") else Text("รอข้อมูลคุณภาพจากคำสั่งจริง")
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
            item {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            viewModel.pause()
                            val result = try { tools.elm327Client().readMode06Summary() } finally { viewModel.resume() }
                            mode06 = result.getOrNull()
                            mode06Message = result.fold(
                                onSuccess = { "Mode 06: พบ ${it.monitorFrameCount} monitor frames" },
                                onFailure = { "Mode 06: รถ/อะแดปเตอร์ไม่ส่งข้อมูลในครั้งนี้" },
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("อ่าน On-board monitor (Mode 06)") }
            }
            mode06Message?.let { item { Text(it, style = MaterialTheme.typography.bodySmall) } }
            if (identity.vin == null) item { TextButton(onClick = viewModel::refreshVehicleIdentity) { Text("ลองอ่าน VIN อีกครั้ง") } }
        }

        overview?.let { result ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Readiness", fontWeight = FontWeight.Bold)
                        val readiness = result.readiness
                        if (readiness == null) Text("รถไม่ส่งข้อมูล Readiness ในครั้งนี้") else {
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

        if (diagnosticHistory.isNotEmpty()) {
            item { Text("ประวัติการตรวจล่าสุด", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(diagnosticHistory.take(5), key = { it.readAtMillis }) { record ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(record.readAtMillis)), fontWeight = FontWeight.SemiBold)
                        Text("DTC ${record.totalDtcCount} • MIL ${record.milOn?.let { if (it) "ON" else "OFF" } ?: "--"} • Mode 06 ${record.mode06Supported?.let { if (it) "พร้อม" else "ไม่รองรับ" } ?: "ยังไม่อ่าน"}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (dtcs.isEmpty()) {
            item { MessageCard(if (overview == null) "ยังไม่มีผลตรวจ" else "ไม่พบรหัสปัญหา", if (overview == null) "กด “สแกนสุขภาพรถ” เพื่ออ่านข้อมูลจาก ECU" else "Stored, Pending และ Permanent DTC ไม่พบในครั้งนี้") }
        } else {
            item { Text("รหัสปัญหา", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(dtcs, key = { "${it.status}-${it.code}" }) { dtc ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(dtc.code, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text(dtc.description ?: "ไม่มีคำอธิบายมาตรฐานหรือเป็นรหัสเฉพาะผู้ผลิต")
                        Text("${dtc.status} • ระบบ ${dtc.category}", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

private enum class SettingsGroup(val title: String) {
    CONNECTION("การเชื่อมต่อ"), DISPLAY("การแสดงผล"), UNITS("หน่วย"), ALERTS("การเตือน"), STORAGE("ข้อมูลในอุปกรณ์"), ABOUT("เกี่ยวกับ")
}

@Composable
fun SettingsScreen(viewModel: MainViewModel, onManageDashboards: (() -> Unit)? = null) {
    val preferences by viewModel.dashboardPreferences.collectAsState()
    val dashboards by viewModel.dashboards.collectAsState()
    val connectionState by viewModel.connection.collectAsState()
    val connectionUi by viewModel.connectionUiState.collectAsState()
    val health by viewModel.adapterHealth.collectAsState()
    val identity by viewModel.vehicleIdentity.collectAsState()
    val context = LocalContext.current
    var group by rememberSaveable { mutableStateOf(SettingsGroup.CONNECTION) }
    var confirmClearLogs by remember { mutableStateOf(false) }
    if (confirmClearLogs) AlertDialog(
        onDismissRequest = { confirmClearLogs = false }, title = { Text("ลบบันทึกการเชื่อมต่อ?") },
        text = { Text("ลบเฉพาะบันทึกระบบปัจจุบัน ไม่ลบหน้าปัด รถ การบันทึก telemetry หรือประวัติการทดสอบ") },
        confirmButton = { TextButton(onClick = { viewModel.clearDiagnosticLogs(); confirmClearLogs = false }) { Text("ลบบันทึกระบบ") } },
        dismissButton = { TextButton(onClick = { confirmClearLogs = false }) { Text("เก็บไว้") } },
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenIntro("การตั้งค่า", "ปรับการเชื่อมต่อ การอ่าน และการแสดงผลให้เหมาะกับรถของคุณ") }
        item { ChoiceChips { SettingsGroup.entries.forEach { option -> FilterChip(selected = group == option,
            onClick = { group = option }, label = { Text(option.title) }) } } }
        when (group) {
            SettingsGroup.CONNECTION -> {
                item { SettingsSection("การเชื่อมต่อ", "${connectionState.shortLabel()} · ${connectionUi.transportType.displayName()}") }
                item { ChoiceChips { listOf(ObdTransportType.BLUETOOTH_CLASSIC, ObdTransportType.USB).forEach { type -> FilterChip(selected = preferences.preferredTransport == type,
                    onClick = { viewModel.selectTransport(type) }, label = { Text(type.displayName()) }) } } }
                item { ToggleSetting("เชื่อมต่ออะแดปเตอร์ล่าสุดอัตโนมัติ", "ใช้กับอะแดปเตอร์และรถคันเดิม", preferences.autoConnectLastAdapter, viewModel::setAutoConnectLastAdapter) }
                item { ToggleSetting("เชื่อมต่อใหม่อัตโนมัติ", "พยายามกลับมาอ่านเมื่อการเชื่อมต่อสะดุด", preferences.autoReconnect, viewModel::setAutoReconnect) }
                item { SettingsSection("จังหวะการอ่าน", "ระบบปรับภาระตามการตอบกลับจริงของ ELM327") }
                item { ChoiceChips { RefreshRate.entries.forEach { rate -> FilterChip(selected = preferences.refreshRate == rate,
                    onClick = { viewModel.setRefreshRate(rate) }, label = { Text(rate.thaiLabel()) }) } } }
                item { Text("ความถี่ของแต่ละ PID ขึ้นกับรถและอะแดปเตอร์ แอปอาจลดจังหวะเมื่อพบ timeout", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item { ToggleSetting("อ่านต่อเมื่อออกจากแอป", "ปิดไว้เพื่อประหยัดพลังงานและหยุดการอ่านเมื่อออกจากหน้าแอป", preferences.continuousMonitoring, viewModel::setContinuousMonitoring) }
                item { ConnectionHealthPanel(health, connectionState == ConnectionState.CONNECTED) }
                item { OutlinedButton(onClick = viewModel::runAdapterSelfTest, modifier = Modifier.fillMaxWidth()) { Text("ทดสอบความเข้ากันได้ของ ELM327") } }
            }
            SettingsGroup.DISPLAY -> {
                item { SettingsSection("หน้าตาแอป", "ธีมแอปแยกจากสีของหน้าปัดที่คุณบันทึกไว้") }
                item { ChoiceChips { listOf(GraphiteTheme, DaylightTheme).forEach { theme -> FilterChip(selected = preferences.theme == theme,
                    onClick = { viewModel.selectTheme(theme) }, label = { Text(if (theme.name == "Graphite") "มืด · Graphite" else "สว่าง · Daylight") }) } } }
                if (preferences.theme != GraphiteTheme && preferences.theme != DaylightTheme) item { Text("กำลังใช้ธีมเดิม: ${preferences.theme.name} · สีที่บันทึกไว้ยังคงอยู่", style = MaterialTheme.typography.bodySmall) }
                item { ToggleSetting("ลดการเคลื่อนไหว", "ลดการเลื่อนหน้าและการเคลื่อนไหวของเข็ม", preferences.reduceMotion, viewModel::setReduceMotion) }
                item { ToggleSetting("เปิดหน้าจอค้างขณะเชื่อมต่อ", "ใช้เมื่อวางโทรศัพท์เป็นหน้าปัด", preferences.keepScreenOn, viewModel::setKeepScreenOn) }
                item { ToggleSetting("โหมดขับรถ", "ป้องกันการแก้ไขหน้าปัดระหว่างใช้งาน", preferences.drivingMode, viewModel::setDrivingMode) }
                item { ToggleSetting("เลื่อนเปลี่ยนหน้าปัด", "ใช้การปัดเพื่อสลับหน้าจอที่บันทึกไว้", preferences.swipePages, viewModel::setSwipePages) }
                item { ToggleSetting("เต็มจออัตโนมัติเมื่อเชื่อมต่อ", "ซ่อนเมนูหลักเมื่อเข้าสู่หน้าปัด", preferences.autoFocusOnConnect, viewModel::setAutoFocusOnConnect) }
                item { ToggleSetting("จอรถยนต์แบบเต็มจอ", "ปรับพื้นที่แสดงผลเมื่อใช้จอ Head unit", preferences.headUnitImmersive, viewModel::setHeadUnitImmersive) }
                item { ToggleSetting("แสดงค่าต่ำสุด–สูงสุด", "ใช้ค่าจาก session ปัจจุบัน", preferences.showMinMax, viewModel::setShowMinMax) }
                if (dashboards.isNotEmpty()) item { ChoiceChips { dashboards.forEach { dashboard -> FilterChip(selected = preferences.selectedDashboardId == dashboard.id,
                    onClick = { viewModel.selectDashboard(dashboard.id) }, label = { Text(dashboard.name) }) } } }
                onManageDashboards?.let { action -> item { OutlinedButton(onClick = action, modifier = Modifier.fillMaxWidth()) { Text("จัดการหน้าปัด") } } }
            }
            SettingsGroup.UNITS -> {
                item { SettingsSection("หน่วยของแต่ละ Widget", "เปลี่ยนหน่วยพร้อมช่วงสเกลและค่าการเตือนในคุณสมบัติ Widget") }
                item { MessageCard("เลือกหน่วยที่เหมาะกับข้อมูล", "ความเร็ว อุณหภูมิ และความดันตั้งค่าแยกได้ในหน้าจัดหน้าปัด หน่วยที่แปลงไม่ได้จะไม่ถูกนำมาแสดงเป็นค่าจริง") }
                onManageDashboards?.let { action -> item { Button(onClick = action, modifier = Modifier.fillMaxWidth()) { Text("เปิดจัดการหน้าปัด") } } }
            }
            SettingsGroup.ALERTS -> {
                item { SettingsSection("การแจ้งเตือนค่า", "กำหนด threshold ของแต่ละ Widget ในตัวแก้ไขหน้าปัด") }
                item { ToggleSetting("ปิดเสียงและการสั่นทั้งหมด", "ป้ายสถานะยังคงแสดงค่าการเตือน", preferences.muteAlerts, viewModel::setMuteAlerts) }
                item { ToggleSetting("เสียงแจ้งเตือน", "ใช้เมื่อค่าข้าม threshold ที่คุณตั้งไว้", preferences.alertSound, viewModel::setAlertSound) }
                item { ToggleSetting("สั่นแจ้งเตือน", "อุปกรณ์ต้องรองรับการสั่น", preferences.alertVibration, viewModel::setAlertVibration) }
                item { Text("ช่วงพักระหว่างแจ้งเตือน · ${preferences.alertCooldownSeconds} s", style = MaterialTheme.typography.titleMedium) }
                item { ChoiceChips { listOf(5, 15, 30, 60).forEach { seconds -> FilterChip(selected = preferences.alertCooldownSeconds == seconds,
                    onClick = { viewModel.setAlertCooldownSeconds(seconds) }, label = { Text("$seconds s") }) } } }
                item { Text("Threshold เป็นการเตือนเพื่อสังเกตค่าที่คุณกำหนด ไม่ใช่ผลวินิจฉัยหรือขีดจำกัดที่ยืนยันจากผู้ผลิต", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            SettingsGroup.STORAGE -> {
                item { SettingsSection("ข้อมูลในอุปกรณ์", "แยกหน้าปัด การบันทึก และประวัติการทดสอบออกจากกัน") }
                item { ToggleSetting("เก็บบันทึกระบบ", "ใช้ตรวจปัญหาการตอบกลับและการเชื่อมต่อ", preferences.diagnosticLogging, viewModel::setDiagnosticLogging) }
                item { OutlinedButton(onClick = viewModel::resetReadingStats, modifier = Modifier.fillMaxWidth()) { Text("เริ่มนับ Min/Max ของ session ใหม่") } }
                item { Text("การรีเซ็ต Min/Max ไม่เปลี่ยนการบันทึกหรือผลทดสอบที่บันทึกไว้", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item { OutlinedButton(onClick = { shareText(context, "NowTuneUp diagnostic report", viewModel.exportDiagnosticLogs()) }, modifier = Modifier.fillMaxWidth()) { Text("ส่งรายงานระบบปัจจุบัน") } }
                item { OutlinedButton(onClick = { shareText(context, "NowTuneUp last session report", viewModel.exportLastSessionReport()) }, modifier = Modifier.fillMaxWidth()) { Text("ส่งรายงาน Session ล่าสุด") } }
                item { TextButton(onClick = { confirmClearLogs = true }) { Text("ลบบันทึกการเชื่อมต่อ…") } }
                item { Text("จัดการและส่งออก telemetry จากหน้าข้อมูลสด ลบผลทดสอบเป็นรายรายการจาก History", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            SettingsGroup.ABOUT -> {
                item { SettingsSection("NowTuneUp", "Professional automotive technology · local-first") }
                item { MessageCard("เวอร์ชัน ${BuildConfig.VERSION_NAME}", "Android 8.0+ · Bluetooth Classic + USB OTG") }
                item { MessageCard("ข้อมูลรถที่ตรวจพบ", "VIN: ${identity.vin ?: "ยังอ่านไม่ได้"}\nAdapter: ${connectionUi.initialization.adapterIdentity ?: "ยังไม่ทราบ"}") }
                item { Text("ค่าประมาณจาก OBD ไม่ใช่ความแม่นยำแบบอุปกรณ์จับเวลาที่รับรองจาก GPS และไม่ใช่ผลวินิจฉัยจากช่าง", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

private fun shareText(context: android.content.Context, subject: String, text: String) {
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, subject); putExtra(Intent.EXTRA_TEXT, text) }, "ส่งรายงานระบบ"))
}

@Composable
private fun ScreenIntro(title: String, detail: String) { NtuScreenHeader(title, detail) }
@Composable
private fun SettingsSection(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable
private fun ToggleSetting(title: String, detail: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().toggleable(value = value, role = Role.Switch, onValueChange = onChange).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = value, onCheckedChange = null)
        }
    }
}
@Composable
private fun ChoiceChips(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) { Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content) }
@Composable
private fun MessageCard(title: String, message: String, actionLabel: String? = null, onAction: (() -> Any)? = null) {
    Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) { Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(message, style = MaterialTheme.typography.bodyMedium); if (actionLabel != null && onAction != null) Button(onClick = { onAction() }) { Text(actionLabel) } } }
}
private fun ConnectionState.shortLabel(): String = when (this) { ConnectionState.DISCONNECTED -> "ยังไม่เชื่อมต่อ"; ConnectionState.DEVICE_DETECTED -> "พบอุปกรณ์"; ConnectionState.REQUESTING_PERMISSION -> "รออนุญาต"; ConnectionState.CONNECTING -> "กำลังเชื่อมต่อ"; ConnectionState.INITIALIZING -> "กำลังเตรียม ECU"; ConnectionState.CONNECTED -> "เชื่อมต่อแล้ว"; ConnectionState.ERROR -> "มีปัญหา" }
private fun ObdTransportType.displayName(): String = when (this) { ObdTransportType.USB -> "USB OTG"; ObdTransportType.BLUETOOTH_CLASSIC -> "Bluetooth ELM327"; ObdTransportType.BLE_EXPERIMENTAL -> "BLE ทดลอง"; ObdTransportType.MOCK -> "ข้อมูลจำลอง" }
private fun RefreshRate.thaiLabel(): String = when (this) { RefreshRate.LOW -> "เสถียรที่สุด"; RefreshRate.BALANCED -> "แนะนำ"; RefreshRate.FAST -> "ตอบสนองไว" }
