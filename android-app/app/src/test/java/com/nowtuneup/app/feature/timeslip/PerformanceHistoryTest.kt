package com.nowtuneup.app.feature.timeslip

import org.junit.Assert.*
import org.junit.Test

class PerformanceHistoryTest {
    private fun record(id: String, elapsed: Long, date: Long = 1, target: Double = 100.0) = TimeSlipRecord(
        id = id, elapsedMillis = elapsed, startedAtEpochMillis = date,
        vehicleProfileId = "test-car", measurementQuality = MeasurementQuality.HIGH,
        speedMilestones = listOf(SpeedMilestoneResult("0–${target.toInt()} km/h", 0.0, target, elapsed, 30.0)))

    @Test fun mphTargetIsNotSixtyKmh() {
        val config = sixtyMphConfig()
        assertEquals(96.56064, config.speedOnlyTargetKmh, 0.00000001)
        assertNull(config.selectedDistanceTarget)
        assertFalse(TimeSlipAnalysis.comparable(record("mph", 1000, target = config.speedOnlyTargetKmh), record("kmh", 1000, target = 60.0)))
    }
    @Test fun sortingKeepsInvalidTimesBehindMeasuredTimesWithoutDroppingRecords() {
        val records = listOf(record("slow", 2000, 1), record("fast", 1000, 3), record("invalid", 0, 2).copy(measurementQuality = MeasurementQuality.INVALID))
        assertEquals(listOf("fast","invalid","slow"), performanceHistory(records).map { it.id })
        assertEquals(listOf("fast","slow","invalid"), performanceHistory(records, sort = HistorySort.VALID_ELAPSED).map { it.id })
    }
    @Test fun searchAndModeFilterPreserveLegacyRecords() {
        val legacy = record("legacy", 1200).copy(vehicleProfileId = null, speedMilestones = emptyList())
        val rolling = record("rolling", 1000).copy(mode = PerformanceMode.ROLLING_START)
        assertEquals(listOf(legacy), performanceHistory(listOf(legacy,rolling), query = "legacy"))
        assertEquals(listOf(rolling), performanceHistory(listOf(legacy,rolling), mode = PerformanceMode.ROLLING_START))
        assertFalse(TimeSlipAnalysis.comparable(legacy, legacy.copy(id = "other")))
        assertFalse(TimeSlipAnalysis.comparable(legacy.copy(vehicleProfileId = ""), legacy.copy(vehicleProfileId = "")))
    }
    @Test fun shareReportIncludesSourceAndLimitations() {
        val report = record("share", 1500).copy(oneFootRolloutEnabled = true,
            validityNotes = listOf("Low sampling frequency")).asShareText()
        assertTrue(report.contains("Elapsed: 1.500"))
        assertTrue(report.contains("OBD-II PID 010D"))
        assertTrue(report.contains("One-foot rollout: true"))
        assertTrue(report.contains("Low sampling frequency"))
        assertTrue(report.contains("estimated"))
    }
}
