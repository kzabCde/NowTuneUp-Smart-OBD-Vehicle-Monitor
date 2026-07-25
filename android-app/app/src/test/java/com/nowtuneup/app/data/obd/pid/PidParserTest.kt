package com.nowtuneup.app.data.obd.pid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PidParserTest {
    @Test
    fun rpm() = assertEquals(1726.0, StandardPids.parse(0x0C, listOf(0x1A, 0xF8)), 0.0)

    @Test
    fun speed() = assertEquals(60.0, StandardPids.parse(0x0D, listOf(0x3C)), 0.0)

    @Test
    fun temperature() = assertEquals(83.0, StandardPids.parse(0x05, listOf(0x7B)), 0.0)

    @Test
    fun voltage() = assertEquals(13.856, StandardPids.parse(0x42, listOf(0x36, 0x20)), 0.0001)

    @Test
    fun mapAndBarometricPressure() {
        assertEquals(160.0, StandardPids.parse(DerivedPids.MAP, listOf(160)), 0.0)
        assertEquals(100.0, StandardPids.parse(DerivedPids.BAROMETRIC_PRESSURE, listOf(100)), 0.0)
        assertEquals(60.0, DerivedPids.turboPressureKpa(160.0, 100.0), 0.0)
    }

    @Test
    fun vacuumProducesNegativeGaugePressure() {
        assertEquals(-65.0, DerivedPids.turboPressureKpa(35.0, 100.0), 0.0)
    }

    @Test
    fun supportedMask() {
        val found = SupportedPidParser.parse(0, listOf(0xBE, 0x1F, 0xA8, 0x13))
        assertTrue(1 in found)
        assertTrue(0x0C in found)
        assertFalse(2 in found)
    }

    @Test(expected = IllegalArgumentException::class)
    fun insufficientBytes() {
        StandardPids.parse(0x0C, listOf(1))
    }
}
