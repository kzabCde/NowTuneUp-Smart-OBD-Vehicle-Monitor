package com.nowtuneup.app.feature.timeslip

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nowtuneup.app.presentation.dashboard.MainViewModel
import com.nowtuneup.app.ui.components.NtuScreenHeader
import com.nowtuneup.app.ui.vehicle.rememberVehicleProfileRepository
import kotlinx.coroutines.launch

/** Dedicated history destination. Opening history never switches on performance polling. */
@Composable
fun PerformanceHistoryScreen(viewModel: MainViewModel, onStartPerformance: () -> Unit = {}) {
    val context = LocalContext.current
    val repository = remember(context) { TimeSlipRepository(context.applicationContext) }
    val vehiclesRepository = rememberVehicleProfileRepository()
    val vehicles by vehiclesRepository.profiles.collectAsState()
    var records by remember { mutableStateOf(repository.list()) }
    var query by rememberSaveable { mutableStateOf("") }
    var vehicleId by rememberSaveable { mutableStateOf<String?>(null) }
    var testType by rememberSaveable { mutableStateOf<String?>(null) }
    var dateRange by remember { mutableStateOf(HistoryDateRange.ALL) }
    var sort by remember { mutableStateOf(HistorySort.NEWEST) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var comparisonId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<TimeSlipRecord?>(null) }
    val scope = rememberCoroutineScope()
    val selected = records.firstOrNull { it.id == selectedId }
    val vehicleNames = remember(vehicles) { vehicles.associate { it.id to it.displayName } }
    val availableVehicles = remember(records) { records.map { it.vehicleProfileId.orEmpty() }.distinct() }
    val types = remember(records) { records.map(::performanceTitle).distinct() }
    val filtered = remember(records, query, vehicleId, testType, dateRange, sort) {
        filterHistoryPresentation(records, query, vehicleId, testType, dateRange, sort, System.currentTimeMillis())
    }
    // Refresh when navigation recreates this destination; no duplicate repository or migration is introduced.
    LaunchedEffect(Unit) { records = repository.list() }
    BackHandler(enabled = selected != null) { selectedId = null; comparisonId = null }
    deleting?.let { record ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("ลบผลทดสอบ?") },
            text = { Text("ลบเฉพาะผลทดสอบและตัวอย่างกราฟนี้ รถ หน้าปัด และข้อมูลอื่นจะยังอยู่") },
            confirmButton = { TextButton(onClick = {
                repository.delete(record.id); records = repository.list(); deleting = null
                if (selectedId == record.id) selectedId = null
            }) { Text("ลบผลทดสอบ", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("ยกเลิก") } })
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { NtuScreenHeader(if (selected == null) "ประวัติ" else "รายงานผลทดสอบ",
            if (selected == null) "ผลการทดสอบที่บันทึกไว้ในเครื่อง · ${records.size} รายการ" else
                "${vehicleNames[selected.vehicleProfileId] ?: "รถเดิม / ไม่ระบุ"} · ${selected.recordedDateLabel()}", eyebrow = "PERFORMANCE HISTORY") }
        if (selected != null) {
            item { TextButton(onClick = { selectedId = null }) { Text("กลับไปประวัติ") } }
            item { ResultCard(selected, isPersonalBest(selected, records)) { scope.launch { sharePerformanceReport(context, selected) } } }
            item {
                val compatible = records.filter { it.id != selected.id && TimeSlipAnalysis.comparable(selected, it) }
                val comparison = compatible.firstOrNull { it.id == comparisonId } ?: previousComparable(selected, records)
                if (compatible.isNotEmpty()) {
                    Text("เปรียบเทียบเฉพาะรถ รูปแบบ เป้าหมาย และ rollout ที่ตรงกัน", style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        compatible.forEach { record -> FilterChip(selected = record.id == comparison?.id,
                            onClick = { comparisonId = record.id },
                            label = { Text("${record.recordedDateLabel()} · ${formatSeconds(record.elapsedMillis)} s") }) }
                    }
                }
                TimeSlipCharts(selected, comparison)
            }
            item { OutlinedButton(onClick = { deleting = selected }, modifier = Modifier.fillMaxWidth()) { Text("ลบผลทดสอบนี้") } }
        } else {
            item {
                OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("ค้นหาการทดสอบ รถ หรือวันที่") })
                HistoryFilterRow {
                    FilterChip(vehicleId == null, { vehicleId = null }, label = { Text("รถทั้งหมด") })
                    availableVehicles.forEach { id -> FilterChip(vehicleId == id, { vehicleId = id },
                        label = { Text(vehicleNames[id] ?: if (id.isBlank()) "รถเดิม / ไม่ระบุ" else "รถที่เก็บไว้: ${id.take(8)}") }) }
                }
                HistoryFilterRow {
                    FilterChip(testType == null, { testType = null }, label = { Text("การทดสอบทั้งหมด") })
                    types.forEach { type -> FilterChip(testType == type, { testType = type }, label = { Text(type) }) }
                }
                HistoryFilterRow {
                    HistoryDateRange.entries.forEach { range -> FilterChip(dateRange == range, { dateRange = range }, label = { Text(range.label) }) }
                }
                HistoryFilterRow {
                    FilterChip(sort == HistorySort.NEWEST, { sort = HistorySort.NEWEST }, label = { Text("วันที่ล่าสุด") })
                    FilterChip(sort == HistorySort.VALID_ELAPSED, { sort = HistorySort.VALID_ELAPSED }, label = { Text("เวลาที่มีข้อมูลครบ") })
                }
            }
            if (filtered.isEmpty()) item {
                Column(Modifier.padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (records.isEmpty()) "ยังไม่มีผลทดสอบ" else "ไม่พบผลที่ตรงกับตัวกรอง", style = MaterialTheme.typography.titleMedium)
                    Text(if (records.isEmpty()) "เริ่มการทดสอบเมื่อรถและการเชื่อมต่อพร้อม ผลที่บันทึกจริงจะแสดงที่นี่" else "ลองเปลี่ยนรถ การทดสอบ หรือช่วงวันที่", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (records.isEmpty()) Button(onClick = onStartPerformance) { Text("ไปที่ Performance") }
                    else TextButton(onClick = { query = ""; vehicleId = null; testType = null; dateRange = HistoryDateRange.ALL }) { Text("ล้างตัวกรอง") }
                }
            }
            items(filtered, key = { it.id }) { record ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(vehicleNames[record.vehicleProfileId] ?: "รถเดิม / ไม่ระบุ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HistoryCard(record, onOpen = { selectedId = record.id; comparisonId = null },
                        isPersonalBest = isPersonalBest(record, records),
                        onShare = { scope.launch { sharePerformanceReport(context, record) } }, onDelete = { deleting = record })
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun HistoryFilterRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

enum class HistoryDateRange(val label: String, val days: Int?) { ALL("ทุกช่วงเวลา", null), WEEK("7 วันที่ผ่านมา", 7), MONTH("30 วันที่ผ่านมา", 30) }

/** Filters only recorded fields. Legacy unknown dates are retained by the unrestricted date filter. */
fun filterHistoryPresentation(records: List<TimeSlipRecord>, query: String = "", vehicleId: String? = null,
    testType: String? = null, dateRange: HistoryDateRange = HistoryDateRange.ALL,
    sort: HistorySort = HistorySort.NEWEST, nowMillis: Long): List<TimeSlipRecord> =
    performanceHistory(records, query = query, sort = sort).filter { record ->
        val earliest = dateRange.days?.let { nowMillis - it * 86_400_000L }
        (vehicleId == null || record.vehicleProfileId.orEmpty() == vehicleId) &&
            (testType == null || performanceTitle(record) == testType) &&
            (earliest == null || record.startedAtEpochMillis in earliest..nowMillis)
    }
