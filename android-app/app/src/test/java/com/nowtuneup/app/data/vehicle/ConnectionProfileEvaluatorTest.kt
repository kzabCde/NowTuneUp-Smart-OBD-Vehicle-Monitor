package com.nowtuneup.app.data.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionProfileEvaluatorTest {
    @Test
    fun stableFastLinkScoresHighly() {
        val score = ConnectionProfileEvaluator.stabilityScore(
            successRate = 0.995,
            averageLatencyMillis = 85L,
            softRecoveries = 0,
        )
        assertTrue(score >= 95)
    }

    @Test
    fun weakLinkIsPenalizedForLatencyAndRecoveries() {
        val score = ConnectionProfileEvaluator.stabilityScore(
            successRate = 0.91,
            averageLatencyMillis = 520L,
            softRecoveries = 4,
        )
        assertTrue(score < 60)
    }

    @Test
    fun scoreAlwaysStaysInRange() {
        assertEquals(0, ConnectionProfileEvaluator.stabilityScore(0.0, 2_000L, 100))
        assertTrue(ConnectionProfileEvaluator.stabilityScore(1.0, 80L, 0) <= 100)
    }
}
