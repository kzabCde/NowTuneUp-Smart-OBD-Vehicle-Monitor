package com.nowtuneup.app.feature.timeslip

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeSlipDisplayClockTest {
    @Test
    fun runningTimerAdvancesBetweenObdSamples() {
        val clock = TimeSlipDisplayClock()
        val snapshot = TimeSlipSnapshot(status = TimeSlipStatus.RUNNING, elapsedMillis = 1_250L)
        clock.synchronize(snapshot, nowNanos = 1_000_000_000L)

        assertEquals(1_583L, clock.elapsedMillis(snapshot, nowNanos = 1_333_000_000L))
    }

    @Test
    fun completedTimerUsesEngineResultWithoutDrift() {
        val clock = TimeSlipDisplayClock()
        val running = TimeSlipSnapshot(status = TimeSlipStatus.RUNNING, elapsedMillis = 1_000L)
        clock.synchronize(running, nowNanos = 1_000_000_000L)
        val completed = TimeSlipSnapshot(status = TimeSlipStatus.COMPLETED, elapsedMillis = 2_500L)

        assertEquals(2_500L, clock.elapsedMillis(completed, nowNanos = 9_000_000_000L))
    }

    @Test
    fun synchronizationCorrectsDisplayToLatestEngineSnapshot() {
        val clock = TimeSlipDisplayClock()
        val first = TimeSlipSnapshot(status = TimeSlipStatus.RUNNING, elapsedMillis = 500L)
        clock.synchronize(first, nowNanos = 1_000_000_000L)
        assertEquals(900L, clock.elapsedMillis(first, nowNanos = 1_400_000_000L))

        val corrected = first.copy(elapsedMillis = 850L)
        clock.synchronize(corrected, nowNanos = 1_400_000_000L)
        assertEquals(1_050L, clock.elapsedMillis(corrected, nowNanos = 1_600_000_000L))
    }
}
