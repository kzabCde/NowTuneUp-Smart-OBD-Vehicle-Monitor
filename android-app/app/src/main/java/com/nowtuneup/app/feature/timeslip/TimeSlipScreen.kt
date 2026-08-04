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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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

private enum class HistoryFilter { ALL, SPEED, DISTANCE }

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
    val displayClock = remember { TimeSlipDisplayClock() }

    var config by remember { mutableStateOf(TimeSlipConfig()) }
    var snapshot by remember { mutableStateOf(engine.snapshot()) }
    var displayElapsedMillis by remember { mutableStateOf(0L) }
    var history by remember { mutableStateOf(repository.list()) }
    var historyFilter by remember { mutableStateOf(HistoryFilter.ALL) }
    var showHistory by remember { mutableStateOf(false) }
    var safetyAcknowledged by remember { mutableStateOf(repository.safetyAcknowledged()) }
    var showSafetyDialog by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var lastSavedRecordId by remember { mutableStateOf<String?>(null) }

    fun armNow() {
        displayClock.reset()
        val next = engine.arm(
            requestedConfig = config,
            currentSpeedKmh = currentSpeedKmh,
            nowNanos = System.nanoTime(),
            wallClockMillis = System.currentTimeMillis(),
        )
        snapshot = next
        displayElapsedMillis = displayClock.synchronize(next)
        showHistory = false
    }

    fun resetToSetup() {
        displayClock.reset()
        snapshot = engine.reset()
        displayElapsedMillis = 0L
        showHistory = false
    }

    LaunchedEffect(speedReading?.updatedAt) {
        val reading = speedReading ?: return@LaunchedEffect
        val value = reading.value ?: return@LaunchedEffect
        if (reading.updatedAt <= 0L) return@LaunchedEffect
        val next = engine.ingestSpeed(value, System.nanoTime())
        snapshot = next
        displayElapsedMillis = displayClock.synchronize(next)
        next.record?.let { completed ->
            if (completed.id != lastSavedRecordId) {
                repository.save(completed)
                history = repository.list()
                lastSavedRecordId = completed.id
            }
        }
    }

    LaunchedEffect(snapshot.status) {
        if (snapshot.status != TimeSlipStatus.RUNNING) {
            displayElapsedMillis = displayClock.synchronize(snapshot)
            return@LaunchedEffect
        }
        while (snapshot.status == TimeSlipStatus.RUNNING) {
            displayElapsedMillis = displayClock.elapsedMillis(snapshot, System.nanoTime())
            delay(DISPLAY_FRAME_MILLIS)
        }
    }

    LaunchedEffect(connection) {
        if (connection != ConnectionState.CONNECTED && snapshot.active) {
            val next = engine.connectionLost()
            snapshot = next
            displayElapsedMillis = displayClock.synchronize(next)
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
                }) { Text("รับทราบและเตรียมทดสอบ") }
            },
        )
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("ยกเลิกการทดสอบ?") },
            text = { Text("ผลของรอบนี้จะไม่ถูกบันทึก") },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) { Text("ทดสอบต่อ") }
            },
            confirmButton = {
                Button(onClick = {
                    showCancelDialog = false
                    val next = engine.cancel()
                    snapshot = next
                    displayElapsedMillis = displayClock.synchronize(next)
                }) { Text("ยกเลิก") }
            },
        )
    }

    when {
        showHistory && !snapshot.active -> HistoryScreen(
            history = history,
            filter = historyFilter,
            onFilterChange = { historyFilter = it },
            onBack = { showHistory = false },
            onShare = { record -> shareText(context, record.asShareText(), "text/plain") },
            onDelete = { record ->
                repository.delete(record.id)
                history = repository.list()
            },
            onClear = {
                repository.clear()
                history = emptyList()
            },
        )

        snapshot.status == TimeSlipStatus.ARMED -> ArmedScreen(
            snapshot = snapshot,
            currentSpeedKmh = currentSpeedKmh,
            connected = connection == ConnectionState.CONNECTED,
            onCancel = { showCancelDialog = true },
        )

        snapshot.status == TimeSlipStatus.RUNNING -> RunningScreen(
            snapshot = snapshot,
            displayElapsedMillis = displayElapsedMillis,
            currentSpeedKmh = currentSpeedKmh,
            connected = connection == ConnectionState.CONNECTED,
            onCancel = { showCancelDialog = true },
        )

        snapshot.status == TimeSlipStatus.COMPLETED && snapshot.record != null -> ResultScreen(
            record = snapshot.record!!,
            history = history,
            onRunAgain = { armNow() },
            onSetup = { resetToSetup() },
            onHistory = { showHistory = true },
            onShare = { shareText(context, snapshot.record!!.asShareText(), "text/plain") },
            onCsv = { shareText(context, snapshot.record!!.asCsv(), "text/csv") },
        )

        else -> SetupScreen(
            config = config,
            connection = connection,
            currentSpeedKmh = currentSpeedKmh,
            updatedAt = speedReading?.updatedAt ?: 0L,
            previousStatus = snapshot.status,
            previousMessage = snapshot.message,
            onConfigChange = { config = it },
            onArm = {
                if (safetyAcknowledged) armNow() else showSafetyDialog = true
            },
            onHistory = { showHistory = true },
        )
    }
}

@Composable
private fun SetupScreen(
    config: TimeSlipConfig,
    connection: ConnectionState,
    currentSpeedKmh: Double,
    updatedAt: Long,
    previousStatus: TimeSlipStatus,
    previousMessage: String?,
    onConfigChange: (TimeSlipConfig) -> Unit,
    onArm: () -> Unit,
    onHistory: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { TimeSlipHeader(onHistory) }
        if (previousStatus != TimeSlipStatus.IDLE && previousStatus != TimeSlipStatus.COMPLETED) {
            item { StatusMessageCard(previousStatus, previousMessage) }
        }
        item {
            ConnectionSummary(
                connected = connection == ConnectionState.CONNECTED,
                speedKmh = currentSpeedKmh,
                updatedAt = updatedAt,
            )
        }
        item { QuickPresetCard(config, onConfigChange) }
        item { SetupCard(config, true, onConfigChange) }
        item {
            Button(
                onClick = onArm,
                enabled = connection == ConnectionState.CONNECTED && updatedAt > 0L,
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Text(" เตรียมทดสอบ")
            }
        }
        item { EstimatedDistanceNotice() }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun TimeSlipHeader(onHistory: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("Time Slip", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text("Turbo Stability & UI Refresh • NowTuneUp 1.7.2", style = MaterialTheme.typography.bodySmall)
        }
        FilledTonalButton(onClick = onHistory) {
            Icon(Icons.Default.History, contentDescription = null)
            Text(" ประวัติ")
        }
    }
}

@Composable
private fun StatusMessageCard(status: TimeSlipStatus, message: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(status.displayName(), fontWeight = FontWeight.Bold)
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
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
                Text(if (connected) "OBD-II พร้อมทดสอบ" else "ยังไม่ได้เชื่อมต่อ OBD-II", fontWeight = FontWeight.Bold)
                Text(
                    if (updatedAt > 0L) "PID 010D กำลังส่งข้อมูล" else "กำลังรอข้อมูลความเร็ว PID 010D",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text("${speedKmh.roundToInt()} km/h", fontSize = 24.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun QuickPresetCard(config: TimeSlipConfig, onConfigChange: (TimeSlipConfig) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("เริ่มเร็วด้วย Preset", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChoiceButton(
                    selected = config.mode == PerformanceMode.STANDING_START &&
                        config.selectedDistanceTarget == null && config.speedOnlyTargetKmh == 60.0,
                    enabled = true,
                    label = "0–60 km/h",
                    onClick = {
                        onConfigChange(
                            config.copy(
                                mode = PerformanceMode.STANDING_START,
                                selectedDistanceTarget = null,
                                speedOnlyTargetKmh = 60.0,
                            ),
                        )
                    },
                )
                ChoiceButton(
                    selected = config.mode == PerformanceMode.STANDING_START &&
                        config.selectedDistanceTarget == null && config.speedOnlyTargetKmh == 100.0,
                    enabled = true,
                    label = "0–100 km/h",
                    onClick = {
                        onConfigChange(
                            config.copy(
                                mode = PerformanceMode.STANDING_START,
                                selectedDistanceTarget = null,
                                speedOnlyTargetKmh = 100.0,
                            ),
                        )
                    },
                )
                ChoiceButton(
                    selected = config.mode == PerformanceMode.ROLLING_START &&
                        config.rollingStartKmh == 60.0 && config.rollingTargetKmh == 100.0,
                    enabled = true,
                    label = "60–100 km/h",
                    onClick = {
                        onConfigChange(
                            config.copy(
                                mode = PerformanceMode.ROLLING_START,
                                selectedDistanceTarget = null,
                                rollingStartKmh = 60.0,
                                rollingTargetKmh = 100.0,
                            ),
                        )
                    },
                )
                ChoiceButton(
                    selected = config.mode == PerformanceMode.STANDING_START &&
                        config.selectedDistanceTarget == DistanceTarget.QUARTER_MILE,
                    enabled = true,
                    label = "1/4 mile",
                    onClick = {
                        onConfigChange(
                            config.copy(
                                mode = PerformanceMode.STANDING_START,
                                selectedDistanceTarget = DistanceTarget.QUARTER_MILE,
                            ),
                        )
                    },
                )
            }
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
            Text("รายละเอียดการทดสอบ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
                    listOf(60.0 to 100.0, 80.0 to 120.0).forEach { (start, target) ->
                        ChoiceButton(
                            selected = config.rollingStartKmh == start && config.rollingTargetKmh == target,
                            enabled = enabled,
                            label = "${start.toInt()}–${target.toInt()} km/h",
                            onClick = {
                                onConfigChange(config.copy(rollingStartKmh = start, rollingTargetKmh = target))
                            },
                        )
                    }
                }
            } else {
                Text("ประเภทเป้าหมาย", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChoiceButton(
                        selected = config.selectedDistanceTarget == null,
                        enabled = enabled,
                        label = "ความเร็ว",
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
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(60.0, 100.0, 120.0, 160.0).forEach { target ->
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
private fun EstimatedDistanceNotice() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Estimated distance — OBD only", fontWeight = FontWeight.Bold)
            Text(
                "ระยะทางคำนวณจาก PID 010D ด้วย trapezoidal integration และยังไม่มี GPS/accelerometer correction " +
                    "ผลระยะทางจึงถูกระบุเป็น Low / Estimated",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ArmedScreen(
    snapshot: TimeSlipSnapshot,
    currentSpeedKmh: Double,
    connected: Boolean,
    onCancel: () -> Unit,
) {
    val ready = snapshot.message?.contains("พร้อมออกตัว") == true
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("TIME SLIP ARMED", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(targetLabel(snapshot.config), style = MaterialTheme.typography.bodyMedium)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (!ready) CircularProgressIndicator()
                Text(
                    if (ready) "พร้อมออกตัว" else "กำลังตรวจสอบความพร้อม",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
                Text("${currentSpeedKmh.roundToInt()} km/h", fontSize = 58.sp, fontWeight = FontWeight.Black)
                Text(snapshot.message.orEmpty(), textAlign = TextAlign.Center)
                Text(
                    if (connected) "OBD ●  PID 010D พร้อม" else "OBD ขาดการเชื่อมต่อ",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text("ยกเลิกการทดสอบ")
            }
        }
    }
}

@Composable
private fun RunningScreen(
    snapshot: TimeSlipSnapshot,
    displayElapsedMillis: Long,
    currentSpeedKmh: Double,
    connected: Boolean,
    onCancel: () -> Unit,
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val target = targetLabel(snapshot.config)
    val progress = runProgress(snapshot, currentSpeedKmh)
    val sampleRate = if (displayElapsedMillis > 0L) {
        snapshot.sampleCount * 1_000.0 / displayElapsedMillis
    } else {
        0.0
    }
    val latestCheckpoint = latestCheckpoint(snapshot)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        if (landscape) {
            Row(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RunningMetric("ความเร็ว", "${currentSpeedKmh.roundToInt()}", "km/h", Modifier.weight(1f))
                RunningMetric("เวลา", formatSeconds(displayElapsedMillis), "seconds", Modifier.weight(1.35f))
                RunningMetric("เป้าหมาย", target, latestCheckpoint ?: "กำลังวัด", Modifier.weight(1f))
                Column(
                    modifier = Modifier.weight(0.8f).fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    LiveQuality(connected, sampleRate, snapshot.droppedSampleCount)
                    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("ยกเลิก") }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("RUNNING • $target", fontWeight = FontWeight.Bold)
                    Text(latestCheckpoint ?: "กำลังจับเวลา", style = MaterialTheme.typography.bodySmall)
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        formatSeconds(displayElapsedMillis),
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text("seconds", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("${currentSpeedKmh.roundToInt()}", fontSize = 58.sp, fontWeight = FontWeight.Black)
                    Text("km/h", style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(
                        progress = { progress.toFloat() },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                    Text("${(progress * 100).roundToInt()}% • ${"%.1f".format(snapshot.distanceMeters)} m")
                }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LiveQuality(connected, sampleRate, snapshot.droppedSampleCount)
                    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                        Text("ยกเลิกการทดสอบ")
                    }
                }
            }
        }
    }
}

@Composable
private fun RunningMetric(
    label: String,
    value: String,
    supporting: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(
                value,
                fontSize = if (value.length > 8) 34.sp else 50.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
            )
            Text(supporting, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LiveQuality(connected: Boolean, sampleRate: Double, droppedSamples: Int) {
    val quality = when {
        !connected -> "LOST"
        sampleRate >= 8.0 && droppedSamples == 0 -> "GOOD"
        sampleRate >= 4.0 -> "MEDIUM"
        else -> "LOW"
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(if (connected) "OBD ●" else "OBD ○")
            Text("${"%.1f".format(sampleRate)} Hz")
            Text(quality, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ResultScreen(
    record: TimeSlipRecord,
    history: List<TimeSlipRecord>,
    onRunAgain: () -> Unit,
    onSetup: () -> Unit,
    onHistory: () -> Unit,
    onShare: () -> Unit,
    onCsv: () -> Unit,
) {
    val primary = primaryResult(record)
    val comparison = comparisonWithPrevious(record, history)
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("ผลการทดสอบ", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text(formatDate(record.startedAtEpochMillis), style = MaterialTheme.typography.bodySmall)
                }
                FilledTonalButton(onClick = onHistory) {
                    Icon(Icons.Default.History, contentDescription = null)
                    Text(" ประวัติ")
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(primary.first, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "${formatSeconds(primary.second)} s",
                        fontSize = 58.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                    )
                    comparison?.let {
                        Text(
                            if (it < 0L) "เร็วขึ้น ${formatSeconds(-it)} s จากครั้งก่อน" else
                                "ช้าลง ${formatSeconds(it)} s จากครั้งก่อน",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        item { ResultCard(record, onShare, onCsv) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRunAgain, modifier = Modifier.weight(1f)) { Text("ทดสอบอีกครั้ง") }
                OutlinedButton(onClick = onSetup, modifier = Modifier.weight(1f)) { Text("เปลี่ยนการตั้งค่า") }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ResultCard(record: TimeSlipRecord, onShare: () -> Unit, onCsv: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("NTU PERFORMANCE TIME SLIP", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
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
                Text(
                    "Estimated distance — OBD only • ยังไม่มี GPS/accelerometer correction",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                )
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
private fun HistoryScreen(
    history: List<TimeSlipRecord>,
    filter: HistoryFilter,
    onFilterChange: (HistoryFilter) -> Unit,
    onBack: () -> Unit,
    onShare: (TimeSlipRecord) -> Unit,
    onDelete: (TimeSlipRecord) -> Unit,
    onClear: () -> Unit,
) {
    val filtered = history.filter { record ->
        when (filter) {
            HistoryFilter.ALL -> true
            HistoryFilter.SPEED -> record.selectedDistanceTarget == null
            HistoryFilter.DISTANCE -> record.selectedDistanceTarget != null
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("ประวัติ Time Slip", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("Newest first • Personal best ใช้ผลที่ผ่านเกณฑ์", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = onBack) { Text("กลับ") }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HistoryFilter.entries.forEach { item ->
                    ChoiceButton(
                        selected = filter == item,
                        enabled = true,
                        label = when (item) {
                            HistoryFilter.ALL -> "ทั้งหมด"
                            HistoryFilter.SPEED -> "Speed"
                            HistoryFilter.DISTANCE -> "Distance"
                        },
                        onClick = { onFilterChange(item) },
                    )
                }
            }
        }
        item { HistorySummary(history) }
        items(filtered, key = { it.id }) { record ->
            HistoryRecordCard(record, { onShare(record) }, { onDelete(record) })
        }
        if (filtered.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) {
                    Text("ยังไม่มีผลการทดสอบในหมวดนี้")
                }
            }
        }
        if (history.isNotEmpty()) {
            item {
                OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Text(" ล้างประวัติทั้งหมด")
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun HistorySummary(history: List<TimeSlipRecord>) {
    val bestZeroToHundred = history
        .filter { it.measurementQuality == MeasurementQuality.HIGH || it.measurementQuality == MeasurementQuality.MEDIUM }
        .mapNotNull { record -> record.speedMilestones.firstOrNull { it.label == "0–100 km/h" }?.elapsedMillis }
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
            Text("ผล Low/Invalid ไม่ถูกนำไปเป็น Personal Best สำหรับการทดสอบความเร็ว", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun HistoryRecordCard(record: TimeSlipRecord, onShare: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(formatDate(record.startedAtEpochMillis), fontWeight = FontWeight.Bold)
            Text(
                record.speedMilestones.joinToString(" • ") { "${it.label} ${formatSeconds(it.elapsedMillis)}s" }
                    .ifBlank {
                        record.distanceSplits.lastOrNull()?.let {
                            "${it.target.label} ${formatSeconds(it.elapsedMillis)}s"
                        }.orEmpty()
                    },
            )
            Text(
                "${record.measurementQuality.displayName()} • ${"%.1f".format(record.obdSampleRateHz)} Hz" +
                    if (record.distanceEstimated) " • Estimated" else "",
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

private fun targetLabel(config: TimeSlipConfig): String = when {
    config.mode == PerformanceMode.ROLLING_START ->
        "${config.rollingStartKmh.toInt()}–${config.rollingTargetKmh.toInt()} km/h"
    config.selectedDistanceTarget != null -> config.selectedDistanceTarget.label
    else -> "0–${config.speedOnlyTargetKmh.toInt()} km/h"
}

private fun runProgress(snapshot: TimeSlipSnapshot, currentSpeedKmh: Double): Double {
    val config = snapshot.config
    return if (config.selectedDistanceTarget != null) {
        (snapshot.distanceMeters / config.selectedDistanceTarget.meters).coerceIn(0.0, 1.0)
    } else {
        val start = if (config.mode == PerformanceMode.ROLLING_START) config.rollingStartKmh else 0.0
        val target = if (config.mode == PerformanceMode.ROLLING_START) config.rollingTargetKmh else config.speedOnlyTargetKmh
        ((currentSpeedKmh - start) / (target - start).coerceAtLeast(1.0)).coerceIn(0.0, 1.0)
    }
}

private fun latestCheckpoint(snapshot: TimeSlipSnapshot): String? {
    val speed = snapshot.speedMilestones.lastOrNull()?.let {
        "${it.label}  ${formatSeconds(it.elapsedMillis)} s"
    }
    val distance = snapshot.distanceSplits.lastOrNull()?.let {
        "${it.target.label}  ${formatSeconds(it.elapsedMillis)} s"
    }
    return distance ?: speed
}

private fun primaryResult(record: TimeSlipRecord): Pair<String, Long> {
    record.selectedDistanceTarget?.let { target ->
        record.distanceSplits.firstOrNull { it.target == target }?.let { return target.label to it.elapsedMillis }
    }
    record.speedMilestones.lastOrNull()?.let { return it.label to it.elapsedMillis }
    return "Elapsed time" to record.elapsedMillis
}

private fun comparisonWithPrevious(record: TimeSlipRecord, history: List<TimeSlipRecord>): Long? {
    val primary = primaryResult(record)
    val previous = history
        .asSequence()
        .filter { it.id != record.id }
        .mapNotNull { candidate ->
            val candidatePrimary = primaryResult(candidate)
            if (candidatePrimary.first == primary.first) candidatePrimary.second else null
        }
        .firstOrNull() ?: return null
    return primary.second - previous
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
private const val DISPLAY_FRAME_MILLIS = 33L
