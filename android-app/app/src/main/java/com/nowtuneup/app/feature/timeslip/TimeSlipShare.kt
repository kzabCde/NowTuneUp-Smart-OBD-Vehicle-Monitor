package com.nowtuneup.app.feature.timeslip

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object TimeSlipShare {
    fun shareText(context: Context, value: String, mimeType: String = "text/plain") {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_TEXT, value)
                },
                "แชร์ผล Time Slip",
            ),
        )
    }

    fun shareImage(context: Context, record: TimeSlipRecord) {
        val bitmap = renderSummary(record)
        val directory = File(context.cacheDir, "time-slip").apply { mkdirs() }
        val file = File(directory, "NowTuneUp-TimeSlip-${record.id}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                "แชร์ภาพ Time Slip",
            ),
        )
    }

    private fun renderSummary(record: TimeSlipRecord): Bitmap {
        val bitmap = Bitmap.createBitmap(1080, 1350, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(5, 10, 16))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        var y = 110f

        fun line(text: String, size: Float = 42f, bold: Boolean = false, gap: Float = 64f) {
            paint.textSize = size
            paint.typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
            canvas.drawText(text.take(54), 72f, y, paint)
            y += gap
        }

        line("NOWTUNEUP", 64f, bold = true, gap = 78f)
        line("PERFORMANCE TIME SLIP • 1.8.0", 34f, bold = true, gap = 70f)
        line(if (record.mode == PerformanceMode.STANDING_START) "STANDING START" else "ROLLING START", 34f)
        line("Elapsed  ${formatSeconds(record.elapsedMillis)} s", 58f, bold = true, gap = 88f)
        if (record.reactionTimeMillis > 0L) line("Reaction  ${formatSeconds(record.reactionTimeMillis)} s", 34f)
        record.speedMilestones.take(5).forEach { line("${it.label}   ${formatSeconds(it.elapsedMillis)} s", 38f) }
        record.distanceSplits.take(6).forEach {
            line("${it.target.label}   ${formatSeconds(it.elapsedMillis)} s   ${"%.1f".format(it.trapSpeedKmh)} km/h", 34f)
        }
        y += 20f
        line("Max speed  ${"%.1f".format(record.maximumSpeedKmh)} km/h", 34f)
        line("Sample rate  ${"%.1f".format(record.obdSampleRateHz)} Hz", 34f)
        line("Source  ${record.dataSource}", 30f)
        line("Confidence  ${(record.speedConfidence ?: ConfidenceLevel.LOW).name} / ${(record.distanceConfidence ?: ConfidenceLevel.LOW).name}", 30f)
        line("Timing error  ±${record.estimatedTimingErrorMillis} ms", 30f)
        paint.color = Color.LTGRAY
        line("Use only on a closed course or private property.", 26f)
        return bitmap
    }
}
