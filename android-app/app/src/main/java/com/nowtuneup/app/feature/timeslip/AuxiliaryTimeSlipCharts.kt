package com.nowtuneup.app.feature.timeslip

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.abs

@Composable
fun AuxiliaryTimeSlipCharts(record: TimeSlipRecord, comparison: TimeSlipRecord?) {
    val series = remember(record) { listOf(0x0C, 0x11).associateWith { auxiliaryRunSeries(record, it) }.filterValues { it.isNotEmpty() } }
    if (series.isEmpty()) return
    var selectedPid by remember(record.id) { mutableIntStateOf(series.keys.first()) }
    var cursor by remember(record.id) { mutableFloatStateOf(0f) }
    val pid = selectedPid.takeIf { it in series } ?: series.keys.first()
    val points = series.getValue(pid)
    val previous = remember(record, comparison, pid) { comparison?.takeIf { TimeSlipAnalysis.comparable(record, it) }?.let { auxiliaryRunSeries(it, pid) }.orEmpty() }
    val maximumTime = maxOf(points.last().seconds, previous.lastOrNull()?.seconds ?: 0.0).coerceAtLeast(0.001)
    val maximum = (points + previous).maxOf { it.value }.coerceAtLeast(1.0)
    val current = points.minBy { abs(it.seconds - cursor * maximumTime) }
    val unit = if (pid == 0x0C) "rpm" else "%"
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Recorded auxiliary signals", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            series.keys.forEach { signal -> FilterChip(selected = signal == pid, onClick = { selectedPid = signal }, label = { Text(if (signal == 0x0C) "RPM" else "Throttle") }) }
        }
        Text(String.format(Locale.US, "Measured %.3f s · %.1f %s", current.seconds, current.value, unit))
        Text(String.format(Locale.US, "Y: 0–%.1f %s · X: 0–%.3f s", maximum, unit, maximumTime), style = MaterialTheme.typography.bodySmall)
        val primary = MaterialTheme.colorScheme.primary
        val secondary = MaterialTheme.colorScheme.secondary
        Canvas(Modifier.fillMaxWidth().height(160.dp).semantics {
            contentDescription = "Recorded $unit at original source timestamps; gaps break the curve"
        }) {
            fun drawSeries(data: List<AuxiliaryRunPoint>, dashed: Boolean) {
                val path = Path()
                data.forEachIndexed { index, point ->
                    val x = (point.seconds/maximumTime*size.width).toFloat()
                    val y = (size.height*(1-point.value/maximum)).toFloat()
                    if (index == 0 || point.breakBefore) path.moveTo(x,y) else path.lineTo(x,y)
                    drawCircle(if (dashed) secondary else primary, 2.dp.toPx(), Offset(x,y))
                }
                drawPath(path, if (dashed) secondary else primary, style = Stroke(2.dp.toPx(),
                    pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(),5.dp.toPx())) else null))
            }
            drawSeries(previous, true); drawSeries(points, false)
            val x = (current.seconds/maximumTime*size.width).toFloat()
            drawLine(primary.copy(alpha=0.5f),Offset(x,0f),Offset(x,size.height),1.dp.toPx())
        }
        Slider(value=cursor, onValueChange={cursor=it}, modifier=Modifier.semantics { contentDescription="Inspect recorded $unit sample" })
        Text("Actual OBD source observations; no interpolated RPM/throttle or reconstructed legacy values.", style=MaterialTheme.typography.bodySmall)
        if (previous.isNotEmpty()) Text("Solid ${record.id.take(8)} · dashed ${comparison?.id?.take(8)}", style=MaterialTheme.typography.bodySmall)
    }
}
