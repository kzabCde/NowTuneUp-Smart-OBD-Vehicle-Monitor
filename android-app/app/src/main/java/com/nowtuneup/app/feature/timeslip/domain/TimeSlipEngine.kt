package com.nowtuneup.app.feature.timeslip.domain

import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

class TimeSlipEngine {
    private var config = TimeSlipConfig()
    private var status = TimeSlipStatus.IDLE
    private var requestedAtEpochMs = 0L
    private var requestedAtMonotonicMs = 0L
    private var stationarySinceMs: Long? = null
    private var stationaryReady = false
    private var startMonotonicMs: Long? = null
    private var startedAtEpochMs: Long? = null
    private var completedMonotonicMs: Long? = null
    private var previousSample: PerformanceSample? = null
    private var accumulatedDistanceMeters = 0.0
    private var distanceAtLastGpsAnchor = 0.0
    private var lastGpsAnchorTimeMs: Long? = null
    private var distanceCorrectionMeters = 0.0
    private var maximumSpeedKmh = 0.0
    private var maximumAccelerationMs2: Double? = null
    private var maximumGapMs = 0L
    private var droppedSampleCount = 0
    private var connectionInterruptions = 0
    private var gpsInterruptions = 0
    private var obdRateHz = 0.0
    private var gpsRateHz = 0.0
    private var averageGpsAccuracyMeters: Double? = null
    private var obdDeviceName: String? = null
    private var sensorDisagreementSum = 0.0
    private var sensorDisagreementCount = 0
    private var invalidReason: String? = null
    private var result: TimeSlipRecord? = null
    private val speedMilestones = mutableListOf<SpeedMilestone>()
    private val distanceSplits = mutableListOf<DistanceSplit>()
    private val milestoneStartTimes = mutableMapOf<String, Long>()
    private val samples = mutableListOf<PerformanceSample>()

    fun snapshot(): TimeSlipEngineSnapshot {
        val now = previousSample?.monotonicTimeMs ?: startMonotonicMs ?: 0L
        val start = startMonotonicMs
        val elapsed = if (start == null) 0L else (completedMonotonicMs ?: now) - start
        return TimeSlipEngineSnapshot(
            status = status,
            currentSpeedKmh = previousSample?.fusedSpeedKmh ?: 0.0,
            elapsedMs = elapsed.coerceAtLeast(0L),
            accumulatedDistanceMeters = accumulatedDistanceMeters,
            nextDistanceTarget = nextDistanceTarget(),
            speedMilestones = speedMilestones.toList(),
            distanceSplits = distanceSplits.toList(),
            quality = result?.measurementQuality ?: provisionalQuality(),
            estimatedTimingErrorMs = result?.estimatedTimingErrorMs,
            invalidReason = invalidReason,
            result = result,
        )
    }

    fun requestArm(
        requestedConfig: TimeSlipConfig,
        wallClockEpochMs: Long,
        monotonicTimeMs: Long,
        connectionReady: Boolean,
        gpsReady: Boolean,
        deviceName: String?,
    ): Result<TimeSlipEngineSnapshot> {
        if (status in activeStatuses || status == TimeSlipStatus.COMPLETED) {
            return Result.failure(IllegalStateException("มีการทดสอบที่กำลังทำงานอยู่แล้ว"))
        }
        val normalized = requestedConfig.normalized()
        normalized.validate()?.let { return Result.failure(IllegalArgumentException(it)) }
        resetInternal()
        config = normalized
        requestedAtEpochMs = wallClockEpochMs
        requestedAtMonotonicMs = monotonicTimeMs
        obdDeviceName = deviceName
        status = when {
            !connectionReady -> TimeSlipStatus.WAITING_FOR_CONNECTION
            config.requiresGps && !gpsReady -> TimeSlipStatus.WAITING_FOR_GPS
            else -> TimeSlipStatus.ARMED
        }
        return Result.success(snapshot())
    }

    fun updateReadiness(connectionReady: Boolean, gpsReady: Boolean): TimeSlipEngineSnapshot {
        if (status == TimeSlipStatus.WAITING_FOR_CONNECTION && connectionReady) {
            status = if (config.requiresGps && !gpsReady) TimeSlipStatus.WAITING_FOR_GPS else TimeSlipStatus.ARMED
        } else if (status == TimeSlipStatus.WAITING_FOR_GPS && gpsReady && connectionReady) {
            status = TimeSlipStatus.ARMED
        }
        return snapshot()
    }

    fun updateTelemetryMetrics(
        obdSampleRateHz: Double,
        gpsSampleRateHz: Double,
        averageGpsAccuracyMeters: Double?,
    ) {
        obdRateHz = obdSampleRateHz.coerceAtLeast(0.0)
        gpsRateHz = gpsSampleRateHz.coerceAtLeast(0.0)
        this.averageGpsAccuracyMeters = averageGpsAccuracyMeters
    }

    fun ingest(sample: PerformanceSample): TimeSlipEngineSnapshot {
        if (status !in activeStatuses) return snapshot()
        val previous = previousSample
        if (previous != null && sample.monotonicTimeMs <= previous.monotonicTimeMs) {
            droppedSampleCount += 1
            return snapshot()
        }
        if (!sample.fusedSpeedKmh.isFinite() || sample.fusedSpeedKmh !in 0.0..TimeSlipConstants.MAX_REASONABLE_SPEED_KMH) {
            return invalidate("พบค่าความเร็วที่ไม่สมเหตุสมผล", TimeSlipStatus.INVALID_RUN)
        }

        if (previous != null) {
            val gap = sample.monotonicTimeMs - previous.monotonicTimeMs
            maximumGapMs = max(maximumGapMs, gap)
            if (gap > TimeSlipConstants.MAX_SAMPLE_GAP_MS) droppedSampleCount += (gap / 250L).toInt().coerceAtLeast(1)
            if (gap > 3_000L && status == TimeSlipStatus.RUNNING) {
                return invalidate("ข้อมูลขาดช่วงเกิน 3 วินาที", TimeSlipStatus.INVALID_RUN)
            }
        }

        if (sample.obdSpeedKmh != null && sample.gpsSpeedKmh != null) {
            sensorDisagreementSum += abs(sample.obdSpeedKmh - sample.gpsSpeedKmh)
            sensorDisagreementCount += 1
        }

        previousSample = sample
        when (status) {
            TimeSlipStatus.ARMED -> handleArmed(sample, previous)
            TimeSlipStatus.LAUNCH_DETECTED,
            TimeSlipStatus.RUNNING,
            -> processRunningSample(sample, previous)
            else -> Unit
        }
        return snapshot()
    }

    private fun handleArmed(sample: PerformanceSample, previous: PerformanceSample?) {
        if (config.testMode == TimeSlipTestMode.STANDING_START) {
            if (sample.fusedSpeedKmh <= TimeSlipConstants.STATIONARY_MAX_KMH) {
                val since = stationarySinceMs ?: sample.monotonicTimeMs.also { stationarySinceMs = it }
                stationaryReady = sample.monotonicTimeMs - since >= config.stationaryDurationMs
                return
            }
            if (!stationaryReady) {
                stationarySinceMs = null
                return
            }
            val crossedLaunch = previous != null &&
                previous.fusedSpeedKmh < config.launchThresholdKmh &&
                sample.fusedSpeedKmh >= config.launchThresholdKmh
            val accelerationLaunch = sample.accelerationMs2?.let { it >= 1.2 } == true && sample.fusedSpeedKmh >= 0.5
            if (crossedLaunch || accelerationLaunch) {
                val startEstimate = if (previous != null) {
                    TimeSlipMath.interpolateSpeedCrossingTime(
                        previous.fusedSpeedKmh,
                        sample.fusedSpeedKmh,
                        TimeSlipConstants.START_ESTIMATE_THRESHOLD_KMH,
                        previous.monotonicTimeMs,
                        sample.monotonicTimeMs,
                    )
                } else sample.monotonicTimeMs
                beginRun(startEstimate, sample, zeroStart = true)
            }
        } else {
            val startSpeed = config.rollingStartSpeedKmh
            if (previous != null && previous.fusedSpeedKmh < startSpeed && sample.fusedSpeedKmh >= startSpeed) {
                val crossing = TimeSlipMath.interpolateSpeedCrossingTime(
                    previous.fusedSpeedKmh,
                    sample.fusedSpeedKmh,
                    startSpeed,
                    previous.monotonicTimeMs,
                    sample.monotonicTimeMs,
                )
                beginRun(crossing, sample, zeroStart = false)
            }
        }
    }

    private fun beginRun(
        startTimeMs: Long,
        current: PerformanceSample,
        zeroStart: Boolean,
    ) {
        startMonotonicMs = startTimeMs
        startedAtEpochMs = requestedAtEpochMs + (startTimeMs - requestedAtMonotonicMs)
        status = TimeSlipStatus.LAUNCH_DETECTED
        config.speedMilestones.filter { it.enabled }.forEach { milestone ->
            val startsWithRun = if (zeroStart) {
                milestone.startSpeedKmh <= TimeSlipConstants.STATIONARY_MAX_KMH
            } else {
                kotlin.math.abs(milestone.startSpeedKmh - config.rollingStartSpeedKmh) < 0.01
            }
            if (startsWithRun) milestoneStartTimes[milestone.id] = startTimeMs
        }
        val startSpeed = if (zeroStart) TimeSlipConstants.START_ESTIMATE_THRESHOLD_KMH else config.rollingStartSpeedKmh
        val runStartSample = current.copy(
            monotonicTimeMs = startTimeMs,
            elapsedMs = 0L,
            fusedSpeedKmh = startSpeed,
            accumulatedDistanceMeters = 0.0,
        )
        previousSample = runStartSample
        appendSample(runStartSample)
        status = TimeSlipStatus.RUNNING
        if (current.monotonicTimeMs > startTimeMs) processRunningSample(current, runStartSample)
        else previousSample = current
    }

    private fun processRunningSample(sample: PerformanceSample, previousInput: PerformanceSample?) {
        val start = startMonotonicMs ?: return
        val previous = previousInput?.takeIf { it.monotonicTimeMs >= start } ?: previousSample ?: return
        if (sample.monotonicTimeMs < start) return
        val deltaMs = (sample.monotonicTimeMs - previous.monotonicTimeMs).coerceAtLeast(0L)
        val integrated = TimeSlipMath.integrateDistanceMeters(
            TimeSlipMath.kmhToMps(previous.fusedSpeedKmh),
            TimeSlipMath.kmhToMps(sample.fusedSpeedKmh),
            deltaMs / 1_000.0,
        )
        val previousDistance = accumulatedDistanceMeters
        accumulatedDistanceMeters += integrated

        if (sample.gpsValid && sample.gpsDistanceDeltaMeters != null) {
            val anchorTime = lastGpsAnchorTimeMs
            if (anchorTime == null) {
                lastGpsAnchorTimeMs = sample.monotonicTimeMs
                distanceAtLastGpsAnchor = accumulatedDistanceMeters
            } else if (sample.monotonicTimeMs > anchorTime) {
                val integratedSinceAnchor = accumulatedDistanceMeters - distanceAtLastGpsAnchor
                val rawCorrection = sample.gpsDistanceDeltaMeters - integratedSinceAnchor
                val limit = max(3.0, integratedSinceAnchor * 0.50)
                val applied = rawCorrection.coerceIn(-limit, limit) * 0.65
                accumulatedDistanceMeters = (accumulatedDistanceMeters + applied).coerceAtLeast(previousDistance)
                distanceCorrectionMeters += applied
                lastGpsAnchorTimeMs = sample.monotonicTimeMs
                distanceAtLastGpsAnchor = accumulatedDistanceMeters
            }
        }

        val speedDerivedAcceleration = if (deltaMs > 0L) {
            TimeSlipMath.kmhToMps(sample.fusedSpeedKmh - previous.fusedSpeedKmh) / (deltaMs / 1_000.0)
        } else null
        val effectiveAcceleration = when {
            sample.accelerationMs2 != null && speedDerivedAcceleration != null ->
                (sample.accelerationMs2 * 0.35 + speedDerivedAcceleration * 0.65)
                    .coerceIn(-TimeSlipConstants.MAX_REASONABLE_ACCELERATION_MS2, TimeSlipConstants.MAX_REASONABLE_ACCELERATION_MS2)
            sample.accelerationMs2 != null -> sample.accelerationMs2
            else -> speedDerivedAcceleration
        }
        val enriched = sample.copy(
            elapsedMs = sample.monotonicTimeMs - start,
            accumulatedDistanceMeters = accumulatedDistanceMeters,
            accelerationMs2 = effectiveAcceleration,
        )
        maximumSpeedKmh = max(maximumSpeedKmh, enriched.fusedSpeedKmh)
        enriched.accelerationMs2?.let { acceleration ->
            maximumAccelerationMs2 = max(maximumAccelerationMs2 ?: acceleration, acceleration)
        }
        detectMilestones(previous.copy(accumulatedDistanceMeters = previousDistance), enriched)
        detectDistanceSplits(previous.copy(accumulatedDistanceMeters = previousDistance), enriched)
        appendSample(enriched)
        previousSample = enriched

        val completionCrossingTime = if (config.selectedDistanceTarget == DistanceTarget.SPEED_ONLY) {
            val enabledIds = config.speedMilestones.filter { it.enabled }.map { it.id }.toSet()
            if (enabledIds.isNotEmpty() && speedMilestones.map { it.id }.containsAll(enabledIds)) {
                speedMilestones.filter { it.id in enabledIds }.maxOfOrNull { it.crossingTimeMs }
            } else null
        } else {
            distanceSplits.firstOrNull { it.target == config.selectedDistanceTarget }?.crossingTimeMs
        }
        when {
            completionCrossingTime != null -> complete(completionCrossingTime)
            enriched.elapsedMs > TimeSlipConstants.MAX_RUN_DURATION_MS ->
                invalidate("ไม่ถึงเป้าหมายภายในเวลาที่กำหนด", TimeSlipStatus.INVALID_RUN)
        }
    }

    private fun detectMilestones(previous: PerformanceSample, current: PerformanceSample) {
        config.speedMilestones.filter { it.enabled }.forEach { definition ->
            if (speedMilestones.any { it.id == definition.id }) return@forEach
            if (definition.targetSpeedKmh <= definition.startSpeedKmh) return@forEach

            if (definition.id !in milestoneStartTimes &&
                previous.fusedSpeedKmh < definition.startSpeedKmh &&
                current.fusedSpeedKmh >= definition.startSpeedKmh
            ) {
                milestoneStartTimes[definition.id] = TimeSlipMath.interpolateSpeedCrossingTime(
                    previous.fusedSpeedKmh,
                    current.fusedSpeedKmh,
                    definition.startSpeedKmh,
                    previous.monotonicTimeMs,
                    current.monotonicTimeMs,
                )
            }
            val milestoneStart = milestoneStartTimes[definition.id] ?: return@forEach
            if (previous.fusedSpeedKmh < definition.targetSpeedKmh && current.fusedSpeedKmh >= definition.targetSpeedKmh) {
                val crossing = TimeSlipMath.interpolateSpeedCrossingTime(
                    previous.fusedSpeedKmh,
                    current.fusedSpeedKmh,
                    definition.targetSpeedKmh,
                    previous.monotonicTimeMs,
                    current.monotonicTimeMs,
                )
                val ratio = if (current.monotonicTimeMs == previous.monotonicTimeMs) 1.0 else
                    ((crossing - previous.monotonicTimeMs).toDouble() /
                        (current.monotonicTimeMs - previous.monotonicTimeMs)).coerceIn(0.0, 1.0)
                val distance = previous.accumulatedDistanceMeters +
                    (current.accumulatedDistanceMeters - previous.accumulatedDistanceMeters) * ratio
                speedMilestones += SpeedMilestone(
                    id = definition.id,
                    displayName = definition.displayName,
                    startSpeedKmh = definition.startSpeedKmh,
                    targetSpeedKmh = definition.targetSpeedKmh,
                    elapsedMs = (crossing - milestoneStart).coerceAtLeast(0L),
                    distanceAtTargetMeters = distance,
                    crossingTimeMs = crossing,
                )
            }
        }
    }

    private fun detectDistanceSplits(previous: PerformanceSample, current: PerformanceSample) {
        if (!config.requiresGps) return
        val targetMax = config.selectedDistanceTarget.meters
        TimeSlipConstants.orderedDistanceTargets
            .filter { it.meters <= targetMax }
            .forEach { target ->
                if (distanceSplits.any { it.target == target }) return@forEach
                if (previous.accumulatedDistanceMeters < target.meters && current.accumulatedDistanceMeters >= target.meters) {
                    val crossing = TimeSlipMath.interpolateDistanceCrossingTime(
                        previous.accumulatedDistanceMeters,
                        current.accumulatedDistanceMeters,
                        target.meters,
                        previous.monotonicTimeMs,
                        current.monotonicTimeMs,
                    )
                    val speed = TimeSlipMath.interpolateValue(
                        previous.fusedSpeedKmh,
                        current.fusedSpeedKmh,
                        previous.monotonicTimeMs,
                        current.monotonicTimeMs,
                        crossing,
                    )
                    val start = startMonotonicMs ?: crossing
                    val elapsed = (crossing - start).coerceAtLeast(0L)
                    val previousElapsed = distanceSplits.lastOrNull()?.elapsedMs ?: 0L
                    distanceSplits += DistanceSplit(
                        target = target,
                        targetDistanceMeters = target.meters,
                        elapsedMs = elapsed,
                        splitMs = elapsed - previousElapsed,
                        trapSpeedKmh = speed,
                        crossingTimeMs = crossing,
                        latitude = current.latitude,
                        longitude = current.longitude,
                    )
                }
            }
    }

    fun cancel(reason: String = "ผู้ใช้ยกเลิกการทดสอบ"): TimeSlipEngineSnapshot {
        if (status in activeStatuses) {
            status = TimeSlipStatus.CANCELLED
            invalidReason = reason
            completedMonotonicMs = previousSample?.monotonicTimeMs
            result = buildRecord(status)
        }
        return snapshot()
    }

    fun connectionLost(): TimeSlipEngineSnapshot {
        connectionInterruptions += 1
        return if (status in activeStatuses) invalidate("การเชื่อมต่อ OBD-II ขาดระหว่างทดสอบ", TimeSlipStatus.CONNECTION_LOST)
        else snapshot()
    }

    fun gpsUnreliable(): TimeSlipEngineSnapshot {
        gpsInterruptions += 1
        return if (status in activeStatuses && config.requiresGps) {
            invalidate("สัญญาณ GPS ไม่เพียงพอสำหรับวัดระยะทาง", TimeSlipStatus.GPS_UNRELIABLE)
        } else snapshot()
    }

    fun reset(): TimeSlipEngineSnapshot {
        resetInternal()
        return snapshot()
    }

    private fun complete(completedAtMs: Long) {
        if (status != TimeSlipStatus.RUNNING && status != TimeSlipStatus.LAUNCH_DETECTED) return
        completedMonotonicMs = completedAtMs
        status = TimeSlipStatus.COMPLETED
        result = buildRecord(status)
    }

    private fun invalidate(reason: String, terminalStatus: TimeSlipStatus): TimeSlipEngineSnapshot {
        invalidReason = reason
        status = terminalStatus
        completedMonotonicMs = previousSample?.monotonicTimeMs
        result = buildRecord(status)
        return snapshot()
    }

    private fun buildRecord(terminalStatus: TimeSlipStatus): TimeSlipRecord {
        val start = startMonotonicMs ?: requestedAtMonotonicMs
        val end = completedMonotonicMs ?: previousSample?.monotonicTimeMs ?: start
        val disagreement = if (sensorDisagreementCount == 0) 0.0 else sensorDisagreementSum / sensorDisagreementCount
        val qualityResult = MeasurementQualityCalculator.calculate(
            requiresGps = config.requiresGps,
            obdRateHz = obdRateHz,
            gpsRateHz = gpsRateHz,
            averageGpsAccuracyMeters = averageGpsAccuracyMeters,
            sampleCount = samples.size,
            droppedSampleCount = droppedSampleCount,
            sensorDisagreementKmh = disagreement,
            connectionInterruptions = connectionInterruptions,
            gpsInterruptions = gpsInterruptions,
            maximumGapMs = maximumGapMs,
            distanceCorrectionMeters = distanceCorrectionMeters,
            totalDistanceMeters = accumulatedDistanceMeters,
            terminalStatus = terminalStatus,
        )
        val dataSource = when {
            samples.any { it.obdValid } && samples.any { it.gpsValid } && samples.any { it.accelerationMs2 != null } ->
                TimeSlipDataSource.OBD_GPS_ACCELEROMETER
            samples.any { it.obdValid } && samples.any { it.gpsValid } -> TimeSlipDataSource.OBD_GPS
            samples.any { it.obdValid } -> TimeSlipDataSource.OBD
            else -> TimeSlipDataSource.GPS
        }
        return TimeSlipRecord(
            id = UUID.randomUUID().toString(),
            status = terminalStatus,
            testMode = config.testMode,
            selectedDistanceTarget = config.selectedDistanceTarget.takeUnless { it == DistanceTarget.SPEED_ONLY },
            startedAtEpochMs = startedAtEpochMs ?: requestedAtEpochMs,
            completedAtEpochMs = (startedAtEpochMs ?: requestedAtEpochMs) + (end - start).coerceAtLeast(0L),
            startMonotonicTimeMs = start,
            completedMonotonicTimeMs = end,
            speedMilestones = speedMilestones.toList(),
            distanceSplits = distanceSplits.toList(),
            totalDistanceMeters = accumulatedDistanceMeters,
            elapsedMs = (end - start).coerceAtLeast(0L),
            maximumSpeedKmh = maximumSpeedKmh,
            maximumAccelerationMs2 = maximumAccelerationMs2,
            dataSource = dataSource,
            obdDeviceName = obdDeviceName,
            obdSampleRateHz = obdRateHz.takeIf { it > 0.0 },
            gpsSampleRateHz = gpsRateHz.takeIf { it > 0.0 },
            averageGpsAccuracyMeters = averageGpsAccuracyMeters,
            sampleCount = samples.size,
            droppedSampleCount = droppedSampleCount,
            connectionInterruptions = connectionInterruptions,
            gpsInterruptions = gpsInterruptions,
            measurementQuality = qualityResult.quality,
            estimatedTimingErrorMs = qualityResult.estimatedTimingErrorMs,
            vehicleName = config.vehicleName.ifBlank { null },
            notes = invalidReason,
            distanceCorrectionMeters = distanceCorrectionMeters,
            samples = samples.toList(),
        )
    }

    private fun appendSample(sample: PerformanceSample) {
        val last = samples.lastOrNull()
        if (last == null || sample.monotonicTimeMs - last.monotonicTimeMs >= 50L) {
            if (samples.size >= TimeSlipConstants.MAX_PERSISTED_SAMPLES) {
                // Keep bounded memory by retaining every second older sample.
                val compacted = samples.filterIndexed { index, _ -> index % 2 == 0 }
                samples.clear()
                samples.addAll(compacted)
                droppedSampleCount += compacted.size
            }
            samples += sample
        }
    }

    private fun nextDistanceTarget(): DistanceTarget? {
        if (!config.requiresGps) return null
        return TimeSlipConstants.orderedDistanceTargets
            .filter { it.meters <= config.selectedDistanceTarget.meters }
            .firstOrNull { candidate -> distanceSplits.none { it.target == candidate } }
    }

    private fun provisionalQuality(): MeasurementQuality = when {
        status in terminalInvalidStatuses -> MeasurementQuality.INVALID
        config.requiresGps && (averageGpsAccuracyMeters == null || averageGpsAccuracyMeters!! > 10.0) -> MeasurementQuality.LOW
        obdRateHz >= 8.0 && (!config.requiresGps || gpsRateHz >= 5.0) -> MeasurementQuality.HIGH
        obdRateHz >= 4.0 -> MeasurementQuality.MEDIUM
        else -> MeasurementQuality.LOW
    }

    private fun resetInternal() {
        status = TimeSlipStatus.IDLE
        requestedAtEpochMs = 0L
        requestedAtMonotonicMs = 0L
        stationarySinceMs = null
        stationaryReady = false
        startMonotonicMs = null
        startedAtEpochMs = null
        completedMonotonicMs = null
        previousSample = null
        accumulatedDistanceMeters = 0.0
        distanceAtLastGpsAnchor = 0.0
        lastGpsAnchorTimeMs = null
        distanceCorrectionMeters = 0.0
        maximumSpeedKmh = 0.0
        maximumAccelerationMs2 = null
        maximumGapMs = 0L
        droppedSampleCount = 0
        connectionInterruptions = 0
        gpsInterruptions = 0
        obdRateHz = 0.0
        gpsRateHz = 0.0
        averageGpsAccuracyMeters = null
        obdDeviceName = null
        sensorDisagreementSum = 0.0
        sensorDisagreementCount = 0
        invalidReason = null
        result = null
        speedMilestones.clear()
        distanceSplits.clear()
        milestoneStartTimes.clear()
        samples.clear()
    }

    companion object {
        val activeStatuses = setOf(
            TimeSlipStatus.WAITING_FOR_CONNECTION,
            TimeSlipStatus.WAITING_FOR_GPS,
            TimeSlipStatus.ARMED,
            TimeSlipStatus.LAUNCH_DETECTED,
            TimeSlipStatus.RUNNING,
        )
        val terminalInvalidStatuses = setOf(
            TimeSlipStatus.CANCELLED,
            TimeSlipStatus.CONNECTION_LOST,
            TimeSlipStatus.GPS_UNRELIABLE,
            TimeSlipStatus.INVALID_RUN,
        )
    }
}
