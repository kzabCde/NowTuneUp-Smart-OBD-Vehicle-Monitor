package com.nowtuneup.app.data.obd.polling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PidPollingSchedulerTest {
    @Test
    fun pollsOnlySupportedPids() {
        val supported = setOf(0x0C, 0x0D, 0x05, 0x42)
        val scheduler = PidPollingScheduler(supported)
        assertTrue(scheduler.snapshot().all { it.pid in supported })
        assertFalse(scheduler.isEmpty())
    }

    @Test
    fun fastPidsReceiveGreaterWeight() {
        val scheduler = PidPollingScheduler(setOf(0x0C, 0x05, 0x42))
        val slots = scheduler.snapshot()
        assertTrue(slots.count { it.pid == 0x0C } > slots.count { it.pid == 0x05 })
        assertTrue(slots.count { it.pid == 0x05 } > slots.count { it.pid == 0x42 })
    }

    @Test
    fun normalAndSlowPidsAreInterleavedBetweenFastRequests() {
        val scheduler = PidPollingScheduler(setOf(0x0C, 0x0D, 0x11, 0x05, 0x0B, 0x42))
        val slots = scheduler.snapshot()
        val firstNormal = slots.indexOfFirst { it.group == PollingGroup.NORMAL }
        val firstSlow = slots.indexOfFirst { it.group == PollingGroup.SLOW }

        assertTrue(firstNormal in 1 until slots.lastIndex)
        assertTrue(firstSlow in 1 until slots.lastIndex)
        assertTrue(slots.take(firstSlow).any { it.group == PollingGroup.FAST })
    }

    @Test
    fun nextWrapsWithoutChangingDeterministicOrder() {
        val scheduler = PidPollingScheduler(setOf(0x0C, 0x05, 0x42))
        val expected = scheduler.snapshot()
        val actual = List(expected.size * 2) { scheduler.next() }

        assertEquals(expected + expected, actual)
    }

    @Test
    fun emptySupportSetDoesNotProduceRequests() {
        val scheduler = PidPollingScheduler(emptySet())
        assertTrue(scheduler.isEmpty())
        assertEquals(null, scheduler.next())
    }
}
