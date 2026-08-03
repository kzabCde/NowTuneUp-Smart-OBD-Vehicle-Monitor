package com.nowtuneup.app.data.timeslip

import com.nowtuneup.app.feature.timeslip.domain.DistanceSplit
import com.nowtuneup.app.feature.timeslip.domain.DistanceTarget
import com.nowtuneup.app.feature.timeslip.domain.MeasurementQuality
import com.nowtuneup.app.feature.timeslip.domain.SpeedMilestone
import com.nowtuneup.app.feature.timeslip.domain.TimeSlipDataSource
import com.nowtuneup.app.feature.timeslip.domain.TimeSlipRecord
import com.nowtuneup.app.feature.timeslip.domain.TimeSlipStatus
import com.nowtuneup.app.feature.timeslip.domain.TimeSlipTestMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeSlipBestResultSelectorTest {
    @Test
    fun lowConfidenceResultIsExcludedByDefault() {
        val medium = record("medium", MeasurementQuality.MEDIUM, 8_500L, 15_200L, 150.0)
        val low = record("low", MeasurementQuality.LOW, 7_900L, 14_800L, 155.0)

        val best = TimeSlipBestResultSelector.select(listOf(medium, low))

        assertEquals("medium", best.zeroTo100Kmh?.first?.id)
        assertEquals("medium", best.quarterMile?.first?.id)
        assertEquals(150.0, best.fastestTrapSpeedKmh ?: 0.0, 0.001)
    }

    @Test
    fun lowConfidenceCanBeIncludedExplicitlyButInvalidNeverWins() {
        val low = record("low", MeasurementQuality.LOW, 7_900L, 14_800L, 155.0)
        val invalid = record("invalid", MeasurementQuality.INVALID, 6_000L, 12_000L, 180.0)

        val best = TimeSlipBestResultSelector.select(listOf(low, invalid), includeLowConfidence = true)

        assertEquals("low", best.zeroTo100Kmh?.first?.id)
        assertEquals(155.0, best.fastestTrapSpeedKmh ?: 0.0, 0.001)
        assertNull(TimeSlipBestResultSelector.select(listOf(invalid), includeLowConfidence = true).zeroTo100Kmh)
    }

    private fun record(
        id: String,
        quality: MeasurementQuality,
        zeroToHundredMs: Long,
        quarterMileMs: Long,
        trapSpeedKmh: Double,
    ) = TimeSlipRecord(
        id = id,
        status = TimeSlipStatus.COMPLETED,
        testMode = TimeSlipTestMode.STANDING_START,
        selectedDistanceTarget = DistanceTarget.QUARTER_MILE,
        startedAtEpochMs = 1_000L,
        completedAtEpochMs = 20_000L,
        startMonotonicTimeMs = 100L,
        completedMonotonicTimeMs = 20_000L,
        speedMilestones = listOf(
            SpeedMilestone("0-100-kmh", "0–100 km/h", 0.0, 100.0, zeroToHundredMs, 120.0, zeroToHundredMs),
        ),
        distanceSplits = listOf(
            DistanceSplit(DistanceTarget.QUARTER_MILE, 402.336, quarterMileMs, quarterMileMs, trapSpeedKmh, quarterMileMs),
        ),
        totalDistanceMeters = 402.336,
        elapsedMs = quarterMileMs,
        maximumSpeedKmh = trapSpeedKmh,
        maximumAccelerationMs2 = 4.0,
        dataSource = TimeSlipDataSource.OBD_GPS,
        obdDeviceName = "test",
        obdSampleRateHz = 8.0,
        gpsSampleRateHz = 10.0,
        averageGpsAccuracyMeters = 2.0,
        sampleCount = 100,
        droppedSampleCount = 0,
        connectionInterruptions = 0,
        gpsInterruptions = 0,
        measurementQuality = quality,
        estimatedTimingErrorMs = 80.0,
        vehicleName = "test",
    )
}
