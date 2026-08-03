package com.nowtuneup.app.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.nowtuneup.app.feature.timeslip.domain.MeasurementQuality
import com.nowtuneup.app.feature.timeslip.domain.TimeSlipRecord
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeSlipExporter {
    fun createCsvFile(context: Context, record: TimeSlipRecord): File {
        val dir = File(context.cacheDir, "time-slip").apply { mkdirs() }
        val file = File(dir, "NTU-Time-Slip-${record.id.take(8)}.csv")
        file.bufferedWriter().use { writer ->
            writer.appendLine("NTU PERFORMANCE TIME SLIP")
            writer.appendLine("record_id,${record.id}")
            writer.appendLine("started_at,${isoDate(record.startedAtEpochMs)}")
            writer.appendLine("mode,${record.testMode.name}")
            writer.appendLine("quality,${record.measurementQuality.name}")
            writer.appendLine("estimated_timing_error_ms,${record.estimatedTimingErrorMs.orEmpty()}")
            writer.appendLine("obd_rate_hz,${record.obdSampleRateHz.orEmpty()}")
            writer.appendLine("gps_rate_hz,${record.gpsSampleRateHz.orEmpty()}")
            writer.appendLine("average_gps_accuracy_m,${record.averageGpsAccuracyMeters.orEmpty()}")
            writer.appendLine()
            writer.appendLine("speed_milestone,elapsed_ms,distance_at_target_m")
            record.speedMilestones.forEach { milestone ->
                writer.appendLine("${milestone.displayName},${milestone.elapsedMs},${"%.3f".format(Locale.US, milestone.distanceAtTargetMeters)}")
            }
            writer.appendLine()
            writer.appendLine("distance_target,elapsed_ms,split_ms,trap_speed_kmh")
            record.distanceSplits.forEach { split ->
                writer.appendLine("${split.target.displayName},${split.elapsedMs},${split.splitMs},${"%.3f".format(Locale.US, split.trapSpeedKmh)}")
            }
            writer.appendLine()
            writer.appendLine("elapsed_ms,obd_speed_kmh,gps_speed_kmh,fused_speed_kmh,distance_m,gps_accuracy_m,acceleration_ms2")
            record.samples.forEach { sample ->
                writer.appendLine(
                    listOf(
                        sample.elapsedMs,
                        sample.obdSpeedKmh.orEmpty(),
                        sample.gpsSpeedKmh.orEmpty(),
                        "%.3f".format(Locale.US, sample.fusedSpeedKmh),
                        "%.3f".format(Locale.US, sample.accumulatedDistanceMeters),
                        sample.gpsAccuracyMeters.orEmpty(),
                        sample.accelerationMs2.orEmpty(),
                    ).joinToString(","),
                )
            }
        }
        return file
    }

    fun createImageFile(context: Context, record: TimeSlipRecord): File {
        val width = 1080
        val rowCount = record.speedMilestones.size + record.distanceSplits.size
        val height = (1180 + rowCount * 86).coerceIn(1500, 2600)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(Color.rgb(8, 12, 18))

        fun text(value: String, x: Float, y: Float, size: Float, color: Int = Color.WHITE, bold: Boolean = false) {
            paint.color = color
            paint.textSize = size
            paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            canvas.drawText(value, x, y, paint)
        }

        paint.color = Color.rgb(0, 229, 255)
        canvas.drawRect(0f, 0f, width.toFloat(), 18f, paint)
        text("NTU", 64f, 105f, 64f, Color.rgb(0, 229, 255), true)
        text("PERFORMANCE TIME SLIP", 64f, 164f, 36f, Color.WHITE, true)
        text(record.testMode.name.replace('_', ' '), 64f, 220f, 28f, Color.LTGRAY)
        text(SimpleDateFormat("dd MMM yyyy  HH:mm:ss", Locale.getDefault()).format(Date(record.startedAtEpochMs)), 64f, 262f, 26f, Color.LTGRAY)

        var y = 345f
        text("SPEED MILESTONES", 64f, y, 28f, Color.rgb(255, 193, 7), true)
        y += 54f
        record.speedMilestones.forEach { milestone ->
            text(milestone.displayName, 76f, y, 31f)
            text(formatSeconds(milestone.elapsedMs), 760f, y, 31f, Color.rgb(0, 229, 255), true)
            y += 70f
        }
        if (record.speedMilestones.isEmpty()) {
            text("—", 76f, y, 31f, Color.GRAY)
            y += 70f
        }

        y += 22f
        text("DISTANCE SPLITS", 64f, y, 28f, Color.rgb(255, 193, 7), true)
        y += 54f
        record.distanceSplits.forEach { split ->
            text(split.target.displayName, 76f, y, 31f)
            text(formatSeconds(split.elapsedMs), 470f, y, 31f, Color.WHITE, true)
            text("${"%.1f".format(split.trapSpeedKmh)} km/h", 760f, y, 27f, Color.rgb(0, 229, 255), true)
            y += 70f
        }
        if (record.distanceSplits.isEmpty()) {
            text("Speed-only test", 76f, y, 31f, Color.GRAY)
            y += 70f
        }

        y += 30f
        paint.color = Color.rgb(35, 45, 58)
        canvas.drawRect(52f, y, width - 52f, y + 310f, paint)
        y += 58f
        text("Maximum speed", 78f, y, 28f, Color.LTGRAY)
        text("${"%.1f".format(record.maximumSpeedKmh)} km/h", 670f, y, 30f, Color.WHITE, true)
        y += 58f
        text("Data source", 78f, y, 28f, Color.LTGRAY)
        text(record.dataSource.name.replace('_', '+'), 670f, y, 27f, Color.WHITE, true)
        y += 58f
        text("Measurement quality", 78f, y, 28f, Color.LTGRAY)
        text(record.measurementQuality.name, 670f, y, 30f, qualityColor(record.measurementQuality), true)
        y += 58f
        text("Estimated error", 78f, y, 28f, Color.LTGRAY)
        text(record.estimatedTimingErrorMs?.let { "±${"%.2f".format(it / 1_000.0)} s" } ?: "—", 670f, y, 30f, Color.WHITE, true)

        text("GPS coordinates are intentionally excluded from shared images.", 64f, height - 78f, 24f, Color.GRAY)
        val dir = File(context.cacheDir, "time-slip").apply { mkdirs() }
        val file = File(dir, "NTU-Time-Slip-${record.id.take(8)}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    fun shareFileIntent(context: Context, file: File, mimeType: String, subject: String): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun shareTextIntent(record: TimeSlipRecord): Intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "NTU Performance Time Slip")
        putExtra(Intent.EXTRA_TEXT, summaryText(record))
    }

    fun summaryText(record: TimeSlipRecord): String = buildString {
        appendLine("NTU PERFORMANCE TIME SLIP")
        appendLine()
        record.speedMilestones.forEach { appendLine("${it.displayName}: ${formatSeconds(it.elapsedMs)}") }
        record.distanceSplits.forEach {
            appendLine("${it.target.displayName}: ${formatSeconds(it.elapsedMs)} — ${"%.1f".format(it.trapSpeedKmh)} km/h")
        }
        appendLine()
        appendLine("Maximum speed: ${"%.1f".format(record.maximumSpeedKmh)} km/h")
        appendLine("Data source: ${record.dataSource.name.replace('_', '+')}")
        appendLine("Measurement quality: ${record.measurementQuality.name}")
        record.estimatedTimingErrorMs?.let { appendLine("Estimated timing error: ±${"%.2f".format(it / 1_000.0)} s") }
        appendLine("GPS coordinates are not included.")
    }

    fun formatSeconds(milliseconds: Long): String = "%.3f s".format(Locale.US, milliseconds / 1_000.0)

    private fun isoDate(epochMs: Long): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(Date(epochMs))
    private fun Double?.orEmpty(): String = this?.let { "%.4f".format(Locale.US, it) }.orEmpty()
    private fun qualityColor(quality: MeasurementQuality): Int = when (quality) {
        MeasurementQuality.HIGH -> Color.rgb(76, 217, 100)
        MeasurementQuality.MEDIUM -> Color.rgb(255, 193, 7)
        MeasurementQuality.LOW -> Color.rgb(255, 152, 0)
        MeasurementQuality.INVALID -> Color.rgb(255, 82, 82)
    }
}
