package com.nowtuneup.app.feature.live

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

/** Each signal has its own labelled axis; values with different units never share an amplitude scale. */
@Composable
fun TelemetryPanel(repository: LiveSessionRepository, live: List<TelemetrySample>, connected: Boolean, source: String) {
    val recording by repository.recording.collectAsState()
    val history by repository.history.collectAsState()
    val storageError by repository.storageError.collectAsState()
    var paused by remember { mutableStateOf<List<TelemetrySample>?>(null) }
    var opened by remember { mutableStateOf<TelemetryRecording?>(null) }
    var selected by remember { mutableStateOf(setOf(0x0D, 0x0C)) }
    var cursor by remember { mutableFloatStateOf(1f) }
    var pendingDelete by remember { mutableStateOf<LiveSessionSummary?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf<TelemetryRecording?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val data = exporting
        if (uri != null && data != null) scope.launch {
            message = runCatching { withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(telemetryCsv(data)) }
                    ?: error("Unable to open CSV destination")
            }; "CSV exported" }.getOrElse { "Export failed: ${it.message}" }
        }
    }
    pendingDelete?.let { summary ->
        AlertDialog(onDismissRequest = { pendingDelete = null }, title = { Text("Delete recording?") },
            text = { Text("This removes the local recording and its samples.") },
            confirmButton = { TextButton(onClick = { repository.delete(summary); opened = null; pendingDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Keep") } })
    }
    val samples = opened?.samples ?: paused ?: live
    val available = remember(samples) { samples.distinctBy { it.pid } }
    val firstTime = samples.minOfOrNull { it.sourceTimeNanos } ?: 0L
    val endTime = samples.maxOfOrNull { it.sourceTimeNanos } ?: firstTime
    val span = (endTime - firstTime).coerceAtLeast(1L)
    val inspectTime = firstTime + (span * cursor).toLong()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Telemetry", style = MaterialTheme.typography.titleLarge)
        val displayedSource = opened?.dataSource ?: source
        Text(if (displayedSource.contains("MOCK")) "SIMULATED SOURCE — not vehicle measurements" else "Source: $displayedSource · observed OBD responses", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = connected || recording.active, onClick = {
                if (recording.active) repository.stop() else repository.start(source = source)
            }) { Text(if (recording.active) "Stop & save" else "Record") }
            OutlinedButton(enabled = opened == null, onClick = { paused = if (paused == null) live.toList() else null }) { Text(if (paused == null) "Pause inspection" else "Resume live") }
            if (opened != null) OutlinedButton(onClick = { opened = null }) { Text("Back to live") }
        }
        Text(if (recording.active) "Recording ${recording.readingFrames} source samples · limit 20,000 / 1 h" else "Local recordings · newest 20 retained", style = MaterialTheme.typography.bodySmall)
        storageError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            available.forEach { signal -> FilterChip(selected = signal.pid in selected, onClick = {
                selected = if (signal.pid in selected) selected - signal.pid else (selected + signal.pid).takeLastSet(4)
            }, label = { Text("${signal.name} · ${signal.unit}") }) }
        }
        if (samples.isEmpty()) Text("No source samples yet. Connect a supported adapter; unavailable values are never zero.")
        available.filter { it.pid in selected }.forEach { signal ->
            val series = remember(samples, signal.pid) { samples.filter { it.pid == signal.pid } }
            val valid = series.filter { it.quality == SampleQuality.VALID && it.value != null }
            val nearest = series.minByOrNull { abs(it.sourceTimeNanos - inspectTime) }
                ?.takeIf { abs(it.sourceTimeNanos - inspectTime) <= 1_500_000_000L && it.quality == SampleQuality.VALID }
            val seconds = (valid.lastOrNull()?.sourceTimeNanos ?: 0L) - (valid.firstOrNull()?.sourceTimeNanos ?: 0L)
            val hz = if (valid.size > 1 && seconds > 0) (valid.size - 1) * 1e9 / seconds else null
            Text("${signal.name}: ${nearest?.value?.let { String.format(Locale.US, "%.2f", it) } ?: "— unavailable"} ${signal.unit}")
            Text("Min ${valid.minOfOrNull { it.value!! }} · Max ${valid.maxOfOrNull { it.value!! }} · ${hz?.let { String.format(Locale.US, "%.1f Hz observed", it) } ?: "Frequency unavailable"}", style = MaterialTheme.typography.bodySmall)
            nearest?.let { Text(String.format(Locale.US, "Measured at %.3f s", (it.sourceTimeNanos-firstTime)/1e9), style = MaterialTheme.typography.bodySmall) }
            SignalPlot(series, firstTime, span, inspectTime, signal.unit)
        }
        if (samples.isNotEmpty()) {
            Slider(value = cursor, onValueChange = { cursor = it; if (paused == null && opened == null) paused = live.toList() },
                modifier = Modifier.semantics { contentDescription = "Inspect measured telemetry sample time" })
            Text(String.format(Locale.US, "Cursor %.3f s · time axis 0–%.3f s · gaps >1.5 s break the curve", (inspectTime-firstTime)/1e9, span/1e9), style = MaterialTheme.typography.bodySmall)
        }
        Text("Recording history", style = MaterialTheme.typography.titleMedium)
        if (history.isEmpty()) Text("No saved recordings")
        history.forEach { summary ->
            HorizontalDivider()
            Text("${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(summary.startedAtMillis))} · ${summary.durationMillis / 1000}s · ${summary.metrics.size} signals")
            summary.stopReason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (summary.recordingId != null) {
                    TextButton(onClick = { scope.launch { opened = repository.loadRecording(summary.recordingId); if(opened == null) message = "Recording file unavailable" } }) { Text("Open") }
                    TextButton(onClick = { scope.launch {
                        exporting = repository.loadRecording(summary.recordingId)
                        if (exporting != null) export.launch("NowTuneUp-${summary.recordingId}.csv") else message = "Recording file unavailable"
                    } }) { Text("Export CSV") }
                } else Text("Legacy summary · no raw samples", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { pendingDelete = summary }) { Text("Delete") }
            }
        }
    }
}

private fun <T> Set<T>.takeLastSet(count: Int) = toList().takeLast(count).toSet()

@Composable
private fun SignalPlot(samples: List<TelemetrySample>, first: Long, span: Long, cursor: Long, unit: String) {
    val values = samples.mapNotNull { it.value }
    val minimum = values.minOrNull() ?: 0.0
    val maximum = (values.maxOrNull() ?: 1.0).coerceAtLeast(minimum + 0.1)
    val color = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    Text(String.format(Locale.US, "Y: %.1f–%.1f %s", minimum, maximum, unit), style = MaterialTheme.typography.labelSmall)
    Canvas(Modifier.fillMaxWidth().height(120.dp).semantics { contentDescription = "Measured $unit versus source elapsed time; invalid samples and missing intervals break the curve" }) {
        val path = Path()
        var previous: TelemetrySample? = null
        samples.forEach { sample ->
            val value = sample.value
            if (value == null || sample.quality != SampleQuality.VALID) previous = null
            else {
                val x = ((sample.sourceTimeNanos - first).toDouble() / span * size.width).toFloat()
                val y = (size.height * (1 - (value - minimum) / (maximum - minimum))).toFloat()
                if (previous == null || sample.sourceTimeNanos - previous!!.sourceTimeNanos > 1_500_000_000L) path.moveTo(x,y) else path.lineTo(x,y)
                drawCircle(color, 1.5.dp.toPx(), Offset(x,y))
                previous = sample
            }
        }
        drawPath(path, color, style = Stroke(2.dp.toPx()))
        val x = ((cursor - first).toDouble()/span*size.width).toFloat()
        drawLine(grid, Offset(x,0f), Offset(x,size.height), 1.dp.toPx())
    }
}
