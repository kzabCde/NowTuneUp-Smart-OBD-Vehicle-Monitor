package com.nowtuneup.app.feature.live

import com.nowtuneup.app.domain.model.VehicleReading

/** Invalid events carry no numerical value. Times are from the source monotonic clock. */
enum class SampleQuality { VALID, INVALID, DISCONNECTED }
data class TelemetrySample(
    val recordingId: String,
    val sourceTimeNanos: Long,
    val sourceTimestampMillis: Long,
    val pid: Int,
    val name: String,
    val value: Double?,
    val unit: String,
    val quality: SampleQuality,
)
data class TelemetryRecording(
    val id: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val samples: List<TelemetrySample>,
    val stopReason: String = "Stopped by user",
    val dataSource: String = "OBD",
)

/** Collection depends on source updates, never frame rendering. Duplicate/delayed responses are rejected. */
class TelemetryBuffer(val capacity: Int = 20_000, private val rolling: Boolean = true) {
    init { require(capacity > 0) }
    private val samples = ArrayDeque<TelemetrySample>()
    private val latestTime = mutableMapOf<Int, Long>()
    private var disconnected = false
    var rejected = 0; private set
    val size get() = samples.size
    fun clear() { samples.clear(); latestTime.clear(); disconnected = false; rejected = 0 }
    fun accept(readings: List<VehicleReading>, connected: Boolean, nowNanos: Long,
        nowMillis: Long, recordingId: String): List<TelemetrySample> {
        val accepted = mutableListOf<TelemetrySample>()
        if (!connected) {
            if (!disconnected) readings.filter { it.pid in latestTime }.forEach { reading ->
                accepted += TelemetrySample(recordingId, nowNanos, nowMillis, reading.pid, reading.name, null, reading.unit, SampleQuality.DISCONNECTED)
                latestTime[reading.pid] = maxOf(latestTime[reading.pid] ?: 0, nowNanos)
            }
            disconnected = true
        } else {
            disconnected = false
            readings.forEach { reading ->
                val time = reading.sourceTimeNanos ?: return@forEach
                if (time <= (latestTime[reading.pid] ?: 0L)) return@forEach
                if (time > nowNanos || nowNanos - time > 3_000_000_000L) { rejected++; return@forEach }
                latestTime[reading.pid] = time
                val valid = reading.supported && reading.value?.isFinite() == true
                accepted += TelemetrySample(recordingId, time, reading.updatedAt, reading.pid, reading.name,
                    reading.value.takeIf { valid }, reading.unit, if (valid) SampleQuality.VALID else SampleQuality.INVALID)
            }
        }
        val retained = if (rolling) accepted else accepted.take((capacity - samples.size).coerceAtLeast(0))
        retained.forEach { samples.addLast(it); while(samples.size > capacity) samples.removeFirst() }
        return retained
    }
    fun snapshot(): List<TelemetrySample> = samples.toList()
}

fun telemetryCsv(recording: TelemetryRecording): String = buildString {
    fun quote(value: String) = "\"" + value.replace("\"", "\"\"") + "\""
    appendLine("recording_id,source_timestamp_ms,source_elapsed_nanos,pid,name,value,unit,quality")
    recording.samples.forEach { s ->
        appendLine(listOf(quote(s.recordingId), s.sourceTimestampMillis, s.sourceTimeNanos,
            s.pid, quote(s.name), s.value?.toString().orEmpty(), quote(s.unit), s.quality).joinToString(","))
    }
}
