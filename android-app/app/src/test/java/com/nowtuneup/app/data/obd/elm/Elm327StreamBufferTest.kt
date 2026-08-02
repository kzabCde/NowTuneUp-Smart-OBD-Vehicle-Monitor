package com.nowtuneup.app.data.obd.elm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Elm327StreamBufferTest {
    @Test
    fun fragmentedResponseCompletesOnlyAfterPrompt() {
        val buffer = Elm327StreamBuffer()
        buffer.append("41 0C")
        assertNull(buffer.pollResponse())
        buffer.append(" 1A")
        assertNull(buffer.pollResponse())
        buffer.append(" F8\r>")
        assertEquals("41 0C 1A F8\r>", buffer.pollResponse())
    }

    @Test
    fun preservesSecondResponseAlreadyInBuffer() {
        val buffer = Elm327StreamBuffer()
        buffer.append("OK\r>41 0D 28\r>")
        assertEquals("OK\r>", buffer.pollResponse())
        assertEquals("41 0D 28\r>", buffer.pollResponse())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnboundedBufferGrowth() {
        val buffer = Elm327StreamBuffer(maximumCharacters = 8)
        buffer.append("123456789")
    }
}
