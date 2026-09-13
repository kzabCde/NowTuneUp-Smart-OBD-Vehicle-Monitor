package com.nowtuneup.app.feature.timeslip

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import com.nowtuneup.app.ui.motion.ntuAnimateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import com.nowtuneup.app.ui.components.NtuPanel as Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.nowtuneup.app.ui.components.NtuScreenHeader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.nowtuneup.app.ui.motion.NtuMotion
import com.nowtuneup.app.ui.vehicle.rememberVehicleProfileRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TimeSlipSessionEntryPoint {
    fun obdSessionManager(): ObdSessionManager
}

private data class SimplePreset(val label: String, val config: TimeSlipConfig)

private val simplePresets = listOf(
    SimplePreset("0–60 km/h", TimeSlipConfig(mode = PerformanceMode.STANDING_START, selectedDistanceTarget = null, speedOnlyTargetKmh = 60.0, enabledMilestonesKmh = listOf(60.0), useSensorFusion = false, oneFootRollout = false)),
    SimplePreset("0–100 km/h", TimeSlipConfig(mode = PerformanceMode.STANDING_START, selectedDistanceTarget = null, speedOnlyTargetKmh = 100.0, enabledMilestonesKmh = listOf(60.0, 100.0), useSensorFusion = false, oneFootRollout = false)),
    SimplePreset("60–100 km/h", TimeSlipConfig(mode = PerformanceMode.ROLLING_START, selectedDistanceTarget = null, rollingStartKmh = 60.0, rollingTargetKmh = 100.0, enabledMilestonesKmh = listOf(100.0), useSensorFusion = false, oneFootRollout = false)),
    SimplePreset("1/4 mile", TimeSlipConfig(mode = PerformanceMode.STANDING_START, selectedDistanceTarget = DistanceTarget.QUARTER_MILE, enabledMilestonesKmh = listOf(60.0, 100.0), useSensorFusion = false, oneFootRollout = false)),
    SimplePreset("1/2 mile", TimeSlipConfig(mode = PerformanceMode.STANDING_START, selectedDistanceTarget = DistanceTarget.HALF_MILE, enabledMilestonesKmh = listOf(60.0, 100.0, 160.0), useSensorFusion = false, oneFootRollout = false)),
    SimplePreset("1 mile", TimeSlipConfig(mode = PerformanceMode.STANDING_START, selectedDistanceTarget = DistanceTarget.ONE_MILE, enabledMilestonesKmh = listOf(60.0, 100.0, 160.0), useSensorFusion = false, oneFootRollout = false)),
)

@Composable
fun TimeSlipScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val view = LocalView.current
    val connection by viewModel.connection.collectAsState()
    val preferences by viewModel.dashboardPreferences.collectAsState()
    val vehicleRepository = rememberVehicleProfileRepository()
    val vehicleProfiles by vehicleRepository.profiles.collectAsState()
    val activeVehicle = vehicleProfiles.firstOrNull { it.isActive }
    val session = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, TimeSlipSessionEntryPoint::class.java).obdSessionManager()
    }
    val speedSample by session.speedTelemetry.collectAsState()
    val readiness by session.speedReadiness.collectAsState()
    val repository = remember { TimeSlipRepository(context.applicationContext) }
    val engine = remember { TimeSlipEngine() }
    val scope = rememberCoroutineScope()
    var selectedPreset by remember { mutableStateOf(simplePresets[1]) }
    var snapshot by remember { mutableStateOf(engine.snapshot()) }
    var history by remember { mutableStateOf(repository.list()) }
    var showHistory by remember { mutableStateOf(false) }
    var safetyAcknowledged by remember { mutableStateOf(repository.safetyAcknowledged()) }
    var showSafetyDialog by remember { mutableStateOf(false) }
    var lastSavedRecordId by remember { mutableStateOf<String?>(null) }
    var uiClockMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var countdownLabel by remember { mutableStateOf<String?>(null) }

    val currentSpeed = speedSample?.speedKmh ?: 0.0
    val modeReady = when (selectedPreset.config.mode) {
        PerformanceMode.STANDING_START -> currentSpeed <= selectedPreset.config.stationaryThresholdKmh
        PerformanceMode.ROLLING_START -> currentSpeed < selectedPreset.config.rollingStartKmh
    }
    val canArm = activeVehicle != null && connection == ConnectionState.CONNECTED && readiness.supported && readiness.hasValue && readiness.fresh && modeReady && countdownLabel == null
    val vehicleHistory = remember(history, activeVehicle?.id) {
        val id = activeVehicle?.id
        if (id == null) emptyList() else history.filter { it.vehicleProfileId == id }
    }

    fun armNow() {
        val latest = speedSample ?: return
        val vehicleId = activeVehicle?.id ?: return
        val stillReady = when (selectedPreset.config.mode) {
            PerformanceMode.STANDING_START -> latest.speedKmh <= selectedPreset.config.stationaryThresholdKmh
            PerformanceMode.ROLLING_START -> latest.speedKmh < selectedPreset.config.rollingStartKmh
        }
        if (!stillReady || connection != ConnectionState.CONNECTED) return
        session.setPerformanceSampling(true)
        snapshot = engine.arm(
            requestedConfig = selectedPreset.config.copy(
                useSensorFusion = false,
                vehicleProfileId = vehicleId,
            ),
            currentSpeedKmh = latest.speedKmh,
            nowNanos = latest.responseReceivedAtNanos,
            wallClockMillis = latest.wallClockMillis,
        )
        showHistory = false
    }

    fun beginCountdown() {
        if (!canArm) return
        scope.launch {
            if (preferences.reduceMotion) {
                countdownLabel = "GO"
                delay(120L)
            } else {
                for (value in 3 downTo 1) {
                    countdownLabel = value.toString()
                    delay(700L)
                }
                countdownLabel = "GO"
                delay(280L)
            }
            countdownLabel = null
            armNow()
        }
    }

    LaunchedEffect(connection) {
        session.setPerformanceSampling(connection == ConnectionState.CONNECTED)
        if (connection != ConnectionState.CONNECTED && snapshot.active) snapshot = engine.connectionLost()
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
    DisposableEffect(Unit) { onDispose { session.setPerformanceSampling(false) } }

    if (showSafetyDialog) {
        AlertDialog(
            onDismissRequest = { showSafetyDialog = false },
            title = { Text("ใช้เฉพาะพื้นที่ปิด") },
            text = { Text("ใช้ Time Slip เฉพาะสนามแข่งหรือพื้นที่ส่วนบุคคลที่ได้รับอนุญาต ห้ามทดสอบบนถนนสาธารณะ") },
            confirmButton = {
                Button(
                    onClick = {
                        repository.acknowledgeSafety()
                        safetyAcknowledged = true
                        showSafetyDialog = false
                        beginCountdown()
                    },
                ) { Text("รับทราบและเริ่ม") }
            },
            dismissButton = { TextButton(onClick = { showSafetyDialog = false }) { Text("ยกเลิก") } },
        )
    }

    countdownLabel?.let { label ->
        CountdownOverlay(label = label, reduceMotion = preferences.reduceMotion)
        return
    }

    if (snapshot.active) {
        ActiveTimeSlip(
            snapshot = snapshot,
            speedKmh = currentSpeed,
            speedUpdatedAt = speedSample?.wallClockMillis ?: 0L,
            uiClockMillis = uiClockMillis,
            reduceMotion = preferences.reduceMotion,
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                NtuScreenHeader("Time Slip", activeVehicle?.displayName ?: "เลือกรถก่อนเริ่มทดสอบ", modifier = Modifier.weight(1f), eyebrow = "PERFORMANCE")
                TextButton(onClick = { showHistory = !showHistory }) { Text(if (showHistory) "ทดสอบ" else "ประวัติ") }
            }
        }
        item { ReadinessCard(connection == ConnectionState.CONNECTED, speedSample?.speedKmh, speedSample?.transportLatencyMillis, readiness.reasonThai, readiness.sampleRateHz) }

        if (showHistory) {
            if (vehicleHistory.isEmpty()) {
                item { SimpleMessageCard("No Time Slip Records", "ผลที่สำเร็จของ ${activeVehicle?.displayName ?: "รถคันนี้"} จะถูกบันทึกไว้ในเครื่อง") }
            } else {
                items(vehicleHistory.take(20), key = { it.id }) { record ->
                    HistoryCard(
                        record,
                        isPersonalBest = isPersonalBest(record, vehicleHistory),
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
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowPresets.forEach { preset ->
                                    FilterChip(
                                        selected = selectedPreset.label == preset.label,
                                        onClick = { selectedPreset = preset; snapshot = engine.reset() },
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
                item {
                    AnimatedVisibility(
                        visible = true,
                        enter = if (preferences.reduceMotion) fadeIn(tween(0)) else fadeIn(tween(NtuMotion.Standard)) + scaleIn(initialScale = 0.96f),
                    ) {
                        ResultCard(
                            record = record,
                            isPersonalBest = isPersonalBest(record, vehicleHistory),
                            onShare = { TimeSlipShare.shareText(context, record.asShareText()) },
                        )
                    }
                }
            }
            if (snapshot.status in setOf(TimeSlipStatus.INVALID_RUN, TimeSlipStatus.CANCELLED, TimeSlipStatus.CONNECTION_LOST)) {
                item { SimpleMessageCard("ยังไม่ได้ผลทดสอบ", snapshot.message ?: "เตรียมรถและลองใหม่") }
            }
            item {
                Button(
                    onClick = { if (safetyAcknowledged) beginCountdown() else showSafetyDialog = true },
                    enabled = canArm,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("เตรียมทดสอบ ${selectedPreset.label}") }
            }
            item {
                Text(
                    startHint(connection == ConnectionState.CONNECTED, readiness.supported, readiness.hasValue, readiness.fresh, selectedPreset.config.mode, currentSpeed, selectedPreset.config.rollingStartKmh),
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
private fun CountdownOverlay(label: String, reduceMotion: Boolean) {
    Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = label,
            transitionSpec = {
                if (reduceMotion) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                else (fadeIn(tween(150)) + scaleIn(initialScale = 0.62f)) togetherWith (fadeOut(tween(120)) + scaleOut(targetScale = 1.18f))
            },
            label = "Time Slip countdown",
        ) { value ->
            Text(
                value,
                fontSize = if (value == "GO") 76.sp else 112.sp,
                fontWeight = FontWeight.Black,
                color = if (value == "GO") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun ActiveTimeSlip(
    snapshot: TimeSlipSnapshot,
    speedKmh: Double,
    speedUpdatedAt: Long,
    uiClockMillis: Long,
    reduceMotion: Boolean,
    onCancel: () -> Unit,
) {
    val delayed = speedUpdatedAt <= 0L || uiClockMillis - speedUpdatedAt > 1_800L
    val displayedElapsed = if (snapshot.status == TimeSlipStatus.RUNNING && !delayed) {
        snapshot.elapsedMillis + (uiClockMillis - speedUpdatedAt).coerceIn(0L, 1_200L)
    } else snapshot.elapsedMillis
    val animatedSpeed by animateFloatAsState(
        targetValue = speedKmh.toFloat(),
        animationSpec = if (reduceMotion) tween(0) else spring(dampingRatio = 0.92f, stiffness = 220f),
        label = "Time Slip live speed",
    )
    val latestMilestone = snapshot.speedMilestones.lastOrNull()?.let { "${it.label} • ${formatSeconds(it.elapsedMillis)} s" }
        ?: snapshot.distanceSplits.lastOrNull()?.let { "${it.target.label} • ${formatSeconds(it.elapsedMillis)} s" }

    Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (snapshot.status == TimeSlipStatus.ARMED) "พร้อมออกตัว" else "กำลังทดสอบ", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(animatedSpeed.roundToInt().toString(), fontSize = 92.sp, fontWeight = FontWeight.Black)
            Text("km/h", style = MaterialTheme.typography.titleMedium)
            Text("${formatSeconds(displayedElapsed)} s", fontSize = 50.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Text("ระยะ ${"%.1f".format(snapshot.distanceMeters)} m")
            AnimatedContent(
                targetState = latestMilestone,
                transitionSpec = {
                    if (reduceMotion) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    else fadeIn(tween(NtuMotion.Standard)) togetherWith fadeOut(tween(NtuMotion.Quick))
                },
                label = "Time Slip milestone",
            ) { milestone ->
                if (milestone != null) Text("✓ $milestone", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Text(if (delayed) "ข้อมูล OBD ล่าช้า กรุณาชะลอหรือยกเลิกการทดสอบ" else snapshot.message ?: "กำลังอ่านความเร็ว", textAlign = TextAlign.Center)
            OutlinedButton(onClick = onCancel) { Text("ยกเลิก") }
        }
    }
}

@Composable
private fun ReadinessCard(connected: Boolean, speedKmh: Double?, latencyMillis: Long?, readinessText: String, sampleRateHz: Double) {
    Card(modifier = Modifier.fillMaxWidth().ntuAnimateContentSize()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (connected) "OBD พร้อมตรวจ" else "ยังไม่เชื่อมต่อ", fontWeight = FontWeight.Bold)
                Text(speedKmh?.let { "${it.roundToInt()} km/h" } ?: "--")
            }
            Text(if (connected) readinessText else "เชื่อมต่อ ELM327 ก่อนใช้งาน", style = MaterialTheme.typography.bodySmall)
            Text("อัตรา ${"%.1f".format(sampleRateHz)} Hz • latency ${latencyMillis?.let { "$it ms" } ?: "--"}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ResultCard(record: TimeSlipRecord, isPersonalBest: Boolean, onShare: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().ntuAnimateContentSize()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("ผลล่าสุด", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (isPersonalBest) Text("PERSONAL BEST", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
            }
            Text("${formatSeconds(record.elapsedMillis)} s", fontSize = 42.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
            record.speedMilestones.forEach { Text("${it.label}: ${formatSeconds(it.elapsedMillis)} s") }
            record.distanceSplits.forEach { Text("${it.target.label}: ${formatSeconds(it.elapsedMillis)} s • ${"%.1f".format(it.trapSpeedKmh)} km/h") }
            HorizontalDivider()
            Text("Quality ${record.qualityScore}/100 • ${record.measurementQuality.name} • ${"%.1f".format(record.obdSampleRateHz)} Hz", fontWeight = FontWeight.Bold)
            Text("Latency เฉลี่ย ${record.averageTransportLatencyMillis} ms • dropped ${"%.1f".format(record.invalidSampleRatio * 100)}%", style = MaterialTheme.typography.bodySmall)
            Text("เวลาโดยประมาณ ±${record.estimatedTimingErrorMillis} ms", style = MaterialTheme.typography.bodySmall)
            record.validityNotes.take(4).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = onShare) { Text("แชร์ผล") }
        }
    }
}

@Composable
private fun HistoryCard(record: TimeSlipRecord, isPersonalBest: Boolean, onShare: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(historyTitle(record), fontWeight = FontWeight.Bold)
                if (isPersonalBest) Text("PB", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
            }
            Text("${formatSeconds(record.elapsedMillis)} s", fontSize = 28.sp, fontFamily = FontFamily.Monospace)
            Text("Quality ${record.qualityScore}/100 • ${"%.1f".format(record.obdSampleRateHz)} Hz • ${record.averageTransportLatencyMillis} ms", style = MaterialTheme.typography.bodySmall)
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

private fun isPersonalBest(record: TimeSlipRecord, history: List<TimeSlipRecord>): Boolean {
    if (record.status != TimeSlipStatus.COMPLETED || record.measurementQuality == MeasurementQuality.INVALID) return false
    val comparable = history.filter { candidate ->
        candidate.id != record.id &&
            candidate.vehicleProfileId == record.vehicleProfileId &&
            candidate.mode == record.mode &&
            candidate.selectedDistanceTarget == record.selectedDistanceTarget &&
            speedTarget(candidate) == speedTarget(record)
    }
    return comparable.none { it.elapsedMillis <= record.elapsedMillis }
}

private fun speedTarget(record: TimeSlipRecord): Int = record.speedMilestones.lastOrNull()?.targetSpeedKmh?.roundToInt() ?: 0

private fun historyTitle(record: TimeSlipRecord): String = when {
    record.mode == PerformanceMode.ROLLING_START -> "60–100 km/h"
    record.selectedDistanceTarget != null -> record.selectedDistanceTarget.label
    record.speedMilestones.isNotEmpty() -> record.speedMilestones.last().label
    else -> "Time Slip"
}

private fun startHint(connected: Boolean, supported: Boolean, hasValue: Boolean, fresh: Boolean, mode: PerformanceMode, speedKmh: Double, rollingStartKmh: Double): String = when {
    !connected -> "เชื่อมต่อ ELM327 ก่อน"
    !supported -> "รถไม่รองรับค่าความเร็ว PID 010D"
    !hasValue -> "กำลังรอค่าความเร็วจาก ECU"
    !fresh -> "ข้อมูลล่าช้า กรุณารอให้การเชื่อมต่อเสถียร"
    mode == PerformanceMode.STANDING_START && speedKmh > 1.0 -> "จอดรถให้นิ่งก่อนกดเตรียมทดสอบ"
    mode == PerformanceMode.ROLLING_START && speedKmh >= rollingStartKmh -> "ลดความเร็วให้ต่ำกว่า ${rollingStartKmh.toInt()} km/h ก่อน"
    else -> "พร้อมใช้งาน กดเตรียมทดสอบแล้วออกตัวหลังข้อความ GO โดยเวลาจริงจะเริ่มจาก telemetry ของรถ"
}
