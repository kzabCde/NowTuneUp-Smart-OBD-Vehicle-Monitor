package com.nowtuneup.app.feature.timeslip

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Standalone monotonic-clock performance engine.
 *
 * The engine consumes only timestamped vehicle-speed samples, so it can be unit tested or replayed
 * without Compose, Bluetooth, an ELM327 adapter, or a physical vehicle. Distance values are
 * trapezoidal OBD-speed estimates and are deliberately marked low-confidence until GPS fusion is
 * added.
 */
class TimeSlipEngine {
    private data class SpeedSample(val timeNanos: Long, val speedKmh: Double)

    private var status = TimeSlipStatus.IDLE
    private var config = TimeSlipConfig()
    private var armedAtNanos = 0L
    private var armedAtEpochMillis = 0L
    private var startNanos: Long? = null
    private var previousSample: SpeedSample? = null
    private var previousRunSample: SpeedSample? = null
    private var stationarySinceNanos: Long? = null
    private var stationaryReady = false
    private var currentSpeedKmh = 0.0
    private var maximumSpeedKmh = 0.0
    private var distanceMeters = 0.0
    private var sampleCount = 0
    private var droppedSampleCount = 0
    private var speedMilestones = mutableListOf<SpeedMilestoneResult>()
    private var distanceSplits = mutableListOf<DistanceSplitResult>()
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
        resetInternal(requestedConfig)

        if (!currentSpeedKmh.isFinite() || currentSpeedKmh !in 0.0..400.0) {
            return invalidate("ข้อมูลความเร็วเริ่มต้นไม่ถูกต้อง")
        }
        if (
            requestedConfig.mode == PerformanceMode.ROLLING_START &&
            requestedConfig.rollingTargetKmh <= requestedConfig.rollingStartKmh
        ) {
            return invalidate("ความเร็วเป้าหมายต้องมากกว่าความเร็วเริ่มต้น")
        }
        if (
            requestedConfig.mode == PerformanceMode.STANDING_START &&
            currentSpeedKmh > requestedConfig.stationaryThresholdKmh
        ) {
            return invalidate("รถต้องหยุดนิ่งก่อน Arm Test")
        }
        if (
            requestedConfig.mode == PerformanceMode.ROLLING_START &&
            currentSpeedKmh >= requestedConfig.rollingStartKmh
        ) {
            return invalidate("ความเร็วปัจจุบันต้องต่ำกว่าจุดเริ่ม Rolling Start")
        }

        status = TimeSlipStatus.ARMED
        armedAtNanos = nowNanos
        armedAtEpochMillis = wallClockMillis
        this.currentSpeedKmh = currentSpeedKmh
        previousSample = SpeedSample(nowNanos, currentSpeedKmh)
        stationarySinceNanos = if (currentSpeedKmh <= requestedConfig.stationaryThresholdKmh) nowNanos else null
        message = if (requestedConfig.mode == PerformanceMode.STANDING_START) {
            "รักษารถให้นิ่งอย่างน้อย 1 วินาที แล้วออกตัวเมื่อพร้อม"
        } else {
            "เร่งผ่าน ${requestedConfig.rollingStartKmh.toInt()} km/h เพื่อเริ่มจับเวลา"
        }
        return snapshot()
    }

    fun ingestSpeed(speedKmh: Double, timeNanos: Long = System.nanoTime()): TimeSlipSnapshot {
        currentSpeedKmh = speedKmh
        if (status != TimeSlipStatus.ARMED && status != TimeSlipStatus.RUNNING) return snapshot()
        if (!speedKmh.isFinite() || speedKmh !in 0.0..400.0) {
            droppedSampleCount += 1
            return invalidate("ได้รับข้อมูลความเร็วที่เป็นไปไม่ได้")
        }

        val previous = previousSample
        if (previous != null && timeNanos <= previous.timeNanos) {
            droppedSampleCount += 1
            message = "ข้ามตัวอย่างที่ timestamp ไม่ต่อเนื่อง"
            return snapshot()
        }
        if (previous != null && timeNanos - previous.timeNanos > MAX_SAMPLE_GAP_NANOS) {
            droppedSampleCount += 1
        }

        val current = SpeedSample(timeNanos, speedKmh)
        sampleCount += 1
        maximumSpeedKmh = max(maximumSpeedKmh, speedKmh)

        if (status == TimeSlipStatus.ARMED && previous != null) {
            detectStart(previous, current)
        }
        if (status == TimeSlipStatus.RUNNING) {
            processRunningSample(current)
        }

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
        message = message,
        record = record,
    )

    private fun detectStart(previous: SpeedSample, current: SpeedSample) {
        when (config.mode) {
            PerformanceMode.STANDING_START -> {
                if (current.speedKmh <= config.stationaryThresholdKmh) {
                    val stationarySince = stationarySinceNanos ?: current.timeNanos.also {
                        stationarySinceNanos = it
                    }
                    if (current.timeNanos - stationarySince >= config.stationaryHoldMillis * NANOS_PER_MILLI) {
                        stationaryReady = true
                        message = "พร้อมออกตัว"
                    }
                    return
                }

                if (!stationaryReady && current.speedKmh >= config.launchThresholdKmh) {
                    invalidate("รถเคลื่อนที่ก่อนผ่านช่วงหยุดนิ่ง 1 วินาที")
                    return
                }

                if (stationaryReady && previous.speedKmh < config.launchThresholdKmh && current.speedKmh >= config.launchThresholdKmh) {
                    // The previous stationary sample is a better estimate of true movement start than
                    // the first Bluetooth sample that already exceeded the launch threshold.
                    startRun(previous.timeNanos, 0.0, current)
                }
            }

            PerformanceMode.ROLLING_START -> {
                if (previous.speedKmh < config.rollingStartKmh && current.speedKmh >= config.rollingStartKmh) {
                    val crossing = interpolateSpeedCrossingTimeNanos(
                        previous.speedKmh,
                        current.speedKmh,
                        config.rollingStartKmh,
                        previous.timeNanos,
                        current.timeNanos,
                    )
                    startRun(crossing, config.rollingStartKmh, current)
                }
            }
        }
    }

    private fun startRun(crossingNanos: Long, crossingSpeedKmh: Double, current: SpeedSample) {
        startNanos = crossingNanos
        previousRunSample = SpeedSample(crossingNanos, crossingSpeedKmh)
        status = TimeSlipStatus.RUNNING
        message = "กำลังจับเวลา"
        if (current.timeNanos > crossingNanos) processRunningSample(current)
    }

    private fun processRunningSample(current: SpeedSample) {
        val runStart = startNanos ?: return
        val previous = previousRunSample ?: return
        if (current.timeNanos <= previous.timeNanos) return

        val previousDistance = distanceMeters
        val deltaSeconds = (current.timeNanos - previous.timeNanos) / NANOS_PER_SECOND.toDouble()
        distanceMeters += integrateDistanceMeters(
            previous.speedKmh / 3.6,
            current.speedKmh / 3.6,
            deltaSeconds,
        )

        val speedCompletion = detectSpeedMilestones(previous, current, previousDistance, distanceMeters)
        val distanceCompletion = detectDistanceSplits(previous, current, previousDistance, distanceMeters)
        previousRunSample = current

        val completionNanos = if (config.selectedDistanceTarget != null) distanceCompletion else speedCompletion
        if (completionNanos != null) complete(completionNanos, runStart)
    }

    private fun detectSpeedMilestones(
        previous: SpeedSample,
        current: SpeedSample,
        previousDistance: Double,
        currentDistance: Double,
    ): Long? {
        val targets = if (config.mode == PerformanceMode.ROLLING_START) {
            listOf(config.rollingTargetKmh)
        } else {
            (config.enabledMilestonesKmh + config.speedOnlyTargetKmh).distinct().sorted()
        }
        var completion: Long? = null

        targets.forEach { target ->
            if (speedMilestones.any { abs(it.targetSpeedKmh - target) < 0.001 }) return@forEach
            if (previous.speedKmh < target && current.speedKmh >= target) {
                val crossing = interpolateSpeedCrossingTimeNanos(
                    previous.speedKmh,
                    current.speedKmh,
                    target,
                    previous.timeNanos,
                    current.timeNanos,
                )
                val ratio = ((crossing - previous.timeNanos).toDouble() /
                    (current.timeNanos - previous.timeNanos).toDouble()).coerceIn(0.0, 1.0)
                val crossingDistance = previousDistance + (currentDistance - previousDistance) * ratio
                val startSpeed = if (config.mode == PerformanceMode.ROLLING_START) config.rollingStartKmh else 0.0
                speedMilestones += SpeedMilestoneResult(
                    label = speedLabel(startSpeed, target),
                    startSpeedKmh = startSpeed,
                    targetSpeedKmh = target,
                    elapsedMillis = ((crossing - (startNanos ?: crossing)) / NANOS_PER_MILLI).coerceAtLeast(0L),
                    distanceAtTargetMeters = crossingDistance,
                )
                if (
                    config.selectedDistanceTarget == null &&
                    abs(target - completionSpeedTarget()) < 0.001
                ) {
                    completion = crossing
                }
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
        DistanceTarget.entries
            .filter { it.meters <= selected.meters }
            .forEach { target ->
                if (distanceSplits.any { it.target == target }) return@forEach
                if (previousDistance < target.meters && currentDistance >= target.meters) {
                    val crossing = interpolateDistanceCrossingTimeNanos(
                        previousDistance,
                        currentDistance,
                        target.meters,
                        previous.timeNanos,
                        current.timeNanos,
                    )
                    val ratio = ((crossing - previous.timeNanos).toDouble() /
                        (current.timeNanos - previous.timeNanos).toDouble()).coerceIn(0.0, 1.0)
                    val trapSpeed = previous.speedKmh + (current.speedKmh - previous.speedKmh) * ratio
                    val elapsed = ((crossing - (startNanos ?: crossing)) / NANOS_PER_MILLI).coerceAtLeast(0L)
                    val priorElapsed = distanceSplits.lastOrNull()?.elapsedMillis ?: 0L
                    distanceSplits += DistanceSplitResult(
                        target = target,
                        elapsedMillis = elapsed,
                        splitMillis = elapsed - priorElapsed,
                        trapSpeedKmh = trapSpeed,
                    )
                    if (target == selected) completion = crossing
                }
            }
        return completion
    }

    private fun complete(completionNanos: Long, runStartNanos: Long) {
        if (status != TimeSlipStatus.RUNNING) return
        status = TimeSlipStatus.COMPLETED
        message = "บันทึกผล Performance สำเร็จ"
        val elapsed = ((completionNanos - runStartNanos) / NANOS_PER_MILLI).coerceAtLeast(0L)
        val rate = calculateSampleRate(elapsed)
        val quality = calculateQuality(rate)
        val startedAt = armedAtEpochMillis + (runStartNanos - armedAtNanos) / NANOS_PER_MILLI
        val completedAt = armedAtEpochMillis + (completionNanos - armedAtNanos) / NANOS_PER_MILLI
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
            estimatedTimingErrorMillis = estimateTimingErrorMillis(rate),
            distanceEstimated = config.selectedDistanceTarget != null,
        )
    }

    private fun completionSpeedTarget(): Double = if (config.mode == PerformanceMode.ROLLING_START) {
        config.rollingTargetKmh
    } else {
        config.speedOnlyTargetKmh
    }

    private fun calculateSampleRate(elapsedMillis: Long): Double {
        if (elapsedMillis <= 0L || sampleCount <= 1) return 0.0
        return (sampleCount - 1) * 1_000.0 / elapsedMillis
    }

    private fun calculateQuality(rateHz: Double): MeasurementQuality {
        if (sampleCount < 3 || rateHz <= 0.0) return MeasurementQuality.INVALID
        if (config.selectedDistanceTarget != null) return MeasurementQuality.LOW
        return when {
            rateHz >= 8.0 && droppedSampleCount == 0 -> MeasurementQuality.HIGH
            rateHz >= 4.0 && droppedSampleCount <= 1 -> MeasurementQuality.MEDIUM
            else -> MeasurementQuality.LOW
        }
    }

    private fun estimateTimingErrorMillis(rateHz: Double): Long {
        if (rateHz <= 0.0) return 1_000L
        val halfSamplePeriod = (500.0 / rateHz).roundToLong()
        return max(20L, halfSamplePeriod + droppedSampleCount * 100L)
    }

    private fun elapsedMillis(): Long {
        val start = startNanos ?: return 0L
        val end = record?.let { start + it.elapsedMillis * NANOS_PER_MILLI }
            ?: previousSample?.timeNanos
            ?: start
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
        startNanos = null
        previousSample = null
        previousRunSample = null
        stationarySinceNanos = null
        stationaryReady = false
        currentSpeedKmh = 0.0
        maximumSpeedKmh = 0.0
        distanceMeters = 0.0
        sampleCount = 0
        droppedSampleCount = 0
        speedMilestones = mutableListOf()
        distanceSplits = mutableListOf()
        record = null
        message = null
    }

    companion object {
        private const val NANOS_PER_MILLI = 1_000_000L
        private const val NANOS_PER_SECOND = 1_000_000_000L
        private const val MAX_SAMPLE_GAP_NANOS = 1_500_000_000L

        fun interpolateSpeedCrossingTimeNanos(
            previousSpeedKmh: Double,
            currentSpeedKmh: Double,
            targetSpeedKmh: Double,
            previousTimeNanos: Long,
            currentTimeNanos: Long,
        ): Long {
            val speedDelta = currentSpeedKmh - previousSpeedKmh
            if (speedDelta <= 0.0) return currentTimeNanos
            val ratio = ((targetSpeedKmh - previousSpeedKmh) / speedDelta).coerceIn(0.0, 1.0)
            return previousTimeNanos + ((currentTimeNanos - previousTimeNanos) * ratio).roundToLong()
        }

        fun interpolateDistanceCrossingTimeNanos(
            previousDistanceMeters: Double,
            currentDistanceMeters: Double,
            targetDistanceMeters: Double,
            previousTimeNanos: Long,
            currentTimeNanos: Long,
        ): Long {
            val distanceDelta = currentDistanceMeters - previousDistanceMeters
            if (distanceDelta <= 0.0) return currentTimeNanos
            val ratio = ((targetDistanceMeters - previousDistanceMeters) / distanceDelta).coerceIn(0.0, 1.0)
            return previousTimeNanos + ((currentTimeNanos - previousTimeNanos) * ratio).roundToLong()
        }

        fun integrateDistanceMeters(
            previousSpeedMps: Double,
            currentSpeedMps: Double,
            deltaTimeSeconds: Double,
        ): Double = ((previousSpeedMps + currentSpeedMps) / 2.0) * deltaTimeSeconds

        fun speedLabel(startSpeedKmh: Double, targetSpeedKmh: Double): String {
            if (startSpeedKmh == 0.0 && abs(targetSpeedKmh - 96.56064) < 0.01) return "0–60 mph"
            return "${startSpeedKmh.roundToLong()}–${targetSpeedKmh.roundToLong()} km/h"
        }
    }
}
