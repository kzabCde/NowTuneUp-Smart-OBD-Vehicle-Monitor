package com.nowtuneup.app.data.dashboard

import com.nowtuneup.app.BuildConfig
import com.nowtuneup.app.domain.model.ColorConfig
import com.nowtuneup.app.domain.model.DashboardConfig
import com.nowtuneup.app.domain.model.DashboardLayout
import com.nowtuneup.app.domain.model.DashboardMode
import com.nowtuneup.app.domain.model.DashboardWidgetConfig
import com.nowtuneup.app.domain.model.DashboardWidgetType
import com.nowtuneup.app.domain.model.DisplayUnit
import com.nowtuneup.app.domain.model.GaugeStyle
import com.nowtuneup.app.domain.model.WarningThreshold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardCodecTest {
    @Test
    fun releaseVersionIs121() {
        assertEquals("1.2.1", BuildConfig.VERSION_NAME)
    }

    @Test
    fun roundTripPreservesEditorConfiguration() {
        val widget = DashboardWidgetConfig(
            id = "rpm",
            pid = 0x0C,
            type = DashboardWidgetType.ANALOG,
            title = "Engine RPM",
            unit = DisplayUnit.RPM,
            decimals = 0,
            valueSize = 46,
            columnSpan = 2,
            rowSpan = 3,
            gaugeStyle = GaugeStyle.SPORT,
            colors = ColorConfig(
                value = 0xFFFF1744,
                label = 0xFFFFFFFF,
                background = 0xFF090D12,
                border = 0xFF334155,
                warning = 0xFFFFB300,
                critical = 0xFFFF5252,
            ),
            threshold = WarningThreshold(warningHigh = 5_500.0, criticalHigh = 6_500.0),
        )
        val config = DashboardConfig(
            id = "track",
            name = "Track dashboard",
            mode = DashboardMode.ANALOG,
            portrait = DashboardLayout(columns = 3, widgets = listOf(widget)),
        )

        val imported = DashboardCodec.import(DashboardCodec.export(config)).getOrThrow()

        assertEquals(config, imported)
        assertEquals(GaugeStyle.SPORT, imported.portrait.widgets.single().gaugeStyle)
        assertEquals(3, imported.portrait.widgets.single().rowSpan)
        assertEquals(6_500.0, imported.portrait.widgets.single().threshold.criticalHigh)
    }

    @Test
    fun digitalCanBecomeAnalogWithoutChangingParameter() {
        val widget = DashboardDefaults.presets.first().portrait.widgets[1]
        val analog = widget.copy(type = DashboardWidgetType.ANALOG)
        assertEquals(widget.pid, analog.pid)
        assertEquals(DashboardWidgetType.ANALOG, analog.type)
    }

    @Test
    fun importRejectsInvalidWidgetHeight() {
        val invalid = DashboardDefaults.presets.first().let { preset ->
            preset.copy(
                portrait = preset.portrait.copy(
                    widgets = preset.portrait.widgets.mapIndexed { index, widget ->
                        if (index == 0) widget.copy(rowSpan = 9) else widget
                    },
                ),
            )
        }

        assertTrue(DashboardCodec.import(DashboardCodec.export(invalid)).isFailure)
    }
}
