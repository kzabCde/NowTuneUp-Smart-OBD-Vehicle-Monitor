package com.nowtuneup.app.data.dashboard

import com.nowtuneup.app.BuildConfig
import com.nowtuneup.app.domain.model.ColorConfig
import com.nowtuneup.app.domain.model.DashboardConfig
import com.nowtuneup.app.domain.model.DashboardLayout
import com.nowtuneup.app.domain.model.DashboardMode
import com.nowtuneup.app.domain.model.DashboardWidgetConfig
import com.nowtuneup.app.domain.model.DashboardWidgetType
import com.nowtuneup.app.domain.model.DigitalRingColorPreset
import com.nowtuneup.app.domain.model.DigitalRingConfig
import com.nowtuneup.app.domain.model.DisplayUnit
import com.nowtuneup.app.domain.model.GaugeStyle
import com.nowtuneup.app.domain.model.WarningThreshold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardCodecTest {
    @Test
    fun releaseVersionIs131() {
        assertEquals("1.3.1", BuildConfig.VERSION_NAME)
    }

    @Test
    fun roundTripPreservesIndependentResponsiveLayouts() {
        val portraitWidget = DashboardWidgetConfig(
            id = "speed",
            pid = 0x0D,
            type = DashboardWidgetType.DIGITAL,
            title = "Speed",
            unit = DisplayUnit.KMH,
            columnSpan = 2,
        )
        val landscapeWidget = portraitWidget.copy(
            type = DashboardWidgetType.DIGITAL_RING,
            columnSpan = 1,
            rowSpan = 2,
            digitalRing = DigitalRingConfig(
                preset = DigitalRingColorPreset.CYAN,
                segmentCount = 48,
            ),
        )
        val config = DashboardConfig(
            id = "responsive",
            name = "Responsive dashboard",
            mode = DashboardMode.HYBRID,
            portrait = DashboardLayout(columns = 2, widgets = listOf(portraitWidget)),
            landscape = DashboardLayout(columns = 4, widgets = listOf(landscapeWidget)),
        )

        val imported = DashboardCodec.import(DashboardCodec.export(config)).getOrThrow()

        assertEquals(2, imported.portrait.columns)
        assertEquals(4, imported.landscape.columns)
        assertEquals(DashboardWidgetType.DIGITAL, imported.portrait.widgets.single().type)
        assertEquals(DashboardWidgetType.DIGITAL_RING, imported.landscape.widgets.single().type)
        assertEquals(48, imported.landscape.widgets.single().digitalRing?.segmentCount)
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

    @Test
    fun importRejectsInvalidDigitalRingSegmentCount() {
        val invalidWidget = DashboardDefaults.presets.first().portrait.widgets.first().copy(
            type = DashboardWidgetType.DIGITAL_RING,
            digitalRing = DigitalRingConfig(segmentCount = 100),
        )
        val invalid = DashboardDefaults.presets.first().copy(
            portrait = DashboardLayout(columns = 2, widgets = listOf(invalidWidget)),
        )
        assertTrue(DashboardCodec.import(DashboardCodec.export(invalid)).isFailure)
    }
}
