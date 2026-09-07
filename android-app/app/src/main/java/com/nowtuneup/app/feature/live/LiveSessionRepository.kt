package com.nowtuneup.app.feature.live

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nowtuneup.app.domain.model.VehicleReading
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LiveMetricSummary(
    val pid: Int,
    val name: String,
    val unit: String,
    val minimum: Double,
    val maximum: Double,
    val average: Double,
    val latest: Double,
    val sampleCount: Int,
)

data class LiveSessionSummary(
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val durationMillis: Long,
    val readingFrames: Int,
    val metrics: List<LiveMetricSummary>,
)

data class LiveRecordingState(
    val active: Boolean = false,
    val startedAtMillis: Long = 0L,
    val readingFrames: Int = 0,
    val trackedMetricCount: Int = 0,
)

private data class MetricAccumulator(
    val pid: Int,
    val name: String,
    val unit: String,
    var minimum: Double,
    var maximum: Double,
    var total: Double,
    var latest: Double,
    var count: Int,
) {
    fun add(value: Double) {
        minimum = minOf(minimum, value)
        maximum = maxOf(maximum, value)
        total += value
        latest = value
        count += 1
    }

    fun summary(): LiveMetricSummary = LiveMetricSummary(
        pid = pid,
        name = name,
        unit = unit,
        minimum = minimum,
        maximum = maximum,
        average = if (count > 0) total / count else latest,
        latest = latest,
        sampleCount = count,
    )
}

@Singleton
class LiveSessionRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val historyType = object : TypeToken<List<LiveSessionSummary>>() {}.type
    private val accumulators = linkedMapOf<Int, MetricAccumulator>()
    private val _recording = MutableStateFlow(LiveRecordingState())
    val recording: StateFlow<LiveRecordingState> = _recording.asStateFlow()
    private val _history = MutableStateFlow(readHistory())
    val history: StateFlow<List<LiveSessionSummary>> = _history.asStateFlow()

    fun start(nowMillis: Long = System.currentTimeMillis()) {
        accumulators.clear()
        _recording.value = LiveRecordingState(active = true, startedAtMillis = nowMillis)
    }

    fun ingest(readings: List<VehicleReading>) {
        val state = _recording.value
        if (!state.active) return
        var accepted = 0
        readings.forEach { reading ->
            val value = reading.value ?: return@forEach
            if (!reading.supported || !value.isFinite()) return@forEach
            accepted += 1
            val existing = accumulators[reading.pid]
            if (existing == null) {
                accumulators[reading.pid] = MetricAccumulator(
                    pid = reading.pid,
                    name = reading.name,
                    unit = reading.unit,
                    minimum = value,
                    maximum = value,
                    total = value,
                    latest = value,
                    count = 1,
                )
            } else {
                existing.add(value)
            }
        }
        if (accepted > 0) {
            _recording.value = state.copy(
                readingFrames = state.readingFrames + 1,
                trackedMetricCount = accumulators.size,
            )
        }
    }

    fun stop(nowMillis: Long = System.currentTimeMillis()): LiveSessionSummary? {
        val state = _recording.value
        if (!state.active) return null
        _recording.value = LiveRecordingState()
        if (state.readingFrames <= 0 || accumulators.isEmpty()) {
            accumulators.clear()
            return null
        }
        val summary = LiveSessionSummary(
            startedAtMillis = state.startedAtMillis,
            endedAtMillis = nowMillis,
            durationMillis = (nowMillis - state.startedAtMillis).coerceAtLeast(0L),
            readingFrames = state.readingFrames,
            metrics = accumulators.values.map { it.summary() }.sortedBy { it.name },
        )
        accumulators.clear()
        val updated = (listOf(summary) + _history.value).take(MAX_HISTORY)
        preferences.edit().putString(KEY_HISTORY, gson.toJson(updated, historyType)).apply()
        _history.value = updated
        return summary
    }

    fun cancel() {
        accumulators.clear()
        _recording.value = LiveRecordingState()
    }

    fun clearHistory() {
        preferences.edit().remove(KEY_HISTORY).apply()
        _history.value = emptyList()
    }

    private fun readHistory(): List<LiveSessionSummary> = runCatching {
        val json = preferences.getString(KEY_HISTORY, null) ?: return emptyList()
        gson.fromJson<List<LiveSessionSummary>>(json, historyType).orEmpty()
            .sortedByDescending { it.startedAtMillis }
            .take(MAX_HISTORY)
    }.getOrDefault(emptyList())

    companion object {
        private const val PREFERENCES_NAME = "ntu_live_sessions"
        private const val KEY_HISTORY = "history_v1"
        private const val MAX_HISTORY = 20
    }
}
