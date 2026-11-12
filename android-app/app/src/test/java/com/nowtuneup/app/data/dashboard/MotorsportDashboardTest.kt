package com.nowtuneup.app.data.dashboard

import com.nowtuneup.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class MotorsportDashboardTest {
    @Test fun disconnectedAndStaleValuesAreNeverDisplayed() {
        val reading = VehicleReading(0x0C, "RPM", 5000.0, "rpm")
        DataFreshness.entries.filter { it !in listOf(DataFreshness.LIVE, DataFreshness.DELAYED) }.forEach {
            assertNull(displayableReading(reading, it))
        }
        assertEquals(reading, displayableReading(reading, DataFreshness.LIVE))
        assertNull(displayableReading(reading.copy(value = Double.NaN), DataFreshness.LIVE))
    }
    @Test fun shiftStagesRequireDriverConfigurationAndActualRpm() {
        assertNull(ShiftLightConfig().stage(6000.0))
        val settings = ShiftLightConfig(true, listOf(3000.0, 4000.0, 5000.0), 6000.0)
        assertEquals(0, settings.stage(1000.0))
        assertEquals(2, settings.stage(4500.0))
        assertEquals(3, settings.stage(6000.0))
        assertNull(settings.stage(null))
        assertNull(settings.stage(Double.NaN))
        assertFalse(settings.copy(thresholdsRpm = listOf(4000.0, 3000.0)).valid())
    }
    @Test fun templatesRoundTripWithoutChangingFactoryOrEmptyProfiles() {
        val presets = DashboardDefaults.presets()
        assertEquals(listOf("Daily", "Engine", "Performance"), presets.map { it.name })
        presets.forEach { template ->
            assertTrue(DashboardCodec.import(DashboardCodec.export(template)).isSuccess)
            assertNotEquals(template.portrait, template.landscape)
        }
        assertTrue(DashboardDefaults.newProfile().portrait.widgets.isEmpty())
        assertEquals(presets, DashboardDefaults.presets())
    }
    @Test fun rejectsFutureSchemaAndInvalidShiftConfiguration() {
        val profile = DashboardDefaults.newProfile()
        assertTrue(DashboardCodec.import(DashboardCodec.export(profile).replace("\"schemaVersion\":2", "\"schemaVersion\":999")).isFailure)
        val widget = DashboardDefaults.widgetCatalog.first { it.pid == 0x0C }.copy(
            type = DashboardWidgetType.SHIFT_LIGHT,
            shiftLight = ShiftLightConfig(true, listOf(5000.0, 4000.0)))
        assertTrue(DashboardCodec.import(DashboardCodec.export(profile.copy(portrait = DashboardLayout(widgets = listOf(widget))))).isFailure)
    }
}
