package com.nowtuneup.app.data.dashboard

import com.nowtuneup.app.data.obd.pid.DerivedPids
import com.nowtuneup.app.domain.model.ColorConfig
import com.nowtuneup.app.domain.model.DashboardConfig
import com.nowtuneup.app.domain.model.DashboardLayout
import com.nowtuneup.app.domain.model.DashboardMode
import com.nowtuneup.app.domain.model.DashboardWidgetConfig
import com.nowtuneup.app.domain.model.DashboardWidgetType
import com.nowtuneup.app.domain.model.DigitalRingColorPreset
import com.nowtuneup.app.domain.model.DisplayUnit
import com.nowtuneup.app.domain.model.GaugeStyle
import com.nowtuneup.app.domain.model.ThemeConfig
import com.nowtuneup.app.domain.model.WarningThreshold
import com.nowtuneup.app.domain.model.digitalRingPreset
import com.nowtuneup.app.domain.model.premiumGaugePreset

object DashboardDefaults {
    private fun widget(
        id: String,
        pid: Int,
        title: String,
        unit: DisplayUnit,
        type: DashboardWidgetType,
        row: Int,
        columnSpan: Int = 1,
        valueSize: Int = 34,
        gaugeStyle: GaugeStyle = GaugeStyle.CLASSIC_METAL,
        colors: ColorConfig = ColorConfig(),
    ): DashboardWidgetConfig {
        val premium = premiumGaugePreset(gaugeStyle)
        return DashboardWidgetConfig(
            id = id,
            pid = pid,
            type = type,
            title = title,
            unit = unit,
            row = row,
            columnSpan = columnSpan,
            valueSize = valueSize,
            gaugeStyle = gaugeStyle,
            bezelFinish = premium.bezelFinish,
            colors = colors.copy(
                value = premium.value,
                label = premium.label,
                background = premium.face,
                border = premium.bezel,
                warning = premium.warning,
                critical = premium.critical,
                face = premium.face,
                bezel = premium.bezel,
                tick = premium.tick,
                needle = premium.needle,
                needleHighlight = premium.needleHighlight,
                glow = premium.glow,
            ),
            threshold = when (pid) {
                0x05 -> WarningThreshold(warningHigh = 105.0, criticalHigh = 115.0)
                0x42 -> WarningThreshold(
                    warningLow = 12.0,
                    warningHigh = 15.0,
                    criticalLow = 11.0,
                    criticalHigh = 16.0,
                )
                else -> WarningThreshold()
            },
        )
    }

    private val core = listOf(
        widget("rpm", 0x0C, "Engine RPM", DisplayUnit.RPM, DashboardWidgetType.ANALOG, 0, gaugeStyle = GaugeStyle.SPORT_RED),
        widget("speed", 0x0D, "Vehicle speed", DisplayUnit.KMH, DashboardWidgetType.DIGITAL, 0, valueSize = 54, gaugeStyle = GaugeStyle.NEO_CYAN),
        widget("coolant", 0x05, "Coolant", DisplayUnit.CELSIUS, DashboardWidgetType.MINI_GAUGE, 1, gaugeStyle = GaugeStyle.OEM_BLUE),
        widget("voltage", 0x42, "Battery", DisplayUnit.VOLT, DashboardWidgetType.MINI_GAUGE, 1, gaugeStyle = GaugeStyle.RACING_AMBER),
        widget("load", 0x04, "Engine load", DisplayUnit.PERCENT, DashboardWidgetType.PROGRESS, 2, gaugeStyle = GaugeStyle.NEO_CYAN),
        widget("throttle", 0x11, "Throttle", DisplayUnit.PERCENT, DashboardWidgetType.DIGITAL, 2, gaugeStyle = GaugeStyle.HUD_GREEN),
    )

    private val turbo = widget(
        id = "turbo-pressure",
        pid = DerivedPids.TURBO_PRESSURE,
        title = "Turbo pressure",
        unit = DisplayUnit.PSI,
        type = DashboardWidgetType.DIGITAL_RING,
        row = 0,
        columnSpan = 2,
        valueSize = 58,
        gaugeStyle = GaugeStyle.NEO_CYAN,
    ).copy(
        rowSpan = 2,
        digitalRing = digitalRingPreset(DigitalRingColorPreset.CYAN, segmentCount = 48),
    )

    val presets = listOf(
        DashboardConfig(
            id = "daily",
            name = "Daily Driving",
            mode = DashboardMode.HYBRID,
            portrait = DashboardLayout(columns = 2, widgets = core),
            landscape = DashboardLayout(columns = 3, widgets = core),
            isDefault = true,
        ),
        DashboardConfig(
            id = "sport",
            name = "Sport & Turbo",
            mode = DashboardMode.ANALOG,
            portrait = DashboardLayout(
                columns = 2,
                widgets = listOf(
                    core[0].copy(type = DashboardWidgetType.ANALOG, gaugeStyle = GaugeStyle.SPORT_RED, rowSpan = 2),
                    turbo,
                ),
            ),
            landscape = DashboardLayout(
                columns = 4,
                widgets = listOf(
                    core[0].copy(type = DashboardWidgetType.ANALOG, gaugeStyle = GaugeStyle.SPORT_RED, columnSpan = 1, rowSpan = 2),
                    core[1].copy(type = DashboardWidgetType.DIGITAL_RING, columnSpan = 1, rowSpan = 2, digitalRing = digitalRingPreset(DigitalRingColorPreset.AMBER)),
                    turbo.copy(columnSpan = 2),
                ),
            ),
        ),
        DashboardConfig(
            id = "hud",
            name = "HUD Essentials",
            mode = DashboardMode.DIGITAL,
            portrait = DashboardLayout(
                columns = 1,
                widgets = listOf(
                    core[1].copy(type = DashboardWidgetType.DIGITAL, columnSpan = 1, valueSize = 72, rowSpan = 2),
                    turbo.copy(columnSpan = 1, rowSpan = 2),
                ),
            ),
            landscape = DashboardLayout(
                columns = 3,
                widgets = listOf(
                    core[1].copy(type = DashboardWidgetType.DIGITAL, columnSpan = 1, valueSize = 72, rowSpan = 2),
                    core[0].copy(type = DashboardWidgetType.DIGITAL, columnSpan = 1, valueSize = 64, rowSpan = 2),
                    turbo.copy(columnSpan = 1, rowSpan = 2),
                ),
            ),
        ),
        DashboardConfig(
            id = "health",
            name = "Engine Health",
            mode = DashboardMode.HYBRID,
            portrait = DashboardLayout(columns = 2, widgets = core.filter { it.pid in setOf(0x05, 0x42, 0x04, 0x11) }),
        ),
        DashboardConfig(
            id = "diagnostic",
            name = "Diagnostic",
            mode = DashboardMode.DIGITAL,
            portrait = DashboardLayout(
                columns = 2,
                widgets = core + widget(
                    "dtc",
                    -1,
                    "DTC status",
                    DisplayUnit.NONE,
                    DashboardWidgetType.DTC_CARD,
                    3,
                    columnSpan = 2,
                ),
            ),
        ),
        DashboardConfig(
            id = "minimal",
            name = "Essential Readings",
            mode = DashboardMode.DIGITAL,
            portrait = DashboardLayout(
                columns = 1,
                widgets = core.take(2).map { it.copy(type = DashboardWidgetType.DIGITAL, columnSpan = 1, valueSize = 56) },
            ),
        ),
        DashboardConfig(
            id = "night",
            name = "Night Driving",
            mode = DashboardMode.ANALOG,
            portrait = DashboardLayout(
                columns = 2,
                widgets = core.take(4).map {
                    it.copy(type = DashboardWidgetType.ANALOG, gaugeStyle = GaugeStyle.NEO_CYAN, rowSpan = 2)
                },
            ),
        ),
    )

    val themes = listOf(
        ThemeConfig(name = "Dark OEM"),
        ThemeConfig(
            name = "Sport Red",
            primary = 0xFFFF1744,
            accent = 0xFFFF1744,
            background = 0xFF08080A,
            card = 0xFF121214,
            gaugeNeedle = 0xFFFF1744,
            gaugeTick = 0xFFFFE8E8,
        ),
        ThemeConfig(
            name = "Neo Cyan",
            primary = 0xFF35E6FF,
            accent = 0xFF35E6FF,
            background = 0xFF041017,
            card = 0xFF091820,
            gaugeNeedle = 0xFF35E6FF,
            gaugeTick = 0xFFBDEFFF,
        ),
        ThemeConfig(
            name = "Amber Night",
            primary = 0xFFFFC247,
            accent = 0xFFFFC247,
            background = 0xFF0D0903,
            card = 0xFF171106,
            gaugeNeedle = 0xFFFFD166,
            gaugeTick = 0xFFFFE5A5,
        ),
        ThemeConfig(
            name = "Premium Blue",
            primary = 0xFF5AAEFF,
            accent = 0xFF5AAEFF,
            background = 0xFF06101D,
            card = 0xFF0B1928,
            gaugeNeedle = 0xFF5AAEFF,
            gaugeTick = 0xFFD7EBFF,
        ),
        ThemeConfig(
            name = "HUD Green",
            primary = 0xFF48FF8A,
            accent = 0xFF48FF8A,
            background = 0xFF020806,
            card = 0xFF06110C,
            gaugeNeedle = 0xFF48FF8A,
            gaugeTick = 0xFFB9FFD0,
        ),
    )
}
