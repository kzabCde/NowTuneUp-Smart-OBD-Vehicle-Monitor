package com.nowtuneup.app.data.obd.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Mode06ParserTest {
    @Test
    fun parsesMode06MonitorFrames() {
        val result = Mode06Parser.parse(
            raw = "46 00 80 00 00 01\r46 01 12 34 56 78\r>",
            nowMillis = 1234L,
        )
        assertNotNull(result)
        assertTrue(result!!.supported)
        assertEquals(2, result.monitorFrameCount)
        assertEquals(1234L, result.readAtMillis)
    }

    @Test
    fun rejectsNonMode06Response() {
        assertNull(Mode06Parser.parse("41 00 BE 3E B8 13\r>"))
    }
}
