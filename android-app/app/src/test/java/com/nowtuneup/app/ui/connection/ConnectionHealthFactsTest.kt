package com.nowtuneup.app.ui.connection

import com.nowtuneup.app.data.obd.session.AdapterHealthState
import org.junit.Assert.*
import org.junit.Test

class ConnectionHealthFactsTest {
    @Test fun unmeasuredAdapterDoesNotAdvertiseDefaultHundredPercentSuccess() {
        assertTrue(connectionHealthFacts(AdapterHealthState()).isEmpty())
        assertTrue(connectionHealthFacts(AdapterHealthState(totalCommands = 10, updatedAtMillis = 0)).isEmpty())
    }

    @Test fun actualObservedMetricsKeepUnitsAndCounts() {
        val facts = connectionHealthFacts(AdapterHealthState(
            totalCommands = 40, failedCommands = 2, softRecoveries = 1,
            averageLatencyMillis = 123, successRate = 0.95, commandsPerSecond = 2.4, updatedAtMillis = 1,
        )).associate { it.label to it.value }
        assertEquals("123 ms", facts["Latency เฉลี่ย"])
        assertEquals("95.0 %", facts["คำสั่งสำเร็จ"])
        assertEquals("2.4 คำสั่ง/s", facts["ความถี่คำสั่งที่วัดได้"])
        assertEquals("40", facts["คำสั่งที่ส่ง"])
        assertEquals("2", facts["คำสั่งล้มเหลว"])
        assertEquals("1", facts["การกู้การเชื่อมต่อ"])
    }

    @Test fun nonFiniteOrInvalidMetricsAreUnavailable() {
        val facts = connectionHealthFacts(AdapterHealthState(
            totalCommands = 1, averageLatencyMillis = -1, successRate = Double.NaN,
            commandsPerSecond = Double.POSITIVE_INFINITY, updatedAtMillis = 1,
        )).associate { it.label to it.value }
        assertEquals("—", facts["Latency เฉลี่ย"])
        assertEquals("—", facts["คำสั่งสำเร็จ"])
        assertEquals("—", facts["ความถี่คำสั่งที่วัดได้"])
    }
}
