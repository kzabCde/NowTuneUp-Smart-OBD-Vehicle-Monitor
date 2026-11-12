package com.nowtuneup.app.feature.timeslip

import org.junit.Assert.*
import org.junit.Test

class HistoryPresentationTest {
    private val now = 40L * 86_400_000L
    private fun record(id: String, car: String?, daysAgo: Long, target: DistanceTarget? = null) = TimeSlipRecord(
        id = id, vehicleProfileId = car, startedAtEpochMillis = now - daysAgo * 86_400_000L,
        selectedDistanceTarget = target, elapsedMillis = 5000, measurementQuality = MeasurementQuality.MEDIUM)

    @Test fun filtersMeasuredVehicleTestAndDateWithoutChangingTheOriginalHistory() {
        val recent = record("recent", "a", 1, DistanceTarget.QUARTER_MILE)
        val other = record("other", "b", 1, DistanceTarget.QUARTER_MILE)
        val old = record("old", "a", 20, DistanceTarget.ONE_MILE)
        val records = listOf(recent, other, old)
        val filtered = filterHistoryPresentation(records, vehicleId = "a", testType = performanceTitle(recent), dateRange = HistoryDateRange.WEEK, nowMillis = now)
        assertEquals(listOf(recent), filtered)
        assertEquals(3, records.size)
        assertEquals(listOf(recent, old), filterHistoryPresentation(records, vehicleId = "a", nowMillis = now))
    }
    @Test fun unknownLegacyVehicleAndDateStayAccessibleWithoutInventedTimestamps() {
        val legacy = TimeSlipRecord(id = "legacy", startedAtEpochMillis = 0L, vehicleProfileId = null)
        assertEquals(listOf(legacy), filterHistoryPresentation(listOf(legacy), vehicleId = "", nowMillis = now))
        assertTrue(filterHistoryPresentation(listOf(legacy), dateRange = HistoryDateRange.WEEK, nowMillis = now).isEmpty())
        assertTrue(legacy.recordedDateLabel().contains("unavailable"))
        assertFalse(legacy.hasMeasuredElapsed())
        assertEquals("Unavailable", legacy.timingEstimateLabel())
        assertTrue(TimeSlipShare.reportText(legacy).contains("Elapsed: Unavailable"))
        assertFalse(TimeSlipShare.reportText(legacy).contains("Elapsed: 0.000"))
    }
    @Test fun reportFactsPreserveRecordedQualitySourceRolloutAndLimitations() {
        val record = record("report", "my-car", 1).copy(dataSource = "OBD-II PID 010D", obdSampleRateHz = 4.2,
            oneFootRolloutEnabled = true, estimatedTimingErrorMillis = 125, validityNotes = listOf("Missing samples"))
        val facts = performanceReportFacts(record).toMap()
        assertEquals("my-car", facts["Vehicle"])
        assertEquals("OBD-II PID 010D", facts["Source"])
        assertEquals("4.2 Hz observed", facts["OBD sampling"])
        assertEquals("1 foot", facts["Rollout"])
        assertEquals("±125 ms", facts["Timing estimate"])
        assertEquals("Missing samples", facts["Limitation"])
        assertFalse(facts.values.any { it.contains("verified", true) || it.contains("GPS", true) })
    }
}
