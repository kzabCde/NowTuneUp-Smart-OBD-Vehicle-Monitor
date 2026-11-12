package com.nowtuneup.app.util

import com.nowtuneup.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class DisplayUnitIntegrityTest {
    @Test fun incompatibleUnitsNeverRelabelAMeasurement() {
        val rpm = VehicleReading(0x0C, "RPM", 2000.0, "rpm")
        assertNull(DisplayReadingAdapter.reading(rpm, DisplayUnit.KMH)?.value)
        assertNull(DisplayReadingAdapter.stats(ReadingStats(maximum = 2000.0), "rpm", DisplayUnit.CELSIUS))
        assertEquals(listOf(DisplayUnit.RPM), UnitConverter.compatibleUnits(DisplayUnit.RPM))
    }
    @Test fun allPressureConversionsPreserveThePhysicalValue() {
        val psi = UnitConverter.convert(1.0, DisplayUnit.BAR, DisplayUnit.PSI)
        assertEquals(14.5037738, psi, 0.000001)
        assertEquals(1.0, UnitConverter.convert(psi, DisplayUnit.PSI, DisplayUnit.BAR), 0.000001)
        assertEquals(100.0, UnitConverter.convert(psi, DisplayUnit.PSI, DisplayUnit.KPA), 0.000001)
    }
}
