package com.nowtuneup.app.domain.connection

import org.junit.Assert.assertEquals
import org.junit.Test

class ReconnectBackoffTest {
    @Test
    fun doublesUntilMaximumDelay() {
        assertEquals(3, ReconnectBackoff.delaySeconds(3, 30, 0))
        assertEquals(6, ReconnectBackoff.delaySeconds(3, 30, 1))
        assertEquals(12, ReconnectBackoff.delaySeconds(3, 30, 2))
        assertEquals(24, ReconnectBackoff.delaySeconds(3, 30, 3))
        assertEquals(30, ReconnectBackoff.delaySeconds(3, 30, 4))
        assertEquals(30, ReconnectBackoff.delaySeconds(3, 30, 12))
    }
}
