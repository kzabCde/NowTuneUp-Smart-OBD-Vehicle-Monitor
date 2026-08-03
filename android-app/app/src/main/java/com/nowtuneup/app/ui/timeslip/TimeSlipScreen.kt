package com.nowtuneup.app.ui.timeslip

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.nowtuneup.app.BuildConfig
import com.nowtuneup.app.domain.model.ConnectionState
import com.nowtuneup.app.feature.timeslip.domain.DistanceSplit
import com.nowtuneup.app.feature.timeslip.domain.DistanceTarget
import com.nowtuneup.app.feature.timeslip.domain.MeasurementQuality
import com.nowtuneup.app.feature.timeslip.domain.PerformanceSample
import com.nowtuneup.app.feature.timeslip.domain.SpeedMilestone
import com.nowtuneup.app.feature.timeslip.domain.TimeSlipConstants
import com.nowtuneup.app.feature.timeslip.domain.TimeSlipRecord
import com.nowtuneup.app.feature.timeslip.domain.TimeSlipStatus
import com.nowtuneup.app.feature.timeslip.domain.TimeSlipTestMode
import com.nowtuneup.app.feature.timeslip.domain.TimeSlipUnitSystem
import com.nowtuneup.app.presentation.timeslip.TimeSlipHistorySort
import com.nowtuneup.app.presentation.timeslip.TimeSlipPage
import com.nowtuneup.app.presentation.timeslip.TimeSlipUiState
import com.nowtuneup.app.presentation.timeslip.TimeSlipViewModel
import com.nowtuneup.app.util.TimeSlipExporter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun TimeSlipScreen(viewModel: TimeSlipViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current
    var confirmCancel by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.onLocationPermissionResult(granted)
    }

    DisposableEffect(state.active) {
        val previous = view.keepScreenOn
        if (state.active) view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }

    if (state.locationExplanationVisible) {
        AlertDialog(
            onDismissRequest = viewModel::dismissLocationExplanation,
            title = { Text("อนุญาตตำแหน่งเพื่อวัดระยะ") },
            text = {
                Text(
                    "Time Slip ใช้ GPS เพื่อวัด 60 ft, 330 ft และระยะไมล์ รวมถึงตรวจความน่าเชื่อถือของผล " +
                        "พิกัดจะไม่ถูกใส่ในรูปที่แชร์โดยค่าเริ่มต้น",
                )
            },
            confirmButton = {
                Button(onClick = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                }) { Text("อนุญาตตำแหน่ง") }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissLocationExplanation) { Text("ไว้ภายหลัง") } },
        )
    }

    if (state.safetyDialogVisible) {
        AlertDialog(
            onDismissRequest = viewModel::dismissSafetyDialog,
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("คำเตือนด้านความปลอดภัย") },
            text = {
                Text(
                    "ใช้การทดสอบสมรรถนะนี้เฉพาะในสนามปิด สนามแข่ง หรือพื้นที่ส่วนบุคคลที่อนุญาตให้ทดสอบรถเท่านั้น " +
                        "ห้ามทดสอบอัตราเร่งบนถนนสาธารณะ ปฏิบัติตามกฎหมายท้องถิ่นและให้ความปลอดภัยมาก่อนเสมอ",
                )
            },
            confirmButton = { Button(onClick = viewModel::acknowledgeSafety) { Text("รับทราบและยอมรับ") } },
            dismissButton = { TextButton(onClick = viewModel::dismissSafetyDialog) { Text("ยกเลิก") } },
        )
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("ยกเลิกการทดสอบหรือไม่") },
            text = { Text("ผลที่ยังไม่เสร็จจะถูกทำเครื่องหมายว่า Cancelled และจะไม่ถูกนับเป็นสถิติที่ดีที่สุด") },
            confirmButton = {
                Button(onClick = { confirmCancel = false; viewModel.cancelTest() }) { Text("ยกเลิกการทดสอบ") }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("ทดสอบต่อ") } },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("ล้างประวัติ Time Slip") },
            text = { Text("รายการที่บันทึกและตัวอย่างกราฟทั้งหมดจะถูกลบจากเครื่อง การดำเนินการนี้ย้อนกลับไม่ได้") },
            confirmButton = {
                Button(onClick = { confirmClear = false; viewModel.clearHistory() }) { Text("ล้างทั้งหมด") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("ยกเลิก") } },
        )
    }

    state.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text("Time Slip") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("ตกลง") } },
        )
    }

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!state.active) {
                TimeSlipPageTabs(state.page, viewModel::setPage)
            }
            when {
                state.active -> TimeSlipLiveContent(state, onCancel = { confirmCancel = true })
                state.page == TimeSlipPage.HISTORY -> TimeSlipHistoryContent(
                    state = state,
                    onSelect = viewModel::selectRecord,
                    onDelete = viewModel::deleteRecord,
                    onClear = { confirmClear = true },
                    onVehicleFilter = viewModel::setHistoryVehicleFilter,
                    onModeFilter = viewModel::setHistoryModeFilter,
                    onSort = viewModel::setHistorySort,
                    onIncludeLow = viewModel::setIncludeLowConfidenceInBest,
                )
                state.page == TimeSlipPage.RESULT -> TimeSlipResultContent(
                    state = state,
                    onSave = viewModel::saveResult,
                    onDelete = { id -> viewModel.deleteRecord(id); viewModel.resetTest() },
                    onRunAgain = viewModel::resetTest,
                    onShareText = { record ->
                        context.startActivity(Intent.createChooser(TimeSlipExporter.shareTextIntent(record), "แชร์ Time Slip"))
                    },
                    onShareCsv = { record ->
                        runCatching {
                            val file = TimeSlipExporter.createCsvFile(context, record)
                            context.startActivity(
                                Intent.createChooser(
                                    TimeSlipExporter.shareFileIntent(context, file, "text/csv", "NTU Time Slip CSV"),
                                    "ส่งออก CSV",
                                ),
                            )
                        }
                    },
                    onShareImage = { record ->
                        runCatching {
                            val file = TimeSlipExporter.createImageFile(context, record)
                            context.startActivity(
                                Intent.createChooser(
                                    TimeSlipExporter.shareFileIntent(context, file, "image/png", "NTU Performance Time Slip"),
                                    "แชร์รูป Time Slip",
                                ),
                            )
                        }
                    },
                )
                else -> TimeSlipSetupContent(
                    state = state,
                    viewModel = viewModel,
                    onOpenLocationSettings = {
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    },
                )
            }
        }
    }
}

@Composable
private fun TimeSlipPageTabs(selected: TimeSlipPage, onSelect: (TimeSlipPage) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected in setOf(TimeSlipPage.SETUP, TimeSlipPage.LIVE),
            onClick = { onSelect(TimeSlipPage.SETUP) },
            label = { Text("ตั้งค่าการทดสอบ") },
            leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null) },
        )
        FilterChip(
            selected = selected == TimeSlipPage.HISTORY,
            onClick = { onSelect(TimeSlipPage.HISTORY) },
            label = { Text("ประวัติ") },
            leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
        )
    }
}

@Composable
private fun TimeSlipSetupContent(
    state: TimeSlipUiState,
    viewModel: TimeSlipViewModel,
    onOpenLocationSettings: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Time Slip", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text("วัดอัตราเร่งและเวลาตามระยะด้วย OBD-II + GPS + เซนเซอร์โทรศัพท์")
        }
        item {
            SafetyCard(state.safetyAccepted, onAcknowledge = viewModel::showSafetyDialog)
        }
        item {
            ConnectionQualityCard(state)
        }
        item {
            SetupSection("รูปแบบการออกตัว", "ออกตัวจากหยุดนิ่ง หรือจับช่วงความเร็วขณะรถกำลังวิ่ง") {
                ChoiceRow {
                    FilterChip(
                        selected = state.config.testMode == TimeSlipTestMode.STANDING_START,
                        onClick = { viewModel.updateMode(TimeSlipTestMode.STANDING_START) },
                        label = { Text("ออกตัวจากหยุดนิ่ง") },
                    )
                    FilterChip(
                        selected = state.config.testMode == TimeSlipTestMode.ROLLING_START,
                        onClick = { viewModel.updateMode(TimeSlipTestMode.ROLLING_START) },
                        label = { Text("จับช่วงความเร็ว") },
                    )
                }
            }
        }
        item {
            SetupSection("เป้าหมายระยะทาง", "เมื่อเลือกเป้าหมายไกล ระบบจะบันทึกทุกจุดก่อนหน้าให้อัตโนมัติ") {
                ChoiceRow {
                    DistanceTarget.entries.forEach { target ->
                        FilterChip(
                            selected = state.config.selectedDistanceTarget == target,
                            onClick = { viewModel.updateDistanceTarget(target) },
                            label = { Text(target.displayName) },
                        )
                    }
                }
                if (state.config.selectedDistanceTarget != DistanceTarget.SPEED_ONLY) {
                    Text(
                        "ระบบจะบันทึก: " + TimeSlipConstants.orderedDistanceTargets
                            .filter { it.meters <= state.config.selectedDistanceTarget.meters }
                            .joinToString { it.displayName },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item {
            SetupSection("เป้าหมายความเร็ว", "0–60 mph คือประมาณ 96.56 km/h และไม่ใช่ 0–60 km/h") {
                state.config.speedMilestones.filterNot { it.id == "custom-range" }.forEach { milestone ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = milestone.enabled && state.config.testMode != TimeSlipTestMode.ROLLING_START,
                            enabled = state.config.testMode != TimeSlipTestMode.ROLLING_START,
                            onCheckedChange = { viewModel.toggleMilestone(milestone.id) },
                        )
                        Text(milestone.displayName)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("ช่วงความเร็วกำหนดเอง", modifier = Modifier.weight(1f))
                    Switch(
                        checked = state.config.customSpeedEnabled || state.config.testMode == TimeSlipTestMode.ROLLING_START,
                        enabled = state.config.testMode != TimeSlipTestMode.ROLLING_START,
                        onCheckedChange = viewModel::setCustomSpeedEnabled,
                    )
                }
                if (state.config.customSpeedEnabled || state.config.testMode == TimeSlipTestMode.ROLLING_START) {
                    SpeedRangeFields(state, viewModel)
                }
            }
        }
        item {
            SetupSection("หน่วยและชื่อรถ", "ชื่อรถช่วยแยกประวัติหลายคัน") {
                ChoiceRow {
                    FilterChip(
                        selected = state.config.unitSystem == TimeSlipUnitSystem.METRIC,
                        onClick = { viewModel.updateUnitSystem(TimeSlipUnitSystem.METRIC) },
                        label = { Text("km/h") },
                    )
                    FilterChip(
                        selected = state.config.unitSystem == TimeSlipUnitSystem.IMPERIAL,
                        onClick = { viewModel.updateUnitSystem(TimeSlipUnitSystem.IMPERIAL) },
                        label = { Text("mph") },
                    )
                }
                OutlinedTextField(
                    value = state.config.vehicleName,
                    onValueChange = viewModel::updateVehicleName,
                    label = { Text("ชื่อรถ เช่น Fortuner 2015") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        }
        if (!state.gpsStatus.permissionGranted && state.config.requiresGps) {
            item {
                Button(onClick = viewModel::requestLocationPermissionExplanation, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.GpsFixed, contentDescription = null)
                    Text("  อนุญาต GPS สำหรับวัดระยะ")
                }
            }
        }
        if (state.gpsStatus.permissionGranted && !state.gpsStatus.providerEnabled && state.config.requiresGps) {
            item {
                OutlinedButton(onClick = onOpenLocationSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("เปิดการตั้งค่าตำแหน่งของ Android")
                }
            }
        }
        item {
            Button(
                onClick = viewModel::armTest,
                modifier = Modifier.fillMaxWidth().height(58.dp),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Text("  เตรียมเริ่มทดสอบ", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (BuildConfig.DEBUG) {
            item {
                OutlinedButton(onClick = viewModel::runSimulation, modifier = Modifier.fillMaxWidth()) {
                    Text("จำลองการวิ่งสำหรับนักพัฒนา")
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SafetyCard(accepted: Boolean, onAcknowledge: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (accepted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (accepted) Icons.Default.CheckCircle else Icons.Default.Warning, contentDescription = null)
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(if (accepted) "รับทราบคำเตือนแล้ว" else "ใช้เฉพาะสนามปิดหรือพื้นที่ส่วนบุคคล", fontWeight = FontWeight.Bold)
                Text("ห้ามทดสอบอัตราเร่งบนถนนสาธารณะ", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onAcknowledge) { Text(if (accepted) "อ่านอีกครั้ง" else "อ่านและยอมรับ") }
        }
    }
}

@Composable
private fun ConnectionQualityCard(state: TimeSlipUiState) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("ความพร้อมของระบบ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            StatusLine(
                "OBD-II",
                if (state.connectionState == ConnectionState.CONNECTED) "เชื่อมต่อแล้ว · ${state.adapterName.orEmpty()}" else state.connectionState.thaiLabel(),
                state.connectionState == ConnectionState.CONNECTED,
            )
            StatusLine(
                "GPS",
                when {
                    !state.gpsStatus.permissionGranted -> "ยังไม่ได้อนุญาตตำแหน่ง"
                    !state.gpsStatus.providerEnabled -> "GPS ปิดอยู่"
                    !state.gpsStatus.hasPositionLock -> "กำลังหาตำแหน่ง"
                    state.gpsStatus.isMock -> "พบตำแหน่งจำลอง"
                    else -> "ความคลาดเคลื่อน ±${state.gpsStatus.accuracyMeters?.let { "%.1f".format(it) } ?: "—"} m"
                },
                state.gpsStatus.hasPositionLock && !state.gpsStatus.isMock,
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val displayedSpeed = displaySpeed(state.currentSpeedKmh, state.config.unitSystem)
                val speedUnit = if (state.config.unitSystem == TimeSlipUnitSystem.METRIC) "km/h" else "mph"
                MetricChip("Speed", "${"%.1f".format(displayedSpeed)} $speedUnit")
                MetricChip("OBD", "${"%.1f".format(state.obdSampleRateHz)} Hz")
                MetricChip("GPS", "${"%.1f".format(state.gpsSampleRateHz)} Hz")
                MetricChip("Accuracy", state.averageGpsAccuracyMeters?.let { "±${"%.1f".format(it)} m" } ?: "—")
            }
        }
    }
}

@Composable
private fun TimeSlipLiveContent(state: TimeSlipUiState, onCancel: () -> Unit) {
    val speed = displaySpeed(state.engine.currentSpeedKmh, state.config.unitSystem)
    val unit = if (state.config.unitSystem == TimeSlipUnitSystem.METRIC) "km/h" else "mph"
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(state.engine.status.thaiLabel(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("%.0f".format(speed), fontSize = 88.sp, fontWeight = FontWeight.Black)
                    Text(unit, fontSize = 22.sp)
                    Text(TimeSlipExporter.formatSeconds(state.engine.elapsedMs), fontSize = 42.sp, fontWeight = FontWeight.Bold)
                    Text("ระยะ ${"%.1f".format(state.engine.accumulatedDistanceMeters)} m")
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard("จุดถัดไป", state.engine.nextDistanceTarget?.displayName ?: "เป้าหมายความเร็ว", Modifier.weight(1f))
                MetricCard("คุณภาพ", state.engine.quality.thaiLabel(), Modifier.weight(1f))
            }
        }
        if (state.engine.status == TimeSlipStatus.ARMED) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Text(
                        if (state.config.testMode == TimeSlipTestMode.STANDING_START) {
                            "จอดรถให้นิ่งประมาณ 1 วินาที แล้วออกตัว ระบบจะเริ่มจับเวลาอัตโนมัติ ไม่ใช้เวลาที่กดปุ่ม"
                        } else {
                            "รักษาความเร็วต่ำกว่า ${state.config.rollingStartSpeedKmh.format1()} km/h แล้วเร่งผ่านจุดเริ่ม ระบบจะจับเวลาอัตโนมัติ"
                        },
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
        item {
            CompletedCheckpoints(state.engine.speedMilestones, state.engine.distanceSplits)
        }
        item {
            ConnectionQualityCard(state)
        }
        item {
            Button(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Text("  ยกเลิกการทดสอบ")
            }
        }
    }
}

@Composable
private fun CompletedCheckpoints(speed: List<SpeedMilestone>, distance: List<DistanceSplit>) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("จุดที่ผ่านแล้ว", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (speed.isEmpty() && distance.isEmpty()) Text("ยังไม่มี")
            speed.forEach { Text("${it.displayName}  ${TimeSlipExporter.formatSeconds(it.elapsedMs)}") }
            distance.forEach {
                Text("${it.target.displayName}  ${TimeSlipExporter.formatSeconds(it.elapsedMs)} · ${"%.1f".format(it.trapSpeedKmh)} km/h")
            }
        }
    }
}

@Composable
private fun TimeSlipResultContent(
    state: TimeSlipUiState,
    onSave: () -> Unit,
    onDelete: (String) -> Unit,
    onRunAgain: () -> Unit,
    onShareText: (TimeSlipRecord) -> Unit,
    onShareCsv: (TimeSlipRecord) -> Unit,
    onShareImage: (TimeSlipRecord) -> Unit,
) {
    val record = state.selectedRecord ?: state.engine.result
    var showGraph by remember(record?.id) { mutableStateOf(false) }
    if (record == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("ยังไม่มีผล Time Slip")
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { DigitalTimeSlipCard(record) }
        if (record.status == TimeSlipStatus.COMPLETED) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (record.id !in state.savedRecordIds) {
                        Button(onClick = onSave, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Text(" บันทึก")
                        }
                    }
                    OutlinedButton(onClick = { onShareText(record) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Text(" แชร์")
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onShareImage(record) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Text(" รูปภาพ")
                    }
                    OutlinedButton(onClick = { onShareCsv(record) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Text(" CSV")
                    }
                }
            }
            item {
                OutlinedButton(onClick = { showGraph = !showGraph }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showGraph) "ซ่อนกราฟ" else "ดูกราฟ")
                }
            }
            if (showGraph) {
                item { TimeSlipGraphs(record.samples, record) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRunAgain, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Text(" ทดสอบใหม่")
                }
                if (record.id in state.savedRecordIds) {
                    OutlinedButton(onClick = { onDelete(record.id) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Text(" ลบ")
                    }
                }
            }
        }
    }
}

@Composable
private fun DigitalTimeSlipCard(record: TimeSlipRecord) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1118)),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("NTU", color = Color(0xFF00E5FF), fontSize = 32.sp, fontWeight = FontWeight.Black)
            Text("PERFORMANCE TIME SLIP", color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                SimpleDateFormat("dd MMM yyyy · HH:mm:ss", Locale.getDefault()).format(Date(record.startedAtEpochMs)),
                color = Color.LightGray,
            )
            Text(record.testMode.thaiLabel(), color = Color.LightGray)
            HorizontalDivider(color = Color.DarkGray)
            if (record.status != TimeSlipStatus.COMPLETED) {
                Text(record.status.thaiLabel(), color = Color(0xFFFF5252), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                record.notes?.let { Text(it, color = Color(0xFFFFCDD2)) }
            }
            record.speedMilestones.forEach { milestone ->
                ResultRow(milestone.displayName, TimeSlipExporter.formatSeconds(milestone.elapsedMs))
            }
            if (record.speedMilestones.isNotEmpty()) HorizontalDivider(color = Color.DarkGray)
            record.distanceSplits.forEach { split ->
                ResultRow(
                    split.target.displayName,
                    "${TimeSlipExporter.formatSeconds(split.elapsedMs)} · ${"%.1f".format(split.trapSpeedKmh)} km/h",
                )
                Text("ช่วงนี้ ${TimeSlipExporter.formatSeconds(split.splitMs)}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider(color = Color.DarkGray)
            ResultRow("เวลาทดสอบ", TimeSlipExporter.formatSeconds(record.elapsedMs))
            ResultRow("ระยะรวม", "${"%.1f".format(record.totalDistanceMeters)} m")
            ResultRow("ความเร็วสูงสุด", "${"%.1f".format(record.maximumSpeedKmh)} km/h")
            record.maximumAccelerationMs2?.let { ResultRow("ความเร่งสูงสุด", "${"%.2f".format(it)} m/s²") }
            ResultRow("แหล่งข้อมูล", record.dataSource.name.replace('_', '+'))
            ResultRow("คุณภาพ", record.measurementQuality.thaiLabel())
            record.estimatedTimingErrorMs?.let { ResultRow("ค่าคลาดเคลื่อนเวลา", "±${"%.2f".format(it / 1_000.0)} s") }
            record.obdSampleRateHz?.let { ResultRow("OBD", "${"%.1f".format(it)} Hz") }
            record.gpsSampleRateHz?.let { ResultRow("GPS", "${"%.1f".format(it)} Hz") }
            record.averageGpsAccuracyMeters?.let { ResultRow("ความแม่น GPS", "±${"%.1f".format(it)} m") }
            ResultRow("ตัวอย่างที่ใช้", "${record.sampleCount}")
            ResultRow("ตัวอย่างที่ตกหล่น", "${record.droppedSampleCount}")
            if (record.connectionInterruptions > 0) ResultRow("OBD ขาดช่วง", "${record.connectionInterruptions}")
            if (record.gpsInterruptions > 0) ResultRow("GPS ขาดช่วง", "${record.gpsInterruptions}")
            Text("รูปที่แชร์จะไม่รวมพิกัด GPS", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.LightGray)
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
    }
}

@Composable
private fun TimeSlipHistoryContent(
    state: TimeSlipUiState,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit,
    onVehicleFilter: (String?) -> Unit,
    onModeFilter: (TimeSlipTestMode?) -> Unit,
    onSort: (TimeSlipHistorySort) -> Unit,
    onIncludeLow: (Boolean) -> Unit,
) {
    val vehicles = state.records.mapNotNull { it.vehicleName }.distinct().sorted()
    val filtered = state.records
        .filter { state.historyVehicleFilter == null || it.vehicleName == state.historyVehicleFilter }
        .filter { state.historyModeFilter == null || it.testMode == state.historyModeFilter }
        .let { records ->
            when (state.historySort) {
                TimeSlipHistorySort.DATE -> records.sortedByDescending { it.startedAtEpochMs }
                TimeSlipHistorySort.PERFORMANCE -> records.sortedBy { recordPerformanceKey(it) }
            }
        }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("ประวัติ Time Slip", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("ผล Low/Invalid จะไม่ถูกนำไปเทียบสถิติที่ดีที่สุด เว้นแต่เปิดตัวเลือกด้านล่าง")
        }
        item { BestResultsCard(state) }
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("รวมผลความมั่นใจต่ำในสถิติ", modifier = Modifier.weight(1f))
                Switch(checked = state.includeLowConfidenceInBest, onCheckedChange = onIncludeLow)
            }
        }
        item {
            ChoiceRow {
                FilterChip(selected = state.historyVehicleFilter == null, onClick = { onVehicleFilter(null) }, label = { Text("รถทุกคัน") })
                vehicles.forEach { vehicle ->
                    FilterChip(selected = state.historyVehicleFilter == vehicle, onClick = { onVehicleFilter(vehicle) }, label = { Text(vehicle) })
                }
            }
        }
        item {
            ChoiceRow {
                FilterChip(selected = state.historyModeFilter == null, onClick = { onModeFilter(null) }, label = { Text("ทุกแบบ") })
                TimeSlipTestMode.entries.forEach { mode ->
                    FilterChip(selected = state.historyModeFilter == mode, onClick = { onModeFilter(mode) }, label = { Text(mode.thaiLabel()) })
                }
            }
        }
        item {
            ChoiceRow {
                TimeSlipHistorySort.entries.forEach { sort ->
                    FilterChip(
                        selected = state.historySort == sort,
                        onClick = { onSort(sort) },
                        label = { Text(if (sort == TimeSlipHistorySort.DATE) "เรียงตามวันที่" else "เรียงตามผลงาน") },
                    )
                }
            }
        }
        if (filtered.isEmpty()) {
            item {
                Card { Text("ยังไม่มีผลที่บันทึก", modifier = Modifier.padding(20.dp)) }
            }
        } else {
            items(filtered, key = { it.id }) { record ->
                HistoryRecordCard(record, onClick = { onSelect(record.id) }, onDelete = { onDelete(record.id) })
            }
            item {
                OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Text("  ล้างประวัติทั้งหมด")
                }
            }
        }
    }
}

@Composable
private fun BestResultsCard(state: TimeSlipUiState) {
    val best = state.bestResults
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("สถิติที่ดีที่สุด", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            ResultMetric("0–60 km/h", best?.zeroTo60Kmh?.second?.elapsedMs)
            ResultMetric("0–100 km/h", best?.zeroTo100Kmh?.second?.elapsedMs)
            ResultMetric("1/8 mile", best?.eighthMile?.second?.elapsedMs)
            ResultMetric("1/4 mile", best?.quarterMile?.second?.elapsedMs)
            ResultMetric("1/2 mile", best?.halfMile?.second?.elapsedMs)
            ResultMetric("1 mile", best?.oneMile?.second?.elapsedMs)
            ResultRowNormal("Trap speed สูงสุด", best?.fastestTrapSpeedKmh?.let { "${"%.1f".format(it)} km/h" } ?: "—")
        }
    }
}

@Composable
private fun ResultMetric(label: String, elapsedMs: Long?) = ResultRowNormal(label, elapsedMs?.let(TimeSlipExporter::formatSeconds) ?: "—")

@Composable
private fun ResultRowNormal(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HistoryRecordCard(record: TimeSlipRecord, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(onClick = onClick) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(record.vehicleName ?: record.testMode.thaiLabel(), fontWeight = FontWeight.Bold)
                Text(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(record.startedAtEpochMs)))
                val primary = record.distanceSplits.lastOrNull()?.let {
                    "${it.target.displayName} ${TimeSlipExporter.formatSeconds(it.elapsedMs)}"
                } ?: record.speedMilestones.lastOrNull()?.let {
                    "${it.displayName} ${TimeSlipExporter.formatSeconds(it.elapsedMs)}"
                } ?: "ไม่มีจุดวัด"
                Text(primary)
                QualityBadge(record.measurementQuality)
            }
            TextButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "ลบผล") }
        }
    }
}

@Composable
private fun TimeSlipGraphs(samples: List<PerformanceSample>, record: TimeSlipRecord) {
    if (samples.size < 2) {
        Card { Text("ไม่มีตัวอย่างละเอียดสำหรับสร้างกราฟ", modifier = Modifier.padding(16.dp)) }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GraphCard("ความเร็วเทียบเวลา") {
            SimpleLineGraph(
                points = samples.map { it.elapsedMs.toDouble() to it.fusedSpeedKmh },
                markers = record.speedMilestones.map { it.elapsedMs.toDouble() to it.targetSpeedKmh },
            )
        }
        GraphCard("ความเร็วเทียบระยะ") {
            SimpleLineGraph(
                points = samples.map { it.accumulatedDistanceMeters to it.fusedSpeedKmh },
                markers = record.distanceSplits.map { it.targetDistanceMeters to it.trapSpeedKmh },
            )
        }
        GraphCard("ความเร่งเทียบเวลา") {
            SimpleLineGraph(
                points = samples.mapNotNull { sample -> sample.accelerationMs2?.let { sample.elapsedMs.toDouble() to it } },
                markers = emptyList(),
            )
        }
        GraphCard("ระยะทางเทียบเวลา") {
            SimpleLineGraph(
                points = samples.map { it.elapsedMs.toDouble() to it.accumulatedDistanceMeters },
                markers = record.distanceSplits.map { it.elapsedMs.toDouble() to it.targetDistanceMeters },
            )
        }
    }
}

@Composable
private fun GraphCard(title: String, content: @Composable () -> Unit) {
    Card {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SimpleLineGraph(points: List<Pair<Double, Double>>, markers: List<Pair<Double, Double>>) {
    val lineColor = MaterialTheme.colorScheme.primary
    val markerColor = MaterialTheme.colorScheme.secondary
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    Canvas(modifier = Modifier.fillMaxWidth().height(190.dp)) {
        if (points.size < 2) return@Canvas
        val minX = points.minOf { it.first }
        val maxX = points.maxOf { it.first }.takeIf { it > minX } ?: (minX + 1.0)
        val minY = points.minOf { it.second }
        val maxY = points.maxOf { it.second }.takeIf { it > minY } ?: (minY + 1.0)
        fun map(pair: Pair<Double, Double>) = Offset(
            x = (((pair.first - minX) / (maxX - minX)) * size.width).toFloat(),
            y = (size.height - ((pair.second - minY) / (maxY - minY)) * size.height).toFloat(),
        )
        points.zipWithNext().forEach { (a, b) ->
            drawLine(lineColor, map(a), map(b), strokeWidth = 4f, cap = StrokeCap.Round)
        }
        markers.forEach { marker ->
            val p = map(marker)
            drawCircle(markerColor, radius = 7f, center = p)
            drawLine(
                markerColor.copy(alpha = 0.5f),
                Offset(p.x, 0f),
                Offset(p.x, size.height),
                strokeWidth = 2f,
            )
        }
        drawRect(outlineColor, style = Stroke(width = 1f))
    }
}

@Composable
private fun SpeedRangeFields(state: TimeSlipUiState, viewModel: TimeSlipViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField(
            label = "เริ่ม km/h",
            value = state.config.rollingStartSpeedKmh,
            modifier = Modifier.weight(1f),
            onValue = viewModel::updateRollingStart,
        )
        NumberField(
            label = "เป้าหมาย km/h",
            value = state.config.rollingTargetSpeedKmh,
            modifier = Modifier.weight(1f),
            onValue = viewModel::updateRollingTarget,
        )
    }
}

@Composable
private fun NumberField(label: String, value: Double, modifier: Modifier, onValue: (Double) -> Unit) {
    var text by remember(value) { mutableStateOf(value.format1()) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            val cleaned = input.filter { it.isDigit() || it == '.' }.take(6)
            text = cleaned
            cleaned.toDoubleOrNull()?.let(onValue)
        },
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
    )
}

@Composable
private fun SetupSection(title: String, detail: String, content: @Composable ColumnScope.() -> Unit) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(detail, style = MaterialTheme.typography.bodySmall)
            content()
        }
    }
}

@Composable
private fun ChoiceRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun StatusLine(label: String, detail: String, good: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape,
                color = if (good) Color(0xFF4CAF50) else Color(0xFFFFA000),
            ) {}
        }
        Text(label, modifier = Modifier.padding(start = 10.dp).width(70.dp), fontWeight = FontWeight.Bold)
        Text(detail, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun MetricChip(label: String, value: String) {
    AssistChip(onClick = {}, label = { Text("$label $value") })
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun QualityBadge(quality: MeasurementQuality) {
    val color = when (quality) {
        MeasurementQuality.HIGH -> Color(0xFF2E7D32)
        MeasurementQuality.MEDIUM -> Color(0xFFF9A825)
        MeasurementQuality.LOW -> Color(0xFFEF6C00)
        MeasurementQuality.INVALID -> Color(0xFFC62828)
    }
    Surface(color = color.copy(alpha = 0.18f), shape = CircleShape) {
        Text(
            quality.thaiLabel(),
            color = color,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun displaySpeed(kmh: Double, unit: TimeSlipUnitSystem): Double =
    if (unit == TimeSlipUnitSystem.METRIC) kmh else kmh / TimeSlipConstants.MPH_TO_KMH

private fun recordPerformanceKey(record: TimeSlipRecord): Long =
    record.distanceSplits.lastOrNull()?.elapsedMs ?: record.speedMilestones.lastOrNull()?.elapsedMs ?: Long.MAX_VALUE

private fun ConnectionState.thaiLabel(): String = when (this) {
    ConnectionState.DISCONNECTED -> "ยังไม่เชื่อมต่อ"
    ConnectionState.DEVICE_DETECTED -> "พบอุปกรณ์"
    ConnectionState.REQUESTING_PERMISSION -> "รออนุญาต"
    ConnectionState.CONNECTING -> "กำลังเชื่อมต่อ"
    ConnectionState.INITIALIZING -> "กำลังเตรียม ELM327"
    ConnectionState.CONNECTED -> "เชื่อมต่อแล้ว"
    ConnectionState.ERROR -> "การเชื่อมต่อมีปัญหา"
}

private fun TimeSlipStatus.thaiLabel(): String = when (this) {
    TimeSlipStatus.IDLE -> "พร้อมตั้งค่า"
    TimeSlipStatus.WAITING_FOR_CONNECTION -> "รอการเชื่อมต่อ OBD-II"
    TimeSlipStatus.WAITING_FOR_GPS -> "รอสัญญาณ GPS"
    TimeSlipStatus.ARMED -> "เตรียมพร้อม"
    TimeSlipStatus.LAUNCH_DETECTED -> "ตรวจพบการออกตัว"
    TimeSlipStatus.RUNNING -> "กำลังจับเวลา"
    TimeSlipStatus.COMPLETED -> "ทดสอบเสร็จแล้ว"
    TimeSlipStatus.CANCELLED -> "ยกเลิกแล้ว"
    TimeSlipStatus.CONNECTION_LOST -> "การเชื่อมต่อขาด"
    TimeSlipStatus.GPS_UNRELIABLE -> "GPS ไม่น่าเชื่อถือ"
    TimeSlipStatus.INVALID_RUN -> "การทดสอบไม่ถูกต้อง"
}

private fun TimeSlipTestMode.thaiLabel(): String = when (this) {
    TimeSlipTestMode.STANDING_START -> "ออกตัวจากหยุดนิ่ง"
    TimeSlipTestMode.ROLLING_START -> "จับช่วงความเร็ว"
}

private fun MeasurementQuality.thaiLabel(): String = when (this) {
    MeasurementQuality.HIGH -> "สูง"
    MeasurementQuality.MEDIUM -> "ปานกลาง"
    MeasurementQuality.LOW -> "ต่ำ / Estimated"
    MeasurementQuality.INVALID -> "ใช้เป็นสถิติไม่ได้"
}

private fun Double.format1(): String = if (this == roundToInt().toDouble()) roundToInt().toString() else "%.1f".format(this)
