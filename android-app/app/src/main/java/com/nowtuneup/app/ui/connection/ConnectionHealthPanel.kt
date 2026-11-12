package com.nowtuneup.app.ui.connection

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nowtuneup.app.data.obd.session.AdapterHealthState
import com.nowtuneup.app.ui.components.NtuPanel
import java.util.Locale

/** Only measurements that have actually been observed are presented as connection facts. */
data class ConnectionHealthFact(val label: String, val value: String)

fun connectionHealthFacts(health: AdapterHealthState): List<ConnectionHealthFact> {
    if (health.totalCommands <= 0 || health.updatedAtMillis <= 0) return emptyList()
    fun decimal(value: Double, suffix: String) = if (value.isFinite() && value >= 0) String.format(Locale.US, "%.1f %s", value, suffix) else "—"
    return listOf(
        ConnectionHealthFact("Latency เฉลี่ย", if (health.averageLatencyMillis >= 0) "${health.averageLatencyMillis} ms" else "—"),
        ConnectionHealthFact("คำสั่งสำเร็จ", if (health.successRate in 0.0..1.0) decimal(health.successRate * 100, "%") else "—"),
        ConnectionHealthFact("ความถี่คำสั่งที่วัดได้", decimal(health.commandsPerSecond, "คำสั่ง/s")),
        ConnectionHealthFact("คำสั่งที่ส่ง", health.totalCommands.toString()),
        ConnectionHealthFact("คำสั่งล้มเหลว", if (health.failedCommands >= 0) health.failedCommands.toString() else "—"),
        ConnectionHealthFact("การกู้การเชื่อมต่อ", if (health.softRecoveries >= 0) health.softRecoveries.toString() else "—"),
    )
}

@Composable
fun ConnectionHealthPanel(health: AdapterHealthState, connected: Boolean, modifier: Modifier = Modifier) {
    val facts = connectionHealthFacts(health)
    NtuPanel(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("คุณภาพการเชื่อมต่อ", style = MaterialTheme.typography.titleMedium)
            if (facts.isEmpty()) {
                Text("ยังไม่มีข้อมูลคำสั่งจากอะแดปเตอร์", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Latency และอัตราสำเร็จจะแสดงหลังมีการตอบกลับจริง", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(if (connected) "ข้อมูลจาก session ปัจจุบัน" else "ข้อมูลที่วัดได้ล่าสุด · ขณะนี้ไม่ได้เชื่อมต่อ",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                facts.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { fact ->
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(fact.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(fact.value, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
                Text("ความถี่คำสั่งไม่ใช่ความถี่ของทุก PID · ขึ้นกับรถ อะแดปเตอร์ และรายการที่อ่าน",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
