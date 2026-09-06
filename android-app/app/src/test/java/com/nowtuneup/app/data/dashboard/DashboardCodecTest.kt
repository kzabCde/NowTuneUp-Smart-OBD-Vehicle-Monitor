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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardCodecTest {
    @Test
    fun releaseVersionUses1130Base() {
        assertTrue(BuildConfig.VERSION_NAME.startsWith("1.13.0"))
    }

    @Test
    fun newProfileStartsEmptyWithoutBundledDashboard() {
        val profile = DashboardDefaults.newProfile(id = "test-profile")
        assertFalse(profile.isDefault)
        assertTrue(profile.portrait.widgets.isEmpty())
        assertTrue(profile.landscape.widgets.isEmpty())
        assertTrue(DashboardDefaults.widgetCatalog.isNotEmpty())
    }

    @Test
    fun catalogProvidesEditableVehicleScaleDefaults() {
        val speed = DashboardDefaults.widgetCatalog.first { it.pid == 0x0D }
        val rpm = DashboardDefaults.widgetCatalog.first { it.pid == 0x0C }
        assertEquals(0.0, speed.scaleMinimum)
        assertEquals(200.0, speed.scaleMaximum)
        assertEquals(0.0, rpm.scaleMinimum)
        assertEquals(7_000.0, rpm.scaleMaximum)
    }

    @Test
    fun roundTripPreservesIndependentResponsiveLayouts() {
        val portraitWidget = DashboardWidgetConfig(
            id = "speed", pid = 0x0D, type = DashboardWidgetType.DIGITAL,
            title = "Speed", unit = DisplayUnit.KMH, columnSpan = 2,
            scaleMinimum = 20.0, scaleMaximum = 240.0,
        )
        val landscapeWidget = portraitWidget.copy(
            type = DashboardWidgetType.DIGITAL_RING,
            columnSpan = 1,
            rowSpan = 2,
            digitalRing = DigitalRingConfig(preset = DigitalRingColorPreset.CYAN, segmentCount = 48),
        )
        val config = DashboardConfig(
            id = "responsive", name = "Responsive dashboard", mode = DashboardMode.HYBRID,
            portrait = DashboardLayout(columns = 2, widgets = listOf(portraitWidget)),
            landscape = DashboardLayout(columns = 4, widgets = listOf(landscapeWidget)),
        )
        val imported = DashboardCodec.import(DashboardCodec.export(config)).getOrThrow()
        assertEquals(2, imported.portrait.columns)
        assertEquals(4, imported.landscape.columns)
        assertEquals(DashboardWidgetType.DIGITAL, imported.portrait.widgets.single().type)
        assertEquals(20.0, imported.portrait.widgets.single().scaleMinimum)
        assertEquals(240.0, imported.portrait.widgets.single().scaleMaximum)
        assertEquals(DashboardWidgetType.DIGITAL_RING, imported.landscape.widgets.single().type)
        assertEquals(48, imported.landscape.widgets.single().digitalRing?.segmentCount)
    }

    @Test
    fun roundTripPreservesPremiumEditorConfigurationAndChosenColors() {
        val widget = DashboardWidgetConfig(
            id = "rpm", pid = 0x0C, type = DashboardWidgetType.ANALOG,
            title = "Engine RPM", unit = DisplayUnit.RPM, decimals = 0, valueSize = 46,
            columnSpan = 2, rowSpan = 3, gaugeStyle = GaugeStyle.CUSTOM,
            bezelFinish = BezelFinish.BLACK_CHROME, gaugeSmoothing = GaugeSmoothing.FAST,
            showPeakMarker = true,
            colors = ColorConfig(
                value = 0xFF35E6FF, label = 0xFFFFFFFF, background = 0xFF111827,
                border = 0xFFB0BEC5, warning = 0xFFFFB300, critical = 0xFFFF1744,
                face = 0xFF111827, bezel = 0xFFB0BEC5, tick = 0xFF35E6FF,
                needle = 0xFFFF1744, needleHighlight = 0xFFFFFFFF, glow = 0x8835E6FF,
            ),
            threshold = WarningThreshold(warningHigh = 5_500.0, criticalHigh = 6_500.0),
        )
        val config = DashboardConfig(
            id = "track", name = "Track dashboard", mode = DashboardMode.ANALOG,
            portrait = DashboardLayout(columns = 3, widgets = listOf(widget)),
        )
        val imported = DashboardCodec.import(DashboardCodec.export(config)).getOrThrow()
        assertEquals(config, imported)
        assertEquals(GaugeStyle.CUSTOM, imported.portrait.widgets.single().gaugeStyle)
        assertEquals(BezelFinish.BLACK_CHROME, imported.portrait.widgets.single().bezelFinish)
        assertEquals(0xFFFF1744, imported.portrait.widgets.single().colors.needle)
        assertEquals(0xFF35E6FF, imported.portrait.widgets.single().colors.value)
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
            val imported = DashboardCodec.import(legacyDashboardJson(legacy)).getOrThrow()
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
              "id":"ring","name":"Ring","mode":"HYBRID",
              "portrait":{"columns":2,"widgets":[{
                "id":"turbo","pid":65537,"type":"DIGITAL_RING","title":"Turbo","unit":"PSI",
                "decimals":1,"valueSize":58,"column":0,"row":0,"columnSpan":2,"rowSpan":2,
                "gaugeStyle":"NEO_CYAN","colors":{"value":4294967295,"label":4294967295,"background":4278190080,"border":4281348144,"warning":4294947584,"critical":4294923602},
                "threshold":{},"digitalRing":{"preset":"UNKNOWN","segmentCount":48,"digitColor":4278249727,"activeSegmentColor":4278249727,"inactiveSegmentColor":4278208071,"scaleColor":4278249727,"titleColor":4278249727,"bezelColor":4281348144,"showScaleLabels":true}
              }]},"landscape":{"columns":3,"widgets":[]},"isDefault":false
            }
        """.trimIndent()
        val imported = DashboardCodec.import(json).getOrThrow()
        assertEquals(DigitalRingColorPreset.CYAN, imported.portrait.widgets.single().digitalRing?.preset)
    }

    @Test
    fun importRejectsInvalidWidgetHeight() {
        val invalidWidget = DashboardDefaults.widgetCatalog.first().copy(rowSpan = 9)
        val invalid = DashboardDefaults.newProfile(id = "invalid-height").copy(
            portrait = DashboardLayout(columns = 2, widgets = listOf(invalidWidget)),
        )
        assertTrue(DashboardCodec.import(DashboardCodec.export(invalid)).isFailure)
    }

    @Test
    fun importRejectsIncompleteOrReversedWidgetScale() {
        val base = DashboardDefaults.widgetCatalog.first()
        val incomplete = DashboardDefaults.newProfile(id = "invalid-scale-incomplete").copy(
            portrait = DashboardLayout(columns = 2, widgets = listOf(base.copy(scaleMinimum = 0.0, scaleMaximum = null))),
        )
        val reversed = DashboardDefaults.newProfile(id = "invalid-scale-reversed").copy(
            portrait = DashboardLayout(columns = 2, widgets = listOf(base.copy(scaleMinimum = 200.0, scaleMaximum = 0.0))),
        )
        assertTrue(DashboardCodec.import(DashboardCodec.export(incomplete)).isFailure)
        assertTrue(DashboardCodec.import(DashboardCodec.export(reversed)).isFailure)
    }

    @Test
    fun importRejectsInvalidDigitalRingSegmentCount() {
        val invalidWidget = DashboardDefaults.widgetCatalog.first().copy(
            type = DashboardWidgetType.DIGITAL_RING,
            digitalRing = DigitalRingConfig(segmentCount = 100),
        )
        val invalid = DashboardDefaults.newProfile(id = "invalid-ring").copy(
            portrait = DashboardLayout(columns = 2, widgets = listOf(invalidWidget)),
        )
        assertTrue(DashboardCodec.import(DashboardCodec.export(invalid)).isFailure)
    }

    private fun legacyDashboardJson(style: String): String = """
        {
          "id":"legacy","name":"Legacy dashboard","mode":"ANALOG",
          "portrait":{"columns":2,"widgets":[{
            "id":"rpm","pid":12,"type":"ANALOG","title":"RPM","unit":"RPM",
            "decimals":0,"valueSize":42,"column":0,"row":0,"columnSpan":1,"rowSpan":2,
            "gaugeStyle":"$style",
            "colors":{"value":4294967295,"label":4291811532,"background":4279372051,"border":4281545525,"warning":4294947584,"critical":4294923602},
            "threshold":{}
          }]},"landscape":{"columns":3,"widgets":[]},"isDefault":false
        }
    """.trimIndent()
}
