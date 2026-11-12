package com.nowtuneup.app.feature.timeslip

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class PerformanceReportShareTest {
    @Test fun reportIsLocalPngAndDoesNotAlterStoredMeasurements() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val record = TimeSlipRecord(id = "share-test", vehicleProfileId = "fixture-car", elapsedMillis = 5123,
            measurementQuality = MeasurementQuality.MEDIUM, qualityScore = 75, obdSampleRateHz = 4.5,
            timingStartNanos = 0, launchTimeNanos = 0, completionTimeNanos = 5_123_000_000,
            launchSpeedKmh = 0.0, rawSamples = listOf(TimeSlipTelemetrySample(1_000_000_000, 1000, 20.0),
                TimeSlipTelemetrySample(2_000_000_000, 2000, 40.0)))
        val file = TimeSlipShare.createReport(context, record)
        try {
            assertTrue(file.isFile)
            assertTrue(file.canonicalPath.startsWith(context.cacheDir.canonicalPath))
            val bitmap = BitmapFactory.decodeFile(file.path)
            assertEquals(800, bitmap.width)
            assertTrue(bitmap.height > 800)
            bitmap.recycle()
            assertEquals(5123, record.elapsedMillis)
            assertEquals(2, record.rawSamples!!.size)
        } finally { file.delete() }
    }
}
