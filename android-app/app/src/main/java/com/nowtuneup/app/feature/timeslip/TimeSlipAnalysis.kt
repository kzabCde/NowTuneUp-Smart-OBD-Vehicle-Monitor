package com.nowtuneup.app.feature.timeslip

import kotlin.math.abs

/** Values derived from OBD speed, not accelerometer or Dragy hardware measurements. */
data class RunChartPoint(
    val seconds: Double,
    val speedKmh: Double,
    val accelerationG: Double?,
    val distanceMeters: Double?,
    val breakBefore: Boolean = false,
)

data class RunChartData(val points: List<RunChartPoint>, val unavailableReason: String? = null)

object TimeSlipAnalysis {
    private const val MAX_GAP_NANOS = 1_500_000_000L

    fun chart(record: TimeSlipRecord): RunChartData {
        fun missing(message: String) = RunChartData(emptyList(), message)
        val start = record.timingStartNanos
            ?: return missing("ผลเดิมยังไม่มีจุดเริ่มกราฟที่แน่นอน เริ่มทดสอบใหม่เพื่อบันทึกกราฟ")
        val launch = record.launchTimeNanos ?: return missing("ไม่มีข้อมูลจุดออกตัว")
        val finish = record.completionTimeNanos ?: return missing("ไม่มีข้อมูลจุดสิ้นสุด")
        val launchSpeed = record.launchSpeedKmh ?: return missing("ไม่มีข้อมูลความเร็วเริ่มต้น")
        if (launch > start || start >= finish || !launchSpeed.isFinite() || launchSpeed !in 0.0..400.0) {
            return missing("ช่วงเวลาของข้อมูลไม่ถูกต้อง")
        }
        val samples = record.rawSamples.orEmpty().filter {
            it.timeNanos > launch && it.obdSpeedKmh.isFinite() && it.obdSpeedKmh in 0.0..400.0
        }.distinctBy { it.timeNanos }.sortedBy { it.timeNanos }
        if (samples.isEmpty()) return missing("ไม่มีข้อมูลความเร็วสำหรับวาดกราฟ")

        var previousTime = launch
        var previousSpeed = launchSpeed
        var distance = 0.0
        var distanceKnown = true
        val points = mutableListOf<RunChartPoint>()
        if (start == launch) points += RunChartPoint(0.0, launchSpeed, null, 0.0)
        for (sample in samples) {
            val end = minOf(sample.timeNanos, finish)
            if (end <= previousTime) break
            val gap = sample.timeNanos - previousTime > MAX_GAP_NANOS
            if (gap) distanceKnown = false
            val fullSeconds = (sample.timeNanos - previousTime) / 1e9
            val acceleration = (sample.obdSpeedKmh - previousSpeed) / 3.6 / fullSeconds
            fun speedAt(time: Long) = previousSpeed + (sample.obdSpeedKmh - previousSpeed) *
                ((time - previousTime).toDouble() / (sample.timeNanos - previousTime))
            // Match the engine's trapezoidal samples and linear distance-crossing interpolation.
            fun distanceAt(time: Long) = distance +
                (previousSpeed + sample.obdSpeedKmh) / 7.2 * ((time - previousTime) / 1e9)
            if (previousTime < start && end >= start && !gap) {
                points += RunChartPoint(0.0, speedAt(start), null, if (distanceKnown) distanceAt(start) else null)
            }
            val endSpeed = speedAt(end)
            val endDistance = distanceAt(end)
            // Do not invent a finish point inside a missing telemetry interval.
            if (end >= start && !(gap && end != sample.timeNanos) && points.lastOrNull()?.seconds != (end - start) / 1e9) {
                points += RunChartPoint(
                    seconds = (end - start) / 1e9,
                    speedKmh = endSpeed,
                    accelerationG = if (gap) null else acceleration / 9.80665,
                    distanceMeters = if (distanceKnown) endDistance else null,
                    breakBefore = gap,
                )
            }
            distance = endDistance
            previousTime = sample.timeNanos
            previousSpeed = sample.obdSpeedKmh
            if (end == finish) break
        }
        return if (points.size < 2) missing("ข้อมูลต่อเนื่องไม่เพียงพอสำหรับกราฟ") else RunChartData(points)
    }

    fun comparable(left: TimeSlipRecord, right: TimeSlipRecord): Boolean =
        left.status == TimeSlipStatus.COMPLETED && right.status == TimeSlipStatus.COMPLETED &&
            left.measurementQuality != MeasurementQuality.INVALID && right.measurementQuality != MeasurementQuality.INVALID &&
            !left.vehicleProfileId.isNullOrBlank() && left.vehicleProfileId == right.vehicleProfileId && left.mode == right.mode &&
            left.oneFootRolloutEnabled == right.oneFootRolloutEnabled &&
            left.selectedDistanceTarget == right.selectedDistanceTarget &&
            (left.selectedDistanceTarget != null ||
                (finishMilestone(left)?.let { l ->
                    finishMilestone(right)?.let { r ->
                        abs(l.startSpeedKmh - r.startSpeedKmh) < 0.001 && abs(l.targetSpeedKmh - r.targetSpeedKmh) < 0.001
                    }
                } == true))

    // One OBD response can cross additional targets after the actual finish.
    fun finishMilestone(record: TimeSlipRecord): SpeedMilestoneResult? = record.speedMilestones
        .filter { it.elapsedMillis <= record.elapsedMillis }
        .maxByOrNull { it.elapsedMillis }
}

/** A measured auxiliary source observation, never aligned to a fabricated speed-sample timestamp. */
data class AuxiliaryRunPoint(val seconds: Double, val value: Double, val breakBefore: Boolean)

fun captureTimeSlipSignal(reading: com.nowtuneup.app.domain.model.VehicleReading?, speedTimeNanos: Long): TimeSlipSignalSample? {
    reading ?: return null
    val time = reading.sourceTimeNanos ?: return null
    val value = reading.value ?: return null
    if (!reading.supported || !value.isFinite() || speedTimeNanos - time !in 0L..1_500_000_000L) return null
    return TimeSlipSignalSample(time, value)
}

fun auxiliaryRunSeries(record: TimeSlipRecord, pid: Int): List<AuxiliaryRunPoint> {
    val start = record.timingStartNanos ?: return emptyList()
    val finish = record.completionTimeNanos ?: return emptyList()
    if (finish <= start) return emptyList()
    val observations = record.rawSamples.orEmpty().mapNotNull { sample ->
        when(pid) { 0x0C -> sample.rpmSample; 0x11 -> sample.throttleSample; else -> null }
    }.filter { it.sourceTimeNanos in start..finish && it.value.isFinite() &&
        if (pid == 0x0C) it.value in 0.0..16383.75 else it.value in 0.0..100.0 }
        .distinctBy { it.sourceTimeNanos }.sortedBy { it.sourceTimeNanos }
    return observations.mapIndexed { index, sample -> AuxiliaryRunPoint((sample.sourceTimeNanos-start)/1e9,
        sample.value, index > 0 && sample.sourceTimeNanos-observations[index-1].sourceTimeNanos > 1_500_000_000L) }
}
