package com.nowtuneup.app.feature.timeslip.domain

enum class TimeSlipStatus {
    IDLE,
    WAITING_FOR_CONNECTION,
    WAITING_FOR_GPS,
    ARMED,
    LAUNCH_DETECTED,
    RUNNING,
    COMPLETED,
    CANCELLED,
    CONNECTION_LOST,
    GPS_UNRELIABLE,
    INVALID_RUN,
}

enum class MeasurementQuality { HIGH, MEDIUM, LOW, INVALID }
enum class TimeSlipTestMode { STANDING_START, ROLLING_START }
enum class TimeSlipUnitSystem { METRIC, IMPERIAL }
enum class TimeSlipDataSource { OBD, GPS, OBD_GPS, OBD_GPS_ACCELEROMETER }

enum class DistanceTarget(val meters: Double, val displayName: String) {
    SPEED_ONLY(0.0, "ทดสอบความเร็วอย่างเดียว"),
    SIXTY_FT(18.288, "60 ft"),
    THREE_THIRTY_FT(100.584, "330 ft"),
    EIGHTH_MILE(201.168, "1/8 mile"),
    QUARTER_MILE(402.336, "1/4 mile"),
    HALF_MILE(804.672, "1/2 mile"),
    ONE_MILE(1609.344, "1 mile"),
}

object TimeSlipConstants {
    val orderedDistanceTargets = listOf(
        DistanceTarget.SIXTY_FT,
        DistanceTarget.THREE_THIRTY_FT,
        DistanceTarget.EIGHTH_MILE,
        DistanceTarget.QUARTER_MILE,
        DistanceTarget.HALF_MILE,
        DistanceTarget.ONE_MILE,
    )

    const val MPH_TO_KMH = 1.609344
    const val STATIONARY_MAX_KMH = 1.0
    const val DEFAULT_STATIONARY_DURATION_MS = 1_000L
    const val DEFAULT_LAUNCH_THRESHOLD_KMH = 2.0
    const val START_ESTIMATE_THRESHOLD_KMH = 0.5
    const val MAX_SAMPLE_GAP_MS = 1_500L
    const val MAX_RUN_DURATION_MS = 180_000L
    const val MAX_REASONABLE_SPEED_KMH = 420.0
    const val MAX_REASONABLE_ACCELERATION_MS2 = 25.0
    const val RECOMMENDED_GPS_ACCURACY_METERS = 5.0
    const val MAX_GPS_ACCURACY_METERS = 10.0
    const val GPS_STALE_AFTER_MS = 1_500L
    const val MIN_RECOMMENDED_GPS_RATE_HZ = 5.0
    const val HIGH_GPS_RATE_HZ = 10.0
    const val MAX_PERSISTED_SAMPLES = 2_000
}

data class SpeedMilestoneDefinition(
    val id: String,
    val displayName: String,
    val startSpeedKmh: Double,
    val targetSpeedKmh: Double,
    val enabled: Boolean = true,
)

object StandardSpeedMilestones {
    val zeroTo60Kmh = SpeedMilestoneDefinition("0-60-kmh", "0–60 km/h", 0.0, 60.0)
    val zeroTo100Kmh = SpeedMilestoneDefinition("0-100-kmh", "0–100 km/h", 0.0, 100.0)
    val zeroTo120Kmh = SpeedMilestoneDefinition("0-120-kmh", "0–120 km/h", 0.0, 120.0, false)
    val zeroTo160Kmh = SpeedMilestoneDefinition("0-160-kmh", "0–160 km/h", 0.0, 160.0, false)
    val sixtyTo100Kmh = SpeedMilestoneDefinition("60-100-kmh", "60–100 km/h", 60.0, 100.0, false)
    val eightyTo120Kmh = SpeedMilestoneDefinition("80-120-kmh", "80–120 km/h", 80.0, 120.0, false)
    val zeroTo60Mph = SpeedMilestoneDefinition(
        "0-60-mph",
        "0–60 mph",
        0.0,
        60.0 * TimeSlipConstants.MPH_TO_KMH,
        false,
    )

    val defaults = listOf(
        zeroTo60Kmh,
        zeroTo100Kmh,
        zeroTo120Kmh,
        zeroTo160Kmh,
        sixtyTo100Kmh,
        eightyTo120Kmh,
        zeroTo60Mph,
    )
}

data class TimeSlipConfig(
    val testMode: TimeSlipTestMode = TimeSlipTestMode.STANDING_START,
    val selectedDistanceTarget: DistanceTarget = DistanceTarget.QUARTER_MILE,
    val speedMilestones: List<SpeedMilestoneDefinition> = StandardSpeedMilestones.defaults,
    val rollingStartSpeedKmh: Double = 60.0,
    val rollingTargetSpeedKmh: Double = 100.0,
    val customSpeedEnabled: Boolean = false,
    val unitSystem: TimeSlipUnitSystem = TimeSlipUnitSystem.METRIC,
    val stationaryDurationMs: Long = TimeSlipConstants.DEFAULT_STATIONARY_DURATION_MS,
    val launchThresholdKmh: Double = TimeSlipConstants.DEFAULT_LAUNCH_THRESHOLD_KMH,
    val vehicleName: String = "",
) {
    val requiresGps: Boolean get() = selectedDistanceTarget != DistanceTarget.SPEED_ONLY

    fun normalized(): TimeSlipConfig {
        val customEnabled = customSpeedEnabled || testMode == TimeSlipTestMode.ROLLING_START
        val custom = SpeedMilestoneDefinition(
            id = "custom-range",
            displayName = "${rollingStartSpeedKmh.formatCompact()}–${rollingTargetSpeedKmh.formatCompact()} km/h",
            startSpeedKmh = rollingStartSpeedKmh.coerceAtLeast(0.0),
            targetSpeedKmh = rollingTargetSpeedKmh.coerceAtLeast(0.1),
            enabled = customEnabled,
        )
        val standard = speedMilestones.filterNot { it.id == custom.id }.map { milestone ->
            if (testMode == TimeSlipTestMode.ROLLING_START) milestone.copy(enabled = false) else milestone
        }
        val milestones = standard + custom
        return copy(
            speedMilestones = milestones,
            customSpeedEnabled = customEnabled,
            stationaryDurationMs = stationaryDurationMs.coerceIn(500L, 5_000L),
            launchThresholdKmh = launchThresholdKmh.coerceIn(1.0, 8.0),
            vehicleName = vehicleName.take(60),
        )
    }

    fun validate(): String? = when {
        (customSpeedEnabled || testMode == TimeSlipTestMode.ROLLING_START) && rollingTargetSpeedKmh <= rollingStartSpeedKmh ->
            "ความเร็วปลายต้องมากกว่าความเร็วเริ่มต้น"
        selectedDistanceTarget == DistanceTarget.SPEED_ONLY && speedMilestones.none { it.enabled } ->
            "เลือกเป้าหมายความเร็วอย่างน้อยหนึ่งรายการ"
        else -> null
    }
}

data class ObdSpeedTelemetry(
    val speedKmh: Double,
    val monotonicTimeMs: Long,
)

data class GpsTelemetry(
    val speedKmh: Double?,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val monotonicTimeMs: Long,
    val isMock: Boolean,
)

data class AccelerometerTelemetry(
    val longitudinalAccelerationMs2: Double,
    val monotonicTimeMs: Long,
)

data class GpsStatus(
    val permissionGranted: Boolean = false,
    val providerEnabled: Boolean = false,
    val hasPositionLock: Boolean = false,
    val accuracyMeters: Double? = null,
    val sampleRateHz: Double = 0.0,
    val lastUpdateMonotonicMs: Long? = null,
    val isMock: Boolean = false,
) {
    fun isUsable(nowMonotonicMs: Long): Boolean = permissionGranted &&
        providerEnabled &&
        hasPositionLock &&
        !isMock &&
        accuracyMeters != null &&
        accuracyMeters <= TimeSlipConstants.MAX_GPS_ACCURACY_METERS &&
        lastUpdateMonotonicMs != null &&
        nowMonotonicMs - lastUpdateMonotonicMs <= TimeSlipConstants.GPS_STALE_AFTER_MS
}

data class PerformanceSample(
    val monotonicTimeMs: Long,
    val elapsedMs: Long = 0L,
    val obdSpeedKmh: Double? = null,
    val gpsSpeedKmh: Double? = null,
    val fusedSpeedKmh: Double,
    val accumulatedDistanceMeters: Double = 0.0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val gpsAccuracyMeters: Double? = null,
    val gpsDistanceDeltaMeters: Double? = null,
    val accelerationMs2: Double? = null,
    val obdValid: Boolean,
    val gpsValid: Boolean,
)

data class SpeedMilestone(
    val id: String,
    val displayName: String,
    val startSpeedKmh: Double,
    val targetSpeedKmh: Double,
    val elapsedMs: Long,
    val distanceAtTargetMeters: Double,
    val crossingTimeMs: Long,
)

data class DistanceSplit(
    val target: DistanceTarget,
    val targetDistanceMeters: Double,
    val elapsedMs: Long,
    val splitMs: Long,
    val trapSpeedKmh: Double,
    val crossingTimeMs: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

data class TimeSlipRecord(
    val id: String,
    val status: TimeSlipStatus,
    val testMode: TimeSlipTestMode,
    val selectedDistanceTarget: DistanceTarget?,
    val startedAtEpochMs: Long,
    val completedAtEpochMs: Long?,
    val startMonotonicTimeMs: Long,
    val completedMonotonicTimeMs: Long?,
    val speedMilestones: List<SpeedMilestone>,
    val distanceSplits: List<DistanceSplit>,
    val totalDistanceMeters: Double,
    val elapsedMs: Long,
    val maximumSpeedKmh: Double,
    val maximumAccelerationMs2: Double?,
    val dataSource: TimeSlipDataSource,
    val obdDeviceName: String?,
    val obdSampleRateHz: Double?,
    val gpsSampleRateHz: Double?,
    val averageGpsAccuracyMeters: Double?,
    val sampleCount: Int,
    val droppedSampleCount: Int,
    val connectionInterruptions: Int,
    val gpsInterruptions: Int,
    val measurementQuality: MeasurementQuality,
    val estimatedTimingErrorMs: Double?,
    val vehicleName: String?,
    val notes: String? = null,
    val distanceCorrectionMeters: Double = 0.0,
    val samples: List<PerformanceSample> = emptyList(),
)

data class MeasurementQualityResult(
    val quality: MeasurementQuality,
    val estimatedTimingErrorMs: Double,
    val score: Int,
    val reasons: List<String>,
)

data class TimeSlipEngineSnapshot(
    val status: TimeSlipStatus = TimeSlipStatus.IDLE,
    val currentSpeedKmh: Double = 0.0,
    val elapsedMs: Long = 0L,
    val accumulatedDistanceMeters: Double = 0.0,
    val nextDistanceTarget: DistanceTarget? = null,
    val speedMilestones: List<SpeedMilestone> = emptyList(),
    val distanceSplits: List<DistanceSplit> = emptyList(),
    val quality: MeasurementQuality = MeasurementQuality.LOW,
    val estimatedTimingErrorMs: Double? = null,
    val invalidReason: String? = null,
    val result: TimeSlipRecord? = null,
)

private fun Double.formatCompact(): String = if (this % 1.0 == 0.0) toInt().toString() else "%.1f".format(this)
