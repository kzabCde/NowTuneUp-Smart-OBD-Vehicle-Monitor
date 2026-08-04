package com.nowtuneup.app.data.obd.session

/**
 * Timestamped speed sample captured at the OBD session boundary.
 * elapsedRealtimeNanos is monotonic and is safe for performance timing.
 */
data class ObdSpeedSample(
    val speedKmh: Double,
    val commandSentAtNanos: Long,
    val responseReceivedAtNanos: Long,
    val wallClockMillis: Long,
    val transportLatencyMillis: Long,
)

data class SpeedPidReadiness(
    val supported: Boolean = false,
    val hasValue: Boolean = false,
    val fresh: Boolean = false,
    val stableSamples: Int = 0,
    val sampleRateHz: Double = 0.0,
    val lastSampleAgeMillis: Long = Long.MAX_VALUE,
    val ready: Boolean = false,
    val reasonThai: String = "กำลังรอข้อมูลความเร็วจากรถ",
)
