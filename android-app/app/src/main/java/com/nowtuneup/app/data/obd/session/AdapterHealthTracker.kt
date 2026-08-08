package com.nowtuneup.app.data.obd.session

import java.util.ArrayDeque

/** Runtime quality of the active ELM327/ECU link. */
enum class AdapterHealthGrade {
    UNKNOWN,
    GOOD,
    FAIR,
    POOR,
}

enum class AdaptivePollingMode {
    FAST,
    BALANCED,
    STABLE,
}

data class AdapterHealthState(
    val grade: AdapterHealthGrade = AdapterHealthGrade.UNKNOWN,
    val recommendedMode: AdaptivePollingMode = AdaptivePollingMode.BALANCED,
    val averageLatencyMillis: Long = 0L,
    val successRate: Double = 1.0,
    val commandsPerSecond: Double = 0.0,
    val totalCommands: Long = 0L,
    val failedCommands: Long = 0L,
    val softRecoveries: Int = 0,
    val updatedAtMillis: Long = 0L,
) {
    val thaiLabel: String
        get() = when (grade) {
            AdapterHealthGrade.UNKNOWN -> "กำลังประเมิน"
            AdapterHealthGrade.GOOD -> "ดี"
            AdapterHealthGrade.FAIR -> "ปานกลาง"
            AdapterHealthGrade.POOR -> "ไม่เสถียร"
        }
}

/**
 * Small rolling health model used to tune command pacing without exposing expert settings.
 * It intentionally favors stability over maximum commands/second on inexpensive ELM327 clones.
 */
class AdapterHealthTracker(
    private val windowSize: Int = 40,
) {
    private val latencyWindow = ArrayDeque<Long>()
    private val outcomeWindow = ArrayDeque<Boolean>()
    private var startedAtMillis = 0L
    private var totalCommands = 0L
    private var failedCommands = 0L
    private var softRecoveries = 0

    fun reset(nowMillis: Long = System.currentTimeMillis()) {
        latencyWindow.clear()
        outcomeWindow.clear()
        startedAtMillis = nowMillis
        totalCommands = 0L
        failedCommands = 0L
        softRecoveries = 0
    }

    fun recordSuccess(latencyMillis: Long, nowMillis: Long = System.currentTimeMillis()): AdapterHealthState {
        ensureStarted(nowMillis)
        totalCommands += 1
        pushOutcome(true)
        latencyWindow.addLast(latencyMillis.coerceAtLeast(0L))
        while (latencyWindow.size > windowSize) latencyWindow.removeFirst()
        return snapshot(nowMillis)
    }

    fun recordFailure(nowMillis: Long = System.currentTimeMillis()): AdapterHealthState {
        ensureStarted(nowMillis)
        totalCommands += 1
        failedCommands += 1
        pushOutcome(false)
        return snapshot(nowMillis)
    }

    fun recordRecovery(nowMillis: Long = System.currentTimeMillis()): AdapterHealthState {
        ensureStarted(nowMillis)
        softRecoveries += 1
        return snapshot(nowMillis)
    }

    fun snapshot(nowMillis: Long = System.currentTimeMillis()): AdapterHealthState {
        ensureStarted(nowMillis)
        val averageLatency = latencyWindow.takeIf { it.isNotEmpty() }?.average()?.toLong() ?: 0L
        val successRate = outcomeWindow.takeIf { it.isNotEmpty() }
            ?.let { outcomes -> outcomes.count { it }.toDouble() / outcomes.size }
            ?: 1.0
        val elapsedSeconds = ((nowMillis - startedAtMillis).coerceAtLeast(1L)) / 1_000.0
        val commandsPerSecond = totalCommands / elapsedSeconds
        val grade = when {
            totalCommands < 5 -> AdapterHealthGrade.UNKNOWN
            successRate >= 0.97 && averageLatency in 1..180 -> AdapterHealthGrade.GOOD
            successRate >= 0.90 && averageLatency <= 450 -> AdapterHealthGrade.FAIR
            else -> AdapterHealthGrade.POOR
        }
        val recommended = when {
            grade == AdapterHealthGrade.POOR || averageLatency > 450 -> AdaptivePollingMode.STABLE
            grade == AdapterHealthGrade.GOOD && averageLatency in 1..110 && successRate >= 0.99 -> AdaptivePollingMode.FAST
            else -> AdaptivePollingMode.BALANCED
        }
        return AdapterHealthState(
            grade = grade,
            recommendedMode = recommended,
            averageLatencyMillis = averageLatency,
            successRate = successRate,
            commandsPerSecond = commandsPerSecond,
            totalCommands = totalCommands,
            failedCommands = failedCommands,
            softRecoveries = softRecoveries,
            updatedAtMillis = nowMillis,
        )
    }

    private fun pushOutcome(value: Boolean) {
        outcomeWindow.addLast(value)
        while (outcomeWindow.size > windowSize) outcomeWindow.removeFirst()
    }

    private fun ensureStarted(nowMillis: Long) {
        if (startedAtMillis == 0L) startedAtMillis = nowMillis
    }
}
