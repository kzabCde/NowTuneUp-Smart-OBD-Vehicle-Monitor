package com.nowtuneup.app.feature.timeslip

/**
 * Smooths only the on-screen timer between OBD speed samples.
 *
 * The performance engine remains the source of truth for launch interpolation, milestones, splits,
 * and the saved result. This clock is re-anchored to every engine snapshot and uses monotonic time
 * only while a run is active.
 */
class TimeSlipDisplayClock {
    private var anchorElapsedMillis: Long = 0L
    private var anchorNanos: Long = 0L
    private var running: Boolean = false

    fun synchronize(snapshot: TimeSlipSnapshot, nowNanos: Long = System.nanoTime()): Long {
        anchorElapsedMillis = snapshot.elapsedMillis
        anchorNanos = nowNanos
        running = snapshot.status == TimeSlipStatus.RUNNING
        return anchorElapsedMillis
    }

    fun elapsedMillis(snapshot: TimeSlipSnapshot, nowNanos: Long = System.nanoTime()): Long {
        if (!running || snapshot.status != TimeSlipStatus.RUNNING) return snapshot.elapsedMillis
        val advanced = ((nowNanos - anchorNanos).coerceAtLeast(0L) / NANOS_PER_MILLI)
        return anchorElapsedMillis + advanced
    }

    fun reset() {
        anchorElapsedMillis = 0L
        anchorNanos = 0L
        running = false
    }

    companion object {
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
