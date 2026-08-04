package com.nowtuneup.app.feature.timeslip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nowtuneup.app.data.obd.session.ObdSessionManager
import com.nowtuneup.app.domain.model.ConnectionState
import com.nowtuneup.app.presentation.dashboard.MainViewModel
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TimeSlipSessionEntryPoint {
    fun obdSessionManager(): ObdSessionManager
}

private data class SimplePreset(
    val label: String,
    val config: TimeSlipConfig,
)

private val simplePresets = listOf(
    SimplePreset(
        "0–60 km/h",
        TimeSlipConfig(
            mode = PerformanceMode.STANDING_START,
            selectedDistanceTarget = null,
            speedOnlyTargetKmh = 60.0,
            enabledMilestonesKmh = listOf(60.0),
            useSensorFusion = false,
            oneFootRollout = false,
        ),
    ),
    SimplePreset(
        "0–100 km/h",
        TimeSlipConfig(
            mode = PerformanceMode.STANDING_START,
            selectedDistanceTarget = null,
            speedOnlyTargetKmh = 100.0,
            enabledMilestonesKmh = listOf(60.0, 100.0),
            useSensorFusion = false,
            oneFootRollout = false,
        ),
    ),
    SimplePreset(
        "60–100 km/h",
        TimeSlipConfig(
            mode = PerformanceMode.ROLLING_START,
            selectedDistanceTarget = null,
            rollingStartKmh = 60.0,
            rollingTargetKmh = 100.0,
            enabledMilestonesKmh = listOf(100.0),
            useSensorFusion = false,
            oneFootRollout = false,
        ),
    ),
    SimplePreset(
        "1/4 mile",
        TimeSlipConfig(
            mode = PerformanceMode.STANDING_START,
            selectedDistanceTarget = DistanceTarget.QUARTER_MILE,
            enabledMilestonesKmh = listOf(60.0, 100.0),
            useSensorFusion = false,
            oneFootRollout = false,
        ),
    ),
)

@Composable
fun TimeSlipScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val view = LocalView.current
    val connection by viewModel.connection.collectAsState()
    val session = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            TimeSlipSessionEntryPoint::class.java,
        ).obdSessionManager()
    }
    val speedSample by session.speedTelemetry.collectAsState()
    val readiness by session.speedReadiness.collectAsState()
    val repository = remember { TimeSlipRepository(context.applicationContext) }
    val engine = remember { TimeSlipEngine() }

    var selectedPreset by remember { mutableStateOf(simplePresets[1]) }
    var snapshot by remember { mutableStateOf(engine.snapshot()) }
    var history by remember { mutableStateOf(repository.list()) }
    var showHistory by remember { mutableStateOf(false) }
    var safetyAcknowledged by remember { mutableStateOf(repository.safetyAcknowledged()) }
    var showSafetyDialog by remember { mutableStateOf(false) }
    var lastSavedRecordId by remember { mutableStateOf<String?>(null) }
    var uiClockMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val currentSpeed = speedSample?.speedKmh ?: 0.0
    val modeReady = when (selectedPreset.config.mode) {
        PerformanceMode.STANDING_START -> currentSpeed <= selectedPreset.config.stationaryThresholdKmh
        PerformanceMode.ROLLING_START -> currentSpeed < selectedPreset.config.rollingStartKmh
    }
    val canArm = connection == ConnectionState.CONNECTED &&
        readiness.supported && readiness.hasValue && readiness.fresh && modeReady

    fun armNow() {
        val latest = speedSample ?: return
        session.setPerformanceSampling(true)
        snapshot = engine.arm(
            requestedConfig = selectedPreset.config,
            currentSpeedKmh = latest.speedKmh,
            nowNanos = latest.responseReceivedAtNanos,
            wallClockMillis = latest.wallClockMillis,
        )
        showHistory = false
    }

    LaunchedEffect(connection) {
        session.setPerformanceSampling(connection == ConnectionState.CONNECTED)
        if (connection != ConnectionState.CONNECTED && snapshot.active) {
            snapshot = engine.connectionLost()
        }
    }

    LaunchedEffect(snapshot.active) {
        while (snapshot.active) {
            uiClockMillis = System.currentTimeMillis()
            delay(50L)
        }
    }

    LaunchedEffect(speedSample?.responseReceivedAtNanos) {
        val sample = speedSample ?: return@LaunchedEffect
        if (!snapshot.active) return@LaunchedEffect
        val next = engine.ingestTelemetry(
            TimeSlipTelemetrySample(
                timeNanos = sample.responseReceivedAtNanos,
                wallClockMillis = sample.wallClockMillis,
                obdSpeedKmh = sample.speedKmh,
                fusedSpeedKmh = sample.speedKmh,
                transportLatencyMillis = sample.transportLatencyMillis,
                source = MeasurementSource.OBD_ONLY,
            ),
        )
        snapshot = next
        next.record?.let { record ->
            if (record.id != lastSavedRecordId) {
                repository.save(record)
                history = repository.list()
                lastSavedRecordId = record.id
            }
        }
    }

    DisposableEffect(snapshot.active) {
        val previous = view.keepScreenOn
        if (snapshot.active) view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }

    DisposableEffect(Unit) {
        onDispose { session.setPerformanceSampling(false) }
    }

    if (showSafetyDialog) {
        AlertDialog(
            onDismissRequest = { showSafetyDialog = false },
            title = { Text("ใช้เฉพาะพื้นที่ปิด") },
            text = {
                Text("ใช้ Time Slip เฉพาะสนามแข่งหรือพื้นที่ส่วนบุคคลที่ได้รับอนุญาต ห้ามทดสอบบนถนนสาธารณะ")
            },
            confirmButton = {
                Button(onClick = {
                    repository.acknowledgeSafety()
                    safetyAcknowledged = true
                    showSafetyDialog = false
                    armNow()
                }) { Text("รับทราบและเริ่ม") }
            },
            dismissButton = { TextButton(onClick = { showSafetyDialog = false }) { Text("ยกเลิก") } },
        )
    }

    if (snapshot.active) {
        ActiveTimeSlip(
            snapshot = snapshot,
            speedKmh = currentSpeed,
            speedUpdatedAt = speedSample?.wallClockMillis ?: 0L,
            uiClockMillis = uiClockMillis,
            onCancel = { snapshot = engine.cancel() },
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Time Slip", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("โหมดใช้งานง่าย • OBD speed priority", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { showHistory = !showHistory }) {
                    Text(if (showHistory) "ทดสอบ" else "ประวัติ")
                }
            }
        }

        item {
            ReadinessCard(
                connected = connection == ConnectionState.CONNECTED,
                speedKmh = speedSample?.speedKmh,
                latencyMillis = speedSample?.transportLatencyMillis,
                readinessText = readiness.reasonThai,
                sampleRateHz = readiness.sampleRateHz,
            )
        }

        if (showHistory) {
            if (history.isEmpty()) {
                item { SimpleMessageCard("ยังไม่มีผลทดสอบ", "ผลที่สำเร็จจะถูกบันทึกไว้ในเครื่อง") }
            } else {
                items(history.take(20), key = { it.id }) { record ->
                    HistoryCard(
                        record = record,
                        onShare = { TimeSlipShare.shareText(context, record.asShareText()) },
                        onDelete = {
                            repository.delete(record.id)
                            history = repository.list()
                        },
                    )
                }
            }
        } else {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("เลือกการทดสอบ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        simplePresets.chunked(2).forEach { rowPresets ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                rowPresets.forEach { preset ->
                                    FilterChip(
                                        selected = selectedPreset.label == preset.label,
                                        onClick = {
                                            selectedPreset = preset
                                            snapshot = engine.reset()
                                        },
                                        label = { Text(preset.label) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            snapshot.record?.let { record ->
                item { ResultCard(record, onShare = { TimeSlipShare.shareText(context, record.asShareText()) }) }
            }

            if (snapshot.status in setOf(
                    TimeSlipStatus.INVALID_RUN,
                    TimeSlipStatus.CANCELLED,
                    TimeSlipStatus.CONNECTION_LOST,
                )
            ) {
                item {
                    SimpleMessageCard(
                        title = "ยังไม่ได้ผลทดสอบ",
                        detail = snapshot.message ?: "เตรียมรถและลองใหม่",
                    )
                }
            }

            item {
                Button(
                    onClick = { if (safetyAcknowledged) armNow() else showSafetyDialog = true },
                    enabled = canArm,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("เตรียมทดสอบ ${selectedPreset.label}")
                }
            }

            item {
                Text(
                    startHint(
                        connected = connection == ConnectionState.CONNECTED,
                        supported = readiness.supported,
                        hasValue = readiness.hasValue,
                        fresh = readiness.fresh,
                        mode = selectedPreset.config.mode,
                        speedKmh = currentSpeed,
                        rollingStartKmh = selectedPreset.config.rollingStartKmh,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun ActiveTimeSlip(
    snapshot: TimeSlipSnapshot,
    speedKmh: Double,
    speedUpdatedAt: Long,
    uiClockMillis: Long,
    onCancel: () -> Unit,
) {
    val delayed = speedUpdatedAt <= 0L || uiClockMillis - speedUpdatedAt > 1_800L
    val displayedElapsed = if (snapshot.status == TimeSlipStatus.RUNNING && !delayed) {
        snapshot.elapsedMillis + (uiClockMillis - speedUpdatedAt).coerceIn(0L, 1_200L)
    } else {
        snapshot.elapsedMillis
    }

    Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                if (snapshot.status == TimeSlipStatus.ARMED) "พร้อมออกตัว" else "กำลังทดสอบ",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
            )
            Text(speedKmh.roundToInt().toString(), fontSize = 92.sp, fontWeight = FontWeight.Black)
            Text("km/h", style = MaterialTheme.typography.titleMedium)
            Text(
                "${formatSeconds(displayedElapsed)} s",
                fontSize = 50.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
            Text("ระยะ ${"%.1f".format(snapshot.distanceMeters)} m")
            Text(
                when {
                    delayed -> "ข้อมูล OBD ล่าช้า กรุณาชะลอหรือยกเลิกการทดสอบ"
                    else -> snapshot.message ?: "กำลังอ่านความเร็ว"
                },
                textAlign = TextAlign.Center,
            )
            OutlinedButton(onClick = onCancel) { Text("ยกเลิก") }
        }
    }
}

@Composable
private fun ReadinessCard(
    connected: Boolean,
    speedKmh: Double?,
    latencyMillis: Long?,
    readinessText: String,
    sampleRateHz: Double,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (connected) "OBD พร้อมตรวจ" else "ยังไม่เชื่อมต่อ", fontWeight = FontWeight.Bold)
                Text(speedKmh?.let { "${it.roundToInt()} km/h" } ?: "--")
            }
            Text(if (connected) readinessText else "เชื่อมต่อ ELM327 ก่อนใช้งาน", style = MaterialTheme.typography.bodySmall)
            Text(
                "อัตรา ${"%.1f".format(sampleRateHz)} Hz • latency ${latencyMillis?.let { "$it ms" } ?: "--"}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ResultCard(record: TimeSlipRecord, onShare: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("ผลล่าสุด", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "${formatSeconds(record.elapsedMillis)} s",
                fontSize = 42.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
            )
            record.speedMilestones.forEach { Text("${it.label}: ${formatSeconds(it.elapsedMillis)} s") }
            record.distanceSplits.forEach {
                Text("${it.target.label}: ${formatSeconds(it.elapsedMillis)} s • ${"%.1f".format(it.trapSpeedKmh)} km/h")
            }
            HorizontalDivider()
            Text("คุณภาพ ${record.measurementQuality.name} • ${"%.1f".format(record.obdSampleRateHz)} Hz")
            Text("เวลาโดยประมาณ ±${record.estimatedTimingErrorMillis} ms", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onShare) { Text("แชร์ผล") }
        }
    }
}

@Composable
private fun HistoryCard(record: TimeSlipRecord, onShare: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(historyTitle(record), fontWeight = FontWeight.Bold)
            Text("${formatSeconds(record.elapsedMillis)} s", fontSize = 28.sp, fontFamily = FontFamily.Monospace)
            Text("${"%.1f".format(record.obdSampleRateHz)} Hz • ${record.measurementQuality.name}", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onShare) { Text("แชร์") }
                TextButton(onClick = onDelete) { Text("ลบ") }
            }
        }
    }
}

@Composable
private fun SimpleMessageCard(title: String, detail: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun historyTitle(record: TimeSlipRecord): String = when {
    record.mode == PerformanceMode.ROLLING_START -> "60–100 km/h"
    record.selectedDistanceTarget != null -> record.selectedDistanceTarget.label
    record.speedMilestones.isNotEmpty() -> record.speedMilestones.last().label
    else -> "Time Slip"
}

private fun startHint(
    connected: Boolean,
    supported: Boolean,
    hasValue: Boolean,
    fresh: Boolean,
    mode: PerformanceMode,
    speedKmh: Double,
    rollingStartKmh: Double,
): String = when {
    !connected -> "เชื่อมต่อ ELM327 ก่อน"
    !supported -> "รถไม่รองรับค่าความเร็ว PID 010D"
    !hasValue -> "กำลังรอค่าความเร็วจาก ECU"
    !fresh -> "ข้อมูลล่าช้า กรุณารอให้การเชื่อมต่อเสถียร"
    mode == PerformanceMode.STANDING_START && speedKmh > 1.0 -> "จอดรถให้นิ่งก่อนกดเตรียมทดสอบ"
    mode == PerformanceMode.ROLLING_START && speedKmh >= rollingStartKmh -> "ลดความเร็วให้ต่ำกว่า ${rollingStartKmh.toInt()} km/h ก่อน"
    else -> "พร้อมใช้งาน กดเตรียมทดสอบแล้วออกตัวเมื่อขึ้นข้อความ “พร้อมออกตัว”"
}
