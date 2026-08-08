package com.nowtuneup.app.feature.timeslip

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** Minimal local Time Slip history used by the production OBD-only flow. */
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

    fun safetyAcknowledged(): Boolean = preferences.getBoolean(KEY_SAFETY_ACKNOWLEDGED, false)

    fun acknowledgeSafety() {
        preferences.edit().putBoolean(KEY_SAFETY_ACKNOWLEDGED, true).apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "ntu_time_slip"
        private const val KEY_RECORDS = "records_v1"
        private const val KEY_SAFETY_ACKNOWLEDGED = "safety_acknowledged"
        private const val MAX_RECORDS = 100
    }
}

fun TimeSlipRecord.asShareText(): String = buildString {
    appendLine("NTU PERFORMANCE TIME SLIP • 1.9.0")
    appendLine(if (mode == PerformanceMode.STANDING_START) "Standing start" else "Rolling start")
    speedMilestones.forEach { appendLine("${it.label}: ${formatSeconds(it.elapsedMillis)} s") }
    distanceSplits.forEach {
        appendLine("${it.target.label}: ${formatSeconds(it.elapsedMillis)} s — ${"%.1f".format(it.trapSpeedKmh)} km/h")
    }
    appendLine("Maximum speed: ${"%.1f".format(maximumSpeedKmh)} km/h")
    appendLine("OBD sample rate: ${"%.1f".format(obdSampleRateHz)} Hz")
    appendLine("Measurement quality: ${measurementQuality.name}")
    appendLine("Estimated timing error: ±${estimatedTimingErrorMillis} ms")
    if (distanceEstimated) appendLine("Distance is estimated from OBD speed; verify against track timing equipment")
}

fun formatSeconds(milliseconds: Long): String = "%.3f".format(milliseconds / 1_000.0)
