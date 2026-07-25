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
    fun roundTripPreservesDigitalRingPresetAndCustomColors() {
        val ring = DigitalRingConfig(
            preset = DigitalRingColorPreset.CUSTOM,
            segmentCount = 48,
            digitColor = 0xFF00C8FF,
            activeSegmentColor = 0xFF00C8FF,
            inactiveSegmentColor = 0xFF003647,
            scaleColor = 0xFFFFFFFF,
            titleColor = 0xFF00C8FF,
            bezelColor = 0xFF252A30,
            showScaleLabels = false,
        )
        val widget = DashboardWidgetConfig(
            id = "boost-ring",
            pid = 0x0B,
            type = DashboardWidgetType.DIGITAL_RING,
            title = "Boost",
            unit = DisplayUnit.PSI,
            decimals = 1,
            valueSize = 58,
            columnSpan = 2,
            rowSpan = 2,
            digitalRing = ring,
        )
        val config = DashboardConfig(
            id = "ring-test",
            name = "Digital Ring",
            mode = DashboardMode.HYBRID,
            portrait = DashboardLayout(columns = 2, widgets = listOf(widget)),
        )

        val imported = DashboardCodec.import(DashboardCodec.export(config)).getOrThrow()
        val restored = imported.portrait.widgets.single()

        assertEquals(DashboardWidgetType.DIGITAL_RING, restored.type)
        assertEquals(DigitalRingColorPreset.CUSTOM, restored.digitalRing?.preset)
        assertEquals(48, restored.digitalRing?.segmentCount)
        assertEquals(0xFF00C8FF, restored.digitalRing?.activeSegmentColor)
        assertEquals(false, restored.digitalRing?.showScaleLabels)
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
