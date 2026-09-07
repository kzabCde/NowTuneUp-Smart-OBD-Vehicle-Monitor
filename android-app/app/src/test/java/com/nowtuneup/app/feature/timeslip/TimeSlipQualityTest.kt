package com.nowtuneup.app.feature.timeslip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeSlipQualityTest {
    @Test
    fun completedObdRunCarriesQualityScoreAndLatency() {
        val engine = TimeSlipEngine()
        engine.arm(
            requestedConfig = TimeSlipConfig(selectedDistanceTarget = null, speedOnlyTargetKmh = 100.0),
            currentSpeedKmh = 0.0,
            nowNanos = seconds(0.0),
            wallClockMillis = 1_000L,
        )
        engine.ingestTelemetry(sample(0.0, 1.0, 80L))
        engine.ingestTelemetry(sample(8.0, 1.25, 82L))
        engine.ingestTelemetry(sample(20.0, 1.50, 78L))
        engine.ingestTelemetry(sample(35.0, 1.75, 85L))
        engine.ingestTelemetry(sample(52.0, 2.00, 84L))
        engine.ingestTelemetry(sample(68.0, 2.25, 79L))
        engine.ingestTelemetry(sample(82.0, 2.50, 81L))
        engine.ingestTelemetry(sample(94.0, 2.75, 83L))
        val completed = engine.ingestTelemetry(sample(104.0, 3.00, 80L))

        assertEquals(TimeSlipStatus.COMPLETED, completed.status)
        val record = completed.record!!
        assertTrue(record.qualityScore >= 65)
        assertTrue(record.averageTransportLatencyMillis in 70L..90L)
        assertEquals("OBD-II PID 010D · interpolated crossings", record.dataSource)
        assertTrue(record.validityNotes.isNotEmpty())
    }

    @Test
    fun impossibleLaunchJumpIsRejected() {
        val engine = TimeSlipEngine()
        engine.arm(TimeSlipConfig(selectedDistanceTarget = null), 0.0, seconds(0.0), 1_000L)
        engine.ingestSpeed(0.0, seconds(1.0))
        val invalid = engine.ingestSpeed(40.0, seconds(1.1))
        assertEquals(TimeSlipStatus.INVALID_RUN, invalid.status)
    }

    private fun sample(speed: Double, second: Double, latency: Long) = TimeSlipTelemetrySample(
        timeNanos = seconds(second),
        wallClockMillis = 1_000L + (second * 1_000).toLong(),
        obdSpeedKmh = speed,
        fusedSpeedKmh = speed,
        transportLatencyMillis = latency,
        source = MeasurementSource.OBD_ONLY,
    )

    private fun seconds(value: Double): Long = (value * 1_000_000_000L).toLong()
}
