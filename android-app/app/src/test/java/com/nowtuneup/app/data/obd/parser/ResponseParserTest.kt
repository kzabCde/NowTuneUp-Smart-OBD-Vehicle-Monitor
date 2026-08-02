package com.nowtuneup.app.data.obd.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseParserTest {
    @Test
    fun removesEchoAndPrompt() {
        val normalized = ObdResponseParser.normalize("010C\r41 0C 1A F8\r>", "010C").getOrThrow()
        assertEquals(listOf(0x41, 0x0C, 0x1A, 0xF8), normalized.frames.single())
    }

    @Test
    fun acceptsCompactFixtures() {
        assertEquals(1726.0, ObdResponseParser.parseMode1("410C1AF8>", 0x0C).getOrThrow(), 0.0)
        assertEquals(40.0, ObdResponseParser.parseMode1("410D28>", 0x0D).getOrThrow(), 0.0)
        assertEquals(50.0, ObdResponseParser.parseMode1("41055A>", 0x05).getOrThrow(), 0.0)
        assertEquals(4.0, ObdResponseParser.parseMode1("41420FA0>", 0x42).getOrThrow(), 0.0)
    }

    @Test
    fun knownAdapterErrorsFail() {
        assertTrue(ObdResponseParser.normalize("NO DATA\r>").isFailure)
        assertTrue(ObdResponseParser.normalize("UNABLE TO CONNECT\r>").isFailure)
        assertTrue(ObdResponseParser.normalize("BUFFER FULL\r>").isFailure)
    }

    @Test
    fun invalidHexFails() {
        assertTrue(ObdResponseParser.normalize("41 0C ZZ\r>").isFailure)
    }

    @Test
    fun incompleteRpmPayloadFailsWithoutCrash() {
        assertTrue(ObdResponseParser.parseMode1("410C1A>", 0x0C).isFailure)
    }

    @Test
    fun negativeEcuResponseFailsWithoutCrash() {
        assertTrue(ObdResponseParser.parseMode1("7F0112>", 0x0C).isFailure)
    }

    @Test
    fun searchingIsInformational() {
        val normalized = ObdResponseParser.normalize("SEARCHING...\r41 0D 3C\r>").getOrThrow()
        assertEquals(1, normalized.informational.size)
        assertEquals(60.0, ObdResponseParser.parseMode1("SEARCHING...\r41 0D 3C\r>", 0x0D).getOrThrow(), 0.0)
    }

    @Test
    fun multipleEcus() {
        val normalized = ObdResponseParser.normalize("41 0D 3C\r41 0D 3D\r>").getOrThrow()
        assertEquals(2, normalized.frames.size)
    }

    @Test
    fun mismatchedPidFails() {
        assertTrue(ObdResponseParser.parseMode1("41 0D 3C>", 0x0C).isFailure)
    }
}
