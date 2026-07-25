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
        gaugeStyle: GaugeStyle = GaugeStyle.CLASSIC,
        colors: ColorConfig = ColorConfig(),
    ) = DashboardWidgetConfig(
        id = id,
        pid = pid,
        type = type,
        title = title,
        unit = unit,
        row = row,
        columnSpan = columnSpan,
        valueSize = valueSize,
        gaugeStyle = gaugeStyle,
        colors = colors,
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

    private val core = listOf(
        widget("rpm", 0x0C, "Engine RPM", DisplayUnit.RPM, DashboardWidgetType.ANALOG, 0),
        widget("speed", 0x0D, "Vehicle speed", DisplayUnit.KMH, DashboardWidgetType.DIGITAL, 0, valueSize = 54),
        widget("coolant", 0x05, "Coolant", DisplayUnit.CELSIUS, DashboardWidgetType.MINI_GAUGE, 1),
        widget("voltage", 0x42, "Battery", DisplayUnit.VOLT, DashboardWidgetType.MINI_GAUGE, 1),
        widget("load", 0x04, "Engine load", DisplayUnit.PERCENT, DashboardWidgetType.PROGRESS, 2),
        widget("throttle", 0x11, "Throttle", DisplayUnit.PERCENT, DashboardWidgetType.DIGITAL, 2),
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
                    core[0].copy(type = DashboardWidgetType.ANALOG, gaugeStyle = GaugeStyle.SPORT),
                    turbo,
                ),
            ),
            landscape = DashboardLayout(
                columns = 4,
                widgets = listOf(
                    core[0].copy(type = DashboardWidgetType.ANALOG, gaugeStyle = GaugeStyle.SPORT, columnSpan = 1),
                    core[1].copy(type = DashboardWidgetType.DIGITAL_RING, columnSpan = 1, digitalRing = digitalRingPreset(DigitalRingColorPreset.AMBER)),
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
            portrait = DashboardLayout(
                columns = 2,
                widgets = core.filter { it.pid in setOf(0x05, 0x42, 0x04, 0x11) },
            ),
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
            name = "Minimal",
            mode = DashboardMode.DIGITAL,
            portrait = DashboardLayout(
                columns = 1,
                widgets = core.take(2).map {
                    it.copy(type = DashboardWidgetType.DIGITAL, columnSpan = 1, valueSize = 56)
                },
            ),
        ),
        DashboardConfig(
            id = "night",
            name = "Night Driving",
            mode = DashboardMode.ANALOG,
            portrait = DashboardLayout(
                columns = 2,
                widgets = core.take(4).map {
                    it.copy(type = DashboardWidgetType.ANALOG, gaugeStyle = GaugeStyle.NEON)
                },
            ),
        ),
    )

    val themes = listOf(
        ThemeConfig(),
        ThemeConfig(
            "Light",
            background = 0xFFF4F6F8,
            card = 0xFFFFFFFF,
            text = 0xFF101820,
            border = 0xFFB0BEC5,
        ),
        ThemeConfig("AMOLED Black", background = 0xFF000000, card = 0xFF050505),
        ThemeConfig("Classic Automotive", primary = 0xFFFFB300, accent = 0xFFFFB300),
        ThemeConfig(
            "Racing Red",
            primary = 0xFFFF1744,
            accent = 0xFFFF1744,
            gaugeNeedle = 0xFFFF1744,
        ),
        ThemeConfig(
            "Electric Blue",
            primary = 0xFF2979FF,
            accent = 0xFF2979FF,
            gaugeNeedle = 0xFF2979FF,
        ),
        ThemeConfig(
            "Neon",
            primary = 0xFF39FF14,
            accent = 0xFFFF00FF,
            gaugeNeedle = 0xFFFF00FF,
        ),
        ThemeConfig(
            "OEM Gray",
            primary = 0xFFB0BEC5,
            accent = 0xFF90A4AE,
            card = 0xFF20252A,
        ),
    )
}
