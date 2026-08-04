package com.nowtuneup.app.feature.timeslip

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** Local-only Time Slip history with backward-compatible JSON records. */
class TimeSlipRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val recordListType = object : TypeToken<List<TimeSlipRecord>>() {}.type

    fun list(): List<TimeSlipRecord> {
        val json = preferences.getString(KEY_RECORDS, null) ?: return emptyList()
        return runCatching {
            gson.fromJson<List<TimeSlipRecord>>(json, recordListType).orEmpty()
                .filterNotNull()
                .sortedByDescending { it.startedAtEpochMillis }
        }.getOrDefault(emptyList())
    }

    fun save(record: TimeSlipRecord) {
        if (record.status != TimeSlipStatus.COMPLETED || record.measurementQuality == MeasurementQuality.INVALID) return
        val updated = (list().filterNot { it.id == record.id } + record)
            .sortedByDescending { it.startedAtEpochMillis }
            .take(MAX_RECORDS)
        preferences.edit().putString(KEY_RECORDS, gson.toJson(updated, recordListType)).apply()
    }

    fun delete(recordId: String) {
        val updated = list().filterNot { it.id == recordId }
        preferences.edit().putString(KEY_RECORDS, gson.toJson(updated, recordListType)).apply()
    }

    fun clear() {
        preferences.edit().remove(KEY_RECORDS).apply()
    }

    fun safetyAcknowledged(): Boolean = preferences.getBoolean(KEY_SAFETY_ACKNOWLEDGED, false)

    fun acknowledgeSafety() {
        preferences.edit().putBoolean(KEY_SAFETY_ACKNOWLEDGED, true).apply()
    }

    fun vehicleProfileId(): String = preferences.getString(KEY_VEHICLE_PROFILE, "default") ?: "default"

    fun setVehicleProfileId(profileId: String) {
        val normalized = profileId.trim().take(40).ifBlank { "default" }
        preferences.edit().putString(KEY_VEHICLE_PROFILE, normalized).apply()
    }

    fun bestSpeedMilestone(label: String, vehicleProfileId: String? = null): TimeSlipRecord? = list()
        .asSequence()
        .filter { it.measurementQuality != MeasurementQuality.INVALID }
        .filter { vehicleProfileId == null || (it.vehicleProfileId ?: "default") == vehicleProfileId }
        .filter { record -> record.speedMilestones.any { it.label == label } }
        .minByOrNull { record -> record.speedMilestones.first { it.label == label }.elapsedMillis }

    fun bestDistance(target: DistanceTarget, vehicleProfileId: String? = null): TimeSlipRecord? = list()
        .asSequence()
        .filter { it.measurementQuality != MeasurementQuality.INVALID }
        .filter { vehicleProfileId == null || (it.vehicleProfileId ?: "default") == vehicleProfileId }
        .filter { record -> record.distanceSplits.any { it.target == target } }
        .minByOrNull { record -> record.distanceSplits.first { it.target == target }.elapsedMillis }

    fun compare(leftId: String, rightId: String): TimeSlipComparison? {
        val byId = list().associateBy { it.id }
        val left = byId[leftId] ?: return null
        val right = byId[rightId] ?: return null
        return TimeSlipComparison(
            left = left,
            right = right,
            elapsedDeltaMillis = right.elapsedMillis - left.elapsedMillis,
            maximumSpeedDeltaKmh = right.maximumSpeedKmh - left.maximumSpeedKmh,
            sampleRateDeltaHz = right.obdSampleRateHz - left.obdSampleRateHz,
        )
    }

    fun replay(recordId: String): TimeSlipRecord? = list().firstOrNull { it.id == recordId }?.let(TimeSlipEngine::replay)

    companion object {
        private const val PREFERENCES_NAME = "ntu_time_slip"
        private const val KEY_RECORDS = "records_v1"
        private const val KEY_SAFETY_ACKNOWLEDGED = "safety_acknowledged"
        private const val KEY_VEHICLE_PROFILE = "vehicle_profile_id"
        private const val MAX_RECORDS = 100
    }
}

fun TimeSlipRecord.asShareText(): String = buildString {
    appendLine("NTU PERFORMANCE TIME SLIP • 1.8.0")
    appendLine(if (mode == PerformanceMode.STANDING_START) "Standing start" else "Rolling start")
    appendLine("Vehicle profile: ${vehicleProfileId ?: "default"}")
    if (reactionTimeMillis > 0L) appendLine("Reaction: ${formatSeconds(reactionTimeMillis)} s")
    if (oneFootRolloutEnabled) appendLine("One-foot rollout: ${formatSeconds(rolloutMillis)} s")
    speedMilestones.forEach { appendLine("${it.label}: ${formatSeconds(it.elapsedMillis)} s") }
    distanceSplits.forEach {
        appendLine("${it.target.label}: ${formatSeconds(it.elapsedMillis)} s — ${"%.1f".format(it.trapSpeedKmh)} km/h")
    }
    appendLine("Maximum speed: ${"%.1f".format(maximumSpeedKmh)} km/h")
    appendLine("Maximum acceleration: ${"%.2f".format(maximumAccelerationMps2)} m/s²")
    appendLine("OBD sample rate: ${"%.1f".format(obdSampleRateHz)} Hz")
    appendLine("Measurement quality: ${measurementQuality.name}")
    appendLine("Speed confidence: ${(speedConfidence ?: ConfidenceLevel.LOW).name}")
    appendLine("Distance confidence: ${(distanceConfidence ?: ConfidenceLevel.LOW).name}")
    appendLine("Data source: $dataSource")
    averageGpsAccuracyMeters?.let { appendLine("Average GNSS accuracy: ${"%.1f".format(it)} m") }
    averageSlopePercent?.let { appendLine("Average slope: ${"%.2f".format(it)} %") }
    appendLine("Estimated timing error: ±${estimatedTimingErrorMillis} ms")
    if (distanceEstimated) appendLine("Distance remains estimated; verify against track timing equipment")
}

fun TimeSlipRecord.asCsv(): String = buildString {
    appendLine("summary_key,summary_value")
    appendLine("elapsed_seconds,${formatSeconds(elapsedMillis)}")
    appendLine("reaction_seconds,${formatSeconds(reactionTimeMillis)}")
    appendLine("rollout_seconds,${formatSeconds(rolloutMillis)}")
    appendLine("maximum_speed_kmh,${"%.3f".format(maximumSpeedKmh)}")
    appendLine("sample_rate_hz,${"%.3f".format(obdSampleRateHz)}")
    appendLine("data_source,$dataSource")
    appendLine()
    appendLine("type,label,elapsed_seconds,trap_speed_kmh,distance_meters")
    speedMilestones.forEach {
        appendLine("speed,${it.label},${formatSeconds(it.elapsedMillis)},,${"%.3f".format(it.distanceAtTargetMeters)}")
    }
    distanceSplits.forEach {
        appendLine("distance,${it.target.label},${formatSeconds(it.elapsedMillis)},${"%.3f".format(it.trapSpeedKmh)},${"%.3f".format(it.target.meters)}")
    }
    if (rawSamples.orEmpty().isNotEmpty()) {
        appendLine()
        appendLine("time_nanos,obd_speed_kmh,gps_speed_kmh,fused_speed_kmh,acceleration_mps2,gps_accuracy_m,satellites,slope_percent,latency_ms,source")
        rawSamples.orEmpty().forEach { sample ->
            appendLine(
                listOf(
                    sample.timeNanos,
                    "%.3f".format(sample.obdSpeedKmh),
                    sample.gpsSpeedKmh?.let { "%.3f".format(it) }.orEmpty(),
                    "%.3f".format(sample.fusedSpeedKmh),
                    "%.3f".format(sample.accelerationMps2),
                    sample.gpsAccuracyMeters?.let { "%.3f".format(it) }.orEmpty(),
                    sample.satellitesUsed,
                    sample.slopePercent?.let { "%.3f".format(it) }.orEmpty(),
                    sample.transportLatencyMillis,
                    sample.source.name,
                ).joinToString(","),
            )
        }
    }
}

fun formatSeconds(milliseconds: Long): String = "%.3f".format(milliseconds / 1_000.0)
