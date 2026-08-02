package com.nowtuneup.app.data.dashboard

import com.nowtuneup.app.BuildConfig
import com.nowtuneup.app.domain.model.BezelFinish
import com.nowtuneup.app.domain.model.ColorConfig
import com.nowtuneup.app.domain.model.DashboardConfig
import com.nowtuneup.app.domain.model.DashboardLayout
import com.nowtuneup.app.domain.model.DashboardMode
import com.nowtuneup.app.domain.model.DashboardWidgetConfig
import com.nowtuneup.app.domain.model.DashboardWidgetType
import com.nowtuneup.app.domain.model.DigitalRingColorPreset
import com.nowtuneup.app.domain.model.DigitalRingConfig
import com.nowtuneup.app.domain.model.DisplayUnit
import com.nowtuneup.app.domain.model.GaugeSmoothing
import com.nowtuneup.app.domain.model.GaugeStyle
import com.nowtuneup.app.domain.model.WarningThreshold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardCodecTest {
    @Test
    fun releaseVersionIs161() {
        assertEquals("1.6.1", BuildConfig.VERSION_NAME)
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
    fun roundTripPreservesPremiumEditorConfiguration() {
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
            gaugeStyle = GaugeStyle.SPORT_RED,
            bezelFinish = BezelFinish.BLACK_CHROME,
            gaugeSmoothing = GaugeSmoothing.FAST,
            showPeakMarker = true,
            colors = ColorConfig(
                value = 0xFFFF1744,
                label = 0xFFFFFFFF,
                background = 0xFF090D12,
                border = 0xFF334155,
                warning = 0xFFFFB300,
                critical = 0xFFFF5252,
                face = 0xFF08080A,
                bezel = 0xFF25262A,
                tick = 0xFFFFE8E8,
                needle = 0xFFFF1744,
                needleHighlight = 0xFFFFA0AF,
                glow = 0x88FF1744,
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
        assertEquals(GaugeStyle.SPORT_RED, imported.portrait.widgets.single().gaugeStyle)
        assertEquals(BezelFinish.BLACK_CHROME, imported.portrait.widgets.single().bezelFinish)
        assertEquals(0xFFFF1744, imported.portrait.widgets.single().colors.needle)
        assertEquals(6_500.0, imported.portrait.widgets.single().threshold.criticalHigh)
    }

    @Test
    fun legacyGaugeStylesMigrateToColoredPremiumStyles() {
        val mappings = mapOf(
            "CLASSIC" to GaugeStyle.CLASSIC_METAL,
            "SPORT" to GaugeStyle.SPORT_RED,
            "MINIMAL" to GaugeStyle.CLASSIC_METAL,
            "NEON" to GaugeStyle.NEO_CYAN,
            "OEM" to GaugeStyle.OEM_BLUE,
        )

        mappings.forEach { (legacy, expected) ->
            val json = legacyDashboardJson(legacy)
            val imported = DashboardCodec.import(json).getOrThrow()
            assertEquals(expected, imported.portrait.widgets.single().gaugeStyle)
        }
    }

    @Test
    fun unknownGaugeStyleFallsBackToClassicMetal() {
        val imported = DashboardCodec.import(legacyDashboardJson("UNKNOWN_STYLE")).getOrThrow()
        assertEquals(GaugeStyle.CLASSIC_METAL, imported.portrait.widgets.single().gaugeStyle)
    }

    @Test
    fun unknownDigitalRingPresetFallsBackToCyan() {
        val json = """
            {
              "id":"ring",
              "name":"Ring",
              "mode":"HYBRID",
              "portrait":{"columns":2,"widgets":[{
                "id":"turbo","pid":65537,"type":"DIGITAL_RING","title":"Turbo","unit":"PSI",
                "decimals":1,"valueSize":58,"column":0,"row":0,"columnSpan":2,"rowSpan":2,
                "gaugeStyle":"NEO_CYAN","colors":{"value":4294967295,"label":4294967295,"background":4278190080,"border":4281348144,"warning":4294947584,"critical":4294923602},
                "threshold":{},"digitalRing":{"preset":"UNKNOWN","segmentCount":48,"digitColor":4278249727,"activeSegmentColor":4278249727,"inactiveSegmentColor":4278208071,"scaleColor":4278249727,"titleColor":4278249727,"bezelColor":4281348144,"showScaleLabels":true}
              }]},
              "landscape":{"columns":3,"widgets":[]},"isDefault":false
            }
        """.trimIndent()
        val imported = DashboardCodec.import(json).getOrThrow()
        assertEquals(DigitalRingColorPreset.CYAN, imported.portrait.widgets.single().digitalRing?.preset)
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

    private fun legacyDashboardJson(style: String): String = """
        {
          "id":"legacy",
          "name":"Legacy dashboard",
          "mode":"ANALOG",
          "portrait":{"columns":2,"widgets":[{
            "id":"rpm","pid":12,"type":"ANALOG","title":"RPM","unit":"RPM",
            "decimals":0,"valueSize":42,"column":0,"row":0,"columnSpan":1,"rowSpan":2,
            "gaugeStyle":"$style",
            "colors":{"value":4294967295,"label":4291811532,"background":4279372051,"border":4281545525,"warning":4294947584,"critical":4294923602},
            "threshold":{}
          }]},
          "landscape":{"columns":3,"widgets":[]},"isDefault":false
        }
    """.trimIndent()
}
