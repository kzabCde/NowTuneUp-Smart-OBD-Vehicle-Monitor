package com.nowtuneup.app.feature.live

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nowtuneup.app.domain.model.VehicleReading
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.launch
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
    val recordingId: String? = null,
    val stopReason: String? = null,
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
    private val directory = java.io.File(context.filesDir, "telemetry-v1").apply { mkdirs() }
    private val ioScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    private val storageLock = Any()
    private val liveBuffer = TelemetryBuffer(1200)
    private val rawBuffer = TelemetryBuffer(rolling = false)
    private var recordingId = ""
    private var startedNanos = 0L
    private var dataSource = "OBD"
    private val _samples = MutableStateFlow<List<TelemetrySample>>(emptyList())
    val samples: StateFlow<List<TelemetrySample>> = _samples.asStateFlow()
    private val _storageError = MutableStateFlow<String?>(null)
    val storageError = _storageError.asStateFlow()
    private val accumulators = linkedMapOf<Int, MetricAccumulator>()
    private val _recording = MutableStateFlow(LiveRecordingState())
    val recording: StateFlow<LiveRecordingState> = _recording.asStateFlow()
    private val _history = MutableStateFlow(readHistory())
    val history: StateFlow<List<LiveSessionSummary>> = _history.asStateFlow()

    fun start(nowMillis: Long = System.currentTimeMillis(), source: String = "OBD") {
        if (_recording.value.active) return
        dataSource = source
        recordingId = java.util.UUID.randomUUID().toString()
        startedNanos = android.os.SystemClock.elapsedRealtimeNanos()
        rawBuffer.clear()
        _storageError.value = null
        accumulators.clear()
        _recording.value = LiveRecordingState(active = true, startedAtMillis = nowMillis)
    }

    fun ingest(readings: List<VehicleReading>, connected: Boolean = true) {
        val nowNanos = android.os.SystemClock.elapsedRealtimeNanos()
        val nowMillis = System.currentTimeMillis()
        val liveUpdates = liveBuffer.accept(readings, connected, nowNanos, nowMillis, "live")
        if (liveUpdates.isNotEmpty()) _samples.value = liveBuffer.snapshot()
        val state = _recording.value
        if (!state.active) return
        val updates = rawBuffer.accept(readings.filter { (it.sourceTimeNanos ?: 0L) >= startedNanos }, connected, nowNanos, nowMillis, recordingId)
        updates.filter { it.quality == SampleQuality.VALID }.forEach { sample ->
            val value = sample.value ?: return@forEach
            val existing = accumulators[sample.pid]
            if (existing == null) accumulators[sample.pid] = MetricAccumulator(sample.pid, sample.name,
                sample.unit, value, value, value, value, 1)
            else existing.add(value)
        }
        if (updates.isNotEmpty()) _recording.value = state.copy(
            readingFrames = state.readingFrames + updates.size, trackedMetricCount = accumulators.size)
        if (rawBuffer.size >= rawBuffer.capacity || nowNanos - startedNanos >= 3_600_000_000_000L) {
            stop(reason = "Recording limit reached (20,000 samples / 1 hour)")
        }
    }

    fun stop(nowMillis: Long = System.currentTimeMillis(), reason: String = "Stopped by user"): LiveSessionSummary? {
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
            durationMillis = ((android.os.SystemClock.elapsedRealtimeNanos() - startedNanos) / 1_000_000).coerceAtLeast(0L),
            readingFrames = state.readingFrames,
            metrics = accumulators.values.map { it.summary() }.sortedBy { it.name },
            recordingId = recordingId,
            stopReason = reason,
        )
        accumulators.clear()
        val raw = TelemetryRecording(recordingId, state.startedAtMillis, nowMillis, rawBuffer.snapshot(), reason, dataSource)
        ioScope.launch {
            synchronized(storageLock) {
                runCatching {
                    val destination = java.io.File(directory, "${raw.id}.json")
                    val temporary = java.io.File(directory, "${raw.id}.tmp")
                    temporary.writeText(gson.toJson(raw))
                    check(temporary.renameTo(destination)) { "Unable to save telemetry" }
                    val updated = (listOf(summary) + _history.value).take(MAX_HISTORY)
                    check(preferences.edit().putString(KEY_HISTORY, gson.toJson(updated, historyType)).commit())
                    _history.value = updated
                    val retained = updated.mapNotNull { it.recordingId }.toSet()
                    directory.listFiles()?.filter { it.extension == "json" && it.nameWithoutExtension !in retained }?.forEach { it.delete() }
                }.onFailure { _storageError.value = "Recording was not saved: ${it.message}" }
            }
        }
        return summary
    }

    fun cancel() {
        accumulators.clear()
        _recording.value = LiveRecordingState()
    }

    fun clearHistory() {
        ioScope.launch { synchronized(storageLock) {
            if (preferences.edit().remove(KEY_HISTORY).commit()) {
                _history.value = emptyList()
                directory.listFiles()?.forEach { it.delete() }
            } else _storageError.value = "Unable to clear history"
        } }
    }

    suspend fun loadRecording(id: String): TelemetryRecording? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (!id.matches(Regex("[a-f0-9-]{36}"))) return@withContext null
        synchronized(storageLock) { runCatching {
            val file = java.io.File(directory, "$id.json")
            if (!file.exists() || file.length() > 12_000_000) null else gson.fromJson(file.readText(), TelemetryRecording::class.java)
        }.getOrNull() }
    }

    fun delete(summary: LiveSessionSummary) {
        ioScope.launch { synchronized(storageLock) {
            val updated = _history.value.filterNot { if (summary.recordingId != null) it.recordingId == summary.recordingId else it.recordingId == null && it.startedAtMillis == summary.startedAtMillis }
            if (preferences.edit().putString(KEY_HISTORY, gson.toJson(updated, historyType)).commit()) {
                _history.value = updated
                summary.recordingId?.takeIf { it.matches(Regex("[a-f0-9-]{36}")) }?.let { java.io.File(directory, "$it.json").delete() }
            } else _storageError.value = "Unable to delete recording"
        } }
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
