package com.nowtuneup.app.feature.timeslip

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nowtuneup.app.ui.components.NtuPanel
import kotlin.math.abs
import java.util.Locale

private enum class ChartMetric(val title: String, val unit: String) {
    SPEED("ความเร็ว", "km/h"), ACCELERATION("อัตราเร่ง", "g"), DISTANCE("ระยะทาง", "m");
    fun value(point: RunChartPoint): Double? = when (this) {
        SPEED -> point.speedKmh
        ACCELERATION -> point.accelerationG
        DISTANCE -> point.distanceMeters
    }
}

@Composable
fun TimeSlipCharts(record: TimeSlipRecord, comparison: TimeSlipRecord? = null) {
    val data = remember(record) { TimeSlipAnalysis.chart(record) }
    val previousData = remember(comparison) { comparison?.let(TimeSlipAnalysis::chart) }
    var metric by remember(record.id) { mutableStateOf(ChartMetric.SPEED) }
    var compare by remember(record.id, comparison?.id) { mutableStateOf(false) }
    var cursor by remember(record.id) { mutableFloatStateOf(0f) }
    val previous = if (compare) previousData?.points.orEmpty() else emptyList()
    val points = data.points
    NtuPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("วิเคราะห์การทดสอบ", style = MaterialTheme.typography.titleMedium)
            if (points.isEmpty()) {
                Text(data.unavailableReason.orEmpty(), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChartMetric.entries.forEach { option ->
                        FilterChip(selected = metric == option, onClick = { metric = option }, label = { Text(option.title) })
                    }
                }
                val maximumTime = maxOf(points.last().seconds, previous.lastOrNull()?.seconds ?: 0.0).coerceAtLeast(0.001)
                val selected = points.minByOrNull { abs(it.seconds - cursor * maximumTime) }!!
                Text("${decimal(selected.seconds)} s  ·  ${metric.value(selected)?.let(::decimal) ?: "—"} ${metric.unit}",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                RunPlot(points, previous, metric, maximumTime, selected.seconds,
                    record.speedMilestones.map { it.elapsedMillis / 1000.0 } + record.distanceSplits.map { it.elapsedMillis / 1000.0 })
                Slider(value = cursor, onValueChange = { cursor = it }, modifier = Modifier.fillMaxWidth().semantics {
                    contentDescription = "เลือกเวลาในกราฟ ${metric.title}"
                })
                Text("เลื่อนเพื่ออ่านค่า • แกนนอน: เวลาหลังเริ่มจับ (s)", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!previousData?.points.isNullOrEmpty()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("เทียบครั้งก่อน", style = MaterialTheme.typography.labelLarge)
                            Text("รถและรูปแบบการทดสอบเดียวกัน", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = compare, onCheckedChange = { compare = it }, modifier = Modifier.semantics { contentDescription = "เทียบกราฟกับครั้งก่อน" })
                    }
                    if (compare && comparison != null) {
                        Text("เส้นทึบ: ครั้งนี้  ·  เส้นประ: ครั้งก่อน", style = MaterialTheme.typography.bodySmall)
                        val delta = record.elapsedMillis - comparison.elapsedMillis
                        Text(when {
                            delta < 0 -> "เร็วขึ้น ${formatSeconds(-delta)} s"
                            delta > 0 -> "ช้าลง ${formatSeconds(delta)} s"
                            else -> "เวลาเท่ากัน"
                        }, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
                Text(when (metric) {
                    ChartMetric.SPEED -> "ความเร็วจาก OBD-II • จุดเริ่มและจุดจบคำนวณระหว่างตัวอย่าง"
                    ChartMetric.ACCELERATION -> "อัตราเร่งโดยประมาณจากการเปลี่ยนความเร็ว OBD ไม่ใช่ค่า G จากเซ็นเซอร์"
                    ChartMetric.DISTANCE -> "ระยะทางประมาณจากความเร็ว OBD นับจากจุดออกตัว รวมระยะ rollout หากเปิดใช้"
                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (points.any { it.breakBefore } || points.last().seconds + 0.002 < record.elapsedMillis / 1000.0) {
                    Text("ข้อมูลบางช่วงขาดหาย กราฟแสดงเฉพาะช่วงที่บันทึกได้", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun RunPlot(
    points: List<RunChartPoint>, previous: List<RunChartPoint>, metric: ChartMetric,
    maximumTime: Double, selectedTime: Double, milestones: List<Double>,
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val values = (points + previous).mapNotNull(metric::value)
    val minimum = minOf(0.0, values.minOrNull() ?: 0.0)
    val maximum = maxOf(if (metric == ChartMetric.ACCELERATION) 0.1 else 1.0, values.maxOrNull() ?: 0.0)
    val range = (maximum - minimum).coerceAtLeast(0.1)
    Canvas(Modifier.fillMaxWidth().height(218.dp).semantics {
        contentDescription = "กราฟ${metric.title} หน่วย ${metric.unit} จาก 0 ถึง ${decimal(maximumTime)} วินาที ใช้แถบเลื่อนด้านล่างเพื่ออ่านค่า"
    }) {
        val left = 48.dp.toPx()
        val top = 20.dp.toPx()
        val bottom = size.height - 28.dp.toPx()
        val right = size.width - 10.dp.toPx()
        val plotWidth = (right - left).coerceAtLeast(1f)
        fun x(seconds: Double) = left + (seconds / maximumTime).toFloat() * plotWidth
        fun y(value: Double) = bottom - ((value - minimum) / range).toFloat() * (bottom - top)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = label.toArgb(); textSize = 10.sp.toPx() }
        drawContext.canvas.nativeCanvas.drawText(metric.unit, 0f, 11.sp.toPx(), paint)
        for (i in 0..4) {
            val value = minimum + range * i / 4
            drawLine(grid, Offset(left, y(value)), Offset(right, y(value)), 1.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText(String.format(Locale.US, if (metric == ChartMetric.ACCELERATION) "%.2f" else "%.0f", value), 0f, y(value) + 3.dp.toPx(), paint)
        }
        for (i in 0..4) {
            val time = maximumTime * i / 4
            paint.textAlign = when (i) { 0 -> Paint.Align.LEFT; 4 -> Paint.Align.RIGHT; else -> Paint.Align.CENTER }
            drawContext.canvas.nativeCanvas.drawText(String.format(Locale.US, "%.1f", time), x(time), size.height - 3.dp.toPx(), paint)
        }
        milestones.distinct().filter { it in 0.0..maximumTime }.forEach { time ->
            drawLine(grid, Offset(x(time), top), Offset(x(time), bottom), 1.dp.toPx(),
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 5.dp.toPx())))
        }
        fun series(data: List<RunChartPoint>, comparison: Boolean) {
            val path = Path()
            var connected = false
            data.forEach { point ->
                val value = metric.value(point)
                if (value == null) connected = false
                else {
                    if (!connected || point.breakBefore) path.moveTo(x(point.seconds), y(value)) else path.lineTo(x(point.seconds), y(value))
                    connected = true
                }
            }
            drawPath(path, if (comparison) secondary else primary, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round,
                pathEffect = if (comparison) androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx())) else null))
        }
        series(previous, true)
        series(points, false)
        drawLine(label.copy(alpha = 0.5f), Offset(x(selectedTime), top), Offset(x(selectedTime), bottom), 1.dp.toPx())
        points.minByOrNull { abs(it.seconds - selectedTime) }?.let { point ->
            metric.value(point)?.let { drawCircle(primary, 4.dp.toPx(), Offset(x(point.seconds), y(it))) }
        }
    }
}

private fun decimal(value: Double): String = String.format(Locale.US, "%.2f", value)
