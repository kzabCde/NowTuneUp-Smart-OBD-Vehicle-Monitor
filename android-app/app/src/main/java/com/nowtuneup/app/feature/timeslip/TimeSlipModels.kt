package com.nowtuneup.app.feature.timeslip

import java.util.UUID

enum class TimeSlipStatus {
    IDLE,
    ARMED,
    RUNNING,
    COMPLETED,
    CANCELLED,
    CONNECTION_LOST,
    INVALID_RUN,
}

enum class PerformanceMode {
    STANDING_START,
    ROLLING_START,
}

enum class MeasurementQuality {
    HIGH,
    MEDIUM,
    LOW,
    INVALID,
}

enum class DistanceTarget(val meters: Double, val label: String) {
    SIXTY_FEET(18.288, "60 ft"),
    THREE_THIRTY_FEET(100.584, "330 ft"),
    EIGHTH_MILE(201.168, "1/8 mile"),
    QUARTER_MILE(402.336, "1/4 mile"),
    HALF_MILE(804.672, "1/2 mile"),
    ONE_MILE(1609.344, "1 mile"),
}

data class TimeSlipConfig(
    val mode: PerformanceMode = PerformanceMode.STANDING_START,
    val selectedDistanceTarget: DistanceTarget? = DistanceTarget.QUARTER_MILE,
    val speedOnlyTargetKmh: Double = 100.0,
    val rollingStartKmh: Double = 60.0,
    val rollingTargetKmh: Double = 100.0,
    val enabledMilestonesKmh: List<Double> = listOf(60.0, 96.56064, 100.0, 120.0, 160.0),
    val stationaryThresholdKmh: Double = 1.0,
    val launchThresholdKmh: Double = 2.0,
    val stationaryHoldMillis: Long = 1_000L,
)

data class SpeedMilestoneResult(
    val label: String,
    val startSpeedKmh: Double,
    val targetSpeedKmh: Double,
    val elapsedMillis: Long,
    val distanceAtTargetMeters: Double,
)

data class DistanceSplitResult(
    val target: DistanceTarget,
    val elapsedMillis: Long,
    val splitMillis: Long,
    val trapSpeedKmh: Double,
)

data class TimeSlipRecord(
    val id: String = UUID.randomUUID().toString(),
    val status: TimeSlipStatus = TimeSlipStatus.COMPLETED,
    val mode: PerformanceMode = PerformanceMode.STANDING_START,
    val selectedDistanceTarget: DistanceTarget? = null,
    val startedAtEpochMillis: Long = 0L,
    val completedAtEpochMillis: Long = 0L,
    val elapsedMillis: Long = 0L,
    val speedMilestones: List<SpeedMilestoneResult> = emptyList(),
    val distanceSplits: List<DistanceSplitResult> = emptyList(),
    val totalDistanceMeters: Double = 0.0,
    val maximumSpeedKmh: Double = 0.0,
    val sampleCount: Int = 0,
    val droppedSampleCount: Int = 0,
    val obdSampleRateHz: Double = 0.0,
    val measurementQuality: MeasurementQuality = MeasurementQuality.INVALID,
    val estimatedTimingErrorMillis: Long = 0L,
    val dataSource: String = "OBD-II PID 010D",
    val distanceEstimated: Boolean = true,
)

data class TimeSlipSnapshot(
    val status: TimeSlipStatus = TimeSlipStatus.IDLE,
    val config: TimeSlipConfig = TimeSlipConfig(),
    val currentSpeedKmh: Double = 0.0,
    val elapsedMillis: Long = 0L,
    val distanceMeters: Double = 0.0,
    val maximumSpeedKmh: Double = 0.0,
    val speedMilestones: List<SpeedMilestoneResult> = emptyList(),
    val distanceSplits: List<DistanceSplitResult> = emptyList(),
    val sampleCount: Int = 0,
    val droppedSampleCount: Int = 0,
    val message: String? = null,
    val record: TimeSlipRecord? = null,
) {
    val active: Boolean
        get() = status == TimeSlipStatus.ARMED || status == TimeSlipStatus.RUNNING
}
