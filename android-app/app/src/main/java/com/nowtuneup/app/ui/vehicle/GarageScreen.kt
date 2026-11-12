package com.nowtuneup.app.ui.vehicle

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nowtuneup.app.data.obd.session.AdapterHealthState
import com.nowtuneup.app.domain.model.*
import com.nowtuneup.app.presentation.dashboard.MainViewModel
import com.nowtuneup.app.ui.components.NtuPanel
import com.nowtuneup.app.ui.components.NtuScreenHeader
import com.nowtuneup.app.ui.connection.ConnectionHealthPanel

@Composable
fun GarageScreen(
    viewModel: MainViewModel,
    onManageVehicles: () -> Unit,
    onConnection: () -> Unit,
    onDiagnostics: () -> Unit,
    onLiveData: () -> Unit,
) {
    val repository = rememberVehicleProfileRepository()
    val profiles by repository.profiles.collectAsState()
    val connection by viewModel.connectionUiState.collectAsState()
    val health by viewModel.adapterHealth.collectAsState()
    GarageContent(profiles.firstOrNull { it.isActive }, profiles.size, connection, health,
        onManageVehicles, onConnection, onDiagnostics, onLiveData)
}

@Composable
fun GarageContent(
    vehicle: VehicleProfileRecord?,
    vehicleCount: Int,
    connection: ConnectionUiState,
    health: AdapterHealthState,
    onManageVehicles: () -> Unit,
    onConnection: () -> Unit,
    onDiagnostics: () -> Unit,
    onLiveData: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { NtuScreenHeader("Garage · รถของฉัน", "รถ การเชื่อมต่อ และผลตรวจที่เก็บในอุปกรณ์", eyebrow = "VEHICLE WORKSPACE") }
        item {
            NtuPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("รถที่ใช้งานอยู่", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(vehicle?.displayName ?: "ยังไม่ได้เลือกรถ", style = MaterialTheme.typography.headlineSmall)
                    vehicle?.let {
                        val detail = listOf(it.brand, it.model, it.year).filter(String::isNotBlank).joinToString(" · ")
                        if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodyMedium)
                        if (it.vin.isNotBlank()) Text("VIN ${it.vin}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("โปรไฟล์รถในอุปกรณ์ $vehicleCount คัน", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = onManageVehicles, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.DirectionsCar, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("จัดการโปรไฟล์รถ")
                    }
                }
            }
        }
        item { GarageAction("การเชื่อมต่อ", "${connection.transportType.garageLabel()} · ${connection.phase.garageLabel()}", Icons.Default.Bluetooth, onConnection) }
        item { GarageAction("ตรวจสุขภาพรถ", "อ่าน DTC, Readiness และข้อมูลอะแดปเตอร์", Icons.Default.MonitorHeart, onDiagnostics) }
        item { GarageAction("ข้อมูลสดและการบันทึก", "ดูค่าที่รองรับ ตรวจจังหวะข้อมูล และส่งออก CSV", Icons.Default.QueryStats, onLiveData) }
        item { ConnectionHealthPanel(health, connection.phase == ConnectionPhase.CONNECTED) }
        item { Text("โปรไฟล์รถและผลตรวจเป็นข้อมูลในอุปกรณ์ ไม่มีบัญชีหรือ cloud ที่จำเป็นต่อการอ่านรถ",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun GarageAction(title: String, detail: String, icon: ImageVector, onClick: () -> Unit) {
    NtuPanel(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun ObdTransportType.garageLabel() = when(this) {
    ObdTransportType.USB -> "USB OTG"; ObdTransportType.BLUETOOTH_CLASSIC -> "Bluetooth Classic"
    ObdTransportType.BLE_EXPERIMENTAL -> "BLE ทดลอง"; ObdTransportType.MOCK -> "จำลอง · ไม่ใช่ข้อมูลรถ"
}
private fun ConnectionPhase.garageLabel() = when(this) {
    ConnectionPhase.CONNECTED -> "เชื่อมต่อแล้ว"; ConnectionPhase.DISCONNECTED -> "ยังไม่เชื่อมต่อ"
    ConnectionPhase.RECONNECTING -> "เชื่อมต่อใหม่"; ConnectionPhase.CONNECTION_FAILED -> "เชื่อมต่อไม่สำเร็จ"
    ConnectionPhase.BLUETOOTH_DISABLED -> "Bluetooth ปิดอยู่"; ConnectionPhase.BLUETOOTH_UNAVAILABLE -> "ไม่มี Bluetooth"
    ConnectionPhase.PERMISSION_REQUIRED -> "ต้องอนุญาตสิทธิ์"; ConnectionPhase.DEVICE_SELECTION -> "เลือกอะแดปเตอร์"
    ConnectionPhase.UNSUPPORTED_ADAPTER -> "ไม่รองรับ"; ConnectionPhase.SCANNING -> "กำลังค้นหา"
    ConnectionPhase.CONNECTING -> "กำลังเชื่อมต่อ"; ConnectionPhase.INITIALIZING_ADAPTER -> "กำลังเริ่มต้น ECU"
}
