package com.nowtuneup.app.data.obd.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleIntelligenceParserTest {
    @Test
    fun parsesVinAcrossMode09Frames() {
        val raw = """
            49 02 01 31 48 47 42 48 34
            49 02 02 31 4A 58 4D 4E 31
            49 02 03 30 39 31 38 36
            >
        """.trimIndent()

        assertEquals("1HGBH41JXMN109186", VinParser.parse(raw))
    }

    @Test
    fun parsesReadinessMilAndDtcCount() {
        val readiness = ReadinessParser.parse("41 01 82 07 E5 E5 >")
        assertNotNull(readiness)
        assertTrue(readiness!!.milOn)
        assertEquals(2, readiness.dtcCount)
        assertEquals("07E5E5", readiness.rawMonitorBytes)
    }

    @Test
    fun parsesPendingAndPermanentStatuses() {
        val pending = DtcParser.parse("47 01 33 00 00 >", "07", 0x47, "Pending")
        val permanent = DtcParser.parse("4A 04 20 >", "0A", 0x4A, "Permanent")

        assertEquals("P0133", pending.single().code)
        assertEquals("Pending", pending.single().status)
        assertEquals("P0420", permanent.single().code)
        assertEquals("Permanent", permanent.single().status)
    }

    @Test
    fun parsesFreezeFrameTriggerDtc() {
        val freeze = FreezeFrameParser.parse("42 02 00 03 00 >")
        assertNotNull(freeze)
        assertEquals("P0300", freeze!!.triggerDtc)
    }

    @Test
    fun vinParserRejectsInvalidShortIdentity() {
        assertFalse(VinParser.parse("49 02 01 31 32 33 >") != null)
    }
}
