package com.nowtuneup.app.data.obd.polling

enum class PollingGroup { FAST, NORMAL, SLOW }

data class PollingSlot(val pid: Int, val group: PollingGroup)

/**
 * Demand-aware PID scheduler.
 *
 * The dashboard asks only for PIDs that are currently useful. Live Data can request all supported
 * PIDs, while Time Slip uses its dedicated speed-priority schedule in ObdSessionManager.
 */
class PidPollingScheduler(
    private val supportedPids: Set<Int>,
    requestedPids: Set<Int>? = null,
) {
    private var requested: Set<Int>? = requestedPids
    private var schedule: List<PollingSlot> = buildInterleavedSchedule(resolveDemand())
    private var index = 0

    @Synchronized
    fun updateDemand(requestedPids: Set<Int>?) {
        val normalized = requestedPids?.toSet()
        if (normalized == requested) return
        requested = normalized
        schedule = buildInterleavedSchedule(resolveDemand())
        index = 0
    }

    @Synchronized
    fun next(): PollingSlot? {
        if (schedule.isEmpty()) return null
        val slot = schedule[index]
        index = (index + 1) % schedule.size
        return slot
    }

    @Synchronized
    fun isEmpty(): Boolean = schedule.isEmpty()

    @Synchronized
    fun snapshot(): List<PollingSlot> = schedule.toList()

    private fun resolveDemand(): Set<Int> {
        val requestedPids = requested
        if (requestedPids == null) return supportedPids
        val supportedRequested = requestedPids.intersect(supportedPids)
        if (supportedRequested.isNotEmpty()) return supportedRequested
        return DEFAULT_CORE_PIDS.intersect(supportedPids)
    }

    private fun buildInterleavedSchedule(activePids: Set<Int>): List<PollingSlot> {
        if (activePids.isEmpty()) return emptyList()

        val fast = activePids.filter { it in FAST_PIDS }.sorted()
        val normal = activePids.filter { it in NORMAL_PIDS || it !in FAST_PIDS + SLOW_PIDS }.sorted()
        val slow = activePids.filter { it in SLOW_PIDS }.sorted()

        val rounds = maxOf(
            fast.size * FAST_WEIGHT,
            normal.size * NORMAL_WEIGHT,
            slow.size * SLOW_WEIGHT,
            1,
        )
        var fastIndex = 0
        var normalIndex = 0
        var slowIndex = 0

        return buildList {
            repeat(rounds) { round ->
                if (fast.isNotEmpty()) {
                    add(PollingSlot(fast[fastIndex % fast.size], PollingGroup.FAST))
                    fastIndex += 1
                }
                if (normal.isNotEmpty() && (fast.isEmpty() || round % NORMAL_INTERVAL == NORMAL_INTERVAL - 1)) {
                    add(PollingSlot(normal[normalIndex % normal.size], PollingGroup.NORMAL))
                    normalIndex += 1
                }
                if (
                    slow.isNotEmpty() &&
                    ((fast.isEmpty() && normal.isEmpty()) || round % SLOW_INTERVAL == SLOW_INTERVAL - 1)
                ) {
                    add(PollingSlot(slow[slowIndex % slow.size], PollingGroup.SLOW))
                    slowIndex += 1
                }
            }
        }
    }

    private companion object {
        val FAST_PIDS = setOf(0x0C, 0x0D, 0x11)
        val NORMAL_PIDS = setOf(0x04, 0x05, 0x0B, 0x0F, 0x10)
        val SLOW_PIDS = setOf(0x2F, 0x42, 0x33)
        val DEFAULT_CORE_PIDS = setOf(0x0C, 0x0D, 0x05)
        const val FAST_WEIGHT = 8
        const val NORMAL_WEIGHT = 3
        const val SLOW_WEIGHT = 1
        const val NORMAL_INTERVAL = 3
        const val SLOW_INTERVAL = 8
    }
}
