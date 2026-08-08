package com.nowtuneup.app.data.obd.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdapterHealthTrackerTest {
    @Test
    fun fastHealthyAdapterIsRecognized() {
        val tracker = AdapterHealthTracker()
        tracker.reset(1_000L)
        var state = AdapterHealthState()
        repeat(8) { index ->
            state = tracker.recordSuccess(80L, 1_100L + index * 100L)
        }

        assertEquals(AdapterHealthGrade.GOOD, state.grade)
        assertEquals(AdaptivePollingMode.FAST, state.recommendedMode)
        assertEquals(80L, state.averageLatencyMillis)
        assertEquals(1.0, state.successRate, 0.0001)
    }

    @Test
    fun failuresForceStableMode() {
        val tracker = AdapterHealthTracker()
        tracker.reset(1_000L)
        repeat(5) { tracker.recordSuccess(250L, 1_100L + it * 100L) }
        var state = AdapterHealthState()
        repeat(4) { state = tracker.recordFailure(1_700L + it * 100L) }

        assertEquals(AdapterHealthGrade.POOR, state.grade)
        assertEquals(AdaptivePollingMode.STABLE, state.recommendedMode)
        assertTrue(state.successRate < 0.90)
    }

    @Test
    fun recoveryCountIsPreserved() {
        val tracker = AdapterHealthTracker()
        tracker.reset(1_000L)
        val state = tracker.recordRecovery(1_100L)
        assertEquals(1, state.softRecoveries)
    }
}
