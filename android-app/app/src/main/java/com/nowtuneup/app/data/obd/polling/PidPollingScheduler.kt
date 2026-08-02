package com.nowtuneup.app.data.obd.polling

enum class PollingGroup { FAST, NORMAL, SLOW }

data class PollingSlot(val pid: Int, val group: PollingGroup)

class PidPollingScheduler(supportedPids: Set<Int>) {
    private val schedule: List<PollingSlot> = buildList {
        val fast = listOf(0x0C, 0x0D, 0x11).filter(supportedPids::contains)
        val normal = listOf(0x04, 0x05, 0x0B, 0x0F, 0x10).filter(supportedPids::contains)
        val slow = listOf(0x2F, 0x42, 0x33).filter(supportedPids::contains)

        repeat(3) { addAll(fast.map { PollingSlot(it, PollingGroup.FAST) }) }
        repeat(2) { addAll(normal.map { PollingSlot(it, PollingGroup.NORMAL) }) }
        addAll(slow.map { PollingSlot(it, PollingGroup.SLOW) })
    }
    private var index = 0

    fun next(): PollingSlot? {
        if (schedule.isEmpty()) return null
        val slot = schedule[index % schedule.size]
        index = (index + 1) % schedule.size
        return slot
    }

    fun isEmpty(): Boolean = schedule.isEmpty()

    fun snapshot(): List<PollingSlot> = schedule.toList()
}
