package com.nowtuneup.app.feature.timeslip

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nowtuneup.app.domain.model.ConnectionState
import com.nowtuneup.app.presentation.dashboard.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun TimeSlipScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val view = LocalView.current
    val configuration = LocalConfiguration.current
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
    var uiClockMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    fun armNow() {
        snapshot = engine.arm(
            requestedConfig = config,
            currentSpeedKmh = currentSpeedKmh,
            nowNanos = System.nanoTime(),
            wallClockMillis = System.currentTimeMillis(),
        )
        showHistory = false
    }

    LaunchedEffect(snapshot.active) {
        while (snapshot.active) {
            uiClockMillis = System.currentTimeMillis()
            delay(50L)
        }
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
        if (connection != ConnectionState.CONNECTED && snapshot.active) snapshot = engine.connectionLost()
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
            text = { Text("ใช้ Time Slip เฉพาะในสนามแข่ง พื้นที่ปิด หรือพื้นที่ส่วนบุคคลที่ได้รับอนุญาต ห้ามทดสอบบนถนนสาธารณะ") },
            dismissButton = { TextButton(onClick = { showSafetyDialog = false }) { Text("ยกเลิก") } },
            confirmButton = {
                Button(onClick = {
                    repository.acknowledgeSafety()
                    safetyAcknowledged = true
                    showSafetyDialog = false
                    armNow()
                }) { Text("รับทราบและเตรียมทดสอบ") }
            },
        )
    }

    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (snapshot.active) {
        ActiveRunScreen(
            snapshot = snapshot,
            currentSpeedKmh = currentSpeedKmh,
            speedUpdatedAt = speedReading?.updatedAt ?: 0L,
            uiClockMillis = uiClockMillis,
            connected = connection == ConnectionState.CONNECTED,
            landscape = isLandscape,
            onCancel = { snapshot = engine.cancel() },
        )
        return
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
                    Text("Performance Test • NowTuneUp 1.7.2", style = MaterialTheme.typography.bodySmall)
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
        } else {
            item { PresetCard(config, onConfigChange = { config = it }) }
            item { SetupCard(config, onConfigChange = { config = it }) }
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
                Button(
                    onClick = { if (safetyAcknowledged) armNow() else showSafetyDialog = true },
                    enabled = connection == ConnectionState.CONNECTED,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text(" เตรียมทดสอบ")
                }
            }
            item {
                Text(
                    "Estimated distance — OBD only: ระยะทางคำนวณจาก PID 010D และยังไม่มี GPS/accelerometer correction",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ActiveRunScreen(
    snapshot: TimeSlipSnapshot,
    currentSpeedKmh: Double,
    speedUpdatedAt: Long,
    uiClockMillis: Long,
    connected: Boolean,
    landscape: Boolean,
    onCancel: () -> Unit,
) {
    val delayed = speedUpdatedAt <= 0L || uiClockMillis - speedUpdatedAt > 800L
    val displayedElapsed = if (snapshot.status == TimeSlipStatus.RUNNING && speedUpdatedAt > 0L) {
        snapshot.elapsedMillis + (uiClockMillis - speedUpdatedAt).coerceIn(0L, 1_500L)
    } else snapshot.elapsedMillis
    val target = when {
        snapshot.config.mode == PerformanceMode.ROLLING_START -> snapshot.config.rollingTargetKmh.toInt().toString()
        snapshot.config.selectedDistanceTarget != null -> snapshot.config.selectedDistanceTarget.label
        else -> snapshot.config.speedOnlyTargetKmh.toInt().toString()
    }

    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        if (landscape) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetricBlock("ความเร็ว", currentSpeedKmh.roundToInt().toString(), "km/h")
                MetricBlock("เวลา", formatSeconds(displayedElapsed), "s")
                MetricBlock("เป้าหมาย", target, if (snapshot.config.selectedDistanceTarget == null) "km/h" else "")
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(snapshot.status.displayName(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(
                    formatSeconds(displayedElapsed),
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                )
                Text("seconds", style = MaterialTheme.typography.labelLarge)
                Text(currentSpeedKmh.roundToInt().toString(), fontSize = 72.sp, fontWeight = FontWeight.Black)
                Text("km/h • Target $target", style = MaterialTheme.typography.titleMedium)
                snapshot.speedMilestones.lastOrNull()?.let {
                    Text("ล่าสุด ${it.label}: ${formatSeconds(it.elapsedMillis)} s", fontWeight = FontWeight.Bold)
                }
                snapshot.message?.let { Text(it, textAlign = TextAlign.Center) }
                Text(
                    when {
                        !connected -> "OBD disconnected"
                        delayed -> "OBD data delayed"
                        else -> "OBD live • ${snapshot.sampleCount} samples"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("กดเพื่อยกเลิกการทดสอบ") }
            }
        }
    }
}

@Composable
private fun MetricBlock(label: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(value, fontSize = 46.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
        Text(unit, style = MaterialTheme.typography.bodySmall)
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
                Text(if (updatedAt > 0L) "PID 010D พร้อมใช้งาน" else "กำลังรอข้อมูลความเร็ว", style = MaterialTheme.typography.bodySmall)
            }
            Text("${speedKmh.roundToInt()} km/h", fontSize = 24.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun PresetCard(config: TimeSlipConfig, onConfigChange: (TimeSlipConfig) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Quick presets", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChoiceButton(config.selectedDistanceTarget == null && config.speedOnlyTargetKmh == 60.0, "0–60", onClick = {
                    onConfigChange(config.copy(mode = PerformanceMode.STANDING_START, selectedDistanceTarget = null, speedOnlyTargetKmh = 60.0))
                })
                ChoiceButton(config.selectedDistanceTarget == null && config.speedOnlyTargetKmh == 100.0, "0–100", onClick = {
                    onConfigChange(config.copy(mode = PerformanceMode.STANDING_START, selectedDistanceTarget = null, speedOnlyTargetKmh = 100.0))
                })
                ChoiceButton(config.mode == PerformanceMode.ROLLING_START, "60–100", onClick = {
                    onConfigChange(config.copy(mode = PerformanceMode.ROLLING_START, selectedDistanceTarget = null, rollingStartKmh = 60.0, rollingTargetKmh = 100.0))
                })
                ChoiceButton(config.selectedDistanceTarget == DistanceTarget.EIGHTH_MILE, "1/8 mile", onClick = {
                    onConfigChange(config.copy(mode = PerformanceMode.STANDING_START, selectedDistanceTarget = DistanceTarget.EIGHTH_MILE))
                })
                ChoiceButton(config.selectedDistanceTarget == DistanceTarget.QUARTER_MILE, "1/4 mile", onClick = {
                    onConfigChange(config.copy(mode = PerformanceMode.STANDING_START, selectedDistanceTarget = DistanceTarget.QUARTER_MILE))
                })
            }
        }
    }
}

@Composable
private fun SetupCard(config: TimeSlipConfig, onConfigChange: (TimeSlipConfig) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("ตั้งค่าการทดสอบ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceButton(config.mode == PerformanceMode.STANDING_START, "Standing", onClick = {
                    onConfigChange(config.copy(mode = PerformanceMode.STANDING_START))
                })
                ChoiceButton(config.mode == PerformanceMode.ROLLING_START, "Rolling", onClick = {
                    onConfigChange(config.copy(mode = PerformanceMode.ROLLING_START, selectedDistanceTarget = null))
                })
            }
            Text(
                if (config.mode == PerformanceMode.ROLLING_START) {
                    "เริ่มจับเวลาที่ ${config.rollingStartKmh.toInt()} และจบที่ ${config.rollingTargetKmh.toInt()} km/h"
                } else if (config.selectedDistanceTarget != null) {
                    "จับเวลาระยะ ${config.selectedDistanceTarget.label} (Estimated / Low confidence)"
                } else {
                    "จับเวลา 0–${config.speedOnlyTargetKmh.toInt()} km/h"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ChoiceButton(selected: Boolean, label: String, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick) { Text(label) }
    else OutlinedButton(onClick = onClick) { Text(label) }
}

@Composable
private fun ResultCard(record: TimeSlipRecord, onShare: () -> Unit, onCsv: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("NTU PERFORMANCE TIME SLIP", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(formatDate(record.startedAtEpochMillis), style = MaterialTheme.typography.bodySmall)
            record.speedMilestones.forEach { ResultRow(it.label, "${formatSeconds(it.elapsedMillis)} s") }
            record.distanceSplits.forEach { ResultRow(it.target.label, "${formatSeconds(it.elapsedMillis)} s • ${"%.1f".format(it.trapSpeedKmh)} km/h") }
            HorizontalDivider()
            ResultRow("Maximum speed", "${"%.1f".format(record.maximumSpeedKmh)} km/h")
            ResultRow("OBD sample rate", "${"%.1f".format(record.obdSampleRateHz)} Hz")
            ResultRow("Measurement quality", record.measurementQuality.displayName())
            ResultRow("Timing uncertainty", "±${record.estimatedTimingErrorMillis} ms")
            if (record.distanceEstimated) Text("Estimated distance — OBD only", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Text(" แชร์ผล")
                }
                OutlinedButton(onClick = onCsv, modifier = Modifier.weight(1f)) { Text("CSV") }
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
    val best = history.filter { it.measurementQuality != MeasurementQuality.INVALID }
        .mapNotNull { record -> record.speedMilestones.firstOrNull { it.label == "0–100 km/h" }?.elapsedMillis }
        .minOrNull()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("สถิติที่ดีที่สุด", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            ResultRow("0–100 km/h", best?.let { "${formatSeconds(it)} s" } ?: "—")
            Text("ผล Invalid ไม่ถูกนำมาคำนวณ Personal Best", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun HistoryRecordCard(record: TimeSlipRecord, onShare: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(formatDate(record.startedAtEpochMillis), fontWeight = FontWeight.Bold)
            Text(record.speedMilestones.joinToString(" • ") { "${it.label} ${formatSeconds(it.elapsedMillis)}s" }.ifBlank { "Distance run" })
            Text("${record.measurementQuality.displayName()} • ${"%.1f".format(record.obdSampleRateHz)} Hz", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onShare, label = { Text("แชร์") }, leadingIcon = { Icon(Icons.Default.Share, null) })
                AssistChip(onClick = onDelete, label = { Text("ลบ") }, leadingIcon = { Icon(Icons.Default.Delete, null) })
            }
        }
    }
}

private fun TimeSlipStatus.displayName(): String = when (this) {
    TimeSlipStatus.IDLE -> "พร้อมตั้งค่า"
    TimeSlipStatus.ARMED -> "READY — รอออกตัว"
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

private fun formatDate(epochMillis: Long): String = SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.getDefault()).format(Date(epochMillis))

private fun shareText(context: Context, text: String, mimeType: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_SUBJECT, "NTU Performance Time Slip")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "แชร์ผล Time Slip"))
}

private const val VEHICLE_SPEED_PID = 0x0D
