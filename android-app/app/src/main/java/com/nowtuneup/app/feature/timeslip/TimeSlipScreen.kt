package com.nowtuneup.app.feature.timeslip

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nowtuneup.app.domain.model.ConnectionState
import com.nowtuneup.app.presentation.dashboard.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun TimeSlipScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val view = LocalView.current
    val readings by viewModel.readings.collectAsState()
    val connection by viewModel.connection.collectAsState()
    val speedReading = readings.firstOrNull { it.pid == VEHICLE_SPEED_PID }
    val currentSpeedKmh = speedReading?.value ?: 0.0
    val repository = remember { TimeSlipRepository(context.applicationContext) }
    val engine = remember { TimeSlipEngine() }

    var config by remember { mutableStateOf(TimeSlipConfig()) }
    var snapshot by remember { mutableStateOf(engine.snapshot()) }
    var history by remember { mutableStateOf(repository.list()) }
    var showHistory by remember { mutableStateOf(false) }
    var safetyAcknowledged by remember { mutableStateOf(repository.safetyAcknowledged()) }
    var showSafetyDialog by remember { mutableStateOf(false) }
    var lastSavedRecordId by remember { mutableStateOf<String?>(null) }

    fun armNow() {
        snapshot = engine.arm(
            requestedConfig = config,
            currentSpeedKmh = currentSpeedKmh,
            nowNanos = System.nanoTime(),
            wallClockMillis = System.currentTimeMillis(),
        )
        showHistory = false
    }

    LaunchedEffect(speedReading?.updatedAt) {
        val reading = speedReading ?: return@LaunchedEffect
        val value = reading.value ?: return@LaunchedEffect
        if (reading.updatedAt <= 0L) return@LaunchedEffect
        val next = engine.ingestSpeed(value, System.nanoTime())
        snapshot = next
        next.record?.let { completed ->
            if (completed.id != lastSavedRecordId) {
                repository.save(completed)
                history = repository.list()
                lastSavedRecordId = completed.id
            }
        }
    }

    LaunchedEffect(connection) {
        if (connection != ConnectionState.CONNECTED && snapshot.active) {
            snapshot = engine.connectionLost()
        }
    }

    DisposableEffect(snapshot.active) {
        val previous = view.keepScreenOn
        if (snapshot.active) view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }

    if (showSafetyDialog) {
        AlertDialog(
            onDismissRequest = { showSafetyDialog = false },
            title = { Text("คำเตือนด้านความปลอดภัย") },
            text = {
                Text(
                    "ใช้การทดสอบ Performance เฉพาะในสนามแข่ง พื้นที่ปิด หรือพื้นที่ส่วนบุคคลที่อนุญาตเท่านั้น " +
                        "ห้ามทดสอบอัตราเร่งบนถนนสาธารณะ โปรดปฏิบัติตามกฎหมายและให้ความสำคัญกับความปลอดภัย",
                )
            },
            dismissButton = {
                TextButton(onClick = { showSafetyDialog = false }) { Text("ยกเลิก") }
            },
            confirmButton = {
                Button(onClick = {
                    repository.acknowledgeSafety()
                    safetyAcknowledged = true
                    showSafetyDialog = false
                    armNow()
                }) { Text("รับทราบและ Arm Test") }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Time Slip", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("Performance Test • NowTuneUp 1.7", style = MaterialTheme.typography.bodySmall)
                }
                FilledTonalButton(onClick = { showHistory = !showHistory }) {
                    Icon(Icons.Default.History, contentDescription = null)
                    Text(if (showHistory) " ทดสอบ" else " ประวัติ")
                }
            }
        }

        item {
            ConnectionSummary(
                connected = connection == ConnectionState.CONNECTED,
                speedKmh = currentSpeedKmh,
                updatedAt = speedReading?.updatedAt ?: 0L,
            )
        }

        if (showHistory) {
            item { HistorySummary(history) }
            items(history, key = { it.id }) { record ->
                HistoryRecordCard(
                    record = record,
                    onShare = { shareText(context, record.asShareText(), "text/plain") },
                    onDelete = {
                        repository.delete(record.id)
                        history = repository.list()
                    },
                )
            }
            if (history.isNotEmpty()) {
                item {
                    OutlinedButton(
                        onClick = {
                            repository.clear()
                            history = emptyList()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Text(" ล้างประวัติทั้งหมด")
                    }
                }
            }
        } else {
            item {
                SetupCard(
                    config = config,
                    enabled = !snapshot.active,
                    onConfigChange = { config = it },
                )
            }

            item { LivePerformanceCard(snapshot, currentSpeedKmh) }

            snapshot.record?.let { record ->
                item {
                    ResultCard(
                        record = record,
                        onShare = { shareText(context, record.asShareText(), "text/plain") },
                        onCsv = { shareText(context, record.asCsv(), "text/csv") },
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = {
                            if (safetyAcknowledged) armNow() else showSafetyDialog = true
                        },
                        enabled = connection == ConnectionState.CONNECTED && !snapshot.active,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Text(" Arm Test")
                    }
                    OutlinedButton(
                        onClick = { snapshot = engine.cancel() },
                        enabled = snapshot.active,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("ยกเลิก")
                    }
                }
            }

            item {
                Text(
                    "ระยะทางในเวอร์ชันนี้คำนวณจาก PID 010D ด้วย trapezoidal integration และแสดงเป็นค่าประมาณ " +
                        "การทดสอบความเร็วใช้ interpolation ระหว่างตัวอย่าง OBD ไม่ใช้เวลา UI หรือ Date.now()",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ConnectionSummary(connected: Boolean, speedKmh: Double, updatedAt: Long) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(if (connected) "OBD-II เชื่อมต่อแล้ว" else "ยังไม่ได้เชื่อมต่อ OBD-II", fontWeight = FontWeight.Bold)
                Text(
                    if (updatedAt > 0L) "PID 010D พร้อมใช้งาน" else "กำลังรอข้อมูลความเร็ว PID 010D",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text("${speedKmh.roundToInt()} km/h", fontSize = 24.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun SetupCard(
    config: TimeSlipConfig,
    enabled: Boolean,
    onConfigChange: (TimeSlipConfig) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("ตั้งค่าการทดสอบ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("รูปแบบการออกตัว", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceButton(
                    selected = config.mode == PerformanceMode.STANDING_START,
                    enabled = enabled,
                    label = "Standing Start",
                    onClick = { onConfigChange(config.copy(mode = PerformanceMode.STANDING_START)) },
                )
                ChoiceButton(
                    selected = config.mode == PerformanceMode.ROLLING_START,
                    enabled = enabled,
                    label = "Rolling Start",
                    onClick = {
                        onConfigChange(config.copy(mode = PerformanceMode.ROLLING_START, selectedDistanceTarget = null))
                    },
                )
            }

            if (config.mode == PerformanceMode.ROLLING_START) {
                Text("ช่วงความเร็ว", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceButton(
                        selected = config.rollingStartKmh == 60.0 && config.rollingTargetKmh == 100.0,
                        enabled = enabled,
                        label = "60–100 km/h",
                        onClick = {
                            onConfigChange(config.copy(rollingStartKmh = 60.0, rollingTargetKmh = 100.0))
                        },
                    )
                    ChoiceButton(
                        selected = config.rollingStartKmh == 80.0 && config.rollingTargetKmh == 120.0,
                        enabled = enabled,
                        label = "80–120 km/h",
                        onClick = {
                            onConfigChange(config.copy(rollingStartKmh = 80.0, rollingTargetKmh = 120.0))
                        },
                    )
                }
            } else {
                Text("เป้าหมายสูงสุด", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChoiceButton(
                        selected = config.selectedDistanceTarget == null,
                        enabled = enabled,
                        label = "Speed only",
                        onClick = { onConfigChange(config.copy(selectedDistanceTarget = null)) },
                    )
                    listOf(
                        DistanceTarget.EIGHTH_MILE,
                        DistanceTarget.QUARTER_MILE,
                        DistanceTarget.HALF_MILE,
                        DistanceTarget.ONE_MILE,
                    ).forEach { target ->
                        ChoiceButton(
                            selected = config.selectedDistanceTarget == target,
                            enabled = enabled,
                            label = target.label,
                            onClick = { onConfigChange(config.copy(selectedDistanceTarget = target)) },
                        )
                    }
                }
                if (config.selectedDistanceTarget == null) {
                    Text("ความเร็วเป้าหมาย", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(100.0, 120.0, 160.0).forEach { target ->
                            ChoiceButton(
                                selected = config.speedOnlyTargetKmh == target,
                                enabled = enabled,
                                label = "0–${target.toInt()} km/h",
                                onClick = { onConfigChange(config.copy(speedOnlyTargetKmh = target)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceButton(selected: Boolean, enabled: Boolean, label: String, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, enabled = enabled) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled) { Text(label) }
    }
}

@Composable
private fun LivePerformanceCard(snapshot: TimeSlipSnapshot, currentSpeedKmh: Double) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Speed, contentDescription = null)
            Text("${currentSpeedKmh.roundToInt()}", fontSize = 58.sp, fontWeight = FontWeight.Black)
            Text("km/h", style = MaterialTheme.typography.titleMedium)
            Text(formatSeconds(snapshot.elapsedMillis) + " s", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("${"%.1f".format(snapshot.distanceMeters)} m • ${snapshot.status.displayName()}")
            snapshot.message?.let { Text(it, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall) }
            if (snapshot.distanceSplits.isNotEmpty()) {
                HorizontalDivider()
                snapshot.distanceSplits.forEach { split ->
                    Text(
                        "${split.target.label}: ${formatSeconds(split.elapsedMillis)} s • " +
                            "${"%.1f".format(split.trapSpeedKmh)} km/h",
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultCard(record: TimeSlipRecord, onShare: () -> Unit, onCsv: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("NTU PERFORMANCE TIME SLIP", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(formatDate(record.startedAtEpochMillis), style = MaterialTheme.typography.bodySmall)
            record.speedMilestones.forEach { ResultRow(it.label, "${formatSeconds(it.elapsedMillis)} s") }
            record.distanceSplits.forEach {
                ResultRow(
                    it.target.label,
                    "${formatSeconds(it.elapsedMillis)} s • ${"%.1f".format(it.trapSpeedKmh)} km/h",
                )
            }
            HorizontalDivider()
            ResultRow("Maximum speed", "${"%.1f".format(record.maximumSpeedKmh)} km/h")
            ResultRow("OBD sample rate", "${"%.1f".format(record.obdSampleRateHz)} Hz")
            ResultRow("Measurement quality", record.measurementQuality.displayName())
            ResultRow("Timing uncertainty", "±${record.estimatedTimingErrorMillis} ms")
            if (record.distanceEstimated) {
                Text("ระยะทางเป็นค่าประมาณจาก OBD-II และยังไม่มี GPS correction", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Text(" แชร์ผล")
                }
                OutlinedButton(onClick = onCsv, modifier = Modifier.weight(1f)) { Text("Export CSV") }
            }
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Spacer(Modifier.width(12.dp))
        Text(value, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
    }
}

@Composable
private fun HistorySummary(history: List<TimeSlipRecord>) {
    val bestZeroToHundred = history
        .filter {
            it.measurementQuality == MeasurementQuality.HIGH ||
                it.measurementQuality == MeasurementQuality.MEDIUM
        }
        .mapNotNull { record ->
            record.speedMilestones.firstOrNull { it.label == "0–100 km/h" }?.elapsedMillis
        }
        .minOrNull()
    val bestQuarter = history
        .filter { it.measurementQuality != MeasurementQuality.INVALID }
        .mapNotNull { record ->
            record.distanceSplits.firstOrNull { it.target == DistanceTarget.QUARTER_MILE }?.elapsedMillis
        }
        .minOrNull()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("สถิติที่ดีที่สุด", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            ResultRow("0–100 km/h", bestZeroToHundred?.let { "${formatSeconds(it)} s" } ?: "—")
            ResultRow("1/4 mile (estimated)", bestQuarter?.let { "${formatSeconds(it)} s" } ?: "—")
            Text("รายการความแม่นยำต่ำไม่ถูกนำไปเทียบสถิติความเร็ว", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun HistoryRecordCard(record: TimeSlipRecord, onShare: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(formatDate(record.startedAtEpochMillis), fontWeight = FontWeight.Bold)
            Text(
                record.speedMilestones.joinToString(" • ") {
                    "${it.label} ${formatSeconds(it.elapsedMillis)}s"
                }.ifBlank {
                    record.distanceSplits.lastOrNull()?.let {
                        "${it.target.label} ${formatSeconds(it.elapsedMillis)}s"
                    }.orEmpty()
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "${record.measurementQuality.displayName()} • ${"%.1f".format(record.obdSampleRateHz)} Hz",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = onShare,
                    label = { Text("แชร์") },
                    leadingIcon = { Icon(Icons.Default.Share, null) },
                )
                AssistChip(
                    onClick = onDelete,
                    label = { Text("ลบ") },
                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                )
            }
        }
    }
}

private fun TimeSlipStatus.displayName(): String = when (this) {
    TimeSlipStatus.IDLE -> "พร้อมตั้งค่า"
    TimeSlipStatus.ARMED -> "ARMED"
    TimeSlipStatus.RUNNING -> "RUNNING"
    TimeSlipStatus.COMPLETED -> "COMPLETED"
    TimeSlipStatus.CANCELLED -> "CANCELLED"
    TimeSlipStatus.CONNECTION_LOST -> "CONNECTION LOST"
    TimeSlipStatus.INVALID_RUN -> "INVALID RUN"
}

private fun MeasurementQuality.displayName(): String = when (this) {
    MeasurementQuality.HIGH -> "High"
    MeasurementQuality.MEDIUM -> "Medium"
    MeasurementQuality.LOW -> "Low / Estimated"
    MeasurementQuality.INVALID -> "Invalid"
}

private fun formatDate(epochMillis: Long): String = SimpleDateFormat(
    "dd MMM yyyy HH:mm:ss",
    Locale.getDefault(),
).format(Date(epochMillis))

private fun shareText(context: Context, text: String, mimeType: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_SUBJECT, "NTU Performance Time Slip")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "แชร์ผล Time Slip"))
}

private const val VEHICLE_SPEED_PID = 0x0D
