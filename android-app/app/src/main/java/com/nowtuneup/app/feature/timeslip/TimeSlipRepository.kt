package com.nowtuneup.app.feature.timeslip

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** Local-only history store. Existing Room and dashboard data are not modified. */
class TimeSlipRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val recordListType = object : TypeToken<List<TimeSlipRecord>>() {}.type

    fun list(): List<TimeSlipRecord> {
        val json = preferences.getString(KEY_RECORDS, null) ?: return emptyList()
        return runCatching {
            gson.fromJson<List<TimeSlipRecord>>(json, recordListType).orEmpty()
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

    fun bestSpeedMilestone(label: String): TimeSlipRecord? = list()
        .asSequence()
        .filter { it.measurementQuality != MeasurementQuality.INVALID }
        .filter { record -> record.speedMilestones.any { it.label == label } }
        .minByOrNull { record -> record.speedMilestones.first { it.label == label }.elapsedMillis }

    fun bestDistance(target: DistanceTarget): TimeSlipRecord? = list()
        .asSequence()
        .filter { it.measurementQuality != MeasurementQuality.INVALID }
        .filter { record -> record.distanceSplits.any { it.target == target } }
        .minByOrNull { record -> record.distanceSplits.first { it.target == target }.elapsedMillis }

    companion object {
        private const val PREFERENCES_NAME = "ntu_time_slip"
        private const val KEY_RECORDS = "records_v1"
        private const val KEY_SAFETY_ACKNOWLEDGED = "safety_acknowledged"
        private const val MAX_RECORDS = 100
    }
}

fun TimeSlipRecord.asShareText(): String = buildString {
    appendLine("NTU PERFORMANCE TIME SLIP")
    appendLine(if (mode == PerformanceMode.STANDING_START) "Standing start" else "Rolling start")
    speedMilestones.forEach { appendLine("${it.label}: ${formatSeconds(it.elapsedMillis)} s") }
    distanceSplits.forEach {
        appendLine("${it.target.label}: ${formatSeconds(it.elapsedMillis)} s — ${"%.1f".format(it.trapSpeedKmh)} km/h")
    }
    appendLine("Maximum speed: ${"%.1f".format(maximumSpeedKmh)} km/h")
    appendLine("OBD sample rate: ${"%.1f".format(obdSampleRateHz)} Hz")
    appendLine("Measurement quality: ${measurementQuality.name}")
    appendLine("Estimated timing error: ±${estimatedTimingErrorMillis} ms")
    if (distanceEstimated) appendLine("Distance: estimated from OBD-II speed; GPS correction not available")
}

fun TimeSlipRecord.asCsv(): String = buildString {
    appendLine("type,label,elapsed_seconds,trap_speed_kmh,distance_meters")
    speedMilestones.forEach {
        appendLine("speed,${it.label},${formatSeconds(it.elapsedMillis)},,${"%.3f".format(it.distanceAtTargetMeters)}")
    }
    distanceSplits.forEach {
        appendLine("distance,${it.target.label},${formatSeconds(it.elapsedMillis)},${"%.3f".format(it.trapSpeedKmh)},${"%.3f".format(it.target.meters)}")
    }
}

fun formatSeconds(milliseconds: Long): String = "%.3f".format(milliseconds / 1_000.0)
