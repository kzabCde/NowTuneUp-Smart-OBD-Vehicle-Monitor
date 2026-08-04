package com.nowtuneup.app.feature.timeslip

import android.Manifest
import android.content.Context
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
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
import com.nowtuneup.app.service.MonitoringService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TimeSlipSessionEntryPoint {
    fun obdSessionManager(): ObdSessionManager
}

@Composable
fun TimeSlipScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val connection by viewModel.connection.collectAsState()
    val session = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            TimeSlipSessionEntryPoint::class.java,
        ).obdSessionManager()
    }
    val speedSample by session.speedTelemetry.collectAsState()
    val readiness by session.speedReadiness.collectAsState()
    val performanceSampling by session.performanceSampling.collectAsState()
    val fusion = remember { TimeSlipSensorFusion(context.applicationContext) }
    val sensorState by fusion.state.collectAsState()
    val repository = remember { TimeSlipRepository(context.applicationContext) }
    val engine = remember { TimeSlipEngine() }

    var vehicleProfile by remember { mutableStateOf(repository.vehicleProfileId()) }
    var config by remember {
        mutableStateOf(
            TimeSlipConfig(
                vehicleProfileId = repository.vehicleProfileId(),
                useSensorFusion = true,
            ),
        )
    }
    var snapshot by remember { mutableStateOf(engine.snapshot()) }
    var history by remember { mutableStateOf(repository.list()) }
    var showHistory by remember { mutableStateOf(false) }
    var safetyAcknowledged by remember { mutableStateOf(repository.safetyAcknowledged()) }
    var showSafetyDialog by remember { mutableStateOf(false) }
    var lastSavedRecordId by remember { mutableStateOf<String?>(null) }
    var compareIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var uiClockMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) fusion.start()
    }

    fun stopRunServices() {
        session.setPerformanceSampling(false)
        fusion.stop()
    }

    fun armNow() {
        val latest = speedSample
        if (!readiness.ready || latest == null) return
        if (config.useSensorFusion && !fusion.hasLocationPermission()) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ),
            )
            return
        }
        repository.setVehicleProfileId(vehicleProfile)
        config = config.copy(vehicleProfileId = vehicleProfile.trim().ifBlank { "default" })
        session.setPerformanceSampling(true)
        MonitoringService.start(context)
        if (config.useSensorFusion) fusion.start()
        snapshot = engine.arm(
            requestedConfig = config,
            currentSpeedKmh = latest.speedKmh,
            nowNanos = latest.responseReceivedAtNanos,
            wallClockMillis = latest.wallClockMillis,
        )
        showHistory = false
    }

    LaunchedEffect(snapshot.active) {
        while (snapshot.active) {
            uiClockMillis = System.currentTimeMillis()
            delay(50L)
        }
    }

    LaunchedEffect(speedSample?.responseReceivedAtNanos) {
        val obd = speedSample ?: return@LaunchedEffect
        if (!snapshot.active) return@LaunchedEffect
        val telemetry = if (config.useSensorFusion) fusion.fuse(obd) else TimeSlipTelemetrySample(
            timeNanos = obd.responseReceivedAtNanos,
            wallClockMillis = obd.wallClockMillis,
            obdSpeedKmh = obd.speedKmh,
            fusedSpeedKmh = obd.speedKmh,
            transportLatencyMillis = obd.transportLatencyMillis,
            source = MeasurementSource.OBD_ONLY,
        )
        val next = engine.ingestTelemetry(telemetry)
        snapshot = next
        next.record?.let { completed ->
            if (completed.id != lastSavedRecordId) {
                repository.save(completed)
                history = repository.list()
                lastSavedRecordId = completed.id
                stopRunServices()
            }
        }
        if (!next.active && next.record == null) stopRunServices()
    }

    LaunchedEffect(connection) {
        if (connection != ConnectionState.CONNECTED && snapshot.active) {
            snapshot = engine.connectionLost()
            stopRunServices()
        }
    }

    DisposableEffect(snapshot.active) {
        val previous = view.keepScreenOn
        if (snapshot.active) view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }

    DisposableEffect(Unit) {
        onDispose {
            session.setPerformanceSampling(false)
            fusion.stop()
        }
    }

    if (showSafetyDialog) {
        AlertDialog(
            onDismissRequest = { showSafetyDialog = false },
            title = { Text("คำเตือนด้านความปลอดภัย") },
            text = {
                Text(
                    "ใช้ Time Slip เฉพาะในสนามแข่ง พื้นที่ปิด หรือพื้นที่ส่วนบุคคลที่ได้รับอนุญาต " +
                        "ห้ามทดสอบบนถนนสาธารณะ และยึดอุปกรณ์ให้แน่นก่อนออกตัว",
                )
            },
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
            currentSpeedKmh = speedSample?.speedKmh ?: snapshot.currentSpeedKmh,
            speedUpdatedAt = speedSample?.wallClockMillis ?: 0L,
            uiClockMillis = uiClockMillis,
            connected = connection == ConnectionState.CONNECTED,
            performanceSampling = performanceSampling,
            sensorState = sensorState,
            landscape = isLandscape,
            onCancel = {
                snapshot = engine.cancel()
                stopRunServices()
            },
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
                    Text("Accurate Performance Test • NowTuneUp 1.8.0", style = MaterialTheme.typography.bodySmall)
                }
                FilledTonalButton(onClick = { showHistory = !showHistory }) {
                    Icon(Icons.Default.History, contentDescription = null)
                    Text(if (showHistory) " ทดสอบ" else " ประวัติ")
                }
            }
        }

        item {
            ReadinessCard(
                connected = connection == ConnectionState.CONNECTED,
                speedKmh = speedSample?.speedKmh,
                readiness = readiness,
                performanceSampling = performanceSampling,
                sensorState = sensorState,
            )
        }

        if (showHistory) {
            item { HistorySummary(history, vehicleProfile) }
            val comparison = compareIds.takeIf { it.size == 2 }?.let { repository.compare(it[0], it[1]) }
            comparison?.let { item { ComparisonCard(it) } }
            items(history, key = { it.id }) { record ->
                HistoryRecordCard(
                    record = record,
                    selectedForCompare = record.id in compareIds,
                    onCompare = {
                        compareIds = when {
                            record.id in compareIds -> compareIds - record.id
                            compareIds.size < 2 -> compareIds + record.id
                            else -> listOf(compareIds.last(), record.id)
                        }
                    },
                    onShare = { TimeSlipShare.shareText(context, record.asShareText()) },
                    onImage = { TimeSlipShare.shareImage(context, record) },
                    onReplay = {
                        repository.replay(record.id)?.let { replayed ->
                            snapshot = engine.reset().copy(record = replayed, status = TimeSlipStatus.COMPLETED)
                            showHistory = false
                        }
                    },
                    onDelete = {
                        repository.delete(record.id)
                        history = repository.list()
                        compareIds = compareIds - record.id
                    },
                )
            }
        } else {
            item { PresetCard(config, onConfigChange = { config = it }) }
            item {
                SetupCard(
                    config = config,
                    vehicleProfile = vehicleProfile,
                    onVehicleProfileChange = { vehicleProfile = it.take(40) },
                    onConfigChange = { config = it },
                )
            }
            snapshot.record?.let { record ->
                item {
                    ResultCard(
                        record = record,
                        onShare = { TimeSlipShare.shareText(context, record.asShareText()) },
                        onCsv = { TimeSlipShare.shareText(context, record.asCsv(), "text/csv") },
                        onImage = { TimeSlipShare.shareImage(context, record) },
                    )
                }
                item { TimeSlipGraph(record) }
            }
            item {
                Button(
                    onClick = { if (safetyAcknowledged) armNow() else showSafetyDialog = true },
                    enabled = connection == ConnectionState.CONNECTED && readiness.ready,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text(" เตรียมทดสอบ")
                }
            }
            item {
                OutlinedButton(
                    onClick = { TimeSlipShare.shareText(context, session.diagnosticReport()) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Assessment, contentDescription = null)
                    Text(" ส่งออกรายงานอุปกรณ์และ OBD")
                }
            }
            item {
                Text(
                    if (config.useSensorFusion) {
                        "Sensor Fusion: OBD speed เป็นค่าหลัก และใช้ GNSS/accelerometer ช่วยแก้ค่า พร้อมบันทึก accuracy, slope และ raw samples โดยไม่เก็บพิกัด"
                    } else {
                        "OBD-only mode: เวลาและระยะทางมีความคลาดเคลื่อนตามความเร็วในการตอบสนองของ ECU และ ELM327"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ReadinessCard(
    connected: Boolean,
    speedKmh: Double?,
    readiness: com.nowtuneup.app.data.obd.session.SpeedPidReadiness,
    performanceSampling: Boolean,
    sensorState: TimeSlipSensorState,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (readiness.ready) "พร้อมทดสอบ" else "ยังไม่พร้อม", fontWeight = FontWeight.Bold)
                Text(speedKmh?.let { "${it.roundToInt()} km/h" } ?: "--")
            }
            Text(if (connected) readiness.reasonThai else "กรุณาเชื่อมต่อ OBD-II ก่อน", style = MaterialTheme.typography.bodySmall)
            Text(
                "Samples ${readiness.stableSamples} • ${"%.1f".format(readiness.sampleRateHz)} Hz • " +
                    "Latency mode ${if (performanceSampling) "Performance" else "Normal"}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "GNSS ${if (sensorState.gpsAvailable) "พร้อม" else "รอข้อมูล"} • ดาวเทียม ${sensorState.satellitesUsed} • " +
                    "Accuracy ${sensorState.gpsAccuracyMeters?.let { "%.1f m".format(it) } ?: "--"}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PresetCard(config: TimeSlipConfig, onConfigChange: (TimeSlipConfig) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Quick presets", fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PresetButton("0–60") { onConfigChange(config.copy(mode = PerformanceMode.STANDING_START, selectedDistanceTarget = null, speedOnlyTargetKmh = 60.0)) }
                PresetButton("0–100") { onConfigChange(config.copy(mode = PerformanceMode.STANDING_START, selectedDistanceTarget = null, speedOnlyTargetKmh = 100.0)) }
                PresetButton("60–100") { onConfigChange(config.copy(mode = PerformanceMode.ROLLING_START, selectedDistanceTarget = null, rollingStartKmh = 60.0, rollingTargetKmh = 100.0)) }
                PresetButton("1/8 mi") { onConfigChange(config.copy(mode = PerformanceMode.STANDING_START, selectedDistanceTarget = DistanceTarget.EIGHTH_MILE)) }
                PresetButton("1/4 mi") { onConfigChange(config.copy(mode = PerformanceMode.STANDING_START, selectedDistanceTarget = DistanceTarget.QUARTER_MILE)) }
                PresetButton("1/2 mi") { onConfigChange(config.copy(mode = PerformanceMode.STANDING_START, selectedDistanceTarget = DistanceTarget.HALF_MILE)) }
                PresetButton("1 mile") { onConfigChange(config.copy(mode = PerformanceMode.STANDING_START, selectedDistanceTarget = DistanceTarget.ONE_MILE)) }
            }
        }
    }
}

@Composable
private fun PresetButton(label: String, onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text(label) })
}

@Composable
private fun SetupCard(
    config: TimeSlipConfig,
    vehicleProfile: String,
    onVehicleProfileChange: (String) -> Unit,
    onConfigChange: (TimeSlipConfig) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("การตั้งค่า 1.8.0", fontWeight = FontWeight.Bold)
            TextField(
                value = vehicleProfile,
                onValueChange = onVehicleProfileChange,
                label = { Text("โปรไฟล์รถ เช่น Fortuner-2015") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            ToggleRow(
                title = "OBD + GNSS + Accelerometer",
                detail = "เพิ่ม confidence และเก็บ raw telemetry โดยไม่เก็บพิกัด",
                checked = config.useSensorFusion,
                onChecked = { onConfigChange(config.copy(useSensorFusion = it)) },
            )
            ToggleRow(
                title = "One-foot rollout",
                detail = "เริ่มเวลาหลังรถเคลื่อนที่ 1 ฟุต เหมาะกับการเทียบ drag timing",
                checked = config.oneFootRollout,
                onChecked = { onConfigChange(config.copy(oneFootRollout = it)) },
            )
            Text(
                when {
                    config.mode == PerformanceMode.ROLLING_START -> "Rolling ${config.rollingStartKmh.toInt()}–${config.rollingTargetKmh.toInt()} km/h"
                    config.selectedDistanceTarget != null -> "Standing start • ${config.selectedDistanceTarget.label}"
                    else -> "Standing start • 0–${config.speedOnlyTargetKmh.toInt()} km/h"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    detail: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun ActiveRunScreen(
    snapshot: TimeSlipSnapshot,
    currentSpeedKmh: Double,
    speedUpdatedAt: Long,
    uiClockMillis: Long,
    connected: Boolean,
    performanceSampling: Boolean,
    sensorState: TimeSlipSensorState,
    landscape: Boolean,
    onCancel: () -> Unit,
) {
    val delayed = speedUpdatedAt <= 0L || uiClockMillis - speedUpdatedAt > 800L
    val displayedElapsed = if (snapshot.status == TimeSlipStatus.RUNNING && speedUpdatedAt > 0L) {
        snapshot.elapsedMillis + (uiClockMillis - speedUpdatedAt).coerceIn(0L, 1_500L)
    } else snapshot.elapsedMillis
    val target = when {
        snapshot.config.mode == PerformanceMode.ROLLING_START -> "${snapshot.config.rollingTargetKmh.toInt()} km/h"
        snapshot.config.selectedDistanceTarget != null -> snapshot.config.selectedDistanceTarget.label
        else -> "${snapshot.config.speedOnlyTargetKmh.toInt()} km/h"
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
                MetricBlock("ระยะ", "%.1f".format(snapshot.distanceMeters), "m")
                ActiveStatus(snapshot, target, delayed, connected, performanceSampling, sensorState, onCancel)
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(if (snapshot.status == TimeSlipStatus.ARMED) "READY" else "RUNNING", fontWeight = FontWeight.Black, fontSize = 24.sp)
                Text(currentSpeedKmh.roundToInt().toString(), fontSize = 88.sp, fontWeight = FontWeight.Black)
                Text("km/h", style = MaterialTheme.typography.titleMedium)
                Text(formatSeconds(displayedElapsed), fontSize = 52.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    MetricBlock("เป้าหมาย", target, "")
                    MetricBlock("ระยะ", "%.1f".format(snapshot.distanceMeters), "m")
                }
                ActiveStatus(snapshot, target, delayed, connected, performanceSampling, sensorState, onCancel)
            }
        }
    }
}

@Composable
private fun ActiveStatus(
    snapshot: TimeSlipSnapshot,
    target: String,
    delayed: Boolean,
    connected: Boolean,
    performanceSampling: Boolean,
    sensorState: TimeSlipSensorState,
    onCancel: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(snapshot.message ?: target, textAlign = TextAlign.Center)
        Text(
            when {
                !connected -> "OBD disconnected"
                delayed -> "OBD data delayed"
                !performanceSampling -> "กำลังสลับเข้า Performance Sampling"
                else -> "Speed priority active • GNSS ${sensorState.satellitesUsed} satellites"
            },
            style = MaterialTheme.typography.bodySmall,
        )
        if (snapshot.reactionTimeMillis > 0L) Text("Reaction ${formatSeconds(snapshot.reactionTimeMillis)} s", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onCancel) {
            Icon(Icons.Default.Stop, contentDescription = null)
            Text(" ยกเลิก")
        }
    }
}

@Composable
private fun MetricBlock(title: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.bodySmall)
        Text(value, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        if (unit.isNotBlank()) Text(unit, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ResultCard(
    record: TimeSlipRecord,
    onShare: () -> Unit,
    onCsv: () -> Unit,
    onImage: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ผลล่าสุด", fontWeight = FontWeight.Bold)
            Text("${formatSeconds(record.elapsedMillis)} s", fontSize = 42.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            if (record.reactionTimeMillis > 0L) Text("Reaction ${formatSeconds(record.reactionTimeMillis)} s")
            if (record.oneFootRolloutEnabled) Text("One-foot rollout ${formatSeconds(record.rolloutMillis)} s")
            record.speedMilestones.forEach { Text("${it.label}: ${formatSeconds(it.elapsedMillis)} s") }
            record.distanceSplits.forEach { Text("${it.target.label}: ${formatSeconds(it.elapsedMillis)} s • ${"%.1f".format(it.trapSpeedKmh)} km/h") }
            HorizontalDivider()
            Text("${record.dataSource} • ${"%.1f".format(record.obdSampleRateHz)} Hz")
            Text("Speed confidence ${(record.speedConfidence ?: ConfidenceLevel.LOW).name} • Distance ${(record.distanceConfidence ?: ConfidenceLevel.LOW).name}")
            Text("Timing error ±${record.estimatedTimingErrorMillis} ms • Slope ${record.averageSlopePercent?.let { "%.2f%%".format(it) } ?: "--"}")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onShare) { Icon(Icons.Default.Share, null); Text(" ข้อความ") }
                FilledTonalButton(onClick = onCsv) { Icon(Icons.Default.Assessment, null); Text(" CSV") }
                FilledTonalButton(onClick = onImage) { Icon(Icons.Default.Image, null); Text(" ภาพ") }
            }
        }
    }
}

@Composable
private fun TimeSlipGraph(record: TimeSlipRecord) {
    val samples = record.rawSamples.orEmpty()
    if (samples.size < 2) return
    val lineColor = MaterialTheme.colorScheme.primary
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Speed vs Time • Raw replay data", fontWeight = FontWeight.Bold)
            Canvas(modifier = Modifier.fillMaxWidth().height(190.dp)) {
                val firstTime = samples.first().timeNanos
                val duration = (samples.last().timeNanos - firstTime).coerceAtLeast(1L).toDouble()
                val maxSpeed = samples.maxOf { it.fusedSpeedKmh }.coerceAtLeast(1.0)
                val path = Path()
                samples.forEachIndexed { index, sample ->
                    val x = ((sample.timeNanos - firstTime) / duration * size.width).toFloat()
                    val y = (size.height - sample.fusedSpeedKmh / maxSpeed * size.height).toFloat()
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawLine(lineColor.copy(alpha = 0.35f), Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2f)
                drawPath(path, lineColor, style = Stroke(width = 5f))
            }
            Text("${samples.size} samples • สามารถส่งออก CSV และ replay ผลเดิมได้", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun HistorySummary(history: List<TimeSlipRecord>, vehicleProfile: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("ประวัติ ${history.size} รายการ", fontWeight = FontWeight.Bold)
            Text("โปรไฟล์ปัจจุบัน: ${vehicleProfile.ifBlank { "default" }} • เลือก 2 รายการเพื่อเปรียบเทียบ", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ComparisonCard(comparison: TimeSlipComparison) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("เปรียบเทียบสองรอบ", fontWeight = FontWeight.Bold)
            Text("เวลาต่างกัน ${signedSeconds(comparison.elapsedDeltaMillis)} s")
            Text("ความเร็วสูงสุดต่างกัน ${"%+.1f".format(comparison.maximumSpeedDeltaKmh)} km/h")
            Text("Sample rate ต่างกัน ${"%+.1f".format(comparison.sampleRateDeltaHz)} Hz")
        }
    }
}

@Composable
private fun HistoryRecordCard(
    record: TimeSlipRecord,
    selectedForCompare: Boolean,
    onCompare: () -> Unit,
    onShare: () -> Unit,
    onImage: () -> Unit,
    onReplay: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(formatDate(record.startedAtEpochMillis), fontWeight = FontWeight.Bold)
                    Text("${record.vehicleProfileId ?: "default"} • ${record.dataSource}", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Compare", fontSize = 11.sp)
                    Checkbox(checked = selectedForCompare, onCheckedChange = { onCompare() })
                }
            }
            Text("${formatSeconds(record.elapsedMillis)} s", fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text("Quality ${record.measurementQuality} • Confidence ${(record.speedConfidence ?: ConfidenceLevel.LOW).name}/${(record.distanceConfidence ?: ConfidenceLevel.LOW).name}")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onShare) { Icon(Icons.Default.Share, null); Text("แชร์") }
                TextButton(onClick = onImage) { Icon(Icons.Default.Image, null); Text("ภาพ") }
                TextButton(onClick = onReplay, enabled = record.rawSamples.orEmpty().size >= 2) { Icon(Icons.Default.Refresh, null); Text("Replay") }
                TextButton(onClick = onDelete) { Icon(Icons.Default.Delete, null); Text("ลบ") }
            }
        }
    }
}

private fun formatDate(epochMillis: Long): String = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date(epochMillis))
private fun signedSeconds(milliseconds: Long): String = "%+.3f".format(milliseconds / 1_000.0)
