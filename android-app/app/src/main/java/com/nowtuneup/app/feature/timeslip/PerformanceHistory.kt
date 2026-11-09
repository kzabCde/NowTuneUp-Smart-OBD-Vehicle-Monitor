package com.nowtuneup.app.feature.timeslip

/** All numerical targets remain canonical km/h; 60 mph is exactly 96.56064 km/h. */
const val SIXTY_MPH_KMH = 96.56064
fun sixtyMphConfig() = TimeSlipConfig(mode = PerformanceMode.STANDING_START,
    selectedDistanceTarget = null, speedOnlyTargetKmh = SIXTY_MPH_KMH,
    enabledMilestonesKmh = listOf(60.0, SIXTY_MPH_KMH), useSensorFusion = false)

enum class HistorySort { NEWEST, VALID_ELAPSED }
fun performanceTitle(record: TimeSlipRecord): String = record.selectedDistanceTarget?.label
    ?: TimeSlipAnalysis.finishMilestone(record)?.label ?: "Time Slip (legacy target unavailable)"

fun performanceHistory(records: List<TimeSlipRecord>, query: String = "", mode: PerformanceMode? = null,
    sort: HistorySort = HistorySort.NEWEST): List<TimeSlipRecord> {
    val filtered = records.filter { record ->
        (mode == null || record.mode == mode) &&
            (query.isBlank() || listOf(performanceTitle(record), record.vehicleProfileId.orEmpty(),
                record.dataSource, record.mode.name, record.measurementQuality.name,
                java.text.DateFormat.getDateInstance().format(java.util.Date(record.startedAtEpochMillis)))
                .any { it.contains(query.trim(), ignoreCase = true) })
    }
    return when(sort) {
        HistorySort.NEWEST -> filtered.sortedByDescending { it.startedAtEpochMillis }
        HistorySort.VALID_ELAPSED -> filtered.sortedWith(compareBy<TimeSlipRecord> {
            if (it.status == TimeSlipStatus.COMPLETED && it.measurementQuality != MeasurementQuality.INVALID && it.elapsedMillis > 0) it.elapsedMillis else Long.MAX_VALUE
        }.thenByDescending { it.startedAtEpochMillis })
    }
}
