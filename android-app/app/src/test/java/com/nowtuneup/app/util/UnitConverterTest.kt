package com.nowtuneup.app.util

import com.nowtuneup.app.domain.model.DisplayUnit
import com.nowtuneup.app.domain.model.WarningThreshold
import org.junit.Assert.assertEquals
import org.junit.Test

class UnitConverterTest {
    @Test fun convertsSpeedAndTemperature() {
        assertEquals(62.137, UnitConverter.convert(100.0, DisplayUnit.KMH, DisplayUnit.MPH), .001)
        assertEquals(212.0, UnitConverter.convert(100.0, DisplayUnit.CELSIUS, DisplayUnit.FAHRENHEIT), .001)
    }

    @Test fun convertsAllThresholdsWithUnits() {
        val converted = UnitConverter.convert(WarningThreshold(0.0, 100.0, -10.0, 110.0), DisplayUnit.CELSIUS, DisplayUnit.FAHRENHEIT)
        assertEquals(32.0, converted.warningLow!!, .001)
        assertEquals(230.0, converted.criticalHigh!!, .001)
    }
}
