package com.nowtuneup.app.data.obd.polling

enum class PollingGroup { FAST, NORMAL, SLOW }

data class PollingSlot(val pid: Int, val group: PollingGroup)

/**
 * Produces a deterministic interleaved schedule optimized for dashboard responsiveness.
 *
 * RPM, speed and throttle are intentionally queried much more often than temperatures, voltage
 * and fuel level. Lower-priority groups are still rotated fairly and never removed from polling.
 */
class PidPollingScheduler(supportedPids: Set<Int>) {
    private val schedule: List<PollingSlot> = buildInterleavedSchedule(supportedPids)
    private var index = 0

    fun next(): PollingSlot? {
        if (schedule.isEmpty()) return null
        val slot = schedule[index]
        index = (index + 1) % schedule.size
        return slot
    }

    fun isEmpty(): Boolean = schedule.isEmpty()

    fun snapshot(): List<PollingSlot> = schedule.toList()

    private fun buildInterleavedSchedule(supportedPids: Set<Int>): List<PollingSlot> {
        val fast = listOf(0x0C, 0x0D, 0x11).filter(supportedPids::contains)
        val normal = listOf(0x04, 0x05, 0x0B, 0x0F, 0x10).filter(supportedPids::contains)
        val slow = listOf(0x2F, 0x42, 0x33).filter(supportedPids::contains)
        if (fast.isEmpty() && normal.isEmpty() && slow.isEmpty()) return emptyList()

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
        const val FAST_WEIGHT = 8
        const val NORMAL_WEIGHT = 3
        const val SLOW_WEIGHT = 1
        const val NORMAL_INTERVAL = 3
        const val SLOW_INTERVAL = 8
    }
}
