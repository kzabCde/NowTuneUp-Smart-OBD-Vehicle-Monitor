package com.nowtuneup.app.feature.timeslip

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Deterministic OBD-only Time Slip engine using monotonic timestamps and interpolated crossings. */
class TimeSlipEngine {
    private data class SpeedSample(
        val timeNanos: Long,
        val speedKmh: Double,
        val telemetry: TimeSlipTelemetrySample,
    )

    private var status = TimeSlipStatus.IDLE
    private var config = TimeSlipConfig()
    private var armedAtNanos = 0L
    private var armedAtEpochMillis = 0L
    private var launchNanos: Long? = null
    private var timingStartNanos: Long? = null
    private var stationaryReadyAtNanos: Long? = null
    private var previousSample: SpeedSample? = null
    private var previousRunSample: SpeedSample? = null
    private var stationarySinceNanos: Long? = null
    private var stationaryReady = false
    private var currentSpeedKmh = 0.0
    private var maximumSpeedKmh = 0.0
    private var maximumAccelerationMps2 = 0.0
    private var distanceMeters = 0.0
    private var sampleCount = 0
    private var droppedSampleCount = 0
    private var reactionTimeMillis = 0L
    private var rolloutMillis = 0L
    private var speedMilestones = mutableListOf<SpeedMilestoneResult>()
    private var distanceSplits = mutableListOf<DistanceSplitResult>()
    private var rawSamples = mutableListOf<TimeSlipTelemetrySample>()
    private var record: TimeSlipRecord? = null
    private var message: String? = null

    fun arm(
        requestedConfig: TimeSlipConfig,
        currentSpeedKmh: Double,
        nowNanos: Long = System.nanoTime(),
        wallClockMillis: Long = System.currentTimeMillis(),
    ): TimeSlipSnapshot {
        if (status == TimeSlipStatus.ARMED || status == TimeSlipStatus.RUNNING) {
            message = "มีการทดสอบที่กำลังทำงานอยู่แล้ว"
            return snapshot()
        }
        resetInternal(requestedConfig.copy(useSensorFusion = false))
        if (!currentSpeedKmh.isFinite() || currentSpeedKmh !in 0.0..400.0) return invalidate("ข้อมูลความเร็วเริ่มต้นไม่ถูกต้อง")
        if (requestedConfig.mode == PerformanceMode.ROLLING_START && requestedConfig.rollingTargetKmh <= requestedConfig.rollingStartKmh) {
            return invalidate("ความเร็วเป้าหมายต้องมากกว่าความเร็วเริ่มต้น")
        }
        if (requestedConfig.mode == PerformanceMode.STANDING_START && currentSpeedKmh > requestedConfig.stationaryThresholdKmh) {
            return invalidate("รถต้องหยุดนิ่งก่อน Arm Test")
        }
        if (requestedConfig.mode == PerformanceMode.ROLLING_START && currentSpeedKmh >= requestedConfig.rollingStartKmh) {
            return invalidate("ความเร็วปัจจุบันต้องต่ำกว่าจุดเริ่ม Rolling Start")
        }
        status = TimeSlipStatus.ARMED
        armedAtNanos = nowNanos
        armedAtEpochMillis = wallClockMillis
        this.currentSpeedKmh = currentSpeedKmh.coerceAtLeast(0.0)
        val initialTelemetry = TimeSlipTelemetrySample(
            timeNanos = nowNanos,
            wallClockMillis = wallClockMillis,
            obdSpeedKmh = currentSpeedKmh,
            fusedSpeedKmh = currentSpeedKmh,
            source = MeasurementSource.OBD_ONLY,
        )
        previousSample = SpeedSample(nowNanos, currentSpeedKmh, initialTelemetry)
        stationarySinceNanos = if (currentSpeedKmh <= requestedConfig.stationaryThresholdKmh) nowNanos else null
        message = if (requestedConfig.mode == PerformanceMode.STANDING_START) {
            "รักษารถให้นิ่งอย่างน้อย 1 วินาที แล้วออกตัวเมื่อพร้อม"
        } else {
            "เร่งผ่าน ${requestedConfig.rollingStartKmh.toInt()} km/h เพื่อเริ่มจับเวลา"
        }
        return snapshot()
    }

    fun ingestSpeed(speedKmh: Double, timeNanos: Long = System.nanoTime()): TimeSlipSnapshot = ingestTelemetry(
        TimeSlipTelemetrySample(
            timeNanos = timeNanos,
            wallClockMillis = System.currentTimeMillis(),
            obdSpeedKmh = speedKmh,
            fusedSpeedKmh = speedKmh,
            source = MeasurementSource.OBD_ONLY,
        ),
    )

    fun ingestTelemetry(telemetry: TimeSlipTelemetrySample): TimeSlipSnapshot {
        val speedKmh = telemetry.obdSpeedKmh
        currentSpeedKmh = speedKmh
        if (status != TimeSlipStatus.ARMED && status != TimeSlipStatus.RUNNING) return snapshot()
        if (!speedKmh.isFinite() || speedKmh !in 0.0..400.0 || telemetry.timeNanos <= 0L) {
            droppedSampleCount += 1
            return invalidate("ได้รับข้อมูลความเร็วที่เป็นไปไม่ได้")
        }
        val previous = previousSample
        if (previous != null && telemetry.timeNanos <= previous.timeNanos) {
            droppedSampleCount += 1
            message = "ข้ามตัวอย่างที่ timestamp ไม่ต่อเนื่อง"
            return snapshot()
        }
        if (previous != null && telemetry.timeNanos - previous.timeNanos > MAX_SAMPLE_GAP_NANOS) droppedSampleCount += 1
        if (
            previous != null && status == TimeSlipStatus.ARMED && config.mode == PerformanceMode.STANDING_START &&
            previous.speedKmh <= config.stationaryThresholdKmh && speedKmh - previous.speedKmh >= MAX_LAUNCH_JUMP_KMH &&
            telemetry.timeNanos - previous.timeNanos <= MAX_LAUNCH_JUMP_WINDOW_NANOS
        ) {
            droppedSampleCount += 1
            return invalidate("ความเร็วกระโดดผิดปกติระหว่างออกตัว กรุณาทดสอบใหม่")
        }
        val obdTelemetry = telemetry.copy(fusedSpeedKmh = speedKmh, source = MeasurementSource.OBD_ONLY)
        val current = SpeedSample(obdTelemetry.timeNanos, speedKmh, obdTelemetry)
        sampleCount += 1
        maximumSpeedKmh = max(maximumSpeedKmh, speedKmh)
        maximumAccelerationMps2 = max(maximumAccelerationMps2, telemetry.accelerationMps2)
        if (rawSamples.size < MAX_RAW_SAMPLES) rawSamples += obdTelemetry
        if (status == TimeSlipStatus.ARMED && previous != null) detectStart(previous, current)
        if (status == TimeSlipStatus.RUNNING) processRunningSample(current)
        previousSample = current
        return snapshot()
    }

    fun cancel(): TimeSlipSnapshot {
        if (status == TimeSlipStatus.ARMED || status == TimeSlipStatus.RUNNING) {
            status = TimeSlipStatus.CANCELLED
            message = "ยกเลิกการทดสอบแล้ว"
        }
        return snapshot()
    }

    fun connectionLost(): TimeSlipSnapshot {
        if (status == TimeSlipStatus.ARMED || status == TimeSlipStatus.RUNNING) {
            status = TimeSlipStatus.CONNECTION_LOST
            message = "การเชื่อมต่อ OBD-II หลุด ผลการทดสอบนี้ไม่ถูกบันทึก"
        }
        return snapshot()
    }

    fun reset(): TimeSlipSnapshot {
        resetInternal(TimeSlipConfig())
        return snapshot()
    }

    fun snapshot(): TimeSlipSnapshot = TimeSlipSnapshot(
        status = status,
        config = config,
        currentSpeedKmh = currentSpeedKmh,
        elapsedMillis = elapsedMillis(),
        distanceMeters = distanceMeters,
        maximumSpeedKmh = maximumSpeedKmh,
        speedMilestones = speedMilestones.toList(),
        distanceSplits = distanceSplits.toList(),
        sampleCount = sampleCount,
        droppedSampleCount = droppedSampleCount,
        reactionTimeMillis = reactionTimeMillis,
        message = message,
        record = record,
    )

    private fun detectStart(previous: SpeedSample, current: SpeedSample) {
        when (config.mode) {
            PerformanceMode.STANDING_START -> {
                if (current.speedKmh <= config.stationaryThresholdKmh) {
                    val stationarySince = stationarySinceNanos ?: current.timeNanos.also { stationarySinceNanos = it }
                    if (current.timeNanos - stationarySince >= config.stationaryHoldMillis * NANOS_PER_MILLI) {
                        stationaryReady = true
                        if (stationaryReadyAtNanos == null) stationaryReadyAtNanos = current.timeNanos
                        message = "พร้อมออกตัว"
                    }
                    return
                }
                if (!stationaryReady && current.speedKmh >= config.launchThresholdKmh) {
                    invalidate("รถเคลื่อนที่ก่อนผ่านช่วงหยุดนิ่ง 1 วินาที")
                    return
                }
                if (stationaryReady && previous.speedKmh < config.launchThresholdKmh && current.speedKmh >= config.launchThresholdKmh) {
                    val launch = previous.timeNanos
                    reactionTimeMillis = stationaryReadyAtNanos?.let { ((launch - it) / NANOS_PER_MILLI).coerceAtLeast(0L) } ?: 0L
                    startRun(launch, 0.0, current)
                }
            }
            PerformanceMode.ROLLING_START -> {
                if (previous.speedKmh < config.rollingStartKmh && current.speedKmh >= config.rollingStartKmh) {
                    val crossing = interpolateSpeedCrossingTimeNanos(
                        previous.speedKmh, current.speedKmh, config.rollingStartKmh, previous.timeNanos, current.timeNanos,
                    )
                    startRun(crossing, config.rollingStartKmh, current)
                }
            }
        }
    }

    private fun startRun(crossingNanos: Long, crossingSpeedKmh: Double, current: SpeedSample) {
        launchNanos = crossingNanos
        timingStartNanos = if (config.oneFootRollout && config.mode == PerformanceMode.STANDING_START) null else crossingNanos
        previousRunSample = SpeedSample(
            crossingNanos,
            crossingSpeedKmh,
            current.telemetry.copy(timeNanos = crossingNanos, obdSpeedKmh = crossingSpeedKmh, fusedSpeedKmh = crossingSpeedKmh),
        )
        status = TimeSlipStatus.RUNNING
        message = if (timingStartNanos == null) "กำลังวัด One-foot rollout" else "กำลังจับเวลา"
        if (current.timeNanos > crossingNanos) processRunningSample(current)
    }

    private fun processRunningSample(current: SpeedSample) {
        val launch = launchNanos ?: return
        val previous = previousRunSample ?: return
        if (current.timeNanos <= previous.timeNanos) return
        val previousDistance = distanceMeters
        val deltaSeconds = (current.timeNanos - previous.timeNanos) / NANOS_PER_SECOND.toDouble()
        distanceMeters += integrateDistanceMeters(previous.speedKmh / 3.6, current.speedKmh / 3.6, deltaSeconds)
        if (timingStartNanos == null && distanceMeters >= ONE_FOOT_METERS) {
            val crossing = interpolateDistanceCrossingTimeNanos(
                previousDistance, distanceMeters, ONE_FOOT_METERS, previous.timeNanos, current.timeNanos,
            )
            timingStartNanos = crossing
            rolloutMillis = ((crossing - launch) / NANOS_PER_MILLI).coerceAtLeast(0L)
            message = "One-foot rollout ผ่านแล้ว • กำลังจับเวลา"
        }
        val speedCompletion = detectSpeedMilestones(previous, current, previousDistance, distanceMeters)
        val distanceCompletion = detectDistanceSplits(previous, current, previousDistance, distanceMeters)
        previousRunSample = current
        val completionNanos = if (config.selectedDistanceTarget != null) distanceCompletion else speedCompletion
        if (completionNanos != null && timingStartNanos != null) complete(completionNanos)
    }

    private fun detectSpeedMilestones(
        previous: SpeedSample,
        current: SpeedSample,
        previousDistance: Double,
        currentDistance: Double,
    ): Long? {
        val targets = if (config.mode == PerformanceMode.ROLLING_START) listOf(config.rollingTargetKmh)
        else (config.enabledMilestonesKmh + config.speedOnlyTargetKmh).distinct().sorted()
        var completion: Long? = null
        targets.forEach { target ->
            if (speedMilestones.any { abs(it.targetSpeedKmh - target) < 0.001 }) return@forEach
            if (previous.speedKmh < target && current.speedKmh >= target) {
                val crossing = interpolateSpeedCrossingTimeNanos(
                    previous.speedKmh, current.speedKmh, target, previous.timeNanos, current.timeNanos,
                )
                val ratio = ((crossing - previous.timeNanos).toDouble() / (current.timeNanos - previous.timeNanos).toDouble()).coerceIn(0.0, 1.0)
                val crossingDistance = previousDistance + (currentDistance - previousDistance) * ratio
                val startSpeed = if (config.mode == PerformanceMode.ROLLING_START) config.rollingStartKmh else 0.0
                speedMilestones += SpeedMilestoneResult(
                    label = speedLabel(startSpeed, target),
                    startSpeedKmh = startSpeed,
                    targetSpeedKmh = target,
                    elapsedMillis = elapsedFromTimingStart(crossing),
                    distanceAtTargetMeters = crossingDistance,
                )
                if (config.selectedDistanceTarget == null && abs(target - completionSpeedTarget()) < 0.001) completion = crossing
            }
        }
        return completion
    }

    private fun detectDistanceSplits(
        previous: SpeedSample,
        current: SpeedSample,
        previousDistance: Double,
        currentDistance: Double,
    ): Long? {
        val selected = config.selectedDistanceTarget ?: return null
        var completion: Long? = null
        DistanceTarget.entries.filter { it.meters <= selected.meters }.forEach { target ->
            if (distanceSplits.any { it.target == target }) return@forEach
            if (previousDistance < target.meters && currentDistance >= target.meters) {
                val crossing = interpolateDistanceCrossingTimeNanos(
                    previousDistance, currentDistance, target.meters, previous.timeNanos, current.timeNanos,
                )
                val ratio = ((crossing - previous.timeNanos).toDouble() / (current.timeNanos - previous.timeNanos).toDouble()).coerceIn(0.0, 1.0)
                val trapSpeed = previous.speedKmh + (current.speedKmh - previous.speedKmh) * ratio
                val elapsed = elapsedFromTimingStart(crossing)
                val priorElapsed = distanceSplits.lastOrNull()?.elapsedMillis ?: 0L
                distanceSplits += DistanceSplitResult(target, elapsed, elapsed - priorElapsed, trapSpeed)
                if (target == selected) completion = crossing
            }
        }
        return completion
    }

    private fun complete(completionNanos: Long) {
        if (status != TimeSlipStatus.RUNNING) return
        val start = timingStartNanos ?: return
        status = TimeSlipStatus.COMPLETED
        message = "บันทึกผล Performance สำเร็จ"
        val elapsed = ((completionNanos - start) / NANOS_PER_MILLI).coerceAtLeast(0L)
        val rate = calculateSampleRate(elapsed + rolloutMillis)
        val averageLatency = rawSamples.map { it.transportLatencyMillis.coerceAtLeast(0L) }
            .takeIf { it.isNotEmpty() }?.average()?.roundToLong() ?: 0L
        val totalObserved = sampleCount + droppedSampleCount
        val invalidRatio = if (totalObserved > 0) droppedSampleCount.toDouble() / totalObserved else 1.0
        val score = calculateQualityScore(rate, averageLatency, invalidRatio, config.selectedDistanceTarget != null)
        val quality = qualityFromScore(score, sampleCount, rate)
        val speedConfidence = confidenceFromQuality(quality)
        val distanceConfidence = if (config.selectedDistanceTarget == null) speedConfidence else when {
            quality == MeasurementQuality.HIGH -> ConfidenceLevel.MEDIUM
            quality == MeasurementQuality.MEDIUM -> ConfidenceLevel.MEDIUM
            quality == MeasurementQuality.LOW -> ConfidenceLevel.LOW
            else -> ConfidenceLevel.INVALID
        }
        val startedAt = armedAtEpochMillis + (start - armedAtNanos) / NANOS_PER_MILLI
        val completedAt = armedAtEpochMillis + (completionNanos - armedAtNanos) / NANOS_PER_MILLI
        val notes = buildList {
            add("OBD sample rate ${"%.1f".format(rate)} Hz")
            add("Average transport latency $averageLatency ms")
            add("Dropped $droppedSampleCount/$totalObserved samples")
            if (config.selectedDistanceTarget != null) add("Distance is integrated from OBD vehicle speed")
        }
        record = TimeSlipRecord(
            mode = config.mode,
            selectedDistanceTarget = config.selectedDistanceTarget,
            startedAtEpochMillis = startedAt,
            completedAtEpochMillis = completedAt,
            elapsedMillis = elapsed,
            speedMilestones = speedMilestones.sortedBy { it.targetSpeedKmh },
            distanceSplits = distanceSplits.sortedBy { it.target.meters },
            totalDistanceMeters = config.selectedDistanceTarget?.meters ?: distanceMeters,
            maximumSpeedKmh = maximumSpeedKmh,
            sampleCount = sampleCount,
            droppedSampleCount = droppedSampleCount,
            obdSampleRateHz = rate,
            measurementQuality = quality,
            qualityScore = score,
            estimatedTimingErrorMillis = estimateTimingErrorMillis(rate, averageLatency),
            averageTransportLatencyMillis = averageLatency,
            invalidSampleRatio = invalidRatio,
            validityNotes = notes,
            launchDetectedAutomatically = true,
            dataSource = "OBD-II PID 010D · interpolated crossings",
            distanceEstimated = true,
            reactionTimeMillis = reactionTimeMillis,
            rolloutMillis = rolloutMillis,
            oneFootRolloutEnabled = config.oneFootRollout,
            speedConfidence = speedConfidence,
            distanceConfidence = distanceConfidence,
            gpsSampleCount = 0,
            averageGpsAccuracyMeters = null,
            averageSlopePercent = null,
            maximumAccelerationMps2 = maximumAccelerationMps2,
            vehicleProfileId = config.vehicleProfileId,
            rawSamples = rawSamples.toList(),
        )
    }

    private fun calculateQualityScore(rateHz: Double, latencyMillis: Long, invalidRatio: Double, distanceRun: Boolean): Int {
        if (rateHz <= 0.0) return 0
        var score = 100
        score -= when {
            rateHz >= 8.0 -> 0
            rateHz >= 5.0 -> 8
            rateHz >= 3.0 -> 18
            rateHz >= 2.0 -> 30
            else -> 45
        }
        score -= when {
            latencyMillis <= 0L -> 5
            latencyMillis <= 120L -> 0
            latencyMillis <= 250L -> 8
            latencyMillis <= 450L -> 18
            else -> 30
        }
        score -= when {
            invalidRatio <= 0.02 -> 0
            invalidRatio <= 0.08 -> 10
            invalidRatio <= 0.15 -> 18
            else -> 30
        }
        if (sampleCount < 8) score -= 10
        if (distanceRun) score -= 15
        return score.coerceIn(0, 100)
    }

    private fun qualityFromScore(score: Int, samples: Int, rateHz: Double): MeasurementQuality = when {
        samples < 3 || rateHz <= 0.0 -> MeasurementQuality.INVALID
        score >= 85 -> MeasurementQuality.HIGH
        score >= 65 -> MeasurementQuality.MEDIUM
        else -> MeasurementQuality.LOW
    }

    private fun confidenceFromQuality(quality: MeasurementQuality): ConfidenceLevel = when (quality) {
        MeasurementQuality.HIGH -> ConfidenceLevel.HIGH
        MeasurementQuality.MEDIUM -> ConfidenceLevel.MEDIUM
        MeasurementQuality.LOW -> ConfidenceLevel.LOW
        MeasurementQuality.INVALID -> ConfidenceLevel.INVALID
    }

    private fun estimateTimingErrorMillis(rateHz: Double, averageLatencyMillis: Long): Long {
        if (rateHz <= 0.0) return 1_000L
        val halfSamplePeriod = (500.0 / rateHz).roundToLong()
        return max(20L, halfSamplePeriod + averageLatencyMillis / 2 + droppedSampleCount * 100L)
    }

    private fun completionSpeedTarget(): Double = if (config.mode == PerformanceMode.ROLLING_START) config.rollingTargetKmh else config.speedOnlyTargetKmh
    private fun calculateSampleRate(elapsedMillis: Long): Double = if (elapsedMillis <= 0L || sampleCount <= 1) 0.0 else (sampleCount - 1) * 1_000.0 / elapsedMillis
    private fun elapsedFromTimingStart(timeNanos: Long): Long = timingStartNanos?.let { ((timeNanos - it) / NANOS_PER_MILLI).coerceAtLeast(0L) } ?: 0L
    private fun elapsedMillis(): Long {
        val start = timingStartNanos ?: return 0L
        val end = record?.let { start + it.elapsedMillis * NANOS_PER_MILLI } ?: previousSample?.timeNanos ?: start
        return ((end - start) / NANOS_PER_MILLI).coerceAtLeast(0L)
    }

    private fun invalidate(reason: String): TimeSlipSnapshot {
        status = TimeSlipStatus.INVALID_RUN
        message = reason
        record = null
        return snapshot()
    }

    private fun resetInternal(requestedConfig: TimeSlipConfig) {
        status = TimeSlipStatus.IDLE
        config = requestedConfig
        armedAtNanos = 0L
        armedAtEpochMillis = 0L
        launchNanos = null
        timingStartNanos = null
        stationaryReadyAtNanos = null
        previousSample = null
        previousRunSample = null
        stationarySinceNanos = null
        stationaryReady = false
        currentSpeedKmh = 0.0
        maximumSpeedKmh = 0.0
        maximumAccelerationMps2 = 0.0
        distanceMeters = 0.0
        sampleCount = 0
        droppedSampleCount = 0
        reactionTimeMillis = 0L
        rolloutMillis = 0L
        speedMilestones = mutableListOf()
        distanceSplits = mutableListOf()
        rawSamples = mutableListOf()
        record = null
        message = null
    }

    companion object {
        private const val NANOS_PER_SECOND = 1_000_000_000L
        private const val NANOS_PER_MILLI = 1_000_000L
        private const val MAX_SAMPLE_GAP_NANOS = 1_500_000_000L
        private const val MAX_LAUNCH_JUMP_KMH = 25.0
        private const val MAX_LAUNCH_JUMP_WINDOW_NANOS = 200_000_000L
        private const val ONE_FOOT_METERS = 0.3048
        private const val MAX_RAW_SAMPLES = 4_000

        fun interpolateSpeedCrossingTimeNanos(
            previousSpeedKmh: Double,
            currentSpeedKmh: Double,
            targetSpeedKmh: Double,
            previousTimeNanos: Long,
            currentTimeNanos: Long,
        ): Long {
            if (currentSpeedKmh <= previousSpeedKmh) return currentTimeNanos
            val ratio = ((targetSpeedKmh - previousSpeedKmh) / (currentSpeedKmh - previousSpeedKmh)).coerceIn(0.0, 1.0)
            return previousTimeNanos + ((currentTimeNanos - previousTimeNanos) * ratio).roundToLong()
        }

        fun interpolateDistanceCrossingTimeNanos(
            previousDistanceMeters: Double,
            currentDistanceMeters: Double,
            targetDistanceMeters: Double,
            previousTimeNanos: Long,
            currentTimeNanos: Long,
        ): Long {
            if (currentDistanceMeters <= previousDistanceMeters) return currentTimeNanos
            val ratio = ((targetDistanceMeters - previousDistanceMeters) / (currentDistanceMeters - previousDistanceMeters)).coerceIn(0.0, 1.0)
            return previousTimeNanos + ((currentTimeNanos - previousTimeNanos) * ratio).roundToLong()
        }

        fun integrateDistanceMeters(previousSpeedMps: Double, currentSpeedMps: Double, deltaTimeSeconds: Double): Double =
            ((previousSpeedMps + currentSpeedMps) / 2.0) * deltaTimeSeconds.coerceAtLeast(0.0)

        fun replay(record: TimeSlipRecord): TimeSlipRecord? {
            val samples = record.rawSamples.orEmpty()
            if (samples.size < 2) return null
            val first = samples.first()
            val engine = TimeSlipEngine()
            engine.arm(
                requestedConfig = TimeSlipConfig(
                    mode = record.mode,
                    selectedDistanceTarget = record.selectedDistanceTarget,
                    speedOnlyTargetKmh = record.speedMilestones.maxOfOrNull { it.targetSpeedKmh } ?: 100.0,
                    vehicleProfileId = record.vehicleProfileId ?: "default",
                ),
                currentSpeedKmh = first.obdSpeedKmh,
                nowNanos = first.timeNanos,
                wallClockMillis = first.wallClockMillis,
            )
            samples.drop(1).forEach { engine.ingestTelemetry(it.copy(source = MeasurementSource.REPLAY)) }
            return engine.snapshot().record
        }

        private fun speedLabel(start: Double, target: Double): String {
            val startLabel = if (abs(start - 96.56064) < 0.01) "60 mph" else "${start.toInt()}"
            val targetLabel = if (abs(target - 96.56064) < 0.01) "60 mph" else "${target.toInt()} km/h"
            return "$startLabel–$targetLabel"
        }
    }
}
