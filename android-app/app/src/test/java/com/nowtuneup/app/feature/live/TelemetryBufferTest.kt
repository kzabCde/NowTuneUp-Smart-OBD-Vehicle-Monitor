package com.nowtuneup.app.feature.live

import com.google.gson.Gson
import com.nowtuneup.app.domain.model.VehicleReading
import org.junit.Assert.*
import org.junit.Test

class TelemetryBufferTest {
    private fun reading(time: Long, value: Double? = 42.0) = VehicleReading(13, "Speed, \"OBD\"", value, "km/h", updatedAt = time / 1_000_000, sourceTimeNanos = time)
    @Test fun duplicatesDelayedFutureAndStaleSamplesDoNotFabricateFrames() {
        val buffer = TelemetryBuffer()
        assertEquals(1, buffer.accept(listOf(reading(2_000_000_000)), true, 2_000_000_000, 100, "id").size)
        assertTrue(buffer.accept(listOf(reading(2_000_000_000), reading(1_000_000_000)), true, 3_000_000_000, 200, "id").isEmpty())
        assertTrue(buffer.accept(listOf(reading(9_000_000_000)), true, 3_000_000_000, 200, "id").isEmpty())
        assertTrue(buffer.accept(listOf(reading(3_000_000_000)), true, 8_000_000_000, 200, "id").isEmpty())
        assertEquals(1, buffer.snapshot().size)
    }
    @Test fun invalidAndDisconnectedEventsHaveNoNumericalValueAndBreakContinuity() {
        val buffer = TelemetryBuffer()
        buffer.accept(listOf(reading(1)), true, 1, 1, "id")
        val invalid = buffer.accept(listOf(reading(2, Double.NaN)), true, 2, 2, "id").single()
        assertNull(invalid.value); assertEquals(SampleQuality.INVALID, invalid.quality)
        val disconnected = buffer.accept(listOf(reading(2)), false, 3, 3, "id").single()
        assertNull(disconnected.value); assertEquals(SampleQuality.DISCONNECTED, disconnected.quality)
        assertTrue(buffer.accept(listOf(reading(2)), false, 4, 4, "id").isEmpty())
        assertTrue(buffer.accept(listOf(reading(2)), true, 5, 5, "id").isEmpty())
        assertEquals(1, buffer.accept(listOf(reading(6)), true, 6, 6, "id").size)
    }
    @Test fun buffersAreBoundedAndResetDoesNotMutateSavedSnapshots() {
        val rolling = TelemetryBuffer(2)
        (1L..3).forEach { rolling.accept(listOf(reading(it)), true, it, it, "id") }
        assertEquals(listOf(2L,3L), rolling.snapshot().map { it.sourceTimeNanos })
        val saved = rolling.snapshot(); rolling.clear(); assertEquals(2, saved.size)
        val recording = TelemetryBuffer(2, rolling = false)
        (1L..3).forEach { recording.accept(listOf(reading(it)), true, it, it, "id") }
        assertEquals(listOf(1L,2L), recording.snapshot().map { it.sourceTimeNanos })
    }
    @Test fun csvAndStorageRoundTripPreserveUnitsTimestampsAndMissingValues() {
        val b = TelemetryBuffer()
        b.accept(listOf(reading(1_234_567)), true, 1_234_567, 77, "session")
        b.accept(listOf(reading(2_000_000, null)), true, 2_000_000, 88, "session")
        val raw = TelemetryRecording("session", 1, 2, b.snapshot())
        assertEquals(raw, Gson().fromJson(Gson().toJson(raw), TelemetryRecording::class.java))
        val csv = telemetryCsv(raw)
        assertTrue(csv.contains("1234567")); assertTrue(csv.contains("\"km/h\""))
        assertTrue(csv.contains("\"Speed, \"\"OBD\"\"\""))
        assertTrue(csv.contains(",,\"km/h\",INVALID"))
        assertEquals(3, csv.trim().lines().size)
    }
}
