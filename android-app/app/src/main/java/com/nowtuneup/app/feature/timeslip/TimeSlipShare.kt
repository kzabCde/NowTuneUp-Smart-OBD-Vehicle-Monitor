package com.nowtuneup.app.feature.timeslip

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Local reports contain only stored run facts and preserve the OBD estimate/quality caveats. */
object TimeSlipShare {
    fun shareText(context: Context, value: String, mimeType: String = "text/plain") {
        launchChooser(context, Intent(Intent.ACTION_SEND).apply { type = mimeType; putExtra(Intent.EXTRA_TEXT, value) })
    }

    suspend fun shareReport(context: Context, record: TimeSlipRecord) {
        val file = withContext(Dispatchers.IO) { createReport(context, record) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, reportText(record))
            clipData = ClipData.newUri(context.contentResolver, "NowTuneUp performance report", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        withContext(Dispatchers.Main) { launchChooser(context, send) }
    }

    fun reportText(record: TimeSlipRecord): String = buildString {
        appendLine("NOWTUNEUP · PERFORMANCE REPORT")
        appendLine(performanceTitle(record))
        appendLine("Elapsed: ${if (record.hasMeasuredElapsed()) "${formatSeconds(record.elapsedMillis)} s" else "Unavailable"}")
        performanceReportFacts(record).forEach { (label, value) -> appendLine("$label: $value") }
        appendLine("Distance and acceleration are estimates from OBD speed, not GPS-grade timing.")
    }

    private fun launchChooser(context: Context, send: Intent) {
        val chooser = Intent.createChooser(send, "แชร์รายงาน NowTuneUp")
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /** Called on IO; this does not alter the run or ask a server to generate a report. */
    internal fun createReport(context: Context, record: TimeSlipRecord): File {
        val facts = performanceReportFacts(record)
        val points = TimeSlipAnalysis.chart(record).points
        val height = 670 + facts.size * 54 + if (points.isNotEmpty()) 270 else 0
        val bitmap = Bitmap.createBitmap(800, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.rgb(15, 17, 20))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            fun text(value: String, x: Float, y: Float, size: Float, color: Int = Color.WHITE, mono: Boolean = false) {
                paint.color = color; paint.textSize = size
                paint.typeface = if (mono) Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL) else Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                canvas.drawText(value, x, y, paint)
            }
            val secondary = Color.rgb(166, 173, 184)
            val accent = Color.rgb(79, 142, 247)
            paint.color = accent; canvas.drawRect(40f, 40f, 80f, 44f, paint)
            text("NOWTUNEUP", 96f, 51f, 24f)
            text("PERFORMANCE REPORT", 40f, 110f, 20f, secondary)
            text(performanceTitle(record).take(40), 40f, 158f, 32f)
            text(if (record.hasMeasuredElapsed()) "${formatSeconds(record.elapsedMillis)} s" else "Result unavailable", 40f, 250f, 64f, mono = true)
            text("OBD-II derived measurement", 40f, 295f, 21f, secondary)
            var y = 350f
            facts.forEach { (label, value) ->
                text(label, 40f, y, 20f, secondary)
                paint.textSize = 20f; paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                val fitted = value.take(paint.breakText(value, true, 470f, null))
                text(fitted, 290f, y, 20f, mono = true)
                paint.color = Color.rgb(45, 49, 55); canvas.drawLine(40f, y + 20, 760f, y + 20, paint)
                y += 54f
            }
            if (points.isNotEmpty()) {
                y += 20f
                text("Speed · km/h", 40f, y, 20f, secondary)
                val top = y + 25f; val bottom = top + 180f
                val maxTime = points.last().seconds.coerceAtLeast(0.001)
                val maxSpeed = points.maxOf { it.speedKmh }.coerceAtLeast(1.0)
                val path = Path()
                points.forEachIndexed { index, point ->
                    val x = 40f + (point.seconds / maxTime * 720).toFloat()
                    val py = bottom - (point.speedKmh / maxSpeed * 180).toFloat()
                    if (index == 0 || point.breakBefore) path.moveTo(x, py) else path.lineTo(x, py)
                }
                paint.color = accent; paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f
                canvas.drawPath(path, paint); paint.style = Paint.Style.FILL
                text(String.format(Locale.US, "0–%.3f s · gaps are not reconstructed", maxTime), 40f, bottom + 30f, 19f, secondary)
                y = bottom + 55f
            }
            text("Estimated distance and acceleration from OBD speed.", 40f, y + 35f, 19f, secondary)
            text("Not GPS-grade or Dragy-certified timing.", 40f, y + 65f, 19f, secondary)
            val directory = File(context.cacheDir, "time-slip").apply { check(exists() || mkdirs()) }
            directory.listFiles()?.filter { it.name.startsWith("report-") && it.extension == "png" }
                ?.sortedByDescending { it.lastModified() }?.drop(9)?.forEach { it.delete() }
            val safeId = record.id.filter { it.isLetterOrDigit() || it == '-' }.take(80).ifBlank { "legacy" }
            return File(directory, "report-$safeId.png").also { file -> file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        } finally { bitmap.recycle() }
    }
}

internal fun TimeSlipRecord.hasMeasuredElapsed(): Boolean = status == TimeSlipStatus.COMPLETED && elapsedMillis > 0L && measurementQuality != MeasurementQuality.INVALID

/** Presentation facts can be unit tested without bitmap rendering or changing a stored record. */
fun performanceReportFacts(record: TimeSlipRecord): List<Pair<String, String>> = buildList {
    add("Vehicle" to (record.vehicleProfileId?.takeIf(String::isNotBlank) ?: "Legacy / unassigned"))
    add("Recorded" to record.recordedDateLabel())
    add("Source" to record.dataSource)
    add("Status" to record.status.name)
    add("Quality" to "${record.measurementQuality.name} · ${record.qualityScore}/100")
    add("OBD sampling" to if (record.obdSampleRateHz > 0 && record.obdSampleRateHz.isFinite()) String.format(Locale.US, "%.1f Hz observed", record.obdSampleRateHz) else "Unavailable")
    add("Timing estimate" to record.timingEstimateLabel())
    add("Rollout" to if (record.oneFootRolloutEnabled) "1 foot" else "Off")
    record.speedMilestones.forEach { add(it.label to "${formatSeconds(it.elapsedMillis)} s") }
    record.distanceSplits.forEach { add(it.target.label to "${formatSeconds(it.elapsedMillis)} s") }
    record.validityNotes.take(4).forEach { add("Limitation" to it) }
}

internal fun TimeSlipRecord.timingEstimateLabel(): String = if (hasMeasuredElapsed() && estimatedTimingErrorMillis > 0)
    "±${estimatedTimingErrorMillis} ms" else "Unavailable"
