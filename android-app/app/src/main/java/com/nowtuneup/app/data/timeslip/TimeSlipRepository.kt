package com.nowtuneup.app.data.timeslip

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nowtuneup.app.data.local.dao.NtuDao
import com.nowtuneup.app.data.local.entity.TimeSlipRecordEntity
import com.nowtuneup.app.data.local.entity.TimeSlipSampleEntity
import com.nowtuneup.app.feature.timeslip.domain.DistanceSplit
import com.nowtuneup.app.feature.timeslip.domain.DistanceTarget
import com.nowtuneup.app.feature.timeslip.domain.MeasurementQuality
import com.nowtuneup.app.feature.timeslip.domain.PerformanceSample
import com.nowtuneup.app.feature.timeslip.domain.SpeedMilestone
import com.nowtuneup.app.feature.timeslip.domain.TimeSlipDataSource
import com.nowtuneup.app.feature.timeslip.domain.TimeSlipRecord
import com.nowtuneup.app.feature.timeslip.domain.TimeSlipStatus
import com.nowtuneup.app.feature.timeslip.domain.TimeSlipTestMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class TimeSlipRepository @Inject constructor(
    private val dao: NtuDao,
) {
    private val gson = Gson()

    val records: Flow<List<TimeSlipRecord>> = dao.timeSlipRecords().map { entities ->
        entities.map { it.toDomain(emptyList()) }
    }

    suspend fun save(record: TimeSlipRecord) {
        require(record.status == TimeSlipStatus.COMPLETED) { "Only completed Time Slip records can be saved" }
        val downsampled = downsample(record.samples)
        dao.saveTimeSlip(record.toEntity(), downsampled.mapIndexed { index, sample -> sample.toEntity(record.id, index) })
    }

    suspend fun getById(id: String): TimeSlipRecord? {
        val entity = dao.timeSlipRecord(id) ?: return null
        return entity.toDomain(dao.timeSlipSamples(id).map { it.toDomain() })
    }

    suspend fun delete(id: String) = dao.deleteTimeSlipRecord(id)
    suspend fun clear() = dao.clearTimeSlipRecords()

    fun bestResults(records: List<TimeSlipRecord>, includeLowConfidence: Boolean = false): TimeSlipBestResults =
        TimeSlipBestResultSelector.select(records, includeLowConfidence)

    private fun downsample(samples: List<PerformanceSample>): List<PerformanceSample> {
        if (samples.size <= 1_000) return samples
        val step = (samples.size / 1_000.0).coerceAtLeast(1.0)
        val result = mutableListOf<PerformanceSample>()
        var index = 0.0
        while (index < samples.size) {
            result += samples[index.toInt().coerceAtMost(samples.lastIndex)]
            index += step
        }
        if (result.lastOrNull() != samples.lastOrNull()) samples.lastOrNull()?.let(result::add)
        return result
    }

    private fun TimeSlipRecord.toEntity() = TimeSlipRecordEntity(
        id = id,
        status = status.name,
        testMode = testMode.name,
        selectedDistanceTarget = selectedDistanceTarget?.name,
        startedAtEpochMs = startedAtEpochMs,
        completedAtEpochMs = completedAtEpochMs,
        startMonotonicTimeMs = startMonotonicTimeMs,
        completedMonotonicTimeMs = completedMonotonicTimeMs,
        elapsedMs = elapsedMs,
        totalDistanceMeters = totalDistanceMeters,
        maximumSpeedKmh = maximumSpeedKmh,
        maximumAccelerationMs2 = maximumAccelerationMs2,
        dataSource = dataSource.name,
        obdDeviceName = obdDeviceName,
        obdSampleRateHz = obdSampleRateHz,
        gpsSampleRateHz = gpsSampleRateHz,
        averageGpsAccuracyMeters = averageGpsAccuracyMeters,
        sampleCount = sampleCount,
        droppedSampleCount = droppedSampleCount,
        connectionInterruptions = connectionInterruptions,
        gpsInterruptions = gpsInterruptions,
        measurementQuality = measurementQuality.name,
        estimatedTimingErrorMs = estimatedTimingErrorMs,
        vehicleName = vehicleName,
        notes = notes,
        distanceCorrectionMeters = distanceCorrectionMeters,
        speedMilestonesJson = gson.toJson(speedMilestones),
        distanceSplitsJson = gson.toJson(distanceSplits),
    )

    private fun TimeSlipRecordEntity.toDomain(samples: List<PerformanceSample>): TimeSlipRecord = TimeSlipRecord(
        id = id,
        status = enumValueOrDefault(status, TimeSlipStatus.INVALID_RUN),
        testMode = enumValueOrDefault(testMode, TimeSlipTestMode.STANDING_START),
        selectedDistanceTarget = selectedDistanceTarget?.let { enumValueOrDefault(it, DistanceTarget.SPEED_ONLY) },
        startedAtEpochMs = startedAtEpochMs,
        completedAtEpochMs = completedAtEpochMs,
        startMonotonicTimeMs = startMonotonicTimeMs,
        completedMonotonicTimeMs = completedMonotonicTimeMs,
        speedMilestones = gson.fromJson(speedMilestonesJson, object : TypeToken<List<SpeedMilestone>>() {}.type) ?: emptyList(),
        distanceSplits = gson.fromJson(distanceSplitsJson, object : TypeToken<List<DistanceSplit>>() {}.type) ?: emptyList(),
        totalDistanceMeters = totalDistanceMeters,
        elapsedMs = elapsedMs,
        maximumSpeedKmh = maximumSpeedKmh,
        maximumAccelerationMs2 = maximumAccelerationMs2,
        dataSource = enumValueOrDefault(dataSource, TimeSlipDataSource.OBD),
        obdDeviceName = obdDeviceName,
        obdSampleRateHz = obdSampleRateHz,
        gpsSampleRateHz = gpsSampleRateHz,
        averageGpsAccuracyMeters = averageGpsAccuracyMeters,
        sampleCount = sampleCount,
        droppedSampleCount = droppedSampleCount,
        connectionInterruptions = connectionInterruptions,
        gpsInterruptions = gpsInterruptions,
        measurementQuality = enumValueOrDefault(measurementQuality, MeasurementQuality.INVALID),
        estimatedTimingErrorMs = estimatedTimingErrorMs,
        vehicleName = vehicleName,
        notes = notes,
        distanceCorrectionMeters = distanceCorrectionMeters,
        samples = samples,
    )

    private fun PerformanceSample.toEntity(recordId: String, sequence: Int) = TimeSlipSampleEntity(
        recordId = recordId,
        sequence = sequence,
        monotonicTimeMs = monotonicTimeMs,
        elapsedMs = elapsedMs,
        obdSpeedKmh = obdSpeedKmh,
        gpsSpeedKmh = gpsSpeedKmh,
        fusedSpeedKmh = fusedSpeedKmh,
        accumulatedDistanceMeters = accumulatedDistanceMeters,
        gpsAccuracyMeters = gpsAccuracyMeters,
        accelerationMs2 = accelerationMs2,
        obdValid = obdValid,
        gpsValid = gpsValid,
    )

    private fun TimeSlipSampleEntity.toDomain() = PerformanceSample(
        monotonicTimeMs = monotonicTimeMs,
        elapsedMs = elapsedMs,
        obdSpeedKmh = obdSpeedKmh,
        gpsSpeedKmh = gpsSpeedKmh,
        fusedSpeedKmh = fusedSpeedKmh,
        accumulatedDistanceMeters = accumulatedDistanceMeters,
        gpsAccuracyMeters = gpsAccuracyMeters,
        accelerationMs2 = accelerationMs2,
        obdValid = obdValid,
        gpsValid = gpsValid,
    )

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
        runCatching { enumValueOf<T>(value) }.getOrDefault(default)
}

data class TimeSlipBestResults(
    val zeroTo60Kmh: Pair<TimeSlipRecord, SpeedMilestone>?,
    val zeroTo100Kmh: Pair<TimeSlipRecord, SpeedMilestone>?,
    val eighthMile: Pair<TimeSlipRecord, DistanceSplit>?,
    val quarterMile: Pair<TimeSlipRecord, DistanceSplit>?,
    val halfMile: Pair<TimeSlipRecord, DistanceSplit>?,
    val oneMile: Pair<TimeSlipRecord, DistanceSplit>?,
    val fastestTrapSpeedKmh: Double?,
)

object TimeSlipBestResultSelector {
    fun select(records: List<TimeSlipRecord>, includeLowConfidence: Boolean = false): TimeSlipBestResults {
        val eligible = records.filter { record ->
            record.status == TimeSlipStatus.COMPLETED &&
                record.measurementQuality != MeasurementQuality.INVALID &&
                (includeLowConfidence || record.measurementQuality in setOf(MeasurementQuality.HIGH, MeasurementQuality.MEDIUM))
        }
        fun bestSpeed(id: String) = eligible.mapNotNull { record ->
            record.speedMilestones.firstOrNull { it.id == id }?.let { record to it }
        }.minByOrNull { it.second.elapsedMs }
        fun bestDistance(target: DistanceTarget) = eligible.mapNotNull { record ->
            record.distanceSplits.firstOrNull { it.target == target }?.let { record to it }
        }.minByOrNull { it.second.elapsedMs }
        return TimeSlipBestResults(
            zeroTo60Kmh = bestSpeed("0-60-kmh"),
            zeroTo100Kmh = bestSpeed("0-100-kmh"),
            eighthMile = bestDistance(DistanceTarget.EIGHTH_MILE),
            quarterMile = bestDistance(DistanceTarget.QUARTER_MILE),
            halfMile = bestDistance(DistanceTarget.HALF_MILE),
            oneMile = bestDistance(DistanceTarget.ONE_MILE),
            fastestTrapSpeedKmh = eligible.flatMap { it.distanceSplits }.maxOfOrNull { it.trapSpeedKmh },
        )
    }
}
