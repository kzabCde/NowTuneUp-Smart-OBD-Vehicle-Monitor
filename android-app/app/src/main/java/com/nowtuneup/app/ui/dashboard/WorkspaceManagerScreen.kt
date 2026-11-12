package com.nowtuneup.app.ui.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nowtuneup.app.data.dashboard.DashboardDefaults
import com.nowtuneup.app.domain.model.DashboardConfig
import com.nowtuneup.app.presentation.dashboard.MainViewModel
import com.nowtuneup.app.ui.components.NtuPanel
import com.nowtuneup.app.ui.components.NtuScreenHeader

/** Workspace actions change dashboard rows only, never vehicle or telemetry history. */
@Composable
fun WorkspaceManagerScreen(
    viewModel: MainViewModel,
    onCreate: () -> Unit,
    onEdit: (DashboardConfig) -> Unit,
    onClose: (() -> Unit)? = null,
) {
    val dashboards by viewModel.dashboards.collectAsState()
    val preferences by viewModel.dashboardPreferences.collectAsState()
    val landingId by viewModel.dashboardLandingId.collectAsState()
    val issues by viewModel.dashboardStorageIssues.collectAsState()
    var renaming by remember { mutableStateOf<DashboardConfig?>(null) }
    var deleting by remember { mutableStateOf<DashboardConfig?>(null) }
    var deleteFailed by remember { mutableStateOf(false) }
    var resetting by remember { mutableStateOf<DashboardConfig?>(null) }
    val locked = preferences.drivingMode
    if (onClose != null) BackHandler(onBack = onClose)

    renaming?.let { dashboard ->
        var name by remember(dashboard.id) { mutableStateOf(dashboard.name) }
        AlertDialog(
            onDismissRequest = { renaming = null }, title = { Text("เปลี่ยนชื่อหน้าปัด") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it.take(60) },
                label = { Text("ชื่อหน้าปัด") }, singleLine = true) },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {
                viewModel.renameDashboard(dashboard.id, name); renaming = null
            }) { Text("บันทึก") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("ยกเลิก") } },
        )
    }
    deleting?.let { dashboard ->
        AlertDialog(
            onDismissRequest = { deleting = null }, title = { Text("ลบ ${dashboard.name}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ลบเฉพาะหน้าปัดนี้และ Widget ภายใน รถ ข้อมูลบันทึก การเดินทาง และประวัติการทดสอบจะยังอยู่ แม้ลบหน้าปัดสุดท้าย")
                    if (deleteFailed) Text(
                        "ลบไม่สำเร็จ กรุณาปิดโหมดขับรถหรือลองอีกครั้ง",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = { TextButton(onClick = {
                viewModel.deleteDashboard(dashboard.id) { success ->
                    if (success) { deleting = null; deleteFailed = false }
                    else deleteFailed = true
                }
            }) { Text("ลบหน้าปัด", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("เก็บหน้าปัดไว้") } },
        )
    }
    resetting?.let { dashboard ->
        var templateIndex by remember(dashboard.id) { mutableIntStateOf(0) }
        val templates = remember { DashboardDefaults.presets() }
        AlertDialog(
            onDismissRequest = { resetting = null }, title = { Text("รีเซ็ต ${dashboard.name}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("แทนที่เลย์เอาต์ทั้งสองแนวด้วยแม่แบบ รวมถึงข้อมูลเดิมที่อ่านไม่ได้ ข้อมูลประเภทอื่นจะยังอยู่")
                    templates.forEachIndexed { index, template ->
                        FilterChip(selected = templateIndex == index, onClick = { templateIndex = index }, label = { Text(template.name) })
                    }
                }
            },
            confirmButton = { TextButton(enabled = templates.isNotEmpty(), onClick = {
                templates.getOrNull(templateIndex)?.let { viewModel.resetDashboardToTemplate(dashboard.id, it) }
                resetting = null
            }) { Text("รีเซ็ตเลย์เอาต์") } },
            dismissButton = { TextButton(onClick = { resetting = null }) { Text("ยกเลิก") } },
        )
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                NtuScreenHeader("หน้าปัด", "จัดลำดับและเลือกหน้าปัดที่เปิดก่อนในหน้าหลัก",
                    modifier = Modifier.weight(1f), eyebrow = "WORKSPACE")
                if (onClose != null) TextButton(onClick = onClose) { Text("เสร็จแล้ว") }
            }
            if (locked) Text("โหมดขับรถเปิดอยู่ จอดรถให้ปลอดภัยและปิดโหมดก่อนแก้ไขหน้าปัด", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(enabled = !locked, onClick = onCreate, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("สร้างหน้าปัด") }
        }
        if (dashboards.isEmpty()) item {
            NtuPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("พื้นที่หน้าปัดของคุณ", style = MaterialTheme.typography.titleMedium)
                    Text("ยังไม่มีหน้าปัด สร้างจากแม่แบบหรือเลือกเกจเองได้ ข้อมูลรถและผลทดสอบของคุณยังอยู่")
                }
            }
        }
        itemsIndexed(dashboards, key = { _, dashboard -> dashboard.id }) { index, dashboard ->
            var menu by remember(dashboard.id) { mutableStateOf(false) }
            val issue = issues.firstOrNull { it.id == dashboard.id }
            val unreadable = issue?.unreadable == true
            NtuPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(dashboard.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("${dashboard.portrait.widgets.size} Widget แนวตั้ง · ${dashboard.landscape.widgets.size} แนวนอน",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(enabled = !locked, onClick = {
                            deleteFailed = false
                            deleting = dashboard
                        }) {
                            Text("ลบ", color = if (locked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                        }
                        Box {
                            IconButton(enabled = !locked, onClick = { menu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "จัดการ ${dashboard.name}")
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("เปลี่ยนชื่อ") }, enabled = !unreadable, onClick = { menu = false; renaming = dashboard })
                                DropdownMenuItem(text = { Text("ทำสำเนา") }, enabled = !unreadable, onClick = { menu = false; viewModel.duplicateDashboard(dashboard) })
                                DropdownMenuItem(text = { Text("เปิดก่อนในหน้าหลัก") }, onClick = { menu = false; viewModel.setLandingDashboard(dashboard.id) })
                                DropdownMenuItem(text = { Text("เลื่อนขึ้น") }, enabled = index > 0, onClick = { menu = false; viewModel.moveDashboard(dashboard.id, -1) })
                                DropdownMenuItem(text = { Text("เลื่อนลง") }, enabled = index < dashboards.lastIndex, onClick = { menu = false; viewModel.moveDashboard(dashboard.id, 1) })
                                DropdownMenuItem(text = { Text("รีเซ็ตจากแม่แบบ") }, onClick = { menu = false; resetting = dashboard })
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text("ลบหน้าปัด", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; deleteFailed = false; deleting = dashboard })
                            }
                        }
                    }
                    if (landingId == dashboard.id) Text("หน้าปัดเริ่มต้น", color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium)
                    issue?.let { Text(it.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = !locked && !unreadable, onClick = { onEdit(dashboard) }, modifier = Modifier.weight(1f)) { Text("แก้ไข Widget") }
                        TextButton(onClick = { viewModel.selectDashboard(dashboard.id); onClose?.invoke() }, modifier = Modifier.weight(1f)) { Text("เปิด") }
                    }
                }
            }
        }
    }
}
