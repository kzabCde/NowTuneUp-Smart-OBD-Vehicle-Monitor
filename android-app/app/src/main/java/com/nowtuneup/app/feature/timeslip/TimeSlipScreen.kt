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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
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
    SimplePreset("0–60 mph", sixtyMphConfig()),
    SimplePreset("0–100 km/h", TimeSlipConfig(mode = PerformanceMode.STANDING_START, selectedDistanceTarget = null, speedOnlyTargetKmh = 100.0, enabledMilestonesKmh = listOf(60.0, 100.0), useSensorFusion = false, oneFootRollout = false)),
    SimplePreset("60–100 km/h", TimeSlipConfig(mode = PerformanceMode.ROLLING_START, selectedDistanceTarget = null, rollingStartKmh = 60.0, rollingTargetKmh = 100.0, enabledMilestonesKmh = listOf(100.0), useSensorFusion = false, oneFootRollout = false)),
    SimplePreset("1/4 mile", TimeSlipConfig(mode = PerformanceMode.STANDING_START, selectedDistanceTarget = DistanceTarget.QUARTER_MILE, enabledMilestonesKmh = listOf(60.0, 100.0), useSensorFusion = false, oneFootRollout = false)),
    SimplePreset("1/2 mile", TimeSlipConfig(mode = PerformanceMode.STANDING_START, selectedDistanceTarget = DistanceTarget.HALF_MILE, enabledMilestonesKmh = listOf(60.0, 100.0, 160.0), useSensorFusion = false, oneFootRollout = false)),
    SimplePreset("1 mile", TimeSlipConfig(mode = PerformanceMode.STANDING_START, selectedDistanceTarget = DistanceTarget.ONE_MILE, enabledMilestonesKmh = listOf(60.0, 100.0, 160.0), useSensorFusion = false, oneFootRollout = false)),
)

@Composable
fun TimeSlipScreen(viewModel: MainViewModel, onOpenHistory: (() -> Unit)? = null, onSessionActiveChange: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val view = LocalView.current
    val readings by viewModel.readings.collectAsState()
    val connection by viewModel.connection.collectAsState()
    val connectionUi by viewModel.connectionUiState.collectAsState()
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
    var selectedPreset by remember { mutableStateOf(simplePresets.first { it.label == "0–100 km/h" }) }
    var snapshot by remember { mutableStateOf(engine.snapshot()) }
    var history by remember { mutableStateOf(repository.list()) }
    var historyQuery by rememberSaveable { mutableStateOf("") }
    var historyMode by remember { mutableStateOf<PerformanceMode?>(null) }
    var historySort by remember { mutableStateOf(HistorySort.NEWEST) }
    var comparisonId by remember { mutableStateOf<String?>(null) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var selectedRecordId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    var safetyAcknowledged by remember { mutableStateOf(repository.safetyAcknowledged()) }
    var showSafetyDialog by remember { mutableStateOf(false) }
    var lastSavedRecordId by remember { mutableStateOf<String?>(null) }
    var uiClockMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var countdownLabel by remember { mutableStateOf<String?>(null) }
    var countdownJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val currentSpeed = speedSample?.speedKmh ?: 0.0
    val modeReady = when (selectedPreset.config.mode) {
        PerformanceMode.STANDING_START -> currentSpeed <= selectedPreset.config.stationaryThresholdKmh
        PerformanceMode.ROLLING_START -> currentSpeed < selectedPreset.config.rollingStartKmh
    }
    val simulated = connectionUi.transportType == com.nowtuneup.app.domain.model.ObdTransportType.MOCK
    val canArm = !simulated && activeVehicle != null && connection == ConnectionState.CONNECTED && readiness.supported && readiness.hasValue && readiness.fresh && modeReady && countdownLabel == null
    val vehicleHistory = remember(history, activeVehicle?.id) {
        val id = activeVehicle?.id
        if (id == null) emptyList() else history.filter { it.vehicleProfileId == id || it.vehicleProfileId.isNullOrBlank() }
    }

    fun armNow() {
        val latest = speedSample ?: return
        val vehicleId = activeVehicle?.id ?: return
        val stillReady = when (selectedPreset.config.mode) {
            PerformanceMode.STANDING_START -> latest.speedKmh <= selectedPreset.config.stationaryThresholdKmh
            PerformanceMode.ROLLING_START -> latest.speedKmh < selectedPreset.config.rollingStartKmh
        }
        val speedReady = readiness.fresh && readiness.hasValue && readiness.supported
        if (!stillReady || connection != ConnectionState.CONNECTED || simulated || !speedReady) return
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
        countdownJob = scope.launch {
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
        fun signal(pid: Int) = captureTimeSlipSignal(readings.firstOrNull { it.pid == pid }, sample.responseReceivedAtNanos)
        val next = engine.ingestTelemetry(
            TimeSlipTelemetrySample(
                timeNanos = sample.responseReceivedAtNanos,
                wallClockMillis = sample.wallClockMillis,
                obdSpeedKmh = sample.speedKmh,
                fusedSpeedKmh = sample.speedKmh,
                transportLatencyMillis = sample.transportLatencyMillis,
                source = MeasurementSource.OBD_ONLY,
                rpmSample = signal(0x0C),
                throttleSample = signal(0x11),
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
    LaunchedEffect(snapshot.active, countdownLabel) { onSessionActiveChange(snapshot.active || countdownLabel != null) }
    DisposableEffect(Unit) { onDispose { session.setPerformanceSampling(false); onSessionActiveChange(false) } }

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

    BackHandler(enabled = countdownLabel != null || snapshot.active) {
        if (countdownLabel != null) { countdownJob?.cancel(); countdownLabel = null }
        else snapshot = engine.cancel()
    }
    countdownLabel?.let { label ->
        CountdownOverlay(label = label, reduceMotion = preferences.reduceMotion,
            onCancel = { countdownJob?.cancel(); countdownLabel = null })
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

    val selectedRecord = vehicleHistory.firstOrNull { it.id == selectedRecordId }
    BackHandler(enabled = selectedRecord != null || showHistory) {
        if (selectedRecord != null) selectedRecordId = null else showHistory = false
    }
    pendingDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("ลบผลทดสอบนี้?") },
            text = { Text("ผลทดสอบและข้อมูลกราฟนี้จะถูกลบออกจากเครื่อง") },
            confirmButton = { TextButton(onClick = {
                repository.delete(id)
                history = repository.list()
                if (selectedRecordId == id) selectedRecordId = null
                pendingDeleteId = null
            }) { Text("ลบผล", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { pendingDeleteId = null }) { Text("เก็บไว้") } },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            NtuScreenHeader(if (selectedRecord != null) historyTitle(selectedRecord) else "Performance",
                activeVehicle?.displayName ?: "เลือกรถก่อนเริ่มทดสอบ", eyebrow = "TIME SLIP")
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !showHistory && selectedRecord == null,
                    onClick = { showHistory = false; selectedRecordId = null }, label = { Text("ทดสอบ") })
                FilterChip(selected = showHistory || selectedRecord != null,
                    onClick = { if (onOpenHistory != null) onOpenHistory() else { showHistory = true; selectedRecordId = null } }, label = { Text("ประวัติ (${vehicleHistory.size})") })
            }
        }
        if (selectedRecord != null) {
            item { ResultCard(selectedRecord, isPersonalBest(selectedRecord, vehicleHistory),
                onShare = { scope.launch { sharePerformanceReport(context, selectedRecord) } }) }
            item {
                val compatible = vehicleHistory.filter { it.id != selectedRecord.id && TimeSlipAnalysis.comparable(selectedRecord, it) }
                val comparison = compatible.firstOrNull { it.id == comparisonId } ?: previousComparable(selectedRecord, vehicleHistory)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    compatible.forEach { other ->
                        FilterChip(selected = other.id == comparison?.id, onClick = { comparisonId = other.id }, label = {
                            Text("${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(other.startedAtEpochMillis))} · ${formatSeconds(other.elapsedMillis)} s")
                        })
                    }
                }
                TimeSlipCharts(selectedRecord, comparison)
            }
        } else if (showHistory) {
            item {
                OutlinedTextField(value = historyQuery, onValueChange = { historyQuery = it },
                    label = { Text("Search mode, vehicle, quality or date") }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = historyMode == null, onClick = { historyMode = null }, label = { Text("All modes") })
                    PerformanceMode.entries.forEach { mode -> FilterChip(selected = historyMode == mode, onClick = { historyMode = mode }, label = { Text(mode.name.replace('_', ' ')) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = historySort == HistorySort.NEWEST, onClick = { historySort = HistorySort.NEWEST }, label = { Text("Newest") })
                    FilterChip(selected = historySort == HistorySort.VALID_ELAPSED, onClick = { historySort = HistorySort.VALID_ELAPSED }, label = { Text("Valid elapsed time") })
                }
            }
            val filtered = performanceHistory(vehicleHistory, historyQuery, historyMode, historySort)
            if (filtered.isEmpty()) item { SimpleMessageCard("No matching results", "Saved local records remain available when you clear the filters.") }
            filtered.groupBy { performanceTitle(it) }.forEach { (title, records) ->
                item { Text(title, style = MaterialTheme.typography.titleMedium) }
                items(records, key = { it.id }) { record ->
                    HistoryCard(record, onOpen = { selectedRecordId = record.id; comparisonId = null },
                        isPersonalBest = isPersonalBest(record, vehicleHistory),
                        onShare = { scope.launch { sharePerformanceReport(context, record) } },
                        onDelete = { pendingDeleteId = record.id })
                }
            }
        } else {
            item { ReadinessCard(connection == ConnectionState.CONNECTED, speedSample?.speedKmh.takeIf { readiness.fresh && connection == ConnectionState.CONNECTED }, speedSample?.transportLatencyMillis, if (simulated) "SIMULATED SOURCE · vehicle performance recording disabled" else readiness.reasonThai, readiness.sampleRateHz) }
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
                            onShare = { scope.launch { sharePerformanceReport(context, record) } },
                        )
                    }
                }
            }
            snapshot.record?.let { record ->
                item { TimeSlipCharts(record, previousComparable(record, vehicleHistory)) }
            }
            if (snapshot.status in setOf(TimeSlipStatus.INVALID_RUN, TimeSlipStatus.CANCELLED, TimeSlipStatus.CONNECTION_LOST)) {
                item { SimpleMessageCard("ยังไม่ได้ผลทดสอบ", snapshot.message ?: "เตรียมรถและลองใหม่") }
            }
            item {
                Button(
                    onClick = { if (safetyAcknowledged) beginCountdown() else showSafetyDialog = true },
                    enabled = canArm,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
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
private fun CountdownOverlay(label: String, reduceMotion: Boolean, onCancel: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("เตรียมการทดสอบ", style = MaterialTheme.typography.titleLarge)
        AnimatedContent(targetState = label,
            transitionSpec = { fadeIn(tween(if (reduceMotion) 0 else 120)) togetherWith fadeOut(tween(if (reduceMotion) 0 else 120)) },
            label = "Time Slip countdown") { value ->
            Text(value, modifier = Modifier.padding(vertical = 20.dp), fontSize = 64.sp,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold,
                color = if (value == "GO") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
        Text("เวลาจริงเริ่มจากข้อมูลความเร็วที่วัดได้ ไม่ใช่ภาพนับถอยหลัง", textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onCancel, modifier = Modifier.padding(top = 20.dp)) { Text("ยกเลิก") }
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

    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        val compact = maxHeight < 420.dp
        Column(modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp)) {
            Text(if (snapshot.status == TimeSlipStatus.ARMED) "พร้อมออกตัว" else "กำลังทดสอบ", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(if (delayed) "—" else animatedSpeed.roundToInt().toString(), fontSize = if (compact) 56.sp else 80.sp,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { contentDescription = if (delayed) "Speed unavailable: delayed OBD source" else "Measured speed ${speedKmh.roundToInt()} kilometres per hour" })
            Text("km/h", style = MaterialTheme.typography.titleMedium)
            Text("${formatSeconds(displayedElapsed)} s", fontSize = if (compact) 32.sp else 44.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Text("ระยะโดยประมาณ ${"%.1f".format(snapshot.distanceMeters)} m", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
internal fun ResultCard(record: TimeSlipRecord, isPersonalBest: Boolean, onShare: () -> Unit) {
    var showQuality by rememberSaveable(record.id) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().ntuAnimateContentSize()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(historyTitle(record), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (isPersonalBest) Text("Best local", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            }
            Text(if (record.hasMeasuredElapsed()) "${formatSeconds(record.elapsedMillis)} s" else "Elapsed unavailable", style = MaterialTheme.typography.displaySmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
            record.speedMilestones.forEach { split ->
                ReportValueRow(split.label, "${formatSeconds(split.elapsedMillis)} s")
            }
            record.distanceSplits.forEach { split ->
                ReportValueRow(split.target.label, "${formatSeconds(split.elapsedMillis)} s")
                Text("Trap speed ${"%.1f".format(split.trapSpeedKmh)} km/h", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Vehicle: ${record.vehicleProfileId?.takeIf { it.isNotBlank() } ?: "Legacy / unassigned"}", style = MaterialTheme.typography.bodySmall)
            Text(record.recordedDateLabel(), style = MaterialTheme.typography.bodySmall)
            Text("Source: ${record.dataSource} · rollout ${if (record.oneFootRolloutEnabled) "1 ft" else "off"}", style = MaterialTheme.typography.bodySmall)
            Text("Distance and acceleration are estimated from OBD speed, not GPS-grade timing.", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("Quality ${record.qualityScore}/100 • ${record.measurementQuality.name} • ${"%.1f".format(record.obdSampleRateHz)} Hz", fontWeight = FontWeight.Bold)
            TextButton(onClick = { showQuality = !showQuality }) { Text(if (showQuality) "ซ่อนคุณภาพสัญญาณ" else "ดูคุณภาพสัญญาณ") }
            if (showQuality) {
                Text("Latency เฉลี่ย ${record.averageTransportLatencyMillis} ms • dropped ${"%.1f".format(record.invalidSampleRatio * 100)}%", style = MaterialTheme.typography.bodySmall)
                Text("เวลาโดยประมาณ ${record.timingEstimateLabel()}", style = MaterialTheme.typography.bodySmall)
                record.validityNotes.take(4).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
            Button(onClick = onShare, modifier = Modifier.fillMaxWidth()) { Text("แชร์รายงาน") }
        }
    }
}

@Composable
internal fun HistoryCard(record: TimeSlipRecord, onOpen: () -> Unit, isPersonalBest: Boolean, onShare: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(historyTitle(record), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                if (isPersonalBest) Text("Best local", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            }
            Text(if (record.hasMeasuredElapsed()) "${formatSeconds(record.elapsedMillis)} s" else "Elapsed unavailable", fontSize = 28.sp, fontFamily = FontFamily.Monospace)
            Text(record.recordedDateLabel(), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("คุณภาพ ${record.qualityScore}/100 • ${"%.1f".format(record.obdSampleRateHz)} Hz", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("ดูกราฟ / รายละเอียด") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onShare) { Text("แชร์") }
                TextButton(onClick = onDelete) { Text("ลบ", color = MaterialTheme.colorScheme.error) }
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

internal fun isPersonalBest(record: TimeSlipRecord, history: List<TimeSlipRecord>): Boolean {
    if (!record.hasMeasuredElapsed()) return false
    val comparable = history.filter { it.id != record.id && it.hasMeasuredElapsed() && TimeSlipAnalysis.comparable(record, it) }
    return comparable.none { it.elapsedMillis <= record.elapsedMillis }
}

internal fun previousComparable(record: TimeSlipRecord, history: List<TimeSlipRecord>): TimeSlipRecord? =
    history.filter { it.id != record.id && it.startedAtEpochMillis < record.startedAtEpochMillis && TimeSlipAnalysis.comparable(record, it) }
        .maxByOrNull { it.startedAtEpochMillis }

internal fun historyTitle(record: TimeSlipRecord): String = when {
    record.selectedDistanceTarget != null -> record.selectedDistanceTarget.label
    TimeSlipAnalysis.finishMilestone(record) != null -> TimeSlipAnalysis.finishMilestone(record)!!.label
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


@Composable
private fun ReportValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
    }
}

internal suspend fun sharePerformanceReport(context: android.content.Context, record: TimeSlipRecord) {
    try {
        TimeSlipShare.shareReport(context, record)
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        TimeSlipShare.shareText(context, TimeSlipShare.reportText(record))
    }
}

internal fun TimeSlipRecord.recordedDateLabel(): String = if (startedAtEpochMillis > 0L)
    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
        .format(java.util.Date(startedAtEpochMillis)) else "Date unavailable · legacy record"
