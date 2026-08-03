package com.nowtuneup.app.feature.timeslip.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementQualityCalculatorTest {
    @Test
    fun highQualityRequiresGoodRatesAndGps() {
        val quality = MeasurementQualityCalculator.calculate(
            requiresGps = true,
            obdRateHz = 9.0,
            gpsRateHz = 10.0,
            averageGpsAccuracyMeters = 1.8,
            sampleCount = 300,
            droppedSampleCount = 0,
            sensorDisagreementKmh = 2.0,
            connectionInterruptions = 0,
            gpsInterruptions = 0,
            maximumGapMs = 140L,
            distanceCorrectionMeters = 3.0,
            totalDistanceMeters = 402.336,
            terminalStatus = TimeSlipStatus.COMPLETED,
        )
        assertEquals(MeasurementQuality.HIGH, quality.quality)
        assertTrue(quality.estimatedTimingErrorMs < 200.0)
    }

    @Test
    fun incompleteRunIsAlwaysInvalid() {
        val quality = MeasurementQualityCalculator.calculate(
            requiresGps = false,
            obdRateHz = 10.0,
            gpsRateHz = 0.0,
            averageGpsAccuracyMeters = null,
            sampleCount = 100,
            droppedSampleCount = 0,
            sensorDisagreementKmh = 0.0,
            connectionInterruptions = 1,
            gpsInterruptions = 0,
            maximumGapMs = 100L,
            distanceCorrectionMeters = 0.0,
            totalDistanceMeters = 100.0,
            terminalStatus = TimeSlipStatus.CONNECTION_LOST,
        )
        assertEquals(MeasurementQuality.INVALID, quality.quality)
    }

    @Test
    fun lowGpsRateAndAccuracyCannotBeHigh() {
        val quality = MeasurementQualityCalculator.calculate(
            requiresGps = true,
            obdRateHz = 5.0,
            gpsRateHz = 1.0,
            averageGpsAccuracyMeters = 9.5,
            sampleCount = 100,
            droppedSampleCount = 12,
            sensorDisagreementKmh = 12.0,
            connectionInterruptions = 0,
            gpsInterruptions = 1,
            maximumGapMs = 1_200L,
            distanceCorrectionMeters = 60.0,
            totalDistanceMeters = 402.336,
            terminalStatus = TimeSlipStatus.COMPLETED,
        )
        assertTrue(quality.quality in setOf(MeasurementQuality.LOW, MeasurementQuality.INVALID))
    }
}
